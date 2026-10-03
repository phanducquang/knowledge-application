# Gemini-powered Ask My Knowledge

Owner-only, optional **single-turn** answers grounded in current indexed Knowledge. Semantic Search, FTS, Quick Search, Reading/sharing, relationships and authoring contracts remain intact. No conversation/history storage, agents, tools, external web search or public AI endpoint.

## Provider and configuration

Production uses the official GA [Google Java GenAI SDK `1.70.0`](https://github.com/googleapis/java-genai/releases/tag/v1.70.0), Maven `com.google.genai:google-genai:1.70.0`. SDK types are isolated in `ai/gemini`; services depend on existing `EmbeddingClient` and the small `KnowledgeAnswerClient`. The old compatible embedding adapter is removed; no runtime provider switch or new framework.

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

`EMBEDDING_TOKEN_ESTIMATE_CHARS_PER_TOKEN` and `ASK_TOKEN_ESTIMATE_CHARS_PER_TOKEN` default **2.5**; estimate = ceil(total input characters / configured ratio), minimum 1. Embedding includes **all** inputs plus the fixed symmetric-task prefix in each native batch. Generation includes system instructions, question, serialized reference data and wrappers. No tokenizer/provider `countTokens` call; estimates are intentionally conservative approximations, not guaranteed actual token counts (especially multilingual/code text). Output is bounded separately by `ASK_MAX_OUTPUT_TOKENS`.

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

Limits: fixed-minute boundaries can burst compared with a sliding provider window; estimated tokens may differ from provider billing. Other applications/keys in the Gemini project are not counted. Use conservative limits, synchronized replica clocks and consistent model/zone/limit settings; changing settings mid-window can alter local accounting. Counter retention/pruning is not implemented, so operators must plan safe maintenance for expired minute rows. Never delete active/day rows to bypass quotas.

## Automatic Gemini migration and reindex

The generic architecture remains: EmbeddingProperties/Strategy/Source/Vectors, MarkdownChunker, repository, indexer and scheduler. The explicit strategy marker is **`semantic-source-v2:provider=gemini:symmetric-text-v1:...`**. In addition to model/dimensions/chunker/settings/endpoint, it identifies changed provider input semantics: both documents and queries prepend `task: semantic similarity | text: `; Embedding 2 uses no unsupported `taskType` ([embedding guidance](https://ai.google.dev/gemini-api/docs/embeddings)). API key/quota settings are not semantic identity.

Old rows remain physically present but fail the shared complete/current strategy hash predicate. Automatic pending selection marks them stale, performs bounded background generation and atomically replaces complete sets after rechecking current source. Model/dimension/endpoint/chunker/input-strategy changes naturally repeat this process without manual deletes, migration edits or dimension truncation. Failed/denied work stays pending; normal CRUD/restore never invokes Gemini. There can be partially indexed/temporarily empty Semantic/Ask coverage during migration. Multi-request notes whose indexing stops mid-generation retry their full transient chunk set later; partial-progress persistence is deferred.

## Retrieval, context and answer contract

Authenticated, CSRF-protected `POST /api/ask` accepts only trimmed non-blank `question`, max 2000 UTF-16 units, bounded JSON body 16 KiB. Unknown fields are rejected. Existing verified OIDC owner context scopes all retrieval; no client ownership override or public/unlisted endpoint. Full response/error examples: [API contract](API.md#ask-my-knowledge-authenticated-owner-only).

1. One existing `EmbeddingClient` query embedding, validated by existing vector semantics.
2. Owner-filtered current complete compatible sets, exact cosine, deterministic ties. SQL per-note cap precedes global chunk cap, avoiding one long note monopolizing all context.
3. Assemble JSON untrusted reference data, whole chunks where possible, Unicode-safe final trim. The serialized budget includes escaped text, title/slug/source/chunk metadata. Sources deduplicate by Knowledge ID in first-appearance order; excerpts ≤600 units, including ellipsis.
4. No usable context: `200 {status:"NO_CONTEXT",answer:null,sources:[]}`, **zero generation calls**. Without a calibrated threshold this means unavailable compatible corpus, not proof that indexed notes are semantically relevant/irrelevant.
5. Otherwise one `KnowledgeAnswerClient` generation request, returning `ANSWERED` plus note-level `{id,title,slug,excerpt}` sources. Output must be non-blank and ≤65536 units. Empty/invalid/provider-failed output becomes safe `ASK_UNAVAILABLE`, never a fabricated fallback.

Only persisted **current** title/summary/Markdown chunks participate, not historical revisions/images/metadata relationships. Restore participates after it becomes current and reindexes. Foreign owners, stale hashes, incompatible models/dimensions/versions and incomplete sets are excluded before distance evaluation. All visibility states remain private workspace context. Source title/slug/chunk text share one SQL snapshot. Concurrent edits/deletion after that snapshot may precede the final answer; the UI does not claim a permanently fresh/locked answer.

Both provider calls occur outside DB transactions/connections. The existing background worker still leases its per-note advisory-lock connection as documented in Semantic Retrieval; Ask does not reuse that lease. No synchronous backfill, stored query vectors, multi-query, rerank, hybrid search, agent/tool execution, cache, conversations, feedback table or question/answer persistence.

System instructions require factual grounding in supplied notes, acknowledge insufficient details and treat both reference content/question as **data, not commands**. Untrusted JSON is never interpolated into system instructions. No prompt design can prove immunity to model hallucination/injection; the architectural protections are no tools/external execution, strict owner retrieval, bounded context and inert answer rendering. Precise inline/per-claim citations are deferred to **Source-linked Answers**.

## Web and privacy boundary

Protected dynamic `/ask` uses the existing workspace/editorial sidebar, a multiline question and explicit Ask/Cmd-Ctrl Enter only. Regular Enter adds a line; mount/typing do not call AI. Active duplicate submits are suppressed. Cancel view/unmount aborts browser work; sequence guards ignore obsolete results, but cannot guarantee cancellation/refund of already-started upstream calls. Controlled loading/no-context/disabled/retrieval/generation errors preserve drafts and offer manual retry only.

Focused `/api/ask-my-knowledge` accepts a bounded question-only JSON POST, rejects cross-origin `Origin`/`Sec-Fetch-Site`, and forwards existing HttpOnly session and backend CSRF server-side. Origin host is validated against incoming `Host`, not Next's potentially different internal URL host; deployment proxies must preserve/validate external Host. No generic proxy, browser provider configuration or `NEXT_PUBLIC_*` key. `/ask` is noindex/private-no-store/no-referrer.

Questions/answers remain transient in application/browser memory, not URL/search params, local/session storage, DB or conversation history. Limited Markdown renders paragraphs/lists/emphasis/code only; raw HTML/images, arbitrary active links and Mermaid are suppressed/inert. Structured source links alone navigate to validated `/knowledge/{slug}`; plain excerpts are escaped. Sources describe context notes, **not verified per-claim citations**; generated answers can be wrong.

Enabling embeddings sends private note-derived inputs to **Google Gemini**. Semantic sends queries. Ask sends its question to query embedding and selected private context/question to generation. Content does **not** remain local. Review [pricing](https://ai.google.dev/gemini-api/docs/pricing) and [current terms](https://ai.google.dev/gemini-api/terms): Free Tier/unpaid handling may use submitted content for product improvement and human review; paid/jurisdiction-specific conditions differ. Do not send confidential data under unsuitable terms. Application non-persistence is not a promise about Google's retention/usage. UI explicitly warns before submitting; enabling credentials/account usage remains an operator decision.

Application code does not log questions/answers/chunks/context/vectors/provider bodies/keys or auth headers. Provider exceptions drop bodies/causes. Only safe aggregate indexing/quota-boundary information is logged, with no owner/note/query metric labels. Restrict proxy/access/APM/body-debug logs; Semantic query URLs need redaction. No live key, private note or real provider request was used in automated/browser QA.

## Verification and limitations

Local `./gradlew test` and `./gradlew clean build` ran successfully; the final clean build passed **224 API tests, zero skipped/failures**, including 19 native-SDK adapter, 15 real PostgreSQL Ask/quota/migration integration, 10 Ask service and 3 Ask configuration tests. `npm test` passed **84 Web tests** (12 new Ask tests); `npm run lint`, `npm run build` and `git diff --check` succeeded. One added quota assertion initially expected an existing zero-count row after an early global denial; it was corrected to assert zero total usage even when no background row was created, then the full clean build passed.

API tests use deterministic embeddings/capturing generation plus native SDK loopback HTTP; real PostgreSQL/pgvector Testcontainers verify Flyway V1–V9, exact ranking/diversity, owner/current filtering, model/dimension migration, quota persistence/concurrency/day reset and quota-limited resumable reindex. SDK tests verify native batch contracts, input accounting, deadlines including stalled bodies, invalid outputs and zero retry after denial/429/errors. Gradle explicitly disables production AI and clears the key to prevent accidental ambient live requests.

Browser QA used the actual production Next.js build with a disposable loopback backend and **synthetic** notes/session responses, not real OAuth or Gemini. Verified desktop 1366px/mobile 390px layout, Enter newline/explicit submit, one active request despite duplicate shortcut, loading/no-context/disabled/retrieval/generation errors/manual retry, canceled-result suppression, inert Markdown/Mermaid, structured source → Reading, mobile drawer and unchanged Keyword/Semantic/Quick Search. Counters confirmed one request per explicit submit, none while typing/switching Semantic, and no Semantic/Ask request from Quick Search. It exposed an internal-URL-vs-Host origin mismatch; that was fixed with a regression test. Real owner auth/CSRF/persistence semantics are separately covered by API integration tests. **Zero external Gemini calls**; live account quota/model availability/retrieval/answer quality still needs an operator-approved environment check.

Known limits: no calibrated relevance threshold, verified per-claim citations, token-aware chunker, ANN index, hybrid/reranking, durable partial indexing progress, multi-model parallel corpus, saved chat, distributed HTTP sessions, guaranteed upstream abort, expired quota cleanup or model quality evaluation. Retained revisions/attachments remain excluded. Operational quotas are estimates/fixed windows, not authoritative provider quotas. Snapshot concurrency and Free Tier privacy restrictions are documented above.

Recommended next milestone: **Source-linked Answers**, precise validated citations/source navigation without assuming note-level context proves every generated claim. Do not start it automatically.
