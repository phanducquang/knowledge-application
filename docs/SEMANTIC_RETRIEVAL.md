# Semantic Retrieval and Search

The foundation implements internal vector persistence and background indexing. Owner-only **Semantic Search is complete** at `/search`, alongside default Keyword FTS. Gemini now powers production embeddings and the optional single-turn [Ask My Knowledge](ASK_MY_KNOWLEDGE.md) slice. FTS, Quick Search, Related Articles, explicit wiki graph edges and existing public/shared routes are unchanged. No raw embedding endpoint, anonymous retrieval or revision/image embedding is added.

## Database and source contract

PostgreSQL remains major version 17. Local Compose and all PostgreSQL Testcontainers use the project-maintained `pgvector/pgvector:0.8.6-pg17-bookworm` image, verified for AMD64/ARM64. [pgvector's installation documentation](https://github.com/pgvector/pgvector#docker) describes these project images. V8 creates the `vector` extension through Flyway; V1–V7 are untouched and Hibernate still validates, never auto-generates, schema.

`knowledge_embedding_chunk` stores ID, Knowledge ID, owner UUID, zero-based chunk index, total chunk count, source Markdown chunk, unconstrained `vector`, configured model/dimensions, chunker version, SHA-256 source hash, source updated timestamp and creation timestamp. The composite `(knowledge_id, owner_id)` FK prevents mismatched owner partitions and cascades on Knowledge deletion. Vector dimensions must match their metadata. Unique positions and chunk-count checks support completeness detection. No vector column is added to Knowledge; no owner email, auth/sharing data, object key, rendered HTML or revision ID is copied.

Only **current title, summary and canonical Markdown content** are semantic sources. Collection, Tags, visibility, slug and timestamps are deliberately excluded from the semantic hash/input. Metadata-only changes therefore do not cause provider calls; title/summary/content changes and authoring restore do. Retained historical revisions never participate. All owner notes can be indexed regardless of visibility, but PUBLIC/UNLISTED APIs never expose or query this internal index.

## Chunking and embedding inputs

Chunker version is `MarkdownChunker.VERSION = 1`. ATX headings provide preferred section boundaries once accumulated text reaches a quarter of primary chunk capacity; tiny sections are grouped rather than producing one tiny embedding per heading. Paragraphs, contiguous lists and fenced code are preserved as blocks when they fit. Oversized blocks split at lines, then words, with a code-point-safe hard split only for oversized tokens. Long fences may span chunks; code and Mermaid are never executed or rendered to HTML. Line endings normalize for excerpts; canonical stored Markdown remains unchanged.

Default maximum persisted chunk size is **4000 Java character units**, including up to **200 characters of tail overlap**. Primary chunk capacity reserves room for overlap plus its separator. Indexes are contiguous and deterministic; blank chunks are omitted. Empty/whitespace-only Markdown uses title/summary chunks so a saved empty note can still have a current index.

Persisted `chunk_text` is the source excerpt, not a repeated provider prompt. Provider inputs prepend `Title: ...` and optional `Summary: ...` to each excerpt. Character bounds are not a tokenizer: operators must choose chunk/batch sizes suitable for their provider's token limits, including that context prefix. A token-aware chunker is deferred.

## Provider boundary and configuration

`EmbeddingClient.embed(List<String>)` returns one finite, non-zero vector per input in the same order. `GeminiEmbeddingClient` uses the official [Google Java GenAI SDK 1.75.0](https://github.com/googleapis/java-genai/releases/tag/v1.75.0), native `models.embedContent(model, List<String>, config)` and `outputDimensionality`. The SDK wraps each input as an independent Content and sends one synchronous `batchEmbedContents` request, not an asynchronous batch job. Ordered response count, dimensions, finite numeric values and non-zero norms are validated. SDK DTOs stay inside Gemini adapters; indexing/search still depend on generic interfaces.

Default exact API ID is [`gemini-embedding-2`](https://ai.google.dev/gemini-api/docs/models/gemini-embedding-2), **768 dimensions**. Per [Gemini embedding guidance](https://ai.google.dev/gemini-api/docs/embeddings), Embedding 2 does not use `taskType`; both documents and queries use the same fixed `task: semantic similarity | text: ` prefix. It precedes the existing title/summary/chunk input for notes. That symmetric-input version is part of the strategy identity. One native batch consumes one RPM/RPD request but the sum of **all** prefixed inputs counts toward estimated input TPM. No client truncation of returned vectors is performed.

One backend-only `GEMINI_API_KEY` is required whenever embedding or Ask generation is enabled. It is passed explicitly to the SDK (native key header); no ambient `GOOGLE_API_KEY`, Vertex identity or keyless production fallback. Base URLs cannot contain credentials, query or fragment. Redirects and connection/SDK automatic retries are disabled (`attempts=1`). Connect timeout plus whole-call deadline bound calls, including stalled response bodies. Provider error bodies/causes, raw input and credentials are never propagated in client exceptions or application indexing logs. Properties' string representation is redacted; only Actuator health is exposed.

| `app.embedding.*` setting | Environment | Default |
| --- | --- | --- |
| `enabled` | `EMBEDDING_ENABLED` | `false` |
| `base-url` | `EMBEDDING_BASE_URL` | `https://generativelanguage.googleapis.com`; SDK uses `v1beta` native routes |
| `model` | `EMBEDDING_MODEL` | `gemini-embedding-2` |
| `dimensions` | `EMBEDDING_DIMENSIONS` | `768` |
| `connect-timeout` | `EMBEDDING_CONNECT_TIMEOUT` | `PT5S` |
| `read-timeout` | `EMBEDDING_READ_TIMEOUT` | `PT30S` |
| `batch-size` | `EMBEDDING_BATCH_SIZE` | `16` inputs/provider request |
| `indexing-enabled` | `EMBEDDING_INDEXING_ENABLED` | `true`; effective only with provider enabled |
| `interval` | `EMBEDDING_INDEX_INTERVAL` | `PT1M` fixed delay |
| `initial-delay` | `EMBEDDING_INDEX_INITIAL_DELAY` | `PT30S` |
| `knowledge-batch-size` | `EMBEDDING_KNOWLEDGE_BATCH_SIZE` | `10` notes/cycle |
| `max-chunk-chars` | `EMBEDDING_MAX_CHUNK_CHARS` | `4000` |
| `overlap-chars` | `EMBEDDING_OVERLAP_CHARS` | `200` |

Enabled configuration fails startup for malformed/blank base URL or model, missing/invalid key, dimensions outside 1–3072, timeouts outside 1–2147483647 milliseconds, invalid batches, interval below 10 seconds, negative initial delay, chunk size outside 256–16000 or overlap outside 0–25% of chunk size. The selected model must actually support configured dimensions; recommended default is 768. Disabled mode accepts absent provider settings, creates neither embedding client nor scheduler, and performs no indexing/provider calls. It does not make health DOWN. Setting `indexing-enabled=false` suspends scheduling without changing stored index state.

Configure these only in the Spring process environment, never `NEXT_PUBLIC_*`. Enabling Gemini sends current private note text to Google and may incur charges. Free Tier/unpaid data handling may allow Google to use submitted content for product improvement and human review; do not send confidential material without reviewing [current terms](https://ai.google.dev/gemini-api/terms). See [Ask operations](ASK_MY_KNOWLEDGE.md) for separate global/background and generation quota settings. Gradle's test JVM explicitly disables production embeddings/Ask and clears the Gemini key even when the parent shell enables them. Tests use a deterministic SHA-256-derived fake and native-SDK loopback HTTP mocks, never external Gemini calls.

## Background indexing, atomicity and retry

The existing scheduling configuration is reused; no duplicate `@EnableScheduling` is introduced. The worker selects bounded pending IDs only from server-configured `APP_OWNER_ID`, independently of servlet authentication. This is an internal deployment partition, not a new authentication bypass or client-selected owner. Each selected note is loaded individually. A process-local cycle lock prevents overlap, and a PostgreSQL session advisory lock on the negative Knowledge ID prevents duplicate concurrent generation across replicas. All replicas must use the same embedding configuration. One DB connection stays leased during generation; normal row locks and authoring transactions are not held across provider calls.

The worker chunks the current snapshot, embeds in bounded requests, validates every response, and only then transactionally replaces the complete chunk set. A brief parent `FOR UPDATE` lock rechecks the semantic source hash: edits/deletion during generation discard the obsolete result. Insert failure rolls back both deletion and new rows. Provider failure leaves old physical rows intact but stale/incompatible rows cannot enter retrieval. Normal CRUD/restore never call the provider or depend on its availability.

Existing rows naturally backfill after enabling; startup and Flyway do not generate vectors. Failures retry in later cycles. An in-memory ID cursor wraps so one repeatedly failing early note cannot starve later notes; restarts simply rescan pending data. No durable external queue or per-note exponential backoff is added. Aggregate logs report indexed notes/chunks, superseded snapshots, failures and cycle elapsed time, without raw data or high-cardinality metric labels.

V9 persists quota reservations across replicas/restarts: global defaults **80 RPM / 24000 estimated input TPM / 800 RPD**, with background **50 / 18000 / 650** required in addition. Interactive Semantic/Ask embeddings use global capacity without the smaller background ceiling. Local denial or provider 429 immediately stops the indexing cycle; remaining notes stay pending and retry only on later scheduled cycles. No blocking sleeps/tight retries. Defaults are application ceilings, not verified provider entitlements; check actual project/model/tier limits in AI Studio. Failed attempted provider requests remain counted conservatively. Token estimates include every input in the batch; see [quota details](ASK_MY_KNOWLEDGE.md#quota-accounting-and-operations).

## Freshness and internal retrieval

Index states are `NOT_INDEXED`, `CURRENT`, and `STALE`. `source_updated_at` records the current note timestamp at successful replacement, but **semantic SHA-256 is authoritative** because metadata-only edits can change `updated_at` without changing semantic inputs. The digest uses unambiguous length-prefixed UTF-8 title/summary/content (null summary maps to empty), plus source strategy marker, provider base URL, model, dimensions, chunker version, chunk-size and overlap settings. API-key rotation does not invalidate vectors.

The Gemini migration explicitly bumps that marker to **`semantic-source-v2:provider=gemini:symmetric-text-v1:...`**. Even an unchanged model name/dimension cannot reuse the old compatible adapter's rows. No V8 rows are manually deleted and no old Flyway migration is edited. Existing vectors remain physically present but stale/excluded until automatic bounded reindex replaces them. A model/dimension/context/chunker change behaves the same way, with temporarily partial Semantic/Ask coverage. API key/quota changes do not alter semantic identity.

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

Disabled/missing client produces `503 SEMANTIC_SEARCH_DISABLED`; local quota denial, provider 429/timeout, invalid response/count/dimensions produces sanitized `503 SEMANTIC_SEARCH_UNAVAILABLE`. Knowledge remains unaffected. Query text necessarily goes to Gemini, may incur usage charges and may be private. Application code does not explicitly log queries/vectors/matches; operational proxy/URL logs must be restricted/redacted. There is no automatic Keyword fallback.

The `/search` Server Component selects exactly one mode: absent/invalid mode is Keyword; `mode=semantic&q=...` can perform one initial semantic query. Client mode switching/typing never calls semantic transport. Separate explicit-submit state distinguishes draft/submitted queries, updates URLs only on submit through native history (no duplicate server query), suppresses duplicate active submit, and aborts/ignores obsolete browser responses. Abort cannot guarantee cancellation of an already-running upstream call. The focused no-store BFF `/api/knowledge-semantic-search` forwards the session server-side; GET requires no CSRF. Loading, empty-index, disabled/provider-error and manual retry states remain restrained, with an explicit Keyword alternative. Quick Search keeps its original 180ms live FTS behavior.

## Previous Semantic Search milestone verification

Local API validation ran `./gradlew test` and `./gradlew clean build`: 190 tests passed, including 19 new Semantic Search tests (9 real-pgvector integration tests). They cover best-chunk/dedup-before-limit, owner/current/compatibility/completeness filtering, all visibility states, ordering, edit/reindex, revision restore, cascade, controlled errors and no automatic provider retries. Web validation ran `npm test`, `npm run lint`, and `npm run build`: 72 tests passed, including 12 new transport/mode/cost-guard/state tests; lint and production build succeeded.

Browser QA ran the actual production Next.js build against a disposable loopback-only backend fixture containing synthetic notes, not a production embedding service or real OAuth session. Verified Keyword default/live behavior, Semantic mode/draft/Enter/button, initial deep link, plain-text matched context, keyboard result/Reading navigation, loading, disabled/provider errors/manual retry, empty index, invalid-mode fallback and unchanged Quick Search. Fixture counters confirmed zero semantic calls on typing/mode switch, one per submit/deep link and none from Quick Search. Desktop 1366px and mobile 390px were checked; a discovered 200-character unbroken-query heading overflow was fixed and reverified. Real authentication/persistence/vector semantics were verified separately by Spring Security + pgvector integration tests. No paid/external embedding call was made; live model retrieval quality remains environment-specific.

Current Gemini migration/Ask and Source-linked Answers verification is recorded separately in [ASK_MY_KNOWLEDGE.md](ASK_MY_KNOWLEDGE.md). Citations reuse the exact owner/current-compatible retrieved chunks without any new embedding/retrieval/provider request or strategy change. SDK remains 1.75.0; native batch behavior and quota-limited migration semantics are unchanged.
