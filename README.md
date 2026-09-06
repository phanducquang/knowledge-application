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

- `apps/web`: initialized with Next.js, TypeScript, App Router, Tailwind CSS and the first Knowledge List reference screen using mock data.
- `apps/api`: reserved for Spring Boot; not initialized yet.
- No authentication, persistence, backend integration or production business logic has been implemented yet.

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
