package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.metadata.*;
import java.nio.file.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

/** Untrusted report loader. Identity first, only validated current-fixture suggestions are reused. */
final class MetadataResume {
    private static final JsonMapper JSON=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    record Reused(MetadataSuggestion suggestion,long estimatedInputTokens,String evaluatedAt,String generationRunId) {}
    record Plan(Map<String,Reused> reused,String fingerprint) {
        static Plan empty() { return new Plan(Map.of(),null); }
    }
    static String hash(String text) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    static String hashIdentity(MetadataEvaluation.Identity identity) { return hash(JSON.writeValueAsString(identity)); }
    static String inputFingerprint(KnowledgeMetadataSuggestionClient.Request input) { return hash(JSON.writeValueAsString(input)); }
    static Plan load(Path path,MetadataCorpus corpus,MetadataEvaluation.Identity target) {
        try {
            if(!Files.isRegularFile(path) || Files.size(path)>2_000_000) throw new IllegalArgumentException();
            try(var input=Files.newInputStream(path)) {
                byte[] bytes=input.readNBytes(2_000_001);
                if(bytes.length>2_000_000) throw new IllegalArgumentException();
                return parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),corpus,target);
            }
        } catch(Exception ex) { throw new MetadataUnavailableException(MetadataFailureCategory.CONFIGURATION); }
    }
    static Plan parse(String text,MetadataCorpus corpus,MetadataEvaluation.Identity target) {
        try {
            if(text.length()>2_000_000) throw new IllegalArgumentException();
            var root=JSON.readTree(text);
            var id=root.path("identity"); String version=id.path("reportVersion").asString("");
            if(!Set.of("metadata-report-v1","metadata-report-v2").contains(version) || !root.path("cases").isArray()
                    || root.path("cases").size()>corpus.fixtures().size()
                    || !Set.of("INCOMPLETE","COMPLETE").contains(root.path("status").asString(""))) throw new IllegalArgumentException();
            var current=JSON.valueToTree(target);
            for(String field:List.of("corpusVersion","corpusFingerprint","provider","model","sdkVersion","promptVersion","promptFingerprint",
                    "schemaVersion","schemaFingerprint","inputStrategyVersion","contentLimit","summaryLimit","tagCountLimit","tagLengthLimit","maxOutputTokens"))
                if(!id.path(field).equals(current.path(field))) throw new IllegalArgumentException();
            String evaluatedAt=id.path("evaluatedAt").asString(""); java.time.Instant.parse(evaluatedAt);
            String rootRunId=version.equals("metadata-report-v1")?hash(id.toString()):root.path("runId").asString("");
            if(!rootRunId.matches("[a-f0-9]{64}")) throw new IllegalArgumentException();
            var fixtures=new HashMap<String,MetadataCorpus.Fixture>(); corpus.fixtures().forEach(f->fixtures.put(f.id(),f));
            var seen=new HashSet<String>(); var reused=new LinkedHashMap<String,Reused>();
            for(var c:root.path("cases")) {
                String fixtureId=c.path("id").asString(""); var f=fixtures.get(fixtureId);
                if(f==null || !seen.add(fixtureId)) throw new IllegalArgumentException();
                if(!"VALID".equals(c.path("status").asString(""))) continue;
                var input=f.input(target.contentLimit());
                if(!f.title().equals(c.path("title").asString("")) || !f.language().equals(c.path("language").asString(""))
                        || !f.category().equals(c.path("category").asString("")) || !f.lengthGroup().equals(c.path("lengthGroup").asString(""))
                        || !f.position().equals(c.path("position").asString("")) || c.path("fullContentChars").asInt(-1)!=f.markdown().length()
                        || c.path("visibleContentChars").asInt(-1)!=input.content().length() || !c.path("truncated").isBoolean()
                        || c.path("truncated").asBoolean()!=input.contentTruncated() || !c.path("existingTags").equals(JSON.valueToTree(f.existingTags()))
                        || !c.path("conceptGroundTruth").equals(JSON.valueToTree(f.concepts())) || !c.path("tagGroundTruth").equals(JSON.valueToTree(f.expectedTags())))
                    throw new IllegalArgumentException();
                if(version.equals("metadata-report-v2") && !inputFingerprint(input).equals(c.path("inputFingerprint").asString(""))) throw new IllegalArgumentException();
                try {
                    var output=c.path("suggestion");
                    if(!output.isObject() || output.size()!=2 || !output.path("summary").isString() || !output.path("tags").isArray()) continue;
                    var tags=new ArrayList<String>();
                    for(var tag:output.path("tags")) { if(!tag.isString()) throw new IllegalArgumentException(); tags.add(tag.asString()); }
                    var suggestion=new MetadataSuggestion(output.path("summary").asString(),List.copyOf(tags));
                    var validated=suggestion.validated(input.currentTags());
                    if(!validated.equals(suggestion)) continue;
                    long cost=c.path("estimatedInputTokens").asLong(-1); if(cost<1 || cost>150000) continue;
                    String generationId=version.equals("metadata-report-v1")?rootRunId:c.path("generationRunId").asString("");
                    if(!generationId.matches("[a-f0-9]{64}")) continue;
                    String generationAt=version.equals("metadata-report-v2") && "REUSED_FROM_PRIOR_RUN".equals(c.path("provenance").asString(""))
                            ? c.path("sourceEvaluatedAt").asString(""):evaluatedAt;
                    java.time.Instant.parse(generationAt);
                    reused.put(fixtureId,new Reused(validated,cost,generationAt,generationId));
                } catch(RuntimeException invalidSuggestion) { /* Invalid/malformed outputs are outstanding, never reused. */ }
            }
            return new Plan(Collections.unmodifiableMap(reused),hash(text));
        } catch(RuntimeException ex) { throw new MetadataUnavailableException(MetadataFailureCategory.CONFIGURATION); }
    }
}
