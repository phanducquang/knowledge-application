package com.knowledgeapplication.api.attachment.dto;

import java.time.Instant;
import java.util.UUID;

public record ImageAttachmentResponse(
        UUID id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant createdAt,
        String markdownSource
) {
}
