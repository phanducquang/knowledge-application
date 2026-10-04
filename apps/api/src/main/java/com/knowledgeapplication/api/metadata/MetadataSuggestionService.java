package com.knowledgeapplication.api.metadata;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class MetadataSuggestionService {
    private final CurrentOwner owner;
    private final MetadataProperties properties;
    private final MetadataSnapshotLoader snapshots;
    private final ObjectProvider<KnowledgeMetadataSuggestionClient> clients;
    public MetadataSuggestionService(CurrentOwner owner, MetadataProperties properties, MetadataSnapshotLoader snapshots,
            ObjectProvider<KnowledgeMetadataSuggestionClient> clients) {
        this.owner=owner; this.properties=properties; this.snapshots=snapshots; this.clients=clients;
    }
    // Suspend any ambient transaction; snapshot's short read transaction finishes before Gemini.
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public MetadataSuggestion suggest(Long id) {
        var ownerId=owner.id();
        if (!properties.enabled()) throw new MetadataUnavailableException(true);
        var input=snapshots.load(id,ownerId,properties.maxContentChars());
        try {
            var client=clients.getIfAvailable();
            if (client==null) throw new MetadataUnavailableException(false);
            return client.suggest(input).validated(input.currentTags());
        } catch (RuntimeException ex) { throw new MetadataUnavailableException(false); }
    }
}
