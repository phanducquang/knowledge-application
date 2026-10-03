package com.knowledgeapplication.api.knowledge.embedding;

import com.knowledgeapplication.api.knowledge.collection.CollectionService;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.service.KnowledgeService;
import com.knowledgeapplication.api.testsupport.TestContainerImages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.knowledgeapplication.api.knowledge.embedding.EmbeddingTestSupport.*;
import static com.knowledgeapplication.api.knowledge.embedding.KnowledgeEmbeddingRepository.IndexState.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
class KnowledgeEmbeddingIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TestContainerImages.POSTGRES);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.object-storage.endpoint", () -> "http://127.0.0.1:19000");
        registry.add("app.object-storage.bucket", () -> "unused-in-embedding-tests");
        registry.add("app.object-storage.access-key", () -> "test-access-key");
        registry.add("app.object-storage.secret-key", () -> "test-secret-key");
        registry.add("app.knowledge.attachment-cleanup.enabled", () -> "false");
        registry.add("app.embedding.enabled", () -> "false");
    }

    @Autowired KnowledgeEmbeddingRepository repository;
    @Autowired KnowledgeEmbeddingIndexer disabledIndexer;
    @Autowired KnowledgeService knowledge;
    @Autowired CollectionService collections;
    @Autowired JdbcClient jdbc;
    @Autowired WebApplicationContext context;
    EmbeddingProperties config;
    EmbeddingStrategy strategy;
    DeterministicEmbeddingClient fake;

    @BeforeEach
    void setUp() {
        jdbc.sql("TRUNCATE knowledge_attachment_delete_queue, knowledge, tag, knowledge_collection RESTART IDENTITY CASCADE").update();
        Instant now = Instant.now();
        var token = new OidcIdToken("test-token", now.minusSeconds(60), now.plusSeconds(300), Map.of(
                "sub", "owner-subject", "email", "owner@example.com", "email_verified", true));
        var principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_OWNER")), token);
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));
        config = EmbeddingTestSupport.properties();
        strategy = new EmbeddingStrategy(config, MarkdownChunker.VERSION);
        fake = spy(new DeterministicEmbeddingClient(3));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void migrationEnablesRealExtensionAndDisabledApplicationRemainsHealthy() throws Exception {
        assertThat(jdbc.sql("SELECT extversion FROM pg_extension WHERE extname = 'vector'").query(String.class).single()).isEqualTo("0.8.6");
        assertThat(context.getBeansOfType(EmbeddingClient.class)).isEmpty();
        assertThat(context.getBeansOfType(EmbeddingIndexScheduler.class)).isEmpty();
        create("Disabled note", "Current text");
        assertThat(disabledIndexer.indexOnce().indexed()).isZero();
        assertThat(rowCount()).isZero();
        MockMvcBuilders.webAppContextSetup(context).build().perform(get("/actuator/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void backfillsCurrentNotesOfAllVisibilitiesWithTitleContextAndUnicodeHashes() {
        var first = knowledge.create("Tiếng Việt 😀", "Tóm tắt", "# Nội dung\n\nTechnical prose 😀", Visibility.PRIVATE, null, List.of());
        knowledge.create("Public", null, "", Visibility.PUBLIC, null, List.of());
        knowledge.create("Unlisted", null, "", Visibility.UNLISTED, null, List.of());
        var largeBatch = EmbeddingTestSupport.properties(true, "test-model", 3, 2, 10);
        var summary = indexer(repository, fake, largeBatch).indexOnce();
        assertThat(summary.indexed()).isEqualTo(3);
        assertThat(rowCount()).isEqualTo(3);
        assertThat(repository.state(OWNER, first.getId(), strategy)).isEqualTo(CURRENT);
        var snapshot = repository.findSource(OWNER, first.getId()).orElseThrow();
        assertThat(jdbc.sql("SELECT source_hash FROM knowledge_embedding_chunk WHERE knowledge_id = :id").param("id", first.getId()).query(String.class).single())
                .isEqualTo(snapshot.hash(strategy));
        assertThat(jdbc.sql("SELECT source_updated_at FROM knowledge_embedding_chunk WHERE knowledge_id = :id").param("id", first.getId())
                .query(OffsetDateTime.class).single().toInstant()).isEqualTo(first.getUpdatedAt());
        verify(fake).embed(argThat(inputs -> inputs.get(0).startsWith("Title: Tiếng Việt 😀\nSummary: Tóm tắt\n\n")));
        assertThat(repository.findSource(OTHER, first.getId())).isEmpty();
    }

    @Test
    void unchangedAndMetadataOnlyEditsDoNotCallProviderAgain() {
        var note = create("Stable", "Current text");
        var indexer = indexer(repository, fake, config);
        indexer.indexOnce();
        clearInvocations(fake);
        assertThat(indexer.indexOnce().indexed()).isZero();
        knowledge.update(note.getId(), "Stable", "summary", "Current text", Visibility.PUBLIC, "Backend", List.of("Java"));
        var current = knowledge.getById(note.getId());
        collections.rename(current.getCollection().getId(), "Platform");
        collections.delete(current.getCollection().getId());
        assertThat(repository.state(OWNER, note.getId(), strategy)).isEqualTo(CURRENT);
        assertThat(indexer.indexOnce().indexed()).isZero();
        verifyNoInteractions(fake);
        assertThat(repository.findNearestChunks(OWNER, strategy, new float[]{1, 0, 0}, 10)).hasSize(1);
    }

    @Test
    void authoringUpdatesAreImmediatelyStaleAndCompleteReplacementRemovesOldChunks() {
        var note = create("Large", "Technical words with context. ".repeat(100));
        var indexer = indexer(repository, fake, config);
        indexer.indexOnce();
        var oldIds = chunkIds(note.getId());
        assertThat(oldIds.size()).isGreaterThan(2);
        clearInvocations(fake);
        knowledge.update(note.getId(), "New title", "new summary", "Short replacement", Visibility.PRIVATE, null, List.of());
        verifyNoInteractions(fake);
        assertThat(repository.state(OWNER, note.getId(), strategy)).isEqualTo(STALE);
        assertThat(chunkIds(note.getId())).isEqualTo(oldIds);
        assertThat(repository.findNearestChunks(OWNER, strategy, new float[]{1, 0, 0}, 10)).isEmpty();
        assertThat(indexer.indexOnce().indexed()).isEqualTo(1);
        assertThat(repository.state(OWNER, note.getId(), strategy)).isEqualTo(CURRENT);
        assertThat(chunkIds(note.getId())).hasSize(1).doesNotContainAnyElementsOf(oldIds);
    }

    @Test
    void failedLaterProviderBatchKeepsOldRowsStaleAndRetryReplacesAllOfThem() {
        var note = create("Failure", "Old technical words. ".repeat(100));
        indexer(repository, fake, config).indexOnce();
        var oldIds = chunkIds(note.getId());
        knowledge.update(note.getId(), "Failure", "summary", "New technical words. ".repeat(120), Visibility.PRIVATE, null, List.of());
        var calls = new AtomicInteger();
        EmbeddingClient failing = inputs -> {
            assertThat(inputs.size()).isLessThanOrEqualTo(2);
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("secret provider error");
            return fake.embed(inputs);
        };
        assertThat(indexer(repository, failing, config).indexOnce().failures()).isEqualTo(1);
        assertThat(chunkIds(note.getId())).isEqualTo(oldIds);
        assertThat(repository.state(OWNER, note.getId(), strategy)).isEqualTo(STALE);
        assertThat(indexer(repository, fake, config).indexOnce().indexed()).isEqualTo(1);
        assertThat(repository.state(OWNER, note.getId(), strategy)).isEqualTo(CURRENT);
        assertThat(chunkIds(note.getId())).doesNotContainAnyElementsOf(oldIds);
    }

    @Test
    void invalidProviderVectorsDoNotPublishAnIndex() {
        var note = create("Invalid vector", "Current text");
        assertThat(indexer(repository, inputs -> List.of(new float[]{1, 0}), config).indexOnce().failures()).isEqualTo(1);
        assertThat(repository.state(OWNER, note.getId(), strategy)).isEqualTo(NOT_INDEXED);
        assertThat(rowCount()).isZero();
    }

    @Test
    void databaseFailureAfterFirstInsertRollsBackDeletionAndPartialReplacement() {
        var note = create("Atomic", "Current text");
        indexer(repository, fake, config).indexOnce();
        var ids = chunkIds(note.getId());
        var source = repository.findSource(OWNER, note.getId()).orElseThrow();
        jdbc.sql("ALTER TABLE knowledge_embedding_chunk ADD CONSTRAINT test_single_chunk CHECK (chunk_index < 1)").update();
        try {
            assertThatThrownBy(() -> repository.replace(source, strategy,
                    List.of(new MarkdownChunker.Chunk(0, "first"), new MarkdownChunker.Chunk(1, "second")),
                    List.of(new float[]{1, 0, 0}, new float[]{0, 1, 0}))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(chunkIds(note.getId())).isEqualTo(ids);
            assertThat(repository.state(OWNER, note.getId(), strategy)).isEqualTo(CURRENT);
        } finally { jdbc.sql("ALTER TABLE knowledge_embedding_chunk DROP CONSTRAINT test_single_chunk").update(); }
    }

    @Test
    void concurrentIndexersCannotDuplicateWorkAndAuthoringDoesNotWaitForProvider() throws Exception {
        var note = create("Concurrent", "Original text");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        EmbeddingClient slow = inputs -> {
            calls.incrementAndGet();
            entered.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Provider was not released"); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
            return fake.embed(inputs);
        };
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> indexer(repository, slow, config).indexOnce());
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(indexer(repository, slow, config).indexOnce().indexed()).isZero();
            assertThat(calls.get()).isEqualTo(1);
            // This save commits while the provider is still blocked on the latch.
            knowledge.update(note.getId(), "Concurrent", "summary", "Changed while embedding", Visibility.PRIVATE, null, List.of());
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).superseded()).isEqualTo(1);
            assertThat(rowCount()).isZero();
            assertThat(indexer(repository, fake, config).indexOnce().indexed()).isEqualTo(1);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void deletingDuringEmbeddingCannotResurrectTheNoteOrChunks() {
        var note = create("Deleted in flight", "Original");
        EmbeddingClient deleting = inputs -> { knowledge.delete(note.getId()); return fake.embed(inputs); };
        assertThat(indexer(repository, deleting, config).indexOnce().superseded()).isEqualTo(1);
        assertThat(rowCount()).isZero();
        assertThat(repository.findSource(OWNER, note.getId())).isEmpty();
    }

    @Test
    void historicalRevisionsAreExcludedUntilRestoredIntoCurrentAuthoringState() {
        var note = create("Revision", "historical-only-token");
        long revision = jdbc.sql("SELECT id FROM knowledge_revision WHERE knowledge_id = :id ORDER BY id LIMIT 1").param("id", note.getId()).query(Long.class).single();
        knowledge.update(note.getId(), "Revision", "summary", "current-only-token", Visibility.PRIVATE, null, List.of());
        var indexer = indexer(repository, fake, config);
        indexer.indexOnce();
        verify(fake).embed(argThat(inputs -> inputs.stream().allMatch(input -> input.contains("current-only-token") && !input.contains("historical-only-token"))));
        clearInvocations(fake);
        knowledge.restoreRevision(note.getId(), revision);
        verifyNoInteractions(fake);
        assertThat(repository.state(OWNER, note.getId(), strategy)).isEqualTo(STALE);
        assertThat(indexer.indexOnce().indexed()).isEqualTo(1);
        assertThat(repository.state(OWNER, note.getId(), strategy)).isEqualTo(CURRENT);
        assertThat(jdbc.sql("SELECT chunk_text FROM knowledge_embedding_chunk WHERE knowledge_id = :id").param("id", note.getId()).query(String.class).single())
                .isEqualTo("historical-only-token");
    }

    @Test
    void modelDimensionsAndChunkerVersionChangesInvalidateAndRebuildTheIndex() {
        var note = create("Compatibility", "Current text");
        indexer(repository, fake, config).indexOnce();
        var next = EmbeddingTestSupport.properties(true, "new-model", 4, 2, 2);
        var nextStrategy = new EmbeddingStrategy(next, MarkdownChunker.VERSION);
        assertThat(repository.state(OWNER, note.getId(), nextStrategy)).isEqualTo(STALE);
        assertThat(repository.findNearestChunks(OWNER, nextStrategy, new float[]{1, 0, 0, 0}, 10)).isEmpty();
        assertThat(indexer(repository, new DeterministicEmbeddingClient(4), next).indexOnce().indexed()).isEqualTo(1);
        assertThat(repository.state(OWNER, note.getId(), nextStrategy)).isEqualTo(CURRENT);
        var versionChanged = new EmbeddingStrategy(next, MarkdownChunker.VERSION + 1);
        assertThat(repository.state(OWNER, note.getId(), versionChanged)).isEqualTo(STALE);
        assertThat(indexer(repository, new DeterministicEmbeddingClient(4), next, versionChanged).indexOnce().indexed()).isEqualTo(1);
        assertThat(repository.state(OWNER, note.getId(), versionChanged)).isEqualTo(CURRENT);
        assertThat(repository.state(OWNER, note.getId(), strategy)).isEqualTo(STALE);
    }

    @Test
    void realCosineRetrievalFiltersOwnerModelDimensionFreshnessAndIncompleteSetsWithStableTies() {
        var alpha = create("Alpha", "Current A");
        var beta = create("Beta", "Current B");
        var wrongModel = create("Other model", "Different model");
        var wrongDimensions = create("Other dimensions", "Different dimensions");
        long other = jdbc.sql("INSERT INTO knowledge (owner_id, title, slug, content) VALUES (:owner, 'Other', 'other-owner', 'Other text') RETURNING id")
                .param("owner", OTHER).query(Long.class).single();
        assertThat(repository.replace(repository.findSource(OWNER, alpha.getId()).orElseThrow(), strategy,
                List.of(new MarkdownChunker.Chunk(0, "first"), new MarkdownChunker.Chunk(1, "second")),
                List.of(new float[]{1, 0, 0}, new float[]{0.8f, 0.6f, 0}))).isTrue();
        persist(beta.getId(), OWNER, strategy, new float[]{1, 0, 0});
        persist(other, OTHER, strategy, new float[]{1, 0, 0});
        persist(wrongModel.getId(), OWNER, new EmbeddingStrategy(EmbeddingTestSupport.properties(true, "other-model", 3, 2, 2), 1), new float[]{1, 0, 0});
        persist(wrongDimensions.getId(), OWNER, new EmbeddingStrategy(EmbeddingTestSupport.properties(true, "test-model", 4, 2, 2), 1), new float[]{1, 0, 0, 0});
        var results = repository.findNearestChunks(OWNER, strategy, new float[]{1, 0, 0}, 10);
        assertThat(results).extracting(KnowledgeEmbeddingRepository.NearestChunk::knowledgeId).containsExactly(alpha.getId(), beta.getId(), alpha.getId());
        assertThat(results.get(0).distance()).isCloseTo(0, within(0.000001));
        assertThat(results.get(2).distance()).isCloseTo(0.2, within(0.000001));
        assertThat(repository.findNearestChunks(OWNER, strategy, new float[]{1, 0, 0}, 1)).hasSize(1);
        assertThat(repository.findNearestChunks(OTHER, strategy, new float[]{1, 0, 0}, 10)).extracting(KnowledgeEmbeddingRepository.NearestChunk::knowledgeId).containsExactly(other);
        jdbc.sql("DELETE FROM knowledge_embedding_chunk WHERE knowledge_id = :id AND chunk_index = 1").param("id", alpha.getId()).update();
        assertThat(repository.state(OWNER, alpha.getId(), strategy)).isEqualTo(STALE);
        assertThat(repository.findNearestChunks(OWNER, strategy, new float[]{1, 0, 0}, 10)).extracting(KnowledgeEmbeddingRepository.NearestChunk::knowledgeId).containsExactly(beta.getId());
        knowledge.update(beta.getId(), "Beta", "summary", "Changed B", Visibility.PRIVATE, null, List.of());
        assertThat(repository.findNearestChunks(OWNER, strategy, new float[]{1, 0, 0}, 10)).isEmpty();
        assertThatThrownBy(() -> repository.findNearestChunks(OWNER, strategy, new float[]{1, 0, 0}, 0))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void databaseEnforcesOwnerPartitionAndDimensionAndKnowledgeDeletionCascadesChunks() {
        var note = create("Cascade", "Current text");
        indexer(repository, fake, config).indexOnce();
        assertThatThrownBy(() -> jdbc.sql("UPDATE knowledge_embedding_chunk SET owner_id = :other WHERE knowledge_id = :id")
                .param("other", OTHER).param("id", note.getId()).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("UPDATE knowledge_embedding_chunk SET embedding = '[1,0]' WHERE knowledge_id = :id")
                .param("id", note.getId()).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        knowledge.delete(note.getId());
        assertThat(rowCount()).isZero();
    }

    @Test
    void boundedCyclesAndFailuresDoNotStarveLaterBackfillNotes() {
        create("Bad", "provider fails for this input");
        for (int i = 0; i < 4; i++) create("Good " + i, "Current text");
        EmbeddingClient selective = inputs -> {
            assertThat(inputs.size()).isLessThanOrEqualTo(config.batchSize());
            if (inputs.get(0).startsWith("Title: Bad\n")) throw new EmbeddingUnavailableException();
            return fake.embed(inputs);
        };
        var indexer = indexer(repository, selective, config);
        assertThat(indexer.indexOnce()).isEqualTo(new KnowledgeEmbeddingIndexer.Summary(1, 1, 0, 1));
        assertThat(indexer.indexOnce().indexed()).isEqualTo(2);
        assertThat(indexer.indexOnce().indexed()).isEqualTo(1);
        assertThat(rowCount()).isEqualTo(4);
        assertThat(indexer.indexOnce().failures()).isEqualTo(1); // wraps and retries only the failed note
    }

    private Knowledge create(String title, String content) { return knowledge.create(title, "summary", content, Visibility.PRIVATE, null, List.of()); }
    private long rowCount() { return jdbc.sql("SELECT count(*) FROM knowledge_embedding_chunk").query(Long.class).single(); }
    private List<Long> chunkIds(long id) { return jdbc.sql("SELECT id FROM knowledge_embedding_chunk WHERE knowledge_id = :id ORDER BY id").param("id", id).query(Long.class).list(); }
    private void persist(long id, java.util.UUID owner, EmbeddingStrategy strategy, float[] vector) {
        assertThat(repository.replace(repository.findSource(owner, id).orElseThrow(), strategy,
                List.of(new MarkdownChunker.Chunk(0, "source excerpt")), List.of(vector))).isTrue();
    }
}
