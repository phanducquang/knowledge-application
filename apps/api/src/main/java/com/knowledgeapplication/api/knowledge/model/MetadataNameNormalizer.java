package com.knowledgeapplication.api.knowledge.model;

import java.util.Locale;

public final class MetadataNameNormalizer {

    private MetadataNameNormalizer() {
    }

    public static String collectionDisplayName(String value) {
        return normalizeWhitespace(value, "collection");
    }

    public static String tagDisplayName(String value) {
        String normalized = normalizeWhitespace(value, "tag");
        normalized = normalized.replaceFirst("^#+", "").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
        return normalizeWhitespace(normalized, "tag");
    }

    public static String key(String displayName) {
        return displayName.toLowerCase(Locale.ROOT);
    }

    private static String normalizeWhitespace(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
