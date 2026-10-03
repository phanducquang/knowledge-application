package com.knowledgeapplication.api.knowledge.embedding;

import java.util.List;

public interface EmbeddingClient {
    /** One finite, non-zero vector per input, in input order. */
    List<float[]> embed(List<String> inputs);
}
