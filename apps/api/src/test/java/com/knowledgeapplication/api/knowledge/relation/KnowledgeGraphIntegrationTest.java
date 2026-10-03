package com.knowledgeapplication.api.knowledge.relation;

import com.knowledgeapplication.api.knowledge.collection.CollectionService;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.service.KnowledgeService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
class KnowledgeGraphIntegrationTest {
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.owner-id", OWNER_ID::toString);
        registry.add("app.auth.allowed-email", () -> "owner@example.com");
        registry.add("app.object-storage.endpoint", () -> "http://127.0.0.1:19000");
        registry.add("app.object-storage.bucket", () -> "unused-in-graph-tests");
        registry.add("app.object-storage.access-key", () -> "test-access-key");
        registry.add("app.object-storage.secret-key", () -> "test-secret-key");
        registry.add("app.knowledge.attachment-cleanup.enabled", () -> "false");
    }

    @Autowired KnowledgeService knowledge;
    @Autowired KnowledgeRelationService relations;
    @Autowired CollectionService collections;
    @Autowired JdbcClient jdbc;
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        authenticate();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.sql("TRUNCATE knowledge_attachment_delete_queue, knowledge_tag, knowledge, tag, knowledge_collection RESTART IDENTITY CASCADE")
                .update();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void includesEmptyLibraryAndEveryVisibilityWithIsolatedNodesAndOrderedMetadata() {
        assertThat(relations.graph()).isEqualTo(new KnowledgeGraphResponse(List.of(), List.of()));
        var first = create("First", "", Visibility.PRIVATE, "Backend", "Spring", "Java");
        assertThat(relations.graph().nodes()).hasSize(1);
        assertThat(relations.graph().edges()).isEmpty();
        var second = create("Second", "", Visibility.PUBLIC, "Backend", "Spring");
        var third = create("Third", "", Visibility.UNLISTED, null);
        jdbc.sql("UPDATE knowledge SET updated_at = '2020-01-01T00:00:00Z' WHERE owner_id = :owner")
                .param("owner", OWNER_ID).update();
        var graph = relations.graph();
        assertThat(graph.nodes()).extracting(KnowledgeGraphResponse.Node::id)
                .containsExactly(third.getId(), second.getId(), first.getId());
        assertThat(graph.nodes().get(2).tags()).containsExactly("Java", "Spring");
        assertThat(graph.nodes().get(2).collection()).isEqualTo("Backend");
        assertThat(graph.nodes().get(2).collectionId()).isEqualTo(first.getCollection().getId());
        assertThat(graph.nodes().get(0).collection()).isNull();
        assertThat(graph.edges()).isEmpty(); // shared tags/collection are not edges
        assertThat(relations.graph()).isEqualTo(graph);
        jdbc.sql("UPDATE knowledge SET updated_at = '2021-01-01T00:00:00Z' WHERE id = :id")
                .param("id", first.getId()).update();
        assertThat(relations.graph().nodes()).extracting(KnowledgeGraphResponse.Node::id)
                .containsExactly(first.getId(), third.getId(), second.getId());
    }

    @Test
    void resolvesOnlyOwnedDirectedWikiEdgesOnceAndKeepsIsolatedNodes() {
        var alpha = create("Alpha", "[[gamma]] [[beta]] [[beta]] [[alpha]] [[missing]] [[other-owner]]",
                Visibility.PRIVATE, null);
        var beta = create("Beta", "[[alpha]]", Visibility.PRIVATE, null);
        var gamma = create("Gamma", "", Visibility.PRIVATE, null);
        var isolated = create("Isolated", "", Visibility.PRIVATE, null);
        jdbc.sql("""
                INSERT INTO knowledge (owner_id, title, slug, content, visibility)
                VALUES (:owner, 'Other owner', 'other-owner', '[[alpha]]', 'PUBLIC')
                """).param("owner", OTHER_OWNER_ID).update();
        var graph = relations.graph();
        assertThat(graph.nodes()).extracting(KnowledgeGraphResponse.Node::id)
                .containsExactly(isolated.getId(), gamma.getId(), beta.getId(), alpha.getId());
        assertThat(graph.edges()).containsExactly(edge(alpha, beta), edge(alpha, gamma), edge(beta, alpha));
        assertThat(relations.backlinks(beta.getId())).extracting(KnowledgeBacklinkResponse::id)
                .containsExactly(alpha.getId());
        assertThat(relations.related(beta.getId(), 20)).extracting(KnowledgeRelatedResponse::id)
                .containsExactly(alpha.getId());
        assertThat(relations.graph()).isEqualTo(graph);
    }

    @Test
    void usesExistingCodeMermaidEscapeAndInlineLinkExclusionsWithoutMutatingMarkdown() {
        create("Target", "", Visibility.PRIVATE, null);
        String content = """
                `[[target]]`

                ```java
                String x = "[[target]]";
                ```

                ```mermaid
                flowchart LR
                  A["[[target]]"] --> B
                ```

                \\[[target]]

                    [[target]]

                [ordinary [[target]] label](https://example.com)
                """;
        var source = create("Source", content, Visibility.PRIVATE, null);
        assertThat(relations.graph().edges()).isEmpty();
        assertThat(knowledge.getById(source.getId()).getContent()).isEqualTo(content);
    }

    @Test
    void preservesEdgesAcrossTitleVisibilityCollectionAndTagChangesButNotRetainedRevisions() {
        var target = create("Target", "", Visibility.PRIVATE, "Backend", "Spring");
        var source = create("Source", "[[target]]", Visibility.UNLISTED, "Backend", "Spring");
        long revision = jdbc.sql("SELECT id FROM knowledge_revision WHERE knowledge_id = :id ORDER BY id LIMIT 1")
                .param("id", source.getId()).query(Long.class).single();
        collections.rename(target.getCollection().getId(), "Platform");
        knowledge.update(target.getId(), "Renamed target", null, "", Visibility.PUBLIC, "Platform", List.of("Java"));
        assertThat(relations.graph().edges()).containsExactly(edge(source, target));
        var targetNode = relations.graph().nodes().stream().filter(node -> node.id().equals(target.getId())).findFirst().orElseThrow();
        assertThat(targetNode.title()).isEqualTo("Renamed target");
        assertThat(targetNode.slug()).isEqualTo("target");
        assertThat(targetNode.collection()).isEqualTo("Platform");
        assertThat(targetNode.tags()).containsExactly("Java");
        knowledge.update(source.getId(), "Renamed source", null, "No current link", Visibility.PUBLIC, null, List.of());
        assertThat(relations.graph().edges()).isEmpty(); // CREATE revision still contains the old link
        var beforeRestore = knowledge.getById(source.getId());
        var restored = knowledge.restoreRevision(source.getId(), revision);
        assertThat(relations.graph().edges()).containsExactly(edge(source, target));
        assertThat(restored.getSlug()).isEqualTo("source");
        assertThat(restored.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(restored.getVisibility()).isEqualTo(beforeRestore.getVisibility());
        assertThat(restored.getPublishedAt()).isEqualTo(beforeRestore.getPublishedAt());
        assertThat(restored.getShareToken()).isEqualTo(beforeRestore.getShareToken()).isNotBlank();
    }

    @Test
    void deletedTargetsRemoveOnlyTheirNodeAndIncomingEdges() {
        var target = create("Target", "", Visibility.PRIVATE, null);
        var source = create("Source", "[[target]]", Visibility.PRIVATE, null);
        knowledge.delete(target.getId());
        assertThat(relations.graph().nodes()).extracting(KnowledgeGraphResponse.Node::id).containsExactly(source.getId());
        assertThat(relations.graph().edges()).isEmpty();
    }

    @Test
    void graphRouteIsAuthenticatedCompactAndSeparateFromExternalArticleApis() throws Exception {
        var note = create("Note", "", Visibility.PUBLIC, "Backend", "Java");
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/knowledge/graph")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/knowledge/graph").param("ownerId", OTHER_OWNER_ID.toString()).with(ownerLogin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nodes.length()").value(1))
                .andExpect(jsonPath("$.nodes[0].id").value(note.getId()))
                .andExpect(jsonPath("$.nodes[0].slug").value("note"))
                .andExpect(jsonPath("$.nodes[0].ownerId").doesNotExist())
                .andExpect(jsonPath("$.nodes[0].content").doesNotExist())
                .andExpect(jsonPath("$.nodes[0].shareToken").doesNotExist())
                .andExpect(jsonPath("$.edges.length()").value(0));
        mvc.perform(get("/api/knowledge/graph").with(ownerLogin()
                        .idToken(token -> token.claim("email", "outsider@example.com").claim("email_verified", true))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/public/knowledge/graph")).andExpect(status().isNotFound());
        mvc.perform(get("/api/shared/knowledge/graph")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/knowledge/note")).andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").doesNotExist()).andExpect(jsonPath("$.edges").doesNotExist());
        authenticate();
        var shared = knowledge.updateVisibility(note.getId(), Visibility.UNLISTED);
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/shared/knowledge/{token}", shared.getShareToken())).andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").doesNotExist()).andExpect(jsonPath("$.edges").doesNotExist());
    }

    private Knowledge create(String title, String content, Visibility visibility, String collection, String... tags) {
        return knowledge.create(title, null, content, visibility, collection, List.of(tags));
    }

    private static KnowledgeGraphResponse.Edge edge(Knowledge source, Knowledge target) {
        return new KnowledgeGraphResponse.Edge(source.getId(), target.getId());
    }

    private static void authenticate() {
        Instant now = Instant.now();
        var token = new OidcIdToken("test-token", now.minusSeconds(60), now.plusSeconds(300), Map.of(
                "sub", "owner-subject", "email", "owner@example.com", "email_verified", true));
        var principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_OWNER")), token);
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), "google"));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor ownerLogin() {
        return oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))
                .idToken(token -> token.claim("email", "owner@example.com").claim("email_verified", true));
    }
}
