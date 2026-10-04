package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.knowledge.embedding.MarkdownChunker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.StreamReadFeature;

record RetrievalCorpus(String version, String description, List<Note> notes, List<Query> queries) {
    record Note(String slug, String title, String summary, String language, String content, String contentResource) {
        String markdown() {
            if (content != null && !content.isBlank() && contentResource == null) return content;
            if (content == null && contentResource != null && contentResource.matches("[a-z0-9-]+\\.md")) return resource(contentResource);
            throw new IllegalArgumentException("Exactly one synthetic Markdown source is required");
        }
    }
    record ChunkTruth(String slug, String marker, String position) {
        ChunkTruth(String slug, String marker) { this(slug, marker, null); }
    }
    record Query(String id, String query, String category, String language, String rationale,
            boolean negative, List<String> relevantKnowledge, List<ChunkTruth> relevantChunks) {}
    static RetrievalCorpus load() {
        return load("corpus.json");
    }
    static RetrievalCorpus load(String resourceName) {
        var corpus = parse(resourceName);
        corpus.validate(new MarkdownChunker(4000, 200));
        return corpus;
    }
    static RetrievalCorpus parse(String resourceName) {
        var mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        return mapper.readValue(resource(resourceName), RetrievalCorpus.class);
    }
    static String resource(String name) {
        try (var input = RetrievalCorpus.class.getResourceAsStream("/retrieval-eval/" + name)) {
            if (input == null) throw new IllegalArgumentException("Missing synthetic fixture resource");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) { throw new IllegalArgumentException("Cannot load synthetic fixture", ex); }
    }
    void validate(MarkdownChunker chunker) {
        require(version != null && !version.isBlank() && description != null && !description.isBlank(), "Missing corpus identity");
        require(notes != null && !notes.isEmpty() && queries != null && !queries.isEmpty(), "Empty evaluation corpus");
        Map<String, Note> bySlug = new LinkedHashMap<>();
        for (var note : notes) {
            require(note != null && note.slug() != null && note.slug().matches("[a-z0-9]+(?:-[a-z0-9]+)*") && note.slug().length() <= 200,
                    "Invalid fixture slug");
            require(note.title() != null && !note.title().isBlank() && note.title().length() <= 255
                    && note.summary() != null && note.language() != null && Set.of("en", "vi").contains(note.language()), "Invalid fixture metadata");
            require(bySlug.putIfAbsent(note.slug(), note) == null, "Duplicate note slug");
            require(!chunker.chunk(note.markdown()).isEmpty(), "Empty fixture Markdown");
        }
        var ids = new HashSet<String>();
        for (var query : queries) {
            require(query != null && query.id() != null && query.id().matches("q-[a-z0-9-]+") && ids.add(query.id()), "Invalid/duplicate query ID");
            require(query.query() != null && !query.query().isBlank() && query.query().length() <= 200, "Invalid query");
            require(query.category() != null && query.language() != null && Set.of("exact_keyword", "semantic_paraphrase", "technical_synonym", "configuration", "code", "ambiguous",
                    "multi_topic", "negative", "bilingual").contains(query.category())
                    && Set.of("en", "vi", "vi->en", "en->vi").contains(query.language())
                    && query.rationale() != null && !query.rationale().isBlank(), "Invalid reporting metadata");
            require(query.relevantKnowledge() != null && query.relevantChunks() != null, "Missing ground truth");
            require(query.negative() == query.relevantKnowledge().isEmpty() && query.negative() == query.category().equals("negative"),
                    "Positive queries require ground truth; negative queries must be marked explicitly");
            require(new HashSet<>(query.relevantKnowledge()).size() == query.relevantKnowledge().size(), "Duplicate note ground truth");
            for (var slug : query.relevantKnowledge()) require(bySlug.containsKey(slug), "Unknown expected slug");
            require(new HashSet<>(query.relevantChunks()).size() == query.relevantChunks().size(), "Duplicate chunk ground truth");
            for (var truth : query.relevantChunks()) {
                require(truth != null && query.relevantKnowledge().contains(truth.slug()) && truth.marker() != null
                        && truth.marker().matches("[a-z0-9]+(?:-[a-z0-9]+)*"), "Malformed expected chunk marker");
                require(chunker.chunk(bySlug.get(truth.slug()).markdown()).stream()
                        .anyMatch(chunk -> chunk.text().contains("[eval:" + truth.marker() + "]")), "Unresolvable chunk marker");
                if (truth.position() != null) {
                    require(Set.of("early", "middle", "late").contains(truth.position()), "Invalid chunk-position declaration");
                    var baseline = new MarkdownChunker(4000, 200).chunk(bySlug.get(truth.slug()).markdown());
                    int index = baseline.stream().filter(c -> c.text().contains("[eval:" + truth.marker() + "]"))
                            .findFirst().orElseThrow().index();
                    require(position(index, baseline.size()).equals(truth.position()), "Chunk-position declaration differs from baseline anchor");
                }
            }
        }
    }
    static String position(int index, int count) {
        if (count < 1 || index < 0 || index >= count) throw new IllegalArgumentException("Invalid chunk position/count");
        return List.of("early", "middle", "late").get(index * 3 / count);
    }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }
}
