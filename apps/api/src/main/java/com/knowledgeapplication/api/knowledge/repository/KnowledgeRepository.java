package com.knowledgeapplication.api.knowledge.repository;

import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeRepository extends JpaRepository<Knowledge, Long> {

    @EntityGraph(attributePaths = {"collection", "tags"})
    Optional<Knowledge> findByIdAndOwnerId(Long id, UUID ownerId);

    @EntityGraph(attributePaths = {"collection", "tags"})
    Optional<Knowledge> findBySlugAndOwnerId(String slug, UUID ownerId);

    @EntityGraph(attributePaths = {"collection", "tags"})
    Optional<Knowledge> findBySlugAndVisibility(String slug, Visibility visibility);

    @EntityGraph(attributePaths = {"collection", "tags"})
    Optional<Knowledge> findByShareTokenAndVisibility(String shareToken, Visibility visibility);

    @EntityGraph(attributePaths = {"collection", "tags"})
    List<Knowledge> findAllByOwnerIdOrderByUpdatedAtDescIdDesc(UUID ownerId);

    boolean existsBySlug(String slug);

    boolean existsByShareToken(String shareToken);
}
