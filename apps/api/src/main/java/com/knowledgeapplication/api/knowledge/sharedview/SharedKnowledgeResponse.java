package com.knowledgeapplication.api.knowledge.sharedview;

import java.time.Instant;
import java.util.List;

public record SharedKnowledgeResponse(
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
