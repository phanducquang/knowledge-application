package com.knowledgeapplication.api.search.controller;

import com.knowledgeapplication.api.search.dto.KnowledgeSearchResultResponse;
import com.knowledgeapplication.api.search.model.KnowledgeSearchResult;
import com.knowledgeapplication.api.search.service.KnowledgeSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search/knowledge")
public class KnowledgeSearchController {

    private final KnowledgeSearchService service;

    public KnowledgeSearchController(KnowledgeSearchService service) {
        this.service = service;
    }

    @GetMapping
    public List<KnowledgeSearchResultResponse> search(
            @RequestParam
            @NotBlank(message = "Query must not be blank")
            @Size(max = 200, message = "Query must not exceed 200 characters")
            String q,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit must not exceed 50")
            int limit
    ) {
        return service.search(q, limit).stream()
                .map(KnowledgeSearchController::toResponse)
                .toList();
    }

    private static KnowledgeSearchResultResponse toResponse(KnowledgeSearchResult result) {
        return new KnowledgeSearchResultResponse(
                result.id(),
                result.title(),
                result.slug(),
                result.summary(),
                result.visibility(),
                result.collection(),
                result.tags(),
                result.updatedAt()
        );
    }
}
