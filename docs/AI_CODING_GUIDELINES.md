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

## CI and verification workflow

GitHub Actions is the authoritative full regression gate for committed changes. The workflow at `.github/workflows/ci.yml` validates the independently buildable Web and API applications on pushes to `main`, pull requests targeting `main`, and manual dispatches.

During feature work, coding agents should:

1. run focused tests for the code they changed while iterating
2. run relevant lint/type/build checks when they are useful for catching local issues early
3. avoid repeatedly rerunning unrelated full suites unless a change crosses those boundaries or a failure requires it
4. after pushing, inspect the GitHub Actions result rather than assuming CI passed
5. if CI fails, inspect the failed job and step first, fix the root cause, then rerun the appropriate checks
6. never report GitHub Actions as passing unless the actual workflow run was checked

The CI workflow intentionally owns the repeatable full regression work:

- Web: clean dependency install, tests, lint and production build
- API: Java 17 Gradle clean build, including the test suite and Testcontainers-backed integration tests

CI does not replace milestone-specific real-stack verification when a change affects runtime integration, persistence, authentication, networking, object storage, browser behavior or external providers. Live Google OAuth and Cloudflare R2 checks remain explicit manual smoke tests when real credentials are available.

## Documentation discipline

When an implementation changes an agreed architecture/design decision, update the appropriate document in the same change.

Do not silently diverge from architecture or design documentation.
