package com.knowledgeapplication.api.knowledge.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Test-only deterministic fake, not a semantic model or runtime fallback. */
public class DeterministicEmbeddingClient implements EmbeddingClient {
    private final int dimensions;
    public DeterministicEmbeddingClient(int dimensions) { this.dimensions = dimensions; }

    @Override
    public List<float[]> embed(List<String> inputs) {
        return inputs.stream().map(input -> {
            try {
                byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
                float[] vector = new float[dimensions];
                for (int i = 0; i < dimensions; i++) vector[i] = ((bytes[i % bytes.length] & 255) + 1) / 256f;
                return vector;
            } catch (Exception ex) {
                throw new AssertionError("SHA-256 unavailable");
            }
        }).toList();
    }
}
