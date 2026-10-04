package com.knowledgeapplication.api.metadata.eval;

import com.knowledgeapplication.api.ai.gemini.*;
import com.knowledgeapplication.api.ai.quota.*;
import com.knowledgeapplication.api.metadata.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.core.env.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

class MetadataEvaluationAdapterTest {
    HttpServer server; AtomicInteger calls=new AtomicInteger(); String body;
    int status=200; String output="{\"summary\":\"health endpoint readiness private\",\"tags\":[\"Actuator\"]}";
    final AiQuotaProperties quota=new AiQuotaProperties(true,10,200000,400,2.5,ZoneOffset.UTC,null);
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            calls.incrementAndGet(); body=new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            String response=JsonMapper.builder().build().writeValueAsString(Map.of("candidates",List.of(Map.of("finishReason","STOP","content",Map.of("parts",List.of(Map.of("text",output)))))));
            byte[] bytes=response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    MetadataEvaluation.Report run() {
        var limiter=mock(AiQuotaLimiter.class); when(limiter.reserve(any(),any(),anyLong())).thenReturn(true);
        var props=new MetadataProperties(true,"configured-candidate","http://127.0.0.1:"+server.getAddress().getPort(),Duration.ofSeconds(1),Duration.ofSeconds(2),600,32000,quota);
        var corpus=MetadataCorpus.load();
        try(var client=new GeminiKnowledgeMetadataSuggestionClient(props,new GeminiProperties("fake-loopback-key"),limiter)) {
            return MetadataEvaluation.run(corpus,client,MetadataEvaluation.Identity.create(corpus,"gemini-loopback",props.model(),32000,600,Instant.EPOCH),quota,c->{},true,20,150000);
        }
    }
    @ParameterizedTest @ValueSource(ints={429,500,503}) void realSdkFailureStopsWholeHarnessAtOneRequest(int code) {
        status=code; var report=run(); assertThat(report.status()).isEqualTo("INCOMPLETE"); assertThat(calls.get()).isEqualTo(1);
        assertThat(report.metrics()).isNull(); assertThat(report.usage().providerRequests()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"not json","{\"summary\":\"x\",\"tags\":[null]}","{\"summary\":\"x\",\"tags\":[],\"extra\":1}"})
    void malformedSdkOutputStopsWholeHarnessWithoutRetries(String invalid) {
        output=invalid; var report=run(); assertThat(report.status()).isEqualTo("INCOMPLETE"); assertThat(calls.get()).isEqualTo(1);
        assertThat(MetadataReportWriter.json(report)).doesNotContain(invalid);
    }
    @Test void liveConfigBindsModelEnvironmentWithoutMovingCredentials() throws Exception {
        var environment=new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("fake-eval",Map.of("app.ai.metadata.enabled",true,"app.gemini.api-key","fake-key","AI_METADATA_MODEL","model-from-environment")));
        environment.getPropertySources().addLast(new ResourcePropertySource(new ClassPathResource("metadata-suggestions.properties")));
        var settings=MetadataLiveSettings.bind(environment);
        assertThat(settings.metadata().model()).isEqualTo("model-from-environment"); assertThat(settings.metadata().maxOutputTokens()).isEqualTo(600);
        assertThat(settings.metadata().maxContentChars()).isEqualTo(32000); assertThat(settings.maxRequests()).isEqualTo(20);
        assertThat(settings.maxTokens()).isEqualTo(150000); assertThat(calls.get()).isZero();
        assertThat(settings.maxRpm()).isEqualTo(10); assertThat(settings.safetyMillis()).isEqualTo(250); assertThat(settings.resumeFrom()).isNull();
    }
    @Test void newEvaluationOnlySettingsAreConfigDriven() throws Exception {
        var environment=new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("fake-eval",Map.of("app.ai.metadata.enabled",true,"app.gemini.api-key","fake-key",
                "METADATA_EVAL_LIVE_MAX_RPM","12","METADATA_EVAL_LIVE_PACING_SAFETY_MS","321","METADATA_EVAL_RESUME_FROM","synthetic-prior.json")));
        environment.getPropertySources().addLast(new ResourcePropertySource(new ClassPathResource("metadata-suggestions.properties")));
        var settings=MetadataLiveSettings.bind(environment);
        assertThat(settings.maxRpm()).isEqualTo(12); assertThat(settings.safetyMillis()).isEqualTo(321); assertThat(settings.resumeFrom()).isEqualTo("synthetic-prior.json");
        assertThat(calls.get()).isZero();
    }
}
