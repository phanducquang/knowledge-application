package com.knowledgeapplication.api.search.repository;

import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.search.model.KnowledgeSearchResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Repository
public class KnowledgeSearchRepository {

    private static final String SEARCH_SQL = """
            WITH search_input AS (
                SELECT websearch_to_tsquery('simple'::regconfig, :query) AS query
            ),
            metadata_vectors AS (
                SELECT
                    k.id,
                    setweight(
                        to_tsvector('simple'::regconfig, coalesce(c.name, '')),
                        'B'
                    ) ||
                    setweight(
                        to_tsvector(
                            'simple'::regconfig,
                            coalesce(string_agg(t.name, ' ' ORDER BY lower(t.name), t.name), '')
                        ),
                        'B'
                    ) AS search_vector
                FROM knowledge k
                LEFT JOIN knowledge_collection c
                    ON c.id = k.collection_id
                    AND c.owner_id = k.owner_id
                LEFT JOIN knowledge_tag kt ON kt.knowledge_id = k.id
                LEFT JOIN tag t
                    ON t.id = kt.tag_id
                    AND t.owner_id = k.owner_id
                WHERE k.owner_id = :ownerId
                GROUP BY k.id, c.name
            ),
            scores AS (
                SELECT
                    k.id,
                    ts_rank_cd(k.search_vector, search_input.query) AS relevance
                FROM knowledge k
                CROSS JOIN search_input
                WHERE k.owner_id = :ownerId
                  AND k.search_vector @@ search_input.query

                UNION ALL

                SELECT
                    metadata_vectors.id,
                    ts_rank_cd(metadata_vectors.search_vector, search_input.query) AS relevance
                FROM metadata_vectors
                CROSS JOIN search_input
                WHERE metadata_vectors.search_vector @@ search_input.query

                UNION ALL

                SELECT
                    k.id,
                    ts_rank_cd(
                        k.search_vector || metadata_vectors.search_vector,
                        search_input.query
                    ) AS relevance
                FROM knowledge k
                JOIN metadata_vectors ON metadata_vectors.id = k.id
                CROSS JOIN search_input
                WHERE k.owner_id = :ownerId
                  AND (k.search_vector || metadata_vectors.search_vector) @@ search_input.query
                  AND NOT (k.search_vector @@ search_input.query)
                  AND NOT (metadata_vectors.search_vector @@ search_input.query)
            ),
            ranked AS (
                SELECT id, sum(relevance) AS relevance
                FROM scores
                GROUP BY id
            )
            SELECT
                k.id,
                k.title,
                k.slug,
                k.summary,
                k.visibility,
                c.name AS collection,
                k.updated_at,
                coalesce(
                    array_agg(t.name ORDER BY lower(t.name), t.name)
                        FILTER (WHERE t.id IS NOT NULL),
                    ARRAY[]::varchar[]
                ) AS tags
            FROM ranked
            JOIN knowledge k
                ON k.id = ranked.id
                AND k.owner_id = :ownerId
            LEFT JOIN knowledge_collection c
                ON c.id = k.collection_id
                AND c.owner_id = k.owner_id
            LEFT JOIN knowledge_tag kt ON kt.knowledge_id = k.id
            LEFT JOIN tag t
                ON t.id = kt.tag_id
                AND t.owner_id = k.owner_id
            GROUP BY
                ranked.relevance,
                k.id,
                k.title,
                k.slug,
                k.summary,
                k.visibility,
                c.name,
                k.updated_at
            ORDER BY ranked.relevance DESC, k.updated_at DESC, k.id DESC
            LIMIT :limit
            """;

    private final JdbcClient jdbcClient;

    public KnowledgeSearchRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<KnowledgeSearchResult> search(UUID ownerId, String query, int limit) {
        return jdbcClient.sql(SEARCH_SQL)
                .param("ownerId", ownerId)
                .param("query", query)
                .param("limit", limit)
                .query(KnowledgeSearchRepository::mapResult)
                .list();
    }

    private static KnowledgeSearchResult mapResult(ResultSet resultSet, int rowNumber) throws SQLException {
        return new KnowledgeSearchResult(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getString("slug"),
                resultSet.getString("summary"),
                Visibility.valueOf(resultSet.getString("visibility")),
                resultSet.getString("collection"),
                readTags(resultSet.getArray("tags")),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }

    private static List<String> readTags(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }

        try {
            return Arrays.stream((Object[]) sqlArray.getArray())
                    .map(String::valueOf)
                    .toList();
        } finally {
            sqlArray.free();
        }
    }
}
