package com.knowledgeapplication.api.search.dto;

import com.knowledgeapplication.api.knowledge.model.Visibility;

import java.time.Instant;
import java.util.List;

public record KnowledgeSearchResultResponse(
        Long id,
        String title,
        String slug,
        String summary,
        Visibility visibility,
        String collection,
        List<String> tags,
        Instant updatedAt
) {
}
