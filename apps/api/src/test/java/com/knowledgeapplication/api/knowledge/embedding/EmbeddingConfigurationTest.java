package com.knowledgeapplication.api.knowledge.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import com.knowledgeapplication.api.ai.gemini.GeminiProperties;
import com.knowledgeapplication.api.ai.quota.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmbeddingConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EmbeddingConfiguration.class, EmbeddingIndexScheduler.class, Dependencies.class)
            .withPropertyValues("app.embedding.enabled=true", "app.embedding.base-url=http://localhost:12345/v1",
                    "app.embedding.model=test-model", "app.embedding.dimensions=3",
                    "app.embedding.connect-timeout=PT1S", "app.embedding.read-timeout=PT2S",
                    "app.embedding.batch-size=2", "app.embedding.indexing-enabled=true",
                    "app.embedding.interval=PT1M", "app.embedding.initial-delay=PT30S",
                    "app.embedding.knowledge-batch-size=2", "app.embedding.max-chunk-chars=256", "app.embedding.overlap-chars=20");

    @Configuration
    static class Dependencies {
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean KnowledgeEmbeddingIndexer indexer() { return mock(KnowledgeEmbeddingIndexer.class); }
        @Bean GeminiProperties key(org.springframework.core.env.Environment env) { return new GeminiProperties(env.getProperty("test.gemini-api-key","test-key")); }
        @Bean AiQuotaLimiter limiter() { return mock(AiQuotaLimiter.class); }
        @Bean AiQuotaProperties embeddingQuota() { return new AiQuotaProperties(false,80,24000,800,2.5,java.time.ZoneId.of("America/Los_Angeles"),new AiQuotaProperties.Budget(50,18000,650)); }
    }

    @Test
    void disabledModeNeedsNoProviderCredentialsAndCreatesNoClientOrScheduler() {
        runner.withPropertyValues("app.embedding.enabled=false", "app.embedding.base-url=", "app.embedding.model=", "app.gemini.api-key=")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(EmbeddingClient.class).doesNotHaveBean(EmbeddingIndexScheduler.class);
                    verifyNoInteractions(context.getBean(KnowledgeEmbeddingIndexer.class));
                });
    }

    @Test
    void enabledModeRegistersGeminiClientAndScheduler() {
        runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(EmbeddingClient.class).hasSingleBean(EmbeddingIndexScheduler.class));
        runner.withPropertyValues("app.embedding.indexing-enabled=false")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(EmbeddingClient.class).doesNotHaveBean(EmbeddingIndexScheduler.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"base-url=", "base-url=file:///tmp/private", "base-url=http://user:secret@localhost/v1",
            "model=", "dimensions=0", "dimensions=3073", "connect-timeout=PT0S", "read-timeout=PT0S",
            "connect-timeout=PT0.000001S", "read-timeout=PT100000000S",
            "batch-size=0", "knowledge-batch-size=0", "interval=PT1S", "initial-delay=-PT1S",
            "max-chunk-chars=10", "overlap-chars=100"})
    void enabledModeFailsFastOnInvalidConfiguration(String invalid) {
        runner.withPropertyValues("app.embedding." + invalid).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledGeminiRequiresKeyAndPropertiesNeverPrintKey() {
        runner.withPropertyValues("test.gemini-api-key=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("test.gemini-api-key=test-secret")
                .run(context -> assertThat(context.getBean(GeminiProperties.class).toString()).doesNotContain("test-secret"));
    }

    @Test
    void disabledIndexerDoesNotTouchProviderOrDatabase() {
        var repository = mock(KnowledgeEmbeddingRepository.class);
        var client = mock(EmbeddingClient.class);
        var properties = EmbeddingTestSupport.properties(false, "", 3, 2, 2);
        assertThat(EmbeddingTestSupport.indexer(repository, client, properties).indexOnce())
                .isEqualTo(new KnowledgeEmbeddingIndexer.Summary(0, 0, 0, 0));
        verifyNoInteractions(client, repository);
    }
}
