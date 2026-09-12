package com.knowledgeapplication.api.knowledge.revision;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class KnowledgeRevisionMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void v5BackfillsTruthfulMetadataSnapshotForExistingKnowledge() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target(MigrationVersion.fromVersion("4"))
                .load()
                .migrate();

        long knowledgeId;
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        )) {
            long collectionId;
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         INSERT INTO knowledge_collection (owner_id, name)
                         VALUES ('00000000-0000-0000-0000-000000000001', 'Backend')
                         RETURNING id
                         """)) {
                result.next();
                collectionId = result.getLong(1);
            }
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         INSERT INTO knowledge (
                             owner_id, title, slug, summary, content, visibility, collection_id
                         ) VALUES (
                             '00000000-0000-0000-0000-000000000001',
                             'Existing title', 'existing-title', 'Existing summary',
                             '# Existing Markdown', 'PUBLIC', %d
                         ) RETURNING id
                         """.formatted(collectionId))) {
                result.next();
                knowledgeId = result.getLong(1);
            }
            long firstTagId;
            long secondTagId;
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         INSERT INTO tag (owner_id, name)
                         VALUES
                           ('00000000-0000-0000-0000-000000000001', 'WebClient'),
                           ('00000000-0000-0000-0000-000000000001', 'Spring Boot')
                         RETURNING id
                         """)) {
                result.next();
                firstTagId = result.getLong(1);
                result.next();
                secondTagId = result.getLong(1);
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO knowledge_tag (knowledge_id, tag_id)
                        VALUES (%d, %d), (%d, %d)
                        """.formatted(knowledgeId, firstTagId, knowledgeId, secondTagId));
            }
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        ); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT title, summary, content, collection_name, tags::text, reason
                     FROM knowledge_revision
                     WHERE knowledge_id = %d
                     """.formatted(knowledgeId))) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("title")).isEqualTo("Existing title");
            assertThat(result.getString("summary")).isEqualTo("Existing summary");
            assertThat(result.getString("content")).isEqualTo("# Existing Markdown");
            assertThat(result.getString("collection_name")).isEqualTo("Backend");
            assertThat(result.getString("tags")).isEqualTo("[\"Spring Boot\", \"WebClient\"]");
            assertThat(result.getString("reason")).isEqualTo("CHECKPOINT");
            assertThat(result.next()).isFalse();
        }
    }
}
