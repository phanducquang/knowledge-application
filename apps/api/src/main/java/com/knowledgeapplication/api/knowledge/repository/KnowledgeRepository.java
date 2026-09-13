package com.knowledgeapplication.api.knowledge.repository;

import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from Knowledge k where k.id = :id")
    Optional<Knowledge> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k.id from Knowledge k where k.id = :id and k.ownerId = :ownerId")
    Optional<Long> lockIdByIdAndOwnerId(
            @Param("id") Long id,
            @Param("ownerId") UUID ownerId
    );

    boolean existsBySlug(String slug);

    boolean existsByShareToken(String shareToken);
}
