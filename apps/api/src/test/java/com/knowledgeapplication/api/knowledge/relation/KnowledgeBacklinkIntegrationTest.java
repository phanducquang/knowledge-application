package com.knowledgeapplication.api.knowledge.relation;

import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.service.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.core.context.SecurityContextHolder;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
class KnowledgeBacklinkIntegrationTest {

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
        registry.add("app.object-storage.bucket", () -> "unused-in-backlink-tests");
        registry.add("app.object-storage.access-key", () -> "test-access-key");
        registry.add("app.object-storage.secret-key", () -> "test-secret-key");
        registry.add("app.knowledge.attachment-cleanup.enabled", () -> "false");
    }

    @Autowired KnowledgeService knowledge;
    @Autowired JdbcClient jdbc;
    @Autowired WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        var token = new OidcIdToken("test-token", now.minusSeconds(60), now.plusSeconds(300), Map.of(
                "sub", "owner-subject", "email", "owner@example.com", "email_verified", true));
        var principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_OWNER")), token);
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), "google"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.sql("TRUNCATE knowledge_attachment_delete_queue, knowledge_tag, knowledge, tag, knowledge_collection RESTART IDENTITY CASCADE")
                .update();
    }

    @Test
    void findsOnlyOwnedProseLinksAndTracksEditsAndDeletion() throws Exception {
        Knowledge target = knowledge.create("Target Note", null, "", Visibility.PRIVATE, null, List.of());
        Knowledge source = knowledge.create("Source Note", null,
                "See [[target-note]] and `[[target-note]]`", Visibility.PRIVATE, null, List.of());
        knowledge.create("Code Example", null, "```\n[[target-note]]\n```", Visibility.PRIVATE, null, List.of());
        jdbc.sql("""
                INSERT INTO knowledge (owner_id, title, slug, content, visibility)
                VALUES (:owner, 'Other owner source', 'other-owner-source', '[[target-note]]', 'PRIVATE')
                """).param("owner", OTHER_OWNER_ID).update();

        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/knowledge/{id}/backlinks", target.getId()).with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(source.getId()))
                .andExpect(jsonPath("$[0].slug").value("source-note"))
                .andExpect(jsonPath("$[0].ownerId").doesNotExist());

        authenticateAsOwner();
        knowledge.update(source.getId(), "Renamed source", null, "No wiki links", Visibility.PRIVATE, null, List.of());
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/knowledge/{id}/backlinks", target.getId()).with(ownerLogin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        authenticateAsOwner();
        long initialRevisionId = jdbc.sql("SELECT id FROM knowledge_revision WHERE knowledge_id = :id ORDER BY id LIMIT 1")
                .param("id", source.getId()).query(Long.class).single();
        knowledge.restoreRevision(source.getId(), initialRevisionId);
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/knowledge/{id}/backlinks", target.getId()).with(ownerLogin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        authenticateAsOwner();
        knowledge.delete(source.getId());
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/knowledge/{id}/backlinks", target.getId()).with(ownerLogin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void hidesUnknownAndCrossOwnerTargetsAndRequiresAuthentication() throws Exception {
        long otherTarget = jdbc.sql("""
                INSERT INTO knowledge (owner_id, title, slug, content, visibility)
                VALUES (:owner, 'Other owner target', 'other-owner-target', '', 'PRIVATE') RETURNING id
                """).param("owner", OTHER_OWNER_ID).query(Long.class).single();
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/knowledge/{id}/backlinks", otherTarget))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/knowledge/{id}/backlinks", otherTarget).with(ownerLogin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_NOT_FOUND"));
        mvc.perform(get("/api/knowledge/99999/backlinks").with(ownerLogin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsEachSourceOnceInMostRecentlyUpdatedOrder() throws Exception {
        Knowledge target = knowledge.create("Target", null, "", Visibility.PRIVATE, null, List.of());
        Knowledge older = knowledge.create("Older", null, "[[target]] and [[target]]",
                Visibility.PRIVATE, null, List.of());
        Knowledge newer = knowledge.create("Newer", null, "See [[target]]",
                Visibility.PRIVATE, null, List.of());

        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/knowledge/{id}/backlinks", target.getId()).with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer.getId()))
                .andExpect(jsonPath("$[1].id").value(older.getId()));
    }

    private static void authenticateAsOwner() {
        Instant now = Instant.now();
        var token = new OidcIdToken("test-token", now.minusSeconds(60), now.plusSeconds(300), Map.of(
                "sub", "owner-subject", "email", "owner@example.com", "email_verified", true));
        var principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_OWNER")), token);
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), "google"));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor ownerLogin() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))
                .idToken(token -> token.claim("email", "owner@example.com").claim("email_verified", true));
    }
}
