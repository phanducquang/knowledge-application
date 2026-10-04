package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.ai.quota.*;
import com.knowledgeapplication.api.knowledge.embedding.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

/** Full live machinery with a capturing fake; never loads local credentials or constructs a Gemini adapter. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveRetrievalEvaluationIntegrationTest {
    private EvaluationDatabase db;
    @BeforeAll void open() { db=new EvaluationDatabase(ChunkSelectionEvaluation.extendedCorpus()); }
    @AfterAll void close() { if(db!=null) db.close(); }
    @BeforeEach void clearQuota() { db.jdbc.sql("TRUNCATE ai_quota_usage").update(); }
    @Test void fullSyntheticRunBatchesOnceAndReusesVectorsAcrossAllLocalReports(@TempDir Path directory) throws Exception {
        var calls=new AtomicInteger(); var seen=new HashSet<String>(); var offline=new OfflineEmbeddingClient();
        EmbeddingClient capturing=inputs->{
            calls.incrementAndGet(); assertThat(inputs).hasSizeLessThanOrEqualTo(16);
            for(var input : inputs) assertThat(seen.add(input)).isTrue();
            return offline.embed(inputs);
        };
        var settings=LiveEmbeddingRunTest.settings();
        var run=new LiveEmbeddingRun(capturing,settings.quota(),16,20,60000,(bg,chars)->{});
        var documents=db.documentInputs(); var queries=db.corpus.queries().stream().map(RetrievalCorpus.Query::query).toList();
        run.preflight(documents,queries);
        var vectors=new LinkedHashMap<>(run.embedOnce(documents,true,64)); vectors.putAll(run.embedOnce(queries,false,64));
        int expectedRequests=(new HashSet<>(documents).size()+15)/16+(new HashSet<>(queries).size()+15)/16;
        var live=LiveRetrievalEvaluation.measure(db,new EmbeddingStrategy(settings.embedding(),MarkdownChunker.VERSION),LiveEmbeddingRun.cached(vectors),"gemini");
        var comparison=LiveRetrievalEvaluation.measure(db,LiveRetrievalEvaluation.offlineStrategy(),offline,"offline");
        var report=LiveRetrievalEvaluation.report(settings,db,run,System.nanoTime(),live,comparison);
        LiveRetrievalEvaluation.write(report,directory);
        assertThat(calls).hasValue(expectedRequests); assertThat(run.usage().documentInputs()).isEqualTo(55); assertThat(run.usage().queryInputs()).isEqualTo(60);
        assertThat(live.variants()).hasSize(12); assertThat(comparison.variants()).hasSize(12);
        assertThat(live.baseline().configuration().provider()).isEqualTo("gemini");
        assertThat(live.baseline().semanticSearch().byLanguage()).containsKeys("en","vi","en->vi","vi->en");
        assertThat(live.variants().stream().filter(ChunkSelectionEvaluation.Result::baseline).findFirst().orElseThrow().diagnostics().byLongNotePosition())
                .containsKeys("early","middle","late");
        assertThat(live.baseline().semanticSearch().cases().stream().filter(c->c.query().negative()).toList()).allMatch(c->c.noteScore()==null);
        assertThat(Files.readString(directory.resolve("live-report.json"))).contains("LIVE GEMINI","OFFLINE SYNTHETIC").doesNotContain("synthetic-test-key","apiKey");
        assertThat(Files.readString(directory.resolve("live-report.txt"))).contains("q-playbook","q-unmapped","KEEP BASELINE");
        assertThat(live.baseline().ragContext().overall()).isEqualTo(comparison.baseline().ragContext().overall());
        assertThat(db.jdbc.sql("SELECT count(*) FROM ai_quota_usage").query(Long.class).single()).isZero();
    }
    static final class MutableClock extends Clock {
        Instant now=Instant.parse("2026-01-01T00:00:10Z");
        public ZoneId getZone() { return ZoneOffset.UTC; } public Clock withZone(ZoneId ignored) { return this; } public Instant instant() { return now; }
    }
    private AiQuotaProperties tinyQuota(long tpm,long daily) { return new AiQuotaProperties(true,1,tpm,daily,2.5,ZoneOffset.UTC,new AiQuotaProperties.Budget(1,tpm,daily)); }
    @Test void waitsOnlyForLocalMinuteThenRealAdapterReservationSemanticsStillApply() {
        var clock=new MutableClock(); var props=tinyQuota(1000,10); var sleeps=new AtomicInteger();
        var limiter=new AiQuotaLimiter(db.jdbc,new DataSourceTransactionManager(db.pool),clock);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,props,100)).isTrue();
        var pacer=new LiveQuotaPacer(db.jdbc,props,clock,60,millis->{sleeps.incrementAndGet();clock.now=clock.now.plusMillis(millis);});
        pacer.await(true,100);
        assertThat(sleeps).hasValue(1); assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,props,100)).isTrue();
        assertThat(db.jdbc.sql("SELECT request_count FROM ai_quota_usage WHERE quota_key='embedding-global-day'").query(Long.class).single()).isEqualTo(2);
        assertThat(db.jdbc.sql("SELECT request_count FROM ai_quota_usage WHERE quota_key='embedding-background-day'").query(Long.class).single()).isEqualTo(2);
    }
    @Test void dailyExhaustionDoesNotWaitOrRetry() {
        var clock=new MutableClock(); var props=tinyQuota(1000,1); var limiter=new AiQuotaLimiter(db.jdbc,new DataSourceTransactionManager(db.pool),clock);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,props,100)).isTrue();
        var pacer=new LiveQuotaPacer(db.jdbc,props,clock,60,millis->{fail("No daily quota sleep permitted");});
        assertThatThrownBy(()->pacer.await(true,100)).isInstanceOf(IllegalStateException.class).hasMessageContaining("daily quota");
    }
    @Test void singleOversizeBatchFailsBeforeReservationOrProvider() {
        var pacer=new LiveQuotaPacer(db.jdbc,tinyQuota(10,10),new MutableClock(),60,millis->{fail("No sleep for impossible batch");});
        assertThatThrownBy(()->pacer.await(true,100)).isInstanceOf(IllegalStateException.class).hasMessageContaining("single batch");
        assertThat(db.jdbc.sql("SELECT count(*) FROM ai_quota_usage").query(Long.class).single()).isZero();
    }
    @Test void totalWaitingIsBoundedAndInterruptible() {
        var clock=new MutableClock(); var props=tinyQuota(1000,10); var limiter=new AiQuotaLimiter(db.jdbc,new DataSourceTransactionManager(db.pool),clock);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,props,100)).isTrue();
        assertThatThrownBy(()->new LiveQuotaPacer(db.jdbc,props,clock,1,millis->{fail("Budget too small to sleep");}).await(true,100))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("waiting exhausted");
        try {
            assertThatThrownBy(()->new LiveQuotaPacer(db.jdbc,props,clock,60,millis->{throw new InterruptedException();}).await(true,100))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }
}
