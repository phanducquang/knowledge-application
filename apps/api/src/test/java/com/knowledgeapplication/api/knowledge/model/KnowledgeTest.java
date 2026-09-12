package com.knowledgeapplication.api.knowledge.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeTest {

    @Test
    void createsPrivateKnowledgeWithSlugFromTitle() {
        Knowledge knowledge = Knowledge.create(
                UUID.randomUUID(),
                "Cấu hình Spring Boot với PostgreSQL",
                "Ghi chú cấu hình",
                "# Nội dung"
        );

        assertThat(knowledge.getSlug()).isEqualTo("cau-hinh-spring-boot-voi-postgresql");
        assertThat(knowledge.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(knowledge.getContent()).isEqualTo("# Nội dung");
    }

    @Test
    void keepsSlugStableWhenTitleChanges() {
        Knowledge knowledge = Knowledge.create(
                UUID.randomUUID(),
                "Original title",
                null,
                "Content"
        );

        knowledge.rename("Updated title");

        assertThat(knowledge.getTitle()).isEqualTo("Updated title");
        assertThat(knowledge.getSlug()).isEqualTo("original-title");
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> Knowledge.create(UUID.randomUUID(), " ", null, "Content"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be blank");
    }

    @Test
    void recordsFirstPublicationAndKeepsItWhenVisibilityChanges() {
        Knowledge knowledge = Knowledge.create(
                UUID.randomUUID(),
                "Publication semantics",
                "publication-semantics",
                null,
                "",
                Visibility.PRIVATE
        );

        knowledge.changeVisibility(Visibility.PUBLIC);
        var firstPublishedAt = knowledge.getPublishedAt();
        knowledge.changeVisibility(Visibility.PRIVATE);
        knowledge.changeVisibility(Visibility.PUBLIC);

        assertThat(firstPublishedAt).isNotNull();
        assertThat(knowledge.getPublishedAt()).isEqualTo(firstPublishedAt);
    }

    @Test
    void preventsCrossOwnerCollectionAndTagAssociations() {
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        Knowledge knowledge = Knowledge.create(ownerId, "Owner boundary", null, "");
        KnowledgeCollection otherCollection = KnowledgeCollection.create(otherOwnerId, "Backend");
        Tag otherTag = Tag.create(otherOwnerId, "Spring Boot");

        assertThatThrownBy(() -> knowledge.replaceMetadata(otherCollection, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("collection must belong to Knowledge owner");
        assertThatThrownBy(() -> knowledge.replaceMetadata(null, Set.of(otherTag)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tags must belong to Knowledge owner");
    }

    @Test
    void allowsSameMetadataNamesForDifferentOwners() {
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();

        assertThat(KnowledgeCollection.create(firstOwner, "Backend").getName())
                .isEqualTo(KnowledgeCollection.create(secondOwner, "Backend").getName());
        assertThat(Tag.create(firstOwner, "Spring Boot").getName())
                .isEqualTo(Tag.create(secondOwner, "Spring Boot").getName());
    }
}
