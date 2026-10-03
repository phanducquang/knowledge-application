package com.knowledgeapplication.api.ai.gemini;

import com.knowledgeapplication.api.ai.quota.*;
import com.knowledgeapplication.api.ask.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.*;
import org.springframework.context.annotation.*;

@Configuration
@EnableConfigurationProperties({GeminiProperties.class,AskProperties.class})
public class GeminiConfiguration {
    @Bean
    AiQuotaProperties embeddingQuota(org.springframework.core.env.Environment environment) {
        return org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bind("app.embedding.quota",AiQuotaProperties.class).orElseThrow(() -> new IllegalArgumentException("Embedding quota missing"));
    }
    @Bean
    AiQuotaProperties askQuota(org.springframework.core.env.Environment environment) {
        return org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bind("app.ask.quota",AiQuotaProperties.class).orElseThrow(() -> new IllegalArgumentException("Ask quota missing"));
    }
    @Bean @ConditionalOnProperty(prefix="app.ask",name="enabled",havingValue="true")
    @ConditionalOnMissingBean(KnowledgeAnswerClient.class)
    KnowledgeAnswerClient knowledgeAnswerClient(AskProperties properties, GeminiProperties key, AiQuotaLimiter limiter,
            @Qualifier("askQuota") AiQuotaProperties quota) { return new GeminiAnswerClient(properties,key,limiter,quota); }
}
