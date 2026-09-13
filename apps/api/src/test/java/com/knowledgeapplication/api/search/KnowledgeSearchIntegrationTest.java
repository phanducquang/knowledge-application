package com.knowledgeapplication.api.search;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.service.KnowledgeService;
import com.knowledgeapplication.api.search.model.KnowledgeSearchResult;
import com.knowledgeapplication.api.search.service.KnowledgeSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
class KnowledgeSearchIntegrationTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.owner-id", OWNER_ID::toString);
        registry.add("app.auth.allowed-email", () -> "owner@example.com");
        registry.add("app.object-storage.endpoint", () -> "http://127.0.0.1:19000");
        registry.add("app.object-storage.region", () -> "us-east-1");
        registry.add("app.object-storage.bucket", () -> "unused-in-search-tests");
        registry.add("app.object-storage.access-key", () -> "test-access-key");
        registry.add("app.object-storage.secret-key", () -> "test-secret-key");
    }

    @Autowired
    KnowledgeSearchService searchService;

    @Autowired
    KnowledgeService knowledgeService;

    @Autowired
    CurrentOwner currentOwner;

    @Autowired
    JdbcClient jdbcClient;

    MockMvc mockMvc;

    @Autowired
    WebApplicationContext webApplicationContext;

    @BeforeEach
    void clearDatabase() {
        authenticateAsOwner();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        jdbcClient.sql("TRUNCATE knowledge_tag, knowledge, tag, knowledge_collection RESTART IDENTITY CASCADE")
                .update();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchesAllFieldsWithDocumentedWeightsAndExcludesOtherOwner() throws Exception {
        insertKnowledge(OWNER_ID, "WebClient title", "title-match", "none", "none", null, null,
                Instant.parse("2026-09-10T01:00:00Z"));
        insertKnowledge(OWNER_ID, "Metadata note", "tag-match", "none", "none", null, "WebClient",
                Instant.parse("2026-09-10T02:00:00Z"));
        insertKnowledge(OWNER_ID, "Summary note", "summary-match", "WebClient summary", "none", null, null,
                Instant.parse("2026-09-10T03:00:00Z"));
        insertKnowledge(OWNER_ID, "Content note", "content-match", "none", "Markdown mentions WebClient", null, null,
                Instant.parse("2026-09-10T04:00:00Z"));
        insertKnowledge(OTHER_OWNER_ID, "WebClient secret", "other-owner", "none", "none", null, null,
                Instant.parse("2026-09-10T05:00:00Z"));

        var response = searchService.search("WEBCLIENT", 20);

        assertThat(slugs(response)).containsExactly(
                "title-match", "tag-match", "summary-match", "content-match"
        );
        assertThat(slugs(response)).doesNotContain("other-owner");
    }

    @Test
    void searchesCollectionTagPhraseAndMarkdownContentWithSafeUserSyntax() throws Exception {
        insertKnowledge(OWNER_ID, "Connection patterns", "connection-patterns", "timeouts", "Use `retry()` safely",
                "Backend Platform", "Spring Boot", Instant.parse("2026-09-10T01:00:00Z"));

        assertThat(slugs(searchService.search("backend", 20))).containsExactly("connection-patterns");
        assertThat(slugs(searchService.search("\"spring boot\"", 20))).containsExactly("connection-patterns");
        assertThat(slugs(searchService.search("RETRY", 20))).containsExactly("connection-patterns");
        assertThat(slugs(searchService.search("connection backend", 20)))
                .containsExactly("connection-patterns");
        assertThat(searchService.search("webclient + - : ( ) & |", 20)).isEmpty();
    }

    @Test
    void appliesStableUpdatedAtAndIdTieBreakersAndLimit() throws Exception {
        Instant sameTime = Instant.parse("2026-09-10T04:00:00Z");
        insertKnowledge(OWNER_ID, "Tie query first", "tie-first", null, "", null, null, sameTime);
        insertKnowledge(OWNER_ID, "Tie query second", "tie-second", null, "", null, null, sameTime);
        insertKnowledge(OWNER_ID, "Tie query newest", "tie-newest", null, "", null, null,
                Instant.parse("2026-09-10T05:00:00Z"));

        assertThat(slugs(searchService.search("tie query", 2))).containsExactly("tie-newest", "tie-second");
        assertThat(slugs(searchService.search("tie query", 2))).containsExactly("tie-newest", "tie-second");
    }

    @Test
    void metadataUpdatesImmediatelyChangeSearchWithoutChangingSlugOrOwner() throws Exception {
        assertThat(currentOwner.id()).isEqualTo(OWNER_ID);
        Knowledge created = knowledgeService.create(
                "Stable document", "", "", Visibility.PRIVATE, "Backend", java.util.List.of("OldMarker")
        );
        long id = created.getId();
        String slug = created.getSlug();
        assertThat(slugs(searchService.search("oldmarker", 20))).containsExactly(slug);

        Knowledge updated = knowledgeService.update(
                id, "Renamed stable document", "", "", Visibility.PRIVATE,
                "Database", java.util.List.of("NewMarker")
        );

        assertThat(updated.getSlug()).isEqualTo(slug);
        assertThat(slugs(searchService.search("oldmarker", 20))).isEmpty();
        assertThat(slugs(searchService.search("backend", 20))).isEmpty();
        assertThat(slugs(searchService.search("newmarker", 20))).containsExactly(slug);
        assertThat(slugs(searchService.search("database", 20))).containsExactly(slug);
        assertThat(jdbcClient.sql("SELECT owner_id FROM knowledge WHERE id = :id")
                .param("id", id).query(UUID.class).single()).isEqualTo(OWNER_ID);
    }

    @Test
    void enforcesBlankQueryAndLimitContract() {
        assertThatThrownBy(() -> searchService.search("   ", 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query must not be blank");
        assertThatThrownBy(() -> searchService.search("valid", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> searchService.search("valid", 51))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsHealthAndOAuthEntryPublicButProtectsPrivateApis() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/api/knowledge"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/search/knowledge").param("q", "query"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymouslyReadsPublicKnowledgeWithMarkdownMetadataAndSafeDto() throws Exception {
        Knowledge created = knowledgeService.create(
                "Public WebClient guide",
                "A public summary",
                "## Configure timeouts\n\nUse `WebClient` safely.\n\n### Retry policy",
                Visibility.PUBLIC,
                "Backend",
                List.of("Spring Boot", "WebClient")
        );
        String slug = created.getSlug();

        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/public/knowledge/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Public WebClient guide"))
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.summary").value("A public summary"))
                .andExpect(jsonPath("$.content").value(created.getContent()))
                .andExpect(jsonPath("$.collection").value("Backend"))
                .andExpect(jsonPath("$.tags[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.tags[1]").value("WebClient"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.visibility").doesNotExist())
                .andExpect(jsonPath("$.authentication").doesNotExist())
                .andExpect(jsonPath("$.session").doesNotExist());

        mockMvc.perform(get("/api/public/knowledge/{slug}", slug).with(ownerLogin()))
                .andExpect(status().isOk());
    }

    @Test
    void privateUnlistedAndMissingPublicLookupsShareOpaqueNotFoundSemantics() throws Exception {
        Knowledge privateKnowledge = knowledgeService.create(
                "Private note", null, "", Visibility.PRIVATE, null, List.of()
        );
        Knowledge unlistedKnowledge = knowledgeService.create(
                "Unlisted note", null, "", Visibility.UNLISTED, null, List.of()
        );

        SecurityContextHolder.clearContext();
        String privateBody = mockMvc.perform(get("/api/public/knowledge/{slug}", privateKnowledge.getSlug()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String unlistedBody = mockMvc.perform(get("/api/public/knowledge/{slug}", unlistedKnowledge.getSlug()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missingBody = mockMvc.perform(get("/api/public/knowledge/missing-note"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(privateBody).isEqualTo(unlistedBody).isEqualTo(missingBody);
    }

    @Test
    void publicVisibilityTransitionsRevokeAndRestoreTheSameStableSlug() throws Exception {
        Knowledge created = knowledgeService.create(
                "Stable public route", "Summary", "## Content", Visibility.PRIVATE, null, List.of()
        );
        long id = created.getId();
        String slug = created.getSlug();

        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/public/knowledge/{slug}", slug)).andExpect(status().isNotFound());

        authenticateAsOwner();
        Knowledge unlisted = knowledgeService.update(
                id, "Renamed stable public route", "Summary", "## Content",
                Visibility.UNLISTED, null, List.of()
        );
        assertThat(unlisted.getSlug()).isEqualTo(slug);
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/public/knowledge/{slug}", slug)).andExpect(status().isNotFound());

        authenticateAsOwner();
        Knowledge published = knowledgeService.update(
                id, "Renamed stable public route", "Summary", "## Content",
                Visibility.PUBLIC, null, List.of()
        );
        Instant firstPublishedAt = published.getPublishedAt();
        assertThat(published.getSlug()).isEqualTo(slug);
        assertThat(firstPublishedAt).isNotNull();
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/public/knowledge/{slug}", slug)).andExpect(status().isOk());

        authenticateAsOwner();
        Knowledge revoked = knowledgeService.update(
                id, "Renamed stable public route", "Summary", "## Content",
                Visibility.PRIVATE, null, List.of()
        );
        assertThat(revoked.getPublishedAt()).isEqualTo(firstPublishedAt);
        assertThat(revoked.getSlug()).isEqualTo(slug);
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/public/knowledge/{slug}", slug)).andExpect(status().isNotFound());

        authenticateAsOwner();
        Knowledge republished = knowledgeService.update(
                id, "Renamed stable public route", "Summary", "## Content",
                Visibility.PUBLIC, null, List.of()
        );
        assertThat(republished.getPublishedAt()).isEqualTo(firstPublishedAt);
        assertThat(republished.getSlug()).isEqualTo(slug);
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/public/knowledge/{slug}", slug)).andExpect(status().isOk());
    }

    @Test
    void publicVisibilityRatherThanCurrentOwnerControlsAnonymousLookup() throws Exception {
        long id = insertKnowledge(
                OTHER_OWNER_ID,
                "Other owner's public note",
                "other-owner-public-note",
                "Public by visibility",
                "## Public content",
                null,
                null,
                Instant.parse("2026-09-10T05:00:00Z")
        );
        jdbcClient.sql("UPDATE knowledge SET visibility = 'PUBLIC', published_at = updated_at WHERE id = :id")
                .param("id", id)
                .update();

        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/public/knowledge/other-owner-public-note"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Other owner's public note"));
    }

    @Test
    void publicReadPermissionDoesNotWeakenOwnerCrudOrSearch() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/knowledge")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/knowledge/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/knowledge/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/search/knowledge").param("q", "public"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/public/knowledge/not-allowed"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void unlistedTokenLifecycleIsStableRevocableRotatableAndPresentationSafe() throws Exception {
        Knowledge privateKnowledge = knowledgeService.create(
                "Secret WebClient guide",
                "Bearer-link summary",
                "## Secret section\n\nUse `WebClient`.\n\n### Retry policy",
                Visibility.PRIVATE,
                "Backend",
                List.of("Spring Boot", "WebClient")
        );
        long id = privateKnowledge.getId();
        String slug = privateKnowledge.getSlug();
        assertThat(privateKnowledge.getShareToken()).isNull();
        mockMvc.perform(get("/api/knowledge/{id}/unlisted-link", id).with(ownerLogin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNLISTED_LINK_NOT_FOUND"));

        authenticateAsOwner();
        Knowledge unlisted = knowledgeService.update(
                id, privateKnowledge.getTitle(), privateKnowledge.getSummary(), privateKnowledge.getContent(),
                Visibility.UNLISTED, "Backend", List.of("Spring Boot", "WebClient")
        );
        String firstToken = unlisted.getShareToken();
        Instant firstTokenCreatedAt = unlisted.getShareTokenCreatedAt();
        assertThat(firstToken).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(firstTokenCreatedAt).isNotNull();

        mockMvc.perform(get("/api/knowledge/{id}/unlisted-link", id).with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(firstToken))
                .andExpect(jsonPath("$.path").value("/s/" + firstToken))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.ownerId").doesNotExist());

        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/shared/knowledge/{token}", firstToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store, max-age=0"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(jsonPath("$.title").value("Secret WebClient guide"))
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.summary").value("Bearer-link summary"))
                .andExpect(jsonPath("$.content").value(unlisted.getContent()))
                .andExpect(jsonPath("$.collection").value("Backend"))
                .andExpect(jsonPath("$.tags[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.tags[1]").value("WebClient"))
                .andExpect(jsonPath("$.publishedAt").isEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.shareToken").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.visibility").doesNotExist());

        mockMvc.perform(get("/api/knowledge/{id}", id).with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareToken").doesNotExist())
                .andExpect(jsonPath("$.shareTokenCreatedAt").doesNotExist());
        mockMvc.perform(get("/api/search/knowledge").param("q", "secret").with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shareToken").doesNotExist());

        authenticateAsOwner();
        Knowledge edited = knowledgeService.update(
                id, "Edited secret guide", unlisted.getSummary(), unlisted.getContent(),
                Visibility.UNLISTED, "Backend", List.of("Spring Boot", "WebClient")
        );
        assertThat(edited.getShareToken()).isEqualTo(firstToken);
        assertThat(edited.getShareTokenCreatedAt()).isEqualTo(firstTokenCreatedAt);

        Knowledge disabled = knowledgeService.update(
                id, edited.getTitle(), edited.getSummary(), edited.getContent(),
                Visibility.PRIVATE, "Backend", List.of("Spring Boot", "WebClient")
        );
        assertThat(disabled.getShareToken()).isEqualTo(firstToken);
        SecurityContextHolder.clearContext();
        String inactiveBody = mockMvc.perform(get("/api/shared/knowledge/{token}", firstToken))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "private, no-store, max-age=0"))
                .andReturn().getResponse().getContentAsString();
        String missingBody = mockMvc.perform(get("/api/shared/knowledge/{token}", "Z".repeat(43)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String malformedBody = mockMvc.perform(get("/api/shared/knowledge/not-a-valid-token"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(inactiveBody).isEqualTo(missingBody).isEqualTo(malformedBody);

        authenticateAsOwner();
        Knowledge reactivated = knowledgeService.update(
                id, disabled.getTitle(), disabled.getSummary(), disabled.getContent(),
                Visibility.UNLISTED, "Backend", List.of("Spring Boot", "WebClient")
        );
        assertThat(reactivated.getShareToken()).isEqualTo(firstToken);
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/shared/knowledge/{token}", firstToken)).andExpect(status().isOk());

        mockMvc.perform(post("/api/knowledge/{id}/unlisted-link/regenerate", id).with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/knowledge/{id}/unlisted-link/regenerate", id).with(ownerLogin()))
                .andExpect(status().isForbidden());
        String rotationBody = mockMvc.perform(post("/api/knowledge/{id}/unlisted-link/regenerate", id)
                        .with(ownerLogin()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondToken = com.jayway.jsonpath.JsonPath.read(rotationBody, "$.token");
        String secondTokenCreatedAtValue = com.jayway.jsonpath.JsonPath.read(rotationBody, "$.createdAt");
        Instant secondTokenCreatedAt = Instant.parse(secondTokenCreatedAtValue);
        assertThat(secondToken).matches("^[A-Za-z0-9_-]{43}$").isNotEqualTo(firstToken);
        assertThat(secondTokenCreatedAt).isAfter(firstTokenCreatedAt);
        assertThat(jdbcClient.sql("SELECT share_token_created_at FROM knowledge WHERE id = :id")
                .param("id", id).query(Instant.class).single()).isEqualTo(secondTokenCreatedAt);

        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/shared/knowledge/{token}", firstToken)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/shared/knowledge/{token}", secondToken)).andExpect(status().isOk());

        authenticateAsOwner();
        Knowledge published = knowledgeService.update(
                id, reactivated.getTitle(), reactivated.getSummary(), reactivated.getContent(),
                Visibility.PUBLIC, "Backend", List.of("Spring Boot", "WebClient")
        );
        assertThat(published.getShareToken()).isEqualTo(secondToken);
        assertThat(published.getPublishedAt()).isNotNull();
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/shared/knowledge/{token}", secondToken)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/knowledge/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareToken").doesNotExist());

        authenticateAsOwner();
        knowledgeService.update(
                id, published.getTitle(), published.getSummary(), published.getContent(),
                Visibility.PRIVATE, "Backend", List.of("Spring Boot", "WebClient")
        );
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/public/knowledge/{slug}", slug)).andExpect(status().isNotFound());
    }

    @Test
    void ownerCannotManageAnotherOwnersTokenAndSharedPermissionStaysReadOnly() throws Exception {
        long otherId = insertKnowledge(
                OTHER_OWNER_ID, "Other owner secret", "other-owner-secret", null, "secret",
                null, null, Instant.parse("2026-09-11T08:00:00Z")
        );
        String otherToken = "Q".repeat(43);
        jdbcClient.sql("""
                        UPDATE knowledge
                        SET visibility = 'UNLISTED', share_token = :token, share_token_created_at = CURRENT_TIMESTAMP
                        WHERE id = :id
                        """)
                .param("token", otherToken)
                .param("id", otherId)
                .update();

        mockMvc.perform(get("/api/knowledge/{id}/unlisted-link", otherId).with(ownerLogin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/knowledge/{id}/unlisted-link", otherId).with(oidcLogin()
                        .idToken(token -> token
                                .claim("email", "owner@example.com")
                                .claim("email_verified", true))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/knowledge/{id}/unlisted-link/regenerate", otherId)
                        .with(ownerLogin()).with(csrf()))
                .andExpect(status().isNotFound());

        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/shared/knowledge/{token}", otherToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/shared/knowledge/{token}", otherToken).with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/knowledge/{id}/unlisted-link", otherId).with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void v4AllowsExistingNullTokensAndEnforcesGlobalTokenUniqueness() {
        assertThat(jdbcClient.sql("""
                        SELECT COUNT(*) FROM information_schema.table_constraints
                        WHERE table_name = 'knowledge' AND constraint_name = 'uk_knowledge_share_token'
                        """).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbcClient.sql("""
                        SELECT COUNT(*) FROM information_schema.table_constraints
                        WHERE table_name = 'knowledge' AND constraint_name = 'ck_knowledge_share_token_pair'
                        """).query(Long.class).single()).isEqualTo(1L);

        long firstId = insertKnowledge(
                OWNER_ID, "Legacy-compatible row", "legacy-compatible-row", null, "", null, null,
                Instant.parse("2026-09-11T08:00:00Z")
        );
        assertThat(jdbcClient.sql("SELECT share_token FROM knowledge WHERE id = :id")
                .param("id", firstId).query(String.class).optional()).isEmpty();

        String token = "U".repeat(43);
        jdbcClient.sql("""
                        UPDATE knowledge
                        SET share_token = :token, share_token_created_at = CURRENT_TIMESTAMP
                        WHERE id = :id
                        """).param("token", token).param("id", firstId).update();
        long secondId = insertKnowledge(
                OTHER_OWNER_ID, "Duplicate token row", "duplicate-token-row", null, "", null, null,
                Instant.parse("2026-09-11T09:00:00Z")
        );

        assertThatThrownBy(() -> jdbcClient.sql("""
                        UPDATE knowledge
                        SET share_token = :token, share_token_created_at = CURRENT_TIMESTAMP
                        WHERE id = :id
                        """).param("token", token).param("id", secondId).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void exposesOwnerProfileAndCsrfOnlyToAllowedVerifiedPrincipal() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("OWNER@example.com"))
                .andExpect(jsonPath("$.name").value("Knowledge Owner"));
        mockMvc.perform(get("/api/auth/csrf").with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));

        mockMvc.perform(get("/api/auth/me").with(oidcLogin()
                        .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))
                        .idToken(token -> token
                                .claim("email", "other@example.com")
                                .claim("email_verified", true))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me").with(oidcLogin()
                        .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))
                        .idToken(token -> token
                                .claim("email", "owner@example.com")
                                .claim("email_verified", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresCsrfForAuthenticatedMutationAndKeepsReadsCsrfFree() throws Exception {
        mockMvc.perform(get("/api/knowledge").with(ownerLogin()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/knowledge")
                        .with(ownerLogin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/knowledge")
                        .with(ownerLogin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Secured note"))
                .andExpect(jsonPath("$.slug").value("secured-note"));
    }

    @Test
    void focusedVisibilityPatchIsOwnerScopedCsrfProtectedAndPreservesArticleFields() throws Exception {
        Knowledge knowledge = knowledgeService.create(
                "Focused visibility", "Unchanged summary", "# Unchanged content",
                Visibility.PRIVATE, "Backend", List.of("Spring Boot")
        );
        long id = knowledge.getId();
        String slug = knowledge.getSlug();

        mockMvc.perform(patch("/api/knowledge/{id}/visibility", id)
                        .with(ownerLogin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"UNLISTED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/knowledge/{id}/visibility", id)
                        .with(ownerLogin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"UNLISTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("UNLISTED"))
                .andExpect(jsonPath("$.title").value("Focused visibility"))
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.summary").value("Unchanged summary"))
                .andExpect(jsonPath("$.content").value("# Unchanged content"))
                .andExpect(jsonPath("$.collection").value("Backend"))
                .andExpect(jsonPath("$.tags[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.shareToken").doesNotExist());

        mockMvc.perform(get("/api/knowledge/{id}/unlisted-link", id).with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(patch("/api/knowledge/{id}/visibility", id)
                        .with(anonymous()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isUnauthorized());

        long otherId = insertKnowledge(
                OTHER_OWNER_ID, "Other visibility", "other-visibility", null, "other",
                null, null, Instant.parse("2026-09-12T08:00:00Z")
        );
        mockMvc.perform(patch("/api/knowledge/{id}/visibility", otherId)
                        .with(ownerLogin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void revisionApiIsOwnerScopedPaginatedAndRestorePreservesPublicationAndSharingState() throws Exception {
        Knowledge created = knowledgeService.create(
                "Original revision title",
                "Original summary",
                "# Original content",
                Visibility.UNLISTED,
                "Backend",
                List.of("WebClient", "Spring Boot")
        );
        long knowledgeId = created.getId();
        String stableSlug = created.getSlug();
        String dormantShareToken = created.getShareToken();
        long createRevisionId = jdbcClient.sql("""
                        SELECT id FROM knowledge_revision
                        WHERE knowledge_id = :knowledgeId AND reason = 'CREATE'
                        """)
                .param("knowledgeId", knowledgeId)
                .query(Long.class)
                .single();

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM knowledge_revision WHERE knowledge_id = :knowledgeId")
                .param("knowledgeId", knowledgeId).query(Long.class).single()).isEqualTo(1L);

        Knowledge published = knowledgeService.updateVisibility(knowledgeId, Visibility.PUBLIC);
        Instant publishedAt = published.getPublishedAt();
        assertThat(publishedAt).isNotNull();
        assertThat(published.getShareToken()).isEqualTo(dormantShareToken);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM knowledge_revision WHERE knowledge_id = :knowledgeId")
                .param("knowledgeId", knowledgeId).query(Long.class).single()).isEqualTo(1L);

        jdbcClient.sql("""
                        UPDATE knowledge_revision
                        SET created_at = CURRENT_TIMESTAMP - INTERVAL '6 minutes'
                        WHERE id = :revisionId
                        """)
                .param("revisionId", createRevisionId)
                .update();
        knowledgeService.update(
                knowledgeId,
                "Intermediate revision title",
                "Intermediate summary",
                "# Intermediate content",
                Visibility.PUBLIC,
                "Database",
                List.of("PostgreSQL")
        );
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM knowledge_revision WHERE knowledge_id = :knowledgeId")
                .param("knowledgeId", knowledgeId).query(Long.class).single()).isEqualTo(1L);

        knowledgeService.update(
                knowledgeId,
                "Current revision title",
                "Current summary",
                "# Current content",
                Visibility.PUBLIC,
                "Platform",
                List.of("Java")
        );
        long checkpointRevisionId = jdbcClient.sql("""
                        SELECT id FROM knowledge_revision
                        WHERE knowledge_id = :knowledgeId AND reason = 'CHECKPOINT'
                        """)
                .param("knowledgeId", knowledgeId)
                .query(Long.class)
                .single();

        mockMvc.perform(get("/api/knowledge/{id}/revisions", knowledgeId).with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/knowledge/{id}/revisions", knowledgeId)
                        .param("page", "0").param("size", "1").with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(checkpointRevisionId))
                .andExpect(jsonPath("$.items[0].reason").value("CHECKPOINT"))
                .andExpect(jsonPath("$.items[0].title").value("Intermediate revision title"))
                .andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.hasMore").value(true));
        mockMvc.perform(get("/api/knowledge/{id}/revisions", knowledgeId)
                        .param("size", "51").with(ownerLogin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/knowledge/{id}/revisions/{revisionId}", knowledgeId, createRevisionId)
                        .with(ownerLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Original revision title"))
                .andExpect(jsonPath("$.content").value("# Original content"))
                .andExpect(jsonPath("$.collection").value("Backend"))
                .andExpect(jsonPath("$.tags[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.tags[1]").value("WebClient"))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.slug").doesNotExist())
                .andExpect(jsonPath("$.visibility").doesNotExist())
                .andExpect(jsonPath("$.shareToken").doesNotExist());

        mockMvc.perform(post("/api/knowledge/{id}/revisions/{revisionId}/restore", knowledgeId, createRevisionId)
                        .with(anonymous()).with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/knowledge/{id}/revisions/{revisionId}/restore", knowledgeId, createRevisionId)
                        .with(ownerLogin()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/knowledge/{id}/revisions/{revisionId}/restore", knowledgeId, createRevisionId)
                        .with(ownerLogin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Original revision title"))
                .andExpect(jsonPath("$.slug").value(stableSlug))
                .andExpect(jsonPath("$.summary").value("Original summary"))
                .andExpect(jsonPath("$.content").value("# Original content"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.collection").value("Backend"))
                .andExpect(jsonPath("$.tags[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.tags[1]").value("WebClient"));

        assertThat(jdbcClient.sql("SELECT slug FROM knowledge WHERE id = :id")
                .param("id", knowledgeId).query(String.class).single()).isEqualTo(stableSlug);
        assertThat(jdbcClient.sql("SELECT visibility FROM knowledge WHERE id = :id")
                .param("id", knowledgeId).query(String.class).single()).isEqualTo("PUBLIC");
        assertThat(jdbcClient.sql("SELECT published_at FROM knowledge WHERE id = :id")
                .param("id", knowledgeId).query(Instant.class).single()).isEqualTo(publishedAt);
        assertThat(jdbcClient.sql("SELECT share_token FROM knowledge WHERE id = :id")
                .param("id", knowledgeId).query(String.class).single()).isEqualTo(dormantShareToken);
        assertThat(jdbcClient.sql("""
                        SELECT COUNT(*) FROM knowledge_revision
                        WHERE knowledge_id = :knowledgeId AND reason = 'BEFORE_RESTORE'
                          AND title = 'Current revision title'
                          AND content = '# Current content'
                        """)
                .param("knowledgeId", knowledgeId).query(Long.class).single()).isEqualTo(1L);

        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/public/knowledge/{slug}", stableSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Original revision title"))
                .andExpect(jsonPath("$.content").value("# Original content"));
    }

    @Test
    void revisionOwnershipAndCascadeRulesPreventCrossKnowledgeAccessAndOrphans() throws Exception {
        Knowledge owned = knowledgeService.create(
                "Owned history", null, "owned", Visibility.PRIVATE, null, List.of()
        );
        long ownedRevisionId = jdbcClient.sql("SELECT id FROM knowledge_revision WHERE knowledge_id = :id")
                .param("id", owned.getId()).query(Long.class).single();
        long otherKnowledgeId = insertKnowledge(
                OTHER_OWNER_ID, "Other history", "other-history", null, "other",
                null, null, Instant.parse("2026-09-12T08:00:00Z")
        );
        long otherRevisionId = jdbcClient.sql("""
                        INSERT INTO knowledge_revision (
                            knowledge_id, title, summary, content, collection_name, tags, reason, created_at
                        ) VALUES (
                            :knowledgeId, 'Other snapshot', NULL, 'other', NULL, '[]'::jsonb,
                            'CHECKPOINT', CURRENT_TIMESTAMP
                        ) RETURNING id
                        """)
                .param("knowledgeId", otherKnowledgeId)
                .query(Long.class)
                .single();

        mockMvc.perform(get("/api/knowledge/{id}/revisions", otherKnowledgeId).with(ownerLogin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_NOT_FOUND"));
        mockMvc.perform(get("/api/knowledge/{id}/revisions/{revisionId}", owned.getId(), otherRevisionId)
                        .with(ownerLogin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_REVISION_NOT_FOUND"));
        mockMvc.perform(post("/api/knowledge/{id}/revisions/{revisionId}/restore", owned.getId(), otherRevisionId)
                        .with(ownerLogin()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_REVISION_NOT_FOUND"));

        assertThat(jdbcClient.sql("""
                        SELECT COUNT(*) FROM information_schema.referential_constraints
                        WHERE constraint_name = 'fk_knowledge_revision_knowledge'
                          AND delete_rule = 'CASCADE'
                        """).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbcClient.sql("""
                        SELECT data_type FROM information_schema.columns
                        WHERE table_name = 'knowledge_revision' AND column_name = 'tags'
                        """).query(String.class).single()).isEqualTo("jsonb");

        authenticateAsOwner();
        knowledgeService.delete(owned.getId());
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM knowledge_revision WHERE id = :id")
                .param("id", ownedRevisionId).query(Long.class).single()).isZero();
    }

    @Test
    void logoutInvalidatesTheAuthenticatedBackendSession() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(
                        get("/api/auth/csrf").with(ownerLogin())
                )
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(ownerLogin())
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }

    private static java.util.List<String> slugs(java.util.List<KnowledgeSearchResult> response) {
        return response.stream().map(KnowledgeSearchResult::slug).toList();
    }

    private static void authenticateAsOwner() {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "test-token",
                now.minusSeconds(60),
                now.plusSeconds(300),
                Map.of(
                        "sub", "owner-subject",
                        "email", "OWNER@example.com",
                        "email_verified", true,
                        "name", "Knowledge Owner",
                        "picture", "https://example.com/avatar.png"
                )
        );
        DefaultOidcUser principal = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_OWNER")),
                idToken
        );
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google"
        ));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor ownerLogin() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))
                .idToken(token -> token
                        .claim("email", "OWNER@example.com")
                        .claim("email_verified", true)
                        .claim("name", "Knowledge Owner")
                        .claim("picture", "https://example.com/avatar.png"));
    }

    private static String validCreateBody() {
        return """
                {
                  "title": "Secured note",
                  "summary": "Created through a protected endpoint",
                  "content": "# Markdown",
                  "visibility": "PRIVATE",
                  "collection": null,
                  "tags": []
                }
                """;
    }

    private long insertKnowledge(
            UUID ownerId,
            String title,
            String slug,
            String summary,
            String content,
            String collection,
            String tag,
            Instant updatedAt
    ) {
        Long collectionId = collection == null ? null : jdbcClient.sql("""
                        INSERT INTO knowledge_collection (owner_id, name)
                        VALUES (:ownerId, :name)
                        RETURNING id
                        """)
                .param("ownerId", ownerId)
                .param("name", collection)
                .query(Long.class)
                .single();

        long knowledgeId = jdbcClient.sql("""
                        INSERT INTO knowledge (
                            owner_id, title, slug, summary, content, visibility,
                            collection_id, created_at, updated_at
                        ) VALUES (
                            :ownerId, :title, :slug, :summary, :content, 'PRIVATE',
                            :collectionId, :updatedAt, :updatedAt
                        )
                        RETURNING id
                        """)
                .param("ownerId", ownerId)
                .param("title", title)
                .param("slug", slug)
                .param("summary", summary)
                .param("content", content)
                .param("collectionId", collectionId)
                .param("updatedAt", updatedAt.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();

        if (tag != null) {
            long tagId = jdbcClient.sql("""
                            INSERT INTO tag (owner_id, name)
                            VALUES (:ownerId, :name)
                            RETURNING id
                            """)
                    .param("ownerId", ownerId)
                    .param("name", tag)
                    .query(Long.class)
                    .single();
            jdbcClient.sql("INSERT INTO knowledge_tag (knowledge_id, tag_id) VALUES (:knowledgeId, :tagId)")
                    .param("knowledgeId", knowledgeId)
                    .param("tagId", tagId)
                    .update();
        }

        return knowledgeId;
    }
}
