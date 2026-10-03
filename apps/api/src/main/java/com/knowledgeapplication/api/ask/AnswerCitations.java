package com.knowledgeapplication.api.ask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** All-or-nothing validation; metadata/evidence never come from the provider. No DB/provider calls. */
public final class AnswerCitations {
    public static final int MAX_EVIDENCE_CHARS = 400;
    private AnswerCitations() {}
    public static AskResponse validate(AnswerDraft draft, AskContext.Assembled context) {
        if (draft == null) throw unavailable();
        draft.validate(context.sourceMap().size());
        var citations = new LinkedHashMap<String,AskResponse.Citation>();
        var blocks = new ArrayList<AskResponse.Block>();
        for (var block : draft.blocks()) {
            var ids = new ArrayList<String>();
            for (var ref : block.sourceRefs()) {
                var selected = context.sourceMap().get(ref);
                if (selected == null) throw unavailable(); // Never drop one invalid reference and return a partial answer.
                var chunk = selected.chunk();
                String text = selected.includedText().strip();
                int end = Math.min(text.length(), MAX_EVIDENCE_CHARS);
                if (end < text.length() && end > 0 && Character.isHighSurrogate(text.charAt(end-1))
                        && Character.isLowSurrogate(text.charAt(end))) end--;
                String evidence = text.substring(0,end).strip(); // Exact substring; no fuzzy matching or invented ellipsis.
                if (evidence.isBlank() || !chunk.chunkText().contains(evidence)) throw unavailable();
                var citation = citations.computeIfAbsent(ref, ignored -> new AskResponse.Citation("C"+(citations.size()+1),
                        new AskResponse.Source(chunk.id(),chunk.title(),chunk.slug()),chunk.chunkIndex(),evidence));
                ids.add(citation.id());
            }
            blocks.add(new AskResponse.Block(block.markdown().trim(),List.copyOf(ids)));
        }
        return new AskResponse(AskResponse.Status.ANSWERED,new AskResponse.Answer(List.copyOf(blocks)),List.copyOf(citations.values()));
    }
    private static AskUnavailableException unavailable() { return new AskUnavailableException(AskUnavailableException.Reason.GENERATION); }
}
