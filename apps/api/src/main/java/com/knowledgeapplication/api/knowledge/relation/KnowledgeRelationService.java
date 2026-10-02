package com.knowledgeapplication.api.knowledge.relation;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Tag;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import com.knowledgeapplication.api.knowledge.service.KnowledgeNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Transactional(readOnly = true)
    public List<KnowledgeRelatedResponse> related(Long targetId, int limit) {
        UUID ownerId = currentOwner.id();
        var target = knowledge.findByIdAndOwnerId(targetId, ownerId)
                .orElseThrow(KnowledgeNotFoundException::new);
        Set<String> outgoing = WikiLinkExtractor.slugs(target.getContent());
        Set<Long> tagIds = target.getTags().stream().map(Tag::getId).collect(Collectors.toSet());

        // The existing entity graph fetches current tags/collection in one owner-scoped read.
        // One candidate per Knowledge merges every signal without a separate edge index.
        return knowledge.findAllByOwnerIdOrderByUpdatedAtDescIdDesc(ownerId).stream()
                .filter(candidate -> !candidate.getId().equals(targetId))
                .map(candidate -> discover(target, candidate, outgoing, tagIds))
                .filter(candidate -> !candidate.reasons().isEmpty())
                .sorted(Comparator.comparingInt(RelatedCandidate::tier)
                        .thenComparing(Comparator.comparingLong(RelatedCandidate::sharedTags).reversed())
                        .thenComparing(candidate -> candidate.note().getUpdatedAt(), Comparator.reverseOrder())
                        .thenComparing(candidate -> candidate.note().getId(), Comparator.reverseOrder()))
                .limit(limit)
                .map(RelatedCandidate::response)
                .toList();
    }

    private static RelatedCandidate discover(Knowledge target, Knowledge candidate,
                                              Set<String> outgoing, Set<Long> tagIds) {
        var reasons = EnumSet.noneOf(KnowledgeRelationReason.class);
        if (outgoing.contains(candidate.getSlug())) {
            reasons.add(KnowledgeRelationReason.WIKI_LINK);
        }
        if (WikiLinkExtractor.slugs(candidate.getContent()).contains(target.getSlug())) {
            reasons.add(KnowledgeRelationReason.BACKLINK);
        }
        long sharedTags = candidate.getTags().stream().map(Tag::getId).filter(tagIds::contains).count();
        if (sharedTags > 0) {
            reasons.add(KnowledgeRelationReason.SHARED_TAG);
        }
        if (target.getCollection() != null && candidate.getCollection() != null
                && target.getCollection().getId().equals(candidate.getCollection().getId())) {
            reasons.add(KnowledgeRelationReason.SAME_COLLECTION);
        }
        return new RelatedCandidate(candidate, reasons, sharedTags);
    }

    private record RelatedCandidate(Knowledge note, EnumSet<KnowledgeRelationReason> reasons, long sharedTags) {
        int tier() {
            if (reasons.contains(KnowledgeRelationReason.WIKI_LINK)
                    || reasons.contains(KnowledgeRelationReason.BACKLINK)) {
                return 0;
            }
            return reasons.contains(KnowledgeRelationReason.SHARED_TAG) ? 1 : 2;
        }

        KnowledgeRelatedResponse response() {
            return new KnowledgeRelatedResponse(note.getId(), note.getSlug(), note.getTitle(),
                    note.getSummary(), List.copyOf(reasons));
        }
    }
}
