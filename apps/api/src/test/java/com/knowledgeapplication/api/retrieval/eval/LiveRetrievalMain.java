package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.ai.gemini.GeminiEmbeddingClient;
import com.knowledgeapplication.api.ai.quota.AiQuotaLimiter;
import com.knowledgeapplication.api.knowledge.embedding.*;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Only entry point that can construct the real adapter. NOT a JUnit test; never included in normal test/build/eval. */
public final class LiveRetrievalMain {
    private LiveRetrievalMain() {}
    public static void main(String[] args) {
        var root=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.WARN);
        for(var name : new String[]{"com.google","okhttp3","org.springframework.core.env"})
            ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(name)).setLevel(ch.qos.logback.classic.Level.OFF);
        long started=System.nanoTime(); LiveEvaluationSettings settings=null; LiveEmbeddingRun run=null;
        var directory=Path.of(System.getProperty("retrieval.eval.report-directory","build/reports/retrieval-eval"));
        try {
            settings=LiveEvaluationSettings.load();
            var corpus=ChunkSelectionEvaluation.extendedCorpus();
            System.out.println("Manual synthetic embedding evaluation: model="+settings.embedding().model()+" dimensions="+settings.embedding().dimensions()
                    +" baseline=4000/200 notes="+corpus.notes().size()+" queries="+corpus.queries().size()+" requestBudget="+settings.maxRequests()+" estimatedTokenBudget="+settings.maxTokens());
            try(var db=new EvaluationDatabase(corpus)) {
                var clock=Clock.systemUTC();
                var limiter=new AiQuotaLimiter(db.jdbc,new DataSourceTransactionManager(db.pool),clock);
                var pacer=new LiveQuotaPacer(db.jdbc,settings.quota(),clock,settings.maxWaitSeconds(),Thread::sleep);
                try(var provider=new GeminiEmbeddingClient(settings.embedding(),settings.credentials(),limiter,settings.quota())) {
                    run=new LiveEmbeddingRun(provider,settings.quota(),settings.embedding().batchSize(),settings.maxRequests(),settings.maxTokens(),pacer);
                    var documents=db.documentInputs(); var queries=corpus.queries().stream().map(RetrievalCorpus.Query::query).toList();
                    run.preflight(documents,queries);
                    var documentVectors=run.embedOnce(documents,true,settings.embedding().dimensions());
                    var queryVectors=run.embedOnce(queries,false,settings.embedding().dimensions());
                    var reservations=LiveRetrievalEvaluation.quotaUsage(db);
                    if(reservations.globalRequests()!=run.usage().totalRequests() || reservations.backgroundRequests()!=run.usage().documentRequests()
                            || reservations.globalEstimatedInputTokens()!=run.usage().estimatedInputTokens())
                        throw new IllegalStateException("Persistent isolated quota accounting mismatch");
                    var strategy=new EmbeddingStrategy(settings.embedding(),MarkdownChunker.VERSION);
                    var all=new java.util.LinkedHashMap<String,float[]>(documentVectors); all.putAll(queryVectors);
                    var live=LiveRetrievalEvaluation.measure(db,strategy,LiveEmbeddingRun.cached(all),"gemini");
                    var offline=LiveRetrievalEvaluation.measure(db,LiveRetrievalEvaluation.offlineStrategy(),new OfflineEmbeddingClient(),"offline");
                    var report=LiveRetrievalEvaluation.report(settings,db,run,started,live,offline);
                    LiveRetrievalEvaluation.write(report,directory);
                    System.out.println("SUCCESS synthetic live retrieval; "+run.usage()+"; production defaults unchanged; no generation calls.");
                }
            }
        } catch(Exception ex) {
            String reason=ex instanceof LiveEvaluationFailure ? ex.getMessage()
                    : ex instanceof EmbeddingQuotaUnavailableException ? "Provider quota/rate boundary (429 or final quota reservation denied); live evaluation incomplete"
                    : ex instanceof EmbeddingUnavailableException ? "Provider failure or invalid embedding response; live evaluation incomplete"
                    : "Configuration, safety budget, interruption or isolated evaluation failure; live evaluation incomplete";
            // Never expose exception messages/causes, Binder values, HTTP bodies, credentials or config dumps.
            System.err.println(reason+"; stopped without retries or quality metrics.");
            try { LiveRetrievalEvaluation.failure(directory,reason,settings,run,started); }
            catch(Exception ignored) { System.err.println("Could not write the sanitized incomplete report."); }
            System.exit(1);
        }
    }
}
