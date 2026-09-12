package com.knowledgeapplication.api.knowledge.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "knowledge",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_knowledge_slug", columnNames = "slug"),
                @UniqueConstraint(name = "uk_knowledge_share_token", columnNames = "share_token")
        },
        indexes = @Index(name = "idx_knowledge_owner_id", columnList = "owner_id")
)
public class Knowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String title;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, updatable = false, length = 200)
    private String slug;

    @Column(columnDefinition = "text")
    private String summary;

    @NotNull
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Size(max = 43)
    @Column(name = "share_token", length = 43)
    private String shareToken;

    @Column(name = "share_token_created_at")
    private Instant shareTokenCreatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private KnowledgeCollection collection;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "knowledge_tag",
            joinColumns = @JoinColumn(name = "knowledge_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new LinkedHashSet<>();

    protected Knowledge() {
    }

    private Knowledge(
            UUID ownerId,
            String title,
            String slug,
            String summary,
            String content,
            Visibility visibility
    ) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.title = requireText(title, "title");
        this.slug = requireText(slug, "slug");
        this.summary = summary;
        this.content = Objects.requireNonNull(content, "content must not be null");
        changeVisibility(Objects.requireNonNull(visibility, "visibility must not be null"));
    }

    public static Knowledge create(UUID ownerId, String title, String summary, String content) {
        return create(ownerId, title, SlugGenerator.fromTitle(title), summary, content, Visibility.PRIVATE);
    }

    public static Knowledge create(
            UUID ownerId,
            String title,
            String slug,
            String summary,
            String content,
            Visibility visibility
    ) {
        return new Knowledge(ownerId, title, slug, summary, content, visibility);
    }

    public void rename(String title) {
        this.title = requireText(title, "title");
    }

    public void updateSummary(String summary) {
        this.summary = summary;
    }

    public void updateContent(String content) {
        this.content = Objects.requireNonNull(content, "content must not be null");
    }

    public void changeVisibility(Visibility visibility) {
        this.visibility = Objects.requireNonNull(visibility, "visibility must not be null");
        if (visibility == Visibility.PUBLIC && publishedAt == null) {
            publishedAt = Instant.now();
        }
    }

    public void replaceShareToken(String shareToken) {
        this.shareToken = requireText(shareToken, "shareToken");
        this.shareTokenCreatedAt = Instant.now();
    }

    public void replaceMetadata(KnowledgeCollection collection, Set<Tag> tags) {
        if (collection != null && !ownerId.equals(collection.getOwnerId())) {
            throw new IllegalArgumentException("collection must belong to Knowledge owner");
        }
        for (Tag tag : tags) {
            if (!ownerId.equals(tag.getOwnerId())) {
                throw new IllegalArgumentException("tags must belong to Knowledge owner");
            }
        }

        this.collection = collection;
        this.tags.clear();
        this.tags.addAll(tags);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public Long getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public String getSlug() {
        return slug;
    }

    public String getSummary() {
        return summary;
    }

    public String getContent() {
        return content;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getShareToken() {
        return shareToken;
    }

    public Instant getShareTokenCreatedAt() {
        return shareTokenCreatedAt;
    }

    public KnowledgeCollection getCollection() {
        return collection;
    }

    public Set<Tag> getTags() {
        return Collections.unmodifiableSet(tags);
    }
}
