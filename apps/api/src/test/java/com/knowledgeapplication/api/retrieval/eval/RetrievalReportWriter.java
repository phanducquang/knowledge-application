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
    private static void mode(StringBuilder out, String label, RetrievalEvaluation.Mode mode) {
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
