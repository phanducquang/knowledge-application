package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.ai.gemini.GeminiProperties;
import com.knowledgeapplication.api.metadata.MetadataProperties;
import java.net.URI;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/** Existing secure local configuration only. No Spring application context/datasource is started. */
record MetadataLiveSettings(MetadataProperties metadata,GeminiProperties credentials,int maxRequests,long maxTokens,int maxWaitSeconds) {
    static void gates(boolean dedicatedTask,String flag) {
        if(!dedicatedTask || !"true".equals(flag)) throw new IllegalArgumentException("Dedicated live task and exact true flag required");
    }
    MetadataLiveSettings {
        credentials.requireKey();
        if(!metadata.enabled() || !metadata.quota().enabled()) throw new IllegalArgumentException("Live evaluator needs validated metadata and quota configuration");
        if(!metadata.model().matches("(?:models/)?[a-z0-9][a-z0-9._-]{0,200}")) throw new IllegalArgumentException("Invalid configured model identifier");
        var origin=URI.create(metadata.baseUrl());
        if(!"https".equals(origin.getScheme()) || !"generativelanguage.googleapis.com".equals(origin.getHost()) || origin.getPort()!=-1
                || !(origin.getPath().isEmpty() || origin.getPath().equals("/"))) throw new IllegalArgumentException("Official Gemini origin required for live evaluation");
        if(maxRequests<1 || maxRequests>20 || maxTokens<1 || maxTokens>150000 || maxWaitSeconds<0 || maxWaitSeconds>600)
            throw new IllegalArgumentException("Evaluation safety bounds exceeded");
    }
    static MetadataLiveSettings load() throws java.io.IOException {
        gates(Boolean.getBoolean("metadata.eval.live-task"),System.getenv("METADATA_EVAL_LIVE"));
        var environment=new StandardEnvironment();
        // Validation enabled ONLY in this manual process; production feature flag/file remains unchanged.
        environment.getPropertySources().addFirst(new MapPropertySource("manual-metadata-evaluation",Map.of("app.ai.metadata.enabled",true)));
        for(var source:new YamlPropertySourceLoader().load("existing-backend-configuration",new ClassPathResource("application.yml")))
            environment.getPropertySources().addLast(source);
        environment.getPropertySources().addLast(new ResourcePropertySource(new ClassPathResource("metadata-suggestions.properties")));
        return bind(environment);
    }
    static MetadataLiveSettings bind(Environment environment) {
        var binder=Binder.get(environment);
        return new MetadataLiveSettings(binder.bind("app.ai.metadata",MetadataProperties.class).orElseThrow(()->new IllegalArgumentException("Metadata configuration required")),
                binder.bind("app.gemini",GeminiProperties.class).orElseThrow(()->new IllegalArgumentException("Gemini configuration required")),
                Integer.parseInt(environment.getProperty("METADATA_EVAL_LIVE_MAX_REQUESTS","20")),
                Long.parseLong(environment.getProperty("METADATA_EVAL_LIVE_MAX_ESTIMATED_INPUT_TOKENS","150000")),
                Integer.parseInt(environment.getProperty("METADATA_EVAL_LIVE_MAX_WAIT_SECONDS","180")));
    }
    @Override public String toString() { return "MetadataLiveSettings[redacted]"; }
}
