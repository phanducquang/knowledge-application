package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.ai.quota.AiQuotaProperties;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Read-only precheck on the ephemeral quota DB; the real adapter still atomically reserves each request. */
final class LiveQuotaPacer implements LiveEmbeddingRun.Pacer {
    interface Sleeper { void sleep(long millis) throws InterruptedException; }
    private final JdbcClient jdbc;
    private final AiQuotaProperties props;
    private final Clock clock;
    private final Sleeper sleeper;
    private final long maxWaitMillis;
    private long waited;
    LiveQuotaPacer(JdbcClient jdbc, AiQuotaProperties props, Clock clock, int maxWaitSeconds, Sleeper sleeper) {
        this.jdbc=jdbc; this.props=props; this.clock=clock; this.maxWaitMillis=maxWaitSeconds*1000L; this.sleeper=sleeper;
    }
    public void await(boolean background,long chars) {
        long tokens=props.estimate(chars);
        for (;;) {
            if(Thread.currentThread().isInterrupted()) throw new LiveEvaluationFailure(LiveEvaluationFailure.Kind.INTERRUPTED);
            Instant now=clock.instant(); boolean minuteBlocked=false;
            String[] keys=background ? new String[]{"embedding-global","embedding-background"} : new String[]{"embedding-global"};
            for(var key : keys) {
                var budget=key.equals("embedding-global") ? props.budget() : props.background();
                if(tokens>budget.inputTokensPerMinute()) throw new LiveEvaluationFailure(LiveEvaluationFailure.Kind.MINUTE_BATCH);
                var day=now.atZone(props.dailyResetZone()).toLocalDate().atStartOfDay(props.dailyResetZone()).toInstant();
                if(!available(key+"-day",day,budget.requestsPerDay(),Long.MAX_VALUE,0))
                    throw new LiveEvaluationFailure(LiveEvaluationFailure.Kind.DAILY_QUOTA);
                minuteBlocked |= !available(key+"-minute",now.truncatedTo(ChronoUnit.MINUTES),budget.requestsPerMinute(),budget.inputTokensPerMinute(),tokens);
            }
            if(!minuteBlocked) return;
            long delay=Duration.between(now,now.truncatedTo(ChronoUnit.MINUTES).plusSeconds(60)).toMillis()+50;
            if(waited+delay>maxWaitMillis) throw new LiveEvaluationFailure(LiveEvaluationFailure.Kind.WAIT_EXHAUSTED);
            System.out.println("Manual evaluation waiting for the next local quota minute; no provider retry.");
            try { sleeper.sleep(delay); waited+=delay; }
            catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new LiveEvaluationFailure(LiveEvaluationFailure.Kind.INTERRUPTED); }
        }
    }
    private boolean available(String key,Instant start,long requests,long tokenLimit,long cost) {
        return jdbc.sql("SELECT request_count < :requests AND estimated_input_tokens <= :limit - :cost FROM ai_quota_usage WHERE quota_key=:key AND window_start=:start")
                .param("requests",requests).param("limit",tokenLimit).param("cost",cost).param("key",key).param("start",start.atOffset(ZoneOffset.UTC))
                .query(Boolean.class).optional().orElse(true);
    }
}
