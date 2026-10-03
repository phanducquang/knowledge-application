package com.knowledgeapplication.api.knowledge.embedding;

import java.util.List;

public interface EmbeddingClient {
    /** Background budget boundary; fakes may delegate to the normal batch contract. */
    default java.util.List<float[]> embedBackground(java.util.List<String> inputs) { return embed(inputs); }
    /** One finite, non-zero vector per input, in input order. */
    List<float[]> embed(List<String> inputs);
}
