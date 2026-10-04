package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.metadata.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.SerializationFeature;

/** Pure offline re-scoring. No provider, credentials, Spring context or database can be constructed here. */
final class MetadataCalibration {
    static final String SCORER_SEMANTICS="calibration-scorer-v2;strict=explicit-canonical-or-alias;unique-concept-credit;NFKC-root-fold;"
            +"macro-means;visible-N/A-excluded;generic=exact-list;tiers=case-explicit-evidence;unknown=unsupported;human=null";
    record Frozen(MetadataEvaluation.Identity generationIdentity,boolean composite,String sourceReportFingerprint,
            String outputFingerprint,Map<String,MetadataSuggestion> suggestions) {}
    record Identity(String reportVersion,String metricsVersion,String corpusVersion,String corpusFingerprint,
            String evaluationFingerprint,String baseCorpusFingerprint,String scorerSemantics,String outputFingerprint) {}
    record TagReview(String tag,MetadataCalibrationCorpus.Tier tier,String canonical,String evidence,String reason,String decisionId,boolean generic) {}
    record Diagnostics(double relevantTagRate,double broadTagRate,double additionalRelevantTagRate,double unsupportedTagRate) {}
    record HumanSummary(Integer factuality,Integer coverage,Integer conciseness,Integer usefulness,Integer languageFit) {}
    record HumanTags(Integer specificity,Integer usefulness,Integer consistency) {}
    record HumanRubric(String authority,HumanSummary summary,HumanTags tags,String notes) {
        static HumanRubric blank() { return new HumanRubric("HUMAN_ONLY",new HumanSummary(null,null,null,null,null),new HumanTags(null,null,null),null); }
    }
    record Finding(String code,String reason) {}
    record View(List<MetadataCorpus.Tag> expectedTags,List<MetadataCorpus.Concept> concepts,List<String> genericTags,MetadataMetrics.Result metrics) {}
    record Case(String id,String language,String category,MetadataSuggestion unchangedSuggestion,View v1,View v2,
            List<TagReview> tagReviews,Diagnostics diagnostics,List<MetadataCalibrationCorpus.Decision> decisions,
            Map<String,Double> benchmarkCalibrationDelta,List<Finding> reviewAssistance,HumanRubric humanRubric) {}
    record Report(Identity identity,MetadataEvaluation.Identity generationIdentity,String sourceReportFingerprint,boolean generationComposite,
            String attribution,int cases,int providerCalls,int quotaReservations,MetadataEvaluation.Aggregate v1,MetadataEvaluation.Aggregate v2Strict,
            Map<String,Double> benchmarkCalibrationDelta,Diagnostics v2Diagnostics,List<Case> caseResults) {}
    static Frozen load(Path path) {
        try {
            if(!Files.isRegularFile(path) || Files.size(path)>2_000_000) throw new IllegalArgumentException();
            byte[] bytes; try(var input=Files.newInputStream(path)) { bytes=input.readNBytes(2_000_001); }
            if(bytes.length>2_000_000) throw new IllegalArgumentException();
            return parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        } catch(Exception ex) { throw new IllegalArgumentException("Invalid or incompatible completed synthetic baseline"); }
    }
    static Frozen parse(String text) {
        try {
            if(text.length()>2_000_000) throw new IllegalArgumentException();
            var root=MetadataCalibrationCorpus.JSON.readTree(text); var base=MetadataCorpus.load();
            var target=MetadataEvaluation.Identity.create(base,"gemini","gemini-3.5-flash-lite",32000,600,Instant.EPOCH);
            var reused=MetadataResume.parse(text,base,target);
            if(!"COMPLETE".equals(root.path("status").asString("")) || root.path("cases").size()!=20 || reused.reused().size()!=20
                    || root.path("cases").valueStream().anyMatch(c->!"VALID".equals(c.path("status").asString("")))) throw new IllegalArgumentException();
            var identity=MetadataCalibrationCorpus.JSON.treeToValue(root.path("identity"),MetadataEvaluation.Identity.class);
            var suggestions=new TreeMap<String,MetadataSuggestion>(); reused.reused().forEach((id,value)->suggestions.put(id,value.suggestion()));
            return new Frozen(identity,root.path("composite").asBoolean(false),MetadataResume.hash(text),
                    MetadataResume.hash(MetadataCalibrationCorpus.JSON.writeValueAsString(suggestions)),Collections.unmodifiableMap(suggestions));
        } catch(RuntimeException ex) { throw new IllegalArgumentException("Invalid or incompatible completed synthetic baseline"); }
    }
    static Report evaluate(Frozen frozen,MetadataCalibrationCorpus overlay) {
        var v1=MetadataCorpus.load(); var v2=overlay.materialize(v1);
        if(frozen.suggestions().size()!=20 || !frozen.generationIdentity().corpusFingerprint().equals(v1.fingerprint()))
            throw new IllegalArgumentException("Incomplete frozen outputs");
        var results=new ArrayList<Case>(); var oldCases=new ArrayList<MetadataEvaluation.Case>(); var newCases=new ArrayList<MetadataEvaluation.Case>();
        for(int i=0;i<v1.fixtures().size();i++) {
            var old=v1.fixtures().get(i); var now=v2.fixtures().get(i); var output=frozen.suggestions().get(old.id());
            if(output==null || !output.equals(output.validated(old.input(32000).currentTags()))) throw new IllegalArgumentException("Invalid frozen output");
            var oldMetrics=MetadataMetrics.score(old,output,v1.genericTags(),32000);
            var newMetrics=MetadataMetrics.score(now,output,v2.genericTags(),32000);
            var tags=classify(now,output,v2.genericTags(),overlay);
            var delta=delta(oldMetrics,newMetrics); var findings=findings(old,output,oldMetrics,newMetrics,tags);
            results.add(new Case(old.id(),old.language(),old.category(),output,new View(old.expectedTags(),old.concepts(),v1.genericTags(),oldMetrics),
                    new View(now.expectedTags(),now.concepts(),v2.genericTags(),newMetrics),tags,diagnostics(tags),overlay.forCase(old.id()),delta,findings,HumanRubric.blank()));
            oldCases.add(metricCase(old,output,oldMetrics)); newCases.add(metricCase(now,output,newMetrics));
        }
        var before=MetadataEvaluation.aggregate(oldCases); var after=MetadataEvaluation.aggregate(newCases);
        return new Report(new Identity(overlay.reportVersion(),overlay.metricsVersion(),v2.version(),v2.fingerprint(),overlay.fingerprint(v2),
                v1.fingerprint(),SCORER_SEMANTICS,frozen.outputFingerprint()),frozen.generationIdentity(),frozen.sourceReportFingerprint(),frozen.composite(),
                "BENCHMARK_CALIBRATION_DELTA_NOT_MODEL_IMPROVEMENT",20,0,0,before,after,aggregateDelta(before,after),
                new Diagnostics(results.stream().mapToDouble(c->c.diagnostics().relevantTagRate()).average().orElseThrow(),
                        results.stream().mapToDouble(c->c.diagnostics().broadTagRate()).average().orElseThrow(),
                        results.stream().mapToDouble(c->c.diagnostics().additionalRelevantTagRate()).average().orElseThrow(),
                        results.stream().mapToDouble(c->c.diagnostics().unsupportedTagRate()).average().orElseThrow()),List.copyOf(results));
    }
    static List<TagReview> classify(MetadataCorpus.Fixture f,MetadataSuggestion output,List<String> generic,MetadataCalibrationCorpus overlay) {
        var tags=new ArrayList<TagReview>();
        for(var value:output.tags()) {
            boolean isGeneric=generic.stream().anyMatch(g->MetadataCorpus.normalize(g).equals(MetadataCorpus.normalize(value)));
            var exact=f.newTags().stream().filter(t->MetadataCorpus.matchesTag(value,t)).findFirst();
            if(exact.isPresent()) { var t=exact.get(); boolean canonical=MetadataCorpus.normalize(value).equals(MetadataCorpus.normalize(t.canonical()));
                tags.add(new TagReview(value,canonical?MetadataCalibrationCorpus.Tier.EXACT_CANONICAL:MetadataCalibrationCorpus.Tier.ACCEPTED_EQUIVALENT,
                        t.canonical(),t.evidence(),"Explicit required canonical/equivalent tag",null,isGeneric)); continue; }
            var decision=overlay.forCase(f.id()).stream().filter(d->d.type()==MetadataCalibrationCorpus.Type.TAG_TIER
                    && MetadataCorpus.normalize(d.value()).equals(MetadataCorpus.normalize(value))).findFirst();
            tags.add(decision.map(d->new TagReview(value,MetadataCalibrationCorpus.Tier.valueOf(d.target()),null,d.evidence(),d.reason(),d.id(),isGeneric))
                    .orElseGet(()->new TagReview(value,MetadataCalibrationCorpus.Tier.IRRELEVANT_UNSUPPORTED,null,null,
                            "No explicit source-backed rule; unsupported/unreviewed, not proof of irrelevance",null,isGeneric)));
        }
        return List.copyOf(tags);
    }
    static Diagnostics diagnostics(List<TagReview> tags) {
        long broad=tags.stream().filter(t->t.tier()==MetadataCalibrationCorpus.Tier.RELEVANT_BUT_BROADER).count();
        long additional=tags.stream().filter(t->t.tier()==MetadataCalibrationCorpus.Tier.RELEVANT_ADDITIONAL).count();
        long unsupported=tags.stream().filter(t->t.tier()==MetadataCalibrationCorpus.Tier.IRRELEVANT_UNSUPPORTED).count();
        return new Diagnostics(MetadataMetrics.ratio(tags.size()-unsupported,tags.size()),MetadataMetrics.ratio(broad,tags.size()),
                MetadataMetrics.ratio(additional,tags.size()),MetadataMetrics.ratio(unsupported,tags.size()));
    }
    private static List<Finding> findings(MetadataCorpus.Fixture f,MetadataSuggestion output,MetadataMetrics.Result old,MetadataMetrics.Result now,List<TagReview> tags) {
        var findings=new ArrayList<Finding>();
        if(old.precision()!=now.precision() || old.recall()!=now.recall()) findings.add(new Finding("BENCHMARK_ALIAS_CALIBRATION","Explicit aliases changed strict matches, not generated output"));
        if(old.requiredConceptCoverage()!=now.requiredConceptCoverage()) findings.add(new Finding("BENCHMARK_PHRASE_MATCH_MISS","Explicit equivalent phrase changes deterministic coverage"));
        if(!now.invisibleConcepts().isEmpty() || !now.invisibleExpectedTags().isEmpty()) findings.add(new Finding("INPUT_LIMIT_32K","Late ground truth retained in full-note metrics; not model omission of supplied evidence"));
        boolean visibleMiss=now.visibleExpectedTags().stream().anyMatch(t->!now.observedTags().contains(t));
        if(visibleMiss) findings.add(new Finding("MODEL_MISS","Some visible required strict tags are not recovered; broader/additional labels do not erase the miss"));
        if(tags.stream().anyMatch(t->t.tier()==MetadataCalibrationCorpus.Tier.RELEVANT_BUT_BROADER))
            findings.add(new Finding(visibleMiss?"MODEL_UNDER_SPECIFIC_TAG":"RELEVANT_BROADER_TAG",
                    "Broad label diagnostic; unavailable late evidence is never classified as a model specificity miss"));
        if(tags.stream().anyMatch(t->t.tier()==MetadataCalibrationCorpus.Tier.RELEVANT_ADDITIONAL)) findings.add(new Finding("RELEVANT_ADDITIONAL_TAG","Required taxonomy is not an exhaustive list of useful source-supported tags"));
        if(tags.stream().anyMatch(t->t.tier()==MetadataCalibrationCorpus.Tier.IRRELEVANT_UNSUPPORTED)) findings.add(new Finding("UNSUPPORTED_OR_UNREVIEWED_TAG","No strict or accepted relevant rule; human review needed"));
        if(!f.language().equals("en") && output.summary().codePoints().allMatch(c->c<128)) findings.add(new Finding("LANGUAGE_MISMATCH_CANDIDATE","ASCII-only prose for a Vietnamese/mixed fixture is a review cue, not language detection or an automatic error"));
        findings.add(new Finding("NEEDS_HUMAN_REVIEW","All rubric fields are HUMAN_ONLY and blank; diagnostics are not quality certification"));
        return List.copyOf(findings);
    }
    private static MetadataEvaluation.Case metricCase(MetadataCorpus.Fixture f,MetadataSuggestion s,MetadataMetrics.Result m) {
        var input=f.input(32000);
        return new MetadataEvaluation.Case(f.id(),f.title(),f.language(),f.category(),f.lengthGroup(),f.position(),f.markdown().length(),input.content().length(),
                input.contentTruncated(),"VALID",null,0,s,m,f.concepts(),f.expectedTags(),f.existingTags(),f.notes());
    }
    static Map<String,Double> delta(MetadataMetrics.Result a,MetadataMetrics.Result b) {
        var result=new TreeMap<String,Double>(); result.put("strictPrecision",b.precision()-a.precision()); result.put("strictRecall",b.recall()-a.recall());
        result.put("strictF1",b.f1()-a.f1()); result.put("fullConceptCoverage",b.requiredConceptCoverage()-a.requiredConceptCoverage());
        if(a.visiblePrecision()!=null && b.visiblePrecision()!=null) {
            result.put("visibleStrictPrecision",b.visiblePrecision()-a.visiblePrecision()); result.put("visibleStrictRecall",b.visibleRecall()-a.visibleRecall());
            result.put("visibleStrictF1",b.visibleF1()-a.visibleF1());
        }
        if(a.visibleConceptCoverage()!=null && b.visibleConceptCoverage()!=null) result.put("visibleConceptCoverage",b.visibleConceptCoverage()-a.visibleConceptCoverage());
        result.put("genericTagRate",b.genericTagRate()-a.genericTagRate()); return Collections.unmodifiableMap(result);
    }
    private static Map<String,Double> aggregateDelta(MetadataEvaluation.Aggregate a,MetadataEvaluation.Aggregate b) {
        var result=new TreeMap<String,Double>(); result.put("strictPrecision",b.precision()-a.precision()); result.put("strictRecall",b.recall()-a.recall());
        result.put("strictF1",b.f1()-a.f1()); result.put("visibleStrictPrecision",b.visiblePrecision()-a.visiblePrecision());
        result.put("visibleStrictRecall",b.visibleRecall()-a.visibleRecall()); result.put("visibleStrictF1",b.visibleF1()-a.visibleF1());
        result.put("fullConceptCoverage",b.requiredConceptCoverage()-a.requiredConceptCoverage()); result.put("visibleConceptCoverage",b.visibleConceptCoverage()-a.visibleConceptCoverage());
        result.put("genericTagRate",b.genericTagRate()-a.genericTagRate()); result.put("forbiddenTagCount",(double)b.forbiddenTagCount()-a.forbiddenTagCount());
        result.put("forbiddenClaimCount",(double)b.forbiddenClaimCount()-a.forbiddenClaimCount()); return Collections.unmodifiableMap(result);
    }
    static String json(Report report) {
        return MetadataCalibrationCorpus.JSON.rebuild().enable(SerializationFeature.INDENT_OUTPUT).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build().writeValueAsString(report)+"\n";
    }
    static String review(Report report) {
        var out=new StringBuilder("# Metadata calibration v2 — OFFLINE review assistance\n\nSame frozen outputs, benchmark calibration deltas NOT model improvement. No human ratings inferred. Full/visible ground truth remain separate; zero forbidden flags do not prove zero hallucination.\n\n");
        out.append("Generation: ").append(report.generationIdentity().model()).append(" / ").append(report.generationIdentity().corpusVersion()).append("; composite: ").append(report.generationComposite()).append("\n\n")
                .append("Evaluation: ").append(report.identity().corpusVersion()).append(" / ").append(report.identity().metricsVersion()).append(" / ").append(report.identity().reportVersion()).append("\n\n")
                .append("Evaluation fingerprint: ").append(report.identity().evaluationFingerprint()).append("\n\nOutput fingerprint: ").append(report.identity().outputFingerprint()).append("\n\n")
                .append("v1 P/R/F1: ").append(report.v1().precision()).append(" / ").append(report.v1().recall()).append(" / ").append(report.v1().f1()).append("\n\nv2 STRICT P/R/F1: ")
                .append(report.v2Strict().precision()).append(" / ").append(report.v2Strict().recall()).append(" / ").append(report.v2Strict().f1()).append("\n\nDeltas: ").append(report.benchmarkCalibrationDelta()).append("\n\n");
        var a=report.v1(); var b=report.v2Strict();
        out.append("| Macro metric | v1 | v2 strict |\n| --- | ---: | ---: |\n");
        row(out,"Tag precision",a.precision(),b.precision()); row(out,"Tag recall",a.recall(),b.recall()); row(out,"Tag F1",a.f1(),b.f1());
        row(out,"Visible tag precision",a.visiblePrecision(),b.visiblePrecision()); row(out,"Visible tag recall",a.visibleRecall(),b.visibleRecall()); row(out,"Visible tag F1",a.visibleF1(),b.visibleF1());
        row(out,"Full concept coverage",a.requiredConceptCoverage(),b.requiredConceptCoverage()); row(out,"Visible concept coverage",a.visibleConceptCoverage(),b.visibleConceptCoverage());
        row(out,"Generic tag rate",a.genericTagRate(),b.genericTagRate()); row(out,"Forbidden tags",a.forbiddenTagCount(),b.forbiddenTagCount()); row(out,"Forbidden claims",a.forbiddenClaimCount(),b.forbiddenClaimCount());
        out.append("\nSeparate source-relevance diagnostics (NOT strict precision or human usefulness): ").append(report.v2Diagnostics()).append("\n\n");
        for(var c:report.caseResults()) {
            out.append("## ").append(c.id()).append(" — ").append(c.language()).append(" / ").append(c.category()).append("\n\n")
                    .append("Summary (unchanged):\n\n> ").append(escape(c.unchangedSuggestion().summary()).replace("\n","\n> ")).append("\n\nTags (unchanged): ").append(escape(c.unchangedSuggestion().tags().toString())).append("\n\n")
                    .append("```json\n").append(MetadataCalibrationCorpus.JSON.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build().writeValueAsString(c)).append("\n```\n\n")
                    .append("HUMAN ONLY, 1–5 (1 poor, 3 usable with edits, 5 strong); no automatic score:\n\nSummary factuality ___; coverage ___; conciseness ___; usefulness ___; language fit ___\n\nTag specificity ___; usefulness ___; consistency ___\n\nHuman notes: ___\n\n");
        }
        return out.toString();
    }
    private static void row(StringBuilder out,String name,Object before,Object after) { out.append("| ").append(name).append(" | ").append(before).append(" | ").append(after).append(" |\n"); }
    static void write(Report report,Path directory,Path source) throws java.io.IOException {
        Files.createDirectories(directory);
        for(String file:List.of("calibration-v2.json","calibration-v2.txt","calibration-review.md"))
            if(Files.exists(directory.resolve(file)) && Files.isSameFile(directory.resolve(file),source)) throw new IllegalArgumentException("Source report cannot be overwritten");
        Files.writeString(directory.resolve("calibration-v2.json"),json(report));
        Files.writeString(directory.resolve("calibration-v2.txt"),review(report));
        Files.writeString(directory.resolve("calibration-review.md"),review(report));
    }
    private static String escape(String text) { return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("`","\\`"); }
}
