package com.knowledgeapplication.api.knowledge.sharedview;

import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharedKnowledgeService {

    private final KnowledgeRepository repository;

    public SharedKnowledgeService(KnowledgeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Knowledge getByShareToken(String shareToken) {
        return repository.findByShareTokenAndVisibility(shareToken, Visibility.UNLISTED)
                .orElseThrow(SharedKnowledgeNotFoundException::new);
    }
}
