package com.knowledgeapplication.api.knowledge.collection;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.KnowledgeCollection;
import com.knowledgeapplication.api.knowledge.model.MetadataNameNormalizer;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeCollectionRepository;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CollectionService {

    private final KnowledgeCollectionRepository collections;
    private final KnowledgeRepository knowledge;
    private final CurrentOwner currentOwner;

    public CollectionService(
            KnowledgeCollectionRepository collections,
            KnowledgeRepository knowledge,
            CurrentOwner currentOwner
    ) {
        this.collections = collections;
        this.knowledge = knowledge;
        this.currentOwner = currentOwner;
    }

    @Transactional(readOnly = true)
    public List<CollectionSummary> list() {
        return collections.findSummariesByOwnerId(currentOwner.id());
    }

    @Transactional(readOnly = true)
    public CollectionSummary get(Long id) {
        UUID ownerId = currentOwner.id();
        KnowledgeCollection collection = owned(id, ownerId);
        return summary(collection, ownerId);
    }

    @Transactional(readOnly = true)
    public List<Knowledge> listKnowledge(Long id) {
        UUID ownerId = currentOwner.id();
        owned(id, ownerId);
        return knowledge.findAllByOwnerIdAndCollectionIdOrderByUpdatedAtDescIdDesc(ownerId, id);
    }

    @Transactional
    public CollectionSummary create(String name) {
        UUID ownerId = currentOwner.id();
        String displayName = MetadataNameNormalizer.collectionDisplayName(name);
        if (collections.existsByOwnerIdAndNormalizedName(ownerId, MetadataNameNormalizer.key(displayName))) {
            throw new CollectionNameConflictException();
        }
        try {
            KnowledgeCollection created = collections.saveAndFlush(KnowledgeCollection.create(ownerId, displayName));
            return summary(created, ownerId);
        } catch (DataIntegrityViolationException exception) {
            throw new CollectionNameConflictException();
        }
    }

    @Transactional
    public CollectionSummary rename(Long id, String name) {
        UUID ownerId = currentOwner.id();
        KnowledgeCollection collection = owned(id, ownerId);
        String displayName = MetadataNameNormalizer.collectionDisplayName(name);
        if (collection.getName().equals(displayName)) {
            return summary(collection, ownerId);
        }
        if (collections.existsByOwnerIdAndNormalizedNameAndIdNot(
                ownerId, MetadataNameNormalizer.key(displayName), id)) {
            throw new CollectionNameConflictException();
        }
        collection.rename(displayName);
        try {
            collections.flush();
            return summary(collection, ownerId);
        } catch (DataIntegrityViolationException exception) {
            throw new CollectionNameConflictException();
        }
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeCollection collection = owned(id, currentOwner.id());
        // V2's ON DELETE SET NULL unassigns notes without deleting them. Revision
        // snapshots retain their denormalized historical collection names.
        collections.delete(collection);
    }

    private KnowledgeCollection owned(Long id, UUID ownerId) {
        return collections.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(CollectionNotFoundException::new);
    }

    private CollectionSummary summary(KnowledgeCollection collection, UUID ownerId) {
        return new CollectionSummary(collection.getId(), collection.getName(),
                knowledge.countByOwnerIdAndCollectionId(ownerId, collection.getId()));
    }
}
