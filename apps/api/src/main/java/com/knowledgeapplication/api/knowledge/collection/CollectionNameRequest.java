package com.knowledgeapplication.api.knowledge.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CollectionNameRequest(
        @NotBlank(message = "Collection name must not be blank")
        @Size(max = 100, message = "Collection name must not exceed 100 characters")
        String name
) {
}
