package com.knowledgeapplication.api.knowledge.relation;

import com.knowledgeapplication.api.knowledge.model.Knowledge;

import java.time.Instant;

public record KnowledgeBacklinkResponse(Long id, String title, String slug, Instant updatedAt) {

    public static KnowledgeBacklinkResponse from(Knowledge knowledge) {
        return new KnowledgeBacklinkResponse(
                knowledge.getId(), knowledge.getTitle(), knowledge.getSlug(), knowledge.getUpdatedAt());
    }
}
