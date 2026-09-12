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

## Run the backend locally

```bash
docker compose -f infra/docker/compose.yml up -d
cd apps/api
export GOOGLE_CLIENT_ID='your-google-client-id'
export GOOGLE_CLIENT_SECRET='your-google-client-secret'
export AUTH_ALLOWED_EMAIL='you@example.com'
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
