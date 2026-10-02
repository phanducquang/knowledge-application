package com.knowledgeapplication.api.knowledge.collection;

import com.knowledgeapplication.api.knowledge.dto.KnowledgeResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/collections")
public class CollectionController {

    private final CollectionService service;

    public CollectionController(CollectionService service) {
        this.service = service;
    }

    @GetMapping
    public List<CollectionResponse> list() {
        return service.list().stream().map(CollectionResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<CollectionResponse> create(@Valid @RequestBody CollectionNameRequest request) {
        CollectionResponse created = CollectionResponse.from(service.create(request.name()));
        return ResponseEntity.created(URI.create("/api/collections/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public CollectionResponse get(@PathVariable Long id) {
        return CollectionResponse.from(service.get(id));
    }

    @GetMapping("/{id}/knowledge")
    public List<KnowledgeResponse> listKnowledge(@PathVariable Long id) {
        return service.listKnowledge(id).stream().map(KnowledgeResponse::from).toList();
    }

    @PutMapping("/{id}")
    public CollectionResponse rename(@PathVariable Long id, @Valid @RequestBody CollectionNameRequest request) {
        return CollectionResponse.from(service.rename(id, request.name()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
