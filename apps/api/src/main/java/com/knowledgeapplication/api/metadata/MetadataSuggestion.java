package com.knowledgeapplication.api.metadata;

import com.knowledgeapplication.api.knowledge.model.MetadataNameNormalizer;
import java.util.*;

/** Untrusted output is validated atomically; new tags only, never deletion/replacement. */
public record MetadataSuggestion(String summary, List<String> tags) {
    public static final int MAX_SUMMARY=500, MAX_TAGS=5, MAX_TAG_LENGTH=50;
    public MetadataSuggestion validated(List<String> existingTags) {
        if (summary==null || summary.isBlank() || summary.length()>MAX_SUMMARY || tags==null || tags.size()>MAX_TAGS
                || summary.codePoints().anyMatch(c -> Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t'))
            throw new MetadataUnavailableException(false);
        var existing=new HashSet<String>();
        existingTags.forEach(t -> existing.add(MetadataNameNormalizer.key(MetadataNameNormalizer.tagDisplayName(t))));
        var normalized=new LinkedHashMap<String,String>();
        for (String tag:tags) {
            if (tag==null || tag.isBlank() || tag.length()>MAX_TAG_LENGTH || tag.codePoints().anyMatch(Character::isISOControl))
                throw new MetadataUnavailableException(false);
            String name;
            try { name=MetadataNameNormalizer.tagDisplayName(tag); }
            catch (IllegalArgumentException ex) { throw new MetadataUnavailableException(false); }
            String key=MetadataNameNormalizer.key(name);
            if (!existing.contains(key)) normalized.putIfAbsent(key,name);
        }
        return new MetadataSuggestion(summary.trim(),List.copyOf(normalized.values()));
    }
    @Override public String toString() { return "MetadataSuggestion[redacted]"; }
}
