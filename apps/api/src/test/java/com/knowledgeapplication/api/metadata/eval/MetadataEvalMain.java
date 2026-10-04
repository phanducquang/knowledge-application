package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.ai.quota.AiQuotaProperties;
import java.nio.file.Path;
import java.time.*;

/** Offline entry point: never loads application.yml, credentials, Spring context or a provider. */
public final class MetadataEvalMain {
    private MetadataEvalMain() {}
    public static void main(String[] args) throws Exception {
        var corpus=MetadataCorpus.load();
        var quota=new AiQuotaProperties(true,10,200000,400,2.5,ZoneId.of("America/Los_Angeles"),null);
        var identity=MetadataEvaluation.Identity.create(corpus,"offline-fake","deterministic-fixture-v1",32000,600,Instant.EPOCH);
        var report=MetadataEvaluation.run(corpus,MetadataEvaluation.fake(corpus,32000),identity,quota,c->{},false,20,150000);
        MetadataReportWriter.write(report,Path.of(System.getProperty("metadata.eval.report-directory","build/reports/metadata-eval")),false);
        if(!report.status().equals("COMPLETE")) throw new IllegalStateException("Offline benchmark failed");
        System.out.println("Offline metadata evaluation COMPLETE: "+corpus.fixtures().size()+" synthetic cases, zero external calls.");
    }
}
