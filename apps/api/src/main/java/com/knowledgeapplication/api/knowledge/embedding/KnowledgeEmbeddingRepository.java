package com.knowledgeapplication.api.knowledge.embedding;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class KnowledgeEmbeddingRepository {
    // Same length-prefixed UTF-8 encoding as EmbeddingSource.hash(), including strategy.
    private static final String SOURCE_HASH = """
            encode(sha256(convert_to(:strategy
                || octet_length(convert_to(k.title, 'UTF8'))::text || ':' || k.title
                || octet_length(convert_to(coalesce(k.summary, ''), 'UTF8'))::text || ':' || coalesce(k.summary, '')
                || octet_length(convert_to(k.content, 'UTF8'))::text || ':' || k.content, 'UTF8')), 'hex')
            """;
    private static final String CURRENT_SET = """
            EXISTS (
                SELECT 1 FROM knowledge_embedding_chunk c
                WHERE c.knowledge_id = k.id AND c.owner_id = k.owner_id
                GROUP BY c.knowledge_id, c.owner_id
                HAVING count(*) = max(c.chunk_count)
                   AND min(c.chunk_index) = 0 AND max(c.chunk_index) = max(c.chunk_count) - 1
                   AND min(c.chunk_count) = max(c.chunk_count)
                   AND bool_and(c.embedding_model = :model AND c.embedding_dimensions = :dimensions
                       AND c.chunker_version = :version AND c.source_hash = %s)
            )
            """.formatted(SOURCE_HASH);
    private final JdbcClient jdbc;
    private final DataSource dataSource;
    private final TransactionTemplate transaction;

    public KnowledgeEmbeddingRepository(JdbcClient jdbc, DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public enum IndexState { NOT_INDEXED, CURRENT, STALE }
    public record NearestChunk(long knowledgeId, int chunkIndex, String chunkText, double distance) {}

    public List<Long> findPending(UUID ownerId, EmbeddingStrategy strategy, int limit, long afterId) {
        checkLimit(limit, 1000);
        return compatible(jdbc.sql("SELECT k.id FROM knowledge k WHERE k.owner_id = :owner AND k.id > :afterId AND NOT "
                        + CURRENT_SET + " ORDER BY k.id LIMIT :limit"), ownerId, strategy)
                .param("afterId", afterId).param("limit", limit).query(Long.class).list();
    }

    public Optional<EmbeddingSource> findSource(UUID ownerId, long id) {
        return jdbc.sql("SELECT id, owner_id, title, summary, content, updated_at FROM knowledge WHERE owner_id = :owner AND id = :id")
                .param("owner", ownerId).param("id", id).query(KnowledgeEmbeddingRepository::source).optional();
    }

    public IndexState state(UUID ownerId, long id, EmbeddingStrategy strategy) {
        return compatible(jdbc.sql("""
                SELECT CASE WHEN %s THEN 'CURRENT'
                    WHEN EXISTS (SELECT 1 FROM knowledge_embedding_chunk c WHERE c.knowledge_id = k.id AND c.owner_id = k.owner_id)
                    THEN 'STALE' ELSE 'NOT_INDEXED' END
                FROM knowledge k WHERE k.owner_id = :owner AND k.id = :id
                """.formatted(CURRENT_SET)), ownerId, strategy).param("id", id).query(String.class)
                .optional().map(IndexState::valueOf).orElse(IndexState.NOT_INDEXED);
    }

    /** Generate outside this transaction. Lock/recheck only for the brief atomic replacement. */
    public boolean replace(EmbeddingSource snapshot, EmbeddingStrategy strategy, List<MarkdownChunker.Chunk> chunks, List<float[]> vectors) {
        if (chunks.isEmpty()) throw new IllegalArgumentException("Embedding chunk set must not be empty");
        EmbeddingVectors.validate(vectors, chunks.size(), strategy.properties().dimensions());
        for (int i = 0; i < chunks.size(); i++) {
            if (chunks.get(i).index() != i || chunks.get(i).text().isBlank()) {
                throw new IllegalArgumentException("Embedding chunk positions/text are invalid");
            }
        }
        return Boolean.TRUE.equals(transaction.execute(status -> {
            var current = jdbc.sql("""
                    SELECT id, owner_id, title, summary, content, updated_at FROM knowledge
                    WHERE owner_id = :owner AND id = :id FOR UPDATE
                    """).param("owner", snapshot.ownerId()).param("id", snapshot.knowledgeId())
                    .query(KnowledgeEmbeddingRepository::source).optional();
            String hash = snapshot.hash(strategy);
            if (current.isEmpty() || !current.get().hash(strategy).equals(hash)) return false;
            jdbc.sql("DELETE FROM knowledge_embedding_chunk WHERE owner_id = :owner AND knowledge_id = :id")
                    .param("owner", snapshot.ownerId()).param("id", snapshot.knowledgeId()).update();
            for (int i = 0; i < chunks.size(); i++) {
                jdbc.sql("""
                        INSERT INTO knowledge_embedding_chunk
                            (knowledge_id, owner_id, chunk_index, chunk_count, chunk_text, embedding,
                             embedding_model, embedding_dimensions, chunker_version, source_hash, source_updated_at)
                        VALUES (:id, :owner, :index, :count, :text, CAST(:vector AS vector),
                                :model, :dimensions, :version, :hash, :updatedAt)
                        """).param("id", snapshot.knowledgeId()).param("owner", snapshot.ownerId())
                        .param("index", i).param("count", chunks.size()).param("text", chunks.get(i).text())
                        .param("vector", EmbeddingVectors.literal(vectors.get(i)))
                        .param("model", strategy.properties().model()).param("dimensions", strategy.properties().dimensions())
                        .param("version", strategy.chunkerVersion()).param("hash", hash)
                        .param("updatedAt", current.get().updatedAt().atOffset(ZoneOffset.UTC)).update();
            }
            return true;
        }));
    }

    /** Internal exact cosine retrieval. Stale/incomplete/incompatible sets cannot participate. */
    public List<NearestChunk> findNearestChunks(UUID ownerId, EmbeddingStrategy strategy, float[] queryVector, int limit) {
        checkLimit(limit, 100);
        EmbeddingVectors.validate(List.of(queryVector), 1, strategy.properties().dimensions());
        return compatible(jdbc.sql("""
                SELECT c.knowledge_id, c.chunk_index, c.chunk_text,
                       c.embedding <=> CAST(:vector AS vector) AS distance
                FROM knowledge_embedding_chunk c JOIN knowledge k ON k.id = c.knowledge_id AND k.owner_id = c.owner_id
                WHERE k.owner_id = :owner AND c.embedding_model = :model AND c.embedding_dimensions = :dimensions
                  AND %s
                ORDER BY distance, c.knowledge_id, c.chunk_index, c.id LIMIT :limit
                """.formatted(CURRENT_SET)), ownerId, strategy).param("vector", EmbeddingVectors.literal(queryVector))
                .param("limit", limit).query((row, n) -> new NearestChunk(row.getLong("knowledge_id"),
                        row.getInt("chunk_index"), row.getString("chunk_text"), row.getDouble("distance"))).list();
    }

    /** Session advisory lock across generation; no row lock/transaction blocks normal authoring. */
    public Optional<IndexingLease> tryLock(long knowledgeId) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            // Negative Knowledge IDs reserve an application-local advisory-lock key space.
            try (var statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1, -knowledgeId);
                try (var result = statement.executeQuery()) {
                    result.next();
                    if (result.getBoolean(1)) return Optional.of(new IndexingLease(connection, knowledgeId));
                }
            }
            connection.close();
            return Optional.empty();
        } catch (SQLException ex) {
            if (connection != null) {
                try { connection.close(); } catch (SQLException ignored) { /* no sensitive SQL logged */ }
            }
            throw new IllegalStateException("Embedding indexing lock unavailable");
        }
    }

    public static final class IndexingLease implements AutoCloseable {
        private final Connection connection;
        private final long knowledgeId;
        private IndexingLease(Connection connection, long knowledgeId) {
            this.connection = connection;
            this.knowledgeId = knowledgeId;
        }
        @Override
        public void close() {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                statement.setLong(1, -knowledgeId);
                statement.execute();
            } catch (SQLException ex) {
                // Never return a connection with an unreleased session lock to the pool.
                try { connection.abort(Runnable::run); } catch (SQLException ignored) { }
                throw new IllegalStateException("Embedding indexing lock release failed");
            } finally {
                try { connection.close(); } catch (SQLException ignored) { }
            }
        }
    }

    private static JdbcClient.StatementSpec compatible(JdbcClient.StatementSpec query, UUID ownerId, EmbeddingStrategy strategy) {
        return query.param("owner", ownerId).param("strategy", strategy.marker()).param("model", strategy.properties().model())
                .param("dimensions", strategy.properties().dimensions()).param("version", strategy.chunkerVersion());
    }

    private static EmbeddingSource source(ResultSet row, int n) throws SQLException {
        return new EmbeddingSource(row.getLong("id"), row.getObject("owner_id", UUID.class), row.getString("title"),
                row.getString("summary"), row.getString("content"), row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static void checkLimit(int limit, int maximum) {
        if (limit < 1 || limit > maximum) throw new IllegalArgumentException("Embedding query limit is out of range");
    }
}
