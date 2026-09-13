package com.knowledgeapplication.api.attachment;

import com.knowledgeapplication.api.attachment.model.KnowledgeAttachment;
import com.knowledgeapplication.api.attachment.service.AttachmentCleanupService;
import com.knowledgeapplication.api.attachment.service.KnowledgeAttachmentService;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.service.KnowledgeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class AttachmentLifecycleCleanupIntegrationTest {

    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String BUCKET = "knowledge-lifecycle-test";
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
        registry.add("app.knowledge.attachment-cleanup.enabled", () -> "false");
        registry.add("app.knowledge.attachment-cleanup.grace-period", () -> "PT24H");
        registry.add("app.knowledge.attachment-cleanup.batch-size", () -> "100");
    }

    @Autowired KnowledgeService knowledgeService;
    @Autowired KnowledgeAttachmentService attachmentService;
    @Autowired AttachmentCleanupService cleanupService;
    @Autowired JdbcClient jdbcClient;
    @Autowired S3Client s3;

    @BeforeEach
    void setUp() {
        authenticateAsOwner();
        jdbcClient.sql("""
                TRUNCATE knowledge_attachment_delete_queue, knowledge_tag, knowledge, tag, knowledge_collection
                RESTART IDENTITY CASCADE
                """).update();
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (RuntimeException ignored) {
            // Shared by tests in this class and may already exist.
        }
        s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(BUCKET).build())
                .contents()
                .forEach(object -> s3.deleteObject(DeleteObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(object.key())
                        .build()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void freshUploadGetsGraceBeforeUnreferencedObjectIsDeleted() {
        Knowledge note = createNote("Fresh orphan grace");
        KnowledgeAttachment attachment = upload(note);

        assertThat(attachment.getOrphanedAt()).isNotNull();
        assertThat(objectExists(attachment.getObjectKey())).isTrue();

        var initial = cleanupService.cleanupOnce();
        assertThat(initial.orphanAttachmentsDeleted()).isZero();
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM knowledge_attachment WHERE id = :id")
                .param("id", attachment.getId()).query(Long.class).single()).isEqualTo(1L);

        ageOrphan(attachment.getId());
        var expired = cleanupService.cleanupOnce();

        assertThat(expired.orphanAttachmentsDeleted()).isEqualTo(1);
        assertThat(expired.failures()).isZero();
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM knowledge_attachment WHERE id = :id")
                .param("id", attachment.getId()).query(Long.class).single()).isZero();
        assertThat(objectExists(attachment.getObjectKey())).isFalse();
    }

    @Test
    void revisionOnlyReferenceProtectsObjectAndRestoreKeepsItAvailable() {
        Knowledge note = createNote("Revision protected image");
        KnowledgeAttachment attachment = upload(note);
        String markdown = "![Architecture](" + KnowledgeAttachmentService.markdownSource(attachment.getId()) + ")";

        knowledgeService.update(note.getId(), note.getTitle(), null, markdown,
                Visibility.PRIVATE, null, List.of());
        assertThat(jdbcClient.sql("SELECT orphaned_at FROM knowledge_attachment WHERE id = :id")
                .param("id", attachment.getId()).query(Instant.class).optional()).isEmpty();

        knowledgeService.update(note.getId(), note.getTitle(), null, "Image removed from current content",
                Visibility.PRIVATE, null, List.of());
        long revisionId = jdbcClient.sql("""
                        SELECT id FROM knowledge_revision
                        WHERE knowledge_id = :knowledgeId AND content = :content
                        ORDER BY id DESC LIMIT 1
                        """)
                .param("knowledgeId", note.getId())
                .param("content", markdown)
                .query(Long.class)
                .single();
        ageOrphan(attachment.getId());

        var cleanup = cleanupService.cleanupOnce();

        assertThat(cleanup.referencesRetained()).isEqualTo(1);
        assertThat(cleanup.orphanAttachmentsDeleted()).isZero();
        assertThat(jdbcClient.sql("SELECT orphaned_at FROM knowledge_attachment WHERE id = :id")
                .param("id", attachment.getId()).query(Instant.class).optional()).isEmpty();
        assertThat(objectExists(attachment.getObjectKey())).isTrue();

        Knowledge restored = knowledgeService.restoreRevision(note.getId(), revisionId);
        assertThat(restored.getContent()).isEqualTo(markdown);
        assertThat(objectExists(attachment.getObjectKey())).isTrue();
    }

    @Test
    void knowledgeDeleteQueuesObjectBeforeMetadataCascadeAndCleanupDrainsQueue() {
        Knowledge note = createNote("Knowledge delete queue");
        KnowledgeAttachment attachment = upload(note);
        String objectKey = attachment.getObjectKey();

        knowledgeService.delete(note.getId());

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM knowledge_attachment WHERE id = :id")
                .param("id", attachment.getId()).query(Long.class).single()).isZero();
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM knowledge_attachment_delete_queue WHERE object_key = :key")
                .param("key", objectKey).query(Long.class).single()).isEqualTo(1L);
        assertThat(objectExists(objectKey)).isTrue();

        var cleanup = cleanupService.cleanupOnce();

        assertThat(cleanup.queuedObjectsDeleted()).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM knowledge_attachment_delete_queue WHERE object_key = :key")
                .param("key", objectKey).query(Long.class).single()).isZero();
        assertThat(objectExists(objectKey)).isFalse();
    }

    @Test
    void alreadyMissingObjectConvergesByRemovingExpiredMetadata() {
        Knowledge note = createNote("Missing object convergence");
        KnowledgeAttachment attachment = upload(note);
        s3.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(attachment.getObjectKey()).build());
        ageOrphan(attachment.getId());

        var cleanup = cleanupService.cleanupOnce();

        assertThat(cleanup.orphanAttachmentsDeleted()).isEqualTo(1);
        assertThat(cleanup.failures()).isZero();
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM knowledge_attachment WHERE id = :id")
                .param("id", attachment.getId()).query(Long.class).single()).isZero();
    }

    private Knowledge createNote(String title) {
        authenticateAsOwner();
        return knowledgeService.create(title, null, "", Visibility.PRIVATE, null, List.of());
    }

    private KnowledgeAttachment upload(Knowledge note) {
        authenticateAsOwner();
        return attachmentService.uploadImage(
                note.getId(),
                new MockMultipartFile("file", "pixel.png", "image/png", PNG)
        );
    }

    private void ageOrphan(UUID attachmentId) {
        jdbcClient.sql("""
                        UPDATE knowledge_attachment
                        SET orphaned_at = CURRENT_TIMESTAMP - INTERVAL '25 hours'
                        WHERE id = :id
                        """)
                .param("id", attachmentId)
                .update();
    }

    private boolean objectExists(String objectKey) {
        return s3.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(BUCKET)
                        .prefix(objectKey)
                        .build())
                .contents()
                .stream()
                .anyMatch(object -> object.key().equals(objectKey));
    }

    private static void authenticateAsOwner() {
        Instant now = Instant.now();
        var token = new OidcIdToken("test-token", now.minusSeconds(60), now.plusSeconds(300), Map.of(
                "sub", "owner-subject",
                "email", "owner@example.com",
                "email_verified", true
        ));
        var principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_OWNER")), token);
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google"
        ));
    }
}
