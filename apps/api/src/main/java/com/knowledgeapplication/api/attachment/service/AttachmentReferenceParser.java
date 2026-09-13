package com.knowledgeapplication.api.attachment.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AttachmentReferenceParser {

    private static final Pattern REFERENCE = Pattern.compile(
            "(?i)attachment://([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
    );

    private AttachmentReferenceParser() {
    }

    public static Set<UUID> extract(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return Set.of();
        }

        Matcher matcher = REFERENCE.matcher(markdown);
        Set<UUID> references = new LinkedHashSet<>();
        while (matcher.find()) {
            references.add(UUID.fromString(matcher.group(1)));
        }
        return Collections.unmodifiableSet(references);
    }

    public static boolean contains(String markdown, UUID attachmentId) {
        return extract(markdown).contains(attachmentId);
    }
}
