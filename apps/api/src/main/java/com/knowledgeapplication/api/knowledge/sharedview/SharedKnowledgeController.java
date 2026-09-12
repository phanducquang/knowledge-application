package com.knowledgeapplication.api.knowledge.sharedview;

import com.knowledgeapplication.api.knowledge.model.Knowledge;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shared/knowledge")
public class SharedKnowledgeController {

    private final SharedKnowledgeService service;

    public SharedKnowledgeController(SharedKnowledgeService service) {
        this.service = service;
    }

    @GetMapping("/{shareToken}")
    public ResponseEntity<SharedKnowledgeResponse> getByShareToken(@PathVariable String shareToken) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                .body(toResponse(service.getByShareToken(shareToken)));
    }

    private static SharedKnowledgeResponse toResponse(Knowledge knowledge) {
        return new SharedKnowledgeResponse(
                knowledge.getTitle(),
                knowledge.getSlug(),
                knowledge.getSummary(),
                knowledge.getContent(),
                knowledge.getCollection() == null ? null : knowledge.getCollection().getName(),
                knowledge.getTags().stream()
                        .map(tag -> tag.getName())
                        .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo))
                        .toList(),
                knowledge.getPublishedAt(),
                knowledge.getUpdatedAt()
        );
    }
}
