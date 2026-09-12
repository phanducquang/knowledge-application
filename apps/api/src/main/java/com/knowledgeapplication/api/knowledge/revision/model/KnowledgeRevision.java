package com.knowledgeapplication.api.knowledge.revision.model;

import com.knowledgeapplication.api.knowledge.model.Knowledge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "knowledge_revision",
        indexes = @Index(
                name = "idx_knowledge_revision_knowledge_created",
                columnList = "knowledge_id, created_at DESC, id DESC"
        )
)
public class KnowledgeRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "knowledge_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_knowledge_revision_knowledge")
    )
    private Knowledge knowledge;

    @Column(nullable = false, updatable = false, length = 255)
    private String title;

    @Column(updatable = false, columnDefinition = "text")
    private String summary;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    @Column(name = "collection_name", updatable = false, length = 100)
    private String collectionName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<String> tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private KnowledgeRevisionReason reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected KnowledgeRevision() {
    }

    private KnowledgeRevision(
            Knowledge knowledge,
            String title,
            String summary,
            String content,
            String collectionName,
            List<String> tags,
            KnowledgeRevisionReason reason,
            Instant createdAt
    ) {
        this.knowledge = Objects.requireNonNull(knowledge, "knowledge must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.summary = summary;
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.collectionName = collectionName;
        this.tags = List.copyOf(tags);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static KnowledgeRevision snapshot(
            Knowledge knowledge,
            KnowledgeRevisionReason reason,
            Instant createdAt
    ) {
        return new KnowledgeRevision(
                knowledge,
                knowledge.getTitle(),
                knowledge.getSummary(),
                knowledge.getContent(),
                knowledge.getCollection() == null ? null : knowledge.getCollection().getName(),
                knowledge.getTags().stream()
                        .map(tag -> tag.getName())
                        .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo))
                        .toList(),
                reason,
                createdAt
        );
    }

    public boolean hasSameAuthoringState(Knowledge current) {
        return title.equals(current.getTitle())
                && Objects.equals(summary, current.getSummary())
                && content.equals(current.getContent())
                && Objects.equals(
                        collectionName,
                        current.getCollection() == null ? null : current.getCollection().getName()
                )
                && tags.equals(current.getTags().stream()
                        .map(tag -> tag.getName())
                        .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo))
                        .toList());
    }

    public Long getId() {
        return id;
    }

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getContent() {
        return content;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public KnowledgeRevisionReason getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
