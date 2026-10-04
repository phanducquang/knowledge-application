# API

Spring Boot owns domain rules, persistence and ownership checks. Next.js must not become the authoritative security layer.

## Operational endpoint

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/actuator/health` | Minimal application and database health |

Only the Actuator health endpoint is exposed.

## Authentication

Spring Security owns Google OAuth2/OIDC login and the authenticated server-side session.

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/oauth2/authorization/google` | Begin Google login; public authentication infrastructure |
| `GET` | `/login/oauth2/code/google` | Standard Spring Security OAuth callback |
| `GET` | `/api/auth/me` | Authorized owner's presentation-safe email, name and picture |
| `GET` | `/api/auth/csrf` | Current authenticated session's CSRF token and header name |
| `POST` | `/api/auth/logout` | CSRF-protected logout; invalidates session and returns `204` |

`/api/auth/me` never returns the owner UUID, OAuth tokens, client secret or session identifier. Unauthenticated private API requests return `401`; an authenticated identity that is not the verified allowlisted owner is rejected with `403`/login failure.

## Knowledge CRUD

All routes below require the authorized owner session and are scoped to the configured persisted owner.

| Method | Route | Result |
| --- | --- | --- |
| `POST` | `/api/knowledge` | Create Knowledge; returns `201 Created` |
| `GET` | `/api/knowledge` | List the current owner's notes by `updatedAt DESC`, then `id DESC` |
| `GET` | `/api/knowledge/{id}` | Fetch the current owner's note by numeric ID |
| `GET` | `/api/knowledge/slug/{slug}` | Fetch the current owner's note by stable slug |
| `PUT` | `/api/knowledge/{id}` | Replace the currently editable fields |
| `PATCH` | `/api/knowledge/{id}/visibility` | Change only visibility; owner-scoped and CSRF-protected |
| `DELETE` | `/api/knowledge/{id}` | Delete the current owner's note; returns `204 No Content` |
| `GET` | `/api/knowledge/{id}/unlisted-link` | Return the current stored bearer token/path; `404` if none has ever been created |
| `POST` | `/api/knowledge/{id}/unlisted-link/regenerate` | Replace the token and return its new relative path; CSRF-protected |

The explicit `/slug/{slug}` route avoids ambiguity between numeric identifiers and slugs. Ordinary CRUD remains distinct from the anonymous public `/k/{slug}` and bearer-token `/s/{shareToken}` access models.

### Create request

```json
{
  "title": "Spring WebClient timeout",
  "summary": "Timeout configuration notes",
  "content": "# WebClient\n\nMarkdown content",
  "visibility": "PRIVATE",
  "collection": "Backend",
  "tags": ["Spring Boot", "WebClient"]
}
```

- `title` is required, trimmed, non-blank and limited to 255 characters.
- `summary` is optional and limited to 2000 characters.
- `content` is required but may be an empty string so a new blank Markdown document can be saved.
- `visibility` may be `PRIVATE`, `UNLISTED` or `PUBLIC`; create defaults to `PRIVATE` when omitted.
- `collection` is optional. A non-null value is trimmed and limited to 100 characters.
- `tags` is optional on create and defaults to an empty set. At most 20 tags may be supplied, each limited to 50 characters.

### Update request

`PUT` uses the same editable fields, but `title`, `content`, `visibility` and `tags` are required. `summary` and `collection` remain nullable.

Metadata uses replacement semantics:

- `collection: null` removes the Knowledge-to-Collection association.
- `tags: []` removes every Knowledge-to-Tag association.
- a non-empty `tags` array becomes the exact resulting tag set; it is not appended to the old set.

The client cannot write `id`, `ownerId`, `slug`, `createdAt`, `updatedAt` or `publishedAt`. Unknown JSON fields are rejected as malformed input.

The focused visibility endpoint accepts exactly the sharing decision:

```json
{
  "visibility": "UNLISTED"
}
```

`visibility` is required and may be `PRIVATE`, `UNLISTED` or `PUBLIC`. The service loads the Knowledge item through `id + current owner`, applies the same publication/token transition rules used by full update, and preserves title, summary, content, slug, collection, tags and owner. It returns the normal Knowledge response, which still omits the bearer token.

### Response

```json
{
  "id": 1,
  "title": "Spring WebClient timeout",
  "slug": "spring-webclient-timeout",
  "summary": "Timeout configuration notes",
  "content": "# WebClient\n\nMarkdown content",
  "visibility": "PRIVATE",
  "collection": "Backend",
  "tags": ["Spring Boot", "WebClient"],
  "createdAt": "2026-09-09T15:30:00Z",
  "updatedAt": "2026-09-09T15:30:00Z",
  "publishedAt": null
}
```

`ownerId` is deliberately absent from responses because the current frontend has no need for it. Normal Knowledge responses also omit `shareToken` and `shareTokenCreatedAt`; bearer credentials are returned only by the explicit owner management endpoints:

```json
{
  "token": "opaque-url-safe-token",
  "path": "/s/opaque-url-safe-token",
  "createdAt": "2026-09-11T10:00:00Z"
}
```

The path is relative so Spring Boot does not guess the frontend origin. Owner GET is read-only and needs no CSRF. Regeneration is a POST, retains owner authorization and CSRF checks, changes no slug, and immediately invalidates the previous token. It is allowed while sharing is inactive; the result stays dormant until visibility becomes UNLISTED.

When no collection exists, `collection` is `null`. When no tags exist, `tags` is an empty array. Tag names are returned in deterministic case-insensitive alphabetical order.

## Wiki links and backlinks

Current Markdown supports owner-workspace wiki references in the form `[[stable-slug]]`, for example `[[spring-webclient-timeout]]`. The target is the immutable Knowledge slug, not the editable title. Repeated references to the same target in one source note count as one backlink. Only literal prose references are considered: fenced/indented code, inline backticks, escaped opening brackets and non-canonical slugs are ignored. Missing targets remain literal text in the owner reader; no note is created implicitly.

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/api/knowledge/{id}/backlinks` | Owner-scoped sources whose current Markdown links to the owned note |

The response is an array of `{ "id": 4, "title": "Source note", "slug": "source-note", "updatedAt": "2026-10-01T10:00:00Z" }` rows ordered by `updatedAt DESC`, then `id DESC`. It contains no source Markdown, owner ID or share token. Missing or differently owned targets return `404 KNOWLEDGE_NOT_FOUND`; unauthenticated requests return `401`. Source edits, restore and deletion are reflected on the next read because backlinks are derived from current Markdown rather than a separate stored edge table.

The authenticated Reading Page resolves `[[slug]]` to the current title and `/knowledge/{slug}` only when that slug exists in the current owner's note list. The anonymous PUBLIC and UNLISTED readers do not resolve wiki references or receive backlinks, so the relationship feature never grants access to a private target. This first personal-workspace slice uses an owner-scoped PostgreSQL substring candidate query plus Markdown validation at read time; a dedicated link index/backfill is deferred until library size warrants it. Links are slug references, not permanent target IDs: deleting a target and later reusing its slug can rebind old references. Title-based resolution and aliases are not implemented; the current owner graph reuses these same canonical references.

## Related articles (owner workspace only)

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/api/knowledge/{id}/related?limit=5` | Compact related notes in deterministic relevance order |

`limit` defaults to 5 and must be an integer from 1 to 20; invalid values return `400` using the existing validation/malformed-input error model. Missing or differently owned targets return `404 KNOWLEDGE_NOT_FOUND`; anonymous calls return `401`. Ownership comes exclusively from authenticated backend context, never from request payload/query parameters.

```json
[
  {
    "id": 12,
    "slug": "spring-security",
    "title": "Spring Security",
    "summary": null,
    "reasons": ["WIKI_LINK", "BACKLINK", "SHARED_TAG", "SAME_COLLECTION"]
  }
]
```

Reasons have the fixed order shown above: outgoing canonical wiki reference, incoming canonical wiki reference, at least one shared persisted Tag ID, and the same actual non-null Collection ID. Each candidate appears once with all matching reasons. Both wiki directions reuse the existing prose/code/escape parser. Candidates include all visibility states inside the authenticated owner's workspace, but exclude self, deleted notes and every other owner. Two unfiled notes are not related just because both collections are null.

Ranking uses three tiers: any explicit wiki relationship first, shared tags second, Collection-only third. Within a tier, more shared tags comes first, then `updatedAt DESC`, then `id DESC`. Multiple explicit directions do not introduce a hidden score. The limit applies after ranking and deduplication. Returned title/summary are current values and slug remains stable after renaming.

Relations are derived on each read from current Knowledge Markdown and metadata using the existing owner-scoped metadata entity graph, not retained revision snapshots. Editing, deleting, metadata replacement and Collection deletion are reflected on the next read; restoring a revision may reintroduce a relation only when its authoring data becomes current. No schema migration, persisted edge table, dedicated relationship index, AI or embeddings are involved. Scanning the personal library is deliberately simple and remains a scaling limitation.

Only authenticated Reading renders the flat Related notes section, after Backlinks, and omits it when empty. The no-store server-only API transport preserves backend ordering. Anonymous PUBLIC/UNLISTED endpoints and `/k/{slug}` / `/s/{shareToken}` pages expose no related list, reason metadata or private workspace navigation.

## Knowledge Graph (owner workspace only)

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/api/knowledge/graph` | Compact deterministic current-library nodes and directed wiki edges |

```json
{
  "nodes": [
    {
      "id": 12,
      "slug": "spring-security",
      "title": "Spring Security",
      "collectionId": 3,
      "collection": "Backend",
      "tags": ["Security", "Spring"],
      "updatedAt": "2026-10-03T00:00:00Z"
    },
    {
      "id": 19,
      "slug": "oauth2",
      "title": "OAuth2",
      "collectionId": null,
      "collection": null,
      "tags": [],
      "updatedAt": "2026-10-02T00:00:00Z"
    }
  ],
  "edges": [{ "sourceId": 12, "targetId": 19 }]
}
```

`collectionId`/`collection` are null for unfiled notes. Every current owned note is included, including isolated notes and all visibility states in the authenticated workspace. Nodes sort by `updatedAt DESC`, then `id DESC`; tags sort case-insensitively with an exact-name tie-break; edges sort by `sourceId ASC`, then `targetId ASC`. An empty library returns `{ "nodes": [], "edges": [] }`.

Only current canonical `[[stable-slug]]` prose references produce edges, through the shared `WikiLinkExtractor`. A source/target pair occurs once, reciprocal references produce two independent directed edges, and backlinks do not synthesize a reverse edge. Self-links, missing/deleted targets and cross-owner targets are excluded. Code fences (including Mermaid), inline/indented code and escaped references follow the existing wiki semantics. Collection/Tags are node metadata only, not graph nodes or edge types; shared metadata alone never creates an edge.

No owner UUID, Markdown/revision content, bearer token, object key or authentication data is returned. Authenticated backend context selects the owner partition; no client owner ID is used. Anonymous calls receive `401`; unauthorized identities receive `403`. There are no PUBLIC/UNLISTED graph endpoints: existing anonymous routes still return only the requested article and never graph topology/counts. No graph write API, schema migration, relationship index or AI/embeddings is added.

Title/visibility/Collection/Tag changes do not change slug-based wiki edges. Source edits and target deletion affect the next graph read; retained revisions alone do not contribute, while a restore can reintroduce edges when its Markdown becomes current. Related Articles ranking remains unchanged and independent of graph edge creation.

The protected `/graph` page uses server-only no-store reads and a focused client canvas. Collection-ID filtering retains only nodes in that actual Collection and edges whose two endpoints remain visible. Focus by control or `/graph?focus=stable-slug` highlights the note, direct incoming/outgoing neighbors and incident edges; unknown/non-visible focus is ignored. Nodes navigate to `/knowledge/{slug}`; pan, pinch/button zoom and fit-view state remain presentation-only. The library is scanned dynamically without an edge index, an intentional scaling tradeoff for a personal workspace.

## Collection management

Collection management is an authenticated owner-only API. Collection IDs are stable across renames; note authoring still accepts a collection display name rather than an ID.

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/api/collections` | List all of the current owner's collections, including empty ones, with note counts |
| `POST` | `/api/collections` | Create a collection; returns `201 Created` |
| `GET` | `/api/collections/{id}` | Fetch one owned collection with its note count |
| `PUT` | `/api/collections/{id}` | Rename an owned collection |
| `DELETE` | `/api/collections/{id}` | Delete an owned collection; returns `204 No Content` |
| `GET` | `/api/collections/{id}/knowledge` | List that collection's owned Knowledge items |

`POST` and `PUT` accept only `{"name":"Backend"}`. The name must be non-blank and at most 100 characters. Surrounding whitespace is trimmed and repeated internal whitespace collapses; names are unique case-insensitively within an owner. A normalized-name collision returns `409 COLLECTION_NAME_CONFLICT`. The response is `{ "id": 3, "name": "Backend", "knowledgeCount": 2 }`; `id`, `knowledgeCount` and `ownerId` are not writable, and `ownerId` is never returned.

The collection list includes zero-count rows and sorts by case-insensitive name, then ID. The Knowledge list uses the normal Knowledge response and sorts by `updatedAt DESC`, then `id DESC`. A missing or differently owned collection returns `404 COLLECTION_NOT_FOUND` on ID-based routes. State-changing methods require the same session CSRF token as Knowledge CRUD.

Renaming updates the reusable Collection name without changing its ID, associations or historical revision snapshots. Deleting a Collection sets the associated notes' `collection` to `null` via the existing V2 foreign-key rule; it does not delete notes, tags, attachments or history. Historical snapshots retain their denormalized collection names. Collection operations do not edit note content, stable slugs, visibility or note `updatedAt` values. Because note editing and revision restore still resolve Collection by display name, a later save of a stale draft or restore of an old snapshot may recreate a deleted name; concurrent editing remains last-write-wins.

## Knowledge revision history

Revision routes are authenticated owner operations under the same Knowledge boundary:

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/api/knowledge/{id}/revisions?page=0&size=20` | Compact snapshots, newest first; `size` is limited to 1–50 |
| `GET` | `/api/knowledge/{id}/revisions/{revisionId}` | One full authoring snapshot, including Markdown |
| `POST` | `/api/knowledge/{id}/revisions/{revisionId}/restore` | Restore authoring fields and return the current Knowledge response |

The list response is deliberately compact and never includes full Markdown:

```json
{
  "items": [
    {
      "id": 12,
      "createdAt": "2026-09-12T08:00:00Z",
      "reason": "CHECKPOINT",
      "title": "Spring WebClient timeout",
      "summaryExcerpt": "Timeout configuration notes"
    }
  ],
  "page": 0,
  "size": 20,
  "hasMore": false
}
```

Detail adds `summary`, `content`, nullable `collection`, and the normalized display-name `tags`. Snapshot reasons are `CREATE`, `CHECKPOINT`, and `BEFORE_RESTORE`.

Revision snapshots contain authoring state only: title, summary, Markdown content, collection display name, and tag display names. They never contain owner ID, slug, visibility, publication timestamps, share token, authentication, or session data. Metadata names are denormalized deliberately so history remains truthful if reusable Collection/Tag rows later change.

Create records the initial snapshot in the same transaction. A meaningful full update checks whether a checkpoint is due before mutating the note; the minimum interval defaults to `PT5M` and is configured with `KNOWLEDGE_REVISION_INTERVAL`. Frequent autosaves inside that window do not create a row, exact duplicate snapshots are skipped, and visibility-only or share-token operations do not create authoring revisions.

Restore always appends a `BEFORE_RESTORE` snapshot of the current authoring state before applying the selected revision. It restores only title, summary, content, collection and tags. Numeric ID, owner, stable slug, visibility, `publishedAt`, share token and its timestamp, and created timestamp remain unchanged; `updatedAt` advances as a normal edit. Consequently existing `/k/{slug}` and `/s/{shareToken}` access continues according to the current sharing state, but serves the restored authoring content.

Every list/detail/restore operation first resolves `{id}` through `id + current owner`; a revision must additionally belong to that Knowledge item. Missing/cross-owner Knowledge returns `404 KNOWLEDGE_NOT_FOUND`, while a revision missing from an owned note returns `404 KNOWLEDGE_REVISION_NOT_FOUND`. Restore is state-changing and requires CSRF. Deleting Knowledge cascades its revision rows.

## Knowledge image attachments

Image upload and delivery are deliberately separate from generic files. The object-storage bucket stays private; clients receive neither object keys, credentials, direct MinIO/R2 URLs nor presigned URLs.

| Method | Route | Access | Result |
| --- | --- | --- | --- |
| `POST` | `/api/knowledge/{id}/attachments/images` | Authenticated owner + CSRF | Upload one image and return attachment metadata |
| `GET` | `/api/knowledge/{id}/attachments/{attachmentId}/content` | Authenticated owner | Stream an image belonging to that owner's exact Knowledge item |
| `GET` | `/api/public/knowledge/{slug}/attachments/{attachmentId}/content` | Anonymous | Stream only when the exact parent is `PUBLIC` |
| `GET` | `/api/shared/knowledge/{shareToken}/attachments/{attachmentId}/content` | Anonymous bearer access | Stream only when the token is current and the exact parent is `UNLISTED` |

Upload uses multipart field `file`. It accepts PNG, JPEG, WebP and GIF up to `KNOWLEDGE_IMAGE_MAX_SIZE` (default 10 MB); SVG is rejected. Validation detects signatures and dimensions instead of trusting the browser filename/media type, requires declared and detected types to agree, and applies `KNOWLEDGE_IMAGE_MAX_DIMENSION` plus `KNOWLEDGE_IMAGE_MAX_PIXELS` safeguards.

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "originalFilename": "diagram.png",
  "contentType": "image/png",
  "sizeBytes": 24576,
  "createdAt": "2026-09-13T02:00:00Z",
  "markdownSource": "attachment://550e8400-e29b-41d4-a716-446655440000"
}
```

`attachment://<UUID>` is the canonical stable Markdown reference. It contains no secret and remains unchanged across owner, PUBLIC, UNLISTED and revision-history views. Each rendering context resolves it to its matching same-origin Next.js content route; Spring performs the authoritative parent/access query, preventing cross-note attachment-ID substitution.

V6 stores only metadata in `knowledge_attachment`; bytes live in S3-compatible storage. The server generates `knowledge/{ownerId}/{knowledgeId}/{attachmentId}` and never accepts or returns that key. It writes the object before flushing metadata, performs best-effort cleanup if metadata persistence/transaction commit fails, and does not commit metadata after an object write failure.

Deleting Knowledge queues its known attachment object keys before attachment metadata cascades. The existing revision-aware cleanup worker removes queued objects with retries after the note and its revisions are gone. Fresh unreferenced uploads receive a grace period, and references in retained revisions protect objects while the note exists. A rare object written before a crash but never recorded in PostgreSQL still requires future bucket-inventory reconciliation. No attachment deletion API exists yet.

Attachment errors are compact: `UNSUPPORTED_IMAGE_TYPE` (`415`), `IMAGE_TOO_LARGE` (`413`), `MALFORMED_IMAGE` (`400`), scoped not-found codes (`404`), `OBJECT_STORAGE_UNAVAILABLE` (`503`) and `ATTACHMENT_PERSISTENCE_FAILED` (`500`). They contain no stack trace or storage detail.

## Public Knowledge read

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/api/public/knowledge/{slug}` | Anonymous read of a Knowledge item only when its visibility is `PUBLIC` |

This is a separate read-only access model from authenticated `GET /api/knowledge/slug/{slug}`. The repository lookup includes both the globally unique slug and `visibility = PUBLIC`; it does not load a private row and decide afterward.

The public response contains only article presentation data:

```json
{
  "title": "Spring WebClient timeout",
  "slug": "spring-webclient-timeout",
  "summary": "Timeout configuration notes",
  "content": "## Configuration\n\nMarkdown content",
  "collection": "Backend",
  "tags": ["Spring Boot", "WebClient"],
  "publishedAt": "2026-09-11T08:00:00Z",
  "updatedAt": "2026-09-11T09:00:00Z"
}
```

It omits owner ID, internal numeric ID, visibility, created timestamp, search rank and all authentication/session data. Collection and tags are loaded with the same entity-graph strategy used by owner reads.

PRIVATE, UNLISTED and nonexistent slugs are indistinguishable to anonymous callers and all return `404` with `PUBLIC_KNOWLEDGE_NOT_FOUND`. There is no anonymous POST, update, delete, listing or Search endpoint.

## Unlisted Knowledge read

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/api/shared/knowledge/{shareToken}` | Anonymous read only when the token matches and Knowledge is currently `UNLISTED` |

UNLISTED is bearer-secret access, not public-slug or authenticated workspace access. The backend creates tokens from 32 bytes (256 bits) produced by `SecureRandom`, then uses URL-safe Base64 without padding (43 characters). A global database unique constraint protects against collisions; generation retries values already present.

The first transition to UNLISTED creates a token if absent. Ordinary edits preserve it. PRIVATE/PUBLIC deactivate but retain it, and a later UNLISTED transition reuses it. Owner regeneration replaces the value and creation timestamp, so the old token immediately fails.

The shared response has the same article-presentation fields as the public response but uses its own access-model DTO. It never returns the token itself, owner ID, internal ID or visibility. `publishedAt` may be null for a note that has never been PUBLIC; UNLISTED does not create a public publication timestamp.

The repository query requires both the supplied token and `visibility = UNLISTED`. Invalid, unknown, PRIVATE, PUBLIC and rotated-old tokens all return the same `404 UNLISTED_KNOWLEDGE_NOT_FOUND`. Successful and not-found responses use private `no-store` caching and `X-Robots-Tag` defenses.

V4 stores `share_token` and `share_token_created_at` as a nullable pair on `knowledge`, supporting exactly one current/dormant link without a generic share table. The token is retrievable so the owner can copy the same link later. Consequently, database readers/backups can reveal bearer links; access must be restricted. Reverse-proxy access logs can also capture token-bearing paths and need operational protection/redaction. Application code does not explicitly log token values, and the Spring request dispatcher is kept above DEBUG to avoid printing full token paths when an ambient debug flag is enabled.

## Knowledge search

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/api/search/knowledge?q={query}&limit={limit}` | Compact owner-scoped results ranked by PostgreSQL |

`q` is required, trimmed, non-blank and limited to 200 characters. A blank query returns `400`; search is deliberately not used as a recent-notes endpoint. `limit` defaults to 20, must be between 1 and 50, and Quick Search requests 6.

The compact response contains `id`, `title`, `slug`, nullable `summary`, `visibility`, nullable `collection`, `tags` and `updatedAt`. It omits `ownerId`, full Markdown content and internal relevance scores.

PostgreSQL uses `websearch_to_tsquery('simple', q)` with parameterized SQL. The `simple` configuration avoids English stemming and stop-word behavior that would be surprising for Vietnamese, identifiers, acronyms and mixed technical terminology. Searchable fields and weights are:

- A: title
- B: collection and tag names
- C: summary
- D: Markdown source content

Results sort by combined relevance descending, then `updatedAt DESC` and `id DESC`. A multi-term query may match across a core field and metadata. The generated stored `knowledge.search_vector` covers title, summary and content and has a GIN index. Collection and tag text remains normalized relational data and is converted to an owner-scoped metadata vector during the query; this avoids duplicated metadata and trigger synchronization at the cost of metadata-only and cross-vector matching not using the core GIN index.

## Semantic Search (authenticated owner only)

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/api/search/knowledge/semantic?q={query}&limit={limit}` | One best current matching chunk per owned Knowledge note |

`q` is required, non-blank, trimmed and at most 200 characters; `limit` defaults to 20, accepts integers 1–50. Only these two parameters are consumed: owner/model/dimensions/provider/vector/credentials remain backend-controlled. Authentication is required (401 without owner session, 403 for unauthorized identity); GET needs no CSRF token. Responses are private `no-store`.

```json
[
  {
    "id": 12,
    "title": "WebClient timeouts",
    "slug": "webclient-timeouts",
    "summary": null,
    "visibility": "PRIVATE",
    "collection": "Spring",
    "tags": ["Java", "WebFlux"],
    "updatedAt": "2026-10-03T00:00:00Z",
    "match": { "chunkIndex": 2, "text": "responseTimeout(Duration.ofSeconds(5)) …" }
  }
]
```

The query is sent once to the configured `EmbeddingClient`; exactly one finite non-zero vector of configured dimensions is required. Calls are outside DB transactions, use existing provider deadlines and have no automatic retry/fallback. Query vectors are temporary, never persisted. SQL reuses the indexer's complete/current SHA-256/model/dimension/chunker predicate, filters owner before ranking, and selects the best chunk per note before the note limit. Ranking is exact cosine distance ascending, then Knowledge updated time and ID descending; best-chunk ties use chunk index and row ID. Metadata is enriched in the same bounded statement/snapshot, not N+1 queries.

`match.text` is the best current source chunk excerpt, capped at 600 UTF-16 units including ellipsis without splitting surrogate pairs. It is plain text, never trusted HTML/Markdown. Responses omit owner, vectors, distance, provider/model/dimensions/hash and secrets. All visibility states may appear in the private workspace. Stale, incompatible, incomplete, unindexed and deleted notes do not participate; background indexing alone makes pending notes searchable. With no similarity cutoff, an empty array means no current compatible indexed notes/library, not proof that no semantically related note exists.

Disabled/missing provider returns `503 SEMANTIC_SEARCH_DISABLED`; timeout, provider error or invalid vectors returns `503 SEMANTIC_SEARCH_UNAVAILABLE`. Both use `{code,message,fieldErrors}` with sanitized messages, no upstream body/cause/secret. Validation/malformed parameter errors remain 400. There is no anonymous/public/shared semantic endpoint, hybrid score or ANN index.

`/search?q=redis` and absent/invalid mode remain Keyword; `/search?mode=semantic&q=cache+invalidation` performs one initial semantic request. Typing/switching to Semantic never invokes the provider; Enter/Search explicitly submits and updates the URL. Browser requests use only `/api/knowledge-semantic-search` BFF with server-side cookie forwarding/no-store. Submitted query text goes to the configured provider and may be sensitive; restrict operational URL/access logs. Application code does not explicitly log it. Keyword SQL/ranking/debounce and Quick Search stay unchanged.

## Semantic retrieval foundation (internal storage)

V8 enables pgvector on PostgreSQL 17 and stores owner-internal current-Knowledge chunks with model/dimension/version and SHA-256 freshness metadata. Background indexing is disabled by default, never calls the provider during CRUD/restore, and replaces chunks only after full generation and a current-source recheck. Deletion cascades chunk rows; revisions and attachment bytes are not embedded. Collection/Tags are excluded from semantic input, so metadata-only changes do not require re-embedding.

The centralized exact cosine repository query filters owner, compatible model/dimensions/strategy, complete sets and current source hash before returning bounded deterministic results. Raw vectors, distances, provider inputs and credentials are not part of any API DTO. Production uses Gemini native embeddings, default `gemini-embedding-2`/768, with a new strategy marker that automatically invalidates/reindexes old vectors. V9 adds persistent minute/day quota counters, not a vector schema change. Local quota denial/429 maps to `SEMANTIC_SEARCH_UNAVAILABLE`. FTS/Quick Search, PUBLIC/UNLISTED, Related Articles and Graph behavior are unchanged. See [`SEMANTIC_RETRIEVAL.md`](SEMANTIC_RETRIEVAL.md).

## Ask My Knowledge (authenticated owner only)

`POST /api/ask` accepts JSON **only** `{ "question": "How did I configure timeouts?" }`. Question is required, trimmed/non-blank, maximum 2000 UTF-16 units; request body is capped at 16 KiB before deserialization. Unknown fields are malformed input: no client owner, model, dimensions, context, limits or key. Existing owner session and CSRF token are required even though Knowledge is not mutated: this operation can consume provider quota/cost. No public/unlisted Ask endpoint exists.

Successful responses (`200`, private/no-store):

```json
{
  "status": "ANSWERED",
  "answer": { "blocks": [
    { "markdown": "The current notes record a five-second response timeout.", "citationIds": ["C1"] }
  ] },
  "citations": [
    {
      "id": "C1",
      "source": { "id": 12, "title": "WebClient timeouts", "slug": "webclient-timeouts" },
      "chunkIndex": 2,
      "evidence": "responseTimeout(Duration.ofSeconds(5))"
    }
  ]
}
```

```json
{ "status": "NO_CONTEXT", "answer": null, "citations": [] }
```

One query embedding uses the same `EmbeddingClient`/strategy/vector checks as Semantic Search. Retrieval uses owner-scoped current complete compatible chunks only, exact cosine, deterministic distance/Knowledge ID/chunk index/row ID ties. The default cap of 2 chunks per note is applied **before** the global 8 chunks, then context assembly caps 6 unique notes and 24000 serialized characters including escaped metadata/text. It preserves whole chunks when possible and safely trims the final chunk. Each included chunk gets one deterministic request-local `S1` sourceRef, mapping server-side to its exact RagChunk and included text. These refs are not permanent IDs and never persisted/exposed in the API. Revisions are excluded unless restored into current content and reindexed. All owner visibility states are allowed; no sharing token/owner/hash/model/vector/distance is returned.

No context means no generation request. Otherwise one `KnowledgeAnswerClient` native Gemini structured-output call returns a provider-neutral draft `{blocks:[{markdown,sourceRefs}]}`. Each block requires ≥1 known ref; backend validates every ref and the whole structure atomically. Unknown/empty/duplicate refs within a block, malformed JSON/extra forged metadata, blank/oversized Markdown or excessive complexity produce `ASK_UNAVAILABLE`, never partially accepted citations. Limits: 24 blocks, 8192 units/block, 65536 total Markdown units, 128 reference occurrences, unique citations ≤the actual included chunks (max configured 100). Native generation requires a complete STOP candidate; no continuation/retry for truncated output.

`C1`, `C2` response IDs are backend-assigned in first answer-reference occurrence order. Repeated citations to the same Knowledge+chunk reuse the ID across blocks; each distinct chunk remains separate even if it belongs to the same note. `source` metadata/chunkIndex comes only from the retrieved snapshot. `evidence` is a backend-derived exact prefix of the included chunk text, surrounding whitespace trimmed, maximum 400 UTF-16 units with Unicode-safe truncation and no fabricated ellipsis. It is checked as a literal substring of that chunk, not fuzzily/semantically matched. The API does not trust provider-returned quotes/IDs/slugs. Citations are the authoritative source contract; the old answer string/separate sources array is replaced, requiring matching API/Web deployment.

Both provider calls are outside DB transactions/connections. Citation validation uses zero extra SQL/provider calls. No synchronous indexing, automatic retry, Keyword fallback, web search, query rewrite, rerank, tools, continuation, agent loop or chat persistence. Citation identity/evidence existence **does not prove logical claim entailment or factual correctness**. Context is current at the SQL snapshot; edits/deletion after that snapshot may occur before the answer arrives. Empty corpus means no compatible indexed context, not a calibrated semantic relevance cutoff.

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Blank/missing/oversized question |
| 400 | `MALFORMED_REQUEST` | Invalid JSON, unknown fields or body over 16 KiB |
| 401 / 403 | Existing auth/access error | Missing owner session, unauthorized identity or invalid CSRF |
| 503 | `ASK_DISABLED` | Generation disabled/missing client |
| 503 | `ASK_RETRIEVAL_UNAVAILABLE` | Embeddings disabled/unavailable, local quota denial, provider 429/timeout or invalid retrieval data |
| 503 | `ASK_UNAVAILABLE` | Generation quota denial/provider error/timeout/invalid answer |

API errors retain `{code,message,fieldErrors}` with constant sanitized messages, no upstream body/cause/secret. Gemini embedding and Ask default off; configured model IDs/dimensions/limits remain server-owned. Shared `GEMINI_API_KEY` never reaches DTOs or browser JavaScript. Persistent quota reservations count attempted calls conservatively, with global/background embedding budgets and independent generation budgets. See [`ASK_MY_KNOWLEDGE.md`](ASK_MY_KNOWLEDGE.md) for exact defaults, privacy, model-change responsibility and quota limits.

Protected `/ask` calls only a focused same-origin `/api/ask-my-knowledge` POST BFF with session/CSRF forwarding, bounded question-only body and Host/Origin validation. Typing/mount do not call AI; button/Cmd-Ctrl Enter explicitly submits; regular Enter adds a newline. No question URL/storage/history. Limited answer Markdown cannot render active arbitrary links/images/HTML/Mermaid or manufacture citation controls. Structured `[1]` controls beside each block focus its evidence item; Sources / Evidence groups notes once with their cited chunk items and Open note links to `/knowledge/{slug}`. Exact in-document chunk/heading jumps are deferred; no guessed fragments/Reading anchor changes. Cancel/obsolete response guards protect UI state, not guaranteed cancellation/refund of already-running provider work.

## AI metadata suggestions (authenticated owner only)

`POST /api/knowledge/{id}/ai/metadata-suggestions`, no input body. CurrentOwner/owner-scoped current snapshot, CSRF required, `Cache-Control: private, no-store, max-age=0`. Success `{ "summary": "Concise factual text", "tags": ["New topical tag"] }`: summary max 500/non-blank, at most five new tags max 50, trimmed/case-insensitively deduplicated and filtered against current tags. No owner/prompt/raw note/provider/quota details. No write until explicit user apply through ordinary authoring PUT.

Errors: 401 anonymous, 403 rejected owner/CSRF, 404 missing/cross-owner when enabled; 503 `AI_METADATA_DISABLED` or `AI_METADATA_UNAVAILABLE` (quota/provider/invalid output). A deterministic prefix bounds long notes; no revisions, attachment binaries, retrieval or retries. Private content goes to Gemini only when explicitly enabled/requested. Next BFF `POST /api/knowledge-metadata-suggestions` accepts only `{id}` and forwards session/CSRF. [Full contract/configuration/privacy](AI_METADATA_SUGGESTIONS.md).

## Ownership and authorization

The backend accepts only an authenticated Google OIDC principal with a verified email matching `AUTH_ALLOWED_EMAIL` case-insensitively. After that identity check, `CurrentOwner` returns:

```text
APP_OWNER_ID
```

`APP_OWNER_ID` is the stable persisted partition key for existing data; it is not an authentication credential and does not grant access on its own. `ownerId` is never accepted from request payloads. Reads, updates and deletes query by both the requested identifier and the authorized owner, so another owner's item is returned as `404` rather than exposed. No User table or ownership migration is part of this single-owner milestone.

A future multi-user system may map OIDC subject to an application User UUID at this boundary without spreading provider-specific identity logic through Knowledge/Search services.

## Next.js consumption

The web application consumes this contract from Next.js Server Components and Server Actions. Its backend URL comes from the server-only `KNOWLEDGE_API_BASE_URL` setting, which defaults to `http://localhost:8080`; no `NEXT_PUBLIC_*` API URL is required. The narrow login redirect may use the separate server-only `KNOWLEDGE_API_BROWSER_BASE_URL` when the browser and server need different origins.

- List, Reading and Edit initialization use owner-specific API reads with `cache: no-store` and dynamic route rendering.
- The workspace shell loads Collection summaries independently of notes, so empty collections appear in the sidebar and Create/Edit pickers. `/collections` manages names and `/collections/{id}` loads the owner's filtered note list.
- Full Search initially executes its selected mode server-side. Subsequent Keyword Full Search and typed Quick Search use `/api/knowledge-search`; explicit-submit Semantic uses the focused `/api/knowledge-semantic-search` BFF. Both delegate to their owner-scoped Spring endpoints without exposing the backend URL.
- Ask uses only the focused `/api/ask-my-knowledge` POST BFF with no-store, server-side session/CSRF and no question in URLs; existing authoring Server Actions remain unchanged.
- `POST`, `PUT`, focused visibility `PATCH`, and link regeneration run through Server Actions. The frontend mapping emits only fields required by each operation.
- Server-side reads and the Search BFF forward the incoming `JSESSIONID`; mutations additionally fetch `/api/auth/csrf` and forward its token header.
- `/k/{slug}` uses a focused server-only public client with `cache: no-store` and does not send the private session cookie. Its basic metadata is indexable only after the public API successfully returns a PUBLIC item.
- `/s/{shareToken}` uses the anonymous shared endpoint with no owner cookie and dynamic `no-store` rendering. It sends `noindex, nofollow, noarchive` plus `Referrer-Policy: no-referrer`; it has no WorkspaceShell or private actions.
- Reading and Edit reuse one dependency-injected Share Dialog. A successful mutation updates its confirmed visibility; Reading refreshes server data, while Edit serializes an in-flight autosave, any pending draft flush, and the focused visibility PATCH in that order. Later autosaves use the confirmed visibility and cannot replay an older value over it.
- The protected `/knowledge/{slug}/history` route reads compact revision pages, fetches full Markdown only for the selected preview, and restores through a CSRF-aware Server Action. Historical attachment references resolve through the current owner's exact Knowledge boundary. The editor flushes pending autosave work before navigating to History.
- Persisted Edit pages enable Crepe ImageBlock. Its upload callback uses the focused same-origin Next.js image route; `proxyDomURL` changes only the displayed URL while Markdown keeps `attachment://<UUID>`. Create mode leaves ImageBlock disabled until the note has a server ID.
- Opening an already-UNLISTED dialog performs the owner link GET only. Tokens are kept only in dialog memory, never in generic Knowledge responses, `localStorage` or `sessionStorage`. Regeneration requires a second inline confirmation and retains the old displayed link if the request fails.
- Workspace pages resolve `/api/auth/me` on the server and redirect `401`/`403` responses to `/login`. Backend authorization remains authoritative.
- Successful mutations revalidate `/`, `/search`, affected Collection routes, the stable Reading route and its Edit route.
- Frontend `Private`/`Unlisted`/`Public` labels map to API `PRIVATE`/`UNLISTED`/`PUBLIC` values in one transport boundary.
- API errors are reduced to quiet user-facing create/autosave feedback; upstream stack traces are never exposed.

Quick Search with an empty query continues to show recently updated notes from the existing owner list. Non-empty queries never fall back to client-side ranking when the search API fails.

## Slugs and publication timestamps

The backend generates a URL-safe lowercase slug from the title only during creation. Diacritics are normalized, punctuation and repeated separators collapse to hyphens, and leading/trailing separators are removed. Updating a title never changes its existing slug.

V1 defines a global unique constraint on `knowledge.slug`; it is retained to keep future public slug lookup unambiguous. Collisions receive numeric suffixes such as `spring-webclient-timeout-2` and `spring-webclient-timeout-3`.

`publishedAt` records the first transition to `PUBLIC`. It remains unchanged if the item later becomes `PRIVATE` or `UNLISTED`; publication history beyond that single timestamp is deferred.

## Collection and tag normalization

Knowledge requests use names rather than database IDs. The service resolves or creates Collection and Tag rows only inside the current owner scope.

- Surrounding whitespace is removed and repeated internal whitespace is collapsed.
- Matching is case-insensitive, while the first persisted capitalization remains the stable display name.
- One or more leading `#` characters are removed from tag names.
- Blank collection/tag names are rejected; tag duplicates in one request are deduplicated after normalization.
- One owner may reuse a metadata row across notes, while different owners may independently use the same textual name.
- Deleting or removing the final association does not automatically delete reusable Collection or Tag rows.

## Errors

Errors never include stack traces. A missing or differently owned item returns `404`:

```json
{
  "code": "KNOWLEDGE_NOT_FOUND",
  "message": "Knowledge item not found",
  "fieldErrors": {}
}
```

Validation failures return `400` with field errors:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "fieldErrors": {
    "title": "Title must not be blank"
  }
}
```

Malformed JSON, unknown fields, invalid enum values and malformed path parameters return `400` with code `MALFORMED_REQUEST`.

Spring Security returns `401` when no authenticated owner session is present and `403` for a rejected authenticated request or a missing/invalid CSRF token. These responses do not contain stack traces.

Owner link-management requests use the same owner-scoped 404 boundary as CRUD. An existing note with no stored link returns `404 UNLISTED_LINK_NOT_FOUND`. Anonymous shared lookup always uses `404 UNLISTED_KNOWLEDGE_NOT_FOUND` and never explains whether a token is unknown, inactive or rotated.

## Deferred API areas

Tag listing-management endpoints, generic/non-image attachments, attachment deletion, bucket-inventory reconciliation for crash-window objects, revision diffs/labels/pruning, collaborative authorship and audit-grade history are intentionally not implemented yet. Existing owner endpoints remain authenticated regardless of visibility. `/k/{slug}` and `/s/{shareToken}` are real, separate anonymous read routes, and the authenticated Share Dialog now persists visibility and manages the single current UNLISTED link.

Concurrent editing is still last-write-wins: there is no optimistic version field or multi-tab conflict resolution. Checkpoints improve recovery after a conflicting save, but they do not merge drafts or turn this feature into collaborative/audit history.
