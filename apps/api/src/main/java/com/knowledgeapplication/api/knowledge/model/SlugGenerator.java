package com.knowledgeapplication.api.knowledge.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SlugGenerator {

    private static final int MAX_LENGTH = 180;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+|-+$)");

    private SlugGenerator() {
    }

    public static String fromTitle(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replace('đ', 'd')
                .replace('Đ', 'D');
        String withoutDiacritics = DIACRITICS.matcher(normalized).replaceAll("");
        String slug = NON_ALPHANUMERIC.matcher(withoutDiacritics.toLowerCase(Locale.ROOT))
                .replaceAll("-");
        slug = EDGE_HYPHENS.matcher(slug).replaceAll("");

        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH);
            slug = EDGE_HYPHENS.matcher(slug).replaceAll("");
        }

        return slug.isBlank() ? "knowledge" : slug;
    }
}
