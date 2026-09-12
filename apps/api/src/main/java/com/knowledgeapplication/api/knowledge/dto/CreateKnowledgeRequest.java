package com.knowledgeapplication.api.knowledge.dto;

import com.knowledgeapplication.api.knowledge.model.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateKnowledgeRequest(
        @NotBlank(message = "Title must not be blank")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 2000, message = "Summary must not exceed 2000 characters")
        String summary,

        @NotNull(message = "Content is required")
        String content,

        Visibility visibility,

        @Size(max = 100, message = "Collection must not exceed 100 characters")
        @Pattern(regexp = ".*\\S.*", message = "Collection must not be blank")
        String collection,

        @Size(max = 20, message = "Knowledge must not have more than 20 tags")
        List<
                @NotBlank(message = "Tag must not be blank")
                @Size(max = 50, message = "Tag must not exceed 50 characters")
                @Pattern(regexp = "^(?=.*[^\\s#]).+$", message = "Tag must contain text after #")
                String> tags
) {
}
