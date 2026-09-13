# Architecture

## 1. Architecture goal

Build a personal knowledge application that is simple to operate initially but has a clear path toward public sharing, revision history, backlinks and AI-assisted retrieval.

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

The workspace shell receives the real owner-scoped Knowledge list for recently updated Quick Search content and derives Collection labels from that data. Non-empty Full Search and Quick Search queries share PostgreSQL ranking through one Spring endpoint. Interactive browser queries use a focused same-origin Next.js Route Handler, which calls the existing server-only API client; no generic backend proxy or public API base URL is introduced.

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

Before meaningful authoring updates, the service captures the current persisted state only when the latest snapshot is at least `KNOWLEDGE_REVISION_INTERVAL` old (default `PT5M`) and not identical. This treats history as periodic recovery checkpoints rather than an autosave log. Focused visibility and link operations bypass revision creation. Restore always appends `BEFORE_RESTORE`, then applies only authoring fields; stable URL, ownership and all sharing/security state are preserved.

There is no optimistic-lock or multi-tab merge protocol yet. Concurrent editors remain last-write-wins; append-only checkpoints provide a recovery path but are not an audit-grade record of every keystroke or actor.

The opaque token is stored retrievably rather than hashed because the owner-management API must return the same existing link. This means database read access can reveal bearer credentials. Database access, backups and operational tooling must therefore be restricted; reverse-proxy/access logs may also contain the token in request paths and should be protected or redacted operationally. The application does not log token values explicitly, and its Spring request dispatcher is kept above DEBUG so an ambient debug flag does not print full token-bearing paths.

Collection and Tag are persisted as reusable owner-scoped entities. `knowledge.collection_id` models the optional many-to-one collection association, while `knowledge_tag` models the many-to-many tag association. Deleting Knowledge cascades only to its join rows; reusable Tag and Collection rows are retained.

Both metadata tables store a stable display name and a PostgreSQL-generated `normalized_name`. Uniqueness on `(owner_id, normalized_name)` prevents case-only or surrounding-whitespace duplicates for one owner without requiring a database extension, while allowing the same name for different owners. The application also collapses internal whitespace and removes leading `#` characters from tag display names before persistence.

The current CRUD API preserves the server-side `APP_OWNER_ID` as the persisted owner partition. It is not proof of identity and cannot grant access by itself. `CurrentOwner` first requires an authenticated OIDC principal whose Google email is verified and equals the backend-only `AUTH_ALLOWED_EMAIL` value case-insensitively, then returns the configured UUID. API payloads cannot choose or modify `owner_id`, and repository reads remain owner-scoped.

The V1 schema intentionally keeps slugs globally unique. This now supports unambiguous public `/k/{slug}` lookup and means collision suffixes are selected globally rather than per owner. Workspace and public URLs reuse the same stable slug; visibility changes never regenerate it. Owner workspace reads by slug still include `owner_id`, while the separate anonymous lookup includes `visibility = PUBLIC` in its repository query.

Knowledge reads use an entity graph for Collection and Tags so the list endpoint does not issue one metadata query per Knowledge row. API responses sort tag display names case-insensitively for deterministic output; tag membership itself is a set rather than an ordered domain relationship.

The Reading Page renders the persisted Markdown with a safe Markdown-to-React pipeline and GitHub-flavored Markdown support. Raw HTML is not enabled. H2-H4 table-of-contents anchors and approximate read time are derived at render time and are not persisted.

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

Future:

- PostgreSQL + pgvector for semantic retrieval
- AI-assisted question answering with citations back to stored knowledge

## 8. Authentication

The application remains personal/single-owner oriented. Spring Security OAuth2 Client uses Google's standard OIDC provider, grants the owner authority only to the verified allowlisted email, and keeps authentication in the servlet session. The session store is currently process memory, so API restarts invalidate sessions.

No application User table exists yet. A future multi-user milestone can map the stable Google/OIDC subject to an application User UUID and then use that UUID as `Knowledge.ownerId`; that model is deliberately not implemented here. Anonymous `PUBLIC` slug access, anonymous bearer-token `UNLISTED` access and authenticated owner access use separate query/DTO paths.

## 9. Future capabilities

Not part of the initial implementation, but architecture should not block:

- revision diffs, labels, pruning/export and collaborative audit history
- backlinks
- `[[Wiki Links]]`
- knowledge graph
- semantic search
- Ask My Knowledge
- automatic tagging and summaries

## 10. Architectural principles

1. Keep MVP operationally simple.
2. Do not introduce infrastructure before a demonstrated need.
3. Backend owns authorization and visibility decisions.
4. Public and private UI may differ in layout but share the same visual language.
5. Web and API remain independently buildable/deployable.
6. Prefer explicit domain rules over framework magic.
