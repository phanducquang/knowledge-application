# Architecture

## 1. Architecture goal

Build a personal knowledge application that is simple to operate initially, with public sharing, revision recovery and owner-only wiki links/backlinks now available and a clear path toward richer relationships and AI-assisted retrieval.

The system uses a monorepo containing two independent applications:

```text
Browser
   | Google OIDC login / HttpOnly JSESSIONID
   v
Next.js Web
   |  Server Components: authenticated no-store reads
   |  Server Actions: cookie + CSRF protected mutations
   |  REST API
   v
Spring Boot API / Spring Security
   |                  \
   v                   v
PostgreSQL         Object Storage
                     R2 / MinIO
```

## 2. Repository boundaries

```text
apps/web
```

Owns:

- authenticated workspace UI
- public article UI
- routing
- responsive behavior
- rendering Markdown/content
- client-side interaction

```text
apps/api
```

Owns:

- authentication/authorization
- knowledge management
- visibility/sharing rules
- tags and collections
- revisions
- search APIs
- persistence
- object-storage integration

```text
infra
```

Owns future deployment configuration only. Application business logic must not be placed here.

## 3. Deployment model

The applications are stored together but remain independently deployable.

Possible routing model:

```text
/        -> Next.js
/api/*   -> Spring Boot
```

An alternative deployment may expose the API on a dedicated subdomain. The repository structure must not depend on either option.

The current web integration keeps the Spring Boot base URL server-only through `KNOWLEDGE_API_BASE_URL`. Knowledge routes are dynamically rendered and API reads use `cache: no-store`, preventing mutable owner data from becoming a static Next.js artifact. Browser mutations call small Next.js Server Actions, which send only the six editable fields to Spring Boot and revalidate the affected Library, Reading, Edit and Search paths. This is transport orchestration, not a second domain or authorization layer.

The workspace shell receives the real owner-scoped Knowledge list for recently updated Quick Search content and independently loads owner-scoped Collection summaries. Empty collections therefore remain navigable, and Create/Edit can select them before any note belongs to them. `/collections` uses narrow CSRF-aware Server Actions for management; `/collections/{id}` renders the filtered owner list. Non-empty Full Search and Quick Search queries share PostgreSQL ranking through one Spring endpoint. Interactive browser queries use a focused same-origin Next.js Route Handler, which calls the existing server-only API client; no generic backend proxy or public API base URL is introduced.

Spring Security is the authoritative authentication layer. Google OAuth2/OIDC establishes a normal server-side `HttpSession`; the browser holds only an HttpOnly `JSESSIONID`. Next.js Server Components, Server Actions and the focused Search BFF forward the incoming cookie server-side. Workspace routes verify the session through `/api/auth/me`, while Spring independently protects every owner CRUD/Search endpoint.

State-changing calls remain CSRF-protected. The server-only Next.js transport first reads `/api/auth/csrf` with the same session cookie, then sends the returned token under its declared header name. Read-only calls do not request a token. The login and logout Route Handlers are explicit boundaries rather than a generic API proxy.

Anonymous public reading uses a deliberately separate path: Next.js `/k/{slug}` calls `GET /api/public/knowledge/{slug}` server-side without forwarding a session. The public service queries by globally unique slug and `PUBLIC` visibility in one repository operation and maps to a minimal DTO without owner ID, internal numeric ID, visibility or authentication data. PRIVATE, UNLISTED and missing rows therefore share the same 404 result. The fetch is dynamic and `no-store`, so changing a note back to PRIVATE revokes app access immediately.

Anonymous UNLISTED reading is a third, separate access model. Next.js `/s/{shareToken}` calls `GET /api/shared/knowledge/{shareToken}` without forwarding the owner session. The opaque token is a 32-byte `SecureRandom` value encoded with unpadded URL-safe Base64. The repository lookup includes both `share_token` and `visibility = UNLISTED`, so invalid, inactive and rotated tokens all receive the same opaque 404. Both the API response and dynamic Next.js route are `no-store`; the page also sends `noindex, nofollow, noarchive` and `Referrer-Policy: no-referrer`.

Authenticated sharing UI uses a focused `PATCH /api/knowledge/{id}/visibility` and the two owner-only UNLISTED link-management endpoints through narrow Next.js Server Actions. The shared dialog receives visibility and operations from its Reading/Edit coordinator rather than owning a second source of truth. On Edit, share changes wait for an in-flight PUT, flush pending fields, then PATCH visibility; autosave is held until that sequence finishes and subsequently carries the confirmed value. This ordering prevents a stale full-draft PUT from reverting sharing state.

Authenticated revision history follows the same transport boundary. Next.js `/knowledge/{slug}/history` loads compact pages and a selected full snapshot through server-only API calls; restore uses a CSRF-aware Server Action. The API resolves the parent Knowledge by current owner before querying its revisions, while the browser never receives an owner identifier. Editor navigation flushes pending autosave work first so the history screen does not strand a local draft.

## 4. Core domain

The initial conceptual domain is:

```text
User
 └── Knowledge
      ├── Tags
      ├── Collection
      ├── Attachments
      ├── Revisions
      └── Sharing / Visibility
```

Expected knowledge fields conceptually include:

- id
- owner
- title
- slug
- summary
- Markdown content
- visibility
- share token where applicable
- created/updated/published timestamps

The backend foundation makes the first persistence decision concrete:

- `knowledge.id`: database-generated numeric identifier
- `knowledge.owner_id`: UUID reserved for later owner-based authorization; no user table is introduced before authentication
- `title`, stable unique `slug`, optional `summary`, and canonical Markdown `content`
- an optional owner-scoped reusable Collection and owner-scoped reusable Tags
- `visibility`: `PRIVATE`, `UNLISTED`, or `PUBLIC`, constrained by both the application enum and database schema
- created, updated, optional first-publication, and optional share-token-created timestamps

Flyway owns the PostgreSQL schema. Hibernate validates the mapped schema and must not create or update it automatically. V4 adds nullable `share_token` and `share_token_created_at` columns directly to Knowledge, which is the smallest model for one current/dormant link per note. A global unique constraint protects token collisions and a pair constraint prevents half-populated token state. Existing rows remain null and no token is generated by SQL. Users remain deferred.

V5 adds append-only `knowledge_revision` snapshots with an `ON DELETE CASCADE` parent relation and a newest-first `(knowledge_id, created_at, id)` index. Snapshot data is intentionally denormalized: title, summary, Markdown, collection display name and sorted tag display names in JSONB. It does not reference live Collection/Tag rows and never copies owner, slug, visibility, publication timestamps or bearer tokens. Existing notes receive one truthful migration-time `CHECKPOINT`; application-created notes receive `CREATE` transactionally.

V6 adds `knowledge_attachment` metadata with an application-generated UUID, cascading Knowledge foreign key, private server-generated object key, original filename, detected media type, byte size and creation time. PostgreSQL never stores image bytes. S3-compatible storage remains private, and stable `attachment://<UUID>` Markdown references resolve through owner/public/shared application routes whose queries bind the attachment to the exact accessible parent Knowledge item.

V7 adds revision-aware lifecycle state and a durable Knowledge-deletion queue. Fresh uploads begin with `orphaned_at` set and receive a configurable grace period. Authoring updates and restores synchronize current references, while cleanup rechecks both current Markdown and retained revisions under the same parent Knowledge pessimistic lock used by update, restore, upload and delete. Destruction is object-first; a storage failure leaves PostgreSQL metadata or queue state retryable. Multiple cleanup replicas can do redundant work, but database locking and idempotent S3 deletion make destructive processing converge without a distributed scheduler.

The queue covers known object keys during Knowledge deletion, not every possible cross-system failure. A process crash after S3 put and before attachment metadata persistence can leave an object invisible to DB-driven cleanup. Historical and crash-window unknown objects remain a future bucket-inventory reconciliation concern.

Before meaningful authoring updates, the service captures the current persisted state only when the latest snapshot is at least `KNOWLEDGE_REVISION_INTERVAL` old (default `PT5M`) and not identical. This treats history as periodic recovery checkpoints rather than an autosave log. Focused visibility and link operations bypass revision creation. Restore always appends `BEFORE_RESTORE`, then applies only authoring fields; stable URL, ownership and all sharing/security state are preserved.

There is no optimistic-lock or multi-tab merge protocol yet. Concurrent editors remain last-write-wins; append-only checkpoints provide a recovery path but are not an audit-grade record of every keystroke or actor.

The opaque token is stored retrievably rather than hashed because the owner-management API must return the same existing link. This means database read access can reveal bearer credentials. Database access, backups and operational tooling must therefore be restricted; reverse-proxy/access logs may also contain the token in request paths and should be protected or redacted operationally. The application does not log token values explicitly, and its Spring request dispatcher is kept above DEBUG so an ambient debug flag does not print full token-bearing paths.

Collection and Tag are persisted as reusable owner-scoped entities. `knowledge.collection_id` models the optional many-to-one collection association, while `knowledge_tag` models the many-to-many tag association. Deleting Knowledge cascades only to its join rows; reusable Tag and Collection rows are retained.

Collection management queries always include the current owner. Its list projection counts owned notes, including zero for empty collections, and sorts by normalized display name and ID. Renaming preserves the Collection ID and note associations; revision snapshots intentionally keep their historical denormalized name. Deleting a Collection follows V2's `ON DELETE SET NULL`: notes become unfiled, while their content, slugs, sharing state, attachments and revisions remain. This requires no new schema migration.

Both metadata tables store a stable display name and a PostgreSQL-generated `normalized_name`. Uniqueness on `(owner_id, normalized_name)` prevents case-only or surrounding-whitespace duplicates for one owner without requiring a database extension, while allowing the same name for different owners. The application also collapses internal whitespace and removes leading `#` characters from tag display names before persistence.

The current CRUD API preserves the server-side `APP_OWNER_ID` as the persisted owner partition. It is not proof of identity and cannot grant access by itself. `CurrentOwner` first requires an authenticated OIDC principal whose Google email is verified and equals the backend-only `AUTH_ALLOWED_EMAIL` value case-insensitively, then returns the configured UUID. API payloads cannot choose or modify `owner_id`, and repository reads remain owner-scoped.

The V1 schema intentionally keeps slugs globally unique. This now supports unambiguous public `/k/{slug}` lookup and means collision suffixes are selected globally rather than per owner. Workspace and public URLs reuse the same stable slug; visibility changes never regenerate it. Owner workspace reads by slug still include `owner_id`, while the separate anonymous lookup includes `visibility = PUBLIC` in its repository query.

Knowledge reads use an entity graph for Collection and Tags so the list endpoint does not issue one metadata query per Knowledge row. API responses sort tag display names case-insensitively for deterministic output; tag membership itself is a set rather than an ordered domain relationship.

The first relationship slice uses canonical `[[stable-slug]]` references in Markdown. Owner Reading resolves only slugs in the authenticated owner's note list; anonymous PUBLIC/UNLISTED and historical views retain literal wiki text rather than following private workspace routes. The owner-only backlinks endpoint first verifies the target by ID and owner, then queries same-owner Markdown candidates containing the literal token and validates them outside code/escapes. Results are current-state, deterministic and require no Flyway migration or write-path synchronization. This is intentionally a scan-based personal-library implementation; a stored edge index and backfill can follow if the library grows, without changing stable slug semantics.

The Reading Page renders persisted Markdown through the shared `KnowledgeMarkdown` React pipeline with GitHub-flavored Markdown support. Fenced blocks are highlighted from their explicit language identifiers by a client-compatible lowlight/highlight.js HAST pipeline with a controlled grammar set; unknown or absent languages remain plain code and no auto-detection runs. Explicit `mermaid` fences are intercepted before highlighting and delegated to a focused Client Component, which lazy-loads Mermaid only when a diagram is mounted. Mermaid runs locally with strict security, HTML labels and click behavior disabled, internally generated render IDs, bounded input/edge limits, stale-render protection and a source-preserving error fallback. Its generated SVG is the only narrowly scoped generated markup insertion; arbitrary raw Markdown HTML remains disabled. History is already an interactive Client Component, while private, PUBLIC and UNLISTED pages still server-render their initial article shell and hydrate only diagram blocks. Canonical Markdown remains the sole persisted/revision/search representation. H2-H4 table-of-contents anchors and approximate read time are derived at render time and are not persisted.

Related Articles extends this derived read model through the same relation service and `WikiLinkExtractor`. An authenticated owner-scoped target lookup precedes an entity-graph read of current owned notes, including their persisted Tags and nullable Collection. One candidate gathers all signals before ranking: explicit wiki/backlink tier, shared-tag tier, then Collection-only tier; ties use shared tag count, updated time and ID descending. A bounded limit is applied last. Self/deleted/cross-owner notes and retained revisions never participate, and null Collection pairs do not match. Restore affects relations only through the authoring fields becoming current.

The compact related DTO contains only navigation/display data and ordered reasons. The server-only no-store Web transport renders a flat Related notes list after Backlinks in owner Reading and omits an empty section. External PUBLIC/UNLISTED query/DTO and presentation paths do not call this service or expose private relationships. There is no dedicated relationship index, schema migration, write-path synchronization, AI, embeddings or relation-aware search scoring; dynamic library scans are an explicit personal-library scaling tradeoff.

Knowledge Graph is another read-only current-state projection of the same owner partition, exposed through `/api/knowledge/graph`. The relation service loads current owned Knowledge with the metadata entity graph, builds a stable-slug map and reuses `WikiLinkExtractor` to resolve directed source/target ID pairs. Every current owned note becomes a node, including isolated notes and all visibility states inside the owner workspace. Duplicate wiki targets collapse; self, missing, deleted and cross-owner targets are excluded. Nodes sort by updated time/ID descending, tags alphabetically (case-insensitive then exact), and edges by source/target ID ascending. Collection/Tags remain metadata only and never create edges; retained revisions do not contribute.

The protected `/graph` Server Component fetches only the compact DTO through no-store server-only transport, then initializes a focused React Flow Client Component. Dagre handles presentation-only layout; no visual positions or edges are persisted. Collection-ID filtering uses the induced subgraph, while stable-slug focus emphasizes direct incoming/outgoing neighbors and their incident edges. Graph navigation uses the existing private Reading route and desktop/mobile shell. PUBLIC/UNLISTED APIs/pages never invoke this projection or expose topology. Knowledge/Collection mutations revalidate the graph route without changing authoring, sharing, revision, search or Related Articles semantics.

This keeps the personal library operationally simple: dynamic Markdown scans and an in-memory browser layout, no relationship index/table, graph database, AI, embeddings or schema migration. Large-library scan/layout costs and browser interaction QA remain explicit follow-up limitations.

## 5. Visibility model

### PRIVATE

Only the owner can view the knowledge entry.

### UNLISTED

The owner can view it normally. Other viewers need its backend-generated non-guessable bearer link. The first transition to UNLISTED generates a token when absent; ordinary edits preserve it. Moving to PRIVATE or PUBLIC leaves it stored but immediately inactive, and returning to UNLISTED reactivates it. Explicit owner rotation replaces it and immediately invalidates the old token. It never appears in listings, public search, metadata feeds or intentional indexing.

### PUBLIC

Anyone can view it via the stable `/k/{slug}` route. The anonymous API exposes only article presentation data and never reuses the owner-scoped controller/service.

Authorization must be enforced in the backend, never only in the frontend.

## 6. Content storage

Knowledge content should be stored as Markdown rather than rendered HTML.

Benefits:

- portable
- version-friendly
- easy to export
- friendly to developer workflows
- easy to process later for AI/search

Attachments/images should be stored in object storage rather than as database blobs.

## 7. Search direction

MVP:

- PostgreSQL Full Text Search across title, summary, Markdown source content and useful metadata — implemented
- `simple` text-search configuration for multilingual technical text without English-only stemming/stop words
- generated stored `tsvector` plus GIN index for Knowledge-owned text
- owner-scoped relational collection/tag vector at query time, avoiding denormalized metadata synchronization
- weights A/B/C/D for title, collection-tags, summary and content; relevance ties use updated time and ID
- safe parameterized `websearch_to_tsquery` input with bounded result counts

Do not introduce Elasticsearch solely for the MVP.

Semantic Retrieval Foundation — implemented:

- PostgreSQL 17 with pgvector, enabled by Flyway V8; unconstrained chunk-level vectors, cascading composite Knowledge/owner FK and no ANN index
- only current title/summary/Markdown, not revisions, attachments or Collection/Tags; all visibility states remain owner-internal
- disabled-by-default backend-only official Gemini Java GenAI SDK `1.75.0` behind `EmbeddingClient`; deterministic fake/native-SDK loopback mocks in tests
- bounded scheduled backfill and reindex, with session advisory locking, no provider call in authoring transactions, complete atomic replacement and source recheck
- authoritative SHA-256 freshness includes model/dimension/provider/chunker version/settings; stale or incomplete sets cannot enter the centralized owner-scoped exact cosine query
- no public embedding/vector DTO; see [`SEMANTIC_RETRIEVAL.md`](SEMANTIC_RETRIEVAL.md) for configuration, privacy and operational constraints

Semantic Search — implemented: authenticated `GET /api/search/knowledge/semantic` resolves `CurrentOwner`, embeds one validated query exactly once outside a database transaction, then reuses the embedding repository's current complete-set predicate. SQL selects the nearest chunk per note before the note limit, orders cosine distance ascending then updated time/ID descending, and enriches metadata in the same bounded statement/snapshot. DTOs expose normal navigation metadata and a plain-text source excerpt capped at 600 UTF-16 units; no vectors, model/provider details or distance leave the API. Query vectors are temporary; search never backfills synchronously or retries automatically.

`/search` defaults to unchanged Keyword FTS/live 180ms debounce. Semantic has separate explicit-submit state, draft versus submitted query and cancellation/latest-response protection. Typing/mode switching makes no semantic request; deep links may execute one initial server query. Native history replacement synchronizes submitted URLs without repeating Server Component requests. A focused no-store `/api/knowledge-semantic-search` BFF forwards session cookies through existing transport without CSRF for this GET. Disabled/provider failures produce distinct sanitized 503 states and an explicit Keyword alternative. Empty results mean no current compatible indexed notes/library, not a similarity cutoff. Quick Search, public/shared routes, Related Articles and Graph are unchanged.

Future:

- Grounding-quality evaluation; source existence alone does not prove logical claim entailment

Ask My Knowledge — implemented: authenticated, CSRF-protected `POST /api/ask` accepts only a question. `AskService` calls the existing query embedding boundary once, then `findRagChunks` uses the shared `CURRENT_SET` predicate and exact cosine ordering. Per-note chunk limits precede the global limit in SQL. Deterministic JSON context includes at most 8 chunks, 2 per note, 6 sources and 24000 serialized characters; Unicode-safe final trimming accounts for escaped text/metadata. No current context returns `NO_CONTEXT` with zero generation calls. Otherwise `KnowledgeAnswerClient` makes one native Gemini generation call with untrusted question/reference data separate from system grounding instructions. Both calls occur outside DB transactions/connections; edits after the retrieval snapshot are an explicit concurrency limitation.

The provider-specific adapters/configuration isolate SDK DTOs. Shared backend-only `GEMINI_API_KEY` is explicit, with no ambient credential/Vertex fallback. Defaults are configurable `gemini-embedding-2`/768 and `gemini-3.5-flash-lite`; the old compatible adapter is removed. Strategy marker `semantic-source-v2` includes Gemini symmetric input semantics, so existing V8 rows become stale automatically and bounded backfill replaces them without vector truncation or schema changes.

Flyway V9 adds only `ai_quota_usage`. Short advisory-locked PostgreSQL transactions reserve fixed-minute RPM/estimated-input-TPM and timezone-aware RPD immediately before each SDK request. Background embedding must satisfy global plus smaller background limits; Semantic Search/Ask query embeddings share only the global limit. Generation has independent counters. Reservations persist across restarts/replicas and count failed calls conservatively. Local denial/provider 429 yields a safe unavailable state; indexing stops that cycle. SDK/transport retries are disabled. These operational ceilings are not Gemini's authoritative model/project quotas; see [`ASK_MY_KNOWLEDGE.md`](ASK_MY_KNOWLEDGE.md) for rate configuration, privacy and fixed-window limitations.

Protected `/ask` uses a focused question-only no-store POST BFF, existing session/CSRF forwarding and Host/Origin checks. Typing/mount never calls AI; only explicit button/Cmd-Ctrl Enter submits, with duplicate suppression and abort/latest-response guards. Questions never enter URLs, browser storage or chat persistence. Answer Markdown disallows active links/images/HTML/Mermaid; structured note sources alone link to Reading. No public/shared retrieval, tool execution, web search, rewrites, reranking or agent loop is introduced.

Source-linked Answers — implemented: `KnowledgeAnswerClient` returns ordinary Java `AnswerDraft` blocks with Markdown/sourceRefs; only the Gemini adapter handles SDK/native JSON schema and strict JSON parsing. `AskContext` assigns deterministic request-local `S1` references to one exact current RagChunk plus its included text, including any final context trimming. `AnswerCitations` validates all refs/structure/bounds atomically, derives compact exact evidence from that text and maps backend-owned title/slug/id/chunkIndex. Response `C1` IDs follow first answer occurrence, deduplicate reused chunks and are attached to blocks; citations are the sole authoritative structure for Web grouping. Invalid output returns safe `ASK_UNAVAILABLE`, not partially accepted citations.

Generation still makes one request; schema/prompt overhead is included in estimated TPM. SDK stays 1.75.0, no continuation/tools/retries or quota/embedding strategy changes. No V10, citation persistence, extra SQL/provider verification call or row locks during generation. `/ask` citation buttons focus evidence, grouped by note with Reading links; Markdown cannot manufacture active citation controls. Exact chunk/heading navigation is deferred rather than guessing fragments. Evidence existed in the current retrieval snapshot; later edits are allowed and citation validation does not establish factual/logical entailment.

AI quota retention — implemented: `AiQuotaCleanupService` independently computes Instant/UTC cutoffs from configurable 2-day minute/30-day daily defaults and deletes at most 1000 total expired rows per delayed 6-hour cycle. The JdbcClient CTE orders eligible PKs oldest first and skips row locks. Stored `window_end` must be strictly before its cutoff and now; active/future/equal-boundary rows remain, including Pacific DST days. Only `*-minute`/`*-day` suffixes are covered; unknown shapes remain. A separate non-blocking transaction advisory guard skips overlapping replica cycles without touching reservation locks. Existing global scheduling/Clock are reused; disabled mode is a no-op, with no startup drain, provider calls, Web/reset endpoint, payload storage or migration. Aggregate logs only. Cleanup is local housekeeping, not a reset of Gemini limits; reservation/restart/replica semantics remain unchanged. Configuration, bounds and backlog/scan limitations: [quota operations](ASK_MY_KNOWLEDGE.md#ai-quota-usage-retention).

Retrieval quality evaluation — implemented offline in test scope: one isolated PostgreSQL/pgvector Testcontainer loads 24 synthetic notes and 40 declared queries once, using the real default Markdown chunker, EmbeddingSource inputs/hash, `replace`, `findNearestKnowledge` and `findRagChunks`. A 64-dimensional controlled vocabulary `EmbeddingClient` runs without Spring/Gemini beans or external provider calls. Separate Semantic note and RAG note/chunk metrics, cap/diversity diagnostics, owner/stale/model/dimension/incomplete decoys and repeatable timestamp-independent text/JSON reports preserve unchanged production SQL, schema, SDK and API/Web contracts. Existing `findNearestChunks` adds distances only, never evaluator ranking. Provider semantic/answer quality is not measured; no live mode, threshold tuning or downstream AskContext budget evaluation. [Corpus, schema and observed misses](RETRIEVAL_EVALUATION.md).

RAG chunk-selection evaluation — complete in the same test harness: four extra synthetic long guides extend the matrix corpus to 28 notes/60 queries. Forty-eight cap/total-limit/paired-chunker variants rebuild real complete sets and call `findRagChunks` using isolated configuration, never by mutating application.yml. PostgreSQL-ordered within-note/after-cap ranks attribute missing evidence to per-note or global limits. Conditional supporting-chunk coverage, long-only position classes and raw/actual AskContext budget/cost diagnostics expose note-hit/chunk-miss failures; complete matrix repeats verify timestamp-independent identity. Diagnostic snapshots are confined to one immutable strategy/index and final limited SQL is always executed. **KEEP BASELINE**, no runtime tuning: observed gains are concentrated in one playbook with source-diversity costs, not reliable multi-fixture/live-model evidence. SDK/schema/quotas/API/Web unchanged; zero external Gemini calls. [Matrix and recommendation](RETRIEVAL_EVALUATION.md#rag-chunk-selection-matrix).

Manual live retrieval evaluation — a separate opt-in test-classpath JavaExec, never a normal Test/build/CI dependency. The dedicated-task marker, explicit flag, existing Gemini key/config and bounded request/token budgets gate construction of the real production embedding adapter. Synthetic fixture chunks/query vectors are generated once with native batching, reused across twelve cap/limit configurations, and stored only in an owned ephemeral pgvector Testcontainer. Existing atomic global/background RPM/estimated TPM/RPD reservations protect calls; only this manual task may wait a bounded interruptible local-minute interval. Provider errors/429 stop without retry or partial quality metrics. Same-corpus offline/live reports remain ignored and separate; no application datasource, generation, production tuning, migration or SDK change. Secret-bearing local application.yml is preserved/uncommitted. [Manual contract/results](RETRIEVAL_EVALUATION.md#manual-live-gemini-retrieval-evaluation).

AI metadata suggestions use a distinct provider-neutral `KnowledgeMetadataSuggestionClient`, native Gemini JSON schema and atomic validation. A short transactional loader copies current text/tags; NOT_SUPPORTED orchestration ensures no ambient transaction/connection/lock spans Gemini. The owner-only POST never writes; Edit drains existing serialized autosave, previews and applies only through explicit choices/ordinary checkpoints. Independent disabled-by-default model and metadata minute/day quota keys reuse the existing credential/transport/suffix retention. No retrieval, background enrichment, schema change or anonymous AI. [Contract and external-provider privacy boundary](AI_METADATA_SUGGESTIONS.md).

## 8. Authentication

The application remains personal/single-owner oriented. Spring Security OAuth2 Client uses Google's standard OIDC provider, grants the owner authority only to the verified allowlisted email, and keeps authentication in the servlet session. The session store is currently process memory, so API restarts invalidate sessions.

No application User table exists yet. A future multi-user milestone can map the stable Google/OIDC subject to an application User UUID and then use that UUID as `Knowledge.ownerId`; that model is deliberately not implemented here. Anonymous `PUBLIC` slug access, anonymous bearer-token `UNLISTED` access and authenticated owner access use separate query/DTO paths.

## 9. Future capabilities

Not part of the initial implementation, but architecture should not block:

- revision diffs, labels, pruning/export and collaborative audit history
- indexed links, richer wiki-link syntax and cross-note relationship tooling beyond the first owner-only `[[stable-slug]]`/backlink slice
- advanced graph exploration beyond the current read-only owner graph
- hybrid/large-library semantic retrieval beyond current exact cosine search
- reliable exact-location source navigation and grounding evaluation beyond validated chunk evidence
- automatic tagging and summaries

## 10. Architectural principles

1. Keep MVP operationally simple.
2. Do not introduce infrastructure before a demonstrated need.
3. Backend owns authorization and visibility decisions.
4. Public and private UI may differ in layout but share the same visual language.
5. Web and API remain independently buildable/deployable.
6. Prefer explicit domain rules over framework magic.
