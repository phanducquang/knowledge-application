package com.knowledgeapplication.api.knowledge.relation;

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

import static com.knowledgeapplication.api.knowledge.relation.KnowledgeRelationReason.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
class KnowledgeRelatedIntegrationTest {
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
        registry.add("app.object-storage.bucket", () -> "unused-in-related-tests");
        registry.add("app.object-storage.access-key", () -> "test-access-key");
        registry.add("app.object-storage.secret-key", () -> "test-secret-key");
        registry.add("app.knowledge.attachment-cleanup.enabled", () -> "false");
    }

    @Autowired KnowledgeService knowledge;
    @Autowired KnowledgeRelationService relations;
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
    void mergesSignalsAndRanksExplicitThenSharedTagCountThenCollection() {
        var target = create("Target", "[[both]] [[both]] [[outgoing]] [[target]] [[missing]]", "Backend", "Spring", "Java");
        var both = create("Both", "[[target]]", "Backend", "Spring", "Java");
        var outgoing = create("Outgoing", "", null);
        var backlink = create("Backlink", "[[target]]", null);
        var twoTags = create("Two tags", "", null, "Spring", "Java");
        var oneTag = create("One tag", "", "Backend", "Spring");
        var collection = create("Collection only", "", "Backend");
        setUpdatedAt(both, "2020-01-01T00:00:00Z");
        setUpdatedAt(outgoing, "2021-01-01T00:00:00Z");
        setUpdatedAt(backlink, "2021-01-01T00:00:00Z");

        var result = relations.related(target.getId(), 20);
        assertThat(result).extracting(KnowledgeRelatedResponse::id)
                .containsExactly(both.getId(), backlink.getId(), outgoing.getId(), twoTags.getId(), oneTag.getId(), collection.getId());
        assertThat(result.get(0).reasons()).containsExactly(WIKI_LINK, BACKLINK, SHARED_TAG, SAME_COLLECTION);
        assertThat(result.get(1).reasons()).containsExactly(BACKLINK);
        assertThat(result.get(2).reasons()).containsExactly(WIKI_LINK);
        assertThat(result.get(3).reasons()).containsExactly(SHARED_TAG);
        assertThat(result.get(4).reasons()).containsExactly(SHARED_TAG, SAME_COLLECTION);
        assertThat(result.get(5).reasons()).containsExactly(SAME_COLLECTION);
        assertThat(relations.related(target.getId(), 20)).isEqualTo(result);
        assertThat(relations.related(target.getId(), 2)).isEqualTo(result.subList(0, 2));
    }

    @Test
    void excludesNullCollectionsSelfMissingDeletedOtherOwnersAndNonProseWikiReferences() {
        var target = create("Target", "[[target]] [[deleted]] [[other-owner]] [[missing]]", null);
        var deleted = create("Deleted", "", null);
        knowledge.delete(deleted.getId());
        create("Unassigned", "", null);
        create("Code only", "`[[target]]`\n\n```mermaid\n[[target]]\n```\n\n\\[[target]]\n\n    [[target]]", null);
        jdbc.sql("""
                INSERT INTO knowledge (owner_id, title, slug, content, visibility)
                VALUES (:owner, 'Other owner', 'other-owner', '[[target]]', 'PRIVATE')
                """).param("owner", OTHER_OWNER_ID).update();
        assertThat(relations.related(target.getId(), 20)).isEmpty();

        knowledge.update(target.getId(), "Target", null, "`[[unassigned]]`\n\n```\n[[unassigned]]\n```\n\n\\[[unassigned]]",
                Visibility.PRIVATE, null, List.of());
        assertThat(relations.related(target.getId(), 20)).isEmpty();
    }

    @Test
    void reflectsEditsTitleRenameDeletionAndUsesOnlyCurrentContentUntilRestore() {
        var target = create("Target", "", null);
        var source = knowledge.create("Source", "Useful summary", "[[target]]", Visibility.UNLISTED, null, List.of());
        long revisionId = jdbc.sql("SELECT id FROM knowledge_revision WHERE knowledge_id = :id ORDER BY id LIMIT 1")
                .param("id", source.getId()).query(Long.class).single();
        assertThat(relations.related(target.getId(), 20)).extracting(KnowledgeRelatedResponse::id).containsExactly(source.getId());

        knowledge.update(source.getId(), "Renamed source", "Useful summary", "No relation", Visibility.PUBLIC, null, List.of());
        assertThat(relations.related(target.getId(), 20)).isEmpty(); // retained CREATE revision is not a candidate
        var beforeRestore = knowledge.getById(source.getId());
        var restored = knowledge.restoreRevision(source.getId(), revisionId);
        assertThat(restored.getSlug()).isEqualTo("source");
        assertThat(restored.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(restored.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(restored.getPublishedAt()).isEqualTo(beforeRestore.getPublishedAt());
        assertThat(restored.getShareToken()).isEqualTo(beforeRestore.getShareToken()).isNotBlank();
        assertThat(restored.getShareTokenCreatedAt()).isEqualTo(beforeRestore.getShareTokenCreatedAt());
        assertThat(relations.related(target.getId(), 20)).extracting(KnowledgeRelatedResponse::id).containsExactly(source.getId());

        knowledge.update(target.getId(), "Renamed target", null, "[[source]]", Visibility.PRIVATE, null, List.of());
        assertThat(relations.related(target.getId(), 20).get(0).reasons()).containsExactly(WIKI_LINK, BACKLINK);
        assertThat(relations.related(source.getId(), 20).get(0).title()).isEqualTo("Renamed target");
        assertThat(relations.related(source.getId(), 20).get(0).slug()).isEqualTo("target");
        knowledge.update(source.getId(), "Current source title", "Useful summary", "No relation", Visibility.PUBLIC, null, List.of());
        var result = relations.related(target.getId(), 20);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Current source title");
        assertThat(result.get(0).slug()).isEqualTo("source");
        assertThat(result.get(0).summary()).isEqualTo("Useful summary");
        assertThat(result.get(0).reasons()).containsExactly(WIKI_LINK);
        knowledge.delete(source.getId());
        assertThat(relations.related(target.getId(), 20)).isEmpty();
    }

    @Test
    void reflectsMetadataReplacementAndActualCollectionDeletion() {
        var target = create("Target", "", "Backend", "Spring");
        var candidate = create("Candidate", "", null);
        assertThat(relations.related(target.getId(), 20)).isEmpty();
        knowledge.update(candidate.getId(), "Candidate", null, "", Visibility.PRIVATE, "backend", List.of("spring"));
        assertThat(relations.related(target.getId(), 20).get(0).reasons()).containsExactly(SHARED_TAG, SAME_COLLECTION);
        knowledge.update(candidate.getId(), "Candidate", null, "", Visibility.PRIVATE, "Backend", List.of());
        assertThat(relations.related(target.getId(), 20).get(0).reasons()).containsExactly(SAME_COLLECTION);
        jdbc.sql("DELETE FROM knowledge_collection WHERE id = :id").param("id", target.getCollection().getId()).update();
        assertThat(relations.related(target.getId(), 20)).isEmpty();
    }

    @Test
    void breaksEqualSignalTiesByUpdatedAtThenId() {
        var target = create("Target", "", null, "Spring");
        var first = create("First", "", null, "Spring");
        var second = create("Second", "", null, "Spring");
        setUpdatedAt(first, "2021-01-01T00:00:00Z");
        setUpdatedAt(second, "2021-01-01T00:00:00Z");
        assertThat(relations.related(target.getId(), 20)).extracting(KnowledgeRelatedResponse::id)
                .containsExactly(second.getId(), first.getId());
        setUpdatedAt(first, "2022-01-01T00:00:00Z");
        assertThat(relations.related(target.getId(), 20)).extracting(KnowledgeRelatedResponse::id)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void validatesHttpLimitsAndReturnsCompactOwnerOnlyResponses() throws Exception {
        var target = create("Target", "", "Backend");
        for (int i = 0; i < 6; i++) create("Candidate " + i, "", "Backend");
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/knowledge/{id}/related", target.getId()).with(ownerLogin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].reasons[0]").value("SAME_COLLECTION"))
                .andExpect(jsonPath("$[0].ownerId").doesNotExist())
                .andExpect(jsonPath("$[0].content").doesNotExist())
                .andExpect(jsonPath("$[0].shareToken").doesNotExist());
        mvc.perform(get("/api/knowledge/{id}/related", target.getId()).param("limit", "1").with(ownerLogin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/knowledge/{id}/related", target.getId()).param("limit", "20").with(ownerLogin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
        for (String invalid : List.of("0", "-1", "21", "abc")) {
            mvc.perform(get("/api/knowledge/{id}/related", target.getId()).param("limit", invalid).with(ownerLogin()))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/knowledge/99999/related").with(ownerLogin())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_NOT_FOUND"));
        mvc.perform(get("/api/knowledge/{id}/related", target.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/knowledge/{id}/related", target.getId()).with(ownerLogin()
                        .idToken(token -> token.claim("email", "outsider@example.com").claim("email_verified", true))))
                .andExpect(status().isForbidden());
        long otherId = jdbc.sql("""
                INSERT INTO knowledge (owner_id, title, slug, content, visibility)
                VALUES (:owner, 'Other target', 'other-target', '', 'PRIVATE') RETURNING id
                """).param("owner", OTHER_OWNER_ID).query(Long.class).single();
        mvc.perform(get("/api/knowledge/{id}/related", otherId).with(ownerLogin())).andExpect(status().isNotFound());
    }

    @Test
    void externalReadModelsDoNotExposeRelationsEvenForAnOwnerSession() throws Exception {
        var target = knowledge.create("Public target", null, "[[private-note]]", Visibility.PUBLIC, "Backend", List.of("Spring"));
        create("Private note", "[[public-target]]", "Backend", "Spring");
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/public/knowledge/{slug}", target.getSlug()).with(ownerLogin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.related").doesNotExist())
                .andExpect(jsonPath("$.reasons").doesNotExist()).andExpect(jsonPath("$.backlinks").doesNotExist());
        authenticate();
        var unlisted = knowledge.updateVisibility(target.getId(), Visibility.UNLISTED);
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/shared/knowledge/{token}", unlisted.getShareToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.related").doesNotExist())
                .andExpect(jsonPath("$.reasons").doesNotExist()).andExpect(jsonPath("$.backlinks").doesNotExist());
    }

    private Knowledge create(String title, String content, String collection, String... tags) {
        return knowledge.create(title, null, content, Visibility.PRIVATE, collection, List.of(tags));
    }

    private void setUpdatedAt(Knowledge note, String timestamp) {
        jdbc.sql("UPDATE knowledge SET updated_at = CAST(:time AS timestamptz) WHERE id = :id")
                .param("time", timestamp).param("id", note.getId()).update();
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
