package com.knowledgeapplication.api.knowledge.relation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WikiLinkExtractorTest {

    @Test
    void extractsUniqueCanonicalSlugsInEncounterOrder() {
        assertThat(WikiLinkExtractor.slugs("See [[spring-boot]] and [[database-2]], then [[spring-boot]]."))
                .containsExactly("spring-boot", "database-2");
    }

    @Test
    void ignoresCodeEscapesAndInvalidReferences() {
        String markdown = """
                [[real-note]] `[[inline-code]]` and ``[[long-code]]``
                \\[[escaped-note]] [[Not A Slug]] [[bad_slug]]
                ```markdown
                [[fenced-note]]
                ```
                ~~~
                [[tilde-note]]
                ~~~
                    [[indented-note]]
                [a [[linked-label]]](https://example.com) [ordinary](https://example.com/[[linked-url]])
                [[last-note]]
                """;
        assertThat(WikiLinkExtractor.slugs(markdown))
                .containsExactlyElementsOf(List.of("real-note", "last-note"));
    }

    @Test
    void emptyMarkdownHasNoLinks() {
        assertThat(WikiLinkExtractor.slugs("")).isEmpty();
    }
}
