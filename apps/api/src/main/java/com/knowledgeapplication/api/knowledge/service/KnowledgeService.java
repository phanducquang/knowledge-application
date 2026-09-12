package com.knowledgeapplication.api.knowledge.service;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.KnowledgeCollection;
import com.knowledgeapplication.api.knowledge.model.MetadataNameNormalizer;
import com.knowledgeapplication.api.knowledge.model.SlugGenerator;
import com.knowledgeapplication.api.knowledge.model.ShareTokenGenerator;
import com.knowledgeapplication.api.knowledge.model.Tag;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeCollectionRepository;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import com.knowledgeapplication.api.knowledge.repository.TagRepository;
import com.knowledgeapplication.api.knowledge.revision.model.KnowledgeRevision;
import com.knowledgeapplication.api.knowledge.revision.service.KnowledgeRevisionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class KnowledgeService {

    private final KnowledgeRepository repository;
    private final KnowledgeCollectionRepository collectionRepository;
    private final TagRepository tagRepository;
    private final CurrentOwner currentOwner;
    private final ShareTokenGenerator shareTokenGenerator;
    private final KnowledgeRevisionService revisionService;

    public KnowledgeService(
            KnowledgeRepository repository,
            KnowledgeCollectionRepository collectionRepository,
            TagRepository tagRepository,
            CurrentOwner currentOwner,
            ShareTokenGenerator shareTokenGenerator,
            KnowledgeRevisionService revisionService
    ) {
        this.repository = repository;
        this.collectionRepository = collectionRepository;
        this.tagRepository = tagRepository;
        this.currentOwner = currentOwner;
        this.shareTokenGenerator = shareTokenGenerator;
        this.revisionService = revisionService;
    }

    @Transactional
    public Knowledge create(
            String title,
            String summary,
            String content,
            Visibility requestedVisibility,
            String collectionName,
            List<String> tagNames
    ) {
        UUID ownerId = currentOwner.id();
        Visibility visibility = requestedVisibility == null ? Visibility.PRIVATE : requestedVisibility;
        String slug = nextAvailableSlug(title);
        Knowledge knowledge = Knowledge.create(
                ownerId,
                title,
                slug,
                summary,
                content,
                visibility
        );
        ensureUnlistedToken(knowledge);
        knowledge.replaceMetadata(
                resolveCollection(ownerId, collectionName),
                resolveTags(ownerId, tagNames == null ? List.of() : tagNames)
        );
        Knowledge created = repository.save(knowledge);
        revisionService.createInitial(created);
        return created;
    }

    @Transactional(readOnly = true)
    public List<Knowledge> list() {
        return repository.findAllByOwnerIdOrderByUpdatedAtDescIdDesc(currentOwner.id());
    }

    @Transactional(readOnly = true)
    public Knowledge getById(Long id) {
        return repository.findByIdAndOwnerId(id, currentOwner.id())
                .orElseThrow(KnowledgeNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Knowledge getBySlug(String slug) {
        return repository.findBySlugAndOwnerId(slug, currentOwner.id())
                .orElseThrow(KnowledgeNotFoundException::new);
    }

    @Transactional
    public Knowledge update(
            Long id,
            String title,
            String summary,
            String content,
            Visibility visibility,
            String collectionName,
            List<String> tagNames
    ) {
        Knowledge knowledge = getById(id);
        UUID ownerId = currentOwner.id();
        KnowledgeCollection collection = resolveCollection(ownerId, collectionName);
        Set<Tag> tags = resolveTags(ownerId, tagNames == null ? List.of() : tagNames);
        if (authoringChanged(knowledge, title, summary, content, collection, tags)) {
            revisionService.checkpointIfDue(knowledge);
        }
        knowledge.rename(title);
        knowledge.updateSummary(summary);
        knowledge.updateContent(content);
        applyVisibility(knowledge, visibility);
        knowledge.replaceMetadata(collection, tags);
        return knowledge;
    }

    @Transactional
    public Knowledge updateVisibility(Long id, Visibility visibility) {
        Knowledge knowledge = getById(id);
        applyVisibility(knowledge, visibility);
        return knowledge;
    }

    @Transactional
    public void delete(Long id) {
        Knowledge knowledge = getById(id);
        repository.delete(knowledge);
    }

    @Transactional(readOnly = true)
    public Knowledge getUnlistedLink(Long id) {
        Knowledge knowledge = getById(id);
        if (knowledge.getShareToken() == null) {
            throw new UnlistedLinkNotFoundException();
        }
        return knowledge;
    }

    @Transactional
    public Knowledge regenerateUnlistedLink(Long id) {
        Knowledge knowledge = getById(id);
        replaceShareToken(knowledge);
        return knowledge;
    }

    @Transactional
    public Knowledge restoreRevision(Long knowledgeId, Long revisionId) {
        Knowledge knowledge = getById(knowledgeId);
        KnowledgeRevision revision = revisionService.get(knowledgeId, revisionId);
        UUID ownerId = currentOwner.id();
        KnowledgeCollection collection = resolveCollection(ownerId, revision.getCollectionName());
        Set<Tag> tags = resolveTags(ownerId, revision.getTags());

        revisionService.snapshotBeforeRestore(knowledge);
        knowledge.rename(revision.getTitle());
        knowledge.updateSummary(revision.getSummary());
        knowledge.updateContent(revision.getContent());
        knowledge.replaceMetadata(collection, tags);
        return knowledge;
    }

    private String nextAvailableSlug(String title) {
        String baseSlug = SlugGenerator.fromTitle(title);
        String candidate = baseSlug;
        int suffix = 2;

        while (repository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + suffix;
            suffix++;
        }

        return candidate;
    }

    private void ensureUnlistedToken(Knowledge knowledge) {
        if (knowledge.getVisibility() == Visibility.UNLISTED && knowledge.getShareToken() == null) {
            replaceShareToken(knowledge);
        }
    }

    private void applyVisibility(Knowledge knowledge, Visibility visibility) {
        knowledge.changeVisibility(visibility);
        ensureUnlistedToken(knowledge);
    }

    private void replaceShareToken(Knowledge knowledge) {
        String candidate;
        do {
            candidate = shareTokenGenerator.generate();
        } while (candidate.equals(knowledge.getShareToken()) || repository.existsByShareToken(candidate));
        knowledge.replaceShareToken(candidate);
    }

    private static boolean authoringChanged(
            Knowledge knowledge,
            String title,
            String summary,
            String content,
            KnowledgeCollection collection,
            Set<Tag> tags
    ) {
        String currentCollection = knowledge.getCollection() == null
                ? null
                : knowledge.getCollection().getName();
        String requestedCollection = collection == null ? null : collection.getName();
        List<String> currentTags = sortedTagNames(knowledge.getTags());
        List<String> requestedTags = sortedTagNames(tags);
        return !knowledge.getTitle().equals(title.trim())
                || !java.util.Objects.equals(knowledge.getSummary(), summary)
                || !knowledge.getContent().equals(content)
                || !java.util.Objects.equals(currentCollection, requestedCollection)
                || !currentTags.equals(requestedTags);
    }

    private static List<String> sortedTagNames(Set<Tag> tags) {
        return tags.stream()
                .map(Tag::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo))
                .toList();
    }

    private KnowledgeCollection resolveCollection(UUID ownerId, String requestedName) {
        if (requestedName == null) {
            return null;
        }

        String displayName = MetadataNameNormalizer.collectionDisplayName(requestedName);
        String normalizedName = MetadataNameNormalizer.key(displayName);
        return collectionRepository.findByOwnerIdAndNormalizedName(ownerId, normalizedName)
                .orElseGet(() -> collectionRepository.save(KnowledgeCollection.create(ownerId, displayName)));
    }

    private Set<Tag> resolveTags(UUID ownerId, List<String> requestedNames) {
        Map<String, String> requestedByNormalizedName = new LinkedHashMap<>();
        for (String requestedName : requestedNames) {
            String displayName = MetadataNameNormalizer.tagDisplayName(requestedName);
            requestedByNormalizedName.putIfAbsent(MetadataNameNormalizer.key(displayName), displayName);
        }

        if (requestedByNormalizedName.isEmpty()) {
            return Set.of();
        }

        Map<String, Tag> tagsByNormalizedName = new LinkedHashMap<>();
        tagRepository.findAllByOwnerIdAndNormalizedNameIn(ownerId, requestedByNormalizedName.keySet())
                .forEach(tag -> tagsByNormalizedName.put(MetadataNameNormalizer.key(tag.getName()), tag));

        List<Tag> missingTags = requestedByNormalizedName.entrySet().stream()
                .filter(entry -> !tagsByNormalizedName.containsKey(entry.getKey()))
                .map(entry -> Tag.create(ownerId, entry.getValue()))
                .toList();
        if (!missingTags.isEmpty()) {
            tagRepository.saveAll(missingTags)
                    .forEach(tag -> tagsByNormalizedName.put(MetadataNameNormalizer.key(tag.getName()), tag));
        }

        Set<Tag> resolved = new LinkedHashSet<>();
        requestedByNormalizedName.keySet().forEach(name -> resolved.add(tagsByNormalizedName.get(name)));
        return resolved;
    }
}
