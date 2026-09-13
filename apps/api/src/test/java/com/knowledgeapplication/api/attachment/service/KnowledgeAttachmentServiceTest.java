package com.knowledgeapplication.api.attachment.service;

import com.knowledgeapplication.api.attachment.model.KnowledgeAttachment;
import com.knowledgeapplication.api.attachment.repository.KnowledgeAttachmentRepository;
import com.knowledgeapplication.api.attachment.storage.S3ObjectStorage;
import com.knowledgeapplication.api.attachment.validation.ImageUploadValidator;
import com.knowledgeapplication.api.attachment.validation.ValidatedImage;
import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeAttachmentServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

    @Mock KnowledgeAttachmentRepository attachmentRepository;
    @Mock KnowledgeRepository knowledgeRepository;
    @Mock CurrentOwner currentOwner;
    @Mock ImageUploadValidator imageValidator;
    @Mock S3ObjectStorage objectStorage;

    KnowledgeAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeAttachmentService(
                attachmentRepository,
                knowledgeRepository,
                currentOwner,
                imageValidator,
                objectStorage,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void uploadUsesServerOwnedIdentityAndObjectKeyThenPersistsMetadata() {
        var knowledge = Knowledge.create(OWNER_ID, "Title", "stable-slug", null, "", Visibility.PRIVATE);
        var file = new MockMultipartFile("file", "../unsafe name.png", "image/png", new byte[]{1, 2, 3});
        when(currentOwner.id()).thenReturn(OWNER_ID);
        when(knowledgeRepository.findByIdAndOwnerId(7L, OWNER_ID)).thenReturn(Optional.of(knowledge));
        when(imageValidator.validate(file)).thenReturn(new ValidatedImage(
                new byte[]{1, 2, 3}, "image/png", 1, 1
        ));
        when(attachmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeAttachment attachment = service.uploadImage(7L, file);

        assertThat(attachment.getOriginalFilename()).isEqualTo("unsafe name.png");
        assertThat(attachment.getCreatedAt()).isEqualTo(NOW);
        assertThat(attachment.getObjectKey())
                .startsWith("knowledge/" + OWNER_ID + "/7/")
                .doesNotContain("unsafe name.png");
        assertThat(KnowledgeAttachmentService.markdownSource(attachment.getId()))
                .isEqualTo("attachment://" + attachment.getId());
        verify(objectStorage).put(attachment.getObjectKey(), new byte[]{1, 2, 3}, "image/png");
    }

    @Test
    void storageFailureDoesNotPersistMetadata() {
        var knowledge = Knowledge.create(OWNER_ID, "Title", "stable-slug", null, "", Visibility.PRIVATE);
        var file = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1});
        when(currentOwner.id()).thenReturn(OWNER_ID);
        when(knowledgeRepository.findByIdAndOwnerId(7L, OWNER_ID)).thenReturn(Optional.of(knowledge));
        when(imageValidator.validate(file)).thenReturn(new ValidatedImage(new byte[]{1}, "image/png", 1, 1));
        var failure = new com.knowledgeapplication.api.attachment.storage.ObjectStorageUnavailableException(
                new IllegalStateException("offline")
        );
        org.mockito.Mockito.doThrow(failure).when(objectStorage).put(any(), any(), any());

        assertThatThrownBy(() -> service.uploadImage(7L, file)).isSameAs(failure);
        verify(attachmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void persistenceFailureAttemptsObjectCleanup() {
        var knowledge = Knowledge.create(OWNER_ID, "Title", "stable-slug", null, "", Visibility.PRIVATE);
        var file = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1});
        when(currentOwner.id()).thenReturn(OWNER_ID);
        when(knowledgeRepository.findByIdAndOwnerId(7L, OWNER_ID)).thenReturn(Optional.of(knowledge));
        when(imageValidator.validate(file)).thenReturn(new ValidatedImage(new byte[]{1}, "image/png", 1, 1));
        when(attachmentRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("metadata failed"));

        assertThatThrownBy(() -> service.uploadImage(7L, file))
                .isInstanceOf(AttachmentPersistenceException.class);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).deleteBestEffort(key.capture());
        assertThat(key.getValue()).startsWith("knowledge/" + OWNER_ID + "/7/");
    }
}
