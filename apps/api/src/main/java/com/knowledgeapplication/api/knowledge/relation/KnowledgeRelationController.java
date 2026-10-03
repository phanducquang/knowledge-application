package com.knowledgeapplication.api.knowledge.relation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
public class KnowledgeRelationController {

    private final KnowledgeRelationService service;

    public KnowledgeRelationController(KnowledgeRelationService service) {
        this.service = service;
    }

    @GetMapping("/api/knowledge/graph")
    public KnowledgeGraphResponse graph() {
        return service.graph();
    }

    @GetMapping("/api/knowledge/{id}/backlinks")
    public List<KnowledgeBacklinkResponse> backlinks(@PathVariable Long id) {
        return service.backlinks(id);
    }

    @GetMapping("/api/knowledge/{id}/related")
    public List<KnowledgeRelatedResponse> related(@PathVariable Long id,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        return service.related(id, limit);
    }
}
