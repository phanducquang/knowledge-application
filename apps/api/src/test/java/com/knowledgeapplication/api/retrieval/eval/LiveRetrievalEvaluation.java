package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.knowledge.embedding.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Measurement only; no production setting changes, generation clients or external fallback. */
final class LiveRetrievalEvaluation {
    record Report(int schemaVersion, String status, Instant evaluatedAt, String corpusVersion, int noteCount, int chunkCount,
            int queryCount, String provider, String model, int dimensions, int chunkerVersion, int maxChunkChars, int overlapChars,
            int batchSize, int maxRequests, long maxEstimatedInputTokens, double tokenEstimateCharsPerToken,
            LiveEmbeddingRun.Usage usage, QuotaUsage quotaReservations, long durationMillis,
            String scope, String comparisonPolicy, String decision, String nextRecommendation,
            RetrievalEvaluation.Report liveBaseline, List<ChunkSelectionEvaluation.Result> liveVariants,
            RetrievalEvaluation.Report offlineBaseline, List<ChunkSelectionEvaluation.Result> offlineVariants) {}
    record Measurement(RetrievalEvaluation.Report baseline,List<ChunkSelectionEvaluation.Result> variants) {}
    record QuotaUsage(long globalRequests, long backgroundRequests, long globalEstimatedInputTokens) {}
    static QuotaUsage quotaUsage(EvaluationDatabase db) {
        var usage=db.jdbc.sql("SELECT quota_key, sum(request_count) AS requests, sum(estimated_input_tokens) AS tokens FROM ai_quota_usage WHERE quota_key IN ('embedding-global-day','embedding-background-day') GROUP BY quota_key")
                .query((row,n)->Map.entry(row.getString("quota_key"),new long[]{row.getLong("requests"),row.getLong("tokens")})).list();
        var byKey=new HashMap<String,long[]>(); usage.forEach(e->byKey.put(e.getKey(),e.getValue()));
        var global=byKey.getOrDefault("embedding-global-day",new long[2]); var background=byKey.getOrDefault("embedding-background-day",new long[2]);
        return new QuotaUsage(global[0],background[0],global[1]);
    }
    static Measurement measure(EvaluationDatabase db,EmbeddingStrategy strategy,EmbeddingClient cached,String provider) {
        db.index(strategy,cached);
        var baseline=new RetrievalEvaluation(db.repository,strategy,cached,EvaluationDatabase.OWNER,db.corpus,db.slugs,db.chunks)
                .evaluate(Instant.now(),0,provider,provider.equals("gemini")?"Production native Gemini embeddings, symmetric-text-v1":"Controlled offline vocabulary fake",
                        provider.equals("gemini")?"Real Gemini embeddings on a small SYNTHETIC technical corpus only; not production/private Knowledge quality":"OFFLINE SYNTHETIC diagnostics, not a prediction of Gemini quality");
        var snapshots=ChunkSelectionEvaluation.snapshots(db.repository,EvaluationDatabase.OWNER,strategy,cached,db.corpus);
        var variants=new ArrayList<ChunkSelectionEvaluation.Result>();
        for(var variant : ChunkSelectionEvaluation.matrix().stream().filter(v -> v.maxChunkChars()==4000 && v.overlapChars()==200).toList()) {
            var evaluator=new RetrievalEvaluation(db.repository,strategy,cached,EvaluationDatabase.OWNER,db.corpus,db.slugs,db.chunks,variant.ragLimit(),variant.perNoteCap());
            var mode=evaluator.evaluateRag(snapshots);
            variants.add(ChunkSelectionEvaluation.diagnose(variant,strategy,mode,db.corpus,db.chunks,db.chunks,db.slugs,EvaluationDatabase.OWNER,snapshots));
        }
        var base=variants.stream().filter(ChunkSelectionEvaluation.Result::baseline).findFirst().orElseThrow();
        return new Measurement(baseline,variants.stream().map(v -> v.delta(base)).toList());
    }
    static EmbeddingStrategy offlineStrategy() {
        return new EmbeddingStrategy(new EmbeddingProperties(false,"http://127.0.0.1/offline-eval",OfflineEmbeddingClient.NAME,64,
                Duration.ofSeconds(1),Duration.ofSeconds(1),16,false,Duration.ofMinutes(1),Duration.ZERO,10,4000,200),MarkdownChunker.VERSION);
    }
    static Report report(LiveEvaluationSettings settings,EvaluationDatabase db,LiveEmbeddingRun run,long started,
            Measurement live,Measurement offline) {
        var base=live.variants().stream().filter(ChunkSelectionEvaluation.Result::baseline).findFirst().orElseThrow();
        String next=base.diagnostics().noteHitChunkMissCount()>0
                ? "Focused RAG supporting-chunk selection evaluation; review ranking/cap and context costs separately before production tuning"
                : "Separate product-feature review (auto tagging/summarization), with generation grounding still unmeasured";
        if(ChunkSelectionMetrics.recall(live.baseline().semanticSearch().overall().noteMetrics(),5)<1)
            next="Focused analysis of observed note-retrieval misses; consider separately reviewed Hybrid Retrieval Evaluation only if lexical evidence supports it";
        var p=settings.embedding();
        return new Report(1,"SUCCESS",Instant.now(),db.corpus.version(),db.corpus.notes().size(),db.chunks.values().stream().mapToInt(List::size).sum(),
                db.corpus.queries().size(),"gemini",p.model(),p.dimensions(),MarkdownChunker.VERSION,4000,200,p.batchSize(),settings.maxRequests(),settings.maxTokens(),
                settings.quota().tokenEstimateCharsPerToken(),run.usage(),quotaUsage(db),(System.nanoTime()-started)/1_000_000,"Real Gemini embeddings evaluated ONLY against a small synthetic technical corpus; no generation or private data",
                "OFFLINE SYNTHETIC and LIVE GEMINI use identical fixtures/chunks/truth/SQL/K. Compare ranks/coverage, NEVER absolute distance scales. @8 censored for limit6.",
                "KEEP BASELINE; no production defaults, model, dimensions, quota or ranking changed",next,live.baseline(),live.variants(),offline.baseline(),offline.variants());
    }
    static void write(Report report,Path directory) throws java.io.IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("live-report.json"),RetrievalReportWriter.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        var out=new StringBuilder("MANUAL LIVE GEMINI RETRIEVAL EVALUATION — SYNTHETIC ONLY\n");
        out.append("status=").append(report.status()).append(" model=").append(report.model()).append(" dimensions=").append(report.dimensions())
                .append(" notes=").append(report.noteCount()).append(" chunks=").append(report.chunkCount()).append(" queries=").append(report.queryCount())
                .append(" chunking=4000/200 version=").append(report.chunkerVersion()).append(" batchSize=").append(report.batchSize()).append('\n')
                .append("Requests/inputs/tokens: ").append(report.usage()).append(" durationMillis=").append(report.durationMillis()).append('\n')
                .append(report.scope()).append('\n').append(report.comparisonPolicy()).append('\n').append(report.decision()).append('\n')
                .append("Next recommendation: ").append(report.nextRecommendation()).append('\n');
        for(var labeled : List.of(Map.entry("LIVE GEMINI",report.liveBaseline()),Map.entry("OFFLINE SYNTHETIC",report.offlineBaseline()))) {
            out.append('\n').append(labeled.getKey()).append('\n');
            RetrievalReportWriter.mode(out,"SEMANTIC SEARCH",labeled.getValue().semanticSearch());
            RetrievalReportWriter.mode(out,"RAG BASELINE limit8/cap2",labeled.getValue().ragContext());
        }
        for(var labeled : List.of(Map.entry("LIVE GEMINI",report.liveVariants()),Map.entry("OFFLINE SYNTHETIC",report.offlineVariants()))) {
            out.append('\n').append(labeled.getKey()).append(" RETRIEVAL-ONLY VARIANTS (no additional Gemini calls)\n");
            for(var r : labeled.getValue()) {
                out.append(r.name()).append("\nmetrics=").append(r.metrics()).append("\ndiagnostics=").append(r.diagnostics()).append("\ndelta=").append(r.deltaFromBaseline()).append('\n');
                for(var c : r.caseDiagnostics()) out.append(c).append('\n');
            }
        }
        Files.writeString(directory.resolve("live-report.txt"),out);
    }
    record Failure(String status, String reason, String model, Integer dimensions, LiveEmbeddingRun.Usage usage, long durationMillis) {}
    static void failure(Path directory,String reason,LiveEvaluationSettings settings,LiveEmbeddingRun run,long started) throws java.io.IOException {
        Files.createDirectories(directory);
        var failure=new Failure("INCOMPLETE",reason,settings==null?null:settings.embedding().model(),settings==null?null:settings.embedding().dimensions(),
                run==null?null:run.usage(),(System.nanoTime()-started)/1_000_000);
        // Replace any prior successful LIVE report so a failed run cannot leave misleading fresh-looking metrics.
        Files.writeString(directory.resolve("live-report.json"),RetrievalReportWriter.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(failure));
        Files.writeString(directory.resolve("live-report.txt"),"INCOMPLETE manual live evaluation: "+reason+"\nNo quality metrics published; no provider retries.\n");
    }
}
