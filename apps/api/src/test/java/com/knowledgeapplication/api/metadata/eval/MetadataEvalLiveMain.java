package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.ai.gemini.GeminiKnowledgeMetadataSuggestionClient;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import com.knowledgeapplication.api.metadata.*;

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
        Path runDirectory=null;
        try {
            var settings=MetadataLiveSettings.load(); var corpus=MetadataCorpus.load();
            var props=settings.metadata();
            var identity=MetadataEvaluation.Identity.create(corpus,"gemini",props.model(),props.maxContentChars(),props.maxOutputTokens(),Instant.now());
            var resume=settings.resumeFrom()==null?MetadataResume.Plan.empty():MetadataResume.load(Path.of(settings.resumeFrom()),corpus,identity);
            long tokens=MetadataEvaluation.preflight(corpus,props.maxContentChars(),props.quota(),settings.maxRequests(),settings.maxTokens(),resume.reused().keySet());
            runDirectory=directory.resolve("live-runs").resolve(MetadataResume.hashIdentity(identity));
            java.nio.file.Files.createDirectories(runDirectory.getParent()); java.nio.file.Files.createDirectory(runDirectory);
            System.out.println("Manual SYNTHETIC metadata evaluation: corpusCases="+corpus.fixtures().size()+" reused="+resume.reused().size()
                    +" newCallsPlanned="+(corpus.fixtures().size()-resume.reused().size())+" plannedNewTokens="+tokens
                    +" effectiveRpm="+Math.min(settings.maxRpm(),props.quota().requestsPerMinute())+"; no application database.");
            var attempts=new java.util.concurrent.atomic.AtomicInteger();
            try(var db=new MetadataQuotaDatabase()) {
                var pacer=db.pacer(props.quota(),settings.maxWaitSeconds(),settings.maxRpm(),settings.safetyMillis());
                try(var client=new GeminiKnowledgeMetadataSuggestionClient(props,settings.credentials(),db.limiter,()->{pacer.attemptStarted();attempts.incrementAndGet();})) {
                    report=MetadataEvaluation.run(corpus,client,identity,props.quota(),pacer,true,settings.maxRequests(),settings.maxTokens(),resume);
                }
                var persisted=db.usage();
                report=report.accounting(attempts.get(),persisted.requests(),persisted.tokens());
                if(persisted.requests()!=attempts.get() || persisted.tokens()!=report.usage().attemptedEstimatedInputTokens())
                    report=report.accountingFailure();
                MetadataReportWriter.write(report,directory,true);
                if(!report.status().equals("COMPLETE")) throw new MetadataUnavailableException(report.failure().category());
                System.out.println("Live synthetic evaluation COMPLETE: requests="+persisted.requests()+" estimatedInputTokens="+persisted.tokens()+"; review ignored reports. No production changes.");
            }
        } catch(Exception ex) {
            // Never print exception, cause, Binder values, keys or raw provider bodies.
            var category=ex instanceof MetadataUnavailableException safe?safe.category():MetadataFailureCategory.CONFIGURATION;
            System.err.println("Live metadata evaluation INCOMPLETE: "+category+". Stopped without retries.");
            if(report==null) {
                try { if(runDirectory==null) { runDirectory=directory.resolve("live-runs").resolve(UUID.randomUUID().toString()); java.nio.file.Files.createDirectories(runDirectory); }
                    java.nio.file.Files.writeString(runDirectory.resolve("live-failure.txt"),"INCOMPLETE: "+category+"; no quality baseline.\n",java.nio.file.StandardOpenOption.CREATE_NEW); }
                catch(Exception ignored) { System.err.println("Could not write sanitized failure marker."); }
            }
            System.exit(1);
        }
    }
}
