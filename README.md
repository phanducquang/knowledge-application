# Knowledge Application

Personal technical knowledge base with private-by-default content and optional public/unlisted sharing.

## Repository model

This project is intentionally organized as a **monorepo with independent applications**:

```text
knowledge-application/
├── apps/
│   ├── web/                 # Next.js application
│   └── api/                 # Spring Boot application
├── docs/                    # Product, architecture and design guidance
├── infra/                   # Deployment/infrastructure configuration
│   ├── nginx/
│   └── docker/
└── .github/
```

The web and API applications remain independently buildable and deployable even though they live in one repository.

## Agreed technology direction

- Web: Next.js + TypeScript
- API: Spring Boot + Java
- Database: PostgreSQL
- Search for MVP: PostgreSQL Full Text Search
- Future semantic search: pgvector
- Object storage: Cloudflare R2 or MinIO
- Content format: Markdown
- Deployment: Docker + Nginx

## Current implementation status

- `apps/web`: approved Next.js reference UI connected server-side to the Knowledge CRUD API for the real List, Reading, Create, Edit, Search and Quick Search workflows.
- `apps/api`: initialized with Java 17, Spring Boot, Gradle Wrapper, PostgreSQL/JPA, Flyway and Actuator health checks.
- Flyway-managed Knowledge, reusable Collection and reusable Tag persistence are implemented, together with the owner-scoped Knowledge CRUD API.
- Create/Edit persist title, summary, Markdown, visibility, collection and tags; existing notes use serialized debounced autosave.
- PostgreSQL Full Text Search is implemented across Knowledge text and metadata. Full Search and typed Quick Search share the owner-scoped backend ranking through a focused same-origin Next.js boundary.
- Google OAuth2/OIDC authentication is implemented with Spring Security and a server-side HTTP session. One verified allowlisted Google email may act as the stable configured `APP_OWNER_ID`; configuration alone no longer grants access.
- Next.js protects workspace routes server-side, forwards the HttpOnly session cookie to Spring Boot, and obtains the session CSRF token before CRUD mutations.
- Anonymous read-only PUBLIC Knowledge is available at `/k/{slug}` through a separate safe backend DTO/query. PRIVATE, UNLISTED and missing slugs all remain opaque public 404s; owner CRUD/Search stays authenticated.
- The shared Reading/Edit Share Dialog now persists PRIVATE/PUBLIC/UNLISTED through an owner-scoped focused visibility mutation. PUBLIC links use `/k/{slug}`; UNLISTED links are loaded and explicitly rotatable through the backend-managed `/s/{shareToken}` contract without placing tokens in generic Knowledge state or browser storage.
- Owner-scoped revision history is available at `/knowledge/{slug}/history`, with compact paginated snapshots, full Markdown preview, interval-limited autosave checkpoints and safe restore that preserves stable URLs and all sharing state.
- Secure images are stored in a private S3-compatible bucket (MinIO locally, R2-compatible for deployment). Persisted Crepe editors upload PNG/JPEG/WebP/GIF and save stable `attachment://<UUID>` Markdown references; owner, PUBLIC, UNLISTED and history views resolve them through access-scoped application routes.
- Fenced Markdown code blocks use restrained syntax highlighting across private, PUBLIC, UNLISTED and History reading surfaces while canonical Markdown, inline code and unknown-language fallback remain unchanged.
- Explicit `mermaid` fences render locally in the browser across the same shared reading surfaces, with a lazy official Mermaid runtime, strict security settings and a source-preserving error fallback.
- GitHub Actions CI independently validates the Web and API applications on pushes to `main`, pull requests targeting `main`, and manual runs.

## Core product direction

Knowledge entries are private by default and may later be exposed as:

- `PRIVATE`: owner only
- `UNLISTED`: accessible through a non-guessable share link
- `PUBLIC`: accessible through a public slug and eligible for indexing

Initial product scope should stay small: knowledge authoring, organization, search, visibility and sharing first; advanced graph/AI features come later.

## Documentation

Read these before implementing features:

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/DESIGN.md`](docs/DESIGN.md)
- [`docs/AI_CODING_GUIDELINES.md`](docs/AI_CODING_GUIDELINES.md)
- [`docs/REFERENCE_SCREENS.md`](docs/REFERENCE_SCREENS.md)
- [`docs/PRODUCT_SCOPE.md`](docs/PRODUCT_SCOPE.md)
- [`docs/API.md`](docs/API.md)
- [`docs/ROADMAP.md`](docs/ROADMAP.md)

For AI-assisted UI work, `docs/DESIGN.md` is the design constitution and must be read before implementing or modifying UI.

## Continuous integration

The repository uses [`.github/workflows/ci.yml`](.github/workflows/ci.yml) as the repeatable full regression gate.

It runs on pushes to `main`, pull requests targeting `main`, and manual dispatches with two independent jobs:

- **Web** — Node.js 22, `npm ci`, `npm test`, `npm run lint`, `npm run build`. CI uses Node 22 because the current test runner relies on Node's built-in TypeScript stripping support.
- **API** — Java 17, Gradle dependency caching, `./gradlew clean build --no-daemon`

The API build includes the test suite, including Testcontainers-backed PostgreSQL/MinIO integration tests where the tests require them. GitHub-hosted Linux runners provide Docker for Testcontainers, so CI does not maintain a second PostgreSQL/MinIO service definition.

CI intentionally does not pretend to replace environment-specific smoke tests such as live Google OAuth or Cloudflare R2. Those remain manual checks when real credentials are available. See [`docs/AI_CODING_GUIDELINES.md`](docs/AI_CODING_GUIDELINES.md) for how coding agents should use CI without repeatedly rerunning unrelated full suites during iteration.

## Run the backend locally

```bash
docker compose -f infra/docker/compose.yml up -d
cd apps/api
export GOOGLE_CLIENT_ID='your-google-client-id'
export GOOGLE_CLIENT_SECRET='your-google-client-secret'
export AUTH_ALLOWED_EMAIL='you@example.com'
export OBJECT_STORAGE_ENDPOINT='http://localhost:9000'
export OBJECT_STORAGE_BUCKET='knowledge-images'
export OBJECT_STORAGE_ACCESS_KEY='knowledge_minio'
export OBJECT_STORAGE_SECRET_KEY='change-me-for-local-development'
./gradlew bootRun
```

Then check `http://localhost:8080/actuator/health`. See [`apps/api/README.md`](apps/api/README.md) for database environment variables and validation commands.

In another terminal, start the web application:

```bash
cd apps/web
npm install
npm run dev
```

Next.js calls `http://localhost:8080` by default. Set the server-only `KNOWLEDGE_API_BASE_URL` and, when different, the browser-reachable `KNOWLEDGE_API_BROWSER_BASE_URL`; see [`apps/web/README.md`](apps/web/README.md). Register `http://localhost:8080/login/oauth2/code/google` as the local Google OAuth redirect URI, then open `http://localhost:3000`.
