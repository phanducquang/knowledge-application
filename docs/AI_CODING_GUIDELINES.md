# AI Coding Guidelines

This repository is expected to be developed with substantial assistance from coding agents such as Codex.

The goal is to use AI for implementation without allowing each task to invent a different architecture or visual language.

## Mandatory reading order for UI work

Before implementing UI, read:

1. `docs/DESIGN.md`
2. `docs/REFERENCE_SCREENS.md`
3. relevant existing components/pages

Existing approved reference screens take precedence over an agent's preferred UI pattern.

## UI implementation workflow

Before coding a new screen:

1. inspect the existing application
2. identify existing primitives and approved reference screens
3. describe the intended visual hierarchy briefly
4. identify what can be reused
5. implement
6. review against `DESIGN.md`
7. remove generic AI-generated SaaS patterns

Do not introduce a new design primitive merely because it is convenient.

## Prompting principle

Bad prompt:

> Build a modern beautiful knowledge dashboard.

Preferred prompt style:

> Implement this screen using the approved visual language in `docs/DESIGN.md` and the existing reference screens. Reuse established typography, spacing, dividers, navigation and control patterns. Do not introduce new card styles or decorative primitives unless the information semantically requires a contained surface.

## Architecture work

Before implementing backend or cross-stack features, read `docs/ARCHITECTURE.md` and the relevant scope/API documentation.

Do not add infrastructure such as Elasticsearch, queues or separate services unless the feature requires it and the architecture decision is explicitly updated.

## Cross-stack changes

Because this is a monorepo, agents should inspect both `apps/web` and `apps/api` for features that span UI and API.

Do not assume that living in one repository means the applications must be deployed together.

## Documentation discipline

When an implementation changes an agreed architecture/design decision, update the appropriate document in the same change.

Do not silently diverge from architecture or design documentation.
