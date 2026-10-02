package com.knowledgeapplication.api.knowledge.relation;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import com.knowledgeapplication.api.knowledge.service.KnowledgeNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeRelationService {

    private final KnowledgeRepository knowledge;
    private final CurrentOwner currentOwner;

    public KnowledgeRelationService(KnowledgeRepository knowledge, CurrentOwner currentOwner) {
        this.knowledge = knowledge;
        this.currentOwner = currentOwner;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBacklinkResponse> backlinks(Long targetId) {
        UUID ownerId = currentOwner.id();
        var target = knowledge.findByIdAndOwnerId(targetId, ownerId)
                .orElseThrow(KnowledgeNotFoundException::new);
        String slug = target.getSlug();
        return knowledge.findAllByOwnerIdAndContentContainingOrderByUpdatedAtDescIdDesc(
                        ownerId, "[[" + slug + "]]").stream()
                .filter(source -> !source.getId().equals(targetId))
                .filter(source -> WikiLinkExtractor.slugs(source.getContent()).contains(slug))
                .map(KnowledgeBacklinkResponse::from)
                .toList();
    }
}
