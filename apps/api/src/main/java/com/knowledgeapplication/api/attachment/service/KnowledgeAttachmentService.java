package com.knowledgeapplication.api.attachment.service;

import com.knowledgeapplication.api.attachment.model.KnowledgeAttachment;
import com.knowledgeapplication.api.attachment.repository.KnowledgeAttachmentRepository;
import com.knowledgeapplication.api.attachment.storage.S3ObjectStorage;
import com.knowledgeapplication.api.attachment.validation.ImageUploadValidator;
import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import com.knowledgeapplication.api.knowledge.service.KnowledgeNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class KnowledgeAttachmentService {

    public static final String MARKDOWN_SCHEME = "attachment://";

    private final KnowledgeAttachmentRepository attachmentRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final CurrentOwner currentOwner;
    private final ImageUploadValidator imageValidator;
    private final S3ObjectStorage objectStorage;
    private final Clock clock;

    public KnowledgeAttachmentService(
            KnowledgeAttachmentRepository attachmentRepository,
            KnowledgeRepository knowledgeRepository,
            CurrentOwner currentOwner,
            ImageUploadValidator imageValidator,
            S3ObjectStorage objectStorage,
            Clock clock
    ) {
        this.attachmentRepository = attachmentRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.currentOwner = currentOwner;
        this.imageValidator = imageValidator;
        this.objectStorage = objectStorage;
        this.clock = clock;
    }

    @Transactional
    public KnowledgeAttachment uploadImage(Long knowledgeId, MultipartFile file) {
        UUID ownerId = currentOwner.id();
        Knowledge knowledge = knowledgeRepository.findByIdAndOwnerId(knowledgeId, ownerId)
                .orElseThrow(KnowledgeNotFoundException::new);
        var validated = imageValidator.validate(file);
        UUID attachmentId = UUID.randomUUID();
        String objectKey = "knowledge/%s/%d/%s".formatted(ownerId, knowledgeId, attachmentId);

        objectStorage.put(objectKey, validated.content(), validated.contentType());
        AtomicBoolean cleanupNeeded = new AtomicBoolean(true);
        registerRollbackCleanup(objectKey, cleanupNeeded);

        try {
            return attachmentRepository.saveAndFlush(KnowledgeAttachment.create(
                    attachmentId,
                    knowledge,
                    objectKey,
                    safeFilename(file.getOriginalFilename()),
                    validated.contentType(),
                    validated.content().length,
                    clock.instant()
            ));
        } catch (RuntimeException exception) {
            cleanup(objectKey, cleanupNeeded);
            throw new AttachmentPersistenceException(exception);
        }
    }

    @Transactional(readOnly = true)
    public AttachmentContent getOwnerContent(Long knowledgeId, UUID attachmentId) {
        var attachment = attachmentRepository.findByIdAndKnowledgeIdAndKnowledgeOwnerId(
                        attachmentId, knowledgeId, currentOwner.id()
                )
                .orElseThrow(AttachmentNotFoundException::new);
        return load(attachment);
    }

    @Transactional(readOnly = true)
    public AttachmentContent getPublicContent(String slug, UUID attachmentId) {
        var attachment = attachmentRepository.findByIdAndKnowledgeSlugAndKnowledgeVisibility(
                        attachmentId, slug, Visibility.PUBLIC
                )
                .orElseThrow(PublicAttachmentNotFoundException::new);
        return load(attachment);
    }

    @Transactional(readOnly = true)
    public AttachmentContent getSharedContent(String shareToken, UUID attachmentId) {
        var attachment = attachmentRepository.findByIdAndKnowledgeShareTokenAndKnowledgeVisibility(
                        attachmentId, shareToken, Visibility.UNLISTED
                )
                .orElseThrow(SharedAttachmentNotFoundException::new);
        return load(attachment);
    }

    public static String markdownSource(UUID attachmentId) {
        return MARKDOWN_SCHEME + attachmentId;
    }

    private AttachmentContent load(KnowledgeAttachment attachment) {
        var stored = objectStorage.get(attachment.getObjectKey());
        return new AttachmentContent(
                stored.content(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getOriginalFilename()
        );
    }

    private void registerRollbackCleanup(String objectKey, AtomicBoolean cleanupNeeded) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    cleanup(objectKey, cleanupNeeded);
                }
            }
        });
    }

    private void cleanup(String objectKey, AtomicBoolean cleanupNeeded) {
        if (cleanupNeeded.getAndSet(false)) {
            objectStorage.deleteBestEffort(objectKey);
        }
    }

    private static String safeFilename(String originalFilename) {
        String filename = originalFilename == null ? "image" : originalFilename;
        filename = filename.replace('\\', '/');
        int separator = filename.lastIndexOf('/');
        if (separator >= 0) filename = filename.substring(separator + 1);
        filename = filename.replaceAll("[\\p{Cntrl}]", "").trim();
        if (filename.isEmpty()) filename = "image";
        if (filename.length() > 255) filename = filename.substring(filename.length() - 255);
        return filename;
    }
}
