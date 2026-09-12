package com.knowledgeapplication.api.knowledge.publicview;

import java.time.Instant;
import java.util.List;

public record PublicKnowledgeResponse(
        String title,
        String slug,
        String summary,
        String content,
        String collection,
        List<String> tags,
        Instant publishedAt,
        Instant updatedAt
) {
}
