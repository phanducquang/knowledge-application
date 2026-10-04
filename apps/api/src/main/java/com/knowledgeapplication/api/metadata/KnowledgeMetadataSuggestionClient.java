package com.knowledgeapplication.api.metadata;

import java.util.List;

public interface KnowledgeMetadataSuggestionClient {
    record Request(String title, String summary, String content, List<String> currentTags, boolean contentTruncated) {
        @Override public String toString() { return "MetadataRequest[redacted]"; }
    }
    MetadataSuggestion suggest(Request request);
}
