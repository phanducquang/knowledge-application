package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.metadata.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class MetadataCalibrationTest {
    final MetadataCorpus v1=MetadataCorpus.load();
    final MetadataCalibrationCorpus overlay=MetadataCalibrationCorpus.load();
    final MetadataCorpus v2=overlay.materialize(v1);
    MetadataCorpus.Fixture fixture(MetadataCorpus corpus,String id) { return corpus.fixtures().stream().filter(f->f.id().equals(id)).findFirst().orElseThrow(); }
    MetadataSuggestion output(String summary,String... tags) { return new MetadataSuggestion(summary,List.of(tags)); }
    String fakeReport() {
        var identity=MetadataEvaluation.Identity.create(v1,"gemini","gemini-3.5-flash-lite",32000,600,Instant.EPOCH);
        var quota=new com.knowledgeapplication.api.ai.quota.AiQuotaProperties(true,10,200000,400,2.5,ZoneId.of("UTC"),null);
        return MetadataReportWriter.json(MetadataEvaluation.run(v1,MetadataEvaluation.fake(v1,32000),identity,quota,c->{},false,20,150000));
    }
    MetadataCalibration.Report evaluate() { return MetadataCalibration.evaluate(MetadataCalibration.parse(fakeReport()),overlay); }
    @Test void v1FingerprintIsFrozenAndV2IdentityIsSeparate() {
        assertThat(v1.fingerprint()).isEqualTo(MetadataCalibrationCorpus.V1_FINGERPRINT);
        assertThat(v2.version()).isEqualTo("metadata-eval-v2"); assertThat(v2.fingerprint()).isNotEqualTo(v1.fingerprint());
        var report=evaluate(); assertThat(report.identity().metricsVersion()).isEqualTo("metadata-metrics-v2");
        assertThat(report.identity().reportVersion()).isEqualTo("metadata-report-v3");
        assertThat(report.identity().evaluationFingerprint()).isNotEqualTo(v2.fingerprint());
    }
    @Test void noteInputsCoreExpectationsAndForbiddenListsAreIdenticalAcrossVersions() {
        for(int i=0;i<20;i++) {
            var a=v1.fixtures().get(i); var b=v2.fixtures().get(i);
            assertThat(a.input(32000)).isEqualTo(b.input(32000)); assertThat(a.markdown()).isEqualTo(b.markdown());
            assertThat(a.expectedTags().stream().map(MetadataCorpus.Tag::canonical)).containsExactlyElementsOf(b.expectedTags().stream().map(MetadataCorpus.Tag::canonical).toList());
            assertThat(a.concepts().stream().map(c->c.id()+"/"+c.evidence())).containsExactlyElementsOf(b.concepts().stream().map(c->c.id()+"/"+c.evidence()).toList());
            assertThat(a.forbiddenClaims()).isEqualTo(b.forbiddenClaims()); assertThat(a.forbiddenTags()).isEqualTo(b.forbiddenTags());
        }
        assertThat(v1.genericTags()).hasSize(6); assertThat(v2.genericTags()).hasSize(15);
    }
    @ParameterizedTest @ValueSource(strings={"Timeouts","TIMEOUTS"," Ｔｉｍｅｏｕｔｓ "})
    void explicitPluralCaseFoldAndUnicodeEquivalentAreStrict(String tag) {
        var f=fixture(v2,"nginx"); var m=MetadataMetrics.score(f,output("connection",tag),v2.genericTags(),32000);
        assertThat(m.precision()).isEqualTo(1); assertThat(m.observedTags()).containsExactly("Timeout");
        assertThat(MetadataMetrics.score(fixture(v1,"nginx"),output("connection",tag),v1.genericTags(),32000).precision()).isZero();
    }
    @Test void canonicalAndAbbreviationAliasesStayExplicit() {
        assertThat(MetadataCorpus.matchesTag("Continuous Integration",fixture(v2,"actions").expectedTags().get(1))).isTrue();
        assertThat(MetadataCorpus.matchesTag("OpenID Connect",fixture(v2,"oidc").expectedTags().get(0))).isTrue();
        assertThat(MetadataCorpus.matchesTag("Cross-Site Request Forgery",fixture(v2,"oidc").expectedTags().get(1))).isTrue();
        var tags=MetadataCalibration.classify(fixture(v2,"nginx"),output("connection","Nginx","Timeouts"),v2.genericTags(),overlay);
        assertThat(tags.get(0).tier()).isEqualTo(MetadataCalibrationCorpus.Tier.EXACT_CANONICAL);
        assertThat(tags.get(1).tier()).isEqualTo(MetadataCalibrationCorpus.Tier.ACCEPTED_EQUIVALENT);
    }
    @ParameterizedTest @ValueSource(strings={"Database Index","database indexing","Indexing"})
    void broaderTagsNeverRecoverCompositeIndex(String tag) {
        String id=tag.equals("database indexing")?"long-middle":"postgres-index";
        var f=fixture(v2,id); var m=MetadataMetrics.score(f,output("chỉ mục ghép",tag),v2.genericTags(),32000);
        assertThat(m.precision()).isZero(); assertThat(m.recall()).isZero();
        assertThat(MetadataCalibration.classify(f,output("chỉ mục ghép",tag),v2.genericTags(),overlay).get(0).tier())
                .isEqualTo(MetadataCalibrationCorpus.Tier.RELEVANT_BUT_BROADER);
    }
    @Test void removingOverbroadAliasCanReduceStrictScores() {
        var suggestion=output("chỉ mục ghép","Indexing");
        assertThat(MetadataMetrics.score(fixture(v1,"postgres-index"),suggestion,v1.genericTags(),32000).precision()).isEqualTo(1);
        assertThat(MetadataMetrics.score(fixture(v2,"postgres-index"),suggestion,v2.genericTags(),32000).precision()).isZero();
    }
    @Test void relevantAdditionalTagDoesNotInflateRecall() {
        var f=fixture(v2,"kafka"); var s=output("offset","Consumer Offsets");
        assertThat(MetadataMetrics.score(f,s,v2.genericTags(),32000).recall()).isZero();
        var reviews=MetadataCalibration.classify(f,s,v2.genericTags(),overlay);
        assertThat(reviews.get(0).tier()).isEqualTo(MetadataCalibrationCorpus.Tier.RELEVANT_ADDITIONAL);
        assertThat(MetadataCalibration.diagnostics(reviews).additionalRelevantTagRate()).isEqualTo(1);
    }
    @Test void unknownAndExplicitUnsupportedTagsRemainUnaccepted() {
        var tags=MetadataCalibration.classify(fixture(v2,"redis-vi"),output("TTL","Performance","Unreviewed Label"),v2.genericTags(),overlay);
        assertThat(tags).allMatch(t->t.tier()==MetadataCalibrationCorpus.Tier.IRRELEVANT_UNSUPPORTED);
        assertThat(MetadataCalibration.diagnostics(tags).relevantTagRate()).isZero();
    }
    @Test void equivalentReadGapPhraseChangesV2OnlyWithoutFuzzyMatching() {
        var s=output("Connection bounds time between read operations.","Nginx");
        var old=MetadataMetrics.score(fixture(v1,"nginx"),s,v1.genericTags(),32000);
        var now=MetadataMetrics.score(fixture(v2,"nginx"),s,v2.genericTags(),32000);
        assertThat(old.requiredConceptCoverage()).isEqualTo(.5); assertThat(now.requiredConceptCoverage()).isEqualTo(1);
        assertThat(MetadataMetrics.score(fixture(v2,"nginx"),output("Reads a total response duration.","Nginx"),v2.genericTags(),32000).observedConcepts()).doesNotContain("read-gap");
    }
    @Test void genericDiagnosticsAreVersionedNotProductionPolicyOrStrictCredit() {
        var s=output("connection","Configuration");
        var a=MetadataMetrics.score(fixture(v1,"nginx"),s,v1.genericTags(),32000);
        var b=MetadataMetrics.score(fixture(v2,"nginx"),s,v2.genericTags(),32000);
        assertThat(a.genericTagRate()).isZero(); assertThat(b.genericTagRate()).isEqualTo(1);
        assertThat(a.precision()).isEqualTo(b.precision());
    }
    @Test void lateFactsAndVisibleNAAreNotRemovedToImproveScores() {
        var r=evaluate();
        var late=r.caseResults().stream().filter(c->c.id().equals("long-late")).findFirst().orElseThrow();
        assertThat(late.v2().metrics().invisibleConcepts()).hasSize(3); assertThat(late.v2().metrics().visibleConceptCoverage()).isNull();
        assertThat(late.v2().metrics().requiredConceptCoverage()).isZero();
        assertThat(late.reviewAssistance()).extracting(MetadataCalibration.Finding::code).contains("INPUT_LIMIT_32K").doesNotContain("MODEL_MISS","MODEL_UNDER_SPECIFIC_TAG");
        var both=r.caseResults().stream().filter(c->c.id().equals("long-early-late")).findFirst().orElseThrow();
        assertThat(both.v2().metrics().requiredConceptCoverage()).isEqualTo(1.0/3); assertThat(both.v2().metrics().visibleConceptCoverage()).isEqualTo(1);
    }
    @Test void existingTagsExcludedAndAliasesCannotInflateConceptCredit() {
        var f=fixture(v2,"oidc"); var s=output("CSRF", "OIDC","CSRF","Cross-Site Request Forgery").validated(f.input(32000).currentTags());
        assertThat(s.tags()).doesNotContain("OIDC");
        var m=MetadataMetrics.score(f,s,v2.genericTags(),32000);
        assertThat(m.expectedNewTags()).containsExactly("CSRF","Session Cookie"); assertThat(m.precision()).isEqualTo(.5); assertThat(m.recall()).isEqualTo(.5);
    }
    @Test void forbiddenDiagnosticsNeverWeakenAcrossVersions() {
        var r=evaluate(); assertThat(r.v1().forbiddenClaimCount()).isEqualTo(r.v2Strict().forbiddenClaimCount());
        var s=output("response must finish in 10", "Kafka");
        for(var corpus:List.of(v1,v2)) {
            var m=MetadataMetrics.score(fixture(corpus,"nginx"),s,corpus.genericTags(),32000);
            assertThat(m.forbiddenClaimCount()).isEqualTo(1); assertThat(m.forbiddenTagCount()).isEqualTo(1);
        }
    }
    @Test void sameFrozenSuggestionsRescoredDeterministicallyWithHumanFieldsAlwaysBlank() {
        var frozen=MetadataCalibration.parse(fakeReport()); var a=MetadataCalibration.evaluate(frozen,overlay); var b=MetadataCalibration.evaluate(frozen,overlay);
        assertThat(MetadataCalibration.json(a)).isEqualTo(MetadataCalibration.json(b));
        assertThat(a.providerCalls()).isZero(); assertThat(a.quotaReservations()).isZero();
        assertThat(a.attribution()).isEqualTo("BENCHMARK_CALIBRATION_DELTA_NOT_MODEL_IMPROVEMENT");
        for(var c:a.caseResults()) {
            assertThat(c.unchangedSuggestion()).isSameAs(frozen.suggestions().get(c.id()));
            assertThat(c.humanRubric()).isEqualTo(MetadataCalibration.HumanRubric.blank());
        }
        assertThat(MetadataCalibration.review(a)).contains("HUMAN ONLY","factuality ___","consistency ___");
    }
    @Test void decisionReasonsAndSemanticIdentityAreFingerprintInputs() {
        assertThat(overlay.decisions()).hasSize(69).allMatch(d->!d.reason().isBlank() && !d.evidence().isBlank());
        var decisions=new ArrayList<>(overlay.decisions()); var d=decisions.get(0);
        decisions.set(0,new MetadataCalibrationCorpus.Decision(d.id(),d.caseId(),d.type(),d.target(),d.value(),d.evidence(),d.reason()+" clarified",d.confidence()));
        var changed=new MetadataCalibrationCorpus(overlay.version(),overlay.metricsVersion(),overlay.reportVersion(),overlay.baseCorpusFingerprint(),decisions);
        assertThat(changed.fingerprint(changed.materialize(v1))).isNotEqualTo(overlay.fingerprint(v2));
    }
    @Test void sourceEvidenceAndDuplicateDecisionEditsAreRequired() {
        var d=overlay.decisions().get(0);
        var invalid=new MetadataCalibrationCorpus.Decision(d.id(),d.caseId(),d.type(),d.target(),d.value(),"missing-evidence",d.reason(),d.confidence());
        var changed=new MetadataCalibrationCorpus(overlay.version(),overlay.metricsVersion(),overlay.reportVersion(),overlay.baseCorpusFingerprint(),List.of(invalid));
        assertThatThrownBy(()->changed.materialize(v1)).isInstanceOf(IllegalArgumentException.class);
        var duplicate=new MetadataCalibrationCorpus(overlay.version(),overlay.metricsVersion(),overlay.reportVersion(),overlay.baseCorpusFingerprint(),List.of(d,d));
        assertThatThrownBy(()->duplicate.materialize(v1)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"INCOMPLETE","BROKEN_JSON","MISSING_CASE","MODEL_MISMATCH","DUPLICATE_CASE","INVALID_OUTPUT","INPUT_MISMATCH"})
    void badBaselineRejectedWithoutFallback(String scenario) {
        var root=MetadataCalibrationCorpus.JSON.readTree(fakeReport()).deepCopy();
        switch(scenario) {
            case "BROKEN_JSON" -> { assertThatThrownBy(()->MetadataCalibration.parse("{secret-input")).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid or incompatible completed synthetic baseline").hasNoCause(); return; }
            case "INCOMPLETE" -> ((tools.jackson.databind.node.ObjectNode)root).put("status","INCOMPLETE");
            case "MISSING_CASE" -> ((tools.jackson.databind.node.ArrayNode)root.path("cases")).remove(0);
            case "MODEL_MISMATCH" -> ((tools.jackson.databind.node.ObjectNode)root.path("identity")).put("model","other-model");
            case "DUPLICATE_CASE" -> ((tools.jackson.databind.node.ArrayNode)root.path("cases")).set(1,root.path("cases").get(0));
            case "INVALID_OUTPUT" -> ((tools.jackson.databind.node.ObjectNode)root.path("cases").get(0).path("suggestion")).put("summary"," ");
            case "INPUT_MISMATCH" -> ((tools.jackson.databind.node.ObjectNode)root.path("cases").get(0)).put("inputFingerprint","bad");
        }
        assertThatThrownBy(()->MetadataCalibration.parse(root.toString())).isInstanceOf(IllegalArgumentException.class).hasNoCause();
    }
    @Test void oversizedMissingAndSourceOverwriteRejected(@TempDir Path dir) throws Exception {
        assertThatThrownBy(()->MetadataCalibration.load(dir.resolve("missing"))).isInstanceOf(IllegalArgumentException.class).hasNoCause();
        var huge=dir.resolve("huge"); Files.writeString(huge," ".repeat(2_000_001));
        assertThatThrownBy(()->MetadataCalibration.load(huge)).isInstanceOf(IllegalArgumentException.class).hasNoCause();
        var source=dir.resolve("calibration-v2.json"); Files.writeString(source,fakeReport()); String before=Files.readString(source);
        assertThatThrownBy(()->MetadataCalibration.write(evaluate(),dir,source)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(source)).isEqualTo(before);
    }
    @Test void cliIsOfflineEvenWithAmbientEnabledFeatureAndFakeKeys(@TempDir Path dir) throws Exception {
        var source=dir.resolve("synthetic-fake.json"); Files.writeString(source,fakeReport()); String before=Files.readString(source);
        var calls=new AtomicInteger(); var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{calls.incrementAndGet();exchange.sendResponseHeaders(500,-1);exchange.close();});server.start();
        try {
            var command=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-Dapp.gemini.api-key=fake-no-network", "-Dapp.ai.metadata.enabled=true",
                    "-Dapp.ai.metadata.base-url=http://127.0.0.1:"+server.getAddress().getPort(),"-Dmetadata.eval.report-directory="+dir.resolve("reports"),
                    "-cp",System.getProperty("metadata.eval.test-classpath"),MetadataEvalCalibrateMain.class.getName());
            command.environment().put("METADATA_EVAL_BASELINE_REPORT",source.toString());
            command.environment().put("AI_METADATA_ENABLED","true"); command.environment().put("GEMINI_API_KEY","fake-no-network"); command.environment().put("GOOGLE_API_KEY","fake-no-network");
            var process=command.redirectErrorStream(true).start();
            boolean finished=process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS); if(!finished) process.destroyForcibly();
            assertThat(finished).isTrue(); assertThat(process.exitValue()).isZero();
            assertThat(new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).contains("zero provider calls/reservations").doesNotContain("fake-no-network");
            assertThat(calls.get()).isZero(); assertThat(Files.readString(source)).isEqualTo(before);
            assertThat(Files.exists(dir.resolve("reports/calibration-review.md"))).isTrue();
        } finally { server.stop(0); }
    }
}
