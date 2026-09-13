package com.knowledgeapplication.api.knowledge.revision.repository;

import com.knowledgeapplication.api.knowledge.revision.model.KnowledgeRevision;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KnowledgeRevisionRepository extends JpaRepository<KnowledgeRevision, Long> {

    Optional<KnowledgeRevision> findFirstByKnowledgeIdOrderByCreatedAtDescIdDesc(Long knowledgeId);

    Slice<KnowledgeRevision> findAllByKnowledgeId(Long knowledgeId, Pageable pageable);

    Optional<KnowledgeRevision> findByIdAndKnowledgeId(Long id, Long knowledgeId);

    boolean existsByKnowledgeIdAndContentContainingIgnoreCase(Long knowledgeId, String reference);
}
