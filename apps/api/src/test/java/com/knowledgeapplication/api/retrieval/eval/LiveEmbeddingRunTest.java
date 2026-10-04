package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.ai.gemini.GeminiProperties;
import com.knowledgeapplication.api.ai.quota.AiQuotaProperties;
import com.knowledgeapplication.api.knowledge.embedding.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class LiveEmbeddingRunTest {
    static AiQuotaProperties quota() { return new AiQuotaProperties(true,80,24000,800,2.5,ZoneOffset.UTC,new AiQuotaProperties.Budget(50,18000,650)); }
    static EmbeddingProperties properties(int dimensions) { return new EmbeddingProperties(true,"https://generativelanguage.googleapis.com","synthetic-test-model",dimensions,
            Duration.ofSeconds(5),Duration.ofSeconds(30),16,false,Duration.ofMinutes(1),Duration.ZERO,10,4000,200); }
    static LiveEvaluationSettings settings() { return new LiveEvaluationSettings(properties(64),new GeminiProperties("synthetic-test-key"),quota(),20,60000,180); }
    static EmbeddingClient fake(AtomicInteger calls) { return texts -> { calls.incrementAndGet(); return texts.stream().map(s -> new float[]{1}).toList(); }; }
    static LiveEmbeddingRun run(EmbeddingClient fake,int batches,long tokens) { return new LiveEmbeddingRun(fake,quota(),2,batches,tokens,(bg,chars)->{}); }
    @Test void bothGatesRequiredEvenWithAmbientKey() {
        for(var flag : Arrays.asList(null,"false","TRUE","1")) assertThatThrownBy(()->LiveEvaluationSettings.gates(true,flag)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->LiveEvaluationSettings.gates(false,"true")).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(()->LiveEvaluationSettings.gates(true,"true")).doesNotThrowAnyException();
    }
    @Test void validKeyConfigAndQuotaAreMandatoryAndRedacted() {
        var p=properties(64); var q=quota();
        assertThatThrownBy(()->new LiveEvaluationSettings(p,new GeminiProperties(""),q,20,60000,180)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new LiveEvaluationSettings(p,new GeminiProperties("synthetic-test-key"),new AiQuotaProperties(false,80,24000,800,2.5,ZoneOffset.UTC,q.background()),20,60000,180)).isInstanceOf(IllegalArgumentException.class);
        assertThat(settings().toString()).isEqualTo("LiveEvaluationSettings[redacted]");
    }
    @Test void budgetsCannotBeSilentlyExpanded() {
        assertThatThrownBy(()->new LiveEvaluationSettings(properties(64),new GeminiProperties("synthetic-test-key"),quota(),21,60000,180)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new LiveEvaluationSettings(properties(64),new GeminiProperties("synthetic-test-key"),quota(),20,60001,180)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void existingSpringBindingResolvesEnvironmentSettingsWithoutCredentialDump() {
        var env=new org.springframework.mock.env.MockEnvironment()
                .withProperty("app.embedding.enabled","true").withProperty("app.embedding.base-url","https://generativelanguage.googleapis.com")
                .withProperty("app.embedding.model","gemini-embedding-2").withProperty("app.embedding.dimensions","768")
                .withProperty("app.embedding.connect-timeout","PT5S").withProperty("app.embedding.read-timeout","PT30S")
                .withProperty("app.embedding.batch-size","16").withProperty("app.embedding.interval","PT1M")
                .withProperty("app.embedding.initial-delay","PT30S").withProperty("app.embedding.knowledge-batch-size","10")
                .withProperty("app.embedding.max-chunk-chars","4000").withProperty("app.embedding.overlap-chars","200")
                .withProperty("app.gemini.api-key","synthetic-test-key").withProperty("app.embedding.quota.enabled","true")
                .withProperty("app.embedding.quota.requests-per-minute","80").withProperty("app.embedding.quota.input-tokens-per-minute","24000")
                .withProperty("app.embedding.quota.requests-per-day","800").withProperty("app.embedding.quota.token-estimate-chars-per-token","2.5")
                .withProperty("app.embedding.quota.daily-reset-zone","America/Los_Angeles")
                .withProperty("app.embedding.quota.background.requests-per-minute","50").withProperty("app.embedding.quota.background.input-tokens-per-minute","18000")
                .withProperty("app.embedding.quota.background.requests-per-day","650");
        var settings=LiveEvaluationSettings.bind(env);
        assertThat(settings.embedding().model()).isEqualTo("gemini-embedding-2"); assertThat(settings.embedding().dimensions()).isEqualTo(768);
        assertThat(settings.maxRequests()).isEqualTo(20); assertThat(settings.maxTokens()).isEqualTo(60000);
        assertThat(settings.toString()).doesNotContain("synthetic-test-key");
        env.withProperty("app.gemini.api-key","${GEMINI_API_KEY:}").withProperty("GEMINI_API_KEY","synthetic-environment-key");
        assertThat(LiveEvaluationSettings.bind(env).credentials().apiKey()).isEqualTo("synthetic-environment-key");
    }
    @Test void evaluatorAcceptsConfiguredLiveDimensionsWithoutRelaxingOfflineFakeContract() {
        assertThatCode(()->new RetrievalEvaluation(null,new EmbeddingStrategy(properties(768),MarkdownChunker.VERSION),
                inputs->List.of(),EvaluationDatabase.OWNER,RetrievalCorpus.load(),Map.of(),Map.of())).doesNotThrowAnyException();
    }
    @Test void preflightRejectsRequestBudgetBeforeAnyCall() {
        var calls=new AtomicInteger(); var run=run(fake(calls),1,60000);
        assertThatThrownBy(()->run.preflight(List.of("doc"),List.of("query"))).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(0);
    }
    @Test void preflightRejectsTokenBudgetIncludingProductionPrefix() {
        var calls=new AtomicInteger(); var run=run(fake(calls),20,1);
        assertThatThrownBy(()->run.preflight(List.of("doc"),List.of("query"))).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(0);
    }
    @Test void duplicateInputsEmbedOnceAndCachesNeverCallProviderForVariants() {
        var calls=new AtomicInteger(); var run=run(fake(calls),20,60000);
        var docs=run.embedOnce(List.of("doc-a","doc-a","doc-b","doc-c"),true,1);
        var queries=run.embedOnce(List.of("query-a","query-a","query-b"),false,1);
        var cached=LiveEmbeddingRun.cached(queries);
        for(int cap=1;cap<=3;cap++) for(int limit : List.of(6,8,10,12)) assertThat(cached.embed(List.of("query-a"))).hasSize(1);
        assertThat(calls).hasValue(3); assertThat(docs).hasSize(3);
        assertThat(run.usage().documentRequests()).isEqualTo(2); assertThat(run.usage().queryRequests()).isEqualTo(1);
        assertThat(run.usage().documentInputs()).isEqualTo(3); assertThat(run.usage().queryInputs()).isEqualTo(2);
        var mutated=cached.embed(List.of("query-a")).get(0); mutated[0]=99;
        assertThat(cached.embed(List.of("query-a")).get(0)[0]).isEqualTo(1);
        assertThatThrownBy(()->cached.embed(List.of("uncached"))).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(3);
        assertThatThrownBy(()->run.embedOnce(List.of("query-a"),false,1)).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(3);
    }
    @Test void perBatchBudgetStopsBeforeSecondRequest() {
        var calls=new AtomicInteger(); var run=run(fake(calls),1,60000);
        assertThatThrownBy(()->run.embedOnce(List.of("a","b","c"),false,1)).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(1);
    }
    @Test void perBatchTokenBudgetStopsBeforeCall() {
        var calls=new AtomicInteger(); var run=run(fake(calls),20,1);
        assertThatThrownBy(()->run.embedOnce(List.of("a"),false,1)).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(0);
    }
    @Test void provider429StopsPermanentlyWithoutRetries() {
        var calls=new AtomicInteger(); var run=run(texts->{calls.incrementAndGet(); throw new EmbeddingQuotaUnavailableException();},20,60000);
        assertThatThrownBy(()->run.embedOnce(List.of("a","b","c"),false,1)).isInstanceOf(EmbeddingQuotaUnavailableException.class);
        assertThatThrownBy(()->run.embedOnce(List.of("d"),false,1)).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(1); assertThat(run.usage().successfulRequests()).isZero();
    }
    @Test void providerTimeoutOr5xxStopsPermanently() {
        var calls=new AtomicInteger(); var run=run(texts->{calls.incrementAndGet(); throw new EmbeddingUnavailableException();},20,60000);
        assertThatThrownBy(()->run.embedOnce(List.of("a"),false,1)).isInstanceOf(EmbeddingUnavailableException.class);
        assertThatThrownBy(()->run.embedOnce(List.of("b"),false,1)).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(1);
    }
    @Test void invalidOrMissingVectorsStopWithoutPublishingPartialCache() {
        for(var bad : List.of(List.<float[]>of(),List.of(new float[]{1,2}),List.of(new float[]{Float.NaN}),List.of(new float[]{0}))) {
            var run=run(texts->bad,20,60000);
            assertThatThrownBy(()->run.embedOnce(List.of("a"),false,1)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(()->run.embedOnce(List.of("b"),false,1)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void failedPacingMakesZeroProviderCalls() {
        var calls=new AtomicInteger(); var run=new LiveEmbeddingRun(fake(calls),quota(),2,20,60000,(bg,chars)->{throw new IllegalStateException("bounded wait");});
        assertThatThrownBy(()->run.embedOnce(List.of("a"),false,1)).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(0); assertThat(run.usage().totalRequests()).isZero();
    }
    @Test void incompleteReportContainsNoMetricsCredentialsOrFailurePayload(@TempDir Path directory) throws Exception {
        LiveRetrievalEvaluation.failure(directory,"Provider failure",settings(),null,System.nanoTime());
        var json=Files.readString(directory.resolve("live-report.json"));
        assertThat(json).contains("INCOMPLETE").doesNotContain("synthetic-test-key","liveBaseline","apiKey","authorization");
    }
    @Test void dedicatedTaskIsNotTestOrNormalBuildDependency() throws Exception {
        var gradle=Files.readString(Path.of("build.gradle"));
        assertThat(gradle).contains("tasks.register('retrievalEvalLive', JavaExec)","System.getenv('RETRIEVAL_EVAL_LIVE') != 'true'");
        assertThat(gradle).doesNotContain("dependsOn tasks.named('retrievalEvalLive')","GeminiAnswerClient");
        var main=Files.readString(Path.of("src/test/java/com/knowledgeapplication/api/retrieval/eval/LiveRetrievalMain.java"));
        assertThat(main).doesNotContain("GeminiAnswerClient","generateContent","SpringApplication","@Test");
    }
}
