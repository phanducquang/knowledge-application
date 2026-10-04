package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.knowledge.embedding.*;
import com.knowledgeapplication.api.testsupport.TestContainerImages;
import com.zaxxer.hikari.HikariDataSource;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/** No connection-string parameter: this helper can ONLY connect to its own disposable Testcontainer. */
final class EvaluationDatabase implements AutoCloseable {
    static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(TestContainerImages.POSTGRES);
    final HikariDataSource pool=new HikariDataSource();
    final JdbcClient jdbc;
    final KnowledgeEmbeddingRepository repository;
    final Map<Long,String> slugs=new LinkedHashMap<>();
    final Map<String,List<MarkdownChunker.Chunk>> chunks=new LinkedHashMap<>();
    final RetrievalCorpus corpus;
    EvaluationDatabase(RetrievalCorpus corpus) {
        this.corpus=corpus;
        try {
            postgres.start(); pool.setJdbcUrl(postgres.getJdbcUrl()); pool.setUsername(postgres.getUsername()); pool.setPassword(postgres.getPassword());
            pool.setMaximumPoolSize(2); pool.setMinimumIdle(0);
            Flyway.configure().dataSource(pool).locations("classpath:db/migration").load().migrate();
            jdbc=JdbcClient.create(pool); repository=new KnowledgeEmbeddingRepository(jdbc,pool,new DataSourceTransactionManager(pool));
            long id=1;
            for(var note : corpus.notes()) {
                jdbc.sql("INSERT INTO knowledge(id,owner_id,slug,title,summary,content,visibility,created_at,updated_at) VALUES(:id,:owner,:slug,:title,:summary,:content,'PRIVATE',:time,:time)")
                        .param("id",id).param("owner",OWNER).param("slug",note.slug()).param("title",note.title()).param("summary",note.summary())
                        .param("content",note.markdown()).param("time",Instant.parse("2026-01-01T00:00:00Z").atOffset(ZoneOffset.UTC)).update();
                slugs.put(id++,note.slug()); chunks.put(note.slug(),new MarkdownChunker(4000,200).chunk(note.markdown()));
            }
        } catch(RuntimeException ex) { close(); throw ex; }
    }
    List<String> documentInputs() {
        var inputs=new ArrayList<String>();
        for(var entry : slugs.entrySet()) {
            var source=repository.findSource(OWNER,entry.getKey()).orElseThrow();
            chunks.get(entry.getValue()).forEach(c -> inputs.add(source.input(c.text())));
        }
        return List.copyOf(inputs);
    }
    void index(EmbeddingStrategy strategy,EmbeddingClient cached) {
        for(var entry : slugs.entrySet()) {
            var source=repository.findSource(OWNER,entry.getKey()).orElseThrow(); var parts=chunks.get(entry.getValue());
            var vectors=cached.embed(parts.stream().map(c -> source.input(c.text())).toList());
            EmbeddingVectors.validate(vectors,parts.size(),strategy.properties().dimensions());
            if(!repository.replace(source,strategy,parts,vectors)) throw new IllegalStateException("Synthetic index replacement failed");
        }
    }
    @Override public void close() { pool.close(); postgres.stop(); }
}
