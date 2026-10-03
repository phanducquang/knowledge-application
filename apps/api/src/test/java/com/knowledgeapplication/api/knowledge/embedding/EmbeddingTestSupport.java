package com.knowledgeapplication.api.knowledge.embedding;

import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

final class EmbeddingTestSupport {
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    static EmbeddingProperties properties() { return properties(true, "test-model", 3, 2, 2); }

    static EmbeddingProperties properties(boolean enabled, String model, int dimensions, int batchSize, int knowledgeBatchSize) {
        return new EmbeddingProperties(enabled, "http://localhost:12345", model, dimensions,
                Duration.ofSeconds(2), Duration.ofSeconds(5), batchSize, false,
                Duration.ofMinutes(1), Duration.ofDays(1), knowledgeBatchSize, 256, 20);
    }

    static KnowledgeEmbeddingIndexer indexer(KnowledgeEmbeddingRepository repository, EmbeddingClient client, EmbeddingProperties properties) {
        return indexer(repository, client, properties, new EmbeddingStrategy(properties, MarkdownChunker.VERSION));
    }

    static KnowledgeEmbeddingIndexer indexer(KnowledgeEmbeddingRepository repository, EmbeddingClient client,
            EmbeddingProperties properties, EmbeddingStrategy strategy) {
        return new KnowledgeEmbeddingIndexer(repository,
                new StaticListableBeanFactory(Map.of("embeddingClient", client)).getBeanProvider(EmbeddingClient.class),
                properties, strategy, new MarkdownChunker(properties.maxChunkChars(), properties.overlapChars()), OWNER);
    }
}
