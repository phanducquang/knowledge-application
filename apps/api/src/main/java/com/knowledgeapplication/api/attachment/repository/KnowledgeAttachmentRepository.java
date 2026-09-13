package com.knowledgeapplication.api.attachment.repository;

import com.knowledgeapplication.api.attachment.model.KnowledgeAttachment;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface KnowledgeAttachmentRepository extends JpaRepository<KnowledgeAttachment, UUID> {

    Optional<KnowledgeAttachment> findByIdAndKnowledgeIdAndKnowledgeOwnerId(
            UUID id,
            Long knowledgeId,
            UUID ownerId
    );

    Optional<KnowledgeAttachment> findByIdAndKnowledgeSlugAndKnowledgeVisibility(
            UUID id,
            String slug,
            Visibility visibility
    );

    Optional<KnowledgeAttachment> findByIdAndKnowledgeShareTokenAndKnowledgeVisibility(
            UUID id,
            String shareToken,
            Visibility visibility
    );

    @Query("select a.knowledge.id from KnowledgeAttachment a where a.id = :id")
    Optional<Long> findKnowledgeIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from KnowledgeAttachment a where a.id = :id")
    Optional<KnowledgeAttachment> findByIdForCleanup(@Param("id") UUID id);

    @Query("""
            select a.id from KnowledgeAttachment a
            where a.orphanedAt is not null and a.orphanedAt <= :cutoff
            order by a.orphanedAt asc, a.id asc
            """)
    List<UUID> findCleanupCandidateIds(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Modifying
    @Query("""
            update KnowledgeAttachment a set a.orphanedAt = null
            where a.knowledge.id = :knowledgeId and a.id in :ids
            """)
    int markReferenced(
            @Param("knowledgeId") Long knowledgeId,
            @Param("ids") Set<UUID> ids
    );

    @Modifying
    @Query("""
            update KnowledgeAttachment a set a.orphanedAt = :orphanedAt
            where a.knowledge.id = :knowledgeId and a.id in :ids
            """)
    int markOrphaned(
            @Param("knowledgeId") Long knowledgeId,
            @Param("ids") Set<UUID> ids,
            @Param("orphanedAt") Instant orphanedAt
    );

    @Modifying
    @Query(value = """
            INSERT INTO knowledge_attachment_delete_queue (object_key, queued_at)
            SELECT object_key, :queuedAt
            FROM knowledge_attachment
            WHERE knowledge_id = :knowledgeId
            ON CONFLICT (object_key) DO NOTHING
            """, nativeQuery = true)
    int enqueueKnowledgeDeletion(
            @Param("knowledgeId") Long knowledgeId,
            @Param("queuedAt") Instant queuedAt
    );
}
