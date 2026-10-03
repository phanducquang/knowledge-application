package com.knowledgeapplication.api.search;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.exception.ApiExceptionHandler;
import com.knowledgeapplication.api.knowledge.embedding.*;
import com.knowledgeapplication.api.knowledge.model.*;
import com.knowledgeapplication.api.knowledge.service.KnowledgeService;
import com.knowledgeapplication.api.knowledge.revision.service.KnowledgeRevisionService;
import com.knowledgeapplication.api.search.controller.KnowledgeSemanticSearchController;
import com.knowledgeapplication.api.search.service.KnowledgeSemanticSearchService;
import com.knowledgeapplication.api.testsupport.TestContainerImages;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
class KnowledgeSemanticSearchIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TestContainerImages.POSTGRES);
    static final UUID OWNER = KnowledgeSemanticSearchServiceTest.OWNER;
    static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.auth.allowed-email", () -> "owner@example.com");
        registry.add("app.object-storage.endpoint", () -> "http://127.0.0.1:19000");
        registry.add("app.object-storage.bucket", () -> "unused-semantic-tests");
        registry.add("app.object-storage.access-key", () -> "test-key");
        registry.add("app.object-storage.secret-key", () -> "test-secret");
        registry.add("app.knowledge.attachment-cleanup.enabled", () -> "false");
        registry.add("app.embedding.enabled", () -> "false");
    }
    @Autowired KnowledgeEmbeddingRepository repository;
    @Autowired KnowledgeService knowledge;
    @Autowired KnowledgeRevisionService revisions;
    @Autowired CurrentOwner owner;
    @Autowired JdbcClient jdbc;
    @Autowired WebApplicationContext context;
    EmbeddingProperties properties;
    EmbeddingStrategy strategy;
    EmbeddingClient client;
    KnowledgeSemanticSearchService service;
    MockMvc http;

    @BeforeEach void setup() {
        jdbc.sql("TRUNCATE knowledge_attachment_delete_queue, knowledge, tag, knowledge_collection RESTART IDENTITY CASCADE").update();
        var token = new OidcIdToken("test-token", Instant.now().minusSeconds(60), Instant.now().plusSeconds(300), Map.of(
                "sub", "test-owner", "email", "owner@example.com", "email_verified", true));
        var principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_OWNER")), token);
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));
        properties = KnowledgeSemanticSearchServiceTest.properties(true);
        strategy = new EmbeddingStrategy(properties, 1);
        client = mock(EmbeddingClient.class);
        when(client.embed(anyList())).thenReturn(List.of(new float[]{1, 0, 0}));
        service = new KnowledgeSemanticSearchService(owner, provider(client), properties, strategy, repository);
        http = MockMvcBuilders.standaloneSetup(new KnowledgeSemanticSearchController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .apply(springSecurity(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class))).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    org.springframework.beans.factory.ObjectProvider<EmbeddingClient> provider(EmbeddingClient embedding) {
        return new StaticListableBeanFactory(Map.of("client", embedding)).getBeanProvider(EmbeddingClient.class);
    }
    Knowledge create(String title, Visibility visibility) {
        return knowledge.create(title, "Summary", "Current " + title, visibility, "Backend", List.of("WebFlux", "Java"));
    }
    void persist(long id, UUID ownerId, EmbeddingStrategy selected, List<String> texts, List<float[]> vectors) {
        var chunks = new ArrayList<MarkdownChunker.Chunk>();
        for (int i = 0; i < texts.size(); i++) chunks.add(new MarkdownChunker.Chunk(i, texts.get(i)));
        assertThat(repository.replace(repository.findSource(ownerId, id).orElseThrow(), selected, chunks, vectors)).isTrue();
    }
    void persist(Knowledge note, float[] vector) { persist(note.getId(), OWNER, strategy, List.of(note.getContent()), List.of(vector)); }

    @Test void picksBestChunkBeforeNoteLimitAndReturnsNormalMetadataOfAllVisibilities() {
        var a = create("Proxy", Visibility.PRIVATE); var b = create("Timeout", Visibility.PUBLIC); var c = create("Gateway", Visibility.UNLISTED);
        persist(a.getId(), OWNER, strategy, List.of("less good", "best context", "another match"),
                List.of(new float[]{.8f, .6f, 0}, new float[]{1, 0, 0}, new float[]{.99f, .1f, 0}));
        persist(b, new float[]{.7f, .7f, 0}); persist(c, new float[]{0, 1, 0});
        var results = service.search("meaning not in the title", 2);
        assertThat(results).extracting(KnowledgeEmbeddingRepository.NearestKnowledge::id).containsExactly(a.getId(), b.getId());
        assertThat(results.get(0).chunkIndex()).isEqualTo(1); assertThat(results.get(0).chunkText()).isEqualTo("best context");
        assertThat(results.get(0).collection()).isEqualTo("Backend"); assertThat(results.get(0).tags()).containsExactly("Java", "WebFlux");
        assertThat(service.search("query", 20)).extracting(KnowledgeEmbeddingRepository.NearestKnowledge::visibility)
                .containsExactly(Visibility.PRIVATE, Visibility.PUBLIC, Visibility.UNLISTED);
    }

    @Test void filtersOtherOwnerAndEveryIncompatibleOrIncompleteSetBeforeRanking() {
        var current = create("Current", Visibility.PRIVATE); persist(current, new float[]{.8f, .6f, 0});
        long other = jdbc.sql("INSERT INTO knowledge(owner_id, title, slug, content) VALUES(:owner, 'Private other title', 'other', 'Other') RETURNING id")
                .param("owner", OTHER).query(Long.class).single();
        persist(other, OTHER, strategy, List.of("Private other chunk"), List.of(new float[]{1, 0, 0}));
        for (String field : List.of("model", "dimensions", "version", "hash", "incomplete")) {
            var note = create(field, Visibility.PRIVATE);
            persist(note.getId(), OWNER, strategy, List.of("first", "second"), List.of(new float[]{1, 0, 0}, new float[]{1, 0, 0}));
            String sql = switch (field) {
                case "model" -> "UPDATE knowledge_embedding_chunk SET embedding_model='old-model' WHERE knowledge_id=:id";
                case "dimensions" -> "UPDATE knowledge_embedding_chunk SET embedding='[1,0]', embedding_dimensions=2 WHERE knowledge_id=:id";
                case "version" -> "UPDATE knowledge_embedding_chunk SET chunker_version=2 WHERE knowledge_id=:id";
                case "hash" -> "UPDATE knowledge_embedding_chunk SET source_hash=repeat('0',64) WHERE knowledge_id=:id";
                default -> "DELETE FROM knowledge_embedding_chunk WHERE knowledge_id=:id AND chunk_index=1";
            };
            jdbc.sql(sql).param("id", note.getId()).update();
        }
        assertThat(service.search("query", 1)).extracting(KnowledgeEmbeddingRepository.NearestKnowledge::id).containsExactly(current.getId());
        assertThat(repository.findNearestKnowledge(OTHER, strategy, new float[]{1, 0, 0}, 50))
                .extracting(KnowledgeEmbeddingRepository.NearestKnowledge::id).containsExactly(other);
    }

    @Test void appliesDistanceThenUpdatedAtThenIdTiesAndStableBestChunkPosition() {
        var a = create("A", Visibility.PRIVATE); var b = create("B", Visibility.PRIVATE); var c = create("C", Visibility.PRIVATE);
        for (var note : List.of(a, b, c)) persist(note.getId(), OWNER, strategy, List.of("first", "second"), List.of(new float[]{1, 0, 0}, new float[]{1, 0, 0}));
        jdbc.sql("UPDATE knowledge SET updated_at='2026-10-01T00:00:00Z'").update();
        jdbc.sql("UPDATE knowledge SET updated_at='2026-10-02T00:00:00Z' WHERE id=:id").param("id", a.getId()).update();
        for (int repeat = 0; repeat < 3; repeat++) {
            var results = service.search("query", 2);
            assertThat(results).extracting(KnowledgeEmbeddingRepository.NearestKnowledge::id).containsExactly(a.getId(), c.getId());
            assertThat(results).extracting(KnowledgeEmbeddingRepository.NearestKnowledge::chunkIndex).containsOnly(0);
        }
    }

    @Test void editImmediatelyHidesOldIndexThenBackgroundIndexerMakesNewStateSearchable() {
        var note = create("Cache", Visibility.PRIVATE); persist(note, new float[]{1, 0, 0});
        assertThat(service.search("query", 20)).hasSize(1);
        knowledge.update(note.getId(), "Cache", "Summary", "Changed PostgreSQL context", Visibility.PRIVATE, "Backend", List.of());
        assertThat(service.search("query", 20)).isEmpty();
        var indexer = new KnowledgeEmbeddingIndexer(repository, provider(new DeterministicEmbeddingClient(3)), properties,
                strategy, new MarkdownChunker(256, 20), OWNER);
        assertThat(indexer.indexOnce().indexed()).isEqualTo(1);
        assertThat(service.search("query", 20).get(0).chunkText()).contains("Changed PostgreSQL context");
    }

    @Test void deleteCascadesAndQueriesAreNeverPersistedOrUsedForBackfill() {
        var note = create("Cascade", Visibility.PRIVATE); persist(note, new float[]{1, 0, 0});
        long before = jdbc.sql("SELECT count(*) FROM knowledge_embedding_chunk").query(Long.class).single();
        service.search("query", 20);
        assertThat(jdbc.sql("SELECT count(*) FROM knowledge_embedding_chunk").query(Long.class).single()).isEqualTo(before);
        knowledge.delete(note.getId()); assertThat(service.search("query", 20)).isEmpty();
        create("Unindexed", Visibility.PRIVATE); assertThat(service.search("query", 20)).isEmpty();
    }

    @Test void historicalRevisionOnlyAppearsAfterRestoreAndReindexOfCurrentAuthoringState() {
        var note = create("Revision", Visibility.PRIVATE);
        long original = revisions.list(note.getId(), 0, 20).getContent().get(0).getId();
        knowledge.update(note.getId(), "Revision", "Summary", "Current replacement context", Visibility.PRIVATE, null, List.of());
        var indexer = new KnowledgeEmbeddingIndexer(repository, provider(new DeterministicEmbeddingClient(3)), properties,
                strategy, new MarkdownChunker(256, 20), OWNER);
        indexer.indexOnce();
        assertThat(service.search("query", 20).get(0).chunkText()).isEqualTo("Current replacement context");
        knowledge.restoreRevision(note.getId(), original);
        assertThat(service.search("query", 20)).isEmpty();
        indexer.indexOnce();
        assertThat(service.search("query", 20).get(0).chunkText()).isEqualTo("Current Revision");
    }

    @Test void httpContractIsOwnerOnlyBoundedPlainTextAndHidesVectorDetails() throws Exception {
        var note = create("HTTP", Visibility.PRIVATE);
        persist(note.getId(), OWNER, strategy, List.of("<script>source</script> " + "x".repeat(800)), List.of(new float[]{1, 0, 0}));
        http.perform(get("/api/search/knowledge/semantic").with(ownerLogin())
                        .param("q", "query").param("ownerId", OTHER.toString()).param("model", "ignored"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store, max-age=0"))
                .andExpect(jsonPath("$[0].id").value(note.getId())).andExpect(jsonPath("$[0].match.chunkIndex").value(0))
                .andExpect(jsonPath("$[0].match.text").value(org.hamcrest.Matchers.startsWith("<script>source</script>")))
                .andExpect(jsonPath("$[0].ownerId").doesNotExist()).andExpect(jsonPath("$[0].embedding").doesNotExist())
                .andExpect(jsonPath("$[0].distance").doesNotExist()).andExpect(jsonPath("$[0].embeddingModel").doesNotExist());
        assertThat(KnowledgeSemanticSearchService.excerpt("x".repeat(800))).hasSize(600);
        verify(client, times(1)).embed(List.of("query"));
        http.perform(get("/api/search/knowledge/semantic").with(anonymous()).param("q", "query"))
                .andExpect(status().isUnauthorized());
    }

    @Test void httpValidatesQueryAndLimitBeforeProvider() throws Exception {
        for (String query : List.of("", "  ", "x".repeat(201))) http.perform(get("/api/search/knowledge/semantic")
                .with(ownerLogin()).param("q", query))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        for (String limit : List.of("0", "51", "abc")) http.perform(get("/api/search/knowledge/semantic")
                .with(ownerLogin()).param("q", "query").param("limit", limit))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(client);
    }

    @Test void httpDisabledAndProviderFailureAreDistinctSafeUnavailableResponses() throws Exception {
        var real = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        real.perform(get("/api/search/knowledge/semantic").with(ownerLogin()).param("q", "query"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SEMANTIC_SEARCH_DISABLED"));
        when(client.embed(anyList())).thenThrow(new IllegalStateException("secret upstream body Authorization"));
        http.perform(get("/api/search/knowledge/semantic").with(ownerLogin()).param("q", "query"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SEMANTIC_SEARCH_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Semantic search is temporarily unavailable"));
        verify(client, times(1)).embed(List.of("query"));
    }
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor ownerLogin() {
        return oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))
                .idToken(t -> t.claim("email", "owner@example.com").claim("email_verified", true));
    }
}
