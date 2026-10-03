package com.knowledgeapplication.api.ai.gemini;

import com.google.genai.Client;
import com.google.genai.types.*;
import okhttp3.OkHttpClient;
import java.time.Duration;

final class GeminiClients {
    private GeminiClients() {}
    static Client create(GeminiProperties key, String baseUrl, Duration connect, Duration read) {
        key.requireKey();
        // Explicit credentials/backend: never fall back to ambient GOOGLE_API_KEY or Vertex auth.
        return Client.builder().apiKey(key.apiKey()).vertexAI(false)
                .httpOptions(HttpOptions.builder().baseUrl(baseUrl).apiVersion("v1beta")
                        .timeout(Math.toIntExact(read.toMillis())).retryOptions(HttpRetryOptions.builder().attempts(1).build()).build())
                .clientOptions(ClientOptions.builder().customHttpClient(new OkHttpClient.Builder()
                        .connectTimeout(connect).readTimeout(read).callTimeout(read)
                        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()).build()).build();
    }
}
