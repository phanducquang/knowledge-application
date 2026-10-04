package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.knowledge.embedding.MarkdownChunker;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ChunkSelectionMetricsTest {
    @Test void noteHitChunkMissIsDifferentFromMissingNote() {
        var hit = ChunkSelectionMetrics.coverage(Set.of("a#4"),List.of("a#2","a#3"),2);
        assertThat(hit.noteHitChunkMiss()).isTrue(); assertThat(hit.supportingChunkRecallGivenRelevantNoteRetrieved()).isZero();
        var missing = ChunkSelectionMetrics.coverage(Set.of("a#4"),List.of("b#0"),2);
        assertThat(missing.noteHitChunkMiss()).isFalse(); assertThat(missing.supportingChunkRecallGivenRelevantNoteRetrieved()).isNull();
    }
    @Test void conditionalRecallExcludesChunksFromAbsentNotesAndCountsUniqueTruth() {
        var c = ChunkSelectionMetrics.coverage(Set.of("a#0","a#4","b#0"),List.of("a#0","a#1"),2);
        assertThat(c.eligibleChunks()).isEqualTo(2); assertThat(c.supportingChunkRecallGivenRelevantNoteRetrieved()).isEqualTo(0.5);
    }
    @Test void capPressureRequiresSameRelevantNoteAtItsActualCap() {
        assertThat(ChunkSelectionMetrics.coverage(Set.of("a#4"),List.of("a#0","a#1","b#0"),2).capPressureChunks()).containsExactly("a#4");
        assertThat(ChunkSelectionMetrics.coverage(Set.of("a#4"),List.of("a#0","b#0"),2).capPressureChunks()).isEmpty();
        assertThat(ChunkSelectionMetrics.coverage(Set.of("a#4"),List.of("a#0","a#4"),2).capPressureChunks()).isEmpty();
    }
    @Test void negativeGroundTruthIsUnscoredInConditionalCoverage() {
        var c = ChunkSelectionMetrics.coverage(Set.of(),List.of("a#0"),2);
        assertThat(c.conditionalSamples()).isZero(); assertThat(c.supportingChunkRecallGivenRelevantNoteRetrieved()).isNull();
    }
    @Test void contextVolumeUsesNearestRankPercentilesAndRealConservativeEstimator() {
        var v = ChunkSelectionMetrics.volume(List.of(100L,200L,300L,1000L));
        assertThat(v.mean()).isEqualTo(400); assertThat(v.p50()).isEqualTo(200); assertThat(v.p95()).isEqualTo(1000);
        assertThat(v.max()).isEqualTo(1000); assertThat(v.meanEstimatedTokens()).isEqualTo(160);
        assertThat(ChunkSelectionMetrics.volume(List.of(0L)).meanEstimatedTokens()).isEqualTo(1);
    }
    @Test void contextVolumeRejectsInvalidSamples() {
        assertThatIllegalArgumentException().isThrownBy(() -> ChunkSelectionMetrics.volume(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> ChunkSelectionMetrics.volume(List.of(-1L)));
    }
    @Test void baselineDeltaIsTransparentSignedSubtraction() {
        var d = ChunkSelectionMetrics.delta(0.8,0.7,4,11000,0.7,0.9,0.6,0.5,8,10000,0.8,0.95);
        assertThat(d.chunkRecallAt8()).isCloseTo(0.2,within(0.00001)); assertThat(d.chunkMrr()).isCloseTo(0.2,within(0.00001));
        assertThat(d.noteHitChunkMissCount()).isEqualTo(-4); assertThat(d.meanContextChars()).isEqualTo(1000);
        assertThat(d.diversity()).isCloseTo(-0.1,within(0.00001)); assertThat(d.noteRecallAt8()).isCloseTo(-0.05,within(0.00001));
    }
    @Test void sizeClassificationUsesFixtureChunkCountNotResultQuality() {
        assertThat(ChunkSelectionMetrics.sizeClass(1)).isEqualTo("short"); assertThat(ChunkSelectionMetrics.sizeClass(2)).isEqualTo("short");
        assertThat(ChunkSelectionMetrics.sizeClass(3)).isEqualTo("medium"); assertThat(ChunkSelectionMetrics.sizeClass(4)).isEqualTo("medium");
        assertThat(ChunkSelectionMetrics.sizeClass(5)).isEqualTo("long");
        assertThatIllegalArgumentException().isThrownBy(() -> ChunkSelectionMetrics.sizeClass(0));
    }
    @Test void positionThirdsAreDeterministicAndBounded() {
        assertThat(RetrievalCorpus.position(0,6)).isEqualTo("early"); assertThat(RetrievalCorpus.position(2,6)).isEqualTo("middle");
        assertThat(RetrievalCorpus.position(5,6)).isEqualTo("late");
        assertThatIllegalArgumentException().isThrownBy(() -> RetrievalCorpus.position(6,6));
        var expected = List.of(new ChunkSelectionEvaluation.ExpectedChunk("short#0","short","early","early",1,1,true,"SELECTED"),
                new ChunkSelectionEvaluation.ExpectedChunk("long#0","long","early","early",3,null,false,"PER_NOTE_CAP"),
                new ChunkSelectionEvaluation.ExpectedChunk("long#5","long","late","late",1,1,true,"SELECTED"));
        var cases = List.of(new ChunkSelectionEvaluation.CaseDiagnostic("q-test",null,expected,List.of(),0,0,0,0,null));
        var buckets = ChunkSelectionEvaluation.buckets(cases,ChunkSelectionEvaluation.ExpectedChunk::baselinePosition,e -> e.sizeClass().equals("long"));
        assertThat(buckets.get("early")).isEqualTo(new ChunkSelectionEvaluation.Bucket(1,0,0.0));
        assertThat(buckets.get("late")).isEqualTo(new ChunkSelectionEvaluation.Bucket(1,1,1.0));
    }
    @Test void incorrectDeclaredPositionIsRejectedRatherThanSilentlyUnscored() {
        var base = RetrievalCorpus.load(); var q = base.queries().get(0);
        var incorrect = new RetrievalCorpus(base.version(),base.description(),base.notes(),List.of(new RetrievalCorpus.Query(q.id(),q.query(),q.category(),q.language(),q.rationale(),false,
                q.relevantKnowledge(),List.of(new RetrievalCorpus.ChunkTruth("spring-webclient-timeout","client-config","late")))));
        assertThatIllegalArgumentException().isThrownBy(() -> incorrect.validate(new MarkdownChunker(4000,200)));
    }
    @Test void matrixIsBoundedUniqueAndIncludesExactlyOneBaseline() {
        var matrix = ChunkSelectionEvaluation.matrix(); assertThat(matrix).hasSize(48).doesNotHaveDuplicates();
        assertThat(matrix.stream().filter(ChunkSelectionEvaluation.Variant::baseline).count()).isEqualTo(1);
        assertThatIllegalArgumentException().isThrownBy(() -> new ChunkSelectionEvaluation.Variant(4000,1200,8,2));
        assertThatIllegalArgumentException().isThrownBy(() -> new ChunkSelectionEvaluation.Variant(4000,200,0,2));
    }
    @Test void expandedCorpusKeepsOriginalQueriesAndIndependentLongEvidence() {
        var corpus = ChunkSelectionEvaluation.extendedCorpus(); assertThat(corpus.notes()).hasSize(28); assertThat(corpus.queries()).hasSize(60);
        assertThat(corpus.queries().subList(0,40)).isEqualTo(RetrievalCorpus.load().queries());
        assertThat(corpus.queries().stream().filter(q -> q.id().equals("q-unmapped")).findFirst().orElseThrow()).isEqualTo(RetrievalCorpus.load().queries().get(28));
        assertThat(corpus.queries().stream().flatMap(q -> q.relevantChunks().stream()).map(RetrievalCorpus.ChunkTruth::position).filter(Objects::nonNull))
                .contains("early","middle","late");
    }
}
