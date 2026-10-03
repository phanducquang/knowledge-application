package com.knowledgeapplication.api.knowledge.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("app.embedding")
public record EmbeddingProperties(
        boolean enabled, String baseUrl, String model, int dimensions,
        Duration connectTimeout, Duration readTimeout, int batchSize,
        boolean indexingEnabled, Duration interval, Duration initialDelay, int knowledgeBatchSize,
        int maxChunkChars, int overlapChars
) {
    public EmbeddingProperties {
        if (enabled) {
            URI uri;
            try {
                uri = URI.create(baseUrl == null ? "" : baseUrl);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Embedding base URL must be a valid HTTP(S) origin/path");
            }
            if (!java.util.Set.of("http", "https").contains(uri.getScheme() == null ? "" : uri.getScheme())
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Embedding base URL must be a valid HTTP(S) origin/path");
            }
            if (model == null || model.isBlank() || model.length() > 255) {
                throw new IllegalArgumentException("Embedding model must be non-blank and at most 255 characters");
            }
            if (dimensions < 1 || dimensions > 3072) {
                throw new IllegalArgumentException("Embedding dimensions must be between 1 and 3072");
            }
            if (!boundedTimeout(connectTimeout) || !boundedTimeout(readTimeout)) {
                throw new IllegalArgumentException("Embedding timeouts must be between 1 and 2147483647 milliseconds");
            }
            if (batchSize < 1 || batchSize > 2048 || knowledgeBatchSize < 1 || knowledgeBatchSize > 1000) {
                throw new IllegalArgumentException("Embedding batch sizes are out of range");
            }
            if (interval == null || interval.compareTo(Duration.ofSeconds(10)) < 0
                    || initialDelay == null || initialDelay.isNegative()) {
                throw new IllegalArgumentException("Embedding interval must be at least 10 seconds; initial delay must not be negative");
            }
            if (maxChunkChars < 256 || maxChunkChars > 16000 || overlapChars < 0 || overlapChars > maxChunkChars / 4) {
                throw new IllegalArgumentException("Embedding chunk size/overlap are out of range");
            }
        }
    }

    private static boolean boundedTimeout(Duration timeout) {
        return timeout != null && timeout.compareTo(Duration.ofMillis(1)) >= 0
                && timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0;
    }

    @Override
    public String toString() {
        return "EmbeddingProperties[enabled=" + enabled + "]";
    }
}
