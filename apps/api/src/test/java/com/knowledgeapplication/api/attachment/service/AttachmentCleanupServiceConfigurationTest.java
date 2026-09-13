package com.knowledgeapplication.api.attachment.service;

import com.knowledgeapplication.api.attachment.repository.AttachmentDeleteQueueRepository;
import com.knowledgeapplication.api.attachment.repository.KnowledgeAttachmentRepository;
import com.knowledgeapplication.api.attachment.storage.S3ObjectStorage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AttachmentCleanupServiceConfigurationTest {

    @Test
    void negativeGracePeriodIsRejected() {
        assertThatThrownBy(() -> cleanupService(Duration.ofSeconds(-1), 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Attachment cleanup grace period must not be negative");
    }

    @Test
    void nonPositiveBatchSizeIsRejected() {
        assertThatThrownBy(() -> cleanupService(Duration.ofHours(24), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Attachment cleanup batch size must be positive");
    }

    private static AttachmentCleanupService cleanupService(Duration gracePeriod, int batchSize) {
        return new AttachmentCleanupService(
                mock(KnowledgeAttachmentRepository.class),
                mock(AttachmentDeleteQueueRepository.class),
                mock(AttachmentOrphanCleanupProcessor.class),
                mock(S3ObjectStorage.class),
                Clock.systemUTC(),
                gracePeriod,
                batchSize
        );
    }
}
