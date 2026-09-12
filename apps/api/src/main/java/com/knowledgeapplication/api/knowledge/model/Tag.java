package com.knowledgeapplication.api.knowledge.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "tag",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tag_owner_normalized_name",
                columnNames = {"owner_id", "normalized_name"}
        )
)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false, length = 50)
    private String name;

    @Column(name = "normalized_name", insertable = false, updatable = false, length = 50)
    private String normalizedName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tag() {
    }

    private Tag(UUID ownerId, String name) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.name = MetadataNameNormalizer.tagDisplayName(name);
    }

    public static Tag create(UUID ownerId, String name) {
        return new Tag(ownerId, name);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
