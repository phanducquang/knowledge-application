package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.knowledge.embedding.*;
import com.knowledgeapplication.api.ask.AskContext;
import com.knowledgeapplication.api.ask.AskProperties;
import java.time.Instant;
import java.util.*;

final class ChunkSelectionEvaluation {
    /** Query diagnostics reused ONLY inside an immutable strategy/index snapshot; final limited retrieval is never cached. */
    record QuerySnapshot(String strategyMarker, float[] vector, List<KnowledgeEmbeddingRepository.NearestChunk> nearest,
            Map<Integer,List<KnowledgeEmbeddingRepository.RagChunk>> capped) {}
    static Map<String,QuerySnapshot> snapshots(KnowledgeEmbeddingRepository repository, UUID owner, EmbeddingStrategy strategy,
            EmbeddingClient embeddings, RetrievalCorpus corpus) {
        var snapshots = new TreeMap<String,QuerySnapshot>();
        for (var q : corpus.queries()) {
            var vector = embeddings.embed(List.of(q.query())).get(0);
            var capped = new TreeMap<Integer,List<KnowledgeEmbeddingRepository.RagChunk>>();
            for (int cap : List.of(1,2,3)) capped.put(cap,repository.findRagChunks(owner,strategy,vector,100,cap));
            snapshots.put(q.id(),new QuerySnapshot(strategy.marker(),vector,repository.findNearestChunks(owner,strategy,vector,100),capped));
        }
        return snapshots;
    }
    record Variant(int maxChunkChars, int overlapChars, int ragLimit, int perNoteCap) {
        Variant {
            if (!Set.of(2000, 3000, 4000).contains(maxChunkChars) || overlapChars < 0 || overlapChars > maxChunkChars/4
                    || ragLimit < 1 || ragLimit > 100 || perNoteCap < 1 || perNoteCap > 10) throw new IllegalArgumentException("Invalid matrix variant");
        }
        String name() { return "c" + maxChunkChars + "-o" + overlapChars + "-l" + ragLimit + "-p" + perNoteCap; }
        boolean baseline() { return equals(new Variant(4000, 200, 8, 2)); }
    }
    record ExpectedChunk(String id, String sizeClass, String baselinePosition, String variantPosition,
            int rankWithinNote, Integer rankAfterCap, boolean selected, String exclusion) {}
    record CaseDiagnostic(String queryId, ChunkSelectionMetrics.Coverage coverage, List<ExpectedChunk> expected,
            List<String> missingNotes, long contextChars, int assembledChunks, int assembledSources,
            int assembledContextChars, Double assembledMarkerRecall) {}
    record Bucket(int expectedChunks, int selectedChunks, Double recall) {}
    record Diagnostics(int conditionalSamples, int conditionalExpectedChunks, Double supportingChunkRecallGivenRelevantNoteRetrieved,
            int noteHitChunkMissCount, List<String> noteHitChunkMissQueryIds, List<String> missingNoteQueryIds,
            int capPressureCases, List<String> capPressureQueryIds, Map<String, Integer> exclusionCounts,
            Map<String, Bucket> byBaselineSize, Map<String, Bucket> byBaselinePosition, Map<String, Bucket> byLongNotePosition, Map<String, Bucket> byVariantPosition,
            ChunkSelectionMetrics.Volume selectedCharacters, ChunkSelectionMetrics.Volume assembledCharacters,
            double meanAssembledChunks, double meanAssembledSources, double meanAssembledMarkerRecall) {}
    record IndexCost(int chunkCount, double meanChunksPerNote, int maxChunksPerNote, long totalEmbeddingInputChars,
            long estimatedInputTokens, int nativeBatchesAtDefault16) {}
    record Result(String name, boolean baseline, Variant configuration, String sourceHashStrategy, IndexCost indexingCost,
            RetrievalEvaluation.Summary metrics, Map<String, RetrievalEvaluation.Summary> byCategory,
            Map<String, RetrievalEvaluation.Summary> byLanguage, Diagnostics diagnostics, ChunkSelectionMetrics.Delta deltaFromBaseline,
            List<RetrievalEvaluation.CaseResult> cases, List<CaseDiagnostic> caseDiagnostics) {
        Result delta(Result base) {
            var delta = ChunkSelectionMetrics.delta(ChunkSelectionMetrics.recall(metrics.chunkMetrics(), 8), metrics.chunkMetrics().reciprocalRank(),
                    diagnostics.noteHitChunkMissCount(), diagnostics.selectedCharacters().mean(), metrics.meanDiversity(),
                    ChunkSelectionMetrics.recall(metrics.noteMetrics(), 8), ChunkSelectionMetrics.recall(base.metrics.chunkMetrics(), 8),
                    base.metrics.chunkMetrics().reciprocalRank(), base.diagnostics.noteHitChunkMissCount(),
                    base.diagnostics.selectedCharacters().mean(), base.metrics.meanDiversity(), ChunkSelectionMetrics.recall(base.metrics.noteMetrics(), 8));
            return new Result(name, baseline, configuration, sourceHashStrategy, indexingCost, metrics, byCategory, byLanguage, diagnostics, delta, cases, caseDiagnostics);
        }
    }
    record Report(int schemaVersion, Instant evaluatedAt, String corpusVersion, int noteCount, int queryCount,
            int addedLongNotes, Map<String, Integer> declaredPositionCases, String provider, String model, int dimensions,
            String scope, String policies, String recommendation, String bestObserved, Result baseline, List<Result> variants) {
        Report deterministicIdentity() { return new Report(schemaVersion, Instant.EPOCH, corpusVersion, noteCount, queryCount, addedLongNotes,
                declaredPositionCases, provider, model, dimensions, scope, policies, recommendation, bestObserved, baseline, variants); }
    }

    static List<Variant> matrix() {
        var variants = new ArrayList<Variant>();
        for (int[] chunk : List.of(new int[]{2000,150}, new int[]{3000,200}, new int[]{4000,200}, new int[]{4000,400}))
            for (int cap : List.of(1,2,3)) for (int limit : List.of(6,8,10,12)) variants.add(new Variant(chunk[0],chunk[1],limit,cap));
        return List.copyOf(variants);
    }
    static RetrievalCorpus extendedCorpus() {
        var original = RetrievalCorpus.load(); var additions = RetrievalCorpus.parse("chunk-selection-additions.json");
        var notes = new ArrayList<>(original.notes()); notes.addAll(additions.notes());
        var queries = new ArrayList<>(original.queries()); queries.addAll(additions.queries());
        var corpus = new RetrievalCorpus(original.version() + "+" + additions.version(), "Synthetic combined corpus; original 40 cases retained, 20 section-specific long-note queries added.", notes, queries);
        corpus.validate(new MarkdownChunker(4000,200)); return corpus;
    }
    static Result diagnose(Variant variant, EmbeddingStrategy strategy, RetrievalEvaluation.Mode evaluation,
            RetrievalCorpus corpus, Map<String,List<MarkdownChunker.Chunk>> chunks, Map<String,List<MarkdownChunker.Chunk>> baselineChunks,
            Map<Long,String> slugs, UUID owner, Map<String,QuerySnapshot> snapshots) {
        var cases = evaluation.cases(); var diagnostics = new ArrayList<CaseDiagnostic>();
        var props = new AskProperties(false, "http://127.0.0.1/offline-eval", "offline-no-generation", null, null, 1200,24000,
                variant.ragLimit(),variant.perNoteCap(),6);
        for (var c : cases) {
            var snapshot = snapshots.get(c.query().id());
            if (!snapshot.strategyMarker().equals(strategy.marker())) throw new IllegalArgumentException("Snapshot strategy mismatch");
            var all = snapshot.nearest();
            var ownRanks = new HashMap<String,Integer>(); var counts = new HashMap<String,Integer>();
            for (var item : all) {
                var slug = slugs.get(item.knowledgeId());
                if (slug == null) throw new IllegalStateException("Owner/currentness filter leaked a decoy");
                ownRanks.put(slug+"#"+item.chunkIndex(),counts.merge(slug,1,Integer::sum));
            }
            // Actual production SQL with relaxed TOTAL limit; used solely to attribute exclusions, never to rank final results.
            var capped = snapshot.capped().get(variant.perNoteCap());
            var capRanks = new HashMap<String,Integer>();
            for (int i=0;i<capped.size();i++) capRanks.put(capped.get(i).slug()+"#"+capped.get(i).chunkIndex(),i+1);
            // Hydrate text from the relaxed production query, in the actual limited-query order already recorded by the evaluator.
            var textById = new HashMap<String,KnowledgeEmbeddingRepository.RagChunk>();
            capped.forEach(r -> textById.put(r.slug()+"#"+r.chunkIndex(),r));
            var ids = c.ranked().stream().map(RetrievalEvaluation.Ranked::chunkId).toList();
            var retrieved = ids.stream().map(textById::get).toList();
            if (retrieved.stream().anyMatch(Objects::isNull)
                    || !ids.equals(capped.stream().limit(variant.ragLimit()).map(r -> r.slug()+"#"+r.chunkIndex()).toList()))
                throw new IllegalStateException("Production total-limit ordering differs from diagnostic prefix");
            var selected = new HashSet<>(ids); var expected = new ArrayList<ExpectedChunk>();
            for (var id : c.expectedChunks()) {
                var slug = ChunkSelectionMetrics.note(id); int index = Integer.parseInt(id.substring(id.lastIndexOf('#')+1));
                // Anchor position/classification fixed by baseline fixture; actual variant position is reported separately.
                var truth = c.query().relevantChunks().stream().filter(t -> t.slug().equals(slug)
                        && chunks.get(slug).get(index).text().contains("[eval:"+t.marker()+"]")).findFirst().orElseThrow();
                int anchor = baselineChunks.get(slug).stream().filter(p -> p.text().contains("[eval:"+truth.marker()+"]")).findFirst().orElseThrow().index();
                int rank = ownRanks.get(id); Integer afterCap = capRanks.get(id);
                String reason = selected.contains(id) ? "SELECTED" : rank > variant.perNoteCap() ? "PER_NOTE_CAP" : "TOTAL_LIMIT";
                expected.add(new ExpectedChunk(id,ChunkSelectionMetrics.sizeClass(baselineChunks.get(slug).size()),
                        RetrievalCorpus.position(anchor,baselineChunks.get(slug).size()),RetrievalCorpus.position(index,chunks.get(slug).size()),
                        rank,afterCap,selected.contains(id),reason));
            }
            var assembled = AskContext.assemble(retrieved,props,RetrievalReportWriter.JSON);
            long markerHits = c.query().relevantChunks().stream().filter(t -> assembled.sourceMap().values().stream()
                    .anyMatch(s -> s.chunk().slug().equals(t.slug()) && s.includedText().contains("[eval:"+t.marker()+"]"))).count();
            diagnostics.add(new CaseDiagnostic(c.query().id(),ChunkSelectionMetrics.coverage(new HashSet<>(c.expectedChunks()),ids,variant.perNoteCap()),
                    List.copyOf(expected),c.query().relevantKnowledge().stream().filter(n -> retrieved.stream().noneMatch(r -> r.slug().equals(n))).toList(),
                    retrieved.stream().mapToLong(r -> r.chunkText().length()).sum(),assembled.sourceMap().size(),
                    (int) assembled.sourceMap().values().stream().map(s -> s.chunk().slug()).distinct().count(),assembled.data().length(),
                    c.query().relevantChunks().isEmpty() ? null : (double) markerHits/c.query().relevantChunks().size()));
        }
        long input = 0; for (var note : corpus.notes()) {
            var source = new EmbeddingSource(1,owner,note.title(),note.summary(),note.markdown(),Instant.EPOCH);
            input += chunks.get(note.slug()).stream().mapToLong(c -> source.input(c.text()).length()).sum();
        }
        int count = chunks.values().stream().mapToInt(List::size).sum();
        var indexCost = new IndexCost(count,(double) count/corpus.notes().size(),chunks.values().stream().mapToInt(List::size).max().orElseThrow(),
                input,ChunkSelectionMetrics.ESTIMATOR.estimate(input),(int) chunks.values().stream().mapToLong(c -> (c.size()+15)/16).sum());
        return new Result(variant.name(),variant.baseline(),variant,strategy.marker(),indexCost,evaluation.overall(),
                evaluation.byCategory(),evaluation.byLanguage(),aggregate(diagnostics),null,cases,List.copyOf(diagnostics));
    }
    private static Diagnostics aggregate(List<CaseDiagnostic> cases) {
        var eligible = cases.stream().filter(c -> c.coverage().conditionalSamples()>0).toList();
        var misses = cases.stream().filter(c -> c.coverage().noteHitChunkMiss()).map(CaseDiagnostic::queryId).toList();
        var pressure = cases.stream().filter(c -> !c.coverage().capPressureChunks().isEmpty()).map(CaseDiagnostic::queryId).toList();
        var reasons = new TreeMap<String,Integer>(); cases.forEach(c -> c.expected().forEach(e -> reasons.merge(e.exclusion(),1,Integer::sum)));
        return new Diagnostics(eligible.size(),eligible.stream().mapToInt(c -> c.coverage().eligibleChunks()).sum(),eligible.isEmpty()?null:
                eligible.stream().mapToDouble(c -> c.coverage().supportingChunkRecallGivenRelevantNoteRetrieved()).average().orElseThrow(),
                misses.size(),misses,cases.stream().filter(c -> !c.missingNotes().isEmpty()).map(CaseDiagnostic::queryId).toList(),pressure.size(),pressure,reasons,
                buckets(cases,ExpectedChunk::sizeClass),buckets(cases,ExpectedChunk::baselinePosition),
                buckets(cases,ExpectedChunk::baselinePosition,e -> e.sizeClass().equals("long")),buckets(cases,ExpectedChunk::variantPosition),
                ChunkSelectionMetrics.volume(cases.stream().map(CaseDiagnostic::contextChars).toList()),
                ChunkSelectionMetrics.volume(cases.stream().map(c -> (long) c.assembledContextChars()).toList()),
                cases.stream().mapToInt(CaseDiagnostic::assembledChunks).average().orElseThrow(),cases.stream().mapToInt(CaseDiagnostic::assembledSources).average().orElseThrow(),
                cases.stream().filter(c -> c.assembledMarkerRecall()!=null).mapToDouble(CaseDiagnostic::assembledMarkerRecall).average().orElseThrow());
    }
    private static Map<String,Bucket> buckets(List<CaseDiagnostic> cases, java.util.function.Function<ExpectedChunk,String> key) {
        return buckets(cases,key,e -> true);
    }
    static Map<String,Bucket> buckets(List<CaseDiagnostic> cases, java.util.function.Function<ExpectedChunk,String> key,
            java.util.function.Predicate<ExpectedChunk> include) {
        var counts = new TreeMap<String,int[]>(); cases.forEach(c -> c.expected().stream().filter(include).forEach(e -> {
            var bucket = counts.computeIfAbsent(key.apply(e),ignored -> new int[2]); bucket[0]++; if(e.selected()) bucket[1]++;
        }));
        var result = new TreeMap<String,Bucket>(); counts.forEach((name,c) -> result.put(name,new Bucket(c[0],c[1],(double)c[1]/c[0]))); return result;
    }
    static Report report(Instant timestamp, RetrievalCorpus corpus, List<Result> results) {
        var baseline = results.stream().filter(Result::baseline).findFirst().orElseThrow();
        var variants = results.stream().map(r -> r.delta(baseline)).toList();
        var best = variants.stream().min(Comparator.<Result>comparingDouble(r -> -ChunkSelectionMetrics.recall(r.metrics().chunkMetrics(),8))
                .thenComparingDouble(r -> r.diagnostics().selectedCharacters().mean()).thenComparing(Result::name)).orElseThrow();
        var positions = new TreeMap<String,Integer>(); corpus.queries().forEach(q -> q.relevantChunks().stream().map(RetrievalCorpus.ChunkTruth::position)
                .filter(Objects::nonNull).distinct().forEach(p -> positions.merge(p,1,Integer::sum)));
        // Recommendation documents measured tradeoffs, never mutates runtime configuration or gates tests on quality.
        var longNotes = corpus.notes().stream().filter(n -> new MarkdownChunker(4000,200).chunk(n.markdown()).size()>=5).map(RetrievalCorpus.Note::slug).toList();
        long improved = longNotes.stream().filter(n -> fixtureRecall(best,n)>fixtureRecall(baseline,n)+1e-12).count();
        String decision = String.format(Locale.ROOT,
                "KEEP BASELINE — bestObserved %s has delta Recall@8 %.4f, noteRecall@8 %.4f, diversity %.4f, raw context growth %.1f%%, "
                        + "and improves %d/%d independent long-note fixtures. Larger-context/diversity/indexing costs and controlled-vocabulary ties "
                        + "do not establish acceptable live-model cost/coverage. No production tuning applied; collect live synthetic evidence with explicit approval first.",
                best.name(),best.deltaFromBaseline().chunkRecallAt8(),best.deltaFromBaseline().noteRecallAt8(),best.deltaFromBaseline().diversity(),
                100*best.deltaFromBaseline().meanContextChars()/baseline.diagnostics.selectedCharacters().mean(),improved,longNotes.size());
        return new Report(1,timestamp,corpus.version(),corpus.notes().size(),corpus.queries().size(),4,positions,"offline",OfflineEmbeddingClient.NAME,64,
                "Provider semantic quality NOT measured. Structural RAG selection only; raw retrieval plus actual AskContext budget diagnostics, no generation.",
                "Conditional recall macro-averaged over queries with expected chunks in notes already present in the FULL result; other absent notes excluded. "
                        + "MRR horizon is each variant limit; @8 censored for limit6. Position/size buckets are micro chunk counts, anchored to baseline; overlap can duplicate marker truth. "
                        + "Context stats include negatives; nearest-rank p50/p95. Cost proxy ceil(chars/2.5), excludes prompts/question/schema/output. "
                        + "BestObserved maximizes Recall@8 then minimizes mean raw chars; not a production selector. Assembled context uses unchanged 6-source/24000-character budgets.",
                decision,
                best.name(),baseline.delta(baseline),variants);
    }
    static double fixtureRecall(Result result, String slug) {
        return result.cases().stream().filter(c -> c.expectedChunks().stream().anyMatch(e -> ChunkSelectionMetrics.note(e).equals(slug))).mapToDouble(c -> {
            var expected = c.expectedChunks().stream().filter(e -> ChunkSelectionMetrics.note(e).equals(slug)).toList();
            var selected = c.ranked().stream().limit(8).map(RetrievalEvaluation.Ranked::chunkId).toList();
            return (double) expected.stream().filter(selected::contains).count()/expected.size();
        }).average().orElse(0);
    }
}
