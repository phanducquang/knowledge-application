package com.knowledgeapplication.api.knowledge.relation;

import com.knowledgeapplication.api.knowledge.model.Knowledge;
import com.knowledgeapplication.api.knowledge.model.Tag;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record KnowledgeGraphResponse(List<Node> nodes, List<Edge> edges) {

    public record Node(Long id, String slug, String title, Long collectionId, String collection,
                       List<String> tags, Instant updatedAt) {
        static Node from(Knowledge knowledge) {
            return new Node(knowledge.getId(), knowledge.getSlug(), knowledge.getTitle(),
                    knowledge.getCollection() == null ? null : knowledge.getCollection().getId(),
                    knowledge.getCollection() == null ? null : knowledge.getCollection().getName(),
                    knowledge.getTags().stream().map(Tag::getName)
                            .sorted(Comparator.comparing((String name) -> name, String.CASE_INSENSITIVE_ORDER)
                                    .thenComparing(Comparator.naturalOrder()))
                            .toList(),
                    knowledge.getUpdatedAt());
        }
    }

    public record Edge(Long sourceId, Long targetId) {
    }
}
