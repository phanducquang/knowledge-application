package com.knowledgeapplication.api.knowledge.revision.service;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import com.knowledgeapplication.api.knowledge.revision.model.KnowledgeRevision;
import com.knowledgeapplication.api.knowledge.revision.model.KnowledgeRevisionReason;
import com.knowledgeapplication.api.knowledge.revision.repository.KnowledgeRevisionRepository;
import com.knowledgeapplication.api.knowledge.service.KnowledgeNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class KnowledgeRevisionService {

    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final KnowledgeRevisionRepository revisionRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final CurrentOwner currentOwner;
    private final Clock clock;
    private final Duration checkpointInterval;

    public KnowledgeRevisionService(
            KnowledgeRevisionRepository revisionRepository,
            KnowledgeRepository knowledgeRepository,
            CurrentOwner currentOwner,
            Clock clock,
            @Value("${app.knowledge.revision-checkpoint-interval}") Duration checkpointInterval
    ) {
        if (checkpointInterval.isNegative()) {
            throw new IllegalArgumentException("Revision checkpoint interval must not be negative");
        }
        this.revisionRepository = revisionRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.currentOwner = currentOwner;
        this.clock = clock;
        this.checkpointInterval = checkpointInterval;
    }

    @Transactional
    public KnowledgeRevision createInitial(Knowledge knowledge) {
        return saveSnapshot(knowledge, KnowledgeRevisionReason.CREATE, clock.instant());
    }

    @Transactional
    public KnowledgeRevision checkpointIfDue(Knowledge knowledge) {
        Instant now = clock.instant();
        var latest = revisionRepository.findFirstByKnowledgeIdOrderByCreatedAtDescIdDesc(knowledge.getId());
        if (latest.isPresent()) {
            KnowledgeRevision previous = latest.get();
            if (now.isBefore(previous.getCreatedAt().plus(checkpointInterval))
                    || previous.hasSameAuthoringState(knowledge)) {
                return null;
            }
        }
        return saveSnapshot(knowledge, KnowledgeRevisionReason.CHECKPOINT, now);
    }

    @Transactional
    public KnowledgeRevision snapshotBeforeRestore(Knowledge knowledge) {
        return saveSnapshot(knowledge, KnowledgeRevisionReason.BEFORE_RESTORE, clock.instant());
    }

    @Transactional(readOnly = true)
    public Slice<KnowledgeRevision> list(Long knowledgeId, int page, int size) {
        requireOwnedKnowledge(knowledgeId);
        return revisionRepository.findAllByKnowledgeId(
                knowledgeId,
                PageRequest.of(page, size, NEWEST_FIRST)
        );
    }

    @Transactional(readOnly = true)
    public KnowledgeRevision get(Long knowledgeId, Long revisionId) {
        requireOwnedKnowledge(knowledgeId);
        return revisionRepository.findByIdAndKnowledgeId(revisionId, knowledgeId)
                .orElseThrow(KnowledgeRevisionNotFoundException::new);
    }

    private Knowledge requireOwnedKnowledge(Long knowledgeId) {
        return knowledgeRepository.findByIdAndOwnerId(knowledgeId, currentOwner.id())
                .orElseThrow(KnowledgeNotFoundException::new);
    }

    private KnowledgeRevision saveSnapshot(
            Knowledge knowledge,
            KnowledgeRevisionReason reason,
            Instant createdAt
    ) {
        return revisionRepository.save(KnowledgeRevision.snapshot(knowledge, reason, createdAt));
    }
}
