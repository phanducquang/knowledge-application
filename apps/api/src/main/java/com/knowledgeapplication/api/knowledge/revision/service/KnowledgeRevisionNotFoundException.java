package com.knowledgeapplication.api.knowledge.revision.service;

public class KnowledgeRevisionNotFoundException extends RuntimeException {

    public KnowledgeRevisionNotFoundException() {
        super("Knowledge revision not found");
    }
}
