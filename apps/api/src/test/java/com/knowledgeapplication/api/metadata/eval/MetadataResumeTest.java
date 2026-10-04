package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.ai.quota.AiQuotaProperties;
import com.knowledgeapplication.api.metadata.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.*;
import static org.assertj.core.api.Assertions.*;

class MetadataResumeTest {
    final MetadataCorpus corpus=MetadataCorpus.load();
    final AiQuotaProperties quota=new AiQuotaProperties(true,10,200000,400,2.5,ZoneOffset.UTC,null);
    final JsonMapper json=JsonMapper.builder().build();
    final MetadataEvaluation.Identity identity=MetadataEvaluation.Identity.create(corpus,"gemini","gemini-3.5-flash-lite",32000,600,Instant.EPOCH);
    MetadataEvaluation.Report fullFake() { return MetadataEvaluation.run(corpus,MetadataEvaluation.fake(corpus,32000),identity,quota,c->{},false,20,150000); }
    ObjectNode prior(boolean legacy) {
        var root=(ObjectNode)json.readTree(MetadataReportWriter.json(fullFake()));
        root.put("status","INCOMPLETE"); root.putNull("metrics");
        var cases=(ArrayNode)root.path("cases"); cases.remove(19); cases.remove(18);
        var failed=(ObjectNode)cases.get(17); failed.put("status","FAILED"); failed.putNull("suggestion"); failed.putNull("metrics");
        if(legacy) {
            ((ObjectNode)root.path("identity")).put("reportVersion","metadata-report-v1"); root.remove("runId"); root.remove("composite");
            root.put("failure","untrusted secret provider text MUST NOT be retained");
            for(var c:cases) { var obj=(ObjectNode)c; for(var key:List.of("provenance","sourceReportFingerprint","sourceEvaluatedAt","generationRunId","inputFingerprint")) obj.remove(key); }
        }
        ((ObjectNode)cases.get(0).path("metrics")).put("requiredConceptCoverage",-999);
        return root;
    }
    MetadataResume.Plan plan(ObjectNode report) { return MetadataResume.parse(json.writeValueAsString(report),corpus,identity); }
    KnowledgeMetadataSuggestionClient outstanding(AtomicInteger calls) {
        var full=fullFake();
        return input -> {
            calls.incrementAndGet();
            var index=corpus.fixtures().stream().filter(f->f.input(32000).equals(input)).findFirst().orElseThrow();
            assertThat(corpus.fixtures().indexOf(index)).isGreaterThanOrEqualTo(17);
            return full.cases().stream().filter(c->c.id().equals(index.id())).findFirst().orElseThrow().suggestion();
        };
    }
    @Test void seventeenValidPlusFailedAndAbsentRequiresExactlyThreeCallsAndRescores() {
        var plan=plan(prior(false)); var calls=new AtomicInteger(); var waits=new AtomicInteger();
        long budget=MetadataEvaluation.preflight(corpus,32000,quota,3,150000,plan.reused().keySet());
        var report=MetadataEvaluation.run(corpus,outstanding(calls),identity,quota,c->waits.incrementAndGet(),false,3,budget,plan);
        assertThat(plan.reused()).hasSize(17); assertThat(calls.get()).isEqualTo(3); assertThat(waits.get()).isEqualTo(3);
        assertThat(report.status()).isEqualTo("COMPLETE"); assertThat(report.cases()).hasSize(20); assertThat(report.composite()).isTrue();
        assertThat(report.metrics()).isEqualTo(fullFake().metrics()); assertThat(report.cases().get(0).metrics().requiredConceptCoverage()).isEqualTo(1);
        assertThat(report.usage().reusedValidCases()).isEqualTo(17); assertThat(report.usage().newCallsPlanned()).isEqualTo(3);
        assertThat(report.usage().newValidOutputs()).isEqualTo(3); assertThat(report.usage().attemptedEstimatedInputTokens()).isEqualTo(budget);
        assertThat(report.usage().corpusEstimatedInputTokens()).isEqualTo(fullFake().usage().corpusEstimatedInputTokens());
        assertThat(report.usage().reusedEstimatedHistoricalTokens()+budget).isEqualTo(report.usage().corpusEstimatedInputTokens());
    }
    @Test void existingV1OutputsResumeWithoutMigrationOrReGeneration() {
        var plan=plan(prior(true)); var calls=new AtomicInteger();
        var report=MetadataEvaluation.run(corpus,outstanding(calls),identity,quota,c->{},false,3,150000,plan);
        assertThat(calls.get()).isEqualTo(3); assertThat(report.status()).isEqualTo("COMPLETE");
        assertThat(report.cases().get(0).provenance()).isEqualTo("REUSED_FROM_PRIOR_RUN");
        assertThat(report.cases().get(0).sourceReportFingerprint()).hasSize(64);
        assertThat(report.cases().get(0).sourceEvaluatedAt()).isEqualTo(Instant.EPOCH.toString());
        assertThat(MetadataReportWriter.json(report)).doesNotContain("untrusted secret provider text");
    }
    @ParameterizedTest @ValueSource(strings={"model","provider","sdkVersion","promptVersion","promptFingerprint","schemaVersion","schemaFingerprint","inputStrategyVersion","corpusVersion","corpusFingerprint","contentLimit","summaryLimit","tagCountLimit","tagLengthLimit","maxOutputTokens"})
    void identityMismatchRejectsBeforeAnyCall(String field) {
        var previous=prior(true); var node=(ObjectNode)previous.path("identity");
        if(node.path(field).isNumber()) node.put(field,node.path(field).asInt()+1); else node.put(field,"incompatible");
        var calls=new AtomicInteger();
        assertThatThrownBy(()->{ var p=plan(previous); MetadataEvaluation.run(corpus,outstanding(calls),identity,quota,c->{},false,20,150000,p); })
                .isInstanceOf(MetadataUnavailableException.class).hasNoCause();
        assertThat(calls.get()).isZero();
    }
    @Test void oldMetricsVersionDoesNotPreventRescoringCompatibleGeneration() {
        var previous=prior(true); ((ObjectNode)previous.path("identity")).put("metricsVersion","old-scorer"); assertThat(plan(previous).reused()).hasSize(17);
    }
    @Test void failedMissingMalformedUnrecognizedStatusesCannotBeReused() {
        var previous=prior(true); var cases=(ArrayNode)previous.path("cases");
        ((ObjectNode)cases.get(0)).put("status","SURPRISE");
        ((ObjectNode)cases.get(1)).putNull("suggestion");
        ((ObjectNode)cases.get(2).path("suggestion")).put("summary"," ");
        ((ObjectNode)cases.get(3).path("suggestion")).put("summary","x".repeat(501));
        assertThat(plan(previous).reused()).hasSize(13);
    }
    @Test void currentFixtureOrInputIdentityCannotBeSwapped() {
        var previous=prior(true); ((ObjectNode)previous.path("cases").get(0)).put("title","other title");
        assertThatThrownBy(()->plan(previous)).isInstanceOf(MetadataUnavailableException.class);
        var invalid=prior(false); ((ObjectNode)invalid.path("cases").get(0)).put("inputFingerprint","0".repeat(64));
        assertThatThrownBy(()->plan(invalid)).isInstanceOf(MetadataUnavailableException.class);
    }
    @Test void duplicateOrUnknownCaseIdsRejected() {
        var duplicate=prior(true); ((ObjectNode)duplicate.path("cases").get(1)).put("id","spring-health");
        assertThatThrownBy(()->plan(duplicate)).isInstanceOf(MetadataUnavailableException.class);
        var unknown=prior(true); ((ObjectNode)unknown.path("cases").get(0)).put("id","unknown-id");
        assertThatThrownBy(()->plan(unknown)).isInstanceOf(MetadataUnavailableException.class);
    }
    @Test void completeReuseConsumesZeroPacingQuotaAndCalls() {
        var plan=MetadataResume.parse(MetadataReportWriter.json(fullFake()),corpus,identity);
        var report=MetadataEvaluation.run(corpus,r->{throw new AssertionError("no provider");},identity,quota,c->{throw new AssertionError("no pacing or reservation");},false,0,1,plan);
        assertThat(report.status()).isEqualTo("COMPLETE"); assertThat(report.usage().newCallsPlanned()).isZero(); assertThat(report.usage().providerRequests()).isZero();
        assertThat(report.usage().plannedEstimatedInputTokens()).isZero(); assertThat(report.usage().attemptedEstimatedInputTokens()).isZero();
    }
    @Test void resumedFailurePreservesAllReusedOutputsAndNextResumeOnlyMissing() {
        var initial=plan(prior(true)); var calls=new AtomicInteger(); var output=outstanding(new AtomicInteger());
        var failed=MetadataEvaluation.run(corpus,r->{if(calls.incrementAndGet()==2) throw new MetadataUnavailableException(MetadataFailureCategory.PROVIDER_RATE_LIMIT,429);return output.suggest(r);},identity,quota,c->{},false,3,150000,initial);
        assertThat(failed.status()).isEqualTo("INCOMPLETE"); assertThat(failed.metrics()).isNull(); assertThat(failed.groups()).isEmpty();
        assertThat(failed.usage().validOutputs()).isEqualTo(18); assertThat(failed.failure().category()).isEqualTo(MetadataFailureCategory.PROVIDER_RATE_LIMIT);
        assertThat(failed.failure().caseId()).isEqualTo("long-late"); assertThat(failed.failure().attemptedCaseNumber()).isEqualTo(2);
        var next=MetadataResume.parse(MetadataReportWriter.json(failed),corpus,identity); assertThat(next.reused()).hasSize(18);
        var second=new AtomicInteger(); var done=MetadataEvaluation.run(corpus,outstanding(second),identity,quota,c->{},false,2,150000,next);
        assertThat(second.get()).isEqualTo(2); assertThat(done.status()).isEqualTo("COMPLETE");
        assertThat(done.cases().get(0).generationRunId()).isEqualTo(failed.cases().get(0).generationRunId());
        assertThat(done.cases().get(0).sourceEvaluatedAt()).isEqualTo(failed.cases().get(0).sourceEvaluatedAt());
    }
    @Test void newRequestAndTokenBudgetsFailBeforeCalling() {
        var plan=plan(prior(true)); var calls=new AtomicInteger();
        assertThatThrownBy(()->MetadataEvaluation.run(corpus,outstanding(calls),identity,quota,c->{},false,2,150000,plan)).isInstanceOf(MetadataUnavailableException.class);
        assertThatThrownBy(()->MetadataEvaluation.run(corpus,outstanding(calls),identity,quota,c->{},false,3,1,plan)).isInstanceOf(MetadataUnavailableException.class);
        assertThat(calls.get()).isZero();
    }
    @Test void reportsNeverOverwritePriorLiveOutputAndShowBlankReviewProvenance(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("live-report.json"),"original synthetic marker");
        var report=MetadataEvaluation.run(corpus,outstanding(new AtomicInteger()),identity,quota,c->{},false,3,150000,plan(prior(true)));
        MetadataReportWriter.write(report,dir,true);
        assertThat(Files.readString(dir.resolve("live-report.json"))).isEqualTo("original synthetic marker");
        var review=Files.readString(dir.resolve("live-runs").resolve(report.runId()).resolve("live-review.md"));
        assertThat(review).contains("resumed/composite","REUSED_FROM_PRIOR_RUN","GENERATED_THIS_RUN","factuality ___");
        assertThatThrownBy(()->MetadataReportWriter.write(report,dir,true)).isInstanceOf(FileAlreadyExistsException.class);
    }
    @Test void oversizedBrokenReportsAndDuplicateJsonFieldsRejectedBeforeReuse(@TempDir Path dir) throws Exception {
        assertThatThrownBy(()->MetadataResume.parse("not json",corpus,identity)).isInstanceOf(MetadataUnavailableException.class).hasNoCause();
        assertThatThrownBy(()->MetadataResume.parse("{} {}",corpus,identity)).isInstanceOf(MetadataUnavailableException.class);
        assertThatThrownBy(()->MetadataResume.parse("{\"identity\":{},\"identity\":{}}",corpus,identity)).isInstanceOf(MetadataUnavailableException.class);
        var file=dir.resolve("oversized.json"); Files.writeString(file,"x".repeat(2_000_001));
        assertThatThrownBy(()->MetadataResume.load(file,corpus,identity)).isInstanceOf(MetadataUnavailableException.class);
    }
    @Test void checkedInV1FixtureIsSyntheticAndCompatible() throws Exception {
        try(var stream=getClass().getResourceAsStream("/metadata-eval/resume-v1.json")) {
            assertThat(stream).isNotNull(); var plan=MetadataResume.parse(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),corpus,identity);
            assertThat(plan.reused()).containsOnlyKeys("spring-health");
            assertThat(plan.reused().get("spring-health").suggestion().summary()).isEqualTo("health endpoint; readiness; private; automatic restart");
        }
    }
}
