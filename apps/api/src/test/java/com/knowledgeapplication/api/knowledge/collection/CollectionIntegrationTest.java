package com.knowledgeapplication.api.knowledge.collection;

import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.service.KnowledgeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
class CollectionIntegrationTest {

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
        registry.add("app.object-storage.bucket", () -> "unused-in-collection-tests");
        registry.add("app.object-storage.access-key", () -> "test-access-key");
        registry.add("app.object-storage.secret-key", () -> "test-secret-key");
        registry.add("app.knowledge.attachment-cleanup.enabled", () -> "false");
    }

    @Autowired CollectionService collections;
    @Autowired KnowledgeService knowledge;
    @Autowired JdbcClient jdbc;
    @Autowired WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        authenticateAsOwner();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.sql("TRUNCATE knowledge_attachment_delete_queue, knowledge_tag, knowledge, tag, knowledge_collection RESTART IDENTITY CASCADE")
                .update();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsEmptyCollectionsAndOwnerScopedCountsThenFiltersNotes() throws Exception {
        CollectionSummary backend = collections.create("  Backend   Platform  ");
        CollectionSummary empty = collections.create("Database");
        Knowledge note = knowledge.create("Backend note", null, "", Visibility.PRIVATE,
                "Backend Platform", List.of());
        long otherId = jdbc.sql("""
                INSERT INTO knowledge_collection (owner_id, name)
                VALUES (:owner, 'Other owner') RETURNING id
                """).param("owner", OTHER_OWNER_ID).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO knowledge (owner_id, title, slug, content, visibility, collection_id)
                VALUES (:owner, 'Other note', 'other-note', '', 'PRIVATE', :collection)
                """).param("owner", OTHER_OWNER_ID).param("collection", otherId).update();

        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/collections").with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(backend.id()))
                .andExpect(jsonPath("$[0].name").value("Backend Platform"))
                .andExpect(jsonPath("$[0].knowledgeCount").value(1))
                .andExpect(jsonPath("$[1].id").value(empty.id()))
                .andExpect(jsonPath("$[1].knowledgeCount").value(0))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/collections/{id}/knowledge", backend.id()).with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(note.getId()))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/collections/{id}/knowledge", empty.id()).with(ownerLogin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/collections/{id}", otherId).with(ownerLogin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COLLECTION_NOT_FOUND"));
        mvc.perform(get("/api/collections/{id}/knowledge", otherId).with(ownerLogin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void renameKeepsCollectionIdAndNotesWhileRevisionKeepsOldName() throws Exception {
        CollectionSummary original = collections.create("Backend");
        Knowledge note = knowledge.create("Stable note", null, "", Visibility.PRIVATE,
                "Backend", List.of());

        SecurityContextHolder.clearContext();
        mvc.perform(put("/api/collections/{id}", original.id()).with(ownerLogin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  Platform   Engineering  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original.id()))
                .andExpect(jsonPath("$.name").value("Platform Engineering"))
                .andExpect(jsonPath("$.knowledgeCount").value(1));
        mvc.perform(get("/api/knowledge/{id}", note.getId()).with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection").value("Platform Engineering"))
                .andExpect(jsonPath("$.slug").value(note.getSlug()));
        assertThat(jdbc.sql("SELECT collection_name FROM knowledge_revision WHERE knowledge_id = :id ORDER BY id LIMIT 1")
                .param("id", note.getId()).query(String.class).single()).isEqualTo("Backend");
        assertThat(jdbc.sql("SELECT collection_id FROM knowledge WHERE id = :id")
                .param("id", note.getId()).query(Long.class).single()).isEqualTo(original.id());
    }

    @Test
    void deleteUnassignsNotesWithoutDeletingNotesOrHistoricalNames() throws Exception {
        CollectionSummary collection = collections.create("Backend");
        Knowledge note = knowledge.create("Keep this note", null, "", Visibility.PRIVATE,
                "Backend", List.of());

        SecurityContextHolder.clearContext();
        mvc.perform(delete("/api/collections/{id}", collection.id()).with(ownerLogin()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/knowledge/{id}", note.getId()).with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection").isEmpty());
        mvc.perform(get("/api/collections/{id}", collection.id()).with(ownerLogin()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT collection_name FROM knowledge_revision WHERE knowledge_id = :id ORDER BY id LIMIT 1")
                .param("id", note.getId()).query(String.class).single()).isEqualTo("Backend");
    }

    @Test
    void validatesNamesConflictsOwnerBoundaryAndCsrf() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/collections")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/collections").with(ownerLogin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Backend\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/collections").with(ownerLogin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        var created = mvc.perform(post("/api/collections").with(ownerLogin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Backend\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andReturn();
        Number createdId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        long id = createdId.longValue();
        mvc.perform(post("/api/collections").with(ownerLogin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" backend \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLLECTION_NAME_CONFLICT"));
        mvc.perform(post("/api/collections").with(ownerLogin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Client owned\",\"ownerId\":\"" + OTHER_OWNER_ID + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        var second = mvc.perform(post("/api/collections").with(ownerLogin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Database\"}"))
                .andExpect(status().isCreated()).andReturn();
        Number secondId = com.jayway.jsonpath.JsonPath.read(second.getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/collections/{id}", secondId.longValue()).with(ownerLogin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" backend \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLLECTION_NAME_CONFLICT"));
        mvc.perform(put("/api/collections/{id}", id).with(ownerLogin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/collections/{id}", id).with(ownerLogin()))
                .andExpect(status().isForbidden());

        long otherId = jdbc.sql("""
                INSERT INTO knowledge_collection (owner_id, name)
                VALUES (:owner, 'Other owner') RETURNING id
                """).param("owner", OTHER_OWNER_ID).query(Long.class).single();
        mvc.perform(put("/api/collections/{id}", otherId).with(ownerLogin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Unauthorized\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/collections/{id}", otherId).with(ownerLogin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private static void authenticateAsOwner() {
        Instant now = Instant.now();
        var token = new OidcIdToken("test-token", now.minusSeconds(60), now.plusSeconds(300), Map.of(
                "sub", "owner-subject", "email", "owner@example.com", "email_verified", true
        ));
        var principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_OWNER")), token);
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), "google"
        ));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor ownerLogin() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))
                .idToken(token -> token.claim("email", "owner@example.com").claim("email_verified", true));
    }
}
