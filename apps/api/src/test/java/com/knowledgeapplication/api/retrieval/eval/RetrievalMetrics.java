package com.knowledgeapplication.api.retrieval.eval;

import java.util.*;

final class RetrievalMetrics {
    private RetrievalMetrics() {}
    record AtK(int k, double hitRate, double recall) {}
    record Score(List<AtK> cutoffs, double reciprocalRank, List<String> duplicateResults) {}

    /** collapseRanks=true for Semantic notes; false for note coverage at RAG chunk positions. */
    static Score score(List<String> retrieved, Set<String> relevant, List<Integer> ks, boolean collapseRanks) {
        if (relevant.isEmpty()) throw new IllegalArgumentException("Negative/absent ground truth is diagnostic, not a recall denominator");
        if (ks.isEmpty() || ks.stream().anyMatch(k -> k == null || k < 1)) throw new IllegalArgumentException("K must be positive");
        var seen = new HashSet<String>(); var duplicates = new LinkedHashSet<String>();
        for (var item : retrieved) if (!seen.add(item)) duplicates.add(item);
        List<String> ranks = collapseRanks ? new ArrayList<>(new LinkedHashSet<>(retrieved)) : retrieved;
        var scores = new ArrayList<AtK>();
        for (int k : ks) {
            var top = new HashSet<>(ranks.subList(0, Math.min(k, ranks.size())));
            top.retainAll(relevant);
            scores.add(new AtK(k, top.isEmpty() ? 0 : 1, (double) top.size() / relevant.size()));
        }
        double rr = 0;
        for (int i = 0; i < ranks.size(); i++) if (relevant.contains(ranks.get(i))) { rr = 1.0 / (i + 1); break; }
        return new Score(List.copyOf(scores), rr, List.copyOf(duplicates));
    }
    static Score mean(List<Score> scores, List<Integer> ks) {
        if (scores.isEmpty()) return null; // Explicitly no eligible samples, never divide by zero or report fake zero quality.
        var cuts = new ArrayList<AtK>();
        for (int k : ks) cuts.add(new AtK(k,
                scores.stream().mapToDouble(s -> s.cutoffs().stream().filter(c -> c.k() == k).findFirst().orElseThrow().hitRate()).average().orElseThrow(),
                scores.stream().mapToDouble(s -> s.cutoffs().stream().filter(c -> c.k() == k).findFirst().orElseThrow().recall()).average().orElseThrow()));
        return new Score(cuts, scores.stream().mapToDouble(Score::reciprocalRank).average().orElseThrow(), List.of());
    }
}
