# Gemini-powered Ask My Knowledge

Explicit authoring Summary/Tag suggestions now have a separate provider-neutral client, model and metadata quota group; no Ask prompts/context/answer DTOs/retrieval/citations/quotas change. [AI_METADATA_SUGGESTIONS.md](AI_METADATA_SUGGESTIONS.md) documents the current-note-only privacy and user-apply flow. Background metadata generation remains out of scope.

Owner-only, optional **single-turn** answers grounded in current indexed Knowledge. Semantic Search, FTS, Quick Search, Reading/sharing, relationships and authoring contracts remain intact. No conversation/history storage, agents, tools, external web search or public AI endpoint.

## Provider and configuration

Production uses the official GA [Google Java GenAI SDK `1.75.0`](https://github.com/googleapis/java-genai/releases/tag/v1.75.0), Maven `com.google.genai:google-genai:1.75.0`. SDK types are isolated in `ai/gemini`; services depend on existing `EmbeddingClient` and the small `KnowledgeAnswerClient`. The old compatible embedding adapter is removed; no runtime provider switch or new framework.

Exact default API model IDs, verified against official model pages:

- [`gemini-embedding-2`](https://ai.google.dev/gemini-api/docs/models/gemini-embedding-2), **768 dimensions**. Native synchronous `models.embedContent` list overload makes independent contents in **one** `batchEmbedContents` request; one ordered vector per input. This is not the asynchronous batch service. `outputDimensionality` controls output; dimensions/count/finite values/non-zero norms are validated. No vector truncation.
- [`gemini-3.5-flash-lite`](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite), native `generateContent`, one candidate, no tools/function execution/history. SDK and connection automatic retries are disabled; no `countTokens` or other paid helper request. Sampling uses provider defaults: per [Gemini 3.5 migration guidance](https://ai.google.dev/gemini-api/docs/whats-new-gemini-3.5), temperature/topP/topK are not overridden. Production answers are not guaranteed deterministic; test fakes/context ordering are deterministic.

All models/dimensions/limits are runtime configurable. One explicit backend-only `GEMINI_API_KEY` is shared, with no ambient `GOOGLE_API_KEY`, Vertex credential fallback or browser key. Both features default disabled; no key is needed when both are disabled. Enabling either requires a non-blank valid key and valid enabled-feature configuration at startup. `.env.example` contains placeholders only; the application does not load that file automatically.

| Environment | Default / meaning |
| --- | --- |
| `GEMINI_API_KEY` | blank; inject securely in Spring process only |
| `EMBEDDING_ENABLED` | `false` |
| `EMBEDDING_BASE_URL` | `https://generativelanguage.googleapis.com`; SDK native `v1beta` routes |
| `EMBEDDING_MODEL` / `EMBEDDING_DIMENSIONS` | `gemini-embedding-2` / `768` |
| `ASK_ENABLED` | `false` |
| `ASK_BASE_URL` | `https://generativelanguage.googleapis.com` |
| `ASK_MODEL` | `gemini-3.5-flash-lite` |
| `ASK_CONNECT_TIMEOUT` / `ASK_READ_TIMEOUT` | `PT5S` / `PT45S` whole-call deadline |
| `ASK_MAX_OUTPUT_TOKENS` | `1200`; accepted 1–8192 |
| `ASK_MAX_CONTEXT_CHARS` | `24000`; accepted 1024–128000 |
| `ASK_MAX_CHUNKS` | `8`; accepted 1–100 |
| `ASK_MAX_CHUNKS_PER_KNOWLEDGE` | `2`; accepted 1–10 |
| `ASK_MAX_SOURCES` | `6`; accepted 1–30 |

Embedding scheduler/batch/chunker/deadline settings remain documented in [Semantic Retrieval](SEMANTIC_RETRIEVAL.md). Timeouts must fit 1–2147483647 milliseconds; URLs must be HTTP(S), without credentials/query/fragment. Production endpoints should remain HTTPS; HTTP is supported for synthetic loopback tests. Redirects are disabled to prevent key forwarding.

| Embedding | Ask | Behavior |
| --- | --- | --- |
| off | off | No Gemini key/client/calls; normal health and CRUD unaffected |
| on | off | Indexing/Semantic enabled, Ask returns `ASK_DISABLED` |
| off | on | Generation client configured, Ask returns `ASK_RETRIEVAL_UNAVAILABLE`; no generation |
| on | on | Current corpus can answer; empty/stale-only corpus returns `NO_CONTEXT` |

## Quota accounting and operations

These defaults are **application operational ceilings**, not verified Gemini account/model entitlements. Actual quotas vary by project, model and tier and may change. The operator must inspect the active project's limits in [Google AI Studio](https://aistudio.google.com/) when enabling/changing models or tiers and tune **all** relevant limits. [Official rate-limit documentation](https://ai.google.dev/gemini-api/docs/rate-limits) describes per-project limits and midnight Pacific RPD reset; multiple keys do not imply independent project quotas.

| Budget | Enabled env / default | RPM env / default | Estimated input TPM env / default | RPD env / default |
| --- | --- | --- | --- | --- |
| Global embeddings | `EMBEDDING_QUOTA_ENABLED=true` | `EMBEDDING_RPM_LIMIT=80` | `EMBEDDING_TPM_LIMIT=24000` | `EMBEDDING_RPD_LIMIT=800` |
| Background sub-budget | same global switch | `EMBEDDING_BG_RPM_LIMIT=50` | `EMBEDDING_BG_TPM_LIMIT=18000` | `EMBEDDING_BG_RPD_LIMIT=650` |
| Ask generation | `ASK_QUOTA_ENABLED=true` | `ASK_RPM_LIMIT=10` | `ASK_TPM_LIMIT=200000` | `ASK_RPD_LIMIT=400` |

Background must satisfy **both global and background** budgets. Interactive Semantic/Ask query embeddings satisfy global only, leaving configured capacity that background cannot consume. Generation counters are independent; embedding limits still apply to Ask's one query embedding. Positive limits and background ≤ global are validated. Setting a quota switch false explicitly disables that local guard, not the provider's limits.

`EMBEDDING_TOKEN_ESTIMATE_CHARS_PER_TOKEN` and `ASK_TOKEN_ESTIMATE_CHARS_PER_TOKEN` default **2.5**; estimate = ceil(total input characters / configured ratio), minimum 1. Embedding includes **all** inputs plus the fixed symmetric-task prefix in each native batch. Generation includes system instructions, question, serialized reference data, wrappers **and serialized structured-response schema**. No tokenizer/provider `countTokens` call; estimates are intentionally conservative approximations, not guaranteed actual token counts (especially multilingual/code text). Output is bounded separately by `ASK_MAX_OUTPUT_TOKENS`. Citation processing adds zero provider requests/counters.

Both `EMBEDDING_RPD_RESET_ZONE` and `ASK_RPD_RESET_ZONE` default **`America/Los_Angeles`**. Daily windows use timezone-aware local start-of-day to next start-of-day, including 23/25-hour DST days. They do not reset at UTC or Vietnam midnight. Minute windows are fixed UTC minute boundaries.

Flyway **V9** creates only `ai_quota_usage`: fixed low-cardinality quota key, window start/end, request count, estimated input tokens and update time. No note ID/owner/title/query/context/vector/key/hash/response is stored. Existing V1–V8 and vector schema are untouched; Hibernate remains `ddl-auto=validate`, `open-in-view=false`.

Immediately before each real SDK request, a short PostgreSQL transaction obtains the quota-group advisory lock, checks every applicable minute/day window and atomically increments them only if all allow it. One native embedding batch is one request, not one request per chunk/note/cycle. If it exceeds a budget, no call and no usage increment. Count reservation persists before network work; failed/provider-rejected attempts count conservatively without refund. RPD and minute counters survive process restarts and are shared across replicas using the same DB and consistent configuration. No lock/transaction for quota accounting spans provider network work.

Local denial/provider **429** returns safe unavailable status. Background stops that cycle without spinning/sleeping; later scheduled cycles resume pending work. Semantic maps to `SEMANTIC_SEARCH_UNAVAILABLE`; Ask maps to `ASK_RETRIEVAL_UNAVAILABLE` or `ASK_UNAVAILABLE` by phase. No automatic SDK/application retries, fallback or retry loop. Quota exhaustion does not make application health DOWN.

Safe internal counter inspection (operator access only; no new HTTP metrics endpoint):

```sql
SELECT quota_key, window_start, window_end, request_count, estimated_input_tokens
FROM ai_quota_usage
WHERE window_end > CURRENT_TIMESTAMP
ORDER BY quota_key, window_start;
```

Limits: fixed-minute boundaries can burst compared with a sliding provider window; estimated tokens may differ from provider billing. Other applications/keys in the Gemini project are not counted. Use conservative limits, synchronized replica clocks and consistent model/zone/limit settings; changing settings mid-window can alter local accounting. Never manually delete active quota rows to bypass limits.

## AI quota usage retention

Historical V9 quota metadata is automatically pruned independently of whether Gemini is enabled. This is **local database housekeeping, not a Gemini quota reset/refund**. Google remains the external enforcement authority. No payload/owner/Knowledge ID, audit log, Web UI, reset endpoint, new dependency or Flyway migration is added; V9 and reservation logic are unchanged.

| `app.ai.quota-cleanup.*` | Environment | Default |
| --- | --- | --- |
| `enabled` | `AI_QUOTA_CLEANUP_ENABLED` | `true` |
| `minute-retention` | `AI_QUOTA_MINUTE_RETENTION` | `P2D` |
| `daily-retention` | `AI_QUOTA_DAILY_RETENTION` | `P30D` |
| `interval` | `AI_QUOTA_CLEANUP_INTERVAL` | `PT6H` fixed delay after completion |
| `initial-delay` | `AI_QUOTA_CLEANUP_INITIAL_DELAY` | `PT10M` |
| `batch-size` | `AI_QUOTA_CLEANUP_BATCH_SIZE` | `1000` **total** minute/day rows per invocation |

Enabled configuration fails startup unless retentions are 1 millisecond–36500 days, interval 10 seconds–365 days, initial delay 0–365 days and batch size 1–10000. Disabled mode requires only parseable settings/defaults, registers no cleanup scheduler and makes even a direct service call a no-op; health/reservation remain unaffected. Startup does not perform synchronous cleanup. Existing scheduling and UTC `Clock` are reused, with no provider call, sleep or unbounded drain loop.

`AiQuotaCleanupService` computes one Instant snapshot: cutoffs are `now - configured retention`. Only keys ending **exactly** in `-minute` or `-day` are covered, including future prefixes following that internal contract. Unexpected window suffixes are conservatively retained. Eligibility requires **both** persisted `window_end < cutoff` and `window_end < now`; equality, retained history, active and future windows are protected even with tiny retention. Daily classification is by key, never an assumed 24-hour duration: stored timestamptz ends cover Pacific 23/25-hour DST days without reconstruction or JVM-local dates. Inspection SQL above remains safe; old history is available only until eligible batches process it.

A separate PostgreSQL `pg_try_advisory_xact_lock(83452003)` guards one short independent transaction (30-second JDBC transaction timeout). If another replica holds it, this cycle skips without waiting. It never acquires reservation locks 83452001/83452002 or serializes interactive AI calls behind cleanup. A parameterized JdbcClient CTE selects PKs ordered `window_end, quota_key, window_start ASC`, `LIMIT batch-size`, `FOR UPDATE SKIP LOCKED`, then deletes only those PKs and returns aggregate minute/day counts. Locked history is retained for a later cycle; otherwise oldest eligible rows go first. The guard releases on commit/rollback; retries/restarts are idempotent. Errors roll back and emit a sanitized warning; later scheduled cycles resume. Successful activity logs only deletion totals/elapsed time, never individual rows/content/credentials. See [PostgreSQL lock semantics](https://www.postgresql.org/docs/17/functions-admin.html#FUNCTIONS-ADVISORY-LOCKS) and [SKIP LOCKED](https://www.postgresql.org/docs/17/sql-select.html#SQL-FOR-UPDATE-SHARE).

Reservations continue to use current windows/persistent counters, count failed attempts conservatively and enforce global/background embedding plus independent Ask RPM/estimated TPM/RPD across replicas/reconstructed limiters. All replicas must keep clocks synchronized and use consistent quotas/reset zones/retention; clock rollback or incompatible deployments are not solved by pruning. Cleanup can lag during outages, lock contention or a large backlog: the batch is a work bound, not an exact deletion deadline. V9's existing PK is retained; eligibility may scan history, and any future retention index needs measured evidence and a forward migration. PostgreSQL autovacuum remains responsible for reclaiming deleted row space.

## Automatic Gemini migration and reindex

The generic architecture remains: EmbeddingProperties/Strategy/Source/Vectors, MarkdownChunker, repository, indexer and scheduler. The explicit strategy marker is **`semantic-source-v2:provider=gemini:symmetric-text-v1:...`**. In addition to model/dimensions/chunker/settings/endpoint, it identifies changed provider input semantics: both documents and queries prepend `task: semantic similarity | text: `; Embedding 2 uses no unsupported `taskType` ([embedding guidance](https://ai.google.dev/gemini-api/docs/embeddings)). API key/quota settings are not semantic identity.

Old rows remain physically present but fail the shared complete/current strategy hash predicate. Automatic pending selection marks them stale, performs bounded background generation and atomically replaces complete sets after rechecking current source. Model/dimension/endpoint/chunker/input-strategy changes naturally repeat this process without manual deletes, migration edits or dimension truncation. Failed/denied work stays pending; normal CRUD/restore never invokes Gemini. There can be partially indexed/temporarily empty Semantic/Ask coverage during migration. Multi-request notes whose indexing stops mid-generation retry their full transient chunk set later; partial-progress persistence is deferred.

## Retrieval, context and answer contract

Authenticated, CSRF-protected `POST /api/ask` accepts only trimmed non-blank `question`, max 2000 UTF-16 units, bounded JSON body 16 KiB. Unknown fields are rejected. Existing verified OIDC owner context scopes all retrieval; no client ownership override or public/unlisted endpoint. Full response/error examples: [API contract](API.md#ask-my-knowledge-authenticated-owner-only).

1. One existing `EmbeddingClient` query embedding, validated by existing vector semantics.
2. Owner-filtered current complete compatible sets, exact cosine, deterministic ties. SQL per-note cap precedes global chunk cap, avoiding one long note monopolizing all context.
3. Assemble JSON untrusted reference data, whole chunks where possible, Unicode-safe final trim. The serialized budget includes escaped text and title/slug/sourceRef/chunk metadata. Deterministic request-local opaque `S1`/`S2` refs map to exactly one retrieved RagChunk and its **included text**, not a permanent note identifier. If context is trimmed, unsent suffixes/unselected chunks cannot become evidence/citations.
4. No usable context: `200 {status:"NO_CONTEXT",answer:null,citations:[]}`, **zero generation calls**. Without a calibrated threshold this means unavailable compatible corpus, not proof that indexed notes are semantically relevant/irrelevant.
5. Otherwise one `KnowledgeAnswerClient` request returns ordinary Java `AnswerDraft` `{blocks:[{markdown,sourceRefs}]}`. Native SDK 1.75.0 `responseMimeType("application/json")`/`responseJsonSchema(...)` config uses a small object/array/string JSON Schema ([Gemini structured outputs](https://ai.google.dev/gemini-api/docs/structured-output), [native generation API](https://ai.google.dev/api/generate-content)). SDK DTOs remain inside `ai/gemini`, no tools/functions or continuation. Configured `ASK_MODEL` must support this schema; unsupported models fail safely without switching models or relaxing validation.
6. Strict JSON parsing rejects duplicate keys, trailing tokens, unknown fields/forged metadata, non-string refs and malformed output. A complete single STOP candidate is required; incomplete generation is not continued/retried. Bounds: raw generated JSON ≤131072 units; 1–24 blocks, non-blank Markdown ≤8192/block and ≤65536 total, ≥1 ref/block, no within-block duplicate refs, ≤128 total occurrences, unique citations ≤included chunk count (configured max 100).
7. Backend `AnswerCitations` validates all refs against this request's source map, atomically rejects the entire draft on any unknown/empty/invalid ref, then assigns browser `C1` IDs by first answer occurrence. Reuse across blocks shares a citation; two chunks of one note remain distinct. The API is now `{status,answer:{blocks:[{markdown,citationIds}]},citations:[{id,source:{id,title,slug},chunkIndex,evidence}]}`. This replaces the old string/separate-sources contract; deploy matching Web/API together. All metadata/indices come from backend snapshots, never arbitrary model IDs/titles/slugs.
8. Evidence is backend-derived, not a model quote: exact leading substring of the included chunk, surrounding whitespace stripped, ≤400 UTF-16 units, safe at surrogate boundaries. No fuzzy matching or invented ellipsis; it is locally verified against the retrieved original chunk. This deterministic compact prefix may not cover the precise claim span. Evidence existence/source linkage **does not prove logical entailment**, and generated answers may still misinterpret evidence. No extra DB/provider verification request or new config/migration.

Only persisted **current** title/summary/Markdown chunks participate, not historical revisions/images/metadata relationships. Restore participates after it becomes current and reindexes. Foreign owners, stale hashes, incompatible models/dimensions/versions and incomplete sets are excluded before distance evaluation. All visibility states remain private workspace context. Source title/slug/chunk text share one SQL snapshot. Concurrent edits/deletion after that snapshot may precede the final answer; the UI does not claim a permanently fresh/locked answer.

Both provider calls occur outside DB transactions/connections. The existing background worker still leases its per-note advisory-lock connection as documented in Semantic Retrieval; Ask does not reuse that lease. No synchronous backfill, stored query vectors, multi-query, rerank, hybrid search, agent/tool execution, cache, conversations, feedback table or question/answer persistence.

System instructions require factual grounding in supplied notes, acknowledge insufficient details, use only supplied refs and treat both reference content/question as **data, not commands**. Untrusted JSON is never interpolated into system instructions. A source asking to cite S999 has no authority; domain validation rejects it. No prompt design can prove immunity to model hallucination/injection; the protections are no tools/external execution, strict owner retrieval, bounded context, all-or-nothing local citation validation and inert rendering. Source-linked block citations are implemented, but exact factual/claim entailment verification is not.

## Web and privacy boundary

Protected dynamic `/ask` uses the existing workspace/editorial sidebar, a multiline question and explicit Ask/Cmd-Ctrl Enter only. Regular Enter adds a line; mount/typing do not call AI. Active duplicate submits are suppressed. Cancel view/unmount aborts browser work; sequence guards ignore obsolete results, but cannot guarantee cancellation/refund of already-started upstream calls. Controlled loading/no-context/disabled/retrieval/generation errors preserve drafts and offer manual retry only.

Focused `/api/ask-my-knowledge` accepts a bounded question-only JSON POST, rejects cross-origin `Origin`/`Sec-Fetch-Site`, and forwards existing HttpOnly session and backend CSRF server-side. Origin host is validated against incoming `Host`, not Next's potentially different internal URL host; deployment proxies must preserve/validate external Host. No generic proxy, browser provider configuration or `NEXT_PUBLIC_*` key. `/ask` is noindex/private-no-store/no-referrer.

Questions/answers/citations/sourceRefs/evidence remain transient in application/browser memory, not URL/search params, local/session storage, DB or conversation history. No V10 is added. Limited Markdown renders paragraphs/lists/emphasis/code only; raw HTML/images, arbitrary active links, model footnotes and Mermaid cannot manufacture active citation controls. Structured `[1]` buttons beside blocks have accessible source-title names/keyboard activation/aria-controls and focus plain-text evidence without changing URLs. Sources / Evidence groups each note once with its distinct cited chunks, wrapping long evidence safely. Open note links alone navigate to validated `/knowledge/{slug}`; exact scroll-to-chunk/heading-aware jumps are deferred rather than guessing fragments or altering Reading anchors. Citation controls are outside aria-live output to avoid noisy announcements. Web mapping rejects malformed/orphaned/duplicate/reordered citation structures and strips non-contract provider/owner fields. Citation linkage is verified, **not logical claim entailment**; notes can change after the retrieval snapshot and answers can be wrong.

Enabling embeddings sends private note-derived inputs to **Google Gemini**. Semantic sends queries. Ask sends its question to query embedding and selected private context/question to generation. Content does **not** remain local. Review [pricing](https://ai.google.dev/gemini-api/docs/pricing) and [current terms](https://ai.google.dev/gemini-api/terms): Free Tier/unpaid handling may use submitted content for product improvement and human review; paid/jurisdiction-specific conditions differ. Do not send confidential data under unsuitable terms. Application non-persistence is not a promise about Google's retention/usage. UI explicitly warns before submitting; enabling credentials/account usage remains an operator decision.

Application code does not log questions/answers/chunks/context/vectors/provider bodies/keys or auth headers. Provider exceptions drop bodies/causes. Only safe aggregate indexing/quota-boundary information is logged, with no owner/note/query metric labels. Restrict proxy/access/APM/body-debug logs; Semantic query URLs need redaction. No live key, private note or real provider request was used in automated/browser QA.

## Previous Ask foundation verification

Local `./gradlew test` and `./gradlew clean build` ran successfully; the final clean build passed **224 API tests, zero skipped/failures**, including 19 native-SDK adapter, 15 real PostgreSQL Ask/quota/migration integration, 10 Ask service and 3 Ask configuration tests. `npm test` passed **84 Web tests** (12 new Ask tests); `npm run lint`, `npm run build` and `git diff --check` succeeded. One added quota assertion initially expected an existing zero-count row after an early global denial; it was corrected to assert zero total usage even when no background row was created, then the full clean build passed.

API tests use deterministic embeddings/capturing generation plus native SDK loopback HTTP; real PostgreSQL/pgvector Testcontainers verify Flyway V1–V9, exact ranking/diversity, owner/current filtering, model/dimension migration, quota persistence/concurrency/day reset and quota-limited resumable reindex. SDK tests verify native batch contracts, input accounting, deadlines including stalled bodies, invalid outputs and zero retry after denial/429/errors. Gradle explicitly disables production AI and clears the key to prevent accidental ambient live requests.

Browser QA used the actual production Next.js build with a disposable loopback backend and **synthetic** notes/session responses, not real OAuth or Gemini. Verified desktop 1366px/mobile 390px layout, Enter newline/explicit submit, one active request despite duplicate shortcut, loading/no-context/disabled/retrieval/generation errors/manual retry, canceled-result suppression, inert Markdown/Mermaid, structured source → Reading, mobile drawer and unchanged Keyword/Semantic/Quick Search. Counters confirmed one request per explicit submit, none while typing/switching Semantic, and no Semantic/Ask request from Quick Search. It exposed an internal-URL-vs-Host origin mismatch; that was fixed with a regression test. Real owner auth/CSRF/persistence semantics are separately covered by API integration tests. **Zero external Gemini calls**; live account quota/model availability/retrieval/answer quality still needs an operator-approved environment check.

## Source-linked Answers verification and limitations

SDK remains 1.75.0; existing embedding/config/native batch/generation paths compile and native loopback contracts continue to pass. Domain tests cover multiple blocks/refs, exact chunk mapping/indices, deterministic numbering/reuse, unknown/empty/duplicate refs, malformed/oversized output, reference complexity, exact compact Unicode-safe evidence and context trimming. Real pgvector tests preserve owner/current/model/dimension/completeness/deletion filtering and add exact citation mapping, one embedding/one generation and concurrent edit snapshot behavior. Existing persistent global/background RPM/estimated TPM/RPD/DST/concurrency and migration tests remain unchanged in semantics.

Local `./gradlew test` and `./gradlew clean build` succeeded; the final clean build passed **244 API tests, zero skipped/failures/errors**, including 31 native-SDK adapter, 17 PostgreSQL Ask integration, 10 Ask service, 6 citation validation and 3 Ask configuration tests. `npm test` passed **87 Web tests**; `npm run lint`, `npm run build` and `git diff --check` succeeded. No dependency version, embedding strategy, quota persistence schema or Flyway migration changed.

Production-build browser QA used a disposable loopback fixture with synthetic notes/session responses: single/multiple sources, repeated citations, two chunks of one note grouped once, keyboard focus on the exact evidence item without URL mutation, inert fabricated Markdown links/labels/HTML/Mermaid, and Open note → the correct Reading slug with unchanged heading anchors. Mobile 390px had no horizontal overflow, including long unbroken evidence. Loading, NO_CONTEXT, disabled/retrieval/generation failures, malformed citations and explicit manual retry preserved the draft without partially rendered citations. Aggregate fixture counters showed eight Ask requests for eight explicit submissions, including the one manual retry. **Zero external Gemini calls** in automated and fixture QA; live provider availability and answer quality are not verified by these fixtures. Existing Keyword/Semantic/Quick Search regression coverage passed with the Web suite; the previous foundation browser checks are recorded separately above.

Known limits: no calibrated relevance threshold, proof of per-claim entailment, exact in-document jump, token-aware chunker, ANN index, hybrid/reranking, durable partial indexing progress, multi-model parallel corpus, saved chat, distributed HTTP sessions, guaranteed upstream abort or live answer/private-corpus quality evaluation. Compact prefix evidence proves existence but may not select the most explanatory sentence. Retained revisions/attachments remain excluded. Operational quotas are estimates/fixed windows, not authoritative provider quotas. Snapshot concurrency and Free Tier privacy restrictions are documented above.

## AI quota retention verification

Focused cleanup tests, full `./gradlew test` and `./gradlew clean build` ran successfully. The final clean build passed **268 API tests, zero failures/errors/skipped**, including 15 cleanup configuration/scheduler tests and 9 real PostgreSQL cleanup integration tests. Coverage includes strict/equal cutoffs, distinct 2/30-day retention, active/future/unknown rows, tiny retention with unchanged current RPD counters, deterministic total-batch ordering, stored 23/25-hour DST ends, disabled cleanup with working reservations, reconstructed limiters, simultaneous replica reservations/cleanup, two actual cleaner invocations with non-blocking guard and skipped historical row locks. Existing quota/provider/OIDC/owner/visibility/CSRF/source-citation regressions remain passing, without changing their implementation.

Web `npm test` passed **87 tests**; `npm run lint`, `npm run build` and `git diff --check` succeeded. Web code and infrastructure were untouched: no browser-visible behavior changed, so browser QA was not required for this maintenance milestone. **Zero external Gemini calls**; tests use deterministic providers/native-SDK loopback mocks and isolated Testcontainers data. SDK stays 1.75.0 and Flyway V1–V9 remain unchanged, with no V10. Backlog/scan, replica-clock consistency and autovacuum limitations are documented in the retention section above.

Quota usage retention and **offline synthetic Retrieval quality evaluation** are complete. [Evaluation](RETRIEVAL_EVALUATION.md) separates RAG note/chunk HitRate/Recall@K and context diversity from Semantic note retrieval, using the actual owner/current-compatible repository SQL and unchanged per-note cap. A long-playbook case finds the correct note but misses the relevant section; this is not a generation/citation correctness score. No AskContext source/character-budget trimming, provider semantic quality or claim entailment is measured. No additional provider calls, quota state, strategy, schema or SDK changes are introduced.

**RAG chunk-selection evaluation is complete**, comparing caps 1/2/3, limits 6/8/10/12 and four paired chunk/overlap variants on an expanded synthetic long-note corpus. It now measures actual `AskContext.assemble` with the unchanged 6-source/24000-character budgets, including included-marker coverage and serialized-context cost proxies. It still measures no generation/claim entailment and makes zero external Gemini calls. [Full matrix](RETRIEVAL_EVALUATION.md#rag-chunk-selection-matrix) explains q-playbook's within-note rank3/cap2 exclusion and remaining multi-section misses. **KEEP BASELINE**: no production cap/limit/chunker or quota changes. Raw retrieval growth is not automatically generation input growth because source/character budgets remain active.

**Manual Live Gemini Retrieval Evaluation** is now a separate explicitly gated JavaExec. It uses the production embedding adapter, current model/dimensions, baseline chunking and disposable synthetic-only pgvector data; cached vectors serve Semantic Search and twelve RAG cap/limit variants. No answer client/generation call is made. Global/background quotas remain active, with hard run request/token limits, bounded local-minute pacing and no provider retries. Normal tests/build/offline evaluator/CI stay Gemini-offline; production defaults and the user's local secret file remain unchanged. [Contract and actual run evidence](RETRIEVAL_EVALUATION.md#manual-live-gemini-retrieval-evaluation) distinguish synthetic retrieval quality from answer entailment or private-corpus quality. Follow-up selection/tuning, hybrid retrieval, auto tagging/summarization, exact source navigation and memory require a separately reviewed milestone, not automatic implementation.

The one approved synthetic live run is complete: `gemini-embedding-2`/768, eight embedding requests/115 inputs/33455 estimated tokens, **no generation**. Baseline note Recall@8 is 1.0, chunk/conditional recall .9717, note-hit/chunk-miss 2 (offline 6); cap3 recovers only PostgreSQL's late section, leaving playbook's late chunk at within-note rank6. Recommended next: focused long-note supporting-chunk/context-cost evaluation, separately reviewed before tuning; no private-corpus or answer-quality claim.
