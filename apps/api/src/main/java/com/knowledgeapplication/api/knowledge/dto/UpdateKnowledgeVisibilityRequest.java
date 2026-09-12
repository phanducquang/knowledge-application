package com.knowledgeapplication.api.knowledge.dto;

import com.knowledgeapplication.api.knowledge.model.Visibility;
import jakarta.validation.constraints.NotNull;

public record UpdateKnowledgeVisibilityRequest(
        @NotNull(message = "Visibility is required")
        Visibility visibility
) {
}
