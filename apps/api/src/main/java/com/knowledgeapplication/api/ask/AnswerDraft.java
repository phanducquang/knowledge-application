package com.knowledgeapplication.api.ask;

import java.util.HashSet;
import java.util.List;

/** Untrusted provider-neutral output. Reference identity is validated separately against this request. */
public record AnswerDraft(List<Block> blocks) {
    public static final int MAX_BLOCKS = 24;
    public static final int MAX_BLOCK_CHARS = 8192;
    public static final int MAX_TOTAL_CHARS = 65536;
    public static final int MAX_REF_OCCURRENCES = 128;
    public record Block(String markdown, List<String> sourceRefs) {
        @Override public String toString() { return "AnswerBlock[redacted]"; }
    }
    public void validate(int availableRefs) {
        if (blocks == null || blocks.isEmpty() || blocks.size() > MAX_BLOCKS) invalid();
        int chars = 0, refs = 0;
        for (var block : blocks) {
            if (block == null || block.markdown() == null || block.markdown().isBlank()
                    || block.markdown().length() > MAX_BLOCK_CHARS || block.sourceRefs() == null
                    || block.sourceRefs().isEmpty() || block.sourceRefs().size() > availableRefs) invalid();
            chars += block.markdown().length(); refs += block.sourceRefs().size();
            if (chars > MAX_TOTAL_CHARS || refs > MAX_REF_OCCURRENCES) invalid();
            var seen = new HashSet<String>();
            for (var ref : block.sourceRefs()) {
                if (ref == null || !ref.matches("S[1-9][0-9]{0,2}") || !seen.add(ref)) invalid();
            }
        }
    }
    private static void invalid() { throw new AskUnavailableException(AskUnavailableException.Reason.GENERATION); }
    @Override public String toString() { return "AnswerDraft[redacted]"; }
}
