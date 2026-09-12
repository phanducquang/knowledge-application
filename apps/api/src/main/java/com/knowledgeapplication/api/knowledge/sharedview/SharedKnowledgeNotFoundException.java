package com.knowledgeapplication.api.knowledge.sharedview;

public class SharedKnowledgeNotFoundException extends RuntimeException {

    public SharedKnowledgeNotFoundException() {
        super("Shared Knowledge item not found");
    }
}
