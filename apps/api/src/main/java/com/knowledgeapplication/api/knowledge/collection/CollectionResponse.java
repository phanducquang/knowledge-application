package com.knowledgeapplication.api.knowledge.collection;

public record CollectionResponse(Long id, String name, long knowledgeCount) {

    public static CollectionResponse from(CollectionSummary summary) {
        return new CollectionResponse(summary.id(), summary.name(), summary.knowledgeCount());
    }
}
