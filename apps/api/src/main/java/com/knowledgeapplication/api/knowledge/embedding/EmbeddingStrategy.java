package com.knowledgeapplication.api.knowledge.embedding;

public record EmbeddingStrategy(EmbeddingProperties properties, int chunkerVersion) {
    public String marker() {
        return "semantic-source-v1:chunker=" + chunkerVersion + ":max=" + properties.maxChunkChars()
                + ":overlap=" + properties.overlapChars() + ":dimensions=" + properties.dimensions()
                + ":provider=" + EmbeddingSource.field(properties.baseUrl())
                + ":model=" + EmbeddingSource.field(properties.model()) + ":fields:";
    }
}
