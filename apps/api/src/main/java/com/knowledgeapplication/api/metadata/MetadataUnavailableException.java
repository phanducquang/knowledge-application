package com.knowledgeapplication.api.metadata;

public class MetadataUnavailableException extends RuntimeException {
    private final boolean disabled;
    private final MetadataFailureCategory category;
    private final Integer httpStatus;
    public MetadataUnavailableException(boolean disabled) {
        this(disabled, MetadataFailureCategory.UNKNOWN_SAFE, null);
    }
    public MetadataUnavailableException(MetadataFailureCategory category) { this(false, category, null); }
    public MetadataUnavailableException(MetadataFailureCategory category, Integer httpStatus) { this(false, category, httpStatus); }
    private MetadataUnavailableException(boolean disabled, MetadataFailureCategory category, Integer httpStatus) {
        super(disabled ? "AI metadata suggestions are disabled" : "AI metadata suggestions are currently unavailable");
        this.disabled=disabled;
        this.category=java.util.Objects.requireNonNull(category);
        this.httpStatus=httpStatus!=null && httpStatus>=400 && httpStatus<=599 ? httpStatus : null;
    }
    public String code() { return disabled ? "AI_METADATA_DISABLED" : "AI_METADATA_UNAVAILABLE"; }
    public MetadataFailureCategory category() { return category; }
    public Integer httpStatus() { return httpStatus; }
}
