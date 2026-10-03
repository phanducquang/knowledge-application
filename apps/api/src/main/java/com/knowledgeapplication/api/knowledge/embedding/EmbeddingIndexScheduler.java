package com.knowledgeapplication.api.knowledge.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.embedding", name = {"enabled", "indexing-enabled"}, havingValue = "true")
public class EmbeddingIndexScheduler {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingIndexScheduler.class);
    private final KnowledgeEmbeddingIndexer indexer;

    public EmbeddingIndexScheduler(KnowledgeEmbeddingIndexer indexer) { this.indexer = indexer; }

    @Scheduled(fixedDelayString = "${app.embedding.interval:PT1M}", initialDelayString = "${app.embedding.initial-delay:PT30S}")
    public void index() {
        try {
            indexer.indexOnce();
        } catch (RuntimeException ex) {
            log.warn("Embedding indexing cycle unavailable; retrying on the next scheduled cycle");
        }
    }
}
