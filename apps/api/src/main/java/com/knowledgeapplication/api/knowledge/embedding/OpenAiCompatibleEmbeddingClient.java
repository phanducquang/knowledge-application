package com.knowledgeapplication.api.knowledge.embedding;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    private final EmbeddingProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OpenAiCompatibleEmbeddingClient(EmbeddingProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        if (inputs.isEmpty() || inputs.size() > properties.batchSize() || inputs.stream().anyMatch(s -> s == null || s.isBlank())) {
            throw new IllegalArgumentException("Embedding input batch is invalid");
        }
        java.util.concurrent.CompletableFuture<HttpResponse<String>> future = null;
        try {
            var request = HttpRequest.newBuilder(URI.create(properties.baseUrl().replaceAll("/+$", "") + "/embeddings"))
                    .timeout(properties.readTimeout()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                            "model", properties.model(), "input", inputs,
                            "dimensions", properties.dimensions(), "encoding_format", "float"))));
            if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
                request.header("Authorization", "Bearer " + properties.apiKey());
            }
            future = http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
            // Deadline also covers a stalled response body, not just receiving HTTP headers.
            var response = future.get(properties.readTimeout().toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200) throw new EmbeddingUnavailableException();
            var json = mapper.readTree(response.body());
            if (!json.path("model").asString().equals(properties.model()) || !json.path("data").isArray()
                    || json.path("data").size() != inputs.size()) throw new EmbeddingUnavailableException();
            List<float[]> result = new ArrayList<>(java.util.Collections.nCopies(inputs.size(), null));
            for (var item : json.path("data")) {
                var indexNode = item.path("index");
                if (!indexNode.isIntegralNumber() || !indexNode.canConvertToInt()) throw new EmbeddingUnavailableException();
                int index = indexNode.intValue();
                var values = item.path("embedding");
                if (index < 0 || index >= result.size() || result.get(index) != null || !values.isArray()
                        || values.size() != properties.dimensions()) throw new EmbeddingUnavailableException();
                float[] vector = new float[values.size()];
                for (int i = 0; i < vector.length; i++) {
                    if (!values.get(i).isNumber()) throw new EmbeddingUnavailableException();
                    vector[i] = values.get(i).floatValue();
                }
                result.set(index, vector);
            }
            EmbeddingVectors.validate(result, inputs.size(), properties.dimensions());
            return List.copyOf(result);
        } catch (Exception ex) {
            if (future != null) future.cancel(true);
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new EmbeddingUnavailableException();
        }
    }
}
