package com.knowledgeapplication.api.attachment.service;

public class AttachmentNotFoundException extends RuntimeException {

    public AttachmentNotFoundException() {
        super("Image attachment not found");
    }
}
