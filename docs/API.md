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
- Full Search initially executes server-side. Subsequent Full Search and typed Quick Search requests use only the focused same-origin `/api/knowledge-search` Next.js Route Handler; it delegates to the same Spring endpoint without exposing the backend URL.
- `POST`, `PUT`, focused visibility `PATCH`, and link regeneration run through Server Actions. The frontend mapping emits only fields required by each operation.
- Server-side reads and the Search BFF forward the incoming `JSESSIONID`; mutations additionally fetch `/api/auth/csrf` and forward its token header.
- `/k/{slug}` uses a focused server-only public client with `cache: no-store` and does not send the private session cookie. Its basic metadata is indexable only after the public API successfully returns a PUBLIC item.
- `/s/{shareToken}` uses the anonymous shared endpoint with no owner cookie and dynamic `no-store` rendering. It sends `noindex, nofollow, noarchive` plus `Referrer-Policy: no-referrer`; it has no WorkspaceShell or private actions.
- Reading and Edit reuse one dependency-injected Share Dialog. A successful mutation updates its confirmed visibility; Reading refreshes server data, while Edit serializes an in-flight autosave, any pending draft flush, and the focused visibility PATCH in that order. Later autosaves use the confirmed visibility and cannot replay an older value over it.
- The protected `/knowledge/{slug}/history` route reads compact revision pages, fetches full Markdown only for the selected preview, and restores through a CSRF-aware Server Action. The editor flushes pending autosave work before navigating to History.
- Opening an already-UNLISTED dialog performs the owner link GET only. Tokens are kept only in dialog memory, never in generic Knowledge responses, `localStorage` or `sessionStorage`. Regeneration requires a second inline confirmation and retains the old displayed link if the request fails.
- Workspace pages resolve `/api/auth/me` on the server and redirect `401`/`403` responses to `/login`. Backend authorization remains authoritative.
- Successful mutations revalidate `/`, `/search`, the stable Reading route and its Edit route.
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

Collection/tag listing-management endpoints, attachments, revision diffs/labels/pruning, collaborative authorship and audit-grade history are intentionally not implemented yet. Existing owner endpoints remain authenticated regardless of visibility. `/k/{slug}` and `/s/{shareToken}` are real, separate anonymous read routes, and the authenticated Share Dialog now persists visibility and manages the single current UNLISTED link.

Concurrent editing is still last-write-wins: there is no optimistic version field or multi-tab conflict resolution. Checkpoints improve recovery after a conflicting save, but they do not merge drafts or turn this feature into collaborative/audit history.
