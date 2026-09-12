package com.knowledgeapplication.api.knowledge.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ShareTokenGeneratorTest {

    private static final Pattern URL_SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    @Test
    void generatesUniqueUrlSafeTokensWithTwoHundredFiftySixBitsOfRandomInput() {
        ShareTokenGenerator generator = new ShareTokenGenerator();
        var tokens = IntStream.range(0, 100)
                .mapToObj(index -> generator.generate())
                .toList();

        assertThat(new HashSet<>(tokens)).hasSize(tokens.size());
        assertThat(tokens).allMatch(token -> URL_SAFE_TOKEN.matcher(token).matches());
    }
}
