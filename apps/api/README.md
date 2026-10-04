# API Application

Spring Boot REST API for Knowledge Application.

## AI metadata suggestions

AI metadata suggestions: owner-only CSRF-protected `POST /api/knowledge/{id}/ai/metadata-suggestions`, no input body, returns `{summary,tags}` without writing. `AI_METADATA_ENABLED=false` by default; independently configured model `AI_METADATA_MODEL=gemini-3.5-flash-lite`, output budget 600. Reuses existing backend Gemini key and SDK 1.75.0. Current text prefix max 32000, summary max 500, five new tags max 50. Independent persistent quota, no retry/retrieval/background generation or migration. [Full environment defaults, privacy and errors](../../docs/AI_METADATA_SUGGESTIONS.md).

```bash
curl -X POST http://localhost:8080/api/knowledge/1/ai/metadata-suggestions \
  -b 'JSESSIONID=<authenticated-session>' \
  -H 'X-CSRF-TOKEN: <token-from-api-auth-csrf>'
```

## Metadata quality benchmark

Run `./gradlew metadataEval` for 20 deterministic synthetic cases, zero Gemini calls; ignored reports are under `build/reports/metadata-eval`. A separately approved manual run uses `METADATA_EVAL_LIVE=true ./gradlew metadataEvalLive`, the existing secure Gemini configuration/current `AI_METADATA_MODEL` and the production adapter/input preparation. Hard request/token budgets, isolated persistent PostgreSQL metadata quotas, fail-stop/no-retry behavior, versioned identity and visible-versus-full ground truth keep comparisons reusable. Never wire live evaluation into CI. `clean` preserves original metadata live reports and fresh `live-runs/<runId>` outputs; keep external backups for long-term retention. [Corpus, metrics, privacy, human review and baseline](../../docs/METADATA_EVALUATION.md).

Evaluation-only rolling pacing defaults to `METADATA_EVAL_LIVE_MAX_RPM=10` and `METADATA_EVAL_LIVE_PACING_SAFETY_MS=250`: effective RPM is the lower of that limit and configured metadata quota, spacing is `ceil(60000/effectiveRpm)+margin`. Read-only quota/monotonic waits share the existing cumulative wait budget before atomic reservation; typed safe diagnostics distinguish rate limit, timeout, structured-output and local-validation failures without raw provider messages. Production defaults/no-retry behavior are unchanged; shared external project usage is not coordinated.

Explicit resume: `METADATA_EVAL_RESUME_FROM=<path>` checks compatible v1/v2 generation identity and current fixture/output validity, recomputes metrics, and reuses valid cases without calls/pacing/reservations. Budgets count only new cases; complete resumed reports are labeled composite with per-case provenance. Read-only `METADATA_EVAL_RESUME_FROM=build/reports/metadata-eval/live-report.json ./gradlew metadataEvalResumeCheck` needs no credentials or DB. The original report yields 17 reusable/3 outstanding. **No live run/resume was executed in the reliability milestone; new approval is required.**

Synthetic live baseline now **COMPLETE, 20/20 VALID, composite/resumed**: one separately approved resume reused all 17 original valid outputs unchanged and generated only three missing cases, **3 new requests/reservations**, **40440 estimated new input tokens**, 10 RPM pacing, no failures/retries. Model/SDK/prompt/schema/prefix remain frozen. Tag P/R/F1 **.446667/.750000/.558095**; full/visible concept coverage **.891667/.973684**; declared forbidden tags/claims **0/0**. Human review and taxonomy calibration remain pending; real/private notes unevaluated. Completed outputs are ignored in `live-runs/53844290c9090428fd06a7d36b76b2ad1196409a79f9a95f3c0327510f0ee6a9`; original live files/corpus/local configuration are unchanged. Resume authorization is consumed; further live work needs new approval. Prior reliability validation: **473 API tests/97 Web tests**, lint/build and offline evaluation passed with zero external Gemini calls; no full suite rerun for these documentation-only execution results. [Results, long-note visibility and limitations](../../docs/METADATA_EVALUATION.md#completed-synthetic-baseline--one-approved-resume).

## Requirements

- Java 17
- Docker with Docker Compose for local PostgreSQL and private MinIO object storage

The application is pinned to Spring Boot 4.1.1, the stable Spring Boot release selected for this foundation, and Gradle 8.14.3. Both support the project's Java 17 baseline. The repository includes a checksum-verified Gradle Wrapper, so a global Gradle installation is not required.

## Run locally

From the repository root:

```bash
docker compose -f infra/docker/compose.yml up -d
cd apps/api
export GOOGLE_CLIENT_ID='your-google-client-id'
export GOOGLE_CLIENT_SECRET='your-google-client-secret'
export AUTH_ALLOWED_EMAIL='you@example.com'
./gradlew bootRun
```

For Google Cloud's Web application client, register this local authorized redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

Then run Next.js and enter login through `http://localhost:3000`. `apps/api/.env.example` lists every backend setting without real credentials. Spring Boot does not automatically load `.env`; export the values in your shell or inject them through your process/container environment.

Check the application and database health:

```bash
curl http://localhost:8080/actuator/health
```

The local defaults are `knowledge_app` for the database name, username and password. `APP_OWNER_ID` defaults to `00000000-0000-0000-0000-000000000001` and remains the persisted owner key, but never grants access without an authorized OIDC principal. Configure with:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
APP_OWNER_ID
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
AUTH_ALLOWED_EMAIL
WEB_BASE_URL
SESSION_COOKIE_SECURE
KNOWLEDGE_REVISION_INTERVAL
KNOWLEDGE_IMAGE_MAX_SIZE
KNOWLEDGE_IMAGE_MAX_DIMENSION
KNOWLEDGE_IMAGE_MAX_PIXELS
OBJECT_STORAGE_ENDPOINT
OBJECT_STORAGE_REGION
OBJECT_STORAGE_BUCKET
OBJECT_STORAGE_ACCESS_KEY
OBJECT_STORAGE_SECRET_KEY
OBJECT_STORAGE_PATH_STYLE
KNOWLEDGE_ATTACHMENT_CLEANUP_ENABLED
KNOWLEDGE_ATTACHMENT_ORPHAN_GRACE_PERIOD
KNOWLEDGE_ATTACHMENT_CLEANUP_INTERVAL
KNOWLEDGE_ATTACHMENT_CLEANUP_INITIAL_DELAY
KNOWLEDGE_ATTACHMENT_CLEANUP_BATCH_SIZE
```

For the supplied Compose stack, use endpoint `http://localhost:9000`, region `us-east-1`, bucket `knowledge-images`, and the development-only MinIO credentials listed in `.env.example`. For Cloudflare R2, set the account S3 endpoint and region `auto`; path-style can be disabled. Never expose these values through `NEXT_PUBLIC_*` settings.

The session cookie is HttpOnly and `SameSite=Lax`; keep `SESSION_COOKIE_SECURE=false` only for local HTTP and enable it for production HTTPS. Sessions currently live in API process memory and are invalidated by an API restart.

Flyway applies schema migrations during startup. Hibernate uses `ddl-auto=validate` and does not create or update the schema.

## Authenticated API examples

Browser login is the normal local workflow. After authentication, server-side Next.js calls automatically forward the session. For manual API inspection, supply the authenticated `JSESSIONID` as a cookie. Read `/api/auth/csrf`, then use its returned token/header for a mutation:

```bash
curl -b 'JSESSIONID=<authenticated-session>' http://localhost:8080/api/auth/me
curl -b 'JSESSIONID=<authenticated-session>' http://localhost:8080/api/auth/csrf
```

In the examples below, replace `<authenticated-session>` and `<csrf-token>` with values from the same session.

Create a private Markdown note with reusable metadata (visibility defaults to `PRIVATE` when omitted):

```bash
curl -i -X POST http://localhost:8080/api/knowledge \
  -b 'JSESSIONID=<authenticated-session>' \
  -H 'X-CSRF-TOKEN: <csrf-token>' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Spring WebClient timeout","summary":"Timeout notes","content":"","collection":"Backend","tags":["Spring Boot","WebClient"]}'
```

Use the returned `id` and `slug` to fetch, update, list and delete it:

```bash
curl -b 'JSESSIONID=<authenticated-session>' http://localhost:8080/api/knowledge/1
curl -b 'JSESSIONID=<authenticated-session>' http://localhost:8080/api/knowledge/slug/spring-webclient-timeout

curl -X PUT http://localhost:8080/api/knowledge/1 \
  -b 'JSESSIONID=<authenticated-session>' \
  -H 'X-CSRF-TOKEN: <csrf-token>' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Updated WebClient timeout","summary":"Updated notes","content":"# Updated","visibility":"PUBLIC","collection":"Backend","tags":["Spring Boot","Reactor"]}'

curl -X PATCH http://localhost:8080/api/knowledge/1/visibility \
  -b 'JSESSIONID=<authenticated-session>' \
  -H 'X-CSRF-TOKEN: <csrf-token>' \
  -H 'Content-Type: application/json' \
  -d '{"visibility":"UNLISTED"}'

curl -b 'JSESSIONID=<authenticated-session>' http://localhost:8080/api/knowledge
curl -i -X DELETE http://localhost:8080/api/knowledge/1 \
  -b 'JSESSIONID=<authenticated-session>' \
  -H 'X-CSRF-TOKEN: <csrf-token>'
```

`ownerId`, slug and timestamps are server-owned and are not writable request fields. See [`../../docs/API.md`](../../docs/API.md) for the complete contract and error semantics.

On update, `collection: null` removes the collection and `tags: []` removes all tags. Metadata names are trimmed and matched case-insensitively within the current owner. A leading `#` is removed from tag names.

## Collection management examples

Collections can be created before they contain a note. Use the same authenticated session and CSRF token as Knowledge CRUD:

```bash
curl -b 'JSESSIONID=<authenticated-session>' http://localhost:8080/api/collections

curl -X POST http://localhost:8080/api/collections \
  -b 'JSESSIONID=<authenticated-session>' \
  -H 'X-CSRF-TOKEN: <csrf-token>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Backend"}'

curl -b 'JSESSIONID=<authenticated-session>' http://localhost:8080/api/collections/1
curl -b 'JSESSIONID=<authenticated-session>' http://localhost:8080/api/collections/1/knowledge

curl -X PUT http://localhost:8080/api/collections/1 \
  -b 'JSESSIONID=<authenticated-session>' \
  -H 'X-CSRF-TOKEN: <csrf-token>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Platform backend"}'

curl -i -X DELETE http://localhost:8080/api/collections/1 \
  -b 'JSESSIONID=<authenticated-session>' \
  -H 'X-CSRF-TOKEN: <csrf-token>'
```

The collection response contains `id`, `name` and `knowledgeCount`; requests can write only `name`. Names are trimmed, internal whitespace is collapsed and uniqueness is case-insensitive within an owner. Duplicate names return `409 COLLECTION_NAME_CONFLICT`; missing or differently owned IDs return `404 COLLECTION_NOT_FOUND`. Deleting a collection leaves notes and their revision history intact but makes those notes unfiled.

## Wiki link and backlink example

Write a canonical stable-slug reference such as `[[spring-webclient-timeout]]` in another note's Markdown content. To list the owned notes that currently reference Knowledge ID 1:

```bash
curl -b 'JSESSIONID=<authenticated-session>' \
  http://localhost:8080/api/knowledge/1/backlinks
```

The response contains compact source ID/title/slug/updated timestamp rows, not Markdown content or owner IDs. The target and candidates are both owner-scoped; code-fenced, inline-code and escaped references are not counted. The endpoint is read-only and needs no CSRF token. A missing or differently owned target returns `404 KNOWLEDGE_NOT_FOUND`.

## Related articles example

```bash
curl -b 'JSESSIONID=<authenticated-session>' \
  'http://localhost:8080/api/knowledge/1/related?limit=5'
```

This owner-only GET returns compact `id`, `slug`, current `title`, nullable `summary` and ordered `reasons` (`WIKI_LINK`, `BACKLINK`, `SHARED_TAG`, `SAME_COLLECTION`). The limit defaults to 5 and accepts 1–20; invalid values return 400. Both wiki directions rank before shared tags, then Collection-only matches; ties use shared tag count, updated time and ID descending. The service reuses the existing wiki parser and current persisted Tag/Collection IDs; null collections never match. Current owner comes from authenticated context, never the client. Missing/cross-owner targets return 404 and anonymous requests 401.

No revisions contribute unless restored into current content. No migration, dedicated relationship index or AI/embeddings is introduced. PUBLIC/UNLISTED APIs never include owner relationships. See `../../docs/API.md` for the full contract.

## Knowledge Graph example

```bash
curl -b 'JSESSIONID=<authenticated-session>' \
  http://localhost:8080/api/knowledge/graph
```

This read-only owner endpoint returns `{ "nodes": [...], "edges": [...] }`. Every current owned note is a node, including isolated notes and all visibility states in the private workspace. Node fields are `id`, `slug`, `title`, nullable `collectionId`/`collection`, sorted `tags` and `updatedAt`; Markdown, owner UUID and bearer tokens are omitted. Nodes sort by updated time then ID descending; edges sort by source ID then target ID ascending.

An edge `{ "sourceId": 1, "targetId": 2 }` exists only for a current canonical `[[target-slug]]` prose link. The existing wiki extractor excludes code, Mermaid and escaped references; repeated references deduplicate, while independent reciprocal links remain two directed edges. Self, missing, deleted and differently owned targets produce no edge. Collection/Tag metadata never creates an edge; revisions contribute only if restored into current Markdown. No schema change, dedicated relationship index, graph storage or AI/embeddings is introduced. Current-library scans remain a personal-library scaling tradeoff.

Authentication is required (`401` without a session, `403` for an unauthorized identity). Owner partition is resolved server-side; clients cannot choose it. PUBLIC/UNLISTED APIs remain separate article-only read models with no graph endpoint/topology.

## Revision history examples

List compact checkpoints and fetch one full Markdown snapshot:

```bash
curl -b 'JSESSIONID=<authenticated-session>' \
  'http://localhost:8080/api/knowledge/1/revisions?page=0&size=20'

curl -b 'JSESSIONID=<authenticated-session>' \
  http://localhost:8080/api/knowledge/1/revisions/12
```

Restore a snapshot with the same session and CSRF token:

```bash
curl -X POST \
  -b 'JSESSIONID=<authenticated-session>' \
  -H 'X-CSRF-TOKEN: <csrf-token>' \
  http://localhost:8080/api/knowledge/1/revisions/12/restore
```

The initial create snapshot and restore safety snapshot are transactional. Normal authoring updates create pre-edit checkpoints no more often than `KNOWLEDGE_REVISION_INTERVAL` (default `PT5M`); duplicate states, visibility-only updates and link operations do not add noise. Restore changes only title, summary, Markdown, collection and tags, while preserving owner, slug, visibility, publication and bearer-link state. V5 backfills one migration-time checkpoint for pre-existing notes and cascades revisions when their Knowledge parent is deleted.

## Knowledge search example

Search the current owner's Knowledge with PostgreSQL Full Text Search:

```bash
curl --get http://localhost:8080/api/search/knowledge \
  -b 'JSESSIONID=<authenticated-session>' \
  --data-urlencode 'q="spring boot"' \
  --data-urlencode 'limit=20'
```

The endpoint searches title, collection, tags, summary and Markdown content using PostgreSQL's `simple` configuration and safe web-style query parsing. It returns compact results without full content or `ownerId`. `q` is required; `limit` defaults to 20 and is bounded to 1–50.

Flyway V3 creates a generated core search vector and its GIN index. Collection and tags participate through owner-scoped relational joins so metadata remains the single source of truth.

## Authentication boundaries

- Spring Security performs standard Google OAuth2/OIDC login and owns authorization.
- Only a verified Google email equal to `AUTH_ALLOWED_EMAIL` case-insensitively receives owner access.
- `CurrentOwner` validates that principal before returning `APP_OWNER_ID`; Knowledge/Search services keep their established owner-scoped repository calls.
- `/api/knowledge/**`, `/api/collections/**`, `/api/search/**`, `/api/auth/me` and `/api/auth/csrf` require owner authorization.
- `/actuator/health` and the minimum OAuth login/callback infrastructure remain public.
- Anonymous `GET /api/public/knowledge/{slug}` returns only PUBLIC article data through a separate DTO and `slug + visibility` repository lookup.
- Anonymous `GET /api/shared/knowledge/{shareToken}` returns only active UNLISTED article data through a separate `token + visibility` lookup and opaque 404 semantics.
- Owner link GET/regeneration stays under `/api/knowledge/**`; regeneration is CSRF-protected.
- CSRF is enabled for authenticated state-changing requests; it is not globally disabled.
- No User table, JWT platform, Redis session store or multiple-link/expiry/password system is introduced.

## Public Knowledge example

No cookie or CSRF token is needed for the read-only public endpoint:

```bash
curl http://localhost:8080/api/public/knowledge/spring-webclient-timeout
```

Only an already-persisted `PUBLIC` note returns `200`. PRIVATE, UNLISTED and missing slugs all return the same opaque `404`. Owner CRUD and PostgreSQL Search remain authenticated.

## Unlisted secret-link examples

Changing a note to `UNLISTED` through the focused owner PATCH (or full PUT) automatically creates a cryptographically random token when one does not already exist. The PATCH preserves all non-visibility fields. Retrieve its relative path with the authenticated session:

```bash
curl -b 'JSESSIONID=<authenticated-session>' \
  http://localhost:8080/api/knowledge/1/unlisted-link
```

Anonymous readers use only the returned token; no cookie or CSRF token is sent:

```bash
curl http://localhost:8080/api/shared/knowledge/<share-token>
```

Rotate the link with the owner session and CSRF token:

```bash
curl -X POST \
  -b 'JSESSIONID=<authenticated-session>' \
  -H 'X-CSRF-TOKEN: <csrf-token>' \
  http://localhost:8080/api/knowledge/1/unlisted-link/regenerate
```

V4 stores one nullable token/timestamp pair on Knowledge under a global unique constraint. The token comes from 32 `SecureRandom` bytes encoded as unpadded URL-safe Base64. It remains stable until explicit rotation, and is readable anonymously only while visibility is exactly `UNLISTED`. Normal CRUD, Search, PUBLIC and shared article responses never expose it.

Retrievable database storage lets the owner copy the same link again, but database read access/backups can therefore reveal bearer credentials. Protect those systems and treat reverse-proxy access logs containing `/s/...` paths as sensitive; application code must not log tokens explicitly. The Spring request dispatcher is explicitly kept above DEBUG so an ambient debug flag does not print full token-bearing paths.

## Image attachment examples

Upload a validated image to an already-created note:

```bash
curl -X POST http://localhost:8080/api/knowledge/1/attachments/images \
  -b 'JSESSIONID=<authenticated-session>' \
  -H 'X-CSRF-TOKEN: <csrf-token>' \
  -F 'file=@./diagram.png;type=image/png'
```

The returned Markdown source is stable, for example `attachment://550e8400-e29b-41d4-a716-446655440000`. Do not replace it with the private object key. Fetch bytes through the matching application endpoint:

```bash
curl -b 'JSESSIONID=<authenticated-session>' \
  http://localhost:8080/api/knowledge/1/attachments/<attachment-id>/content \
  --output image.png

curl http://localhost:8080/api/public/knowledge/<slug>/attachments/<attachment-id>/content \
  --output public-image.png

curl http://localhost:8080/api/shared/knowledge/<share-token>/attachments/<attachment-id>/content \
  --output shared-image.png
```

Only PNG, JPEG, WebP and GIF are accepted; SVG is deliberately excluded. Upload requires the owner session and CSRF. Public/shared streams remain scoped to the exact parent and active visibility.

Revision-safe cleanup is enabled by default. Fresh unreferenced uploads are protected for `PT24H`; cleanup starts after `PT5M`, then runs every `PT1H` in batches of 100. Retained revisions keep their referenced objects alive, and Knowledge deletion places object keys in a durable retry queue before attachment metadata cascades. Override the five `KNOWLEDGE_ATTACHMENT_*` settings above only through the backend environment. Invalid negative grace/delay, non-positive interval or non-positive batch size fails startup.

PostgreSQL and object storage are not one transaction. Object deletion happens before metadata deletion so failures remain retryable. A rare process crash after S3 upload and before metadata persistence can still create an object that DB-driven cleanup cannot discover; future bucket inventory reconciliation is intentionally deferred. See [`../../docs/ATTACHMENT_LIFECYCLE.md`](../../docs/ATTACHMENT_LIFECYCLE.md) for the complete contract.

## Semantic retrieval foundation

PostgreSQL remains major 17 and now uses the pgvector project image. Flyway V8 enables the extension and creates owner-partitioned chunk-level vector storage with cascading Knowledge deletion. Only current title, summary and Markdown are embedded; revisions and attachments are excluded. Collection/Tags do not participate in semantic input.

Embeddings are **disabled by default** (`EMBEDDING_ENABLED=false`), with no provider client/scheduler calls or health penalty. Production uses official Google Java GenAI SDK `1.75.0`, native synchronous batch embeddings, default `gemini-embedding-2`/768. Set backend-only `GEMINI_API_KEY` securely when enabling; no `NEXT_PUBLIC_*` credentials. `.env.example` lists configurable model/dimensions, batches, deadlines, scheduler/chunk settings and global/background quotas.

Background indexing/backfill is bounded, preserves authoring responsiveness and transactionally replaces complete sets only if current source still matches. SHA-256 detects stale/model/dimension/chunker changes; the new explicit Gemini strategy marker invalidates the old adapter's rows automatically. Exact cosine retrieval excludes stale/incomplete/incompatible chunks during gradual reindex. Local quota denial/429 stops that cycle; later scheduled cycles resume pending work. No raw vector endpoint, AI relationships or ANN index is added.

## Semantic Search example

```bash
curl --get http://localhost:8080/api/search/knowledge/semantic \
  -b 'JSESSIONID=<authenticated-session>' \
  --data-urlencode 'q=how did I solve reverse proxy timeouts?' \
  --data-urlencode 'limit=20'
```

Authenticated owner only, no CSRF for GET; q is non-blank/max 200, limit defaults 20 and accepts 1–50. One provider query embedding, no automatic retry, no DB transaction across the provider, no query persistence or synchronous backfill. SQL selects each note's best current compatible chunk before applying the limit, orders exact cosine then updated time/ID descending, and returns compact metadata plus plain-text `match: {chunkIndex,text}` (max 600 characters). No raw vector/distance/model/credentials are exposed.

Default disabled configuration returns `503 SEMANTIC_SEARCH_DISABLED`; local quota denial, provider 429/timeout/invalid response returns `503 SEMANTIC_SEARCH_UNAVAILABLE`, without upstream details. All owner visibility states may participate, never anonymous PUBLIC/UNLISTED. Keyword FTS, Quick Search, Related Articles and Graph stay unchanged. Query text goes to Gemini and can be private; protect access/URL logs. No hybrid rank, ANN or similarity threshold is added.

See [`../../docs/SEMANTIC_RETRIEVAL.md`](../../docs/SEMANTIC_RETRIEVAL.md) for all defaults, source/hash semantics, provider privacy implications, local extension inspection and production migration privileges. Normal tests use deterministic fake embeddings and loopback HTTP mocks without API keys or paid/network calls.

## Ask My Knowledge

`ASK_ENABLED=false` by default. To enable answers, securely inject `GEMINI_API_KEY`, set `EMBEDDING_ENABLED=true` and `ASK_ENABLED=true`, and allow automatic indexing to build the current compatible corpus. Generation defaults to configurable `gemini-3.5-flash-lite`. Embeddings alone may be enabled; Ask alone returns retrieval unavailable without doing generation. Neither feature requires a key while both are disabled.

After existing Google owner login, obtain the session's CSRF token/header with:

```bash
curl http://localhost:8080/api/auth/csrf -b 'JSESSIONID=<authenticated-session>'
curl http://localhost:8080/api/ask \
  -b 'JSESSIONID=<authenticated-session>' \
  -H 'Content-Type: application/json' \
  -H 'X-CSRF-TOKEN: <token-from-csrf-response>' \
  --data '{"question":"How did I configure WebClient timeouts?"}'
```

Use the `headerName` returned by the CSRF response. Only `question` is writable: trimmed/non-blank/max 2000, JSON body max 16 KiB. Unknown fields (including owner/model/context) are rejected. `ANSWERED` now exposes `{status,answer:{blocks:[{markdown,citationIds}]},citations:[{id,source:{id,title,slug},chunkIndex,evidence}]}`. This replaces the old string answer/separate sources array; deploy the matching Web/API together. `NO_CONTEXT` exposes null answer and empty citations, making zero generation calls. Responses are private/no-store. Disabled generation gives `503 ASK_DISABLED`, retrieval/quota errors `503 ASK_RETRIEVAL_UNAVAILABLE`, generation/quota/invalid-citation errors `503 ASK_UNAVAILABLE`; messages never expose provider internals. Authentication/CSRF remain 401/403.

At most one query embedding and one generation request per Ask; no automatic SDK/application retries, tools, continuation, fallback, synchronous backfill or question/answer/citation persistence. Context defaults: 8 chunks, 2 per note before global ranking, 6 unique notes, 24000 serialized characters. Notes are current owner-only indexed text, not historical revisions. Gemini SDK 1.75.0 uses JSON MIME plus native response JSON schema, returning provider-neutral `AnswerDraft` blocks/sourceRefs. Configured `ASK_MODEL` must support this structured-output contract; unsupported models fail safely, never silently switch.

Each request-local `S1` reference maps to one exact retrieved chunk (and only its included text if context was trimmed). Backend validation rejects the entire output for any unknown/duplicate-invalid/empty citation or malformed structure. Citation IDs `C1` are assigned by first answer occurrence and reused across blocks. Metadata/chunkIndex comes from the retrieved SQL snapshot; backend evidence is an exact Unicode-safe prefix ≤400 characters, never a model quote. Up to 24 blocks, 8192 characters/block, 65536 total and 128 reference occurrences; unique citations ≤retrieved chunks. No new migration or verification provider call. This proves source/evidence linkage, not logical entailment. Reading links have no fake chunk fragments; notes may change after retrieval.

V9 adds PostgreSQL quota counters only. Defaults: embeddings global `80 RPM / 24000 input TPM / 800 RPD`, background `50 / 18000 / 650` within global; independent generation `10 / 200000 / 400`. Estimates use ceil(total input chars / 2.5), RPD midnight `America/Los_Angeles`, counters persist across restarts/replicas. These are operational ceilings, **not actual account quotas**; check AI Studio when changing model/tier and keep all replicas consistent.

Gemini receives private text/questions when enabled. Free Tier/unpaid handling may differ from paid terms and permit product improvement/human review. Review [Google terms](https://ai.google.dev/gemini-api/terms) and [pricing](https://ai.google.dev/gemini-api/docs/pricing) before enabling. See [`../../docs/ASK_MY_KNOWLEDGE.md`](../../docs/ASK_MY_KNOWLEDGE.md) for full configuration, cost/retention boundaries and limitations. Tests explicitly disable production AI and clear its key; zero external Gemini calls.

## AI quota usage retention

Historical quota rows are cleaned automatically; this is local housekeeping, **not a Gemini quota reset/refund**. No new migration, reset endpoint, payload storage or provider call. `AiQuotaLimiter`, SDK `1.75.0` and current-window enforcement are unchanged.

All settings live under `app.ai.quota-cleanup` and are listed in `.env.example`:

```bash
AI_QUOTA_CLEANUP_ENABLED=true
AI_QUOTA_MINUTE_RETENTION=P2D
AI_QUOTA_DAILY_RETENTION=P30D
AI_QUOTA_CLEANUP_INTERVAL=PT6H
AI_QUOTA_CLEANUP_INITIAL_DELAY=PT10M
AI_QUOTA_CLEANUP_BATCH_SIZE=1000
```

One scheduled run deletes at most **batch-size total** minute/day rows, oldest eligible unlocked PK first. Eligibility is strict persisted `window_end < now - retention` **and** `window_end < now`; equality/active/future windows remain. `*-minute`/`*-day` determine retention, not duration (Pacific DST daily windows can be 23/25 hours); unknown suffixes remain. Instant/UTC cutoffs do not use JVM local dates. PostgreSQL counters still survive restarts/replicas; keep clocks/configuration consistent.

A separate non-blocking transaction advisory lock skips another replica's active cycle. Reservation locks are never acquired. Bounded JdbcClient CTE deletion uses `FOR UPDATE SKIP LOCKED`, an independent 30-second transaction timeout, aggregate counts and sanitized logs; locked history/backlogs resume later. Existing scheduling/Clock are reused, without synchronous startup cleanup or a drain loop. `AI_QUOTA_CLEANUP_ENABLED=false` registers no scheduler, makes service calls no-ops and leaves health/reservation unchanged.

Enabled bounds: retention 1ms–36500 days, interval 10s–365 days, initial delay 0–365 days, batch 1–10000; disabled values need only remain parseable. Do not manually delete active counters. Old inspection history disappears when eligible batches reach it, not at an exact deadline. No retention index is added without measured need; normal PostgreSQL autovacuum handles deleted rows. Full contract: [quota operations](../../docs/ASK_MY_KNOWLEDGE.md#ai-quota-usage-retention).

## Offline retrieval evaluation

```bash
./gradlew retrievalEval
```

Requires Java 17 and Docker, not a running API/database, OAuth or Gemini key. One isolated PostgreSQL/pgvector Testcontainer loads 24 synthetic notes/40 queries with explicit note/section ground truth. Test-only controlled vocabulary embeddings call the actual production Semantic/RAG repository queries, not copied ranking SQL. Reports include HitRate/Recall@K, bounded MRR, separate chunk coverage, diversity and per-query/category/language diagnostics; **provider semantic quality NOT measured**.

Reports: `build/reports/retrieval-eval/report.txt` and `report.json` (ignored, never commit). Fixed fixture IDs/timestamps plus repeated-run comparisons verify determinism excluding report timestamp. Owner/stale/model/dimension/incomplete decoys and long-note cap regressions are included. The full evaluator also runs in ordinary tests; the dedicated task always reruns. Production AI is disabled and Gemini/Google keys cleared in all Gradle Test tasks; no external Gemini calls. SDK stays 1.75.0, schema V1–V9 and production ranking/chunking/API/Web are unchanged. Fixture/metrics/schema/limits and observed misses: [Retrieval evaluation](../../docs/RETRIEVAL_EVALUATION.md).

The same task also writes `build/reports/retrieval-eval/chunk-selection-report.txt` / `.json`: **48 configurations** (caps 1/2/3, limits 6/8/10/12, chunk/overlap 2000/150, 3000/200, 4000/200, 4000/400) on an extended 28-note/60-query corpus with four additional six-section guides. It reuses one isolated pgvector environment and immutable strategy-scoped diagnostics, but every limited retrieval still executes production SQL. Conditional chunk coverage, note-hit/chunk-miss IDs, cap/global-cutoff causes, long-only position breakdowns, raw/assembled context chars and reindex proxies are reported, with baseline deltas. `AskContext` is exercised without generation, keeping its 6-source/24000-character budgets. Decision **KEEP BASELINE**: production 4000/200, limit8/cap2 and all quota settings remain unchanged by this milestone. Full matrix runs twice in ordinary tests too; no external Gemini calls, evaluation UI or schema/dependency changes. [Comparison and tradeoffs](../../docs/RETRIEVAL_EVALUATION.md#rag-chunk-selection-matrix).

## Manual live Gemini retrieval evaluation

Only after explicit operator approval, from `apps/api`:

```bash
RETRIEVAL_EVAL_LIVE=true ./gradlew retrievalEvalLive
```

This separate **JavaExec**, not a Test task, also checks the dedicated task marker and existing valid Spring Gemini configuration. An ambient key alone cannot enable it. It reads existing backend `app.gemini` / `app.embedding` / embedding quota settings, including environment overrides; no second credential system, full application startup, normal datasource, generation client or indexing scheduler is used. Optional production flags remain unchanged; only manual adapter validation is enabled in memory.

Only the existing **28 synthetic notes / 60 queries**, baseline **4000/200** chunks, and an ephemeral PostgreSQL/pgvector Testcontainer are used. Each unique document/query input is embedded once through the production Gemini native-batch adapter; identical vectors serve Semantic Search and **12 retrieval-only** limit/cap variants. No alternate live chunking/model/dimension comparisons, private data or generation calls.

Hard bounds: `RETRIEVAL_EVAL_LIVE_MAX_REQUESTS=20`, `RETRIEVAL_EVAL_LIVE_MAX_ESTIMATED_INPUT_TOKENS=60000`; overrides may reduce, not raise, these ceilings. Estimates include the adapter's symmetric task prefix. Global/background PostgreSQL RPM/estimated TPM/RPD reservations remain enabled in the disposable DB. Local minute exhaustion may wait up to `RETRIEVAL_EVAL_LIVE_MAX_WAIT_SECONDS=180` total (allowed 0–600); impossible batches/daily exhaustion fail immediately. **Provider 429, timeout, 5xx or bad vectors stop without retries**. Usage separates document/query and attempted/successful requests; incomplete reports contain no quality metrics.

Reports: ignored `build/reports/retrieval-eval/live-report.txt` / `.json`; offline reports are not overwritten. Normal `test`, `retrievalEval`, `clean build` and CI never invoke the live entry point; automated harness tests use capturing fakes. Real Gemini on synthetic data does not prove production retrieval or answer quality. No browser-visible behavior changes. See [live contract/results](../../docs/RETRIEVAL_EVALUATION.md#manual-live-gemini-retrieval-evaluation).

**Local secret discipline:** an intentional uncommitted `application.yml` key must never be staged, printed, reverted or copied into tracked files/reports. Use explicit Git paths, never `git add .` / `git add -A`. No key appears in the invocation above. Build resources/reports remain ignored; don't distribute local build artifacts containing local configuration.

One approved synthetic run completed: **`gemini-embedding-2` / 768**, 55 document + 60 query inputs, **4 + 4 successful requests**, **33455 estimated tokens**, task **2m3s**, with bounded local-minute waits and no retries/generation. Baseline Semantic Recall@5=1.0, RAG chunk/conditional recall=.9717, note-hit/chunk-miss=2 (offline 6). Production defaults remain unchanged. Next: separately reviewed long-note supporting-chunk/context-cost evaluation; [full observations](../../docs/RETRIEVAL_EVALUATION.md#approved-live-run--observed-results).

## Validate

```bash
./gradlew test
./gradlew clean build
```

Before implementing backend features, read:

- `../../docs/ARCHITECTURE.md`
- `../../docs/API.md`
- `../../docs/PRODUCT_SCOPE.md`
