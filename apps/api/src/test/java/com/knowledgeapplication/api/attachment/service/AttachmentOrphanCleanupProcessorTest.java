package com.knowledgeapplication.api.attachment.service;

import com.knowledgeapplication.api.attachment.model.KnowledgeAttachment;
import com.knowledgeapplication.api.attachment.repository.KnowledgeAttachmentRepository;
import com.knowledgeapplication.api.attachment.storage.ObjectStorageUnavailableException;
import com.knowledgeapplication.api.attachment.storage.S3ObjectStorage;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import com.knowledgeapplication.api.knowledge.revision.repository.KnowledgeRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentOrphanCleanupProcessorTest {

    private static final UUID ATTACHMENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Instant ORPHANED_AT = Instant.parse("2026-09-12T00:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-09-13T00:00:00Z");

    @Mock KnowledgeAttachmentRepository attachmentRepository;
    @Mock KnowledgeRepository knowledgeRepository;
    @Mock KnowledgeRevisionRepository revisionRepository;
    @Mock S3ObjectStorage objectStorage;
    @Mock KnowledgeAttachment attachment;
    @Mock Knowledge knowledge;

    AttachmentOrphanCleanupProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AttachmentOrphanCleanupProcessor(
                attachmentRepository,
                knowledgeRepository,
                revisionRepository,
                objectStorage
        );
        when(attachmentRepository.findKnowledgeIdById(ATTACHMENT_ID)).thenReturn(Optional.of(7L));
        when(knowledgeRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(knowledge));
        when(attachmentRepository.findByIdForCleanup(ATTACHMENT_ID)).thenReturn(Optional.of(attachment));
        when(attachment.getOrphanedAt()).thenReturn(ORPHANED_AT);
    }

    @Test
    void retainedRevisionHealsCandidateWithoutDeletingObject() {
        String reference = KnowledgeAttachmentService.markdownSource(ATTACHMENT_ID);
        when(knowledge.getContent()).thenReturn("current text");
        when(revisionRepository.existsByKnowledgeIdAndContentContainingIgnoreCase(7L, reference))
                .thenReturn(true);

        assertThat(processor.cleanupIfEligible(ATTACHMENT_ID, CUTOFF))
                .isEqualTo(AttachmentCleanupOutcome.RETAINED_BY_REFERENCE);

        verify(attachment).markReferenced();
        verify(objectStorage, never()).delete(org.mockito.ArgumentMatchers.anyString());
        verify(attachmentRepository, never()).delete(attachment);
    }

    @Test
    void storageFailureLeavesMetadataRetryable() {
        when(knowledge.getContent()).thenReturn("current text");
        when(attachment.getObjectKey()).thenReturn("knowledge/owner/7/object");
        var failure = new ObjectStorageUnavailableException(new IllegalStateException("offline"));
        org.mockito.Mockito.doThrow(failure).when(objectStorage).delete("knowledge/owner/7/object");

        assertThatThrownBy(() -> processor.cleanupIfEligible(ATTACHMENT_ID, CUTOFF)).isSameAs(failure);
        verify(attachmentRepository, never()).delete(attachment);
    }

    @Test
    void unreferencedDueCandidateDeletesObjectBeforeMetadata() {
        when(knowledge.getContent()).thenReturn("current text");
        when(attachment.getObjectKey()).thenReturn("knowledge/owner/7/object");

        assertThat(processor.cleanupIfEligible(ATTACHMENT_ID, CUTOFF))
                .isEqualTo(AttachmentCleanupOutcome.DELETED);

        var order = org.mockito.Mockito.inOrder(objectStorage, attachmentRepository);
        order.verify(objectStorage).delete("knowledge/owner/7/object");
        order.verify(attachmentRepository).delete(attachment);
    }
}
