package com.knowledgeapplication.api.metadata;

public class MetadataUnavailableException extends RuntimeException {
    private final boolean disabled;
    public MetadataUnavailableException(boolean disabled) {
        super(disabled ? "AI metadata suggestions are disabled" : "AI metadata suggestions are currently unavailable");
        this.disabled=disabled;
    }
    public String code() { return disabled ? "AI_METADATA_DISABLED" : "AI_METADATA_UNAVAILABLE"; }
}
