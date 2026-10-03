package com.knowledgeapplication.api.search.service;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.embedding.*;
import com.knowledgeapplication.api.knowledge.embedding.KnowledgeEmbeddingRepository.NearestKnowledge;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class KnowledgeSemanticSearchService {
    public static final int MAX_EXCERPT_CHARS = 600;
    private final CurrentOwner currentOwner;
    private final ObjectProvider<EmbeddingClient> clients;
    private final EmbeddingProperties properties;
    private final EmbeddingStrategy strategy;
    private final KnowledgeEmbeddingRepository repository;

    public KnowledgeSemanticSearchService(CurrentOwner currentOwner, ObjectProvider<EmbeddingClient> clients,
            EmbeddingProperties properties, EmbeddingStrategy strategy, KnowledgeEmbeddingRepository repository) {
        this.currentOwner = currentOwner;
        this.clients = clients;
        this.properties = properties;
        this.strategy = strategy;
        this.repository = repository;
    }

    // Deliberately no transaction: external embedding must not hold a database transaction/connection.
    public List<NearestKnowledge> search(String query, int limit) {
        if (query == null || query.isBlank() || query.length() > 200) throw new IllegalArgumentException("Search query is invalid");
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("Search limit is invalid");
        var owner = currentOwner.id();
        if (!properties.enabled()) throw new SemanticSearchUnavailableException(true);
        var client = clients.getIfAvailable();
        if (client == null) throw new SemanticSearchUnavailableException(true);
        float[] vector;
        try {
            var vectors = client.embed(List.of(query.trim()));
            EmbeddingVectors.validate(vectors, 1, properties.dimensions());
            vector = vectors.get(0);
        } catch (RuntimeException unavailable) {
            // No upstream body, cause, input or credentials cross the public exception boundary.
            throw new SemanticSearchUnavailableException(false);
        }
        return repository.findNearestKnowledge(owner, strategy, vector, limit);
    }

    public static String excerpt(String text) {
        if (text.length() <= MAX_EXCERPT_CHARS) return text;
        int end = MAX_EXCERPT_CHARS - 1;
        if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
        return text.substring(0, end) + "…";
    }
}
