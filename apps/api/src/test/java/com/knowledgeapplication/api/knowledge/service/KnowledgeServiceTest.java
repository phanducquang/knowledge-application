package com.knowledgeapplication.api.knowledge.service;

import com.knowledgeapplication.api.attachment.service.KnowledgeAttachmentLifecycleService;
import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.KnowledgeCollection;
import com.knowledgeapplication.api.knowledge.model.Tag;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.model.ShareTokenGenerator;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeCollectionRepository;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import com.knowledgeapplication.api.knowledge.repository.TagRepository;
import com.knowledgeapplication.api.knowledge.revision.model.KnowledgeRevision;
import com.knowledgeapplication.api.knowledge.revision.model.KnowledgeRevisionReason;
import com.knowledgeapplication.api.knowledge.revision.service.KnowledgeRevisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private KnowledgeRepository repository;

    @Mock
    private KnowledgeCollectionRepository collectionRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private CurrentOwner currentOwner;

    @Mock
    private ShareTokenGenerator shareTokenGenerator;

    @Mock
    private KnowledgeRevisionService revisionService;

    @Mock
    private KnowledgeAttachmentLifecycleService attachmentLifecycleService;

    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        when(currentOwner.id()).thenReturn(OWNER_ID);
        service = new KnowledgeService(
                repository,
                collectionRepository,
                tagRepository,
                currentOwner,
                shareTokenGenerator,
                revisionService,
                attachmentLifecycleService
        );
    }

    @Test
    void createsKnowledgeForServerOwnerWithGloballyUniqueSlug() {
        when(repository.existsBySlug("spring-webclient-timeout")).thenReturn(true);
        when(repository.existsBySlug("spring-webclient-timeout-2")).thenReturn(false);
        when(repository.save(any(Knowledge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Knowledge created = service.create(
                "  Spring WebClient timeout  ",
                "Timeout notes",
                "",
                null,
                null,
                List.of()
        );

        assertThat(created.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(created.getTitle()).isEqualTo("Spring WebClient timeout");
        assertThat(created.getSlug()).isEqualTo("spring-webclient-timeout-2");
        assertThat(created.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(created.getContent()).isEmpty();
        verify(repository).save(created);
        verify(revisionService).createInitial(created);
    }

    @Test
    void fetchesKnowledgeByOwnerScopedIdAndSlug() {
        Knowledge knowledge = knowledge(OWNER_ID, "owner-note");
        when(repository.findByIdAndOwnerId(10L, OWNER_ID)).thenReturn(Optional.of(knowledge));
        when(repository.findBySlugAndOwnerId("owner-note", OWNER_ID)).thenReturn(Optional.of(knowledge));

        assertThat(service.getById(10L)).isSameAs(knowledge);
        assertThat(service.getBySlug("owner-note")).isSameAs(knowledge);
    }

    @Test
    void listsOnlyCurrentOwnerInDeterministicRepositoryOrder() {
        Knowledge first = knowledge(OWNER_ID, "first");
        Knowledge second = knowledge(OWNER_ID, "second");
        when(repository.findAllByOwnerIdOrderByUpdatedAtDescIdDesc(OWNER_ID))
                .thenReturn(List.of(first, second));

        assertThat(service.list()).containsExactly(first, second);
        verify(repository).findAllByOwnerIdOrderByUpdatedAtDescIdDesc(OWNER_ID);
    }

    @Test
    void updatesEditableFieldsWithoutChangingSlugOrOwner() {
        Knowledge knowledge = knowledge(OWNER_ID, "stable-slug");
        when(repository.findByIdAndOwnerId(10L, OWNER_ID)).thenReturn(Optional.of(knowledge));

        Knowledge updated = service.update(
                10L,
                "Updated title",
                "Updated summary",
                "# Updated Markdown",
                Visibility.PUBLIC,
                null,
                List.of()
        );

        assertThat(updated.getTitle()).isEqualTo("Updated title");
        assertThat(updated.getSummary()).isEqualTo("Updated summary");
        assertThat(updated.getContent()).isEqualTo("# Updated Markdown");
        assertThat(updated.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(updated.getSlug()).isEqualTo("stable-slug");
        assertThat(updated.getOwnerId()).isEqualTo(OWNER_ID);
        verify(revisionService).checkpointIfDue(knowledge);
        verify(attachmentLifecycleService).synchronizeReferences(10L, "Test content", "# Updated Markdown");
    }

    @Test
    void fullUpdateWithOnlyVisibilityChangedDoesNotCreateAuthoringCheckpoint() {
        Knowledge knowledge = knowledge(OWNER_ID, "visibility-only-put");
        when(repository.findByIdAndOwnerId(11L, OWNER_ID)).thenReturn(Optional.of(knowledge));

        service.update(
                11L,
                knowledge.getTitle(),
                knowledge.getSummary(),
                knowledge.getContent(),
                Visibility.PUBLIC,
                null,
                List.of()
        );

        assertThat(knowledge.getVisibility()).isEqualTo(Visibility.PUBLIC);
        verify(revisionService, never()).checkpointIfDue(any());
        verify(attachmentLifecycleService).synchronizeReferences(11L, "Test content", "Test content");
    }

    @Test
    void noOpAuthoringUpdateDoesNotCreateCheckpoint() {
        Knowledge knowledge = knowledge(OWNER_ID, "no-op-update");
        when(repository.findByIdAndOwnerId(12L, OWNER_ID)).thenReturn(Optional.of(knowledge));

        service.update(
                12L,
                "  Test title  ",
                "Test summary",
                "Test content",
                Visibility.PRIVATE,
                null,
                List.of()
        );

        verify(revisionService, never()).checkpointIfDue(any());
        verify(attachmentLifecycleService).synchronizeReferences(12L, "Test content", "Test content");
    }

    @Test
    void updatesOnlyVisibilityThroughOwnerScopedLookupAndCreatesUnlistedToken() {
        Knowledge knowledge = knowledge(OWNER_ID, "stable-share-slug");
        String token = "S".repeat(43);
        when(repository.findByIdAndOwnerId(15L, OWNER_ID)).thenReturn(Optional.of(knowledge));
        when(shareTokenGenerator.generate()).thenReturn(token);
        when(repository.existsByShareToken(token)).thenReturn(false);

        Knowledge updated = service.updateVisibility(15L, Visibility.UNLISTED);

        assertThat(updated.getVisibility()).isEqualTo(Visibility.UNLISTED);
        assertThat(updated.getShareToken()).isEqualTo(token);
        assertThat(updated.getTitle()).isEqualTo("Test title");
        assertThat(updated.getSlug()).isEqualTo("stable-share-slug");
        assertThat(updated.getOwnerId()).isEqualTo(OWNER_ID);
        verify(repository).findByIdAndOwnerId(15L, OWNER_ID);
        verify(repository, never()).findById(15L);
    }

    @Test
    void visibilityUpdateCannotReachAnotherOwnersKnowledge() {
        when(repository.findByIdAndOwnerId(16L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateVisibility(16L, Visibility.PUBLIC))
                .isInstanceOf(KnowledgeNotFoundException.class);
        verify(repository, never()).findById(16L);
        verify(revisionService, never()).checkpointIfDue(any());
    }

    @Test
    void deletesOnlyKnowledgeResolvedThroughCurrentOwner() {
        Knowledge knowledge = knowledge(OWNER_ID, "delete-me");
        when(repository.findByIdAndOwnerId(10L, OWNER_ID)).thenReturn(Optional.of(knowledge));

        service.delete(10L);

        verify(attachmentLifecycleService).enqueueKnowledgeDeletion(10L);
        verify(repository).delete(knowledge);
    }

    @Test
    void returnsNotFoundForUnknownDelete() {
        when(repository.findByIdAndOwnerId(99L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(KnowledgeNotFoundException.class)
                .hasMessage("Knowledge item not found");
        verify(repository, never()).delete(any());
        verify(attachmentLifecycleService, never()).enqueueKnowledgeDeletion(99L);
    }

    @Test
    void doesNotFallBackToOwnerUnscopedLookup() {
        Knowledge otherOwnersKnowledge = knowledge(OTHER_OWNER_ID, "other-owner");
        when(repository.findByIdAndOwnerId(20L, OWNER_ID)).thenReturn(Optional.empty());
        when(repository.findBySlugAndOwnerId("other-owner", OWNER_ID)).thenReturn(Optional.empty());

        assertThat(otherOwnersKnowledge.getOwnerId()).isEqualTo(OTHER_OWNER_ID);
        assertThatThrownBy(() -> service.getById(20L)).isInstanceOf(KnowledgeNotFoundException.class);
        assertThatThrownBy(() -> service.getBySlug("other-owner"))
                .isInstanceOf(KnowledgeNotFoundException.class);
        verify(repository, never()).findById(20L);
        verify(repository, never()).findBySlugAndOwnerId("other-owner", OTHER_OWNER_ID);
    }

    @Test
    void createsAndNormalizesCollectionAndTags() {
        KnowledgeCollection backend = KnowledgeCollection.create(OWNER_ID, "Backend");
        Tag springBoot = Tag.create(OWNER_ID, "Spring Boot");
        when(collectionRepository.findByOwnerIdAndNormalizedName(OWNER_ID, "backend"))
                .thenReturn(Optional.of(backend));
        when(tagRepository.findAllByOwnerIdAndNormalizedNameIn(any(), any()))
                .thenReturn(List.of(springBoot));
        when(tagRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(Knowledge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Knowledge created = service.create(
                "Metadata note",
                null,
                "",
                Visibility.PRIVATE,
                "  backend  ",
                List.of("#Spring Boot", " spring boot ", "##WebClient")
        );

        assertThat(created.getCollection()).isSameAs(backend);
        assertThat(created.getTags()).extracting(Tag::getName)
                .containsExactly("Spring Boot", "WebClient");
        verify(collectionRepository).findByOwnerIdAndNormalizedName(OWNER_ID, "backend");
        verify(tagRepository).findAllByOwnerIdAndNormalizedNameIn(
                OWNER_ID,
                new LinkedHashSet<>(List.of("spring boot", "webclient"))
        );
    }

    @Test
    void reusesSameOwnerCollectionAndTagWithoutChangingTheirDisplayNames() {
        KnowledgeCollection existingCollection = KnowledgeCollection.create(OWNER_ID, "Backend");
        Tag existingTag = Tag.create(OWNER_ID, "Spring Boot");
        when(collectionRepository.findByOwnerIdAndNormalizedName(OWNER_ID, "backend"))
                .thenReturn(Optional.of(existingCollection));
        when(tagRepository.findAllByOwnerIdAndNormalizedNameIn(any(), any()))
                .thenReturn(List.of(existingTag));
        when(repository.save(any(Knowledge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Knowledge created = service.create(
                "Reuse metadata",
                null,
                "",
                Visibility.PRIVATE,
                "BACKEND",
                List.of("spring boot")
        );

        assertThat(created.getCollection()).isSameAs(existingCollection);
        assertThat(created.getCollection().getName()).isEqualTo("Backend");
        assertThat(created.getTags()).containsExactly(existingTag);
        verify(collectionRepository, never()).save(any());
        verify(tagRepository, never()).saveAll(any());
    }

    @Test
    void updateReplacesAndThenRemovesMetadataWithoutChangingSlugOrOwner() {
        Knowledge knowledge = knowledge(OWNER_ID, "metadata-stable-slug");
        KnowledgeCollection oldCollection = KnowledgeCollection.create(OWNER_ID, "Backend");
        Tag oldTag = Tag.create(OWNER_ID, "Old Tag");
        knowledge.replaceMetadata(oldCollection, Set.of(oldTag));

        KnowledgeCollection database = KnowledgeCollection.create(OWNER_ID, "Database");
        Tag redis = Tag.create(OWNER_ID, "Redis");
        when(repository.findByIdAndOwnerId(42L, OWNER_ID)).thenReturn(Optional.of(knowledge));
        when(collectionRepository.findByOwnerIdAndNormalizedName(OWNER_ID, "database"))
                .thenReturn(Optional.of(database));
        when(tagRepository.findAllByOwnerIdAndNormalizedNameIn(any(), any()))
                .thenReturn(List.of(redis));

        Knowledge updated = service.update(
                42L,
                "Updated metadata note",
                null,
                "",
                Visibility.PRIVATE,
                "Database",
                List.of("Redis")
        );

        assertThat(updated.getCollection()).isSameAs(database);
        assertThat(updated.getTags()).containsExactly(redis);
        assertThat(updated.getSlug()).isEqualTo("metadata-stable-slug");
        assertThat(updated.getOwnerId()).isEqualTo(OWNER_ID);

        Knowledge cleared = service.update(
                42L,
                "Updated metadata note",
                null,
                "",
                Visibility.PRIVATE,
                null,
                List.of()
        );

        assertThat(cleared.getCollection()).isNull();
        assertThat(cleared.getTags()).isEmpty();
    }

    @Test
    void createsTokenOnFirstUnlistedStateAndPreservesItAcrossEditsAndReactivation() {
        String token = "A".repeat(43);
        when(shareTokenGenerator.generate()).thenReturn(token);
        when(repository.existsByShareToken(token)).thenReturn(false);
        when(repository.save(any(Knowledge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Knowledge knowledge = service.create(
                "Secret note", null, "", Visibility.UNLISTED, null, List.of()
        );
        var createdAt = knowledge.getShareTokenCreatedAt();
        when(repository.findByIdAndOwnerId(7L, OWNER_ID)).thenReturn(Optional.of(knowledge));

        service.update(7L, "Edited secret note", null, "updated", Visibility.UNLISTED, null, List.of());
        service.update(7L, "Edited secret note", null, "updated", Visibility.PRIVATE, null, List.of());
        service.update(7L, "Edited secret note", null, "updated", Visibility.UNLISTED, null, List.of());

        assertThat(knowledge.getShareToken()).isEqualTo(token);
        assertThat(knowledge.getShareTokenCreatedAt()).isEqualTo(createdAt);
        verify(shareTokenGenerator, times(1)).generate();
    }

    @Test
    void regenerationRetriesExistingAndCurrentTokensAndInvalidatesTheOldValue() {
        String oldToken = "O".repeat(43);
        String collision = "C".repeat(43);
        String newToken = "N".repeat(43);
        Knowledge knowledge = knowledge(OWNER_ID, "rotated-secret");
        knowledge.replaceShareToken(oldToken);
        var originalCreatedAt = knowledge.getShareTokenCreatedAt();
        when(repository.findByIdAndOwnerId(8L, OWNER_ID)).thenReturn(Optional.of(knowledge));
        when(shareTokenGenerator.generate()).thenReturn(oldToken, collision, newToken);
        when(repository.existsByShareToken(collision)).thenReturn(true);
        when(repository.existsByShareToken(newToken)).thenReturn(false);

        Knowledge rotated = service.regenerateUnlistedLink(8L);

        assertThat(rotated.getShareToken()).isEqualTo(newToken).isNotEqualTo(oldToken);
        assertThat(rotated.getShareTokenCreatedAt()).isAfterOrEqualTo(originalCreatedAt);
        verify(repository).existsByShareToken(collision);
        verify(repository).existsByShareToken(newToken);
        verify(repository, never()).existsByShareToken(oldToken);
        verify(revisionService, never()).checkpointIfDue(any());
    }

    @Test
    void restoreReplacesOnlyAuthoringStateAndKeepsSlugSharingAndPublicationState() {
        Knowledge current = knowledge(OWNER_ID, "stable-restore-slug");
        current.changeVisibility(Visibility.PUBLIC);
        current.replaceShareToken("T".repeat(43));
        Instant publishedAt = current.getPublishedAt();
        String shareToken = current.getShareToken();
        Instant shareTokenCreatedAt = current.getShareTokenCreatedAt();

        Knowledge historical = Knowledge.create(
                OWNER_ID,
                "Historical title",
                "irrelevant-historical-slug",
                "Historical summary",
                "# Historical Markdown",
                Visibility.PRIVATE
        );
        KnowledgeCollection historicalCollection = KnowledgeCollection.create(OWNER_ID, "Database");
        Tag historicalTag = Tag.create(OWNER_ID, "PostgreSQL");
        historical.replaceMetadata(historicalCollection, Set.of(historicalTag));
        KnowledgeRevision revision = KnowledgeRevision.snapshot(
                historical,
                KnowledgeRevisionReason.CHECKPOINT,
                Instant.parse("2026-09-12T01:00:00Z")
        );

        when(repository.findByIdAndOwnerId(21L, OWNER_ID)).thenReturn(Optional.of(current));
        when(revisionService.get(21L, 31L)).thenReturn(revision);
        when(collectionRepository.findByOwnerIdAndNormalizedName(OWNER_ID, "database"))
                .thenReturn(Optional.of(historicalCollection));
        when(tagRepository.findAllByOwnerIdAndNormalizedNameIn(any(), any()))
                .thenReturn(List.of(historicalTag));

        Knowledge restored = service.restoreRevision(21L, 31L);

        assertThat(restored.getTitle()).isEqualTo("Historical title");
        assertThat(restored.getSummary()).isEqualTo("Historical summary");
        assertThat(restored.getContent()).isEqualTo("# Historical Markdown");
        assertThat(restored.getCollection()).isSameAs(historicalCollection);
        assertThat(restored.getTags()).containsExactly(historicalTag);
        assertThat(restored.getSlug()).isEqualTo("stable-restore-slug");
        assertThat(restored.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(restored.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(restored.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(restored.getShareToken()).isEqualTo(shareToken);
        assertThat(restored.getShareTokenCreatedAt()).isEqualTo(shareTokenCreatedAt);
        verify(revisionService).snapshotBeforeRestore(current);
        verify(attachmentLifecycleService).synchronizeReferences(
                21L,
                "Test content",
                "# Historical Markdown"
        );
    }

    @Test
    void ownerLinkManagementNeverFallsBackToAnUnscopedLookup() {
        when(repository.findByIdAndOwnerId(91L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUnlistedLink(91L))
                .isInstanceOf(KnowledgeNotFoundException.class);
        assertThatThrownBy(() -> service.regenerateUnlistedLink(91L))
                .isInstanceOf(KnowledgeNotFoundException.class);
        verify(repository, never()).findById(91L);
        verify(shareTokenGenerator, never()).generate();
    }

    private static Knowledge knowledge(UUID ownerId, String slug) {
        return Knowledge.create(
                ownerId,
                "Test title",
                slug,
                "Test summary",
                "Test content",
                Visibility.PRIVATE
        );
    }
}
