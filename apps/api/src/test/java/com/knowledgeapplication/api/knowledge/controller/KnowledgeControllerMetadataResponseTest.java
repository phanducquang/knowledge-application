package com.knowledgeapplication.api.knowledge.controller;

import com.knowledgeapplication.api.knowledge.dto.KnowledgeResponse;
import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.KnowledgeCollection;
import com.knowledgeapplication.api.knowledge.model.Tag;
import com.knowledgeapplication.api.knowledge.model.Visibility;
import com.knowledgeapplication.api.knowledge.service.KnowledgeService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeControllerMetadataResponseTest {

    @Test
    void returnsCollectionAndSortedTagsForIdSlugAndList() {
        UUID ownerId = UUID.randomUUID();
        Knowledge knowledge = Knowledge.create(
                ownerId,
                "Metadata response",
                "metadata-response",
                null,
                "",
                Visibility.PRIVATE
        );
        knowledge.replaceMetadata(
                KnowledgeCollection.create(ownerId, "Backend"),
                Set.of(Tag.create(ownerId, "WebClient"), Tag.create(ownerId, "Spring Boot"))
        );

        KnowledgeService service = mock(KnowledgeService.class);
        when(service.getById(1L)).thenReturn(knowledge);
        when(service.getBySlug("metadata-response")).thenReturn(knowledge);
        when(service.list()).thenReturn(List.of(knowledge));
        KnowledgeController controller = new KnowledgeController(service);

        KnowledgeResponse byId = controller.getById(1L);
        KnowledgeResponse bySlug = controller.getBySlug("metadata-response");
        KnowledgeResponse fromList = controller.list().get(0);

        assertThat(byId.collection()).isEqualTo("Backend");
        assertThat(byId.tags()).containsExactly("Spring Boot", "WebClient");
        assertThat(bySlug).isEqualTo(byId);
        assertThat(fromList).isEqualTo(byId);
    }
}
