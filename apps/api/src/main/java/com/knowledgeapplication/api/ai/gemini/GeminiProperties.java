package com.knowledgeapplication.api.ai.gemini;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.gemini")
public record GeminiProperties(String apiKey) {
    public void requireKey() {
        if (apiKey == null || apiKey.isBlank() || apiKey.chars().anyMatch(c -> c < 33 || c > 126))
            throw new IllegalArgumentException("A valid backend Gemini API key is required when AI is enabled");
    }
    @Override public String toString() { return "GeminiProperties[redacted]"; }
}
