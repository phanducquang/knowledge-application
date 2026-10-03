package com.knowledgeapplication.api.ask;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.exception.ApiExceptionHandler;
import com.knowledgeapplication.api.knowledge.embedding.*;
import com.knowledgeapplication.api.knowledge.model.*;
import com.knowledgeapplication.api.knowledge.service.KnowledgeService;
import com.knowledgeapplication.api.knowledge.revision.service.KnowledgeRevisionService;
import com.knowledgeapplication.api.ai.quota.*;
import com.knowledgeapplication.api.testsupport.TestContainerImages;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.knowledgeapplication.api.ask.AskServiceTest.*;

@Testcontainers @SpringBootTest
class AskIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>(TestContainerImages.POSTGRES);
    static final UUID OTHER=UUID.fromString("00000000-0000-0000-0000-000000000002");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl); r.add("spring.datasource.username",POSTGRES::getUsername); r.add("spring.datasource.password",POSTGRES::getPassword);
        r.add("app.auth.allowed-email",()->"owner@example.com"); r.add("app.object-storage.endpoint",()->"http://127.0.0.1:19000");
        r.add("app.object-storage.bucket",()->"unused-ask-tests"); r.add("app.object-storage.access-key",()->"test-key"); r.add("app.object-storage.secret-key",()->"test-secret");
        r.add("app.knowledge.attachment-cleanup.enabled",()->"false");
    }
    @Autowired KnowledgeEmbeddingRepository repository; @Autowired KnowledgeService knowledge; @Autowired KnowledgeRevisionService revisions;
    @Autowired CurrentOwner owner; @Autowired JdbcClient jdbc; @Autowired PlatformTransactionManager manager; @Autowired WebApplicationContext context;
    EmbeddingProperties ep; EmbeddingStrategy strategy; EmbeddingClient embedding; DeterministicAnswerClient answer; AskService service; MockMvc http;
    MutableClock clock; AiQuotaLimiter limiter;
    @BeforeEach void setup() {
        jdbc.sql("TRUNCATE ai_quota_usage, knowledge_attachment_delete_queue, knowledge, tag, knowledge_collection RESTART IDENTITY CASCADE").update();
        var token=new OidcIdToken("test-token",Instant.now().minusSeconds(60),Instant.now().plusSeconds(300),Map.of("sub","owner","email","owner@example.com","email_verified",true));
        var principal=new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_OWNER")),token);
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(principal,principal.getAuthorities(),"google"));
        ep=embeddingProps(true); strategy=new EmbeddingStrategy(ep,1); embedding=mock(EmbeddingClient.class);
        when(embedding.embed(anyList())).thenAnswer(inv->{ assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty(); return List.of(new float[]{1,0,0}); });
        answer=new DeterministicAnswerClient() { @Override public AnswerDraft answer(Request request) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
            return super.answer(request); } };
        service=new AskService(owner,askProps(true),ep,strategy,provider(EmbeddingClient.class,embedding),provider(KnowledgeAnswerClient.class,answer),repository,new ObjectMapper());
        http=MockMvcBuilders.standaloneSetup(new AskController(service)).setControllerAdvice(new ApiExceptionHandler())
                .apply(springSecurity(context.getBean("springSecurityFilterChain",jakarta.servlet.Filter.class))).addFilters(new AskRequestSizeFilter()).build();
        clock=new MutableClock(Instant.parse("2026-10-03T12:00:00Z")); limiter=new AiQuotaLimiter(jdbc,manager,clock);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    static class MutableClock extends Clock {
        Instant now; MutableClock(Instant now) { this.now=now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; } @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    Knowledge create(String title) { return knowledge.create(title,null,"Current "+title,Visibility.PRIVATE,null,List.of()); }
    void persist(long id,UUID ownerId,EmbeddingStrategy selected,List<String> texts,List<float[]> vectors) {
        var chunks=new ArrayList<MarkdownChunker.Chunk>(); for(int i=0;i<texts.size();i++) chunks.add(new MarkdownChunker.Chunk(i,texts.get(i)));
        assertThat(repository.replace(repository.findSource(ownerId,id).orElseThrow(),selected,chunks,vectors)).isTrue();
    }
    void persist(Knowledge note) { persist(note.getId(),OWNER,strategy,List.of(note.getContent()),List.of(new float[]{1,0,0})); }
    static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor login() {
        return oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_OWNER")).idToken(t->t.claim("email","owner@example.com").claim("email_verified",true));
    }
    @Test void ragPerNoteCapPrecedesGlobalLimitAndSourcesDeduplicateInDeterministicOrder() {
        var a=create("A"); var b=create("B"); var c=create("C");
        persist(a.getId(),OWNER,strategy,List.of("a0","a1","a2","a3"),Collections.nCopies(4,new float[]{1,0,0})); persist(b); persist(c);
        var chunks=repository.findRagChunks(OWNER,strategy,new float[]{1,0,0},4,2);
        assertThat(chunks).extracting(KnowledgeEmbeddingRepository.RagChunk::id).containsExactly(a.getId(),a.getId(),b.getId(),c.getId());
        assertThat(chunks).extracting(KnowledgeEmbeddingRepository.RagChunk::chunkIndex).containsExactly(0,1,0,0);
        assertThat(service.ask("q").citations().stream().map(citation -> citation.source().id()).distinct()).containsExactly(a.getId(),b.getId(),c.getId());
        assertThat(answer.requests).hasSize(1); verify(embedding).embed(List.of("q"));
    }
    @Test void currentSetExcludesForeignModelDimensionVersionHashAndIncompleteBeforeDistance() {
        var current=create("Current"); persist(current);
        long foreign=jdbc.sql("INSERT INTO knowledge(owner_id,title,slug,content) VALUES(:owner,'Other','other','Other') RETURNING id").param("owner",OTHER).query(Long.class).single();
        persist(foreign,OTHER,strategy,List.of("foreign private"),List.of(new float[]{1,0,0}));
        for(String kind:List.of("model","dimensions","version","hash","incomplete")) {
            var note=create(kind); persist(note.getId(),OWNER,strategy,List.of("a","b"),Collections.nCopies(2,new float[]{1,0,0}));
            String sql=switch(kind) {
                case "model" -> "UPDATE knowledge_embedding_chunk SET embedding_model='old' WHERE knowledge_id=:id";
                case "dimensions" -> "UPDATE knowledge_embedding_chunk SET embedding='[1,0]',embedding_dimensions=2 WHERE knowledge_id=:id";
                case "version" -> "UPDATE knowledge_embedding_chunk SET chunker_version=2 WHERE knowledge_id=:id";
                case "hash" -> "UPDATE knowledge_embedding_chunk SET source_hash=repeat('0',64) WHERE knowledge_id=:id";
                default -> "DELETE FROM knowledge_embedding_chunk WHERE knowledge_id=:id AND chunk_index=1";
            }; jdbc.sql(sql).param("id",note.getId()).update();
        }
        assertThat(service.ask("q").citations()).extracting(citation -> citation.source().id()).containsExactly(current.getId());
        assertThat(answer.requests.get(0).referenceData()).doesNotContain("foreign private");
    }
    @Test void editRestoreAndDeleteUseOnlyCurrentIndexedStateAndNeverPersistQuestionAnswer() {
        var note=create("Revision"); long original=revisions.list(note.getId(),0,20).getContent().get(0).getId(); persist(note);
        knowledge.update(note.getId(),note.getTitle(),null,"New current",Visibility.PRIVATE,null,List.of());
        assertThat(service.ask("not stored").status()).isEqualTo(AskResponse.Status.NO_CONTEXT); assertThat(answer.requests).isEmpty();
        var indexer=new KnowledgeEmbeddingIndexer(repository,provider(EmbeddingClient.class,new DeterministicEmbeddingClient(3)),ep,strategy,new MarkdownChunker(4000,200),OWNER);
        indexer.indexOnce(); service.ask("not stored"); assertThat(answer.requests.get(0).referenceData()).contains("New current").doesNotContain("Current Revision");
        knowledge.restoreRevision(note.getId(),original); assertThat(service.ask("q").status()).isEqualTo(AskResponse.Status.NO_CONTEXT);
        indexer.indexOnce(); service.ask("q"); assertThat(answer.requests.get(1).referenceData()).contains("Current Revision");
        knowledge.delete(note.getId()); assertThat(service.ask("q").status()).isEqualTo(AskResponse.Status.NO_CONTEXT);
        assertThat(jdbc.sql("SELECT count(*) FROM knowledge_embedding_chunk").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('conversation','chat_message','ask_question','ask_answer')").query(Long.class).single()).isZero();
    }
    @Test void allVisibilityStatesRemainPrivateWorkspaceContext() {
        for(var visibility:Visibility.values()) { var note=knowledge.create(visibility.name(),null,"Current "+visibility,visibility,null,List.of()); persist(note); }
        assertThat(service.ask("q").citations()).hasSize(3);
    }
    @Test void httpSessionCsrfQuestionValidationUnknownFieldsDisabledAndHealth() throws Exception {
        var note=create("HTTP"); persist(note);
        http.perform(post("/api/ask").with(login()).with(csrf()).contentType("application/json").content("{\"question\":\" How? \"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store, max-age=0"))
                .andExpect(jsonPath("$.status").value("ANSWERED")).andExpect(jsonPath("$.citations[0].source.id").value(note.getId()))
                .andExpect(jsonPath("$.citations[0].source.ownerId").doesNotExist()).andExpect(jsonPath("$.citations[0].distance").doesNotExist())
                .andExpect(jsonPath("$.answer.blocks[0].citationIds[0]").value("C1"))
                .andExpect(jsonPath("$.citations[0].evidence").value(note.getContent()));
        http.perform(post("/api/ask").with(anonymous()).contentType("application/json").content("{\"question\":\"q\"}")).andExpect(status().isUnauthorized());
        http.perform(post("/api/ask").with(login()).contentType("application/json").content("{\"question\":\"q\"}")).andExpect(status().isForbidden());
        for(String question:List.of("","  ","x".repeat(2001))) http.perform(post("/api/ask").with(login()).with(csrf())
                .contentType("application/json").content(new ObjectMapper().writeValueAsString(Map.of("question",question))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        var real=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(new AskRequestSizeFilter()).build();
        real.perform(post("/api/ask").servletPath("/api/ask").with(login()).with(csrf()).contentType("application/json")
                .content("{\"question\":\""+"x".repeat(16384)+"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(header().string("Cache-Control","private, no-store, max-age=0"));
        real.perform(post("/api/ask").with(login()).with(csrf()).contentType("application/json").content("{\"question\":\"q\",\"ownerId\":\"other\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        real.perform(post("/api/ask").with(login()).with(csrf()).contentType("application/json").content("{\"question\":\"q\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ASK_DISABLED"));
        real.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        verify(embedding,times(1)).embed(List.of("How?")); assertThat(answer.requests).hasSize(1);
    }
    @Test void safeHttpRetrievalAndGenerationFailuresAndNoContext() throws Exception {
        var note=create("Failure");
        http.perform(post("/api/ask").with(login()).with(csrf()).contentType("application/json").content("{\"question\":\"q\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_CONTEXT")).andExpect(jsonPath("$.citations").isEmpty());
        when(embedding.embed(anyList())).thenThrow(new EmbeddingQuotaUnavailableException());
        http.perform(post("/api/ask").with(login()).with(csrf()).contentType("application/json").content("{\"question\":\"q\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ASK_RETRIEVAL_UNAVAILABLE"));
        doReturn(List.of(new float[]{1,0,0})).when(embedding).embed(anyList()); persist(note); answer.failure=new IllegalStateException("private key");
        http.perform(post("/api/ask").with(login()).with(csrf()).contentType("application/json").content("{\"question\":\"q\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ASK_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Answer generation is temporarily unavailable"));
    }
    AiQuotaProperties quota(long rpm,long tpm,long rpd,boolean enabled,AiQuotaProperties.Budget background) {
        return new AiQuotaProperties(enabled,rpm,tpm,rpd,2.5,ZoneId.of("America/Los_Angeles"),background);
    }
    @Test void rpmAndTpmAndIndependentAskBudgetEnforced() {
        var q=quota(2,100,10,true,null);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,125)).isTrue();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,125)).isTrue();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,1)).isFalse();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.ASK,q,251)).isFalse(); // ceil(251/2.5)=101
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.ASK,q,250)).isTrue();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.ASK,q,1)).isFalse();
        clock.now=clock.now.plusSeconds(60); assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,1)).isTrue();
    }
    @Test void backgroundCapReservesInteractiveCapacityAndGlobalAlwaysWins() {
        var q=quota(4,100,5,true,new AiQuotaProperties.Budget(2,80,3));
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,q,10)).isTrue();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,q,10)).isTrue();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,q,10)).isFalse();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,10)).isTrue();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,10)).isTrue();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,10)).isFalse();
        clock.now=clock.now.plusSeconds(60);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,10)).isTrue(); // daily global=5
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,q,10)).isFalse();
        long bg=jdbc.sql("SELECT sum(request_count) FROM ai_quota_usage WHERE quota_key='embedding-background-day'").query(Long.class).single();
        assertThat(bg).isEqualTo(2); // Denied reservations never partially consume the other budget.
    }
    @Test void backgroundTpmAndRpdRemainBelowGlobalBudgetAcrossMinuteWindows() {
        var q=quota(10,100,10,true,new AiQuotaProperties.Budget(5,10,2));
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,q,25)).isTrue();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,q,1)).isFalse(); // background TPM only
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,25)).isTrue();
        clock.now=clock.now.plusSeconds(60);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,q,1)).isTrue();
        clock.now=clock.now.plusSeconds(60);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,q,1)).isFalse(); // background RPD only
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,1)).isTrue();
        assertThat(jdbc.sql("SELECT request_count FROM ai_quota_usage WHERE quota_key='embedding-background-day'").query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT request_count FROM ai_quota_usage WHERE quota_key='embedding-global-day'").query(Long.class).single()).isEqualTo(4);
    }
    @Test void globalTpmDenialDoesNotConsumeAvailableBackgroundCapacity() {
        var q=quota(10,100,10,true,new AiQuotaProperties.Budget(5,80,5));
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,250)).isTrue();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,q,1)).isFalse();
        assertThat(jdbc.sql("SELECT coalesce(sum(request_count),0) FROM ai_quota_usage WHERE quota_key='embedding-background-day'").query(Long.class).single()).isZero();
        clock.now=clock.now.plusSeconds(60);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,q,1)).isTrue();
    }
    @Test void dailyRpdSurvivesNewLimiterAndResetsAtPacificMidnightIncludingDst() {
        clock.now=Instant.parse("2026-10-03T06:59:50Z");
        var q=quota(100,10000,1,true,null);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,10)).isTrue();
        var reconstructed=new AiQuotaLimiter(jdbc,manager,clock);
        assertThat(reconstructed.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,10)).isFalse();
        clock.now=Instant.parse("2026-10-03T07:00:01Z"); // Pacific midnight, not UTC/Vietnam midnight.
        assertThat(reconstructed.reserve(AiQuotaLimiter.Purpose.EMBEDDING,q,10)).isTrue();
        clock.now=Instant.parse("2026-11-01T09:00:00Z"); assertThat(reconstructed.reserve(AiQuotaLimiter.Purpose.ASK,q,10)).isTrue();
        assertThat(jdbc.sql("SELECT extract(epoch FROM(window_end-window_start))::bigint FROM ai_quota_usage WHERE quota_key='ask-generation-day'").query(Long.class).single()).isEqualTo(25*3600L);
    }
    @Test void disabledQuotaCreatesNoUsageAndConcurrentReservationsAreAtomic() throws Exception {
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.ASK,quota(1,1,1,false,null),10000)).isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM ai_quota_usage").query(Long.class).single()).isZero();
        var pool=Executors.newFixedThreadPool(8); var q=quota(100,10000,5,true,null);
        try {
            List<Callable<Boolean>> attempts=new ArrayList<>(); for(int i=0;i<20;i++) attempts.add(()->new AiQuotaLimiter(jdbc,manager,clock).reserve(AiQuotaLimiter.Purpose.ASK,q,10));
            int accepted=0; for(var future:pool.invokeAll(attempts)) if(future.get()) accepted++;
            assertThat(accepted).isEqualTo(5);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.sql("SELECT request_count FROM ai_quota_usage WHERE quota_key='ask-generation-day'").query(Long.class).single()).isEqualTo(5);
    }
    @ParameterizedTest @ValueSource(ints={3,4}) void modelAndDimensionMigrationAutomaticallyReindexWithoutDeletion(int dimensions) {
        var note=create("Migration"); persist(note);
        var changed=new EmbeddingProperties(true,ep.baseUrl(),"model-B",dimensions,ep.connectTimeout(),ep.readTimeout(),16,false,ep.interval(),ep.initialDelay(),10,4000,200);
        var next=new EmbeddingStrategy(changed,1);
        assertThat(repository.state(OWNER,note.getId(),next)).isEqualTo(KnowledgeEmbeddingRepository.IndexState.STALE);
        assertThat(repository.findNearestKnowledge(OWNER,next,new DeterministicEmbeddingClient(dimensions).embed(List.of("q")).get(0),20)).isEmpty();
        assertThat(repository.findRagChunks(OWNER,next,new DeterministicEmbeddingClient(dimensions).embed(List.of("q")).get(0),8,2)).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM knowledge_embedding_chunk").query(Long.class).single()).isEqualTo(1);
        var indexer=new KnowledgeEmbeddingIndexer(repository,provider(EmbeddingClient.class,new DeterministicEmbeddingClient(dimensions)),changed,next,new MarkdownChunker(4000,200),OWNER);
        assertThat(indexer.indexOnce().indexed()).isEqualTo(1); assertThat(repository.state(OWNER,note.getId(),next)).isEqualTo(KnowledgeEmbeddingRepository.IndexState.CURRENT);
    }
    @Test void quotaLimitedFullReindexStopsAndNextWindowContinuesOnlyPendingNotes() {
        var notes=List.of(create("A"),create("B"),create("C"),create("D")); for(var note:notes) persist(note);
        var changed=new EmbeddingProperties(true,ep.baseUrl(),"new-model",3,ep.connectTimeout(),ep.readTimeout(),16,false,ep.interval(),ep.initialDelay(),10,4000,200);
        var next=new EmbeddingStrategy(changed,1); var q=quota(10,10000,20,true,new AiQuotaProperties.Budget(2,9000,10));
        var calls=new AtomicInteger(); var fake=new DeterministicEmbeddingClient(3);
        EmbeddingClient budgeted=new EmbeddingClient() {
            @Override public List<float[]> embed(List<String> inputs) { return fake.embed(inputs); }
            @Override public List<float[]> embedBackground(List<String> inputs) {
                if(!limiter.reserve(AiQuotaLimiter.Purpose.EMBEDDING_BACKGROUND,q,inputs.stream().mapToLong(String::length).sum())) throw new EmbeddingQuotaUnavailableException();
                calls.incrementAndGet(); return fake.embed(inputs);
            }
        };
        var indexer=new KnowledgeEmbeddingIndexer(repository,provider(EmbeddingClient.class,budgeted),changed,next,new MarkdownChunker(4000,200),OWNER);
        assertThat(indexer.indexOnce().indexed()).isEqualTo(2); assertThat(calls.get()).isEqualTo(2);
        assertThat(repository.findPending(OWNER,next,10,0)).hasSize(2);
        clock.now=clock.now.plusSeconds(60); indexer.indexOnce(); indexer.indexOnce();
        assertThat(calls.get()).isEqualTo(4); assertThat(repository.findPending(OWNER,next,10,0)).isEmpty();
        assertThat(jdbc.sql("SELECT request_count FROM ai_quota_usage WHERE quota_key='embedding-global-day'").query(Long.class).single()).isEqualTo(4);
    }
    @Test void citationsPreserveExactPgvectorChunkIdentityAndNoExtraProviderCalls() {
        var note=create("Evidence");
        persist(note.getId(),OWNER,strategy,List.of("Chunk zero evidence.","Chunk one evidence."),Collections.nCopies(2,new float[]{1,0,0}));
        answer.output=new AnswerDraft(List.of(new AnswerDraft.Block("First",List.of("S2","S1")),new AnswerDraft.Block("Again",List.of("S2"))));
        var result=service.ask("q");
        assertThat(result.citations()).extracting(AskResponse.Citation::chunkIndex).containsExactly(1,0);
        assertThat(result.citations()).extracting(AskResponse.Citation::evidence).containsExactly("Chunk one evidence.","Chunk zero evidence.");
        assertThat(result.citations()).extracting(c -> c.source().slug()).containsOnly(note.getSlug());
        assertThat(result.answer().blocks().get(1).citationIds()).containsExactly("C1");
        verify(embedding,times(1)).embed(List.of("q")); assertThat(answer.requests).hasSize(1);
        answer.output=new AnswerDraft(List.of(new AnswerDraft.Block("Known plus forged",List.of("S1","S999"))));
        assertThatThrownBy(()->service.ask("q")).isInstanceOfSatisfying(AskUnavailableException.class,ex -> assertThat(ex.code()).isEqualTo("ASK_UNAVAILABLE"));
        assertThat(answer.requests).hasSize(2); verify(embedding,times(2)).embed(List.of("q"));
    }
    @Test void concurrentEditDuringGenerationPreservesExplicitRetrievedSnapshotWithoutRowLock() {
        var note=create("Snapshot"); persist(note);
        KnowledgeAnswerClient generator=request -> {
            assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
            knowledge.update(note.getId(),"New title",null,"New current content",Visibility.PRIVATE,null,List.of());
            return new AnswerDraft(List.of(new AnswerDraft.Block("Snapshot statement",List.of("S1"))));
        };
        var snapshotService=new AskService(owner,askProps(true),ep,strategy,provider(EmbeddingClient.class,embedding),provider(KnowledgeAnswerClient.class,generator),repository,new ObjectMapper());
        var response=snapshotService.ask("q");
        assertThat(response.citations().get(0).source().title()).isEqualTo("Snapshot");
        assertThat(response.citations().get(0).evidence()).isEqualTo("Current Snapshot");
        assertThat(service.ask("q").status()).isEqualTo(AskResponse.Status.NO_CONTEXT);
        assertThat(jdbc.sql("SELECT count(*) FROM information_schema.tables WHERE table_name IN ('ask_citation','answer_block','ask_evidence')").query(Long.class).single()).isZero();
    }
}
