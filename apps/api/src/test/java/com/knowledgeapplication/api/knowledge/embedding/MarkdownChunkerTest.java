package com.knowledgeapplication.api.knowledge.embedding;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MarkdownChunkerTest {
    @Test
    void blankContentDoesNotProduceEmptyChunks() {
        var chunker = new MarkdownChunker(100, 0);
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk(" \n\t\r\n")).isEmpty();
    }

    @Test
    void shortContentIsOneChunkNotOnePerLine() {
        String content = "# Intro\n\nShort paragraph.\n\n- first\n- second";
        assertThat(new MarkdownChunker(200, 0).chunk(content)).containsExactly(new MarkdownChunker.Chunk(0, content));
    }

    @Test
    void headingsStartNewSectionsAndPreserveContext() {
        String first = "# A\n\n" + "A useful paragraph in the first section. ".repeat(3);
        String second = "## B\n\nSecond paragraph with its own heading.";
        var chunks = new MarkdownChunker(200, 0).chunk(first + "\n\n" + second);
        assertThat(chunks).extracting(MarkdownChunker.Chunk::text).containsExactly(first.strip(), second);
    }

    @Test
    void manyTinyHeadingsInAShortNoteAreGroupedRatherThanCreatingHundredsOfChunks() {
        var chunks = new MarkdownChunker(4000, 200).chunk("# Small\n\nx\n\n".repeat(100));
        assertThat(chunks).isNotEmpty().hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void usesParagraphAndListBoundaries() {
        String paragraph = "A paragraph with enough words to fill a chunk.";
        String list = "- first item\n- second item\n- third item";
        var chunks = new MarkdownChunker(60, 0).chunk(paragraph + "\n\n" + list);
        assertThat(chunks).extracting(MarkdownChunker.Chunk::text).containsExactly(paragraph, list);
    }

    @Test
    void preservesFencesAndBlankLinesWithoutParsingCodeHeadingsOrExecutingMermaid() {
        String code = "```java\n# not a heading\n\nString x = \"value\";\n```";
        String diagram = "~~~mermaid\nflowchart LR\nA --> B\n~~~";
        assertThat(new MarkdownChunker(300, 0).chunk(code + "\n\n" + diagram))
                .extracting(MarkdownChunker.Chunk::text).containsExactly(code + "\n\n" + diagram);
    }

    @Test
    void splitsLongSectionsAtWordsAndLinesWithStableIndexesAndBounds() {
        String source = "# Large\n\n" + "Several useful technical words. ".repeat(100) + "\n\n- another item";
        var chunker = new MarkdownChunker(120, 20);
        var chunks = chunker.chunk(source);
        assertThat(chunks.size()).isGreaterThan(2).isLessThan(50);
        assertThat(chunks).isEqualTo(chunker.chunk(source));
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).index()).isEqualTo(i);
            assertThat(chunks.get(i).text()).isNotBlank().hasSizeLessThanOrEqualTo(120);
        }
    }

    @Test
    void overlapRetainsASmallTailAndCanBeDisabled() {
        String first = "Words for the first section and its final context.";
        var chunks = new MarkdownChunker(100, 20).chunk(first + "\n\n# Second\n\nAnother paragraph.");
        assertThat(chunks.get(1).text()).startsWith("its final context.\n\n# Second");
        assertThat(new MarkdownChunker(100, 0).chunk(first + "\n\n# Second\n\nAnother paragraph.").get(1).text())
                .startsWith("# Second");
    }

    @Test
    void oversizedTokensAndCodeStillRespectSizeWithoutSplittingUnicodeSurrogates() {
        String source = "```\n" + "😀".repeat(120) + "\n```";
        List<MarkdownChunker.Chunk> chunks = new MarkdownChunker(65, 10).chunk(source);
        for (var chunk : chunks) {
            assertThat(chunk.text()).isNotBlank().hasSizeLessThanOrEqualTo(65);
            assertThat(chunk.text().chars().filter(c -> Character.isHighSurrogate((char)c)).count())
                    .isEqualTo(chunk.text().chars().filter(c -> Character.isLowSurrogate((char)c)).count());
        }
    }

    @Test
    void unclosedFenceDoesNotLoseContent() {
        assertThat(new MarkdownChunker(200, 0).chunk("```java\nline\n\n# still code"))
                .extracting(MarkdownChunker.Chunk::text).containsExactly("```java\nline\n\n# still code");
    }
}
