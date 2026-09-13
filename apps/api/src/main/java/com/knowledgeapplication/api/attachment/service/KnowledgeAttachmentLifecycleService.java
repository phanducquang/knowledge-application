package com.knowledgeapplication.api.attachment.service;

import com.knowledgeapplication.api.attachment.repository.KnowledgeAttachmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class KnowledgeAttachmentLifecycleService {

    private final KnowledgeAttachmentRepository attachmentRepository;
    private final Clock clock;

    public KnowledgeAttachmentLifecycleService(
            KnowledgeAttachmentRepository attachmentRepository,
            Clock clock
    ) {
        this.attachmentRepository = attachmentRepository;
        this.clock = clock;
    }

    @Transactional
    public void synchronizeReferences(Long knowledgeId, String previousContent, String nextContent) {
        Set<UUID> previous = new HashSet<>(AttachmentReferenceParser.extract(previousContent));
        Set<UUID> next = new HashSet<>(AttachmentReferenceParser.extract(nextContent));

        if (!next.isEmpty()) {
            // Scope by parent Knowledge so a copied UUID in another note can never retain
            // or reactivate an attachment it is not authorized to serve.
            attachmentRepository.markReferenced(knowledgeId, next);
        }

        previous.removeAll(next);
        if (!previous.isEmpty()) {
            attachmentRepository.markOrphaned(knowledgeId, previous, clock.instant());
        }
    }

    @Transactional
    public int enqueueKnowledgeDeletion(Long knowledgeId) {
        return attachmentRepository.enqueueKnowledgeDeletion(knowledgeId, clock.instant());
    }
}
