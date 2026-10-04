package com.knowledgeapplication.api.metadata;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.service.*;
import com.knowledgeapplication.api.ai.quota.*;
import com.knowledgeapplication.api.testsupport.TestContainerImages;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers @SpringBootTest
class MetadataIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>(TestContainerImages.POSTGRES);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl); r.add("spring.datasource.username",DB::getUsername); r.add("spring.datasource.password",DB::getPassword);
        r.add("app.auth.allowed-email",()->"owner@example.com"); r.add("app.ai.metadata.enabled",()->"true");
        r.add("app.knowledge.attachment-cleanup.enabled",()->"false"); r.add("app.ai.quota-cleanup.enabled",()->"false");
        r.add("app.object-storage.endpoint",()->"http://127.0.0.1:19000"); r.add("app.object-storage.bucket",()->"unused-metadata-tests");
        r.add("app.object-storage.access-key",()->"test-key"); r.add("app.object-storage.secret-key",()->"test-secret");
    }
    @MockitoBean KnowledgeMetadataSuggestionClient client;
    @Autowired MetadataSuggestionService service; @Autowired MetadataSnapshotLoader snapshots; @Autowired MetadataProperties properties;
    @Autowired KnowledgeService knowledge; @Autowired CurrentOwner owner; @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager manager; @Autowired WebApplicationContext context;
    MockMvc http;
    @BeforeEach void setup() {
        jdbc.sql("TRUNCATE ai_quota_usage, knowledge_attachment_delete_queue, knowledge, tag, knowledge_collection RESTART IDENTITY CASCADE").update();
        var token=new OidcIdToken("test-token",Instant.now().minusSeconds(60),Instant.now().plusSeconds(300),Map.of("sub","owner","email","owner@example.com","email_verified",true));
        var principal=new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_OWNER")),token);
        SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(principal,principal.getAuthorities(),"google"));
        when(client.suggest(any())).thenReturn(new MetadataSuggestion("Concise summary",List.of("Spring Boot","Timeout")));
        http=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    long create() { return knowledge.create("Current title","Existing summary","Current content",Visibility.PRIVATE,null,List.of("Spring Boot")).getId(); }
    @Test void explicitOnlyCurrentOwnerSnapshotNoTransactionNoWritesEvenWithAmbientTransaction() {
        long id=create(); verifyNoInteractions(client);
        var before=jdbc.sql("SELECT title,summary,content,updated_at FROM knowledge WHERE id=:id").param("id",id).query().singleRow();
        long revisions=jdbc.sql("SELECT count(*) FROM knowledge_revision").query(Long.class).single();
        when(client.suggest(any())).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
            var input=inv.getArgument(0,KnowledgeMetadataSuggestionClient.Request.class);
            assertThat(input.title()).isEqualTo("Current title"); assertThat(input.summary()).isEqualTo("Existing summary");
            assertThat(input.content()).isEqualTo("Current content"); assertThat(input.currentTags()).containsExactly("Spring Boot");
            return new MetadataSuggestion("  concise  ",List.of("spring boot"," Timeout ","timeout","Docker"));
        });
        var result=new TransactionTemplate(manager).execute(s -> service.suggest(id));
        assertThat(result).isEqualTo(new MetadataSuggestion("concise",List.of("Timeout","Docker"))); verify(client,times(1)).suggest(any());
        assertThat(jdbc.sql("SELECT title,summary,content,updated_at FROM knowledge WHERE id=:id").param("id",id).query().singleRow()).isEqualTo(before);
        assertThat(jdbc.sql("SELECT count(*) FROM knowledge_revision").query(Long.class).single()).isEqualTo(revisions);
        assertThat(knowledge.getById(id).getTags()).hasSize(1);
        // Explicit apply uses the existing authoring service, not an AI-write path or second provider call.
        var current=knowledge.getById(id);
        knowledge.update(id,current.getTitle(),result.summary(),current.getContent(),current.getVisibility(),null,List.of("Spring Boot","Timeout","Docker"));
        var applied=knowledge.getById(id);
        assertThat(applied.getSummary()).isEqualTo("concise"); assertThat(applied.getContent()).isEqualTo("Current content");
        assertThat(applied.getTags()).extracting(com.knowledgeapplication.api.knowledge.model.Tag::getName).containsExactlyInAnyOrder("Spring Boot","Timeout","Docker");
        assertThat(applied.getSlug()).isEqualTo(current.getSlug()); verify(client,times(1)).suggest(any());
    }
    @Test void unknownAndOtherOwnerReturnNotFoundBeforeProvider() {
        long id=jdbc.sql("INSERT INTO knowledge(owner_id,title,slug,content) VALUES(:owner,'Foreign','foreign','Private') RETURNING id")
                .param("owner",UUID.randomUUID()).query(Long.class).single();
        assertThatThrownBy(()->service.suggest(id)).isInstanceOf(KnowledgeNotFoundException.class);
        assertThatThrownBy(()->service.suggest(9999L)).isInstanceOf(KnowledgeNotFoundException.class); verifyNoInteractions(client);
    }
    @Test void deterministicPrefixBoundAndNoHistoryOrAttachmentReads() {
        long id=create(); String content="a".repeat(31999)+"😀"+"SECRET OMITTED TAIL";
        knowledge.update(id,"Current title",null,content,Visibility.PRIVATE,null,List.of());
        service.suggest(id);
        var capture=org.mockito.ArgumentCaptor.forClass(KnowledgeMetadataSuggestionClient.Request.class); verify(client).suggest(capture.capture());
        assertThat(capture.getValue().content()).isEqualTo("a".repeat(31999)); assertThat(capture.getValue().contentTruncated()).isTrue();
        assertThat(capture.getValue().toString()).doesNotContain("Current title","SECRET");
    }
    @Test void disabledServiceHasNoProviderCallsAndSafeCode() {
        var disabled=new MetadataSuggestionService(owner,new MetadataProperties(false,null,null,null,null,0,0,null),snapshots,
                context.getBeanProvider(KnowledgeMetadataSuggestionClient.class));
        assertThatThrownBy(()->disabled.suggest(create())).isInstanceOf(MetadataUnavailableException.class).hasMessage("AI metadata suggestions are disabled").hasNoCause();
        verifyNoInteractions(client);
    }
    @Test void providerErrorIsSanitized() {
        when(client.suggest(any())).thenThrow(new IllegalStateException("private provider body"));
        assertThatThrownBy(()->service.suggest(create())).isInstanceOf(MetadataUnavailableException.class).hasNoCause().hasMessage("AI metadata suggestions are currently unavailable");
        verify(client,times(1)).suggest(any());
    }
    @Test void authenticatedCsrfOwnerOnlyAndNoStoreApi() throws Exception {
        long id=create(); SecurityContextHolder.clearContext(); String path="/api/knowledge/"+id+"/ai/metadata-suggestions";
        http.perform(post(path)).andExpect(status().isUnauthorized());
        http.perform(post(path).with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_OWNER")))).andExpect(status().isForbidden());
        http.perform(post(path).with(login()).with(csrf())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store, max-age=0"))
                .andExpect(jsonPath("$.summary").value("Concise summary")).andExpect(jsonPath("$.tags[0]").value("Timeout"))
                .andExpect(jsonPath("$.ownerId").doesNotExist());
        http.perform(post("/api/knowledge/9999/ai/metadata-suggestions").with(login()).with(csrf())).andExpect(status().isNotFound());
        http.perform(post(path).with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());
        verify(client,times(1)).suggest(any());
    }
    static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor login() {
        return oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_OWNER")).idToken(t->t.claim("email","owner@example.com").claim("email_verified",true));
    }
    @ParameterizedTest @ValueSource(strings={"rpm","tpm","rpd"})
    void independentPersistentQuotaDenial(String kind) {
        var clock=Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"),ZoneOffset.UTC);
        var props=new AiQuotaProperties(true,kind.equals("rpm")?1:10,kind.equals("tpm")?1:1000,kind.equals("rpd")?1:400,2.5,ZoneId.of("America/Los_Angeles"),null);
        var limiter=new AiQuotaLimiter(jdbc,manager,clock);
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.METADATA,props,1)).isTrue();
        assertThat(new AiQuotaLimiter(jdbc,manager,clock).reserve(AiQuotaLimiter.Purpose.METADATA,props,10)).isFalse();
        assertThat(limiter.reserve(AiQuotaLimiter.Purpose.ASK,props,1)).isTrue();
        assertThat(jdbc.sql("SELECT request_count FROM ai_quota_usage WHERE quota_key='metadata-generation-day'").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM ai_quota_usage WHERE quota_key LIKE 'embedding-%'").query(Long.class).single()).isZero();
    }
    @Test void existingRetentionCleansMetadataSuffixesOnlyWhenExpired() {
        var now=Instant.parse("2026-10-04T12:00:00Z");
        for(String key:List.of("metadata-generation-minute","metadata-generation-day")) {
            jdbc.sql("INSERT INTO ai_quota_usage(quota_key,window_start,window_end) VALUES(:key,:start,:end)")
                    .param("key",key).param("start",now.minus(Duration.ofDays(42)).atOffset(ZoneOffset.UTC)).param("end",now.minus(Duration.ofDays(41)).atOffset(ZoneOffset.UTC)).update();
        }
        var cleanup=new AiQuotaCleanupService(jdbc,manager,new AiQuotaCleanupProperties(true,Duration.ofDays(2),Duration.ofDays(30),Duration.ofHours(6),Duration.ZERO,100),Clock.fixed(now,ZoneOffset.UTC));
        assertThat(cleanup.cleanupOnce()).isEqualTo(new AiQuotaCleanupService.Summary(true,1,1));
    }
    @Test void metadataReservationLockIsIndependentFromCleanupCoordination() {
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(manager).execute(status -> {
                jdbc.sql("SELECT pg_advisory_xact_lock(83452003)").query((row,n)->true).single();
                var future=pool.submit(()->new AiQuotaLimiter(jdbc,manager,Clock.systemUTC())
                        .reserve(AiQuotaLimiter.Purpose.METADATA,properties.quota(),10));
                try { assertThat(future.get(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                catch (Exception ex) { throw new AssertionError("Metadata quota must not wait on cleanup lock",ex); }
                return null;
            });
        } finally { pool.shutdownNow(); }
    }
}
