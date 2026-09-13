package com.knowledgeapplication.api.attachment;

import tools.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
class KnowledgeAttachmentIntegrationTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BUCKET = "knowledge-images-test";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z")
    )
            .withEnv("MINIO_ROOT_USER", "minio-test")
            .withEnv("MINIO_ROOT_PASSWORD", "minio-test-secret")
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.owner-id", OWNER_ID::toString);
        registry.add("app.auth.allowed-email", () -> "owner@example.com");
        registry.add("app.object-storage.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("app.object-storage.region", () -> "us-east-1");
        registry.add("app.object-storage.bucket", () -> BUCKET);
        registry.add("app.object-storage.access-key", () -> "minio-test");
        registry.add("app.object-storage.secret-key", () -> "minio-test-secret");
        registry.add("app.object-storage.path-style", () -> "true");
        registry.add("app.knowledge.image-max-size", () -> "64KB");
        registry.add("app.knowledge.revision-checkpoint-interval", () -> "PT0S");
    }

    @Autowired WebApplicationContext context;
    @Autowired KnowledgeService knowledgeService;
    @Autowired JdbcClient jdbcClient;
    @Autowired S3Client s3;
    @Autowired ObjectMapper objectMapper;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authenticateAsOwner();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbcClient.sql("TRUNCATE knowledge_tag, knowledge, tag, knowledge_collection RESTART IDENTITY CASCADE")
                .update();
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (RuntimeException ignored) {
            // Bucket is shared by the tests in this class and may already exist.
        }
        s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(BUCKET).build())
                .contents()
                .forEach(object -> s3.deleteObject(DeleteObjectRequest.builder()
                        .bucket(BUCKET).key(object.key()).build()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void persistsAndStreamsPrivatePublicAndSharedImagesWithoutExposingStorageDetails() throws Exception {
        Knowledge note = create("Attachment note", Visibility.PRIVATE);
        UUID attachmentId = upload(note, "../pixel.png");

        Map<String, Object> metadata = jdbcClient.sql("""
                        SELECT object_key, original_filename, content_type, size_bytes
                        FROM knowledge_attachment WHERE id = :id
                        """)
                .param("id", attachmentId)
                .query((rs, row) -> {
                    Map<String, Object> values = new java.util.LinkedHashMap<>();
                    values.put("key", rs.getString("object_key"));
                    values.put("filename", rs.getString("original_filename"));
                    values.put("type", rs.getString("content_type"));
                    values.put("size", rs.getLong("size_bytes"));
                    return values;
                }).single();
        assertThat(metadata.get("key").toString())
                .startsWith("knowledge/" + OWNER_ID + "/" + note.getId() + "/")
                .doesNotContain("pixel.png");
        assertThat(metadata).containsEntry("filename", "pixel.png")
                .containsEntry("type", "image/png")
                .containsEntry("size", (long) PNG.length);
        assertThat(s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(BUCKET).key(metadata.get("key").toString()).build()).asByteArray()).isEqualTo(PNG);

        assertImage(downloadOwner(note.getId(), attachmentId), true);

        Knowledge otherNote = create("Other note", Visibility.PRIVATE);
        mockMvc.perform(get("/api/knowledge/{knowledgeId}/attachments/{attachmentId}/content",
                        otherNote.getId(), attachmentId).with(ownerLogin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_FOUND"));

        authenticateAsOwner();
        knowledgeService.updateVisibility(note.getId(), Visibility.PUBLIC);
        SecurityContextHolder.clearContext();
        assertImage(downloadAnonymous("/api/public/knowledge/{slug}/attachments/{id}/content",
                note.getSlug(), attachmentId), false);
        mockMvc.perform(get("/api/public/knowledge/{slug}/attachments/{id}/content",
                        otherNote.getSlug(), attachmentId))
                .andExpect(status().isNotFound());

        authenticateAsOwner();
        knowledgeService.updateVisibility(note.getId(), Visibility.UNLISTED);
        String shareToken = knowledgeService.getUnlistedLink(note.getId()).getShareToken();
        SecurityContextHolder.clearContext();
        assertImage(downloadAnonymous("/api/shared/knowledge/{token}/attachments/{id}/content",
                shareToken, attachmentId), true);

        authenticateAsOwner();
        knowledgeService.updateVisibility(note.getId(), Visibility.PRIVATE);
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/shared/knowledge/{token}/attachments/{id}/content", shareToken, attachmentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadRequiresAuthenticationAndCsrfAndRejectsInvalidImages() throws Exception {
        Knowledge note = create("Protected upload", Visibility.PRIVATE);
        var valid = new org.springframework.mock.web.MockMultipartFile("file", "pixel.png", "image/png", PNG);

        SecurityContextHolder.clearContext();
        mockMvc.perform(multipart("/api/knowledge/{id}/attachments/images", note.getId()).file(valid))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/knowledge/{id}/attachments/images", note.getId())
                        .file(valid).with(ownerLogin()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/knowledge/{id}/attachments/images", note.getId())
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "active.svg", "image/svg+xml", "<svg/>".getBytes()
                        )).with(ownerLogin()).with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM knowledge_attachment")
                .query(Long.class).single()).isZero();
        assertThat(s3.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).build()).keyCount()).isZero();
    }

    @Test
    void revisionRestoreKeepsImageReadableAndDeleteRetainsObjectForDeferredCleanup() throws Exception {
        Knowledge note = create("Deferred cleanup", Visibility.PRIVATE);
        UUID attachmentId = upload(note, "pixel.png");
        String objectKey = jdbcClient.sql("SELECT object_key FROM knowledge_attachment WHERE id = :id")
                .param("id", attachmentId).query(String.class).single();
        String markdown = "![Architecture](attachment://" + attachmentId + ")";

        authenticateAsOwner();
        knowledgeService.update(note.getId(), note.getTitle(), null, markdown,
                Visibility.PRIVATE, null, List.of());
        knowledgeService.update(note.getId(), note.getTitle(), null, "Image temporarily removed",
                Visibility.PRIVATE, null, List.of());
        long imageRevisionId = jdbcClient.sql("""
                        SELECT id FROM knowledge_revision
                        WHERE knowledge_id = :id AND content = :content
                        ORDER BY id DESC LIMIT 1
                        """)
                .param("id", note.getId()).param("content", markdown)
                .query(Long.class).single();
        Knowledge restored = knowledgeService.restoreRevision(note.getId(), imageRevisionId);

        assertThat(restored.getContent()).isEqualTo(markdown);
        assertImage(downloadOwner(note.getId(), attachmentId), true);

        authenticateAsOwner();
        knowledgeService.delete(note.getId());

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM knowledge_attachment WHERE id = :id")
                .param("id", attachmentId).query(Long.class).single()).isZero();
        assertThat(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key(objectKey).build())
                .asByteArray()).isEqualTo(PNG);
    }

    private Knowledge create(String title, Visibility visibility) {
        authenticateAsOwner();
        return knowledgeService.create(title, null, "", visibility, null, List.of());
    }

    private UUID upload(Knowledge note, String filename) throws Exception {
        var result = mockMvc.perform(multipart("/api/knowledge/{id}/attachments/images", note.getId())
                        .file(new org.springframework.mock.web.MockMultipartFile("file", filename, "image/png", PNG))
                        .with(ownerLogin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdownSource").value(org.hamcrest.Matchers.startsWith("attachment://")))
                .andExpect(jsonPath("$.objectKey").doesNotExist())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("id").asText());
    }

    private MvcResult downloadOwner(long knowledgeId, UUID attachmentId) throws Exception {
        MvcResult initial = mockMvc.perform(get(
                        "/api/knowledge/{knowledgeId}/attachments/{attachmentId}/content", knowledgeId, attachmentId
                ).with(ownerLogin()))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(initial)).andExpect(status().isOk()).andReturn();
    }

    private MvcResult downloadAnonymous(String path, Object... variables) throws Exception {
        MvcResult initial = mockMvc.perform(get(path, variables))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(initial)).andExpect(status().isOk()).andReturn();
    }

    private void assertImage(MvcResult result, boolean noIndex) {
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        if (noIndex) {
            assertThat(result.getResponse().getHeader("X-Robots-Tag")).contains("noindex");
        }
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
        return oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))
                .idToken(token -> token.claim("email", "owner@example.com").claim("email_verified", true));
    }
}
