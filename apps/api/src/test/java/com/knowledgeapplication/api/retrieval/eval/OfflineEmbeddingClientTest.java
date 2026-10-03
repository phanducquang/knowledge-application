package com.knowledgeapplication.api.retrieval.eval;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OfflineEmbeddingClientTest {
    private final OfflineEmbeddingClient client = new OfflineEmbeddingClient();
    @Test void repeatedInputIsDeterministicAndUnitLength() {
        var a = client.embed(List.of("WebClient timeout retry HTTP Java")).get(0);
        assertThat(a).containsExactly(client.embed(List.of("WebClient timeout retry HTTP Java")).get(0));
        double length = 0; for (float value : a) { assertThat(Float.isFinite(value)).isTrue(); length += value * value; }
        assertThat(length).isCloseTo(1, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(a).hasSize(64);
    }
    @Test void declaredSynonymsAndVietnameseNormalizationShareFeatures() {
        assertThat(client.embed(List.of("cache")).get(0)).containsExactly(client.embed(List.of("bộ nhớ đệm")).get(0));
        assertThat(client.embed(List.of("timeout")).get(0)).containsExactly(client.embed(List.of("latency")).get(0));
    }
    @Test void unrecognizedQueriesAreNonzeroButNotMagicallyMappedToGroundTruth() {
        assertThat(client.embed(List.of("gardening flowers")).get(0)).containsExactly(client.embed(List.of("unrecognized words")).get(0));
        assertThat(client.embed(List.of("How do I keep an absent entry remembered for a short spell?")).get(0))
                .isNotEqualTo(client.embed(List.of("Redis cache")).get(0));
    }
    @Test void evaluationMarkersDoNotLeakGroundTruthIntoVectors() {
        assertThat(client.embed(List.of("Redis [eval:cache-timeout]")).get(0)).containsExactly(client.embed(List.of("Redis")).get(0));
    }
}
