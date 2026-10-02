package com.knowledgeapplication.api.knowledge.collection;

public class CollectionNameConflictException extends RuntimeException {

    public CollectionNameConflictException() {
        super("A collection with this name already exists");
    }
}
