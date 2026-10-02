package com.knowledgeapplication.api.knowledge.collection;

public class CollectionNotFoundException extends RuntimeException {

    public CollectionNotFoundException() {
        super("Collection not found");
    }
}
