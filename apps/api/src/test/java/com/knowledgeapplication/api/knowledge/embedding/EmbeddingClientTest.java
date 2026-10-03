package com.knowledgeapplication.api.knowledge.embedding;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

class EmbeddingClientTest {
    HttpServer server;
    ExecutorService executor;
    volatile String body = "{\"model\":\"test-model\",\"data\":[{\"index\":1,\"embedding\":[0,1,0]},{\"index\":0,\"embedding\":[1,0,0]}]}";
    volatile String requestBody;
    volatile String authorization;
    volatile int status = 200;
    volatile int delayMillis;
    volatile boolean delayBody;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(r -> { var thread = new Thread(r); thread.setDaemon(true); return thread; });
        server.setExecutor(executor);
        server.createContext("/v1/embeddings", exchange -> {
            try {
                requestBody = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (!delayBody && delayMillis > 0) Thread.sleep(delayMillis);
                byte[] response = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, response.length);
                if (delayBody && delayMillis > 0) Thread.sleep(delayMillis);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException ignored) {
                // Timeout tests deliberately cancel/close the exchange.
            } finally { exchange.close(); }
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); executor.shutdownNow(); }

    private OpenAiCompatibleEmbeddingClient client(Duration timeout) {
        var properties = new EmbeddingProperties(true, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "test-api-key", "test-model", 3, Duration.ofSeconds(1), timeout, 2, false,
                Duration.ofMinutes(1), Duration.ZERO, 2, 256, 20);
        return new OpenAiCompatibleEmbeddingClient(properties, new ObjectMapper());
    }

    @Test
    void fakeIsRepeatableDistinctFixedDimensionalAndPreservesOrder() {
        var fake = new DeterministicEmbeddingClient(8);
        var batch = fake.embed(List.of("Alpha", "Beta", "Alpha"));
        assertThat(batch.get(0)).containsExactly(fake.embed(List.of("Alpha")).get(0));
        assertThat(batch.get(2)).containsExactly(batch.get(0));
        assertThat(batch.get(1)).isNotEqualTo(batch.get(0));
        assertThat(batch).allSatisfy(vector -> assertThat(vector).hasSize(8));
    }

    @Test
    void productionClientUsesIndexedOrderingAndBackendOnlyConfiguration() {
        var result = client(Duration.ofSeconds(2)).embed(List.of("First", "Second"));
        assertThat(result.get(0)).containsExactly(1, 0, 0);
        assertThat(result.get(1)).containsExactly(0, 1, 0);
        var request = new ObjectMapper().readTree(requestBody);
        assertThat(request.path("input").get(0).asString()).isEqualTo("First");
        assertThat(request.path("model").asString()).isEqualTo("test-model");
        assertThat(request.path("dimensions").intValue()).isEqualTo(3);
        assertThat(request.path("encoding_format").asString()).isEqualTo("float");
        assertThat(authorization).isEqualTo("Bearer test-api-key");
        assertThat(requestBody).doesNotContain("test-api-key");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not JSON",
            "{\"model\":\"test-model\",\"data\":[]}",
            "{\"model\":\"wrong-model\",\"data\":[{\"index\":0,\"embedding\":[1,0,0]}]}",
            "{\"model\":\"test-model\",\"data\":[{\"index\":0,\"embedding\":[1,0]}]}",
            "{\"model\":\"test-model\",\"data\":[{\"index\":0,\"embedding\":[0,0,0]}]}",
            "{\"model\":\"test-model\",\"data\":[{\"index\":0,\"embedding\":[\"secret\",0,0]}]}",
            "{\"model\":\"test-model\",\"data\":[{\"embedding\":[1,0,0]}]}",
            "{\"model\":\"test-model\",\"data\":[{\"index\":9,\"embedding\":[1,0,0]}]}"
    })
    void rejectsMalformedCountDimensionModelAndIndexWithoutSensitiveDetails(String response) {
        body = response;
        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).embed(List.of("private note")))
                .isInstanceOf(EmbeddingUnavailableException.class).hasNoCause()
                .hasMessageNotContaining("secret").hasMessageNotContaining("private note");
    }

    @Test
    void rejectsDuplicateIndexes() {
        body = "{\"model\":\"test-model\",\"data\":[{\"index\":0,\"embedding\":[1,0,0]},{\"index\":0,\"embedding\":[0,1,0]}]}";
        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).embed(List.of("one", "two")))
                .isInstanceOf(EmbeddingUnavailableException.class);
    }

    @Test
    void httpFailureDoesNotEchoProviderBodyKeyOrSensitiveInput() {
        status = 429;
        body = "test-api-key Authorization: private note";
        assertThatThrownBy(() -> client(Duration.ofSeconds(2)).embed(List.of("private note")))
                .isInstanceOf(EmbeddingUnavailableException.class).hasNoCause()
                .hasMessageNotContaining("test-api-key").hasMessageNotContaining("Authorization").hasMessageNotContaining("private note");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeoutBoundsHeadersAndStalledBody(boolean stallBody) {
        delayMillis = 1500;
        delayBody = stallBody;
        long started = System.nanoTime();
        assertThatThrownBy(() -> client(Duration.ofMillis(200)).embed(List.of("one", "two")))
                .isInstanceOf(EmbeddingUnavailableException.class).hasNoCause();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void validatorRejectsWrongBatchDimensionsNonFiniteAndZeroVectors() {
        assertThatThrownBy(() -> EmbeddingVectors.validate(List.of(new float[]{1, 0}), 1, 3)).isInstanceOf(EmbeddingUnavailableException.class);
        assertThatThrownBy(() -> EmbeddingVectors.validate(List.of(new float[]{1, 0, 0}), 2, 3)).isInstanceOf(EmbeddingUnavailableException.class);
        assertThatThrownBy(() -> EmbeddingVectors.validate(List.of(new float[]{Float.NaN, 0, 0}), 1, 3)).isInstanceOf(EmbeddingUnavailableException.class);
        assertThatThrownBy(() -> EmbeddingVectors.validate(List.of(new float[]{Float.POSITIVE_INFINITY, 0, 0}), 1, 3)).isInstanceOf(EmbeddingUnavailableException.class);
        assertThatThrownBy(() -> EmbeddingVectors.validate(List.of(new float[]{0, 0, 0}), 1, 3)).isInstanceOf(EmbeddingUnavailableException.class);
    }
}
