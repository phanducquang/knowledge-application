package com.knowledgeapplication.api.knowledge.revision.service;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.KnowledgeCollection;
import com.knowledgeapplication.api.knowledge.model.Tag;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import com.knowledgeapplication.api.knowledge.revision.model.KnowledgeRevision;
import com.knowledgeapplication.api.knowledge.revision.model.KnowledgeRevisionReason;
import com.knowledgeapplication.api.knowledge.revision.repository.KnowledgeRevisionRepository;
import com.knowledgeapplication.api.knowledge.service.KnowledgeNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRevisionServiceTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

    @Mock
    private KnowledgeRevisionRepository revisionRepository;

    @Mock
    private KnowledgeRepository knowledgeRepository;

    @Mock
    private CurrentOwner currentOwner;

    private KnowledgeRevisionService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeRevisionService(
                revisionRepository,
                knowledgeRepository,
                currentOwner,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5)
        );
        lenient().when(revisionRepository.save(any(KnowledgeRevision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void initialRevisionSnapshotsNormalizedAuthoringStateOnly() {
        Knowledge knowledge = knowledge("  Initial title  ", "initial-slug", "# Initial");
        knowledge.replaceMetadata(
                KnowledgeCollection.create(OWNER_ID, "  Backend  "),
                Set.of(Tag.create(OWNER_ID, "#WebClient"), Tag.create(OWNER_ID, "Spring Boot"))
        );

        KnowledgeRevision revision = service.createInitial(knowledge);

        assertThat(revision.getReason()).isEqualTo(KnowledgeRevisionReason.CREATE);
        assertThat(revision.getTitle()).isEqualTo("Initial title");
        assertThat(revision.getSummary()).isEqualTo("Summary");
        assertThat(revision.getContent()).isEqualTo("# Initial");
        assertThat(revision.getCollectionName()).isEqualTo("Backend");
        assertThat(revision.getTags()).containsExactly("Spring Boot", "WebClient");
        assertThat(revision.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void createsCheckpointOfCurrentStateWhenLatestRevisionIsOldAndDifferent() {
        Knowledge current = knowledge("Current", "stable", "current");
        Knowledge old = knowledge("Old", "ignored", "old");
        KnowledgeRevision previous = KnowledgeRevision.snapshot(
                old,
                KnowledgeRevisionReason.CREATE,
                NOW.minus(Duration.ofMinutes(6))
        );
        when(revisionRepository.findFirstByKnowledgeIdOrderByCreatedAtDescIdDesc(null))
                .thenReturn(Optional.of(previous));

        KnowledgeRevision checkpoint = service.checkpointIfDue(current);

        assertThat(checkpoint).isNotNull();
        assertThat(checkpoint.getReason()).isEqualTo(KnowledgeRevisionReason.CHECKPOINT);
        assertThat(checkpoint.getTitle()).isEqualTo("Current");
        assertThat(checkpoint.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void frequentAutosaveInsideIntervalDoesNotCreateCheckpoint() {
        Knowledge current = knowledge("Current", "stable", "current");
        KnowledgeRevision previous = KnowledgeRevision.snapshot(
                knowledge("Old", "ignored", "old"),
                KnowledgeRevisionReason.CHECKPOINT,
                NOW.minus(Duration.ofMinutes(4))
        );
        when(revisionRepository.findFirstByKnowledgeIdOrderByCreatedAtDescIdDesc(null))
                .thenReturn(Optional.of(previous));

        assertThat(service.checkpointIfDue(current)).isNull();
        verify(revisionRepository, never()).save(any());
    }

    @Test
    void duplicateSnapshotIsSkippedEvenAfterInterval() {
        Knowledge current = knowledge("Current", "stable", "current");
        KnowledgeRevision previous = KnowledgeRevision.snapshot(
                current,
                KnowledgeRevisionReason.CREATE,
                NOW.minus(Duration.ofMinutes(10))
        );
        when(revisionRepository.findFirstByKnowledgeIdOrderByCreatedAtDescIdDesc(null))
                .thenReturn(Optional.of(previous));

        assertThat(service.checkpointIfDue(current)).isNull();
        verify(revisionRepository, never()).save(any());
    }

    @Test
    void beforeRestoreIsAlwaysAnExplicitUndoCheckpoint() {
        Knowledge current = knowledge("Current", "stable", "current");

        KnowledgeRevision checkpoint = service.snapshotBeforeRestore(current);

        assertThat(checkpoint.getReason()).isEqualTo(KnowledgeRevisionReason.BEFORE_RESTORE);
        assertThat(checkpoint.getTitle()).isEqualTo("Current");
        verify(revisionRepository).save(checkpoint);
    }

    @Test
    void listIsOwnerScopedBoundedAndNewestFirst() {
        Knowledge owned = knowledge("Owned", "owned", "owned");
        when(currentOwner.id()).thenReturn(OWNER_ID);
        when(knowledgeRepository.findByIdAndOwnerId(7L, OWNER_ID)).thenReturn(Optional.of(owned));
        when(revisionRepository.findAllByKnowledgeId(any(), any()))
                .thenReturn(new SliceImpl<>(List.of()));

        service.list(7L, 2, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(revisionRepository).findAllByKnowledgeId(org.mockito.ArgumentMatchers.eq(7L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void detailCannotEscapeKnowledgeOrOwnerScope() {
        when(currentOwner.id()).thenReturn(OWNER_ID);
        when(knowledgeRepository.findByIdAndOwnerId(8L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(8L, 90L))
                .isInstanceOf(KnowledgeNotFoundException.class);
        verify(revisionRepository, never()).findByIdAndKnowledgeId(any(), any());
    }

    @Test
    void rejectsNegativeCheckpointInterval() {
        assertThatThrownBy(() -> new KnowledgeRevisionService(
                revisionRepository,
                knowledgeRepository,
                currentOwner,
                Clock.systemUTC(),
                Duration.ofSeconds(-1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static Knowledge knowledge(String title, String slug, String content) {
        return Knowledge.create(
                OWNER_ID,
                title,
                slug,
                "Summary",
                content,
                Visibility.PRIVATE
        );
    }
}
