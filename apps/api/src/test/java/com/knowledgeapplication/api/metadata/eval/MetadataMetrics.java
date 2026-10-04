package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.metadata.MetadataSuggestion;
import java.util.*;

final class MetadataMetrics {
    record Result(double precision, double recall, double f1, Double visiblePrecision, Double visibleRecall,
            Double visibleF1, double genericTagRate, int forbiddenTagCount, int suggestedTagCount,
            double requiredConceptCoverage, Double visibleConceptCoverage, double expectedCoverageCeiling,
            int forbiddenClaimCount, int summaryChars, int summaryWords, double conceptsPer100Chars,
            List<String> expectedNewTags, List<String> visibleExpectedTags, List<String> invisibleExpectedTags,
            List<String> observedTags, List<String> requiredConcepts, List<String> visibleConcepts,
            List<String> invisibleConcepts, List<String> observedConcepts, List<String> flaggedClaims,
            List<String> flaggedTags) {}
    static Result score(MetadataCorpus.Fixture fixture, MetadataSuggestion suggestion, List<String> generic, int limit) {
        String visible=fixture.visibleEvidence(limit);
        var expected=fixture.newTags();
        var visibleTags=expected.stream().filter(t -> MetadataCorpus.contains(visible,t.evidence())).toList();
        var observed=expected.stream().filter(t -> suggestion.tags().stream().anyMatch(s -> MetadataCorpus.matchesTag(s,t))).toList();
        int hits=observed.size(), visibleHits=(int)observed.stream().filter(visibleTags::contains).count(), total=suggestion.tags().size();
        double precision=ratio(hits,total), recall=ratio(hits,expected.size());
        Double visibleRecall=visibleTags.isEmpty()?null:ratio(visibleHits,visibleTags.size());
        Double visiblePrecision=visibleTags.isEmpty()?null:ratio(visibleHits,total);
        var required=fixture.concepts();
        var visibleConcepts=required.stream().filter(c -> MetadataCorpus.contains(visible,c.evidence())).toList();
        var observedConcepts=required.stream().filter(c -> c.phrases().stream().anyMatch(p -> MetadataCorpus.contains(suggestion.summary(),p))).toList();
        var flaggedClaims=fixture.forbiddenClaims().stream().filter(p -> MetadataCorpus.contains(suggestion.summary(),p)).toList();
        var flaggedTags=suggestion.tags().stream().filter(t -> fixture.forbiddenTags().stream().anyMatch(f -> MetadataCorpus.normalize(t).equals(MetadataCorpus.normalize(f)))).toList();
        long genericCount=suggestion.tags().stream().filter(t -> generic.stream().anyMatch(g -> MetadataCorpus.normalize(t).equals(MetadataCorpus.normalize(g)))).count();
        int chars=suggestion.summary().length(), words=suggestion.summary().strip().split("\\s+").length;
        return new Result(precision,recall,f1(precision,recall),visiblePrecision,visibleRecall,
                visibleRecall==null?null:f1(visiblePrecision,visibleRecall),ratio(genericCount,total),flaggedTags.size(),total,
                ratio(observedConcepts.size(),required.size()),visibleConcepts.isEmpty()?null:
                        ratio(observedConcepts.stream().filter(visibleConcepts::contains).count(),visibleConcepts.size()),
                ratio(visibleConcepts.size(),required.size()),flaggedClaims.size(),chars,words,ratio(observedConcepts.size()*100L,chars),
                expected.stream().map(MetadataCorpus.Tag::canonical).toList(),visibleTags.stream().map(MetadataCorpus.Tag::canonical).toList(),
                expected.stream().filter(t -> !visibleTags.contains(t)).map(MetadataCorpus.Tag::canonical).toList(),
                observed.stream().map(MetadataCorpus.Tag::canonical).toList(),required.stream().map(MetadataCorpus.Concept::id).toList(),
                visibleConcepts.stream().map(MetadataCorpus.Concept::id).toList(),required.stream().filter(c -> !visibleConcepts.contains(c)).map(MetadataCorpus.Concept::id).toList(),
                observedConcepts.stream().map(MetadataCorpus.Concept::id).toList(),flaggedClaims,flaggedTags);
    }
    static double ratio(long numerator,long denominator) { return denominator==0?0:(double)numerator/denominator; }
    static double f1(double precision,double recall) { return precision+recall==0?0:2*precision*recall/(precision+recall); }
}
