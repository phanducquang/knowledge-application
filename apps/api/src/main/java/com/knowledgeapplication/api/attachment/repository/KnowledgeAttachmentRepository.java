package com.knowledgeapplication.api.attachment.repository;

import com.knowledgeapplication.api.attachment.model.KnowledgeAttachment;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgeAttachmentRepository extends JpaRepository<KnowledgeAttachment, UUID> {

    Optional<KnowledgeAttachment> findByIdAndKnowledgeIdAndKnowledgeOwnerId(
            UUID id,
            Long knowledgeId,
            UUID ownerId
    );

    Optional<KnowledgeAttachment> findByIdAndKnowledgeSlugAndKnowledgeVisibility(
            UUID id,
            String slug,
            Visibility visibility
    );

    Optional<KnowledgeAttachment> findByIdAndKnowledgeShareTokenAndKnowledgeVisibility(
            UUID id,
            String shareToken,
            Visibility visibility
    );
}
