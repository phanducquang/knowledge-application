package com.knowledgeapplication.api.knowledge.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class KnowledgeEmbeddingIndexer {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeEmbeddingIndexer.class);
    private final KnowledgeEmbeddingRepository repository;
    private final ObjectProvider<EmbeddingClient> client;
    private final EmbeddingProperties properties;
    private final EmbeddingStrategy strategy;
    private final MarkdownChunker chunker;
    private final UUID ownerId;
    private final ReentrantLock cycleLock = new ReentrantLock();
    private long cursor;

    public KnowledgeEmbeddingIndexer(KnowledgeEmbeddingRepository repository, ObjectProvider<EmbeddingClient> client,
            EmbeddingProperties properties, EmbeddingStrategy strategy, MarkdownChunker chunker,
            @Value("${app.owner-id}") UUID ownerId) {
        this.repository = repository;
        this.client = client;
        this.properties = properties;
        this.strategy = strategy;
        this.chunker = chunker;
        this.ownerId = ownerId;
    }

    public record Summary(int indexed, int chunks, int superseded, int failures) {}

    public Summary indexOnce() {
        if (!properties.enabled() || !cycleLock.tryLock()) return new Summary(0, 0, 0, 0);
        long started = System.nanoTime();
        int indexed = 0, chunkCount = 0, superseded = 0, failures = 0;
        try {
            var pending = repository.findPending(ownerId, strategy, properties.knowledgeBatchSize(), cursor);
            if (pending.isEmpty() && cursor > 0) {
                cursor = 0;
                pending = repository.findPending(ownerId, strategy, properties.knowledgeBatchSize(), cursor);
            }
            for (long id : pending) {
                cursor = id; // Failed early rows cannot starve later notes; retry after the next wrap.
                try {
                    var lease = repository.tryLock(id);
                    if (lease.isEmpty()) continue;
                    try (var ignored = lease.get()) {
                        var snapshot = repository.findSource(ownerId, id);
                        if (snapshot.isEmpty() || repository.state(ownerId, id, strategy) == KnowledgeEmbeddingRepository.IndexState.CURRENT) continue;
                        var source = snapshot.get();
                        List<MarkdownChunker.Chunk> chunks = chunker.chunk(source.content());
                        if (chunks.isEmpty()) {
                            // A saved empty Markdown note is still meaningful via its title/summary.
                            chunks = chunker.chunk(source.title() + (source.summary() == null ? "" : "\n\n" + source.summary()));
                        }
                        List<float[]> vectors = new ArrayList<>();
                        EmbeddingClient provider = client.getObject();
                        for (int offset = 0; offset < chunks.size(); offset += properties.batchSize()) {
                            var batch = chunks.subList(offset, Math.min(chunks.size(), offset + properties.batchSize()));
                            var inputs = batch.stream().map(chunk -> source.input(chunk.text())).toList();
                            var generated = provider.embedBackground(inputs);
                            EmbeddingVectors.validate(generated, inputs.size(), properties.dimensions());
                            vectors.addAll(generated);
                        }
                        if (repository.replace(source, strategy, chunks, vectors)) {
                            indexed++;
                            chunkCount += chunks.size();
                        } else superseded++;
                    }
                } catch (EmbeddingQuotaUnavailableException ex) {
                    log.info("Embedding indexing cycle stopped by quota/rate boundary; work remains pending");
                    break;
                } catch (RuntimeException ex) {
                    failures++;
                    // Do not attach the exception: providers/database errors may echo sensitive inputs.
                    log.warn("Embedding indexing failed; note remains pending for a later cycle");
                }
            }
            return new Summary(indexed, chunkCount, superseded, failures);
        } finally {
            log.info("Embedding indexing cycle completed. indexed={}, chunks={}, superseded={}, failures={}, elapsedMs={}",
                    indexed, chunkCount, superseded, failures, (System.nanoTime() - started) / 1_000_000);
            cycleLock.unlock();
        }
    }
}
