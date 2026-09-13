package com.knowledgeapplication.api.attachment.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentReferenceParserTest {

    @Test
    void extractsCanonicalReferencesCaseInsensitivelyAndDeduplicates() {
        UUID first = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID second = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        String markdown = """
                ![One](attachment://550e8400-e29b-41d4-a716-446655440000)
                attachment://550E8400-E29B-41D4-A716-446655440000
                `attachment://6ba7b810-9dad-11d1-80b4-00c04fd430c8`
                """;

        assertThat(AttachmentReferenceParser.extract(markdown)).containsExactly(first, second);
    }

    @Test
    void ignoresMalformedReferencesAndHandlesBlankContent() {
        assertThat(AttachmentReferenceParser.extract(null)).isEmpty();
        assertThat(AttachmentReferenceParser.extract("")).isEmpty();
        assertThat(AttachmentReferenceParser.extract("attachment://not-a-uuid")).isEmpty();
    }
}
