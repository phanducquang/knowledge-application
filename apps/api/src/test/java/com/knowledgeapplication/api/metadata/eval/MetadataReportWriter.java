package com.knowledgeapplication.api.metadata.eval;

import java.nio.file.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;

final class MetadataReportWriter {
    static String json(MetadataEvaluation.Report report) {
        return JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build().writeValueAsString(report)+"\n";
    }
    static String review(MetadataEvaluation.Report report) {
        var out=new StringBuilder("# Synthetic metadata quality evaluation\n\n");
        out.append("Concept coverage is a phrase-matching proxy, NOT factuality/entailment proof. Forbidden flags are incomplete diagnostics, including possible negation false positives. Language fit is human-reviewed.\n\n");
        out.append("```json\n").append(json(new MetadataEvaluation.Report(report.identity(),report.status(),report.failure(),report.usage(),report.metrics(),report.groups(),List.of()))).append("```\n\n");
        out.append("Metrics are macro averages; visible-only averages omit N/A cases with no visible expected ground truth. F1=0 when both P/R=0. Nearest-rank p50/p95; chars are UTF-16, words whitespace-separated. Provider request counts are conservative attempted/reserved calls, not billing or usageMetadata.\n\n");
        out.append("Human ratings are intentionally **blank**. For each dimension, 1=poor, 3=usable with edits, 5=strong. Summary factuality: every claim supported; coverage: key facts preserved; conciseness: no fluff; usefulness: useful note preview; language fit: source language/technical terms retained. Tags specificity: topical not generic; usefulness: useful retrieval labels; consistency: stable canonical concepts.\n\n");
        for(var c:report.cases()) {
            out.append("## ").append(c.id()).append(" — ").append(escape(c.title())).append("\n\n")
                    .append("Language: ").append(c.language()).append("; topic: ").append(c.category()).append("; length: ").append(c.lengthGroup()).append("; position: ").append(c.position()).append("\n\n")
                    .append("Rationale: ").append(escape(c.rationale())).append("\n\n");
            if(c.suggestion()!=null) out.append("Generated summary (plain text):\n\n> ").append(escape(c.suggestion().summary()).replace("\n","\n> ")).append("\n\nGenerated new tags: ").append(escape(c.suggestion().tags().toString())).append("\n\n");
            out.append("```json\n").append(JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build().writeValueAsString(c)).append("\n```\n\n")
                    .append("Summary ratings (1–5): factuality ___; coverage ___; conciseness ___; usefulness ___; language fit ___\n\n")
                    .append("Tag ratings (1–5): specificity ___; usefulness ___; consistency ___\n\nReview notes: ___\n\n");
        }
        return out.toString();
    }
    static void write(MetadataEvaluation.Report report,Path directory,boolean live) throws java.io.IOException {
        Files.createDirectories(directory);
        String stem=live?"live-report":"report";
        Files.writeString(directory.resolve(stem+".json"),json(report));
        Files.writeString(directory.resolve(stem+".txt"),review(report));
        if(live) Files.writeString(directory.resolve("live-review.md"),review(report));
    }
    private static String escape(String text) { return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("`","\\`"); }
}
