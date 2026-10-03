package com.knowledgeapplication.api.ai.quota;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.ai.quota-cleanup")
public record AiQuotaCleanupProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("P2D") Duration minuteRetention,
        @DefaultValue("P30D") Duration dailyRetention,
        @DefaultValue("PT6H") Duration interval,
        @DefaultValue("PT10M") Duration initialDelay,
        @DefaultValue("1000") int batchSize
) {
    public AiQuotaCleanupProperties {
        if (enabled) {
            if (!retentionValid(minuteRetention) || !retentionValid(dailyRetention)) {
                throw new IllegalArgumentException("AI quota retention must be between 1 millisecond and 36500 days");
            }
            if (interval == null || interval.compareTo(Duration.ofSeconds(10)) < 0
                    || interval.compareTo(Duration.ofDays(365)) > 0) {
                throw new IllegalArgumentException("AI quota cleanup interval must be between 10 seconds and 365 days");
            }
            if (initialDelay == null || initialDelay.isNegative() || initialDelay.compareTo(Duration.ofDays(365)) > 0) {
                throw new IllegalArgumentException("AI quota cleanup initial delay must be between zero and 365 days");
            }
            if (batchSize < 1 || batchSize > 10000) {
                throw new IllegalArgumentException("AI quota cleanup batch size must be between 1 and 10000");
            }
        }
    }

    private static boolean retentionValid(Duration retention) {
        return retention != null && retention.compareTo(Duration.ofMillis(1)) >= 0
                && retention.compareTo(Duration.ofDays(36500)) <= 0;
    }
}
