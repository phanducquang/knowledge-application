package com.knowledgeapplication.api.attachment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(
        prefix = "app.knowledge.attachment-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AttachmentCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttachmentCleanupScheduler.class);

    private final AttachmentCleanupService cleanupService;

    public AttachmentCleanupScheduler(
            AttachmentCleanupService cleanupService,
            @Value("${app.knowledge.attachment-cleanup.interval:PT1H}") Duration interval,
            @Value("${app.knowledge.attachment-cleanup.initial-delay:PT5M}") Duration initialDelay
    ) {
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("Attachment cleanup interval must be positive");
        }
        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("Attachment cleanup initial delay must not be negative");
        }
        this.cleanupService = cleanupService;
    }

    @Scheduled(
            fixedDelayString = "${app.knowledge.attachment-cleanup.interval:PT1H}",
            initialDelayString = "${app.knowledge.attachment-cleanup.initial-delay:PT5M}"
    )
    public void cleanup() {
        AttachmentCleanupSummary summary = cleanupService.cleanupOnce();
        if (summary.hasActivity()) {
            log.info(
                    "Attachment cleanup completed. queuedDeleted={}, orphanDeleted={}, retained={}, failures={}",
                    summary.queuedObjectsDeleted(),
                    summary.orphanAttachmentsDeleted(),
                    summary.referencesRetained(),
                    summary.failures()
            );
        }
    }
}
