package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.ai.gemini.GeminiKnowledgeMetadataSuggestionClient;
import com.knowledgeapplication.api.ai.quota.AiQuotaProperties;
import com.knowledgeapplication.api.metadata.*;
import java.time.*;
import java.util.*;
import java.util.function.ToDoubleFunction;

final class MetadataEvaluation {
    static final String REPORT_VERSION="metadata-report-v1", METRICS_VERSION="metadata-metrics-v1";
    record Identity(String reportVersion,String metricsVersion,String corpusVersion,String corpusFingerprint,String provider,String model,String sdkVersion,
            String promptVersion,String promptFingerprint,String schemaVersion,String schemaFingerprint,String inputStrategyVersion,int contentLimit,int summaryLimit,
            int tagCountLimit,int tagLengthLimit,int maxOutputTokens,String evaluatedAt) {
        static Identity create(MetadataCorpus corpus,String provider,String model,int limit,int output,Instant now) {
            return new Identity(REPORT_VERSION,METRICS_VERSION,corpus.version(),corpus.fingerprint(),provider,model,"1.75.0",
                    MetadataGenerationContract.PROMPT_VERSION,GeminiKnowledgeMetadataSuggestionClient.promptFingerprint(),
                    MetadataGenerationContract.SCHEMA_VERSION,GeminiKnowledgeMetadataSuggestionClient.schemaFingerprint(),
                    MetadataGenerationContract.inputStrategyVersion(limit),limit,MetadataSuggestion.MAX_SUMMARY,
                    MetadataSuggestion.MAX_TAGS,MetadataSuggestion.MAX_TAG_LENGTH,output,now.toString());
        }
    }
    record Case(String id,String title,String language,String category,String lengthGroup,String position,
            int fullContentChars,int visibleContentChars,boolean truncated,String status,Double latencyMs,
            long estimatedInputTokens,MetadataSuggestion suggestion,MetadataMetrics.Result metrics,
            List<MetadataCorpus.Concept> conceptGroundTruth,List<MetadataCorpus.Tag> tagGroundTruth,
            List<String> existingTags,String rationale) {}
    record Aggregate(int cases,double precision,double recall,double f1,Double visiblePrecision,Double visibleRecall,
            Double visibleF1,double genericTagRate,int forbiddenTagCount,double meanSuggestedTagCount,
            double requiredConceptCoverage,Double visibleConceptCoverage,double expectedCoverageCeiling,
            int forbiddenClaimCount,double meanSummaryChars,double p50SummaryChars,double p95SummaryChars,
            Double meanLatencyMs,Double p50LatencyMs,Double p95LatencyMs) {}
    record Usage(int plannedCases,int attemptedCases,int validOutputs,int providerRequests,int fakeCalls,
            long plannedEstimatedInputTokens,long attemptedEstimatedInputTokens,double validStructuredOutputRate,
            Double durationMs) {}
    record Report(Identity identity,String status,String failure,Usage usage,Aggregate metrics,
            Map<String,Map<String,Aggregate>> groups,List<Case> cases) {}
    interface Pacer { void await(long inputChars); }
    static long preflight(MetadataCorpus corpus,int limit,AiQuotaProperties quota,int requestBudget,long tokenBudget) {
        corpus.validate();
        if(corpus.fixtures().size()>requestBudget) throw new IllegalArgumentException("Corpus exceeds live request budget");
        long tokens=0;
        for(var fixture:corpus.fixtures()) {
            long cost=quota.estimate(GeminiKnowledgeMetadataSuggestionClient.estimatedInputChars(fixture.input(limit)));
            if(cost>quota.inputTokensPerMinute()) throw new IllegalArgumentException("A case exceeds the configured minute token quota");
            tokens=Math.addExact(tokens,cost);
        }
        if(tokens>tokenBudget || corpus.fixtures().size()>quota.requestsPerDay()) throw new IllegalArgumentException("Corpus exceeds live token/day budget");
        return tokens;
    }
    static Report run(MetadataCorpus corpus,KnowledgeMetadataSuggestionClient provider,Identity identity,AiQuotaProperties quota,
            Pacer pacer,boolean live,int requestBudget,long tokenBudget) {
        long planned=preflight(corpus,identity.contentLimit(),quota,requestBudget,tokenBudget),start=System.nanoTime(),used=0;
        var results=new ArrayList<Case>(); int attempted=0,valid=0; String failure=null;
        for(var fixture:corpus.fixtures()) {
            var input=fixture.input(identity.contentLimit());
            long chars=GeminiKnowledgeMetadataSuggestionClient.estimatedInputChars(input),tokens=quota.estimate(chars),caseStart=System.nanoTime();
            MetadataSuggestion suggestion=null; MetadataMetrics.Result metrics=null; String status="VALID";
            try {
                pacer.await(chars); caseStart=System.nanoTime(); attempted++; used+=tokens;
                suggestion=provider.suggest(input).validated(input.currentTags());
                metrics=MetadataMetrics.score(fixture,suggestion,corpus.genericTags(),identity.contentLimit()); valid++;
            } catch(RuntimeException ex) { status="FAILED"; failure="Provider, quota, invalid output or pacing failure; stopped without retries."; }
            results.add(new Case(fixture.id(),fixture.title(),fixture.language(),fixture.category(),fixture.lengthGroup(),fixture.position(),
                    fixture.markdown().length(),input.content().length(),input.contentTruncated(),status,live?(System.nanoTime()-caseStart)/1_000_000.0:null,
                    tokens,suggestion,metrics,fixture.concepts(),fixture.expectedTags(),fixture.existingTags(),fixture.notes()));
            if(failure!=null) break;
        }
        var usage=new Usage(corpus.fixtures().size(),attempted,valid,live?attempted:0,live?0:attempted,planned,used,
                MetadataMetrics.ratio(valid,attempted),live?(System.nanoTime()-start)/1_000_000.0:null);
        // Partial cases remain reviewable, but no aggregate quality claim for incomplete runs.
        return new Report(identity,failure==null?"COMPLETE":"INCOMPLETE",failure,usage,failure==null?aggregate(results):null,
                failure==null?groups(results):Map.of(),List.copyOf(results));
    }
    static KnowledgeMetadataSuggestionClient fake(MetadataCorpus corpus,int limit) {
        var iterator=corpus.fixtures().iterator();
        return request -> {
            var fixture=iterator.next();
            if(!fixture.input(limit).equals(request)) throw new IllegalArgumentException("Fake input contract mismatch");
            String visible=fixture.visibleEvidence(limit);
            String summary=String.join("; ",fixture.concepts().stream().filter(c -> MetadataCorpus.contains(visible,c.evidence())).map(c -> c.phrases().get(0)).toList());
            if(summary.isEmpty()) summary="Synthetic operations background checklist.";
            var tags=new ArrayList<>(fixture.newTags().stream().filter(t -> MetadataCorpus.contains(visible,t.evidence())).map(MetadataCorpus.Tag::canonical).toList());
            // Intentionally imperfect, deterministic first case exercises generic/forbidden diagnostics.
            if(fixture.id().equals("spring-health")) { tags.add("Software"); summary+="; automatic restart"; }
            return new MetadataSuggestion(summary,tags);
        };
    }
    private static Map<String,Map<String,Aggregate>> groups(List<Case> cases) {
        var groups=new TreeMap<String,Map<String,Aggregate>>();
        for(String axis:List.of("language","category","length","position")) {
            var buckets=new TreeMap<String,List<Case>>();
            for(var c:cases) { String key=switch(axis) { case "language" -> c.language(); case "category" -> c.category(); case "length" -> c.lengthGroup(); default -> c.position(); };
                buckets.computeIfAbsent(key,k->new ArrayList<>()).add(c); }
            var aggregates=new TreeMap<String,Aggregate>(); buckets.forEach((key,value)->aggregates.put(key,aggregate(value))); groups.put(axis,aggregates);
        }
        return groups;
    }
    static Aggregate aggregate(List<Case> cases) {
        var values=cases.stream().map(Case::metrics).filter(Objects::nonNull).toList();
        var latency=cases.stream().map(Case::latencyMs).filter(Objects::nonNull).toList();
        var chars=values.stream().map(v -> (double)v.summaryChars()).toList();
        return new Aggregate(values.size(),mean(values,MetadataMetrics.Result::precision),mean(values,MetadataMetrics.Result::recall),mean(values,MetadataMetrics.Result::f1),
                nullableMean(values.stream().map(MetadataMetrics.Result::visiblePrecision).toList()),nullableMean(values.stream().map(MetadataMetrics.Result::visibleRecall).toList()),
                nullableMean(values.stream().map(MetadataMetrics.Result::visibleF1).toList()),mean(values,MetadataMetrics.Result::genericTagRate),
                values.stream().mapToInt(MetadataMetrics.Result::forbiddenTagCount).sum(),mean(values,MetadataMetrics.Result::suggestedTagCount),
                mean(values,MetadataMetrics.Result::requiredConceptCoverage),nullableMean(values.stream().map(MetadataMetrics.Result::visibleConceptCoverage).toList()),
                mean(values,MetadataMetrics.Result::expectedCoverageCeiling),values.stream().mapToInt(MetadataMetrics.Result::forbiddenClaimCount).sum(),
                mean(values,MetadataMetrics.Result::summaryChars),percentile(chars,.5),percentile(chars,.95),nullableMean(latency),
                latency.isEmpty()?null:percentile(latency,.5),latency.isEmpty()?null:percentile(latency,.95));
    }
    static double percentile(List<Double> values,double p) { if(values.isEmpty()) return 0; var sorted=values.stream().sorted().toList(); return sorted.get(Math.max(0,(int)Math.ceil(p*sorted.size())-1)); }
    private static <T> double mean(List<T> values,ToDoubleFunction<T> f) { return values.stream().mapToDouble(f).average().orElse(0); }
    private static Double nullableMean(List<Double> values) { return values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null); }
    static Map<String,Double> compare(Report baseline,Report candidate) {
        if(!baseline.identity().corpusVersion().equals(candidate.identity().corpusVersion())
                || !baseline.identity().corpusFingerprint().equals(candidate.identity().corpusFingerprint())
                || !baseline.identity().metricsVersion().equals(candidate.identity().metricsVersion())
                || !baseline.identity().reportVersion().equals(candidate.identity().reportVersion())
                || baseline.metrics()==null || candidate.metrics()==null) throw new IllegalArgumentException("Incompatible or incomplete reports");
        return new TreeMap<>(Map.of("precision",candidate.metrics().precision()-baseline.metrics().precision(),
                "recall",candidate.metrics().recall()-baseline.metrics().recall(),"f1",candidate.metrics().f1()-baseline.metrics().f1(),
                "conceptCoverage",candidate.metrics().requiredConceptCoverage()-baseline.metrics().requiredConceptCoverage(),
                "genericTagRate",candidate.metrics().genericTagRate()-baseline.metrics().genericTagRate(),
                "forbiddenClaims",(double)candidate.metrics().forbiddenClaimCount()-baseline.metrics().forbiddenClaimCount()));
    }
}
