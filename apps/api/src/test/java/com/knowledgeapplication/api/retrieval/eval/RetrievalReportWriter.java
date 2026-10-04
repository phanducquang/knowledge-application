package com.knowledgeapplication.api.retrieval.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

final class RetrievalReportWriter {
    private RetrievalReportWriter() {}
    static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    static void write(RetrievalEvaluation.Report report, Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("report.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        var text = new StringBuilder("RETRIEVAL QUALITY EVALUATION — offline synthetic only\n");
        text.append("Evaluated: ").append(report.evaluatedAt()).append("\nCorpus: ").append(report.corpus()).append('\n');
        text.append("Configuration: ").append(report.configuration()).append('\n');
        text.append("Provider semantic quality NOT measured. Small controlled samples are diagnostics, not production quality estimates.\n");
        mode(text, "SEMANTIC SEARCH (deduplicated notes; MRR@20)", report.semanticSearch());
        mode(text, "RAG CONTEXT (chunk positions; note AND chunk coverage, RR@8)", report.ragContext());
        Files.writeString(directory.resolve("report.txt"), text);
    }
    static void writeMatrix(ChunkSelectionEvaluation.Report report, Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("chunk-selection-report.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        var out = new StringBuilder("RAG CHUNK SELECTION — offline synthetic matrix\nProvider semantic quality NOT measured.\n");
        out.append("Corpus: ").append(report.corpusVersion()).append(" notes=").append(report.noteCount()).append(" queries=").append(report.queryCount())
                .append(" addedLongNotes=").append(report.addedLongNotes()).append(" declaredPositions=").append(report.declaredPositionCases()).append('\n');
        out.append(report.policies()).append('\n').append("Decision: ").append(report.recommendation()).append('\n')
                .append("BestObserved (Recall@8 then lowest raw context, NOT production choice): ").append(report.bestObserved()).append('\n');
        out.append("BASELINE: ").append(report.baseline().name()).append("; all deltas use the SAME extended corpus\n")
                .append("variant | Recall@1/3/5/8 | MRR@limit | noteRecall@8 | conditionalRecall@limit | noteHitChunkMiss | mean/p50/p95/max chars | diversity | chunkCount | delta\n");
        for (var r : report.variants()) {
            out.append(r.baseline()?"BASELINE ":"").append(r.name()).append(" | ").append(r.metrics().chunkMetrics().cutoffs().stream().map(k -> String.format(Locale.ROOT,"%.4f",k.recall())).toList())
                    .append(" | ").append(r.metrics().chunkMetrics().reciprocalRank()).append(" | ").append(ChunkSelectionMetrics.recall(r.metrics().noteMetrics(),8))
                    .append(" | ").append(r.diagnostics().supportingChunkRecallGivenRelevantNoteRetrieved()).append(" | ").append(r.diagnostics().noteHitChunkMissCount())
                    .append(" | ").append(r.diagnostics().selectedCharacters()).append(" | ").append(r.metrics().meanDiversity())
                    .append(" | ").append(r.indexingCost().chunkCount()).append(" | ").append(r.deltaFromBaseline()).append('\n');
        }
        for (var r : report.variants()) {
            out.append("\n").append(r.name()).append("\nIndex cost: ").append(r.indexingCost()).append("\nDiagnostics: ").append(r.diagnostics()).append('\n');
            r.byCategory().forEach((name,stats) -> summary(out,"category="+name,stats));
            r.byLanguage().forEach((name,stats) -> summary(out,"language="+name,stats));
            var playbook = r.cases().stream().filter(c -> c.query().id().equals("q-playbook")).findFirst().orElseThrow();
            out.append("q-playbook ranked candidates: ").append(playbook.ranked()).append('\n');
            for (var c : r.caseDiagnostics()) out.append(c.queryId()).append(" ").append(c.coverage()).append(" expected=").append(c.expected())
                    .append(" missingNotes=").append(c.missingNotes()).append(" chars=").append(c.contextChars())
                    .append(" assembledChars=").append(c.assembledContextChars()).append(" assembledMarkerRecall=").append(c.assembledMarkerRecall()).append('\n');
        }
        Files.writeString(directory.resolve("chunk-selection-report.txt"),out);
    }
    static void mode(StringBuilder out, String label, RetrievalEvaluation.Mode mode) {
        out.append('\n').append(label).append('\n');
        summary(out, "overall", mode.overall());
        mode.byCategory().forEach((name, stats) -> summary(out, "category=" + name, stats));
        mode.byLanguage().forEach((name, stats) -> summary(out, "language=" + name, stats));
        for (var c : mode.cases()) {
            var q = c.query();
            out.append('\n').append(q.id()).append(" [").append(q.category()).append('/').append(q.language()).append("] ").append(q.query()).append('\n');
            out.append("Rationale: ").append(q.rationale()).append("\nExpected notes: ").append(q.relevantKnowledge())
                    .append("; declared markers: ").append(q.relevantChunks()).append("; resolved chunks: ").append(c.expectedChunks()).append('\n');
            out.append("Retrieved: ").append(c.ranked()).append('\n');
            out.append("Relevant note ranks: ").append(c.relevantNoteRanks()).append("; chunk ranks: ").append(c.relevantChunkRanks()).append('\n');
            out.append("Missing notes@K: ").append(c.missingNotesAtK()).append("; missing chunks@K: ").append(c.missingChunksAtK()).append('\n');
            out.append(q.negative() ? "NEGATIVE: unscored nearest/distance diagnostics; no calibrated cutoff\n" : "Note " + score(c.noteScore()) + "; chunk " + score(c.chunkScore()) + "\n");
            out.append("uniqueNoteCount=").append(c.uniqueNoteCount()).append(" retrievedChunks=").append(c.retrievedChunks())
                    .append(" diversity=").append(c.diversity()).append(" sourcesAtCap=").append(c.sourcesAtPerNoteCap())
                    .append(" repeatedNotes=").append(c.duplicateNotes()).append('\n');
        }
    }
    private static void summary(StringBuilder out, String name, RetrievalEvaluation.Summary s) {
        out.append(name).append(" n=").append(s.queryCount()).append(" positive=").append(s.positiveSamples())
                .append(" negative=").append(s.negativeSamples()).append(" chunkSamples=").append(s.chunkSamples())
                .append(" notes{").append(score(s.noteMetrics())).append("} chunks{").append(score(s.chunkMetrics())).append("}")
                .append(" meanUniqueNotes=").append(s.meanUniqueNoteCount()).append(" meanChunks=").append(s.meanRetrievedChunks())
                .append(" meanDiversity=").append(s.meanDiversity()).append(" meanSourcesAtCap=").append(s.meanSourcesAtPerNoteCap())
                .append(" repeatedNoteCases=").append(s.duplicateNoteCases()).append('\n');
    }
    private static String score(RetrievalMetrics.Score s) {
        if (s == null) return "not scored (no eligible ground truth)";
        var text = new StringBuilder();
        for (var k : s.cutoffs()) text.append(String.format(Locale.ROOT, "Hit@%d=%.4f Recall@%d=%.4f ", k.k(), k.hitRate(), k.k(), k.recall()));
        return text + String.format(Locale.ROOT, "MRR=%.4f", s.reciprocalRank());
    }
}
