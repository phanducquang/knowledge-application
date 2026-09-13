package com.knowledgeapplication.api.attachment.service;

public class AttachmentPersistenceException extends RuntimeException {

    public AttachmentPersistenceException(Throwable cause) {
        super("Image metadata could not be saved", cause);
    }
}
