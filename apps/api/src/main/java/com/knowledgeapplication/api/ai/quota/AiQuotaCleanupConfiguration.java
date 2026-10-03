package com.knowledgeapplication.api.ai.quota;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiQuotaCleanupProperties.class)
public class AiQuotaCleanupConfiguration {
    // Scheduling and Clock are already configured application-wide.
}
