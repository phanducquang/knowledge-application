package com.knowledgeapplication.api.retrieval.eval;

import com.knowledgeapplication.api.knowledge.embedding.*;
import com.knowledgeapplication.api.testsupport.TestContainerImages;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

/** No Spring context/provider beans, HTTP clients or ambient API credentials are instantiated/read. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetrievalEvaluationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TestContainerImages.POSTGRES);
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FOREIGN = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private final OfflineEmbeddingClient embeddings = new OfflineEmbeddingClient();
    private final MarkdownChunker chunker = new MarkdownChunker(4000, 200);
    private final RetrievalCorpus corpus = RetrievalCorpus.load();
    private final Map<Long, String> slugs = new LinkedHashMap<>();
    private final Map<String, List<MarkdownChunker.Chunk>> chunks = new LinkedHashMap<>();
    private KnowledgeEmbeddingRepository repository;
    private JdbcClient jdbc;
    private EmbeddingStrategy strategy;
    private RetrievalEvaluation evaluation;
    private HikariDataSource dataSource;

    @BeforeAll
    void loadIsolatedSyntheticDatabaseOnce() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl()); dataSource.setUsername(POSTGRES.getUsername()); dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setMaximumPoolSize(2); dataSource.setMinimumIdle(0);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(dataSource);
        repository = new KnowledgeEmbeddingRepository(jdbc, dataSource, new DataSourceTransactionManager(dataSource));
        strategy = strategy(OfflineEmbeddingClient.NAME, OfflineEmbeddingClient.DIMENSIONS);
        long id = 1;
        for (var note : corpus.notes()) {
            insert(id, OWNER, note.slug(), note.title(), note.summary(), note.markdown(), strategy);
            slugs.put(id++, note.slug()); chunks.put(note.slug(), chunker.chunk(note.markdown()));
        }
        String decoyText = corpus.notes().stream().filter(n -> n.slug().equals("webclient-playbook")).findFirst().orElseThrow().markdown();
        insert(1001, FOREIGN, "decoy-foreign", "WebClient timeout retry HTTP Java", "WebClient timeout retry HTTP Java", decoyText, strategy);
        insert(1002, OWNER, "decoy-stale", "WebClient timeout retry HTTP Java", "WebClient timeout retry HTTP Java", decoyText, strategy);
        jdbc.sql("UPDATE knowledge SET content=content || ' changed source' WHERE id=1002").update();
        insert(1003, OWNER, "decoy-model", "WebClient timeout retry HTTP Java", "", decoyText, strategy("offline-incompatible-v1", 64));
        insert(1004, OWNER, "decoy-dimensions", "WebClient timeout retry HTTP Java", "", decoyText, strategy(OfflineEmbeddingClient.NAME, 32));
        insert(1005, OWNER, "decoy-incomplete", "WebClient timeout retry HTTP Java", "", decoyText, strategy);
        jdbc.sql("DELETE FROM knowledge_embedding_chunk WHERE knowledge_id=1005 AND chunk_index=1").update();
        evaluation = new RetrievalEvaluation(repository, strategy, embeddings, OWNER, corpus, slugs, chunks);
    }

    private static EmbeddingStrategy strategy(String model, int dimensions) {
        return strategy(model, dimensions, 4000, 200);
    }
    private static EmbeddingStrategy strategy(String model, int dimensions, int max, int overlap) {
        return new EmbeddingStrategy(new EmbeddingProperties(false, "http://127.0.0.1/offline-eval", model, dimensions,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 20, false, Duration.ofSeconds(60), Duration.ZERO, 10, max, overlap), MarkdownChunker.VERSION);
    }

    private void insert(long id, UUID owner, String slug, String title, String summary, String content, EmbeddingStrategy selected) {
        jdbc.sql("""
                INSERT INTO knowledge(id, owner_id, slug, title, summary, content, visibility, created_at, updated_at)
                VALUES (:id, :owner, :slug, :title, :summary, :content, 'PRIVATE', :time, :time)
                """).param("id", id).param("owner", owner).param("slug", slug).param("title", title)
                .param("summary", summary).param("content", content).param("time", FIXED_TIME.atOffset(ZoneOffset.UTC)).update();
        index(id, owner, selected);
    }
    private void index(long id, UUID owner, EmbeddingStrategy selected) {
        var source = repository.findSource(owner, id).orElseThrow();
        var parts = new MarkdownChunker(selected.properties().maxChunkChars(),selected.properties().overlapChars()).chunk(source.content());
        var vectors = embeddings.embed(parts.stream().map(part -> source.input(part.text())).toList()).stream()
                .map(vector -> Arrays.copyOf(vector, selected.properties().dimensions())).toList();
        assertThat(repository.replace(source, selected, parts, vectors)).isTrue();
    }

    @Test
    void evaluateActualRetrievalAndWriteRepeatableReports() throws Exception {
        var report = evaluation.evaluate(Instant.now(), 5);
        var repeated = evaluation.evaluate(Instant.now(), 5);
        assertThat(report.deterministicIdentity()).isEqualTo(repeated.deterministicIdentity());
        assertThat(RetrievalReportWriter.JSON.writeValueAsString(report.deterministicIdentity()))
                .isEqualTo(RetrievalReportWriter.JSON.writeValueAsString(repeated.deterministicIdentity()));
        assertThat(report.corpus().noteCount()).isEqualTo(24);
        assertThat(report.corpus().queryCount()).isEqualTo(40);
        assertThat(report.corpus().positiveQueries()).isEqualTo(38);
        assertThat(report.corpus().negativeQueries()).isEqualTo(2);
        assertThat(report.corpus().chunkGroundTruthQueries()).isEqualTo(33);
        assertThat(report.semanticSearch().overall().positiveSamples()).isEqualTo(38);
        assertThat(report.ragContext().overall().chunkSamples()).isEqualTo(33);
        assertThat(report.semanticSearch().byCategory().get("negative").noteMetrics()).isNull();
        assertThat(report.ragContext().byCategory().get("negative").chunkMetrics()).isNull();
        for (var result : report.semanticSearch().cases()) {
            assertThat(result.ranked()).hasSize(20);
            assertThat(result.duplicateNotes()).isEmpty();
            assertThat(result.ranked().stream().map(RetrievalEvaluation.Ranked::slug)).doesNotHaveDuplicates()
                    .allMatch(chunks::containsKey);
            assertThat(result.chunkScore()).isNull();
        }
        for (var result : report.ragContext().cases()) {
            assertThat(result.ranked()).hasSize(8);
            assertThat(result.ranked().stream().map(RetrievalEvaluation.Ranked::chunkId)).doesNotHaveDuplicates();
            var counts = new HashMap<String, Integer>();
            result.ranked().forEach(r -> { assertThat(chunks).containsKey(r.slug()); counts.merge(r.slug(), 1, Integer::sum); });
            assertThat(counts.values()).allMatch(count -> count <= 2);
            assertThat(result.diversity()).isBetween(0.0, 1.0);
            if (result.query().negative()) {
                assertThat(result.noteScore()).isNull(); assertThat(result.chunkScore()).isNull();
                assertThat(result.ranked()).isNotEmpty(); // No calibrated distance cutoff in production.
            }
        }
        // Focused controlled-vocabulary regressions, not aggregate quality thresholds.
        assertThat(caseById(report.semanticSearch(), "q-nginx").ranked().get(0).slug()).isEqualTo("nginx-upstream-timeout");
        assertThat(caseById(report.semanticSearch(), "q-redis").ranked().stream().limit(3).map(RetrievalEvaluation.Ranked::slug))
                .contains("redis-ttl");
        assertThat(chunks.get("webclient-playbook").size()).isGreaterThanOrEqualTo(4);
        var playbook = caseById(report.ragContext(), "q-playbook");
        assertThat(playbook.ranked().stream().filter(r -> r.slug().equals("webclient-playbook")).count()).isEqualTo(2);
        assertThat(playbook.uniqueNoteCount()).isGreaterThan(1);
        assertThat(playbook.ranked().stream().map(RetrievalEvaluation.Ranked::slug)).contains("spring-webclient-timeout");
        assertThat(caseById(report.semanticSearch(), "q-playbook").ranked().stream().map(RetrievalEvaluation.Ranked::slug))
                .contains("webclient-playbook", "spring-webclient-timeout");
        assertThat(repository.state(FOREIGN, 1001, strategy)).isEqualTo(KnowledgeEmbeddingRepository.IndexState.CURRENT);
        for (long id : List.of(1002L, 1003L, 1004L, 1005L)) {
            assertThat(repository.state(OWNER, id, strategy)).isEqualTo(KnowledgeEmbeddingRepository.IndexState.STALE);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM knowledge").query(Long.class).single()).isEqualTo(29);
        assertThat(jdbc.sql("SELECT count(*) FROM ai_quota_usage").query(Long.class).single()).isZero();
        RetrievalReportWriter.write(report, Path.of(System.getProperty("retrieval.eval.report-directory", "build/reports/retrieval-eval")));
        evaluateMatrixAndWriteReports();
    }

    @AfterAll void closePool() { if (dataSource != null) dataSource.close(); }

    private void evaluateMatrixAndWriteReports() throws Exception {
        var expanded = ChunkSelectionEvaluation.extendedCorpus();
        long id=25;
        for (var note : expanded.notes().subList(24,expanded.notes().size())) {
            insert(id,OWNER,note.slug(),note.title(),note.summary(),note.markdown(),strategy); slugs.put(id++,note.slug());
        }
        var baselineChunks = new TreeMap<String,List<MarkdownChunker.Chunk>>();
        expanded.notes().forEach(n -> baselineChunks.put(n.slug(),chunker.chunk(n.markdown())));
        assertThat(baselineChunks.values().stream().filter(c -> c.size()>=5).count()).isEqualTo(5);
        assertThat(expanded.queries()).hasSize(60);
        var first = matrixRun(expanded,baselineChunks);
        var second = matrixRun(expanded,baselineChunks);
        var matrixReport = ChunkSelectionEvaluation.report(Instant.now(),expanded,first);
        var repeated = ChunkSelectionEvaluation.report(Instant.now(),expanded,second);
        assertThat(matrixReport.deterministicIdentity()).isEqualTo(repeated.deterministicIdentity());
        assertThat(RetrievalReportWriter.JSON.writeValueAsString(matrixReport.deterministicIdentity()))
                .isEqualTo(RetrievalReportWriter.JSON.writeValueAsString(repeated.deterministicIdentity()));
        assertThat(matrixReport.variants()).hasSize(48);
        assertThat(matrixReport.baseline().configuration()).isEqualTo(new ChunkSelectionEvaluation.Variant(4000,200,8,2));
        for (var result : matrixReport.variants()) {
            for (var c : result.cases()) {
                assertThat(c.ranked()).hasSize(result.configuration().ragLimit());
                assertThat(c.ranked().stream().map(RetrievalEvaluation.Ranked::chunkId)).doesNotHaveDuplicates();
                assertThat(c.ranked().stream().map(RetrievalEvaluation.Ranked::slug)).allMatch(baselineChunks::containsKey);
                var counts = new HashMap<String,Integer>(); c.ranked().forEach(r -> counts.merge(r.slug(),1,Integer::sum));
                assertThat(counts.values()).allMatch(n -> n<=result.configuration().perNoteCap());
                assertThat(c.ranked().stream().mapToDouble(RetrievalEvaluation.Ranked::distance).toArray()).isSorted();
            }
            for (var c : result.caseDiagnostics()) {
                assertThat(c.assembledChunks()).isLessThanOrEqualTo(result.configuration().ragLimit());
                assertThat(c.assembledSources()).isLessThanOrEqualTo(6);
                assertThat(c.assembledContextChars()).isLessThanOrEqualTo(24000);
                assertThat(c.expected()).allMatch(e -> e.rankWithinNote()>0);
            }
        }
        var baselineCase = matrixReport.baseline().caseDiagnostics().stream().filter(c -> c.queryId().equals("q-playbook")).findFirst().orElseThrow();
        assertThat(baselineCase.expected()).singleElement().satisfies(e -> {
            assertThat(e.id()).isEqualTo("webclient-playbook#4"); assertThat(e.rankWithinNote()).isEqualTo(3);
            assertThat(e.exclusion()).isEqualTo("PER_NOTE_CAP");
        });
        var cap3 = matrixReport.variants().stream().filter(r -> r.name().equals("c4000-o200-l8-p3")).findFirst().orElseThrow();
        assertThat(cap3.caseDiagnostics().stream().filter(c -> c.queryId().equals("q-playbook")).findFirst().orElseThrow().expected())
                .singleElement().satisfies(e -> assertThat(e.selected()).isTrue());
        assertThat(jdbc.sql("SELECT count(*) FROM ai_quota_usage").query(Long.class).single()).isZero();
        RetrievalReportWriter.writeMatrix(matrixReport,Path.of(System.getProperty("retrieval.eval.report-directory", "build/reports/retrieval-eval")));
    }

    private List<ChunkSelectionEvaluation.Result> matrixRun(RetrievalCorpus expanded, Map<String,List<MarkdownChunker.Chunk>> baselineChunks) {
        var results = new ArrayList<ChunkSelectionEvaluation.Result>();
        for (int[] sizes : List.of(new int[]{2000,150},new int[]{3000,200},new int[]{4000,200},new int[]{4000,400})) {
            var selected = strategy(OfflineEmbeddingClient.NAME,64,sizes[0],sizes[1]);
            var variantChunker = new MarkdownChunker(sizes[0],sizes[1]);
            expanded.validate(variantChunker);
            var parts = new TreeMap<String,List<MarkdownChunker.Chunk>>();
            for (var note : expanded.notes()) parts.put(note.slug(),variantChunker.chunk(note.markdown()));
            assertThat(parts.values().stream().mapToInt(List::size).sum()).isLessThanOrEqualTo(100);
            for (long noteId : slugs.keySet()) index(noteId,OWNER,selected);
            // Recreate each exclusion against THIS strategy, so incompatible old strategies cannot mask owner/source/completeness checks.
            index(1001,FOREIGN,selected);
            jdbc.sql("UPDATE knowledge SET content=:content WHERE id=1002").param("content",RetrievalCorpus.resource("webclient-playbook.md")).update();
            index(1002,OWNER,selected); jdbc.sql("UPDATE knowledge SET content=content || ' changed source' WHERE id=1002").update();
            index(1003,OWNER,strategy("offline-incompatible-v1",64,sizes[0],sizes[1]));
            index(1004,OWNER,strategy(OfflineEmbeddingClient.NAME,32,sizes[0],sizes[1]));
            index(1005,OWNER,selected); jdbc.sql("DELETE FROM knowledge_embedding_chunk WHERE knowledge_id=1005 AND chunk_index=1").update();
            assertThat(repository.state(FOREIGN,1001,selected)).isEqualTo(KnowledgeEmbeddingRepository.IndexState.CURRENT);
            for (long decoy : List.of(1002L,1003L,1004L,1005L))
                assertThat(repository.state(OWNER,decoy,selected)).isEqualTo(KnowledgeEmbeddingRepository.IndexState.STALE);
            var snapshots = ChunkSelectionEvaluation.snapshots(repository,OWNER,selected,embeddings,expanded);
            for (var variant : ChunkSelectionEvaluation.matrix().stream().filter(v -> v.maxChunkChars()==sizes[0] && v.overlapChars()==sizes[1]).toList()) {
                var evaluation = new RetrievalEvaluation(repository,selected,embeddings,OWNER,expanded,slugs,parts,variant.ragLimit(),variant.perNoteCap());
                var report = evaluation.evaluateRag(snapshots);
                if (variant.baseline()) assertThat(report).isEqualTo(evaluation.evaluateRag()); // Cached diagnostics cannot change actual SQL ranks/metrics.
                results.add(ChunkSelectionEvaluation.diagnose(variant,selected,report,expanded,parts,baselineChunks,slugs,OWNER,snapshots));
            }
        }
        return List.copyOf(results);
    }

    private static RetrievalEvaluation.CaseResult caseById(RetrievalEvaluation.Mode mode, String id) {
        return mode.cases().stream().filter(c -> c.query().id().equals(id)).findFirst().orElseThrow();
    }
}
