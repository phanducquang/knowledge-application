package com.knowledgeapplication.api.metadata.eval;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.StreamReadFeature;

/** Versioned evaluation overlay: note inputs and v1 resources are never edited. */
record MetadataCalibrationCorpus(String version,String metricsVersion,String reportVersion,
        String baseCorpusFingerprint,List<Decision> decisions) {
    static final String V1_FINGERPRINT="9f64eb96cf6b55b4ef62261ae755dff4a7b9895c5dec36c5bc8a1e4124b7d1db";
    static final JsonMapper JSON=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    enum Type { TAG_ALIAS, REMOVE_TAG_ALIAS, CONCEPT_PHRASE, GENERIC_TAG_ADDITION, TAG_TIER }
    enum Tier { EXACT_CANONICAL, ACCEPTED_EQUIVALENT, RELEVANT_BUT_BROADER, RELEVANT_ADDITIONAL, IRRELEVANT_UNSUPPORTED }
    record Decision(String id,String caseId,Type type,String target,String value,String evidence,String reason,String confidence) {}
    static MetadataCalibrationCorpus load() {
        try(var stream=MetadataCalibrationCorpus.class.getResourceAsStream("/metadata-eval/calibration-v2.json")) {
            if(stream==null) throw new IllegalArgumentException("Missing calibration overlay");
            return JSON.readValue(stream,MetadataCalibrationCorpus.class);
        } catch(java.io.IOException ex) { throw new IllegalArgumentException("Invalid calibration overlay"); }
    }
    List<Decision> forCase(String id) { return decisions.stream().filter(d->d.caseId().equals(id) || d.caseId().equals("*")).toList(); }
    MetadataCorpus materialize(MetadataCorpus base) {
        require(base.version().equals("metadata-eval-v1") && base.fingerprint().equals(V1_FINGERPRINT)
                && base.fingerprint().equals(baseCorpusFingerprint),"Frozen v1 mismatch");
        require(version.equals("metadata-eval-v2") && metricsVersion.equals("metadata-metrics-v2")
                && reportVersion.equals("metadata-report-v3") && decisions!=null,"Invalid calibration identity");
        var ids=new HashSet<String>(); var edits=new HashSet<String>();
        for(var d:decisions) {
            require(d!=null && d.id()!=null && ids.add(d.id()) && d.type()!=null && nonblank(d.target()) && nonblank(d.value())
                    && nonblank(d.evidence()) && nonblank(d.reason()) && "HIGH".equals(d.confidence()),"Invalid decision");
            require(edits.add(d.caseId()+"/"+d.type()+"/"+d.target()+"/"+MetadataCorpus.normalize(d.value())),"Duplicate decision");
            if(d.type()==Type.TAG_TIER) require(edits.add(d.caseId()+"/tier-label/"+MetadataCorpus.normalize(d.value())),"Ambiguous tier label");
            if(d.type()==Type.GENERIC_TAG_ADDITION) { require(d.caseId().equals("*") && d.target().equals("genericTags"),"Invalid generic policy"); continue; }
            var f=base.fixtures().stream().filter(x->x.id().equals(d.caseId())).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown calibration case"));
            require(MetadataCorpus.contains(f.fullEvidence(),d.evidence()),"Decision missing fixture evidence");
            switch(d.type()) {
                case TAG_ALIAS, REMOVE_TAG_ALIAS -> {
                    var tag=f.expectedTags().stream().filter(t->t.canonical().equals(d.target())).findFirst().orElseThrow();
                    require(d.type()==Type.REMOVE_TAG_ALIAS?tag.aliases().contains(d.value()):!MetadataCorpus.matchesTag(d.value(),tag),"Invalid alias edit");
                }
                case CONCEPT_PHRASE -> require(f.concepts().stream().anyMatch(c->c.id().equals(d.target()) && !c.phrases().contains(d.value())),"Invalid concept edit");
                case TAG_TIER -> { var tier=Tier.valueOf(d.target()); require(tier!=Tier.EXACT_CANONICAL && tier!=Tier.ACCEPTED_EQUIVALENT,"Strict tiers come only from ground truth"); }
                default -> throw new IllegalArgumentException("Unsupported decision");
            }
        }
        var generic=new ArrayList<>(base.genericTags());
        decisions.stream().filter(d->d.type()==Type.GENERIC_TAG_ADDITION).forEach(d->{require(generic.stream().noneMatch(g->MetadataCorpus.normalize(g).equals(MetadataCorpus.normalize(d.value()))),"Duplicate generic tag");generic.add(d.value());});
        var fixtures=new ArrayList<MetadataCorpus.Fixture>();
        for(var f:base.fixtures()) {
            var editsForCase=forCase(f.id()); var tags=new ArrayList<MetadataCorpus.Tag>();
            for(var tag:f.expectedTags()) {
                var aliases=new ArrayList<>(tag.aliases());
                for(var d:editsForCase) if(d.target().equals(tag.canonical())) {
                    if(d.type()==Type.TAG_ALIAS) aliases.add(d.value());
                    if(d.type()==Type.REMOVE_TAG_ALIAS) aliases.remove(d.value());
                }
                tags.add(new MetadataCorpus.Tag(tag.canonical(),List.copyOf(aliases),tag.evidence()));
            }
            var concepts=new ArrayList<MetadataCorpus.Concept>();
            for(var c:f.concepts()) {
                var phrases=new ArrayList<>(c.phrases());
                editsForCase.stream().filter(d->d.type()==Type.CONCEPT_PHRASE && d.target().equals(c.id())).forEach(d->phrases.add(d.value()));
                concepts.add(new MetadataCorpus.Concept(c.id(),List.copyOf(phrases),c.evidence()));
            }
            fixtures.add(new MetadataCorpus.Fixture(f.id(),f.title(),f.summary(),f.content(),f.lateContent(),f.paddingBeforeLate(),f.paddingAfter(),
                    f.existingTags(),f.language(),f.category(),f.position(),List.copyOf(tags),List.copyOf(concepts),f.forbiddenClaims(),f.forbiddenTags(),f.notes()));
        }
        var result=new MetadataCorpus(version,List.copyOf(generic),List.copyOf(fixtures)); result.validate();
        for(var d:decisions) if(d.type()==Type.TAG_TIER) {
            var f=result.fixtures().stream().filter(x->x.id().equals(d.caseId())).findFirst().orElseThrow();
            require(f.expectedTags().stream().noneMatch(t->MetadataCorpus.matchesTag(d.value(),t)),"Tier rule overlaps strict ground truth");
        }
        return result;
    }
    String fingerprint(MetadataCorpus calibrated) {
        return MetadataResume.hash(JSON.writeValueAsString(this)+"\n"+calibrated.fingerprint()+"\n"+MetadataCalibration.SCORER_SEMANTICS);
    }
    private static boolean nonblank(String text) { return text!=null && !text.isBlank(); }
    private static void require(boolean valid,String message) { if(!valid) throw new IllegalArgumentException(message); }
}
