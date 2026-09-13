package com.knowledgeapplication.api.attachment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "knowledge_attachment_delete_queue")
public class AttachmentDeleteQueueEntry {

    @Id
    @Column(name = "object_key", nullable = false, updatable = false, length = 500)
    private String objectKey;

    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt;

    protected AttachmentDeleteQueueEntry() {
    }

    public String getObjectKey() {
        return objectKey;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }
}
