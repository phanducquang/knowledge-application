package com.knowledgeapplication.api.knowledge.embedding;

public class EmbeddingUnavailableException extends RuntimeException {
    public EmbeddingUnavailableException() {
        // Deliberately retain neither provider body nor nested exceptions with sensitive input.
        super("Embedding provider unavailable or returned an invalid response");
    }
}
