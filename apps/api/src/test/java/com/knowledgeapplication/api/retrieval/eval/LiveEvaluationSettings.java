package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.ai.gemini.GeminiProperties;
import com.knowledgeapplication.api.ai.quota.AiQuotaProperties;
import com.knowledgeapplication.api.knowledge.embedding.EmbeddingProperties;
import java.net.URI;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** Reads only existing Spring settings. Never starts an application context or binds a datasource. */
record LiveEvaluationSettings(EmbeddingProperties embedding, GeminiProperties credentials, AiQuotaProperties quota,
        int maxRequests, long maxTokens, int maxWaitSeconds) {
    static void gates(boolean dedicatedTask, String flag) {
        if (!dedicatedTask || !"true".equals(flag)) throw new IllegalArgumentException("Explicit manual live task and flag required");
    }
    LiveEvaluationSettings {
        credentials.requireKey();
        if (embedding.model()==null || !embedding.model().matches("(?:models/)?[a-z0-9][a-z0-9._-]{0,200}"))
            throw new IllegalArgumentException("A configured embedding model identifier is required");
        if (!embedding.enabled() || embedding.maxChunkChars()!=4000 || embedding.overlapChars()!=200)
            throw new IllegalArgumentException("Live evaluation requires the unchanged baseline chunking");
        var origin = URI.create(embedding.baseUrl());
        if (!"https".equals(origin.getScheme()) || !"generativelanguage.googleapis.com".equals(origin.getHost())
                || origin.getPort()!=-1 || !(origin.getPath().isEmpty() || origin.getPath().equals("/")))
            throw new IllegalArgumentException("Live evaluation requires the official Gemini origin");
        if (!quota.enabled() || quota.background()==null) throw new IllegalArgumentException("Enabled global/background quota protection required");
        if (maxRequests<1 || maxRequests>20 || maxTokens<1 || maxTokens>60000 || maxWaitSeconds<0 || maxWaitSeconds>600)
            throw new IllegalArgumentException("Evaluation budgets must not exceed 20 requests/60000 estimated tokens/600 seconds waiting");
    }
    static LiveEvaluationSettings load() throws java.io.IOException {
        gates(Boolean.getBoolean("retrieval.eval.live-task"),System.getenv("RETRIEVAL_EVAL_LIVE"));
        var environment = new StandardEnvironment();
        // Enable validation ONLY for this manual evaluator; production flags/files remain untouched.
        environment.getPropertySources().addFirst(new MapPropertySource("manual-evaluation",Map.of("app.embedding.enabled",true)));
        for (var source : new YamlPropertySourceLoader().load("existing-backend-configuration",new ClassPathResource("application.yml")))
            environment.getPropertySources().addLast(source);
        return bind(environment);
    }
    static LiveEvaluationSettings bind(org.springframework.core.env.Environment environment) {
        var binder = Binder.get(environment);
        return new LiveEvaluationSettings(binder.bind("app.embedding",EmbeddingProperties.class).orElseThrow(() -> new IllegalArgumentException("Embedding configuration required")),
                binder.bind("app.gemini",GeminiProperties.class).orElseThrow(() -> new IllegalArgumentException("Gemini configuration required")),
                binder.bind("app.embedding.quota",AiQuotaProperties.class).orElseThrow(() -> new IllegalArgumentException("Quota configuration required")),
                Integer.parseInt(environment.getProperty("RETRIEVAL_EVAL_LIVE_MAX_REQUESTS","20")),
                Long.parseLong(environment.getProperty("RETRIEVAL_EVAL_LIVE_MAX_ESTIMATED_INPUT_TOKENS","60000")),
                Integer.parseInt(environment.getProperty("RETRIEVAL_EVAL_LIVE_MAX_WAIT_SECONDS","180")));
    }
    @Override public String toString() { return "LiveEvaluationSettings[redacted]"; }
}
