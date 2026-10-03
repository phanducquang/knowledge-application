# Semantic Retrieval and Search

The foundation implements internal vector persistence and background indexing. Owner-only **Semantic Search is now complete** at `/search`, alongside default Keyword FTS. FTS, Quick Search, Related Articles, explicit wiki graph edges and existing public/shared routes are unchanged. No raw embedding endpoint, anonymous retrieval, LLM/RAG or revision/image embedding is added. Ask My Knowledge is next.

## Database and source contract

PostgreSQL remains major version 17. Local Compose and all PostgreSQL Testcontainers use the project-maintained `pgvector/pgvector:0.8.6-pg17-bookworm` image, verified for AMD64/ARM64. [pgvector's installation documentation](https://github.com/pgvector/pgvector#docker) describes these project images. V8 creates the `vector` extension through Flyway; V1–V7 are untouched and Hibernate still validates, never auto-generates, schema.

`knowledge_embedding_chunk` stores ID, Knowledge ID, owner UUID, zero-based chunk index, total chunk count, source Markdown chunk, unconstrained `vector`, configured model/dimensions, chunker version, SHA-256 source hash, source updated timestamp and creation timestamp. The composite `(knowledge_id, owner_id)` FK prevents mismatched owner partitions and cascades on Knowledge deletion. Vector dimensions must match their metadata. Unique positions and chunk-count checks support completeness detection. No vector column is added to Knowledge; no owner email, auth/sharing data, object key, rendered HTML or revision ID is copied.

Only **current title, summary and canonical Markdown content** are semantic sources. Collection, Tags, visibility, slug and timestamps are deliberately excluded from the semantic hash/input. Metadata-only changes therefore do not cause provider calls; title/summary/content changes and authoring restore do. Retained historical revisions never participate. All owner notes can be indexed regardless of visibility, but PUBLIC/UNLISTED APIs never expose or query this internal index.

## Chunking and embedding inputs

Chunker version is `MarkdownChunker.VERSION = 1`. ATX headings provide preferred section boundaries once accumulated text reaches a quarter of primary chunk capacity; tiny sections are grouped rather than producing one tiny embedding per heading. Paragraphs, contiguous lists and fenced code are preserved as blocks when they fit. Oversized blocks split at lines, then words, with a code-point-safe hard split only for oversized tokens. Long fences may span chunks; code and Mermaid are never executed or rendered to HTML. Line endings normalize for excerpts; canonical stored Markdown remains unchanged.

Default maximum persisted chunk size is **4000 Java character units**, including up to **200 characters of tail overlap**. Primary chunk capacity reserves room for overlap plus its separator. Indexes are contiguous and deterministic; blank chunks are omitted. Empty/whitespace-only Markdown uses title/summary chunks so a saved empty note can still have a current index.

Persisted `chunk_text` is the source excerpt, not a repeated provider prompt. Provider inputs prepend `Title: ...` and optional `Summary: ...` to each excerpt. Character bounds are not a tokenizer: operators must choose chunk/batch sizes suitable for their provider's token limits, including that context prefix. A token-aware chunker is deferred.

## Provider boundary and configuration

`EmbeddingClient.embed(List<String>)` returns one finite, non-zero vector per input in the same order. The production client uses an [OpenAI-compatible embeddings contract](https://developers.openai.com/api/reference/resources/embeddings/methods/create): POST `<base-url>/embeddings` with `input`, `model`, `dimensions`, and float encoding. Response indexes are checked and reordered; count, numeric values, dimension and returned model are validated. The provider must accept the dimensions parameter and return the configured model identifier; aliases resolving to a different identifier need explicit configuration. Providers requiring another HTTP contract need another client, not changes to indexing/domain logic.

API keys stay backend-only. A non-blank key becomes a Bearer header; keyless compatible local hosts are supported, while `api.openai.com` requires a key. Base URLs cannot contain credentials, query or fragment. Redirects are disabled so credentials cannot be forwarded to another host. Connect timeout plus a whole-response deadline bound calls, including a stalled response body. Provider error bodies/causes, raw input and credentials are never propagated in client exceptions or application indexing logs. Properties' string representation is redacted; only Actuator health is exposed.

| `app.embedding.*` setting | Environment | Default |
| --- | --- | --- |
| `enabled` | `EMBEDDING_ENABLED` | `false` |
| `base-url` | `EMBEDDING_BASE_URL` | blank; include API prefix such as `/v1` |
| `api-key` | `EMBEDDING_API_KEY` | blank; inject securely |
| `model` | `EMBEDDING_MODEL` | blank |
| `dimensions` | `EMBEDDING_DIMENSIONS` | `1536` |
| `connect-timeout` | `EMBEDDING_CONNECT_TIMEOUT` | `PT5S` |
| `read-timeout` | `EMBEDDING_READ_TIMEOUT` | `PT30S` |
| `batch-size` | `EMBEDDING_BATCH_SIZE` | `16` inputs/provider request |
| `indexing-enabled` | `EMBEDDING_INDEXING_ENABLED` | `true`; effective only with provider enabled |
| `interval` | `EMBEDDING_INDEX_INTERVAL` | `PT1M` fixed delay |
| `initial-delay` | `EMBEDDING_INDEX_INITIAL_DELAY` | `PT30S` |
| `knowledge-batch-size` | `EMBEDDING_KNOWLEDGE_BATCH_SIZE` | `10` notes/cycle |
| `max-chunk-chars` | `EMBEDDING_MAX_CHUNK_CHARS` | `4000` |
| `overlap-chars` | `EMBEDDING_OVERLAP_CHARS` | `200` |

Enabled configuration fails startup for malformed/blank base URL or model, invalid dimensions (1–16000), non-positive timeouts/batches, interval below 10 seconds, negative initial delay, chunk size outside 256–16000 or overlap outside 0–25% of chunk size. Disabled mode accepts absent provider settings, creates neither external client nor embedding scheduler, and performs no indexing/provider calls. It does not make health DOWN. Setting `indexing-enabled=false` suspends scheduling without changing stored index state.

Configure these only in the Spring process environment, never `NEXT_PUBLIC_*`. Enabling an external provider sends current private note text to that chosen provider and can incur charges; review its privacy/retention policy and costs before enabling. Gradle's test JVM explicitly disables production embeddings even when the parent shell enables them. Tests use a deterministic SHA-256-derived fake and loopback HTTP mocks, never a paid service/API key.

## Background indexing, atomicity and retry

The existing scheduling configuration is reused; no duplicate `@EnableScheduling` is introduced. The worker selects bounded pending IDs only from server-configured `APP_OWNER_ID`, independently of servlet authentication. This is an internal deployment partition, not a new authentication bypass or client-selected owner. Each selected note is loaded individually. A process-local cycle lock prevents overlap, and a PostgreSQL session advisory lock on the negative Knowledge ID prevents duplicate concurrent generation across replicas. All replicas must use the same embedding configuration. One DB connection stays leased during generation; normal row locks and authoring transactions are not held across provider calls.

The worker chunks the current snapshot, embeds in bounded requests, validates every response, and only then transactionally replaces the complete chunk set. A brief parent `FOR UPDATE` lock rechecks the semantic source hash: edits/deletion during generation discard the obsolete result. Insert failure rolls back both deletion and new rows. Provider failure leaves old physical rows intact but stale/incompatible rows cannot enter retrieval. Normal CRUD/restore never call the provider or depend on its availability.

Existing rows naturally backfill after enabling; startup and Flyway do not generate vectors. Failures retry in later cycles. An in-memory ID cursor wraps so one repeatedly failing early note cannot starve later notes; restarts simply rescan pending data. No durable external queue or per-note exponential backoff is added. Aggregate logs report indexed notes/chunks, superseded snapshots, failures and cycle elapsed time, without raw data or high-cardinality metric labels.

## Freshness and internal retrieval

Index states are `NOT_INDEXED`, `CURRENT`, and `STALE`. `source_updated_at` records the current note timestamp at successful replacement, but **semantic SHA-256 is authoritative** because metadata-only edits can change `updated_at` without changing semantic inputs. The digest uses unambiguous length-prefixed UTF-8 title/summary/content (null summary maps to empty), plus source strategy marker, provider base URL, model, dimensions, chunker version, chunk-size and overlap settings. API-key rotation does not invalidate vectors.

Candidate selection and retrieval recompute the same hash in PostgreSQL, not the browser. A current set must have every contiguous chunk, a consistent total count, and compatible hash/model/dimension/version on every row. Returning authoring text exactly to an earlier state can reuse its identical compatible semantic index. Changing model, dimensions, provider, chunk settings or the code's chunker version invalidates old sets and naturally reindexes them.

`findNearestChunks` is internal only: owner-scoped, compatible/current complete sets, cosine distance via PostgreSQL `embedding <=> query_vector`, deterministic ties by Knowledge ID/chunk index/row ID, and limit 1–100. It returns internal source excerpts and raw distance, not percentages or public DTOs. Real integration tests prove extension/storage/cosine ordering, cross-owner/model/dimension exclusions and immediate stale filtering. Exact vector scans are appropriate at personal-library scale; HNSW/IVFFlat ANN indexes are deferred until corpus size and a fixed model/dimension justify them.

## Operations and limitations

The image must contain pgvector's extension files before Flyway runs. Local/Testcontainers database roles can create extensions. Production must provision an extension-capable migration role (or an administrator-approved preinstalled extension), without granting superuser privileges to the long-running API role merely for this feature. V8 runs `CREATE EXTENSION IF NOT EXISTS vector` automatically; do not wait until after application startup to manually install it.

An existing PostgreSQL 17 volume can continue with this PostgreSQL 17 pgvector image. Back up before changing images; never remove user volumes automatically. Alpine-to-Debian transitions may change collation libraries; review/reindex affected collation-dependent indexes and refresh their version metadata as appropriate for the deployment. No MinIO image/pre-pull strategy is changed.

Validate Compose with `docker compose -f infra/docker/compose.yml config --quiet`, start PostgreSQL, then start the API normally to let Flyway migrate. Inspect with:

```bash
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -U knowledge_app -d knowledge_app \
  -c "SELECT extversion FROM pg_extension WHERE extname = 'vector';"
```

Limitations remain: no ANN index, token-aware batching, durable retry telemetry, multi-model parallel corpus, query cache, hybrid ranking, similarity threshold, or revision/attachment embeddings. Production provider credentials/live embedding quality are environment-specific; automated tests use fake/local mock providers only.

## Owner-only Semantic Search

`GET /api/search/knowledge/semantic?q=...&limit=20` uses authenticated `CurrentOwner`, validates q (non-blank/max 200) and limit (1–50), then calls `EmbeddingClient` once with the trimmed query. It requires one finite non-zero vector of configured dimensions. The provider call holds no DB transaction, is never automatically retried and does not persist query vectors or backfill notes.

`findNearestKnowledge` reuses the exact same `CURRENT_SET` predicate as the indexer and internal chunk smoke query. Compatible current chunks are materialized before cosine evaluation; SQL row-number selection picks one best chunk per Knowledge (distance, chunk index, row ID), then applies note ordering (distance ASC, updated_at DESC, Knowledge ID DESC) and note limit. Collection/Tags are loaded in the same owner-scoped bounded SQL statement, not one network query per result. Raw distance/vectors remain internal. Result `match.text` is capped at 600 UTF-16 units including ellipsis, without splitting surrogate pairs, and is rendered as plain text.

Only current complete compatible sets participate, across the owner's PRIVATE/UNLISTED/PUBLIC notes. Edits hide stale notes immediately; indexing later makes their new current state searchable. Retained revisions never contribute until restore becomes current and is reindexed. Deletion cascades. With no calibrated cutoff, an empty result means no current compatible indexed notes/library rather than proof of no semantically related notes.

Disabled/missing client produces `503 SEMANTIC_SEARCH_DISABLED`; provider timeout, invalid response/count/dimensions produces sanitized `503 SEMANTIC_SEARCH_UNAVAILABLE`. Knowledge remains unaffected. Query text necessarily goes to the configured provider, may incur usage charges and may be private. Application code does not explicitly log queries/vectors/matches; operational proxy/URL logs must be restricted/redacted. There is no automatic Keyword fallback.

The `/search` Server Component selects exactly one mode: absent/invalid mode is Keyword; `mode=semantic&q=...` can perform one initial semantic query. Client mode switching/typing never calls semantic transport. Separate explicit-submit state distinguishes draft/submitted queries, updates URLs only on submit through native history (no duplicate server query), suppresses duplicate active submit, and aborts/ignores obsolete browser responses. Abort cannot guarantee cancellation of an already-running upstream call. The focused no-store BFF `/api/knowledge-semantic-search` forwards the session server-side; GET requires no CSRF. Loading, empty-index, disabled/provider-error and manual retry states remain restrained, with an explicit Keyword alternative. Quick Search keeps its original 180ms live FTS behavior.

## Milestone verification

Local API validation ran `./gradlew test` and `./gradlew clean build`: 190 tests passed, including 19 new Semantic Search tests (9 real-pgvector integration tests). They cover best-chunk/dedup-before-limit, owner/current/compatibility/completeness filtering, all visibility states, ordering, edit/reindex, revision restore, cascade, controlled errors and no automatic provider retries. Web validation ran `npm test`, `npm run lint`, and `npm run build`: 72 tests passed, including 12 new transport/mode/cost-guard/state tests; lint and production build succeeded.

Browser QA ran the actual production Next.js build against a disposable loopback-only backend fixture containing synthetic notes, not a production embedding service or real OAuth session. Verified Keyword default/live behavior, Semantic mode/draft/Enter/button, initial deep link, plain-text matched context, keyboard result/Reading navigation, loading, disabled/provider errors/manual retry, empty index, invalid-mode fallback and unchanged Quick Search. Fixture counters confirmed zero semantic calls on typing/mode switch, one per submit/deep link and none from Quick Search. Desktop 1366px and mobile 390px were checked; a discovered 200-character unbroken-query heading overflow was fixed and reverified. Real authentication/persistence/vector semantics were verified separately by Spring Security + pgvector integration tests. No paid/external embedding call was made; live model retrieval quality remains environment-specific.
