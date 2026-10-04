package com.knowledgeapplication.api.ai.gemini;

import com.knowledgeapplication.api.metadata.*;
import com.knowledgeapplication.api.ai.quota.AiQuotaLimiter;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@PropertySource("classpath:metadata-suggestions.properties")
@EnableConfigurationProperties(MetadataProperties.class)
public class GeminiMetadataConfiguration {
    @Bean @ConditionalOnProperty(prefix="app.ai.metadata",name="enabled",havingValue="true")
    @ConditionalOnMissingBean(KnowledgeMetadataSuggestionClient.class)
    KnowledgeMetadataSuggestionClient metadataSuggestionClient(MetadataProperties properties, GeminiProperties key, AiQuotaLimiter limiter) {
        return new GeminiKnowledgeMetadataSuggestionClient(properties,key,limiter);
    }
}
