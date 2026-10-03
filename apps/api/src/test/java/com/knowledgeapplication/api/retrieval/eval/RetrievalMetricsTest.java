package com.knowledgeapplication.api.retrieval.eval;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RetrievalMetricsTest {
    private static final List<Integer> KS = List.of(1, 3, 5);
    @Test void firstRankAndMultipleRelevantRecall() {
        var score = RetrievalMetrics.score(List.of("a", "x", "b"), Set.of("a", "b"), KS, true);
        assertThat(score.cutoffs()).containsExactly(new RetrievalMetrics.AtK(1, 1, 0.5),
                new RetrievalMetrics.AtK(3, 1, 1), new RetrievalMetrics.AtK(5, 1, 1));
        assertThat(score.reciprocalRank()).isEqualTo(1);
    }
    @Test void kthRankAndReciprocalRank() {
        var score = RetrievalMetrics.score(List.of("x", "y", "a"), Set.of("a"), KS, true);
        assertThat(score.cutoffs().get(0)).isEqualTo(new RetrievalMetrics.AtK(1, 0, 0));
        assertThat(score.cutoffs().get(1)).isEqualTo(new RetrievalMetrics.AtK(3, 1, 1));
        assertThat(score.reciprocalRank()).isEqualTo(1.0 / 3);
    }
    @Test void missingRelevantNeverGetsReciprocalRank() {
        var score = RetrievalMetrics.score(List.of("x", "y"), Set.of("a"), KS, true);
        assertThat(score.reciprocalRank()).isZero();
        assertThat(score.cutoffs()).allMatch(k -> k.hitRate() == 0 && k.recall() == 0);
    }
    @Test void semanticDuplicatesAreReportedAndCollapsedBeforeRank() {
        var score = RetrievalMetrics.score(List.of("x", "x", "a", "b"), Set.of("a", "b"), KS, true);
        assertThat(score.duplicateResults()).containsExactly("x");
        assertThat(score.cutoffs().get(1).recall()).isEqualTo(1);
        assertThat(score.reciprocalRank()).isEqualTo(0.5);
    }
    @Test void ragNotesUseChunkPositionsButDoNotDoubleCountRelevantNote() {
        var score = RetrievalMetrics.score(List.of("a", "a", "x", "b"), Set.of("a", "b"), KS, false);
        assertThat(score.cutoffs().get(1).recall()).isEqualTo(0.5);
        assertThat(score.cutoffs().get(2).recall()).isEqualTo(1);
    }
    @Test void shorterRetrievalAndEmptyRetrievalAreSafe() {
        assertThat(RetrievalMetrics.score(List.of("a"), Set.of("a", "b"), KS, true).cutoffs().get(2).recall()).isEqualTo(0.5);
        assertThat(RetrievalMetrics.score(List.of(), Set.of("a"), KS, true).reciprocalRank()).isZero();
    }
    @Test void negativeOrAbsentTruthMustNotBeScored() {
        assertThatIllegalArgumentException().isThrownBy(() -> RetrievalMetrics.score(List.of("a"), Set.of(), KS, true));
        assertThat(RetrievalMetrics.mean(List.of(), KS)).isNull();
    }
    @Test void invalidCutoffsFailFast() {
        assertThatIllegalArgumentException().isThrownBy(() -> RetrievalMetrics.score(List.of(), Set.of("a"), List.of(0), true));
        assertThatIllegalArgumentException().isThrownBy(() -> RetrievalMetrics.score(List.of(), Set.of("a"), List.of(), true));
    }
    @Test void macroMeanUsesOnlyEligibleSamples() {
        var hit = RetrievalMetrics.score(List.of("a"), Set.of("a", "b"), KS, true);
        var miss = RetrievalMetrics.score(List.of("x"), Set.of("a"), KS, true);
        var mean = RetrievalMetrics.mean(List.of(hit, miss), KS);
        assertThat(mean.cutoffs().get(0)).isEqualTo(new RetrievalMetrics.AtK(1, 0.5, 0.25));
        assertThat(mean.reciprocalRank()).isEqualTo(0.5);
        assertThat(mean.cutoffs()).allMatch(k -> k.recall() >= 0 && k.recall() <= 1);
    }
}
