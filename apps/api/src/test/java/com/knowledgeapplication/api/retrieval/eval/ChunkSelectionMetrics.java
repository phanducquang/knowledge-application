package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.ai.quota.AiQuotaProperties;
import java.time.ZoneId;
import java.util.*;

/** Pure diagnostic math; never ranks candidates or decides runtime settings. */
final class ChunkSelectionMetrics {
    private ChunkSelectionMetrics() {}
    static final AiQuotaProperties ESTIMATOR = new AiQuotaProperties(false, 1, 1, 1, 2.5, ZoneId.of("UTC"), null);
    record Volume(int samples, double mean, long p50, long p95, long max, double meanEstimatedTokens) {}
    record Coverage(int conditionalSamples, int eligibleChunks, Double supportingChunkRecallGivenRelevantNoteRetrieved,
            boolean noteHitChunkMiss, List<String> capPressureChunks) {}
    record Delta(double chunkRecallAt8, double chunkMrr, int noteHitChunkMissCount,
            double meanContextChars, double diversity, double noteRecallAt8) {}

    static Coverage coverage(Set<String> expected, List<String> retrieved, int cap) {
        var selected = new HashSet<>(retrieved);
        var counts = new HashMap<String, Integer>();
        retrieved.forEach(c -> counts.merge(note(c), 1, Integer::sum));
        var eligible = expected.stream().filter(c -> counts.containsKey(note(c))).toList();
        long hits = eligible.stream().filter(selected::contains).count();
        var pressure = eligible.stream().filter(c -> !selected.contains(c) && counts.get(note(c)) >= cap).sorted().toList();
        return new Coverage(eligible.isEmpty() ? 0 : 1, eligible.size(), eligible.isEmpty() ? null : (double) hits / eligible.size(),
                !eligible.isEmpty() && hits < eligible.size(), pressure);
    }
    static String note(String chunkId) { return chunkId.substring(0, chunkId.lastIndexOf('#')); }
    static String sizeClass(int baselineChunkCount) {
        if (baselineChunkCount < 1) throw new IllegalArgumentException("Empty note classification");
        return baselineChunkCount <= 2 ? "short" : baselineChunkCount <= 4 ? "medium" : "long";
    }
    static Volume volume(List<Long> values) {
        if (values.isEmpty() || values.stream().anyMatch(v -> v < 0)) throw new IllegalArgumentException("Invalid context samples");
        var sorted = values.stream().sorted().toList();
        return new Volume(values.size(), values.stream().mapToLong(Long::longValue).average().orElseThrow(),
                percentile(sorted, 0.5), percentile(sorted, 0.95), sorted.get(sorted.size()-1),
                values.stream().mapToLong(ESTIMATOR::estimate).average().orElseThrow());
    }
    private static long percentile(List<Long> sorted, double p) { return sorted.get((int) Math.ceil(p * sorted.size()) - 1); }
    static double recall(RetrievalMetrics.Score score, int k) {
        return score.cutoffs().stream().filter(c -> c.k() == k).findFirst().orElseThrow().recall();
    }
    static Delta delta(double recall, double mrr, int misses, double chars, double diversity, double noteRecall,
            double baseRecall, double baseMrr, int baseMisses, double baseChars, double baseDiversity, double baseNoteRecall) {
        return new Delta(recall-baseRecall, mrr-baseMrr, misses-baseMisses, chars-baseChars, diversity-baseDiversity, noteRecall-baseNoteRecall);
    }
}
