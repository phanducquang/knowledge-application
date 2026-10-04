package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.ai.quota.*;
import com.knowledgeapplication.api.testsupport.TestContainerImages;
import com.zaxxer.hikari.HikariDataSource;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import com.knowledgeapplication.api.metadata.*;

/** Own container only, quota table only. Cannot accept an application DB URL or load Knowledge. */
final class MetadataQuotaDatabase implements AutoCloseable {
    interface Sleeper { void sleep(long millis) throws InterruptedException; }
    private final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(TestContainerImages.POSTGRES);
    private final HikariDataSource pool=new HikariDataSource();
    final JdbcClient jdbc;
    final AiQuotaLimiter limiter;
    MetadataQuotaDatabase() {
        try {
            postgres.start(); pool.setJdbcUrl(postgres.getJdbcUrl()); pool.setUsername(postgres.getUsername()); pool.setPassword(postgres.getPassword());
            pool.setMaximumPoolSize(2); pool.setMinimumIdle(0);
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V9__ai_quota_usage.sql")).execute(pool);
            jdbc=JdbcClient.create(pool); limiter=new AiQuotaLimiter(jdbc,new DataSourceTransactionManager(pool),Clock.systemUTC());
        } catch(RuntimeException ex) { close(); throw ex; }
    }
    record Usage(int requests,long tokens) {}
    Usage usage() {
        return jdbc.sql("SELECT COALESCE(SUM(request_count),0) requests, COALESCE(SUM(estimated_input_tokens),0) tokens FROM ai_quota_usage WHERE quota_key='metadata-generation-day'")
                .query((row,n)->new Usage(row.getInt("requests"),row.getLong("tokens"))).single();
    }
    MetadataEvaluation.Pacer pacer(AiQuotaProperties quota,int maxWaitSeconds) {
        return new QuotaPacer(jdbc,quota,Clock.systemUTC(),maxWaitSeconds,Thread::sleep);
    }
    MetadataPacing pacer(AiQuotaProperties quota,int maxWaitSeconds,int maxRpm,long safetyMillis) {
        var wait=new MetadataPacing.WaitBudget(System::nanoTime,Thread::sleep,maxWaitSeconds);
        return new MetadataPacing(maxRpm,quota.requestsPerMinute(),safetyMillis,System::nanoTime,wait,
                new QuotaPacer(jdbc,quota,Clock.systemUTC(),maxWaitSeconds,Thread::sleep,wait));
    }
    static final class QuotaPacer implements MetadataEvaluation.Pacer {
        private final JdbcClient jdbc; private final AiQuotaProperties quota; private final Clock clock;
        private final Sleeper sleeper; private final long maxWaitMillis; private long waited;
        private final MetadataPacing.WaitBudget sharedWait;
        QuotaPacer(JdbcClient jdbc,AiQuotaProperties quota,Clock clock,int maxWaitSeconds,Sleeper sleeper) {
            this(jdbc,quota,clock,maxWaitSeconds,sleeper,null);
        }
        QuotaPacer(JdbcClient jdbc,AiQuotaProperties quota,Clock clock,int maxWaitSeconds,Sleeper sleeper,MetadataPacing.WaitBudget sharedWait) {
            this.jdbc=jdbc; this.quota=quota; this.clock=clock; this.maxWaitMillis=maxWaitSeconds*1000L; this.sleeper=sleeper;
            this.sharedWait=sharedWait;
        }
        @Override public void await(long chars) {
            long tokens=quota.estimate(chars);
            for(;;) {
                if(Thread.currentThread().isInterrupted()) throw new MetadataUnavailableException(MetadataFailureCategory.PACING_BUDGET);
                Instant now=clock.instant();
                if(tokens>quota.inputTokensPerMinute() || !available("metadata-generation-day",
                        now.atZone(quota.dailyResetZone()).toLocalDate().atStartOfDay(quota.dailyResetZone()).toInstant(),quota.requestsPerDay(),Long.MAX_VALUE,0))
                    throw new MetadataUnavailableException(MetadataFailureCategory.LOCAL_QUOTA);
                Instant minute=now.truncatedTo(ChronoUnit.MINUTES);
                if(available("metadata-generation-minute",minute,quota.requestsPerMinute(),quota.inputTokensPerMinute(),tokens)) return;
                long delay=Duration.between(now,minute.plusSeconds(60)).toMillis()+50;
                if(sharedWait!=null) { sharedWait.sleep(delay*1_000_000L); continue; }
                if(waited+delay>maxWaitMillis) throw new MetadataUnavailableException(MetadataFailureCategory.PACING_BUDGET);
                // Each sleep <=30s; interruptible, no provider request/retry during pacing.
                long remaining=delay;
                while(remaining>0) {
                    long chunk=Math.min(30000,remaining);
                    try { sleeper.sleep(chunk); }
                    catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new MetadataUnavailableException(MetadataFailureCategory.PACING_BUDGET); }
                    remaining-=chunk; waited+=chunk;
                }
            }
        }
        private boolean available(String key,Instant start,long requests,long tokenLimit,long cost) {
            return jdbc.sql("SELECT request_count < :requests AND estimated_input_tokens <= :limit - :cost FROM ai_quota_usage WHERE quota_key=:key AND window_start=:start")
                    .param("requests",requests).param("limit",tokenLimit).param("cost",cost).param("key",key).param("start",start.atOffset(ZoneOffset.UTC))
                    .query(Boolean.class).optional().orElse(true);
        }
    }
    @Override public void close() { pool.close(); postgres.stop(); }
}
