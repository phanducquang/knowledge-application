package com.knowledgeapplication.api.knowledge.publicview;

public class PublicKnowledgeNotFoundException extends RuntimeException {

    public PublicKnowledgeNotFoundException() {
        super("Public knowledge item not found");
    }
}
