package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.knowledge.embedding.EmbeddingClient;
import java.text.Normalizer;
import java.util.*;

/** Controlled synthetic semantics, NOT a natural-language model. No query IDs/ground truth/network/config lookup. */
final class OfflineEmbeddingClient implements EmbeddingClient {
    static final String NAME = "offline-synthetic-v1";
    static final int DIMENSIONS = 64;
    // One binary feature per row. Explicit small synonym vocabulary; metadata never influences features.
    private static final List<List<String>> FEATURES = List.of(
            List.of("webclient", "reactor"),
            List.of("timeout", "timeouts", "latency", "hanging", "treo", "cho qua lau"),
            List.of("nginx", "upstream", "proxy_read_timeout"),
            List.of("redis"), List.of("cache", "caching", "bo nho dem"),
            List.of("postgresql", "postgres"), List.of("index", "indexing", "btree", "chi muc", "explain"),
            List.of("docker", "container", "containers", "image"), List.of("multistage", "multi stage", "build", "stages"),
            List.of("kafka", "consumer", "consumers", "poll", "rebalance"),
            List.of("oauth", "oidc", "authentication", "dang nhap"), List.of("csrf", "forgery"),
            List.of("kubernetes", "k8s", "pod", "pods"), List.of("readiness", "liveness", "probe", "probes", "san sang"),
            List.of("java", "jvm"), List.of("serialization", "serialize", "jackson", "json", "tuan tu hoa"),
            List.of("nextjs", "next js", "bff", "server actions"), List.of("http", "session", "cookie", "cookies", "samesite"),
            List.of("s3", "minio", "bucket", "object storage"), List.of("attachment", "attachments", "upload", "uploads"),
            List.of("pgvector", "cosine", "embedding", "embeddings"), List.of("github actions", "ci", "workflow", "pipeline"),
            List.of("proxy", "forwarded", "connect"), List.of("retry", "retries", "backoff"),
            List.of("lock", "locking", "advisory", "concurrency"), List.of("quota", "rpm", "rpd")
    );

    @Override public List<float[]> embed(List<String> inputs) { return inputs.stream().map(this::vector).toList(); }

    private float[] vector(String input) {
        String text = normalize(input.replaceAll("\\[eval:[a-z0-9-]+]", ""));
        float[] vector = new float[DIMENSIONS];
        vector[DIMENSIONS - 1] = 0.05f; // Non-zero fallback for unknown/negative queries; no no-match claim.
        for (int i = 0; i < FEATURES.size(); i++) {
            for (String word : FEATURES.get(i)) {
                if (text.contains(normalize(word))) { vector[i] = 1; break; }
            }
        }
        double norm = 0;
        for (float value : vector) norm += value * value;
        for (int i = 0; i < vector.length; i++) vector[i] /= (float) Math.sqrt(norm);
        return vector;
    }

    private static String normalize(String text) {
        return " " + Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").strip().replaceAll("\\s+", " ") + " ";
    }
}
