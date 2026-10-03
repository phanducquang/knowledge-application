package com.knowledgeapplication.api.ai.quota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.ai.quota-cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiQuotaCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(AiQuotaCleanupScheduler.class);
    private final AiQuotaCleanupService service;

    public AiQuotaCleanupScheduler(AiQuotaCleanupService service, AiQuotaCleanupProperties properties) {
        this.service = service;
        // Inject properties so enabled configuration is validated before scheduled task registration.
    }

    @Scheduled(fixedDelayString = "${app.ai.quota-cleanup.interval:PT6H}",
            initialDelayString = "${app.ai.quota-cleanup.initial-delay:PT10M}")
    public void cleanup() {
        long started = System.nanoTime();
        try {
            var summary = service.cleanupOnce();
            if (summary.minuteRowsDeleted() + summary.dailyRowsDeleted() > 0) {
                log.info("AI quota cleanup completed. minuteRowsDeleted={}, dailyRowsDeleted={}, elapsedMs={}",
                        summary.minuteRowsDeleted(), summary.dailyRowsDeleted(), (System.nanoTime() - started) / 1000000);
            }
        } catch (RuntimeException ex) {
            // No row/payload/exception bodies; rollback preserves history for the next scheduled cycle.
            log.warn("AI quota cleanup unavailable; retained history will be retried on the next scheduled cycle");
        }
    }
}
