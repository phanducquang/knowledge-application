package com.knowledgeapplication.api.knowledge.revision.dto;

import java.util.List;

public record KnowledgeRevisionPageResponse(
        List<KnowledgeRevisionSummaryResponse> items,
        int page,
        int size,
        boolean hasMore
) {
}
