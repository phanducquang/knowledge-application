package com.knowledgeapplication.api.ai.quota;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Atomic, cross-replica fixed-minute and timezone-aware daily reservations. No payload storage. */
@Component
public class AiQuotaLimiter {
    public enum Purpose { EMBEDDING, EMBEDDING_BACKGROUND, ASK, METADATA }
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    public AiQuotaLimiter(JdbcClient jdbc, PlatformTransactionManager manager, Clock clock) {
        this.jdbc = jdbc; this.transaction = new TransactionTemplate(manager); this.clock = clock;
    }
    private record Window(String key, Instant start, Instant end, long requests, long tokens) {}

    public boolean reserve(Purpose purpose, AiQuotaProperties props, long inputChars) {
        if (!props.enabled()) return true;
        if (inputChars < 0) throw new IllegalArgumentException("AI input size is invalid");
        long tokens = props.estimate(inputChars);
        return Boolean.TRUE.equals(transaction.execute(status -> {
            // Serialize a quota group across replicas, including its background sub-budget.
            jdbc.sql("SELECT pg_advisory_xact_lock(:key)").param("key", purpose == Purpose.METADATA ? 83452004L : purpose == Purpose.ASK ? 83452002L : 83452001L)
                    .query((row, n) -> true).single();
            Instant now = clock.instant();
            List<Window> windows = new ArrayList<>();
            add(windows, purpose == Purpose.METADATA ? "metadata-generation" : purpose == Purpose.ASK ? "ask-generation" : "embedding-global", props.budget(), props, now);
            if (purpose == Purpose.EMBEDDING_BACKGROUND) {
                if (props.background() == null) throw new IllegalArgumentException("Background quota is required");
                add(windows, "embedding-background", props.background(), props, now);
            }
            for (var window : windows) {
                jdbc.sql("""
                        INSERT INTO ai_quota_usage(quota_key, window_start, window_end) VALUES(:key,:start,:end)
                        ON CONFLICT DO NOTHING
                        """).param("key", window.key()).param("start", window.start().atOffset(ZoneOffset.UTC))
                        .param("end", window.end().atOffset(ZoneOffset.UTC)).update();
                boolean available = jdbc.sql("""
                        SELECT request_count < :requests AND estimated_input_tokens <= :tokens - :input AS available
                        FROM ai_quota_usage WHERE quota_key=:key AND window_start=:start
                        """).param("key", window.key()).param("start", window.start().atOffset(ZoneOffset.UTC))
                        .param("requests", window.requests()).param("tokens", window.tokens()).param("input", tokens)
                        .query(Boolean.class).single();
                if (!available) return false; // No usage counters increment unless EVERY window allows it.
            }
            for (var window : windows) jdbc.sql("""
                    UPDATE ai_quota_usage SET request_count=request_count+1,
                        estimated_input_tokens=estimated_input_tokens+:input, updated_at=CURRENT_TIMESTAMP
                    WHERE quota_key=:key AND window_start=:start
                    """).param("key", window.key()).param("start", window.start().atOffset(ZoneOffset.UTC))
                    .param("input", tokens).update();
            return true;
        }));
    }
    private static void add(List<Window> windows, String key, AiQuotaProperties.Budget limits, AiQuotaProperties props, Instant now) {
        Instant minute = now.truncatedTo(ChronoUnit.MINUTES);
        var day = now.atZone(props.dailyResetZone()).toLocalDate();
        windows.add(new Window(key+"-minute", minute, minute.plusSeconds(60), limits.requestsPerMinute(), limits.inputTokensPerMinute()));
        windows.add(new Window(key+"-day", day.atStartOfDay(props.dailyResetZone()).toInstant(),
                day.plusDays(1).atStartOfDay(props.dailyResetZone()).toInstant(), limits.requestsPerDay(), Long.MAX_VALUE));
    }
}
