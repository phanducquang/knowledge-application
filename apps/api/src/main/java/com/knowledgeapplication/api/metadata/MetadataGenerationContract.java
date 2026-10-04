package com.knowledgeapplication.api.metadata;

/** Traceability identifiers: bump when semantics change, not when a model is configured. */
public final class MetadataGenerationContract {
    public static final String PROMPT_VERSION = "metadata-prompt-v1";
    public static final String SCHEMA_VERSION = "metadata-schema-v1";
    private MetadataGenerationContract() {}
    public static String inputStrategyVersion(int maxChars) { return "prefix-" + maxChars + "-v1"; }
}
