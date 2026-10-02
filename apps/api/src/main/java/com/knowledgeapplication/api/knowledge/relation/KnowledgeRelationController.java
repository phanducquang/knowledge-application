package com.knowledgeapplication.api.knowledge.relation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class KnowledgeRelationController {

    private final KnowledgeRelationService service;

    public KnowledgeRelationController(KnowledgeRelationService service) {
        this.service = service;
    }

    @GetMapping("/api/knowledge/{id}/backlinks")
    public List<KnowledgeBacklinkResponse> backlinks(@PathVariable Long id) {
        return service.backlinks(id);
    }
}
