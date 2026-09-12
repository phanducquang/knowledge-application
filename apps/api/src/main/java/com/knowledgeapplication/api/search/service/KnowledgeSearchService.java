package com.knowledgeapplication.api.search.service;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.search.model.KnowledgeSearchResult;
import com.knowledgeapplication.api.search.repository.KnowledgeSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeSearchService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    private final KnowledgeSearchRepository repository;
    private final CurrentOwner currentOwner;

    public KnowledgeSearchService(KnowledgeSearchRepository repository, CurrentOwner currentOwner) {
        this.repository = repository;
        this.currentOwner = currentOwner;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }

        return repository.search(currentOwner.id(), query.trim(), limit);
    }
}
