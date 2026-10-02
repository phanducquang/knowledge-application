package com.knowledgeapplication.api.knowledge.relation;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Extracts canonical [[slug]] references from Markdown prose, not code. */
public final class WikiLinkExtractor {

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");

    private WikiLinkExtractor() {
    }

    public static Set<String> slugs(String markdown) {
        Set<String> slugs = new LinkedHashSet<>();
        if (markdown == null || markdown.isEmpty()) {
            return slugs;
        }

        char fenceMarker = 0;
        int fenceLength = 0;
        int inlineTicks = 0;
        for (String line : markdown.split("\\r?\\n", -1)) {
            var fence = FENCE.matcher(line);
            if (fence.matches()) {
                String marker = fence.group(1);
                if (fenceMarker == 0) {
                    fenceMarker = marker.charAt(0);
                    fenceLength = marker.length();
                    continue;
                }
                if (marker.charAt(0) == fenceMarker && marker.length() >= fenceLength
                        && fence.group(2).isBlank()) {
                    fenceMarker = 0;
                    fenceLength = 0;
                }
                continue;
            }
            if (fenceMarker != 0 || line.startsWith("    ") || line.startsWith("\t")) {
                continue;
            }

            for (int index = 0; index < line.length();) {
                char current = line.charAt(index);
                if (current == '\\' && index + 1 < line.length()) {
                    index += 2;
                    continue;
                }
                if (current == '`') {
                    int end = index + 1;
                    while (end < line.length() && line.charAt(end) == '`') {
                        end++;
                    }
                    int length = end - index;
                    if (inlineTicks == 0) {
                        inlineTicks = length;
                    } else if (inlineTicks == length) {
                        inlineTicks = 0;
                    }
                    index = end;
                    continue;
                }
                if (inlineTicks == 0 && current == '[' && index + 1 < line.length()
                        && line.charAt(index + 1) != '[') {
                    int labelEnd = line.indexOf("](", index + 1);
                    if (labelEnd >= 0) {
                        int destinationEnd = line.indexOf(')', labelEnd + 2);
                        if (destinationEnd >= 0) {
                            // A wiki-looking token inside an ordinary Markdown link's
                            // label or destination is not standalone wiki prose.
                            index = destinationEnd + 1;
                            continue;
                        }
                    }
                }
                if (inlineTicks == 0 && current == '[' && index + 1 < line.length()
                        && line.charAt(index + 1) == '[') {
                    int close = line.indexOf("]]", index + 2);
                    if (close > index + 2) {
                        String slug = line.substring(index + 2, close);
                        if (slug.length() <= 200 && SLUG.matcher(slug).matches()) {
                            slugs.add(slug);
                            index = close + 2;
                            continue;
                        }
                    }
                }
                index++;
            }
        }
        return slugs;
    }
}
