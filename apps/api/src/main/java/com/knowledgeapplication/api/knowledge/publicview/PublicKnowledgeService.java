package com.knowledgeapplication.api.knowledge.publicview;

import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicKnowledgeService {

    private final KnowledgeRepository repository;

    public PublicKnowledgeService(KnowledgeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Knowledge getBySlug(String slug) {
        return repository.findBySlugAndVisibility(slug, Visibility.PUBLIC)
                .orElseThrow(PublicKnowledgeNotFoundException::new);
    }
}
