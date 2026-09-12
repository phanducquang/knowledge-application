package com.knowledgeapplication.api.knowledge.publicview;

import com.knowledgeapplication.api.knowledge.model.Knowledge;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/knowledge")
public class PublicKnowledgeController {

    private final PublicKnowledgeService service;

    public PublicKnowledgeController(PublicKnowledgeService service) {
        this.service = service;
    }

    @GetMapping("/{slug}")
    public PublicKnowledgeResponse getBySlug(@PathVariable String slug) {
        return toResponse(service.getBySlug(slug));
    }

    private static PublicKnowledgeResponse toResponse(Knowledge knowledge) {
        return new PublicKnowledgeResponse(
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
