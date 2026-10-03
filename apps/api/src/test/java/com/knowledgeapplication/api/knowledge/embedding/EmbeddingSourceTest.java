package com.knowledgeapplication.api.knowledge.embedding;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class EmbeddingSourceTest {
    private final EmbeddingProperties config = EmbeddingTestSupport.properties();
    private final EmbeddingStrategy strategy = new EmbeddingStrategy(config, MarkdownChunker.VERSION);

    @Test
    void hashesExactSemanticInputsWithSHA256AndUnambiguousFieldBoundaries() {
        var source = source("Title", "Summary", "Markdown");
        assertThat(source.hash(strategy)).matches("[0-9a-f]{64}").isEqualTo(source.hash(strategy));
        assertThat(source("New title", "Summary", "Markdown").hash(strategy)).isNotEqualTo(source.hash(strategy));
        assertThat(source("Title", "New summary", "Markdown").hash(strategy)).isNotEqualTo(source.hash(strategy));
        assertThat(source("Title", "Summary", "New Markdown").hash(strategy)).isNotEqualTo(source.hash(strategy));
        assertThat(source("a", "bc", "d").hash(strategy)).isNotEqualTo(source("ab", "c", "d").hash(strategy));
    }

    @Test
    void metadataAndTimeAreExcludedAndAbsentSummaryMatchesEmptySemanticSummary() {
        var source = source("Title", null, "Content");
        assertThat(source("Title", "", "Content").hash(strategy)).isEqualTo(source.hash(strategy));
        var same = new EmbeddingSource(99, EmbeddingTestSupport.OTHER, "Title", null, "Content", Instant.now());
        assertThat(same.hash(strategy)).isEqualTo(source.hash(strategy));
        assertThat(source.input("raw chunk")).isEqualTo("Title: Title\n\nraw chunk");
        assertThat(source("Title", "Summary", "Content").input("raw chunk")).isEqualTo("Title: Title\nSummary: Summary\n\nraw chunk");
    }

    @Test
    void modelDimensionVersionChunkSettingsAndEndpointChangesInvalidateAndGeminiStrategyIsExplicit() {
        var source = source("Title", "Summary", "Content");
        assertThat(source.hash(new EmbeddingStrategy(config, MarkdownChunker.VERSION + 1))).isNotEqualTo(source.hash(strategy));
        assertThat(source.hash(new EmbeddingStrategy(EmbeddingTestSupport.properties(true, "new-model", 3, 2, 2), 1))).isNotEqualTo(source.hash(strategy));
        assertThat(source.hash(new EmbeddingStrategy(EmbeddingTestSupport.properties(true, "test-model", 4, 2, 2), 1))).isNotEqualTo(source.hash(strategy));
        for (var changed : new EmbeddingProperties[]{
                changed(config.baseUrl(), 512, 20), changed(config.baseUrl(), 256, 10), changed("http://localhost:12346", 256, 20)}) {
            assertThat(source.hash(new EmbeddingStrategy(changed, 1))).isNotEqualTo(source.hash(strategy));
        }
        assertThat(strategy.marker()).startsWith("semantic-source-v2:provider=gemini:symmetric-text-v1");
        assertThat(strategy.marker()).doesNotContain("api-key");
    }

    private EmbeddingProperties changed(String url, int maxChars, int overlap) {
        return new EmbeddingProperties(true, url, config.model(), config.dimensions(), config.connectTimeout(), config.readTimeout(),
                config.batchSize(), config.indexingEnabled(), config.interval(), config.initialDelay(), config.knowledgeBatchSize(), maxChars, overlap);
    }
    private EmbeddingSource source(String title, String summary, String content) {
        return new EmbeddingSource(1, EmbeddingTestSupport.OWNER, title, summary, content, Instant.EPOCH);
    }
}
