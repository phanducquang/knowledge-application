package com.knowledgeapplication.api.search.model;

import com.knowledgeapplication.api.knowledge.model.Visibility;

import java.time.Instant;
import java.util.List;

public record KnowledgeSearchResult(
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
