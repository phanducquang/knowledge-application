package com.knowledgeapplication.api.metadata;

import java.util.List;

/** Pure preparation shared by the current-note loader and synthetic evaluations. */
public final class MetadataSuggestionInputBuilder {
    private MetadataSuggestionInputBuilder() {}
    public static KnowledgeMetadataSuggestionClient.Request build(String title, String summary, String content,
            List<String> tags, int maxChars) {
        if (maxChars < 1 || maxChars > 32000) throw new IllegalArgumentException("Invalid metadata input limit");
        int end = Math.min(content.length(), maxChars);
        if (end > 0 && end < content.length() && Character.isHighSurrogate(content.charAt(end - 1))) end--;
        return new KnowledgeMetadataSuggestionClient.Request(title, summary, content.substring(0, end),
                tags.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), content.length() > maxChars);
    }
}
