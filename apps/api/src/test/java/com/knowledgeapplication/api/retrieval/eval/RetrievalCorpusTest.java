package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.knowledge.embedding.MarkdownChunker;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

class RetrievalCorpusTest {
    private final MarkdownChunker chunker = new MarkdownChunker(4000, 200);
    @Test void syntheticFixtureIsValidAndHasExplicitCoverage() {
        var c = RetrievalCorpus.load();
        assertThat(c.notes()).hasSize(24); assertThat(c.queries()).hasSize(40);
        assertThat(c.queries().stream().map(RetrievalCorpus.Query::category)).contains("exact_keyword", "semantic_paraphrase", "technical_synonym",
                "configuration", "code", "ambiguous", "multi_topic", "negative", "bilingual");
        assertThat(c.queries().stream().map(RetrievalCorpus.Query::language)).contains("en", "vi", "vi->en", "en->vi");
        assertThat(c.queries().stream().filter(q -> !q.negative())).allMatch(q -> !q.relevantKnowledge().isEmpty());
    }
    @ParameterizedTest @MethodSource("invalidFixtures")
    void invalidGroundTruthAndFixturesFailFast(Function<RetrievalCorpus, RetrievalCorpus> edit) {
        assertThatIllegalArgumentException().isThrownBy(() -> edit.apply(RetrievalCorpus.load()).validate(chunker));
    }
    static Stream<Function<RetrievalCorpus, RetrievalCorpus>> invalidFixtures() {
        return Stream.of(
                c -> new RetrievalCorpus(c.version(), c.description(), List.of(), c.queries()),
                c -> new RetrievalCorpus(c.version(), c.description(), c.notes(), List.of()),
                c -> new RetrievalCorpus(c.version(), c.description(), List.of(c.notes().get(0), c.notes().get(0)), c.queries()),
                c -> new RetrievalCorpus(c.version(), c.description(), c.notes(), List.of(c.queries().get(0), c.queries().get(0))),
                c -> replaceQuery(c, "   ", c.queries().get(0).relevantKnowledge(), c.queries().get(0).relevantChunks(), false, "exact_keyword"),
                c -> replaceQuery(c, "Redis cache", List.of("nonexistent"), List.of(), false, "exact_keyword"),
                c -> replaceQuery(c, "Redis cache", List.of(), List.of(), false, "exact_keyword"),
                c -> replaceQuery(c, "Redis cache", List.of("redis-ttl"), List.of(), true, "negative"),
                c -> replaceQuery(c, "Redis cache", List.of(), List.of(), true, "exact_keyword"),
                c -> replaceQuery(c, "Redis cache", List.of("redis-ttl"), List.of(new RetrievalCorpus.ChunkTruth("redis-ttl", "absent-marker")), false, "exact_keyword"),
                c -> replaceQuery(c, "Redis cache", List.of("redis-ttl"), List.of(new RetrievalCorpus.ChunkTruth("redis-ttl", "../invalid")), false, "exact_keyword"),
                c -> replaceQuery(c, "Redis cache", List.of("redis-ttl", "redis-ttl"), List.of(), false, "exact_keyword"),
                c -> new RetrievalCorpus(c.version(), c.description(), List.of(new RetrievalCorpus.Note("blank", "Title", "", "en", "  ", null)), c.queries()),
                c -> new RetrievalCorpus(c.version(), c.description(), List.of(new RetrievalCorpus.Note("path", "Title", "", "en", null, "../secret.md")), c.queries())
        );
    }
    private static RetrievalCorpus replaceQuery(RetrievalCorpus c, String query, List<String> notes, List<RetrievalCorpus.ChunkTruth> chunks,
            boolean negative, String category) {
        return new RetrievalCorpus(c.version(), c.description(), c.notes(),
                List.of(new RetrievalCorpus.Query("q-invalid", query, category, "en", "Synthetic validation case", negative, notes, chunks)));
    }
    @Test void impossibleDimensionConfigurationFailsBeforeRetrieval() {
        var p = new com.knowledgeapplication.api.knowledge.embedding.EmbeddingProperties(false, "http://127.0.0.1/offline-eval",
                OfflineEmbeddingClient.NAME, 32, null, null, 1, false, null, null, 1, 4000, 200);
        assertThatIllegalArgumentException().isThrownBy(() -> new RetrievalEvaluation(null,
                new com.knowledgeapplication.api.knowledge.embedding.EmbeddingStrategy(p, MarkdownChunker.VERSION),
                new OfflineEmbeddingClient(), UUID.fromString("00000000-0000-0000-0000-000000000001"), RetrievalCorpus.load(), Map.of(), Map.of()));
    }
}
