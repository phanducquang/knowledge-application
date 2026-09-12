package com.knowledgeapplication.api.knowledge.dto;

import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.model.Knowledge;

import java.time.Instant;
import java.util.List;

public record KnowledgeResponse(
        Long id,
        String title,
        String slug,
        String summary,
        String content,
        Visibility visibility,
        String collection,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt
) {
    public static KnowledgeResponse from(Knowledge knowledge) {
        return new KnowledgeResponse(
                knowledge.getId(),
                knowledge.getTitle(),
                knowledge.getSlug(),
                knowledge.getSummary(),
                knowledge.getContent(),
                knowledge.getVisibility(),
                knowledge.getCollection() == null ? null : knowledge.getCollection().getName(),
                knowledge.getTags().stream()
                        .map(tag -> tag.getName())
                        .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo))
                        .toList(),
                knowledge.getCreatedAt(),
                knowledge.getUpdatedAt(),
                knowledge.getPublishedAt()
        );
    }
}
