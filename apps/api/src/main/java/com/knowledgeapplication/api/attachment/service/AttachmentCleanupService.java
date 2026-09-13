package com.knowledgeapplication.api.attachment.service;

import com.knowledgeapplication.api.attachment.repository.AttachmentDeleteQueueRepository;
import com.knowledgeapplication.api.attachment.repository.KnowledgeAttachmentRepository;
import com.knowledgeapplication.api.attachment.storage.S3ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class AttachmentCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentCleanupService.class);

    private final KnowledgeAttachmentRepository attachmentRepository;
    private final AttachmentDeleteQueueRepository deleteQueueRepository;
    private final AttachmentOrphanCleanupProcessor orphanProcessor;
    private final S3ObjectStorage objectStorage;
    private final Clock clock;
    private final Duration gracePeriod;
    private final int batchSize;

    public AttachmentCleanupService(
            KnowledgeAttachmentRepository attachmentRepository,
            AttachmentDeleteQueueRepository deleteQueueRepository,
            AttachmentOrphanCleanupProcessor orphanProcessor,
            S3ObjectStorage objectStorage,
            Clock clock,
            @Value("${app.knowledge.attachment-cleanup.grace-period:PT24H}") Duration gracePeriod,
            @Value("${app.knowledge.attachment-cleanup.batch-size:100}") int batchSize
    ) {
        if (gracePeriod.isNegative()) {
            throw new IllegalArgumentException("Attachment cleanup grace period must not be negative");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Attachment cleanup batch size must be positive");
        }
        this.attachmentRepository = attachmentRepository;
        this.deleteQueueRepository = deleteQueueRepository;
        this.orphanProcessor = orphanProcessor;
        this.objectStorage = objectStorage;
        this.clock = clock;
        this.gracePeriod = gracePeriod;
        this.batchSize = batchSize;
    }

    public AttachmentCleanupSummary cleanupOnce() {
        int queuedDeleted = cleanupQueuedKnowledgeDeletes();
        int orphanDeleted = 0;
        int retained = 0;
        int failures = 0;

        Instant cutoff = clock.instant().minus(gracePeriod);
        var candidateIds = attachmentRepository.findCleanupCandidateIds(
                cutoff,
                PageRequest.of(0, batchSize)
        );
        for (var attachmentId : candidateIds) {
            try {
                AttachmentCleanupOutcome outcome = orphanProcessor.cleanupIfEligible(attachmentId, cutoff);
                if (outcome == AttachmentCleanupOutcome.DELETED) {
                    orphanDeleted++;
                } else if (outcome == AttachmentCleanupOutcome.RETAINED_BY_REFERENCE) {
                    retained++;
                }
            } catch (RuntimeException exception) {
                failures++;
                log.warn("Attachment orphan cleanup failed. attachmentId={}, errorType={}",
                        attachmentId, exception.getClass().getSimpleName());
            }
        }

        return new AttachmentCleanupSummary(queuedDeleted, orphanDeleted, retained, failures);
    }

    private int cleanupQueuedKnowledgeDeletes() {
        int deleted = 0;
        var queued = deleteQueueRepository.findAllByOrderByQueuedAtAscObjectKeyAsc(
                PageRequest.of(0, batchSize)
        );
        for (var entry : queued) {
            try {
                objectStorage.delete(entry.getObjectKey());
                deleteQueueRepository.deleteById(entry.getObjectKey());
                deleted++;
            } catch (RuntimeException exception) {
                log.warn("Queued attachment object cleanup failed. errorType={}",
                        exception.getClass().getSimpleName());
            }
        }
        return deleted;
    }
}
