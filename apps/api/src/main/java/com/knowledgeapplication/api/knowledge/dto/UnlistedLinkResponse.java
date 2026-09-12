package com.knowledgeapplication.api.knowledge.dto;

import java.time.Instant;

public record UnlistedLinkResponse(
        String token,
        String path,
        Instant createdAt
) {
}
