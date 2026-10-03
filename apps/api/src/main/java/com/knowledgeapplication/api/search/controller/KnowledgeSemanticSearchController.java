package com.knowledgeapplication.api.search.controller;

import com.knowledgeapplication.api.search.dto.KnowledgeSemanticSearchResultResponse;
import com.knowledgeapplication.api.search.service.KnowledgeSemanticSearchService;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/search/knowledge/semantic")
public class KnowledgeSemanticSearchController {
    private final KnowledgeSemanticSearchService service;
    public KnowledgeSemanticSearchController(KnowledgeSemanticSearchService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<KnowledgeSemanticSearchResultResponse>> search(
            @RequestParam @NotBlank(message = "Query must not be blank")
            @Size(max = 200, message = "Query must not exceed 200 characters") String q,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        var results = service.search(q, limit).stream().map(note -> new KnowledgeSemanticSearchResultResponse(
                note.id(), note.title(), note.slug(), note.summary(), note.visibility(), note.collection(), note.tags(), note.updatedAt(),
                new KnowledgeSemanticSearchResultResponse.Match(note.chunkIndex(), KnowledgeSemanticSearchService.excerpt(note.chunkText())))).toList();
        return ResponseEntity.ok().header("Cache-Control", "private, no-store, max-age=0").body(results);
    }
}
