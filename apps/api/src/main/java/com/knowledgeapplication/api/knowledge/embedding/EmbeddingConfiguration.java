package com.knowledgeapplication.api.knowledge.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.knowledgeapplication.api.ai.gemini.*;
import com.knowledgeapplication.api.ai.quota.*;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.embedding", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(EmbeddingClient.class)
    EmbeddingClient embeddingClient(EmbeddingProperties properties, GeminiProperties key, AiQuotaLimiter limiter,
            @Qualifier("embeddingQuota") AiQuotaProperties quota) {
        return new GeminiEmbeddingClient(properties, key, limiter, quota);
    }

    @Bean
    EmbeddingStrategy embeddingStrategy(EmbeddingProperties properties) {
        return new EmbeddingStrategy(properties, MarkdownChunker.VERSION);
    }

    @Bean
    MarkdownChunker markdownChunker(EmbeddingProperties properties) {
        return new MarkdownChunker(properties.maxChunkChars(), properties.overlapChars());
    }
}
