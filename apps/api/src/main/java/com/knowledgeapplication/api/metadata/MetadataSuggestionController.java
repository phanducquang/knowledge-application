package com.knowledgeapplication.api.metadata;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Positive;

@RestController
public class MetadataSuggestionController {
    private final MetadataSuggestionService service;
    public MetadataSuggestionController(MetadataSuggestionService service) { this.service=service; }
    @PostMapping("/api/knowledge/{id}/ai/metadata-suggestions")
    public ResponseEntity<MetadataSuggestion> suggest(@PathVariable @Positive Long id) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store, max-age=0").body(service.suggest(id));
    }
}
