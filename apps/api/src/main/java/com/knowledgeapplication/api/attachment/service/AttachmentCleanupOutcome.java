package com.knowledgeapplication.api.attachment.service;

public enum AttachmentCleanupOutcome {
    DELETED,
    RETAINED_BY_REFERENCE,
    NOT_DUE,
    MISSING
}
