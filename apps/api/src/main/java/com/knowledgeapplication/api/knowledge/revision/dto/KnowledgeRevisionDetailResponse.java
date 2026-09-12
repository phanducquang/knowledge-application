package com.knowledgeapplication.api.knowledge.revision.dto;

import com.knowledgeapplication.api.knowledge.revision.model.KnowledgeRevisionReason;

import java.time.Instant;
import java.util.List;

public record KnowledgeRevisionDetailResponse(
        Long id,
        String title,
        String summary,
        String content,
        String collection,
        List<String> tags,
        KnowledgeRevisionReason reason,
        Instant createdAt
) {
}
