package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.ai.gemini.GeminiKnowledgeMetadataSuggestionClient;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Only manual gated entry point can instantiate the real adapter; never a JUnit test. */
public final class MetadataEvalLiveMain {
    private MetadataEvalLiveMain() {}
    public static void main(String[] args) {
        var root=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.WARN);
        for(var name:new String[]{"com.google","okhttp3","org.springframework.core.env"})
            ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(name)).setLevel(ch.qos.logback.classic.Level.OFF);
        var directory=Path.of(System.getProperty("metadata.eval.report-directory","build/reports/metadata-eval"));
        MetadataEvaluation.Report report=null;
        try {
            var settings=MetadataLiveSettings.load(); var corpus=MetadataCorpus.load();
            var props=settings.metadata();
            long tokens=MetadataEvaluation.preflight(corpus,props.maxContentChars(),props.quota(),settings.maxRequests(),settings.maxTokens());
            var identity=MetadataEvaluation.Identity.create(corpus,"gemini",props.model(),props.maxContentChars(),props.maxOutputTokens(),Instant.now());
            System.out.println("Manual SYNTHETIC metadata evaluation: model="+props.model()+" cases="+corpus.fixtures().size()+" plannedEstimatedTokens="+tokens+"; no application database.");
            try(var db=new MetadataQuotaDatabase(); var client=new GeminiKnowledgeMetadataSuggestionClient(props,settings.credentials(),db.limiter)) {
                report=MetadataEvaluation.run(corpus,client,identity,props.quota(),db.pacer(props.quota(),settings.maxWaitSeconds()),true,settings.maxRequests(),settings.maxTokens());
                MetadataReportWriter.write(report,directory,true);
                var persisted=db.usage();
                if(persisted.requests()!=report.usage().providerRequests() || persisted.tokens()!=report.usage().attemptedEstimatedInputTokens())
                    throw new IllegalStateException("Conservative isolated quota accounting mismatch");
                if(!report.status().equals("COMPLETE")) throw new IllegalStateException("Incomplete live evaluation");
                System.out.println("Live synthetic evaluation COMPLETE: requests="+persisted.requests()+" estimatedInputTokens="+persisted.tokens()+"; review ignored reports. No production changes.");
            }
        } catch(Exception ex) {
            // Never print exception, cause, Binder values, keys or raw provider bodies.
            System.err.println("Live metadata evaluation INCOMPLETE: configuration/budget/quota/provider/output failure. Stopped without retries.");
            if(report==null) {
                try { java.nio.file.Files.createDirectories(directory); java.nio.file.Files.writeString(directory.resolve("live-failure.txt"),
                        "INCOMPLETE before result report. No quality baseline claimed; inspect configuration/safety gates privately.\n"); }
                catch(Exception ignored) { System.err.println("Could not write sanitized failure marker."); }
            }
            System.exit(1);
        }
    }
}
