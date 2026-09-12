package com.knowledgeapplication.api.knowledge.repository;

import com.knowledgeapplication.api.knowledge.model.KnowledgeCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgeCollectionRepository extends JpaRepository<KnowledgeCollection, Long> {

    Optional<KnowledgeCollection> findByOwnerIdAndNormalizedName(UUID ownerId, String normalizedName);
}
