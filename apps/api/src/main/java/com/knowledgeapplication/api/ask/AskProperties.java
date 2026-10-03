package com.knowledgeapplication.api.ask;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("app.ask")
public record AskProperties(boolean enabled, String baseUrl, String model, Duration connectTimeout, Duration readTimeout,
        int maxOutputTokens, int maxContextChars, int maxChunks, int maxChunksPerKnowledge, int maxSources) {
    public AskProperties {
        if (enabled) {
            URI uri;
            try { uri=URI.create(baseUrl == null ? "" : baseUrl); }
            catch (RuntimeException ex) { throw new IllegalArgumentException("Ask base URL is invalid"); }
            if (!java.util.Set.of("http","https").contains(uri.getScheme()==null ? "" : uri.getScheme()) || uri.getHost()==null
                    || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException("Ask base URL is invalid");
            if (model==null || model.isBlank() || model.length()>255) throw new IllegalArgumentException("Ask model is invalid");
            if (connectTimeout==null || connectTimeout.toMillis()<1 || connectTimeout.toMillis()>Integer.MAX_VALUE
                    || readTimeout==null || readTimeout.toMillis()<1 || readTimeout.toMillis()>Integer.MAX_VALUE)
                throw new IllegalArgumentException("Ask timeouts must be positive bounded milliseconds");
            if (maxOutputTokens<1 || maxOutputTokens>8192 || maxContextChars<1024 || maxContextChars>128000
                    || maxChunks<1 || maxChunks>100 || maxChunksPerKnowledge<1 || maxChunksPerKnowledge>10
                    || maxSources<1 || maxSources>30) throw new IllegalArgumentException("Ask context/output limits are invalid");
        }
    }
    @Override public String toString() { return "AskProperties[enabled=" + enabled + "]"; }
}
