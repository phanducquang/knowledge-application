# Reference Screens

The first approved screens become the reference implementation of the visual language. New screens should reuse their patterns rather than independently inventing a style.

Build and approve them in this order.

## 1. Knowledge List

Purpose:

- establish workspace layout
- establish desktop/mobile navigation
- establish sidebar behavior
- establish search placement
- establish list item hierarchy
- establish tags/metadata presentation
- establish dividers and density

Knowledge entries should be presented primarily as an editorial list, not a card grid.

Representative content may include technical notes such as Spring Boot, Redis, Elasticsearch and Nginx topics.

## 2. Knowledge Reading Page

Purpose:

- establish article width
- establish title and metadata hierarchy
- establish H1/H2/H3/body typography
- establish code blocks, tables, lists and quotes
- establish breadcrumbs/table-of-contents behavior
- establish public/private reading DNA

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
