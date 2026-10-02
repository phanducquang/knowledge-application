package com.knowledgeapplication.api.knowledge.repository;

import com.knowledgeapplication.api.knowledge.model.KnowledgeCollection;
import com.knowledgeapplication.api.knowledge.collection.CollectionSummary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeCollectionRepository extends JpaRepository<KnowledgeCollection, Long> {

    Optional<KnowledgeCollection> findByOwnerIdAndNormalizedName(UUID ownerId, String normalizedName);

    Optional<KnowledgeCollection> findByIdAndOwnerId(Long id, UUID ownerId);

    boolean existsByOwnerIdAndNormalizedNameAndIdNot(UUID ownerId, String normalizedName, Long id);

    boolean existsByOwnerIdAndNormalizedName(UUID ownerId, String normalizedName);

    @Query("""
            select new com.knowledgeapplication.api.knowledge.collection.CollectionSummary(c.id, c.name, count(k.id))
            from KnowledgeCollection c
            left join Knowledge k on k.collection = c and k.ownerId = :ownerId
            where c.ownerId = :ownerId
            group by c.id, c.name
            order by lower(c.name), c.id
            """)
    List<CollectionSummary> findSummariesByOwnerId(@Param("ownerId") UUID ownerId);
}
