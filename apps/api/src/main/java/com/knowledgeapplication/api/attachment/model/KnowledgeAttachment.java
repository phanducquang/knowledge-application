package com.knowledgeapplication.api.attachment.model;

import com.knowledgeapplication.api.knowledge.model.Knowledge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "knowledge_attachment")
public class KnowledgeAttachment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "knowledge_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_knowledge_attachment_knowledge")
    )
    private Knowledge knowledge;

    @Column(name = "object_key", nullable = false, updatable = false, length = 500, unique = true)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, updatable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected KnowledgeAttachment() {
    }

    private KnowledgeAttachment(
            UUID id,
            Knowledge knowledge,
            String objectKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.knowledge = Objects.requireNonNull(knowledge, "knowledge must not be null");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey must not be null");
        this.originalFilename = Objects.requireNonNull(originalFilename, "originalFilename must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        this.sizeBytes = sizeBytes;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static KnowledgeAttachment create(
            UUID id,
            Knowledge knowledge,
            String objectKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            Instant createdAt
    ) {
        return new KnowledgeAttachment(
                id, knowledge, objectKey, originalFilename, contentType, sizeBytes, createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
