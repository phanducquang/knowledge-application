package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.knowledge.embedding.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** Test-only evaluator. Every rank originates in the production repository's PostgreSQL queries. */
final class RetrievalEvaluation {
    static final List<Integer> SEMANTIC_K = List.of(1, 3, 5);
    static final List<Integer> RAG_K = List.of(1, 3, 5, 8);
    static final int SEMANTIC_LIMIT = 20, RAG_LIMIT = 8, PER_NOTE = 2;
    record Header(String corpusVersion, String description, int noteCount, int queryCount, int positiveQueries,
            int negativeQueries, int chunkGroundTruthQueries, int currentChunkCount, int isolationDecoyCount) {}
    record Configuration(String provider, String model, int dimensions, String embeddingAlgorithm,
            int chunkerVersion, int maxChunkChars, int overlapChars, String sourceHashStrategy,
            int semanticLimit, int ragLimit, int perNoteCap, List<Integer> semanticK, List<Integer> ragK,
            String measurementScope, String negativePolicy, String chunkTruthPolicy, String rankPolicy) {}
    record Ranked(int rank, String slug, int chunkIndex, double distance) {
        String chunkId() { return slug + "#" + chunkIndex; }
    }
    record CaseResult(RetrievalCorpus.Query query, List<String> expectedChunks, List<Ranked> ranked,
            RetrievalMetrics.Score noteScore, RetrievalMetrics.Score chunkScore,
            Map<String, Integer> relevantNoteRanks, Map<String, Integer> relevantChunkRanks,
            Map<Integer, List<String>> missingNotesAtK, Map<Integer, List<String>> missingChunksAtK,
            List<String> duplicateNotes, int uniqueNoteCount, int retrievedChunks, double diversity,
            List<String> sourcesAtPerNoteCap) {}
    record Summary(int queryCount, int positiveSamples, int negativeSamples, int chunkSamples,
            RetrievalMetrics.Score noteMetrics, RetrievalMetrics.Score chunkMetrics,
            double meanUniqueNoteCount, double meanRetrievedChunks, double meanDiversity,
            double meanSourcesAtPerNoteCap, int duplicateNoteCases) {}
    record Mode(Summary overall, Map<String, Summary> byCategory, Map<String, Summary> byLanguage, List<CaseResult> cases) {}
    record Report(int schemaVersion, Instant evaluatedAt, Header corpus, Configuration configuration,
            Mode semanticSearch, Mode ragContext) {
        // Timestamp is metadata, excluded from repeatability comparisons.
        Report deterministicIdentity() { return new Report(schemaVersion, Instant.EPOCH, corpus, configuration, semanticSearch, ragContext); }
    }

    private final KnowledgeEmbeddingRepository repository;
    private final EmbeddingStrategy strategy;
    private final EmbeddingClient embeddings;
    private final UUID owner;
    private final RetrievalCorpus corpus;
    private final Map<Long, String> slugs;
    private final Map<String, List<MarkdownChunker.Chunk>> chunks;

    RetrievalEvaluation(KnowledgeEmbeddingRepository repository, EmbeddingStrategy strategy, EmbeddingClient embeddings,
            UUID owner, RetrievalCorpus corpus, Map<Long, String> slugs, Map<String, List<MarkdownChunker.Chunk>> chunks) {
        this.repository = repository; this.strategy = strategy; this.embeddings = embeddings;
        this.owner = owner; this.corpus = corpus; this.slugs = slugs; this.chunks = chunks;
        if (strategy.properties().dimensions() != OfflineEmbeddingClient.DIMENSIONS || SEMANTIC_LIMIT < 5
                || RAG_LIMIT < 8 || PER_NOTE < 1 || chunks.values().stream().mapToInt(List::size).sum() > 100) {
            throw new IllegalArgumentException("Impossible evaluation configuration / distance diagnostic horizon");
        }
    }

    Report evaluate(Instant timestamp, int decoys) {
        var semantic = new ArrayList<CaseResult>(); var rag = new ArrayList<CaseResult>();
        for (var query : corpus.queries()) {
            float[] vector = embeddings.embed(List.of(query.query())).get(0);
            // Diagnostics ONLY. Never sort/filter the Semantic/RAG ranks using this separate query.
            var distances = new HashMap<String, Double>();
            for (var chunk : repository.findNearestChunks(owner, strategy, vector, 100)) {
                var slug = slugs.get(chunk.knowledgeId());
                if (slug == null) throw new IllegalStateException("Isolation decoy participated in retrieval");
                distances.put(slug + "#" + chunk.chunkIndex(), chunk.distance());
            }
            var notes = repository.findNearestKnowledge(owner, strategy, vector, SEMANTIC_LIMIT);
            var noteRanks = new ArrayList<Ranked>();
            for (var note : notes) noteRanks.add(ranked(noteRanks.size() + 1, note.slug(), note.chunkIndex(), distances));
            semantic.add(result(query, noteRanks, SEMANTIC_K, true));
            var context = repository.findRagChunks(owner, strategy, vector, RAG_LIMIT, PER_NOTE);
            var contextRanks = new ArrayList<Ranked>();
            for (var chunk : context) contextRanks.add(ranked(contextRanks.size() + 1, chunk.slug(), chunk.chunkIndex(), distances));
            rag.add(result(query, contextRanks, RAG_K, false));
        }
        var p = strategy.properties();
        return new Report(1, timestamp,
                new Header(corpus.version(), corpus.description(), corpus.notes().size(), corpus.queries().size(),
                        (int) corpus.queries().stream().filter(q -> !q.negative()).count(),
                        (int) corpus.queries().stream().filter(RetrievalCorpus.Query::negative).count(),
                        (int) corpus.queries().stream().filter(q -> !q.relevantChunks().isEmpty()).count(),
                        chunks.values().stream().mapToInt(List::size).sum(), decoys),
                new Configuration("offline", p.model(), p.dimensions(), "binary controlled vocabulary/synonyms, L2 normalized; unknown bias 0.05",
                        strategy.chunkerVersion(), p.maxChunkChars(), p.overlapChars(), strategy.marker(),
                        SEMANTIC_LIMIT, RAG_LIMIT, PER_NOTE, SEMANTIC_K, RAG_K,
                        "Retrieval correctness under controlled embeddings; provider semantic quality NOT measured; no answer generation/entailment scoring",
                        "Negatives excluded from recall/MRR means; nearest results and cosine distances are diagnostics, not no-result expectations",
                        "Declared markers resolve to ALL actual default-chunker chunks containing the marker; missing chunk truth is unscored",
                        "Semantic deduplicated before rank metrics; MRR@20. RAG note coverage uses raw chunk positions with unique relevant hits; RR horizon 8. Fixed note IDs/timestamps, production SQL tie-breaks"),
                mode(semantic, SEMANTIC_K), mode(rag, RAG_K));
    }

    private Ranked ranked(int rank, String slug, int chunk, Map<String, Double> distances) {
        var distance = distances.get(slug + "#" + chunk);
        if (distance == null || !Double.isFinite(distance)) throw new IllegalStateException("Missing PostgreSQL distance / ineligible source");
        return new Ranked(rank, slug, chunk, distance);
    }

    private CaseResult result(RetrievalCorpus.Query query, List<Ranked> ranked, List<Integer> ks, boolean semantic) {
        var expectedChunks = new TreeSet<String>();
        for (var truth : query.relevantChunks()) for (var chunk : chunks.get(truth.slug())) {
            if (chunk.text().contains("[eval:" + truth.marker() + "]")) expectedChunks.add(truth.slug() + "#" + chunk.index());
        }
        List<String> notes = ranked.stream().map(Ranked::slug).toList();
        List<String> chunkIds = ranked.stream().map(Ranked::chunkId).toList();
        var relevantNotes = new LinkedHashSet<>(query.relevantKnowledge());
        var noteScore = query.negative() ? null : RetrievalMetrics.score(notes, relevantNotes, ks, semantic);
        // Semantic is explicitly note-only; chunk-level metrics belong only to the RAG path.
        var chunkScore = semantic || expectedChunks.isEmpty() ? null : RetrievalMetrics.score(chunkIds, expectedChunks, ks, false);
        var seen = new HashSet<String>(); var duplicates = new TreeSet<String>();
        var counts = new TreeMap<String, Integer>();
        for (var note : notes) { if (!seen.add(note)) duplicates.add(note); counts.merge(note, 1, Integer::sum); }
        return new CaseResult(query, List.copyOf(expectedChunks), List.copyOf(ranked), noteScore, chunkScore,
                ranks(semantic ? new ArrayList<>(new LinkedHashSet<>(notes)) : notes, relevantNotes),
                semantic ? Map.of() : ranks(chunkIds, expectedChunks),
                missing(semantic ? new ArrayList<>(new LinkedHashSet<>(notes)) : notes, relevantNotes, ks),
                semantic ? Map.of() : missing(chunkIds, expectedChunks, ks), List.copyOf(duplicates), seen.size(), ranked.size(),
                ranked.isEmpty() ? 0 : (double) seen.size() / ranked.size(),
                semantic ? List.of() : counts.entrySet().stream().filter(e -> e.getValue() == PER_NOTE).map(Map.Entry::getKey).toList());
    }
    private static Map<String, Integer> ranks(List<String> retrieved, Set<String> relevant) {
        var ranks = new TreeMap<String, Integer>();
        for (var item : relevant) { int index = retrieved.indexOf(item); ranks.put(item, index < 0 ? null : index + 1); }
        return ranks;
    }
    private static Map<Integer, List<String>> missing(List<String> retrieved, Set<String> relevant, List<Integer> ks) {
        var missing = new TreeMap<Integer, List<String>>();
        for (int k : ks) {
            var top = new HashSet<>(retrieved.subList(0, Math.min(k, retrieved.size())));
            missing.put(k, relevant.stream().filter(r -> !top.contains(r)).sorted().toList());
        }
        return missing;
    }
    private static Mode mode(List<CaseResult> cases, List<Integer> ks) {
        return new Mode(summary(cases, ks), groups(cases, ks, c -> c.query().category()), groups(cases, ks, c -> c.query().language()), List.copyOf(cases));
    }
    private static Map<String, Summary> groups(List<CaseResult> cases, List<Integer> ks, Function<CaseResult, String> key) {
        var buckets = new TreeMap<String, List<CaseResult>>();
        cases.forEach(c -> buckets.computeIfAbsent(key.apply(c), ignored -> new ArrayList<>()).add(c));
        var summaries = new TreeMap<String, Summary>(); buckets.forEach((name, bucket) -> summaries.put(name, summary(bucket, ks)));
        return summaries;
    }
    private static Summary summary(List<CaseResult> cases, List<Integer> ks) {
        var noteScores = cases.stream().map(CaseResult::noteScore).filter(Objects::nonNull).toList();
        var chunkScores = cases.stream().map(CaseResult::chunkScore).filter(Objects::nonNull).toList();
        return new Summary(cases.size(), noteScores.size(), (int) cases.stream().filter(c -> c.query().negative()).count(),
                chunkScores.size(), RetrievalMetrics.mean(noteScores, ks), RetrievalMetrics.mean(chunkScores, ks),
                cases.stream().mapToInt(CaseResult::uniqueNoteCount).average().orElse(0),
                cases.stream().mapToInt(CaseResult::retrievedChunks).average().orElse(0),
                cases.stream().mapToDouble(CaseResult::diversity).average().orElse(0),
                cases.stream().mapToInt(c -> c.sourcesAtPerNoteCap().size()).average().orElse(0),
                (int) cases.stream().filter(c -> !c.duplicateNotes().isEmpty()).count());
    }
}
