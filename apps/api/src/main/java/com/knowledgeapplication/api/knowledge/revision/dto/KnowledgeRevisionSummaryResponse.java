package com.knowledgeapplication.api.knowledge.revision.dto;

import com.knowledgeapplication.api.knowledge.revision.model.KnowledgeRevisionReason;

import java.time.Instant;

public record KnowledgeRevisionSummaryResponse(
        Long id,
        Instant createdAt,
        KnowledgeRevisionReason reason,
        String title,
        String summaryExcerpt
) {
}
