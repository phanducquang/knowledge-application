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

Revision-safe attachment lifecycle cleanup is complete: fresh uploads receive a grace period, current and retained-revision references protect objects, Knowledge deletion preserves keys in a durable queue, and storage failures retry safely. Collection management/navigation is complete with owner-scoped CRUD, empty-collection visibility, filtered note lists and safe unfiling on deletion. Owner-only wiki links, backlinks, Related Articles and Knowledge Graph use current persisted content/metadata and stable slugs. Related notes retain deterministic ranking, while Graph contains only explicit wiki edges and includes isolated notes. Neither exposes owner relationships on PUBLIC/UNLISTED pages or uses AI/embeddings/a dedicated edge index. Semantic Retrieval Foundation, Semantic Search, Gemini-powered Ask My Knowledge and Source-linked Answers are complete. Generic files and image processing remain separate work.

## Phase 2 — Sharing and authoring quality

- Unlisted visibility/share token — complete, including Reading/Edit Share Dialog wiring
- attachments/images via R2 or MinIO — complete for secure images, access-scoped delivery, revision-safe orphan retention and retryable deletion cleanup
- revision history — complete for checkpoint list/preview/restore; diffs, labels and retention remain deferred
- richer collection management/navigation — complete for create, rename, delete, counts, empty collections and filtered note lists
- table of contents — complete for persisted Markdown H2-H4 headings
- syntax highlighting — complete for fenced Markdown code across every shared reading surface
- Mermaid diagram rendering — complete for explicit fenced Markdown across private, PUBLIC, UNLISTED and History reading surfaces

## Phase 3 — Knowledge relationships

- backlinks — complete for owner-scoped current Markdown references
- wiki links — complete for canonical `[[stable-slug]]` navigation in owner Reading
- related articles — complete for owner-only current wiki, backlink, shared-tag and Collection signals with deterministic ranking
- knowledge graph — complete for owner-only current directed wiki edges, isolated nodes, Collection filtering and focus/navigation

## Phase 4 — AI-assisted retrieval

Semantic Retrieval Foundation, owner-only Semantic Search, **Gemini-powered Ask My Knowledge and Source-linked Answers** are complete. Embedding/generation remain disabled unless explicitly enabled with a backend-only key. `/search` offers explicit-submit Semantic alongside default 180ms live Keyword FTS; Quick Search stays FTS. `/ask` adds one structured answer with backend-validated current chunk citations/evidence and Reading navigation, not chat or proof of logical claim entailment. PostgreSQL quota reservations protect global/background embeddings and independent generation; model/strategy changes still automatically stale/reindex old vectors. SDK stays 1.75.0 without continuation. No hybrid ranking or anonymous AI access is added.

- PostgreSQL 17 + pgvector — complete
- embedding provider/storage infrastructure — complete
- current Knowledge chunk indexing/backfill and freshness detection — complete
- semantic search — complete
- Ask My Knowledge — complete for single-turn Gemini answers and note-level sources
- Source-linked Answers — complete for structured blocks, exact current chunk/evidence validation, citation reuse and Reading navigation
- quota usage retention — complete for configurable bounded expired-window cleanup, active/DST safety and non-blocking cross-replica coordination; persistent reservation semantics unchanged
- retrieval quality evaluation — complete for an offline synthetic corpus, actual PostgreSQL/pgvector Semantic/RAG queries, independent note/chunk metrics and deterministic reports; [details and observed misses](RETRIEVAL_EVALUATION.md)
- source-chunk coverage/relevance evaluation — recommended next, based on long-note configuration/paraphrase diagnostics; no tuning or automatic provider checks started
- live Gemini retrieval evaluation — future/manual, requires explicit operator approval; real-model semantic quality not evaluated by the offline harness
- auto tagging/summarization — future, requires separate review after reliability work

Exact scroll-to-chunk/heading-aware source jumps remain deferred until Reading has a reliable deterministic anchor contract. Grounding evaluation and evidence-to-claim correctness remain explicit limitations, not claims of factual proof.
