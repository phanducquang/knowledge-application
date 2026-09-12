package com.knowledgeapplication.api.knowledge.controller;

import com.knowledgeapplication.api.knowledge.dto.CreateKnowledgeRequest;
import com.knowledgeapplication.api.knowledge.dto.KnowledgeResponse;
import com.knowledgeapplication.api.knowledge.dto.UpdateKnowledgeRequest;
import com.knowledgeapplication.api.knowledge.dto.UpdateKnowledgeVisibilityRequest;
import com.knowledgeapplication.api.knowledge.dto.UnlistedLinkResponse;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<KnowledgeResponse> create(@Valid @RequestBody CreateKnowledgeRequest request) {
        Knowledge created = service.create(
                request.title(),
                request.summary(),
                request.content(),
                request.visibility(),
                request.collection(),
                request.tags()
        );
        return ResponseEntity
                .created(URI.create("/api/knowledge/" + created.getId()))
                .body(toResponse(created));
    }

    @GetMapping
    public List<KnowledgeResponse> list() {
        return service.list().stream().map(KnowledgeController::toResponse).toList();
    }

    @GetMapping("/{id}")
    public KnowledgeResponse getById(@PathVariable Long id) {
        return toResponse(service.getById(id));
    }

    @GetMapping("/slug/{slug}")
    public KnowledgeResponse getBySlug(@PathVariable String slug) {
        return toResponse(service.getBySlug(slug));
    }

    @PutMapping("/{id}")
    public KnowledgeResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateKnowledgeRequest request
    ) {
        return toResponse(service.update(
                id,
                request.title(),
                request.summary(),
                request.content(),
                request.visibility(),
                request.collection(),
                request.tags()
        ));
    }

    @PatchMapping("/{id}/visibility")
    public KnowledgeResponse updateVisibility(
            @PathVariable Long id,
            @Valid @RequestBody UpdateKnowledgeVisibilityRequest request
    ) {
        return toResponse(service.updateVisibility(id, request.visibility()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/unlisted-link")
    public UnlistedLinkResponse getUnlistedLink(@PathVariable Long id) {
        return toUnlistedLinkResponse(service.getUnlistedLink(id));
    }

    @PostMapping("/{id}/unlisted-link/regenerate")
    public UnlistedLinkResponse regenerateUnlistedLink(@PathVariable Long id) {
        return toUnlistedLinkResponse(service.regenerateUnlistedLink(id));
    }

    private static UnlistedLinkResponse toUnlistedLinkResponse(Knowledge knowledge) {
        return new UnlistedLinkResponse(
                knowledge.getShareToken(),
                "/s/" + knowledge.getShareToken(),
                knowledge.getShareTokenCreatedAt()
        );
    }

    private static KnowledgeResponse toResponse(Knowledge knowledge) {
        return KnowledgeResponse.from(knowledge);
    }
}
