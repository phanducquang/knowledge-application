# Product Scope

## Product statement

Knowledge Application is a personal technical knowledge base where content is private by default and can be intentionally shared when desired.

## MVP

The initial product should focus on:

- authentication
- create/edit/delete knowledge
- Markdown content
- tags
- basic collections
- search
- Private/Public visibility
- stable public URLs

The exact feature sequencing may evolve, but scope should remain intentionally small.

## Next stage

After the core workflow is proven:

- Unlisted sharing
- image/file upload
- revision history
- richer collections
- table of contents
- code syntax highlighting
- Mermaid rendering

## Later stage

- backlinks and stable-slug wiki links — first owner-only slice complete
- related articles — complete for deterministic owner-only current relationships; no AI/embeddings
- knowledge graph — complete for owner-only current wiki edges, isolated notes, Collection filtering and direct-neighbor focus; no graph editing/AI
- improved search

## AI stage

Only after enough useful knowledge exists:

Semantic Retrieval Foundation is complete for owner-internal current-Knowledge chunk storage, disabled-by-default embeddings and background indexing/backfill. Revisions, attachments and anonymous retrieval are excluded; existing FTS and relationships remain unchanged.

Owner-only **Semantic Search is complete** on `/search`, alongside default live Keyword FTS. Semantic requests require explicit submit and send the query to the configured provider; results contain one best current compatible chunk per note. No anonymous retrieval or hybrid ranking is added. Next milestone: **Ask My Knowledge**.

- semantic search using the pgvector foundation — complete
- Ask My Knowledge
- automatic tags
- automatic summaries
- similar-knowledge suggestions

## Non-goals for MVP

Do not turn the first version into:

- a full Notion clone
- a generic team collaboration platform
- a multi-service distributed architecture
- an Elasticsearch-heavy platform
- an AI-first product before the core knowledge workflow works
