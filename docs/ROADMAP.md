# Roadmap

This roadmap represents the current direction, not a release commitment.

## Phase 0 — Foundation

- monorepo structure
- architecture documentation
- design constitution
- AI coding guidelines
- reference screen definitions

## Phase 1 — Core knowledge MVP

- initialize Next.js application
- initialize Spring Boot application — complete
- establish PostgreSQL/Flyway/Knowledge persistence foundation — complete
- owner-scoped Knowledge CRUD API — complete
- Collection and Tag persistence in Knowledge CRUD — complete
- Google OAuth2/OIDC authentication and principal-bound single-owner access — complete
- connect approved Knowledge screens to the CRUD API — complete
- Markdown editor/rendering — complete
- tags persistence — complete
- PostgreSQL persistence — complete
- PostgreSQL Full Text Search — complete
- Private/Public visibility — complete for owner editing and anonymous PUBLIC reads
- public slug page — complete at `/k/{slug}`
- server-managed UNLISTED bearer link and persisted Share Dialog — complete at `/s/{shareToken}`
- checkpoint-based revision history and authoring restore — complete at `/knowledge/{slug}/history`
- secure image attachments with private S3-compatible storage and Crepe ImageBlock — complete

### Completed backend vertical slice

The completed backend slice is:

```text
Create Knowledge
  -> persist to PostgreSQL
  -> reload
  -> Reading Page
  -> Edit
  -> Save
  -> reload
  -> updated data remains
```

The backend and approved frontend now provide this persistent CRUD contract, including collection and tags. List, Reading, explicit Create mode and Edit are API-backed; existing notes use real serialized autosave. Full Search and typed Quick Search now use the same owner-scoped PostgreSQL Full Text Search ranking, while empty Quick Search retains the recently-updated list.

Authentication now proves a verified allowlisted Google identity before `CurrentOwner` may resolve the unchanged `APP_OWNER_ID`. Spring Security protects CRUD/Search, Next.js forwards the HttpOnly session and CSRF token server-side, and private workspace routes redirect unauthenticated users to `/login`.

Anonymous PUBLIC reading now uses a separate `/api/public/knowledge/{slug}` query/DTO and dynamic `/k/{slug}` page. Visibility revocation is immediate, and the private owner routes remain authenticated.

UNLISTED sharing now uses a server-generated, persisted, non-guessable token and remains distinct from owner authentication and PUBLIC slug access. Visibility transitions activate/deactivate the same link, while explicit owner rotation invalidates it.

The Share Dialog is now wired from both Reading and Edit. It persists visibility through a focused owner endpoint, loads an existing UNLISTED token without rotation, and requires inline confirmation before rotation. Editor share changes serialize with autosave so an older full PUT cannot overwrite a newer visibility decision.

Revision history now records a CREATE snapshot, interval-limited pre-edit checkpoints, and a mandatory pre-restore safety snapshot. Owner-scoped list/detail/restore APIs and the protected History screen restore authoring fields while preserving slug, visibility, publication state and UNLISTED bearer credentials.

The recommended next milestone is attachment lifecycle/retention cleanup, with richer collection management as a smaller alternative. Generic files and image processing remain separate work.

## Phase 2 — Sharing and authoring quality

- Unlisted visibility/share token — complete, including Reading/Edit Share Dialog wiring
- attachments/images via R2 or MinIO — complete for secure images and access-scoped delivery; deletion/orphan retention is deferred
- revision history — complete for checkpoint list/preview/restore; diffs, labels and retention remain deferred
- richer collection management/navigation
- table of contents — complete for persisted Markdown H2-H4 headings
- syntax highlighting — complete for fenced Markdown code across every shared reading surface
- Mermaid diagram rendering — complete for explicit fenced Markdown across private, PUBLIC, UNLISTED and History reading surfaces

## Phase 3 — Knowledge relationships

- backlinks
- wiki links
- related articles
- knowledge graph

## Phase 4 — AI-assisted retrieval

- pgvector
- embeddings
- semantic search
- Ask My Knowledge
- source-linked answers
- auto tagging/summarization
