package com.knowledgeapplication.api.knowledge.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public record EmbeddingSource(long knowledgeId, UUID ownerId, String title, String summary, String content, Instant updatedAt) {
    static String field(String text) {
        String value = text == null ? "" : text;
        return value.getBytes(StandardCharsets.UTF_8).length + ":" + value;
    }

    public String hash(EmbeddingStrategy strategy) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (strategy.marker() + field(title) + field(summary) + field(content)).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the Java platform");
        }
    }

    public String input(String chunk) {
        return "Title: " + title + (summary == null || summary.isBlank() ? "" : "\nSummary: " + summary) + "\n\n" + chunk;
    }

    @Override
    public String toString() { return "EmbeddingSource[knowledgeId=" + knowledgeId + "]"; }
}
