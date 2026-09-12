package com.knowledgeapplication.api.knowledge.service;

public class UnlistedLinkNotFoundException extends RuntimeException {

    public UnlistedLinkNotFoundException() {
        super("Unlisted link not found");
    }
}
