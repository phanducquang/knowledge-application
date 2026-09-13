package com.knowledgeapplication.api.attachment.service;

import com.knowledgeapplication.api.attachment.repository.KnowledgeAttachmentRepository;
import com.knowledgeapplication.api.attachment.storage.S3ObjectStorage;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import com.knowledgeapplication.api.knowledge.revision.repository.KnowledgeRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AttachmentOrphanCleanupProcessor {

    private final KnowledgeAttachmentRepository attachmentRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeRevisionRepository revisionRepository;
    private final S3ObjectStorage objectStorage;

    public AttachmentOrphanCleanupProcessor(
            KnowledgeAttachmentRepository attachmentRepository,
            KnowledgeRepository knowledgeRepository,
            KnowledgeRevisionRepository revisionRepository,
            S3ObjectStorage objectStorage
    ) {
        this.attachmentRepository = attachmentRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.revisionRepository = revisionRepository;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public AttachmentCleanupOutcome cleanupIfEligible(UUID attachmentId, Instant cutoff) {
        Long knowledgeId = attachmentRepository.findKnowledgeIdById(attachmentId).orElse(null);
        if (knowledgeId == null) {
            return AttachmentCleanupOutcome.MISSING;
        }

        // Lock parent before attachment to match Knowledge deletion/update lock order and
        // serialize cleanup against a concurrent authoring change.
        var knowledge = knowledgeRepository.findByIdForUpdate(knowledgeId).orElse(null);
        if (knowledge == null) {
            return AttachmentCleanupOutcome.MISSING;
        }

        var attachment = attachmentRepository.findByIdForCleanup(attachmentId).orElse(null);
        if (attachment == null) {
            return AttachmentCleanupOutcome.MISSING;
        }
        if (attachment.getOrphanedAt() == null || attachment.getOrphanedAt().isAfter(cutoff)) {
            return AttachmentCleanupOutcome.NOT_DUE;
        }

        String reference = KnowledgeAttachmentService.markdownSource(attachmentId);
        if (AttachmentReferenceParser.contains(knowledge.getContent(), attachmentId)
                || revisionRepository.existsByKnowledgeIdAndContentContainingIgnoreCase(knowledgeId, reference)) {
            // A retained revision is part of the attachment liveness contract. Clearing the
            // candidate avoids repeatedly selecting the same protected object every run.
            attachment.markReferenced();
            return AttachmentCleanupOutcome.RETAINED_BY_REFERENCE;
        }

        // PostgreSQL and S3 are not transactional together. Delete the object first. If S3
        // fails, the exception rolls back this transaction and metadata remains retryable.
        // If the later DB commit fails, S3 delete is idempotent so the next run converges.
        objectStorage.delete(attachment.getObjectKey());
        attachmentRepository.delete(attachment);
        return AttachmentCleanupOutcome.DELETED;
    }
}
