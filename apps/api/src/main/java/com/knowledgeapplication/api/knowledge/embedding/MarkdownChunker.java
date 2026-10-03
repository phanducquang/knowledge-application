package com.knowledgeapplication.api.knowledge.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Source Markdown only: no HTML rendering, code execution or provider tokenization. */
public final class MarkdownChunker {
    public static final int VERSION = 1;
    private static final Pattern HEADING = Pattern.compile(" {0,3}#{1,6}\\s+.*");
    private static final Pattern FENCE = Pattern.compile(" {0,3}(`{3,}|~{3,})(.*)");
    private final int maxChars;
    private final int overlap;

    public MarkdownChunker(int maxChars, int overlap) {
        if (maxChars < 16 || overlap < 0 || overlap > maxChars / 4) {
            throw new IllegalArgumentException("Invalid Markdown chunk size/overlap");
        }
        this.maxChars = maxChars;
        this.overlap = overlap;
    }

    public record Chunk(int index, String text) {}

    public List<Chunk> chunk(String markdown) {
        if (markdown == null || markdown.isBlank()) return List.of();
        int capacity = maxChars - (overlap == 0 ? 0 : overlap + 2);
        List<String> base = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : blocks(markdown.replace("\r\n", "\n").replace('\r', '\n'))) {
            // Group tiny sections instead of producing one tiny embedding per heading.
            if (current.length() >= capacity / 4 && HEADING.matcher(block.lines().findFirst().orElse("")).matches()) {
                flush(base, current);
            }
            for (String piece : split(block, capacity)) {
                if (current.length() > 0 && current.length() + 2 + piece.length() > capacity) flush(base, current);
                if (current.length() > 0) current.append("\n\n");
                current.append(piece);
            }
        }
        flush(base, current);
        List<Chunk> result = new ArrayList<>();
        for (int i = 0; i < base.size(); i++) {
            String prefix = i == 0 || overlap == 0 ? "" : tail(base.get(i - 1));
            result.add(new Chunk(i, prefix.isBlank() ? base.get(i) : prefix + "\n\n" + base.get(i)));
        }
        return List.copyOf(result);
    }

    private static List<String> blocks(String markdown) {
        List<String> result = new ArrayList<>();
        StringBuilder block = new StringBuilder();
        char fenceChar = 0;
        int fenceLength = 0;
        for (String line : markdown.split("\n", -1)) {
            var fence = FENCE.matcher(line);
            if (fenceChar == 0 && (line.isBlank() || HEADING.matcher(line).matches() || fence.matches())) {
                flush(result, block);
            }
            if (fence.matches()) {
                String marker = fence.group(1);
                if (fenceChar == 0) {
                    fenceChar = marker.charAt(0);
                    fenceLength = marker.length();
                } else if (marker.charAt(0) == fenceChar && marker.length() >= fenceLength && fence.group(2).isBlank()) {
                    fenceChar = 0;
                    if (block.length() > 0) block.append('\n');
                    block.append(line);
                    flush(result, block);
                    continue;
                }
            }
            if (line.isBlank() && fenceChar == 0) continue;
            if (block.length() > 0) block.append('\n');
            block.append(line);
            if (fenceChar == 0 && HEADING.matcher(line).matches()) flush(result, block);
        }
        flush(result, block);
        return result;
    }

    private static List<String> split(String block, int capacity) {
        List<String> result = new ArrayList<>();
        String remaining = block.strip();
        while (remaining.length() > capacity) {
            int end = capacity;
            // Prefer full lines, then words. Only a single oversized token needs a hard cut.
            int newline = remaining.lastIndexOf('\n', capacity - 1);
            if (newline >= capacity / 2) end = newline;
            else {
                for (int i = capacity - 1; i >= capacity / 2; i--) {
                    if (Character.isWhitespace(remaining.charAt(i))) { end = i; break; }
                }
            }
            if (Character.isHighSurrogate(remaining.charAt(end - 1)) && Character.isLowSurrogate(remaining.charAt(end))) end--;
            String piece = remaining.substring(0, end).strip();
            if (!piece.isBlank()) result.add(piece);
            remaining = remaining.substring(end).strip();
        }
        if (!remaining.isBlank()) result.add(remaining);
        return result;
    }

    private String tail(String previous) {
        int start = Math.max(0, previous.length() - overlap);
        if (start > 0 && Character.isLowSurrogate(previous.charAt(start))) start++;
        if (start > 0) {
            int space = previous.indexOf(' ', start);
            int newline = previous.indexOf('\n', start);
            int boundary = space < 0 ? newline : newline < 0 ? space : Math.min(space, newline);
            if (boundary >= 0) start = boundary + 1;
        }
        return previous.substring(start).strip();
    }

    private static void flush(List<String> destination, StringBuilder text) {
        if (!text.toString().isBlank()) destination.add(text.toString().strip());
        text.setLength(0);
    }
}
