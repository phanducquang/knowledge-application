package com.knowledgeapplication.api.metadata.eval;

import java.nio.file.Path;
import java.time.Instant;

/** Read-only explicit report inspection, never loads credentials/configuration/database/provider. */
public final class MetadataResumeCheckMain {
    private MetadataResumeCheckMain() {}
    public static void main(String[] args) {
        try {
            String path=System.getenv("METADATA_EVAL_RESUME_FROM");
            if(path==null || path.isBlank()) throw new IllegalArgumentException();
            var corpus=MetadataCorpus.load();
            String model=System.getenv().getOrDefault("AI_METADATA_MODEL","gemini-3.5-flash-lite");
            int limit=Integer.parseInt(System.getenv().getOrDefault("AI_METADATA_MAX_CONTENT_CHARS","32000"));
            int output=Integer.parseInt(System.getenv().getOrDefault("AI_METADATA_MAX_OUTPUT_TOKENS","600"));
            var identity=MetadataEvaluation.Identity.create(corpus,"gemini",model,limit,output,Instant.EPOCH);
            var plan=MetadataResume.load(Path.of(path),corpus,identity);
            System.out.println("Offline resume compatibility PASS: corpus="+corpus.fixtures().size()+" reusable="+plan.reused().size()
                    +" outstanding="+(corpus.fixtures().size()-plan.reused().size())+"; zero provider calls/reservations; no report writes.");
        } catch(Exception ex) { System.err.println("Offline resume compatibility CONFIGURATION failure; no provider calls."); System.exit(1); }
    }
}
