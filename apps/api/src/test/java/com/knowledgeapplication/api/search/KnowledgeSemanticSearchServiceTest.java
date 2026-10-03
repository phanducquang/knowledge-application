package com.knowledgeapplication.api.search;

import com.knowledgeapplication.api.configuration.CurrentOwner;
import com.knowledgeapplication.api.knowledge.embedding.*;
import com.knowledgeapplication.api.search.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeSemanticSearchServiceTest {
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    CurrentOwner owner;
    EmbeddingClient client;
    KnowledgeEmbeddingRepository repository;

    static EmbeddingProperties properties(boolean enabled) {
        return new EmbeddingProperties(enabled, "http://localhost:12345/v1", "", "test-model", 3,
                Duration.ofSeconds(2), Duration.ofSeconds(5), 2, false, Duration.ofMinutes(1), Duration.ZERO, 10, 256, 20);
    }

    @BeforeEach void setUp() {
        owner = mock(CurrentOwner.class); client = mock(EmbeddingClient.class); repository = mock(KnowledgeEmbeddingRepository.class);
        when(owner.id()).thenReturn(OWNER);
    }

    KnowledgeSemanticSearchService service(boolean enabled, boolean hasClient) {
        var properties = properties(enabled);
        return new KnowledgeSemanticSearchService(owner, new StaticListableBeanFactory(hasClient ? Map.of("client", client) : Map.of())
                .getBeanProvider(EmbeddingClient.class), properties, new EmbeddingStrategy(properties, 1), repository);
    }

    @Test void embedsTrimmedQueryExactlyOnceOutsideTransactionAndUsesAuthenticatedOwner() {
        float[] vector = {1, 0, 0};
        when(client.embed(List.of("reverse proxy"))).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return List.of(vector);
        });
        var properties = properties(true);
        service(true, true).search("  reverse proxy  ", 20);
        verify(owner).id(); verify(client, times(1)).embed(List.of("reverse proxy"));
        verify(repository).findNearestKnowledge(OWNER, new EmbeddingStrategy(properties, 1), vector, 20);
        verifyNoMoreInteractions(client);
    }

    @Test void rejectsBlankNullAndLongQueriesWithoutCallingProvider() {
        for (String query : Arrays.asList(null, "", " \t ", "x".repeat(201)))
            assertThatThrownBy(() -> service(true, true).search(query, 20)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client, repository, owner);
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, 51})
    void rejectsInvalidLimits(int limit) {
        assertThatThrownBy(() -> service(true, true).search("query", limit)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client, repository, owner);
    }

    @Test void disabledOrMissingClientIsControlledWithoutProviderOrDatabaseCalls() {
        for (var service : List.of(service(false, true), service(true, false))) {
            assertThatThrownBy(() -> service.search("query", 20)).isInstanceOf(SemanticSearchUnavailableException.class)
                    .satisfies(error -> assertThat(((SemanticSearchUnavailableException) error).code()).isEqualTo("SEMANTIC_SEARCH_DISABLED"));
        }
        verifyNoInteractions(client, repository);
    }

    @Test void authorizationPrecedesAnyProviderCall() {
        when(owner.id()).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));
        assertThatThrownBy(() -> service(true, true).search("query", 20)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(client, repository);
    }

    @Test void rejectsInvalidVectorsWithoutLeakingInputsOrRetrying() {
        List<List<float[]>> invalid = new ArrayList<>();
        invalid.add(null); invalid.add(List.of()); invalid.add(List.of(new float[]{1, 0, 0}, new float[]{1, 0, 0}));
        invalid.add(List.of(new float[]{1, 0})); invalid.add(List.of(new float[]{0, 0, 0}));
        invalid.add(List.of(new float[]{Float.NaN, 0, 0})); invalid.add(Collections.singletonList(null));
        for (var response : invalid) {
            reset(client);
            when(client.embed(List.of("private query"))).thenReturn(response);
            assertThatThrownBy(() -> service(true, true).search("private query", 20))
                    .isInstanceOf(SemanticSearchUnavailableException.class).hasMessage("Semantic search is temporarily unavailable").hasNoCause();
            verify(client, times(1)).embed(List.of("private query"));
        }
        verifyNoInteractions(repository);
    }

    @Test void providerFailureIsSanitizedAndNeverRetried() {
        when(client.embed(anyList())).thenThrow(new IllegalStateException("secret-key Authorization upstream body private query"));
        assertThatThrownBy(() -> service(true, true).search("private query", 20))
                .hasMessage("Semantic search is temporarily unavailable").hasNoCause();
        verify(client, times(1)).embed(List.of("private query")); verifyNoInteractions(repository);
    }

    @Test void boundedExcerptIsPlainTextAndDoesNotSplitSurrogates() {
        String raw = "x".repeat(598) + "😀" + "<script>secret</script>";
        assertThat(KnowledgeSemanticSearchService.excerpt(raw)).isEqualTo("x".repeat(598) + "…");
        assertThat(KnowledgeSemanticSearchService.excerpt("<b>source</b>")).isEqualTo("<b>source</b>");
        assertThat(KnowledgeSemanticSearchService.excerpt("short 😀")).isEqualTo("short 😀");
    }
}
