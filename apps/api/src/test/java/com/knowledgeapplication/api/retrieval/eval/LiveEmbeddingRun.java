package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.ai.quota.AiQuotaProperties;
import com.knowledgeapplication.api.knowledge.embedding.*;
import java.util.*;

/** Test-only run budgets, fail-stop and vector cache; delegates batching/input semantics to production. */
final class LiveEmbeddingRun {
    static final String TASK_PREFIX = "task: semantic similarity | text: "; // production adapter's cost overhead, NOT added to inputs here
    interface Pacer { void await(boolean background, long prefixedChars); }
    record Usage(int documentRequests, int queryRequests, int successfulRequests, int documentInputs, int queryInputs,
            long estimatedInputTokens, int totalRequests) {}
    private final EmbeddingClient provider;
    private final AiQuotaProperties quota;
    private final int batchSize, maxRequests;
    private final long maxTokens;
    private final Pacer pacer;
    private int documents, queries, successful, documentInputs, queryInputs;
    private long tokens;
    private boolean stopped;
    private boolean documentsStarted, queriesStarted;
    LiveEmbeddingRun(EmbeddingClient provider, AiQuotaProperties quota, int batchSize, int maxRequests, long maxTokens, Pacer pacer) {
        if (batchSize<1 || maxRequests<1 || maxTokens<1) throw new IllegalArgumentException("Invalid run limits");
        this.provider=provider; this.quota=quota; this.batchSize=batchSize; this.maxRequests=maxRequests; this.maxTokens=maxTokens; this.pacer=pacer;
    }
    long chars(List<String> inputs) { return inputs.stream().mapToLong(s -> s.length()+TASK_PREFIX.length()).sum(); }
    void preflight(List<String> documentTexts, List<String> queryTexts) {
        long plannedTokens=0; int requests=0;
        for (var phase : List.of(documentTexts,queryTexts)) {
            var unique=new ArrayList<>(new LinkedHashSet<>(phase));
            for(int i=0;i<unique.size();i+=batchSize) { requests++; plannedTokens+=quota.estimate(chars(unique.subList(i,Math.min(unique.size(),i+batchSize)))); }
        }
        if (requests>maxRequests || plannedTokens>maxTokens) throw new LiveEvaluationFailure(LiveEvaluationFailure.Kind.RUN_BUDGET);
    }
    Map<String,float[]> embedOnce(List<String> texts, boolean background, int dimensions) {
        if (stopped) throw new IllegalStateException("Live run is stopped; no further provider calls allowed");
        if (background ? documentsStarted : queriesStarted) throw new IllegalStateException("Synthetic phase already embedded; reuse cached vectors");
        if (background) documentsStarted=true; else queriesStarted=true;
        var unique=new ArrayList<>(new LinkedHashSet<>(texts)); var cache=new LinkedHashMap<String,float[]>();
        for(int i=0;i<unique.size();i+=batchSize) {
            var batch=List.copyOf(unique.subList(i,Math.min(unique.size(),i+batchSize)));
            long cost=quota.estimate(chars(batch));
            if (documents+queries>=maxRequests || tokens>maxTokens-cost) { stopped=true; throw new LiveEvaluationFailure(LiveEvaluationFailure.Kind.RUN_BUDGET); }
            try {
                pacer.await(background,chars(batch));
                // Count attempted adapter/provider requests, including a failed final request. No retries.
                if(background) { documents++; documentInputs+=batch.size(); } else { queries++; queryInputs+=batch.size(); }
                tokens+=cost;
                var vectors=background ? provider.embedBackground(batch) : provider.embed(batch);
                EmbeddingVectors.validate(vectors,batch.size(),dimensions);
                successful++;
                for(int j=0;j<batch.size();j++) cache.put(batch.get(j),vectors.get(j).clone());
            } catch(RuntimeException ex) { stopped=true; throw ex; }
        }
        return Collections.unmodifiableMap(cache);
    }
    Usage usage() { return new Usage(documents,queries,successful,documentInputs,queryInputs,tokens,documents+queries); }
    static EmbeddingClient cached(Map<String,float[]> vectors) {
        return inputs -> inputs.stream().map(s -> {
            var vector=vectors.get(s); if(vector==null) throw new IllegalStateException("Uncached synthetic input; no provider fallback");
            return vector.clone();
        }).toList();
    }
}
