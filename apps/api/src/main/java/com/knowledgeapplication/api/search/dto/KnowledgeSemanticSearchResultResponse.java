package com.knowledgeapplication.api.search.dto;

import com.knowledgeapplication.api.knowledge.model.Visibility;
import java.time.Instant;
import java.util.List;

public record KnowledgeSemanticSearchResultResponse(long id, String title, String slug, String summary,
        Visibility visibility, String collection, List<String> tags, Instant updatedAt, Match match) {
    public record Match(int chunkIndex, String text) {}
}
