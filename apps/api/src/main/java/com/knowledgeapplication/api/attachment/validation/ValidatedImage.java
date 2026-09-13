package com.knowledgeapplication.api.attachment.validation;

public record ValidatedImage(
        byte[] content,
        String contentType,
        int width,
        int height
) {
}
