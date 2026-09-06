# Reference Screens

The first approved screens become the reference implementation of the visual language. New screens should reuse their patterns rather than independently inventing a style.

Build and approve them in this order.

## 1. Knowledge List

**Status: Approved reference screen — September 6, 2026.**

Purpose:

- establish workspace layout
- establish desktop/mobile navigation
- establish sidebar behavior
- establish search placement
- establish list item hierarchy
- establish tags/metadata presentation
- establish dividers and density

Approved decisions:

- desktop uses the persistent sidebar as the global navigation; there is no fixed global top navbar
- each screen may have its own page header inside the content area for context, title, search and page-specific actions
- mobile uses a minimal top application bar to open the navigation drawer and expose an essential primary action
- knowledge entries use an editorial list with dividers rather than a card grid
- the warm-neutral + petrol/teal palette and the 60/25/15 Technical Editorial Workspace direction are approved foundations

Representative content may include technical notes such as Spring Boot, Redis, Elasticsearch and Nginx topics.

Later screens must treat the Knowledge List shell, sidebar, density, palette and basic controls as approved primitives rather than redesigning them.

## 2. Knowledge Reading Page

**Status: In progress — reference screen #2.**

Purpose:

- establish article width
- establish title and metadata hierarchy
- establish H1/H2/H3/body typography
- establish code blocks, tables, lists and quotes
- establish breadcrumbs/table-of-contents behavior
- establish public/private reading DNA
- establish page-specific reading actions without introducing a global top navbar

Current decisions under review:

- page-specific navigation/actions such as `← All notes`, `Edit`, and `Share` belong to the navigation/action hierarchy, not metadata
- these actions use approximately 14px on mobile and 13px on larger screens, with comfortable minimum hit areas
- article metadata such as visibility, updated date, and read time remains around 12px so the hierarchy stays visibly distinct
- the action row remains page-local and must not evolve into a duplicated global top navigation bar

This is one of the two most important reference screens together with Knowledge List.

## 3. Knowledge Editor

Purpose:

- establish writing surface
- establish title editing
- establish compact formatting controls
- establish save/autosave feedback
- establish editor/mobile behavior

The editor should feel quiet and content-first.

## 4. Search

Purpose:

- establish search input treatment
- establish keyboard navigation
- establish result hierarchy
- establish empty/loading states

Search results should remain information-dense and consistent with Knowledge List.

## 5. Share Dialog

Purpose:

- establish dialog visual language
- establish visibility selection
- establish copy-link behavior
- establish form controls and primary/secondary actions

Expected visibility options:

- Private
- Unlisted
- Public

## Approval rule

Do not rush from one reference screen to the next.

A reference screen should first be reviewed for:

- hierarchy
- density
- spacing
- responsiveness
- unnecessary cards
- unnecessary icons
- excessive radius/shadows
- consistency with `DESIGN.md`

Once approved, later screens should treat it as an implementation reference rather than redesigning the same primitives.
