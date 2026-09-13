package com.knowledgeapplication.api.attachment.service;

public class SharedAttachmentNotFoundException extends RuntimeException {

    public SharedAttachmentNotFoundException() {
        super("Shared image not found");
    }
}
