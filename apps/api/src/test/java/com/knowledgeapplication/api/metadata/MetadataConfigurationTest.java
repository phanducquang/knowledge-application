package com.knowledgeapplication.api.metadata;

import com.knowledgeapplication.api.ai.gemini.*;
import com.knowledgeapplication.api.ai.quota.AiQuotaLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetadataConfigurationTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(GeminiMetadataConfiguration.class)
            .withBean(GeminiProperties.class,()->new GeminiProperties(""))
            .withBean(AiQuotaLimiter.class,()->mock(AiQuotaLimiter.class));
    @Test void defaultDisabledHasIndependentDefaultsAndNoProviderOrKeyRequirement() {
        runner.run(context->{
            assertThat(context).hasNotFailed().doesNotHaveBean(KnowledgeMetadataSuggestionClient.class);
            var props=context.getBean(MetadataProperties.class);
            assertThat(props.enabled()).isFalse(); assertThat(props.model()).isEqualTo("gemini-3.5-flash-lite");
            assertThat(props.maxContentChars()).isEqualTo(32000); assertThat(props.maxOutputTokens()).isEqualTo(600);
            assertThat(props.quota().requestsPerMinute()).isEqualTo(10); assertThat(props.quota().inputTokensPerMinute()).isEqualTo(200000);
            assertThat(props.quota().requestsPerDay()).isEqualTo(400);
        });
    }
    @Test void environmentAliasesOverrideModelLimitsAndQuotaWithoutJavaChanges() {
        runner.withPropertyValues("AI_METADATA_MODEL=independent-model","AI_METADATA_MAX_OUTPUT_TOKENS=700","AI_METADATA_RPD_LIMIT=7")
                .run(context->{ var props=context.getBean(MetadataProperties.class); assertThat(props.model()).isEqualTo("independent-model");
                    assertThat(props.maxOutputTokens()).isEqualTo(700); assertThat(props.quota().requestsPerDay()).isEqualTo(7); });
    }
    @Test void enabledRequiresExistingGeminiCredential() {
        runner.withPropertyValues("app.ai.metadata.enabled=true").run(context->assertThat(context).hasFailed());
    }
    @Test void ambientEnableAliasCannotBypassGradleOfflineOverride() {
        runner.withPropertyValues("AI_METADATA_ENABLED=true").run(context->{
            assertThat(context).hasNotFailed().doesNotHaveBean(KnowledgeMetadataSuggestionClient.class);
            assertThat(context.getBean(MetadataProperties.class).enabled()).isFalse();
        });
    }
    @Test void outputAndInputConfigurationCannotExpandHardBounds() {
        runner.withPropertyValues("app.ai.metadata.enabled=true","app.ai.metadata.max-content-chars=32001")
                .run(context->assertThat(context).hasFailed());
    }
}
