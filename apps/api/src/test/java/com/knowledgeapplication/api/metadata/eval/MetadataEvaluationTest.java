package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.ai.gemini.GeminiProperties;
import com.knowledgeapplication.api.ai.quota.AiQuotaProperties;
import com.knowledgeapplication.api.metadata.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class MetadataEvaluationTest {
    final MetadataCorpus corpus=MetadataCorpus.load();
    final AiQuotaProperties quota=new AiQuotaProperties(true,10,200000,400,2.5,ZoneId.of("America/Los_Angeles"),null);
    MetadataEvaluation.Identity identity(String model) { return MetadataEvaluation.Identity.create(corpus,"offline-fake",model,32000,600,Instant.EPOCH); }
    MetadataEvaluation.Report run(KnowledgeMetadataSuggestionClient client) { return MetadataEvaluation.run(corpus,client,identity("fake-v1"),quota,c->{},false,20,150000); }
    MetadataCorpus.Fixture fixture(String id) { return corpus.fixtures().stream().filter(f->f.id().equals(id)).findFirst().orElseThrow(); }
    MetadataMetrics.Result score(String id,String summary,List<String> tags) { return MetadataMetrics.score(fixture(id),new MetadataSuggestion(summary,tags).validated(fixture(id).existingTags()),corpus.genericTags(),32000); }
    MetadataCorpus changed(MetadataCorpus.Fixture fixture) { return new MetadataCorpus(corpus.version(),corpus.genericTags(),List.of(fixture)); }
    MetadataCorpus.Fixture copy(MetadataCorpus.Fixture f,List<MetadataCorpus.Tag> tags,List<MetadataCorpus.Concept> concepts) {
        return new MetadataCorpus.Fixture(f.id(),f.title(),f.summary(),f.content(),f.lateContent(),f.paddingBeforeLate(),f.paddingAfter(),f.existingTags(),f.language(),f.category(),f.position(),tags,concepts,f.forbiddenClaims(),f.forbiddenTags(),f.notes());
    }
    @Test void curatedFixtureCoverageAndAllEvidenceValid() {
        corpus.validate(); assertThat(corpus.fixtures()).hasSize(20);
        assertThat(corpus.fixtures().stream().map(MetadataCorpus.Fixture::language).distinct()).containsExactlyInAnyOrder("en","vi","mixed-technical");
        assertThat(corpus.fixtures().stream().map(MetadataCorpus.Fixture::category).distinct()).hasSize(16);
        assertThat(corpus.fixtures().stream().map(MetadataCorpus.Fixture::lengthGroup).distinct()).containsExactlyInAnyOrder("short","medium","long");
        assertThat(corpus.fixtures().stream().filter(f->f.lengthGroup().equals("long"))).hasSize(4);
    }
    @Test void duplicateFixtureIdsRejected() {
        var f=corpus.fixtures().get(0);
        assertThatThrownBy(()->new MetadataCorpus(corpus.version(),corpus.genericTags(),List.of(f,f)).validate()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingExpectedTagsRejected() { var f=corpus.fixtures().get(0); assertThatThrownBy(()->changed(copy(f,List.of(),f.concepts())).validate()).isInstanceOf(IllegalArgumentException.class); }
    @Test void ambiguousAliasesRejected() {
        var f=fixture("webclient-vi");
        assertThatThrownBy(()->changed(copy(f,List.of(new MetadataCorpus.Tag("WebClient",List.of("Timeout"),"WebClient"),new MetadataCorpus.Tag("Timeout",List.of(),"timeout")),f.concepts())).validate()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void blankAliasRejected() {
        var f=fixture("webclient-vi"); assertThatThrownBy(()->changed(copy(f,List.of(new MetadataCorpus.Tag("WebClient",List.of(" "),"WebClient")),f.concepts())).validate()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void absentEvidenceRejected() {
        var f=fixture("webclient-vi"); assertThatThrownBy(()->changed(copy(f,List.of(new MetadataCorpus.Tag("WebClient",List.of(),"absent-evidence")),f.concepts())).validate()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void invalidConceptAndDuplicateConceptRejected() {
        var f=fixture("webclient-vi");
        assertThatThrownBy(()->changed(copy(f,f.expectedTags(),List.of(new MetadataCorpus.Concept("bad",List.of(),"WebClient")))).validate()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->changed(copy(f,f.expectedTags(),List.of(f.concepts().get(0),f.concepts().get(0)))).validate()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void transparentPrecisionRecallF1Math() {
        var m=score("webclient-vi","connect timeout and responseTimeout",List.of("WebClient","Timeout","Java","Software"));
        assertThat(m.precision()).isEqualTo(.5); assertThat(m.recall()).isCloseTo(2.0/3,within(1e-12)); assertThat(m.f1()).isCloseTo(4.0/7,within(1e-12));
        assertThat(m.requiredConceptCoverage()).isCloseTo(2.0/3,within(1e-12)); assertThat(m.genericTagRate()).isEqualTo(.25);
    }
    @Test void zeroTagsAndZeroHitsProduceZeroF1NotNaN() {
        var m=score("webclient-vi","No declared concepts",List.of());
        assertThat(m.precision()).isZero(); assertThat(m.recall()).isZero(); assertThat(m.f1()).isZero(); assertThat(m.genericTagRate()).isZero();
    }
    @Test void explicitUnicodeAliasNormalizationNotFuzzy() {
        var m=score("postgres-index","chỉ mục ghép owner_id EXPLAIN ANALYZE",List.of(" Ｃｏｍｐｏｓｉｔｅ Ｉｎｄｅｘ ","Query Plan","Index"));
        assertThat(m.precision()).isCloseTo(2.0/3,within(1e-12)); assertThat(m.recall()).isEqualTo(1);
        assertThat(MetadataCorpus.normalize("Straße")).isEqualTo("strasse");
    }
    @Test void synonymousSuggestionsCannotInflateRecovery() {
        var m=score("pgvector","cosine",List.of("Cosine Similarity","Cosine Distance"));
        assertThat(m.observedTags()).containsExactly("Cosine Similarity"); assertThat(m.precision()).isEqualTo(.5);
    }
    @Test void forbiddenClaimsAndTagsAreDiagnostics() {
        var m=score("webclient-vi","automatic retry for response timeout",List.of("Kafka","Software"));
        assertThat(m.forbiddenClaimCount()).isEqualTo(1); assertThat(m.forbiddenTagCount()).isEqualTo(1); assertThat(m.genericTagRate()).isEqualTo(.5);
        assertThat(m.flaggedClaims()).containsExactly("automatic retry");
    }
    @Test void existingTagsRemovedFromNewRecallDenominator() {
        var m=score("spring-health","health endpoint readiness private",List.of("Spring Boot","Actuator","Readiness"));
        assertThat(m.expectedNewTags()).containsExactly("Actuator","Readiness Probe"); assertThat(m.recall()).isEqualTo(1); assertThat(m.precision()).isEqualTo(1);
    }
    @Test void lateEvidenceNARatherThanProviderFailureAndFullCoverageZero() {
        var m=score("long-late","Synthetic checklist",List.of("Software"));
        assertThat(m.visibleRecall()).isNull(); assertThat(m.visibleConceptCoverage()).isNull(); assertThat(m.expectedCoverageCeiling()).isZero();
        assertThat(m.invisibleConcepts()).hasSize(3); assertThat(m.invisibleExpectedTags()).hasSize(3); assertThat(m.requiredConceptCoverage()).isZero();
    }
    @Test void mixedEarlyLateSeparatesVisibleAndFull() {
        var m=score("long-early-late","response timeout 3 giây",List.of("Timeout"));
        assertThat(m.visibleConceptCoverage()).isEqualTo(1); assertThat(m.requiredConceptCoverage()).isCloseTo(1.0/3,within(1e-12));
        assertThat(m.visibleRecall()).isEqualTo(1); assertThat(m.recall()).isCloseTo(1.0/3,within(1e-12));
        assertThat(m.invisibleExpectedTags()).containsExactly("Circuit Breaker","Resilience4j");
    }
    @Test void earlyAndMiddleEvidenceStillVisible() {
        for(var id:List.of("long-early","long-middle")) {
            var f=fixture(id); var m=MetadataMetrics.score(f,new MetadataSuggestion("unmatched",List.of()),corpus.genericTags(),32000);
            assertThat(m.expectedCoverageCeiling()).isEqualTo(1); assertThat(m.invisibleConcepts()).isEmpty();
            assertThat(f.input(32000).content()).hasSize(32000); assertThat(f.input(32000).contentTruncated()).isTrue();
        }
    }
    @Test void productionPureBuilderPreservesSortingTruncationAndSurrogateBoundary() {
        var request=MetadataSuggestionInputBuilder.build("Title",null,"a".repeat(31999)+"😀tail",List.of("Z","a"),32000);
        assertThat(request.content()).hasSize(31999); assertThat(request.currentTags()).containsExactly("a","Z"); assertThat(request.contentTruncated()).isTrue();
        assertThatThrownBy(()->MetadataSuggestionInputBuilder.build("x",null,"x",List.of(),32001)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void identityRecordsConfiguredModelAndImmutableContracts() {
        var id=identity("candidate-model"); assertThat(id.model()).isEqualTo("candidate-model"); assertThat(id.sdkVersion()).isEqualTo("1.75.0");
        assertThat(id.promptVersion()).isEqualTo("metadata-prompt-v1"); assertThat(id.schemaVersion()).isEqualTo("metadata-schema-v1");
        assertThat(id.inputStrategyVersion()).isEqualTo("prefix-32000-v1"); assertThat(id.corpusVersion()).isEqualTo("metadata-eval-v1");
    }
    @Test void stableSerializationReportRubricGroupsAndFakeCallCounts(@TempDir Path directory) throws Exception {
        var calls=new AtomicInteger(); var fake=MetadataEvaluation.fake(corpus,32000);
        var a=run(r->{calls.incrementAndGet();return fake.suggest(r);}); var b=run(MetadataEvaluation.fake(corpus,32000));
        assertThat(calls.get()).isEqualTo(20); assertThat(a.usage().fakeCalls()).isEqualTo(20); assertThat(a.usage().providerRequests()).isZero();
        assertThat(MetadataReportWriter.json(a)).isEqualTo(MetadataReportWriter.json(b));
        assertThat(a.groups()).containsKeys("language","category","length","position"); assertThat(a.metrics().forbiddenClaimCount()).isEqualTo(1);
        assertThat(MetadataReportWriter.review(a)).contains("factuality ___","language fit ___","specificity ___","NOT factuality/entailment proof","invisibleConcepts");
        MetadataReportWriter.write(a,directory,false); assertThat(directory.resolve("report.json")).exists(); assertThat(directory.resolve("report.txt")).exists();
    }
    @Test void modelComparisonKeepsDimensionsSeparate() {
        var a=run(MetadataEvaluation.fake(corpus,32000));
        var b=MetadataEvaluation.run(corpus,MetadataEvaluation.fake(corpus,32000),identity("candidate"),quota,c->{},false,20,150000);
        assertThat(MetadataEvaluation.compare(a,b).values()).containsOnly(0.0);
        var incompatible=new MetadataEvaluation.Report(new MetadataEvaluation.Identity("x","x","x","x","gemini","x","1.75.0","x","x","x","x","x",32000,500,5,50,600,"x"),a.status(),null,a.usage(),a.metrics(),a.groups(),a.cases());
        assertThatThrownBy(()->MetadataEvaluation.compare(a,incompatible)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void liveGatesExactFlagAndDedicatedTask() {
        assertThatCode(()->MetadataLiveSettings.gates(true,"true")).doesNotThrowAnyException();
        for(var flag:Arrays.asList(null,"false","TRUE","1")) assertThatThrownBy(()->MetadataLiveSettings.gates(true,flag)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->MetadataLiveSettings.gates(false,"true")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void liveSettingsCannotWeakenSafetyOrChangeProviderOrigin() {
        var props=new MetadataProperties(true,"configured-model","https://generativelanguage.googleapis.com",Duration.ofSeconds(5),Duration.ofSeconds(30),600,32000,quota);
        var s=new MetadataLiveSettings(props,new GeminiProperties("fake-test-key"),20,150000,180); assertThat(s.toString()).isEqualTo("MetadataLiveSettings[redacted]");
        assertThatThrownBy(()->new MetadataLiveSettings(props,new GeminiProperties("fake-test-key"),21,150000,180)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MetadataLiveSettings(props,new GeminiProperties("fake-test-key"),20,150001,180)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MetadataLiveSettings(props,new GeminiProperties(""),20,150000,180)).isInstanceOf(IllegalArgumentException.class);
        var other=new MetadataProperties(true,"x","https://example.invalid",Duration.ofSeconds(5),Duration.ofSeconds(30),600,32000,quota);
        assertThatThrownBy(()->new MetadataLiveSettings(other,new GeminiProperties("fake-test-key"),20,150000,180)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void budgetPreflightBeforeAnyFakeOrProviderCalls() {
        var calls=new AtomicInteger(); KnowledgeMetadataSuggestionClient client=r->{calls.incrementAndGet();throw new AssertionError("must not call");};
        assertThatThrownBy(()->MetadataEvaluation.run(corpus,client,identity("x"),quota,c->{},true,19,150000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->MetadataEvaluation.run(corpus,client,identity("x"),quota,c->{},true,20,1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls.get()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"429 raw provider body","500 secret body","timeout raw secret detail","malformed structured output"})
    void providerFailureStopsImmediatelyWithoutRetryOrLeaking(String failure) {
        var calls=new AtomicInteger(); var fake=MetadataEvaluation.fake(corpus,32000);
        var report=run(r->{if(calls.incrementAndGet()==3) throw new IllegalStateException(failure); return fake.suggest(r);});
        assertThat(calls.get()).isEqualTo(3); assertThat(report.status()).isEqualTo("INCOMPLETE"); assertThat(report.metrics()).isNull();
        assertThat(report.usage().validOutputs()).isEqualTo(2); assertThat(report.usage().validStructuredOutputRate()).isCloseTo(2.0/3,within(1e-12));
        assertThat(MetadataReportWriter.json(report)).doesNotContain(failure);
    }
    @Test void invalidOutputStopsAndIsNotSerialized() {
        var report=run(r->new MetadataSuggestion("x".repeat(501),List.of())); assertThat(report.status()).isEqualTo("INCOMPLETE");
        assertThat(report.cases()).hasSize(1); assertThat(report.cases().get(0).suggestion()).isNull(); assertThat(report.usage().validOutputs()).isZero();
    }
    @Test void quotaPacingFailureMakesZeroCalls() {
        var calls=new AtomicInteger();
        var report=MetadataEvaluation.run(corpus,r->{calls.incrementAndGet();throw new AssertionError();},identity("fake"),quota,c->{throw new IllegalStateException();},true,20,150000);
        assertThat(calls.get()).isZero(); assertThat(report.usage().attemptedCases()).isZero(); assertThat(report.status()).isEqualTo("INCOMPLETE");
    }
    @Test void nearestRankPercentilesAreExplicit() {
        assertThat(MetadataEvaluation.percentile(List.of(5.0,1.0,3.0,2.0,4.0),.5)).isEqualTo(3);
        assertThat(MetadataEvaluation.percentile(List.of(5.0,1.0,3.0,2.0,4.0),.95)).isEqualTo(5);
    }
    @Test void publicContractFingerprintsAreStableAndCorpusChangesCannotHideBehindVersion() {
        assertThat(corpus.fingerprint()).hasSize(64).isEqualTo(MetadataCorpus.load().fingerprint());
        assertThat(com.knowledgeapplication.api.ai.gemini.GeminiKnowledgeMetadataSuggestionClient.promptFingerprint()).hasSize(64);
        assertThat(com.knowledgeapplication.api.ai.gemini.GeminiKnowledgeMetadataSuggestionClient.schemaFingerprint()).hasSize(64);
        var f=fixture("webclient-vi");
        assertThat(changed(copy(f,f.expectedTags(),List.of(f.concepts().get(0)))).fingerprint()).isNotEqualTo(corpus.fingerprint());
    }
}
