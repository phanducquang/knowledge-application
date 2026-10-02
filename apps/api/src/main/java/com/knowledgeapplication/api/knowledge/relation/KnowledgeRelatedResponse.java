package com.knowledgeapplication.api.knowledge.relation;

import java.util.List;

public record KnowledgeRelatedResponse(
        Long id,
        String slug,
        String title,
        String summary,
        List<KnowledgeRelationReason> reasons
) {
}
