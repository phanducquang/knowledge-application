package com.knowledgeapplication.api.metadata;

import com.knowledgeapplication.api.knowledge.model.Tag;
import com.knowledgeapplication.api.knowledge.repository.KnowledgeRepository;
import com.knowledgeapplication.api.knowledge.service.KnowledgeNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class MetadataSnapshotLoader {
    private final KnowledgeRepository repository;
    public MetadataSnapshotLoader(KnowledgeRepository repository) { this.repository=repository; }
    @Transactional(readOnly=true)
    public KnowledgeMetadataSuggestionClient.Request load(Long id, UUID owner, int maxChars) {
        var note=repository.findByIdAndOwnerId(id,owner).orElseThrow(KnowledgeNotFoundException::new);
        String content=note.getContent(); boolean truncated=content.length()>maxChars;
        int end=Math.min(content.length(),maxChars);
        if (end>0 && end<content.length() && Character.isHighSurrogate(content.charAt(end-1))) end--;
        // Only text, no attachment object lookup, revision reads, share tokens or owner identifier.
        return new KnowledgeMetadataSuggestionClient.Request(note.getTitle(),note.getSummary(),content.substring(0,end),
                note.getTags().stream().map(Tag::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList(),truncated);
    }
}
