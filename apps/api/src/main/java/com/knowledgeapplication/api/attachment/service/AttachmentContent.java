package com.knowledgeapplication.api.attachment.service;

import java.io.InputStream;

public record AttachmentContent(
        InputStream content,
        String contentType,
        long contentLength,
        String originalFilename
) {
}
