# Architecture

## 1. Architecture goal

Build a personal knowledge application that is simple to operate initially but has a clear path toward public sharing, revision history, backlinks and AI-assisted retrieval.

The system uses a monorepo containing two independent applications:

```text
Browser
   |
   v
Next.js Web
   |
   | REST API
   v
Spring Boot API
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

The exact persistence model is intentionally deferred until implementation.

## 5. Visibility model

### PRIVATE

Only the owner can view the knowledge entry.

### UNLISTED

The owner can view it normally. Other viewers need a non-guessable share URL/token. It should not appear in public listings and should not be intentionally indexed.

### PUBLIC

Anyone can view it via a stable public slug.

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

- PostgreSQL Full Text Search across title, summary, content and useful metadata

Do not introduce Elasticsearch solely for the MVP.

Future:

- PostgreSQL + pgvector for semantic retrieval
- AI-assisted question answering with citations back to stored knowledge

## 8. Authentication direction

The application is initially personal/single-owner oriented.

A simple OAuth-based login may be used rather than building a large user-management system. Authorization must still be modeled correctly so multi-user support remains possible later.

## 9. Future capabilities

Not part of the initial implementation, but architecture should not block:

- revision history and restore
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
