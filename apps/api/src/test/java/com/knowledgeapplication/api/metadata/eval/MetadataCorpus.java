package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.metadata.*;
import java.text.Normalizer;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.StreamReadFeature;

record MetadataCorpus(String version, List<String> genericTags, List<Fixture> fixtures) {
    record Tag(String canonical, List<String> aliases, String evidence) {}
    record Concept(String id, List<String> phrases, String evidence) {}
    record Fixture(String id, String title, String summary, String content, String lateContent,
            int paddingBeforeLate, int paddingAfter, List<String> existingTags, String language, String category,
            String position, List<Tag> expectedTags, List<Concept> concepts,
            List<String> forbiddenClaims, List<String> forbiddenTags, String notes) {
        String markdown() { return content + padding(paddingBeforeLate,language) + lateContent + padding(paddingAfter,language); }
        KnowledgeMetadataSuggestionClient.Request input(int limit) {
            return MetadataSuggestionInputBuilder.build(title, summary, markdown(), existingTags, limit);
        }
        String fullEvidence() { return evidence(input(32000), markdown()); }
        String visibleEvidence(int limit) { var input = input(limit); return evidence(input, input.content()); }
        private static String evidence(KnowledgeMetadataSuggestionClient.Request input, String body) {
            return input.title()+"\n"+Objects.toString(input.summary(), "")+"\n"+body+"\n"+String.join("\n",input.currentTags());
        }
        String lengthGroup() { return markdown().length() <= 800 ? "short" : markdown().length() <= 32000 ? "medium" : "long"; }
        List<Tag> newTags() { return expectedTags.stream().filter(t -> existingTags.stream().noneMatch(e -> matchesTag(e,t))).toList(); }
    }
    // Explicit synthetic appendix. No imports of application Knowledge or any datasource.
    private static String padding(int chars,String language) {
        String paragraph = language.equals("en")
                ? "\n\n## Synthetic operations appendix\nRecord the observed outcome in the lab notebook. Keep the demonstration small and reversible. Compare the before and after result using the same example. This repeated checklist is background context, not a new design decision.\n"
                : "\n\n## Phụ lục vận hành tổng hợp\nGhi nhận kết quả quan sát trong sổ lab. Giữ bài thử nhỏ và có thể hoàn tác. So sánh kết quả trước và sau với cùng ví dụ tổng hợp. Checklist lặp này là ngữ cảnh tham khảo, không phải quyết định thiết kế mới.\n";
        return paragraph.repeat((chars+paragraph.length()-1)/paragraph.length()).substring(0, chars);
    }
    static String normalize(String text) {
        return Normalizer.normalize(text.strip(),Normalizer.Form.NFKC).toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
    }
    static boolean contains(String text, String phrase) { return normalize(text).contains(normalize(phrase)); }
    static boolean matchesTag(String value, Tag tag) {
        return normalize(value).equals(normalize(tag.canonical())) || tag.aliases().stream().anyMatch(a -> normalize(value).equals(normalize(a)));
    }
    static MetadataCorpus load() {
        try (var stream = MetadataCorpus.class.getResourceAsStream("/metadata-eval/corpus.json")) {
            if (stream == null) throw new IllegalArgumentException("Missing synthetic corpus");
            var json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
            var corpus=json.readValue(stream,MetadataCorpus.class); corpus.validate(); return corpus;
        } catch (java.io.IOException ex) { throw new IllegalArgumentException("Cannot load synthetic corpus"); }
    }
    String fingerprint() {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest((JsonMapper.builder().build().writeValueAsString(this)+"\n"+String.join("\n",fixtures.stream().map(Fixture::markdown).toList()))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    void validate() {
        require(version != null && version.matches("metadata-eval-v[0-9]+"), "Invalid corpus version");
        require(genericTags != null && !genericTags.isEmpty() && genericTags.stream().allMatch(MetadataCorpus::nonBlank), "Invalid generic tags");
        require(fixtures != null && !fixtures.isEmpty() && fixtures.size()<=20, "Invalid fixture count");
        var ids=new HashSet<String>();
        for (var f : fixtures) {
            require(f != null && nonBlank(f.id()) && ids.add(f.id()), "Duplicate or invalid fixture ID");
            require(nonBlank(f.title()) && f.title().length()<=255 && nonBlank(f.content()) && f.lateContent()!=null
                    && (f.summary()==null || f.summary().length()<=2000), "Invalid note text");
            require(f.paddingBeforeLate()>=0 && f.paddingBeforeLate()<=45000 && f.paddingAfter()>=0 && f.paddingAfter()<=45000, "Invalid padding");
            require(Set.of("en","vi","mixed-technical").contains(f.language()) && nonBlank(f.category())
                    && Set.of("early-important-facts","middle-important-facts","after-32k-important-facts","early-and-late-important-facts").contains(f.position()), "Invalid grouping");
            require(f.existingTags()!=null && f.existingTags().size()<=20 && f.existingTags().stream().allMatch(t -> nonBlank(t) && t.length()<=50), "Invalid existing tags");
            require(f.expectedTags()!=null && !f.expectedTags().isEmpty() && f.expectedTags().size()<=5, "Missing expected tags");
            var aliases=new HashSet<String>();
            for (var tag : f.expectedTags()) {
                require(tag!=null && nonBlank(tag.canonical()) && tag.canonical().length()<=50 && tag.aliases()!=null, "Invalid tag");
                for (var alias : java.util.stream.Stream.concat(java.util.stream.Stream.of(tag.canonical()),tag.aliases().stream()).toList())
                    require(nonBlank(alias) && alias.length()<=50 && aliases.add(normalize(alias)), "Invalid or ambiguous alias");
                require(nonBlank(tag.evidence()) && contains(f.fullEvidence(),tag.evidence()), "Missing tag evidence");
            }
            require(f.concepts()!=null && !f.concepts().isEmpty(), "Missing required concepts");
            var concepts=new HashSet<String>();
            for (var c : f.concepts()) require(c!=null && nonBlank(c.id()) && concepts.add(c.id()) && c.phrases()!=null
                    && !c.phrases().isEmpty() && c.phrases().stream().allMatch(MetadataCorpus::nonBlank)
                    && nonBlank(c.evidence()) && contains(f.fullEvidence(),c.evidence()), "Invalid required concept");
            require(f.forbiddenClaims()!=null && f.forbiddenClaims().stream().allMatch(MetadataCorpus::nonBlank)
                    && f.forbiddenTags()!=null && f.forbiddenTags().stream().allMatch(MetadataCorpus::nonBlank) && nonBlank(f.notes()), "Invalid diagnostics/rationale");
            require(f.forbiddenTags().stream().noneMatch(t -> aliases.contains(normalize(t))), "Forbidden expected tag");
        }
    }
    private static boolean nonBlank(String s) { return s!=null && !s.isBlank(); }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalArgumentException(message); }
}
