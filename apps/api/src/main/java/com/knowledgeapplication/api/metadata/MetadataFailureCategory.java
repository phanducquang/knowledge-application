package com.knowledgeapplication.api.metadata;

/** Allowlisted diagnostics only; never provider text, causes, credentials or payloads. */
public enum MetadataFailureCategory {
    LOCAL_QUOTA, PACING_BUDGET, PROVIDER_RATE_LIMIT, PROVIDER_TIMEOUT, PROVIDER_UNAVAILABLE,
    INVALID_STRUCTURED_OUTPUT, OUTPUT_VALIDATION, CONFIGURATION, SAFETY_BUDGET, UNKNOWN_SAFE
}
