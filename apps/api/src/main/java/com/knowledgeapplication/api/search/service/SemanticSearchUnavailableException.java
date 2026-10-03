package com.knowledgeapplication.api.search.service;

public final class SemanticSearchUnavailableException extends RuntimeException {
    private final boolean disabled;

    public SemanticSearchUnavailableException(boolean disabled) {
        super(disabled ? "Semantic search is not configured" : "Semantic search is temporarily unavailable");
        this.disabled = disabled;
    }

    public String code() { return disabled ? "SEMANTIC_SEARCH_DISABLED" : "SEMANTIC_SEARCH_UNAVAILABLE"; }
}
