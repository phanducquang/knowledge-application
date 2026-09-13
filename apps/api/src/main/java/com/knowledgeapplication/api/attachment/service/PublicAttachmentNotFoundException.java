package com.knowledgeapplication.api.attachment.service;

public class PublicAttachmentNotFoundException extends RuntimeException {

    public PublicAttachmentNotFoundException() {
        super("Public image not found");
    }
}
