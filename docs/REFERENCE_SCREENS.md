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
- the mobile navigation drawer uses an icon-only close action with an accessible label; it belongs in the sidebar header layout rather than floating over header content
- knowledge entries use an editorial list with dividers rather than a card grid
- the warm-neutral + petrol/teal palette and the 60/25/15 Technical Editorial Workspace direction are approved foundations

Representative content may include technical notes such as Spring Boot, Redis, Elasticsearch and Nginx topics.

Later screens must treat the Knowledge List shell, sidebar, density, palette and basic controls as approved primitives rather than redesigning them.

## 2. Knowledge Reading Page

**Status: Approved reference screen — September 6, 2026.**

Purpose:

- establish article width
- establish title and metadata hierarchy
- establish H1/H2/H3/body typography
- establish code blocks, tables, lists and quotes
- establish breadcrumbs/table-of-contents behavior
- establish public/private reading DNA
- establish page-specific reading actions without introducing a global top navbar

Approved decisions:

- article content targets approximately 760px maximum reading width
- desktop table of contents may remain sticky because it directly supports long-form navigation
- the page-local action row `← All notes / Edit / Share` is not sticky and must not become a duplicated global navbar
- navigation/actions use approximately 14px on mobile and 13px on larger screens with comfortable minimum hit areas
- article metadata such as visibility, updated date and read time remains around 12px
- mobile uses an inline expandable table of contents instead of a persistent right rail
- code blocks, tables, lists and blockquotes remain flat editorial elements with borders rather than card containers
- the article footer uses the explicit label `Collection & tags` rather than editorial wording such as `Filed under`

This screen and Knowledge List are the primary approved visual references for subsequent authoring/search work.

## 3. Knowledge Editor

**Status: In progress — reference screen #3.**

Purpose:

- establish the complete authoring surface for a Knowledge item, not only the Markdown body
- establish title and summary editing
- establish content editing
- establish editable visibility, collection and tags
- establish compact formatting controls
- establish save/autosave feedback
- establish editor/mobile behavior

Current reference direction:

- the editor is responsible for editing the user-controlled Knowledge fields: `title`, `summary`, `content`, `visibility`, `collection`, and `tags`
- system-managed metadata such as `updatedAt` is displayed as read-only and must not look editable
- slug should normally be generated from the title when the article is created and remain stable afterward; it is not a normal editor field
- public/unlisted link mechanics and share-token management belong to the Share flow rather than the main writing surface
- editable areas must be visually recognizable before focus; users should not have to click or delete text to discover that a field is editable
- `Title`, `Summary`, and `Content` use explicit micro-labels plus persistent subtle input/editor boundaries; focus strengthens the boundary with the product accent
- the editor title is intentionally more compact than the Reading Page title: approximately 28px on mobile and 30px on larger screens, so it feels like an editable working document rather than a publication headline
- summary remains visually secondary at approximately 15px
- reuse the Reading Page content width so writing and reading feel like the same document system
- keep formatting controls compact and limited to common Markdown operations rather than a large permanent toolbar
- show save state as quiet status feedback rather than a dominant control
- desktop article properties use a narrow property rail without an extra `Article settings` heading; mobile keeps `Article settings` only as the label for the expandable properties section
- property typography must stay visibly subordinate to the authoring surface: labels remain around 10px uppercase micro-labels, while most property values, visibility options, tags, search results and helper actions sit around 11px; collection trigger text may use approximately 12px for readability
- `Visibility` uses one visible three-option radio group because the option set is small and fixed; visually it should read as lightweight text tabs with a subtle selected indicator, not three boxed buttons
- the visibility control must remain stable for longer labels such as `Unlisted`; selection must not change control dimensions or cause wrapping/layout shift
- `Collection` uses a compact searchable combobox because collections are extensible; the popover supports search, existing selection, and creating a new collection
- the collection popover closes when interaction moves outside its trigger/popover boundary
- the collection popover always exposes a creation entry: it shows `+ New collection` before a draft name exists, then changes to `+ Create “<name>”` when the typed value does not match an existing collection
- `Tags` use semantic chips: typing a tag and pressing Enter creates a chip; each chip exposes an `×` removal action; duplicate tags should not be created
- existing tag chips and the new-tag input are visually separate; do not place both inside one surrounding input border
- tag chips are an intentional semantic exception to the general rule against decorative pills; they represent discrete editable values, use restrained radius, and must not become a colorful badge system
- `Updated` is presented as quiet read-only metadata without redundant explanatory copy
- editable property values use the same neutral text hierarchy; accent is reserved for selected/focus/interaction states rather than making one property appear more important than another
- semantic color may be used when a state needs to communicate actual meaning, but editable form values should not rely on color alone
- page actions such as `Preview` and `Share` remain page-local and do not introduce a global top navbar
- implementation/reference disclaimers should not be displayed inside the user-facing editor UI; development status belongs in project documentation instead
- formatting buttons, autosave behavior, and persistence remain implementation work after the reference UI is approved

The editor should feel quiet, complete and content-first: a user should be able to understand that all meaningful article data can be maintained from this screen without turning it into an admin form.

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
