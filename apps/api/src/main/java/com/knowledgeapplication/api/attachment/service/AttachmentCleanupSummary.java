package com.knowledgeapplication.api.attachment.service;

public record AttachmentCleanupSummary(
        int queuedObjectsDeleted,
        int orphanAttachmentsDeleted,
        int referencesRetained,
        int failures
) {
    public boolean hasActivity() {
        return queuedObjectsDeleted > 0 || orphanAttachmentsDeleted > 0 || referencesRetained > 0 || failures > 0;
    }
}
