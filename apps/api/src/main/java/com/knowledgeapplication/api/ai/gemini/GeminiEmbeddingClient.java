package com.knowledgeapplication.api.ai.gemini;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.EmbedContentConfig;
import com.knowledgeapplication.api.ai.quota.*;
import com.knowledgeapplication.api.knowledge.embedding.*;
import java.util.*;

public class GeminiEmbeddingClient implements EmbeddingClient, AutoCloseable {
    private final Client client;
    private final EmbeddingProperties properties;
    private final AiQuotaLimiter limiter;
    private final AiQuotaProperties quota;
    public GeminiEmbeddingClient(EmbeddingProperties props, GeminiProperties key, AiQuotaLimiter limiter, AiQuotaProperties quota) {
        this.properties=props; this.limiter=limiter; this.quota=quota;
        client=GeminiClients.create(key, props.baseUrl(), props.connectTimeout(), props.readTimeout());
    }
    @Override public List<float[]> embed(List<String> inputs) { return embed(inputs, AiQuotaLimiter.Purpose.EMBEDDING); }
    @Override public List<float[]> embedBackground(List<String> inputs) { return embed(inputs, AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND); }
    private List<float[]> embed(List<String> inputs, AiQuotaLimiter.Purpose purpose) {
        if (inputs.isEmpty() || inputs.size()>properties.batchSize() || inputs.stream().anyMatch(s -> s==null || s.isBlank()))
            throw new IllegalArgumentException("Embedding input batch is invalid");
        // Symmetric task prefix is deliberately included in strategy v2 for document/query compatibility.
        var texts=inputs.stream().map(s -> "task: semantic similarity | text: " + s).toList();
        try {
            if (!limiter.reserve(purpose, quota, texts.stream().mapToLong(String::length).sum())) throw new EmbeddingQuotaUnavailableException();
            // SDK List<String> overload uses native synchronous batchEmbedContents, ONE request, ordered independent contents.
            var response=client.models.embedContent(properties.model(), texts,
                    EmbedContentConfig.builder().outputDimensionality(properties.dimensions()).build());
            List<float[]> vectors=new ArrayList<>();
            for (var embedding : response.embeddings().orElseThrow()) {
                var values=embedding.values().orElseThrow();
                float[] vector=new float[values.size()];
                for (int i=0;i<vector.length;i++) vector[i]=values.get(i);
                vectors.add(vector);
            }
            EmbeddingVectors.validate(vectors, inputs.size(), properties.dimensions());
            return List.copyOf(vectors);
        } catch (EmbeddingQuotaUnavailableException ex) { throw ex;
        } catch (ApiException ex) {
            if (ex.code()==429) throw new EmbeddingQuotaUnavailableException();
            throw new EmbeddingUnavailableException();
        } catch (RuntimeException ex) { throw new EmbeddingUnavailableException(); }
    }
    @Override public void close() { client.close(); }
}
