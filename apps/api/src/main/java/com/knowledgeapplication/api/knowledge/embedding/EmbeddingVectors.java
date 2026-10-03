package com.knowledgeapplication.api.knowledge.embedding;

import java.util.List;
import java.util.StringJoiner;

public final class EmbeddingVectors {
    private EmbeddingVectors() {}

    public static void validate(List<float[]> vectors, int count, int dimensions) {
        if (vectors == null || vectors.size() != count) throw new EmbeddingUnavailableException();
        for (float[] vector : vectors) {
            if (vector == null || vector.length != dimensions) throw new EmbeddingUnavailableException();
            double norm = 0;
            for (float value : vector) {
                if (!Float.isFinite(value)) throw new EmbeddingUnavailableException();
                norm += (double) value * value;
            }
            if (norm == 0) throw new EmbeddingUnavailableException();
        }
    }

    static String literal(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) joiner.add(Float.toString(value));
        return joiner.toString();
    }
}
