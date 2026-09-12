package com.knowledgeapplication.api.knowledge.service;

public class KnowledgeNotFoundException extends RuntimeException {

    public KnowledgeNotFoundException() {
        super("Knowledge item not found");
    }
}
