# API Application

Spring Boot REST API for Knowledge Application.

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
- `/api/knowledge/**`, `/api/search/**`, `/api/auth/me` and `/api/auth/csrf` require owner authorization.
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

Only PNG, JPEG, WebP and GIF are accepted; SVG is deliberately excluded. Upload requires the owner session and CSRF. Public/shared streams remain scoped to the exact parent and active visibility. See `../../docs/API.md` for consistency and deferred orphan-cleanup semantics.

## Validate

```bash
./gradlew test
./gradlew clean build
```

Before implementing backend features, read:

- `../../docs/ARCHITECTURE.md`
- `../../docs/API.md`
- `../../docs/PRODUCT_SCOPE.md`
