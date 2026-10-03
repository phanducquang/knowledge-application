package com.knowledgeapplication.api.ai.quota;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Expired operational metadata only. Never shares reservation locks or performs provider calls. */
@Service
public class AiQuotaCleanupService {
    static final long CLEANUP_LOCK_KEY = 83452003L;
    public record Summary(boolean lockAcquired, long minuteRowsDeleted, long dailyRowsDeleted) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final AiQuotaCleanupProperties properties;
    private final Clock clock;

    public AiQuotaCleanupService(JdbcClient jdbc, PlatformTransactionManager manager,
            AiQuotaCleanupProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(30);
    }

    public Summary cleanupOnce() {
        if (!properties.enabled()) return new Summary(false, 0, 0);
        return transaction.execute(status -> {
            boolean acquired = jdbc.sql("SELECT pg_try_advisory_xact_lock(:key)")
                    .param("key", CLEANUP_LOCK_KEY).query(Boolean.class).single();
            if (!acquired) return new Summary(false, 0, 0);
            var now = clock.instant();
            return jdbc.sql("""
                    WITH expired AS (
                        SELECT quota_key, window_start
                        FROM ai_quota_usage
                        WHERE window_end < :now
                          AND ((quota_key LIKE '%-minute' AND window_end < :minuteCutoff)
                            OR (quota_key LIKE '%-day' AND window_end < :dailyCutoff))
                        ORDER BY window_end ASC, quota_key ASC, window_start ASC
                        LIMIT :batchSize
                        FOR UPDATE SKIP LOCKED
                    ), deleted AS (
                        DELETE FROM ai_quota_usage q USING expired
                        WHERE q.quota_key = expired.quota_key AND q.window_start = expired.window_start
                        RETURNING q.quota_key
                    )
                    SELECT count(*) FILTER (WHERE quota_key LIKE '%-minute') AS minute_rows,
                           count(*) FILTER (WHERE quota_key LIKE '%-day') AS daily_rows
                    FROM deleted
                    """)
                    .param("now", now.atOffset(ZoneOffset.UTC))
                    .param("minuteCutoff", now.minus(properties.minuteRetention()).atOffset(ZoneOffset.UTC))
                    .param("dailyCutoff", now.minus(properties.dailyRetention()).atOffset(ZoneOffset.UTC))
                    .param("batchSize", properties.batchSize())
                    .query((row, n) -> new Summary(true, row.getLong("minute_rows"), row.getLong("daily_rows")))
                    .single();
        });
    }
}
