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

This screen and Knowledge List are primary approved visual references for subsequent authoring/search work.

## 3. Knowledge Editor

**Status: Approved reference screen — September 6, 2026.**

Purpose:

- establish the complete authoring surface for a Knowledge item, not only the Markdown body
- establish title and summary editing
- establish content editing
- establish editable visibility, collection and tags
- establish Markdown authoring interactions
- establish save/autosave feedback placement
- establish editor/mobile behavior

Approved decisions:

- the editor is responsible for editing the user-controlled Knowledge fields: `title`, `summary`, `content`, `visibility`, `collection`, and `tags`
- system-managed metadata such as `updatedAt` is displayed as read-only and must not look editable
- slug should normally be generated from the title when the article is created and remain stable afterward; it is not a normal editor field
- public/unlisted link mechanics and share-token management belong to the Share flow rather than the main writing surface
- editable areas must be visually recognizable before focus; users should not have to click or delete text to discover that a field is editable
- `Title`, `Summary`, and `Content` use explicit micro-labels plus persistent subtle input/editor boundaries; focus strengthens the boundary with the product accent
- the editor title is intentionally more compact than the Reading Page title: approximately 28px on mobile and 30px on larger screens, so it feels like an editable working document rather than a publication headline
- summary remains visually secondary at approximately 15px
- reuse the Reading Page content width so writing and reading feel like the same document system
- the Markdown Content editor uses **Milkdown + Crepe** rather than a hand-built textarea toolbar
- Crepe `TopBar` is enabled so common structure/format/list/insert/block commands remain available while writing; the floating selection toolbar also remains enabled
- Crepe block editing remains enabled, including slash commands, block handles and drag/drop; these interactions are explicitly allowed inside Content even when they resemble familiar Notion-style authoring patterns
- Crepe CodeMirror, list, link and table features remain available for technical writing; code blocks are a first-class authoring primitive
- the top-bar heading selector exposes Text, H2, H3 and H4 because the Knowledge title is the document-level title outside the Markdown body
- Crepe `ImageBlock` remains disabled until image upload/object-storage persistence is implemented, so the UI must not advertise an upload action that cannot be saved reliably
- Crepe AI remains disabled until the AI phase has a configured provider and an approved product flow
- Markdown remains the canonical content value; Crepe's `markdownUpdated` event updates the current Markdown representation even though the visible authoring surface is WYSIWYG/block-oriented
- Crepe is themed through its CSS variables and scoped editor styles to inherit the product's warm-neutral surfaces, petrol/teal accent, Geist typography and Geist Mono code treatment
- the general product rule to avoid becoming a Notion clone is explicitly relaxed **inside the Markdown Content editor only**; slash commands, block handles, drag/drop, floating/contextual toolbars and similar block-editor interactions are acceptable if they improve writing efficiency
- the Content-editor exception does not authorize Notion-style workspace layout, navigation, card systems or decorative visual language elsewhere in the product
- show save state as quiet status feedback rather than a dominant control
- desktop article properties use a narrow property rail without an extra `Article settings` heading; mobile keeps `Article settings` only as the label for the expandable properties section
- property typography must stay visibly subordinate to the authoring surface: labels remain around 10px uppercase micro-labels, while values and controls may use a larger readable scale as long as they remain secondary to title/summary/content
- `Visibility` uses one visible three-option radio group because the option set is small and fixed; visually it reads as lightweight text tabs with a subtle selected indicator, not three boxed buttons
- the visibility control remains stable for longer labels such as `Unlisted`; selection must not change control dimensions or cause wrapping/layout shift
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
- implementation/reference disclaimers are not displayed inside the user-facing editor UI; development status belongs in project documentation instead
- autosave and backend persistence remain product implementation work, but they do not block the approved Editor reference UI unless they later require a meaningful interaction change

The approved editor is quiet, complete and content-first: a user can understand that all meaningful article data can be maintained from this screen without turning it into an admin form.

## 4. Search

**Status: In progress — reference screen #4.**

Purpose:

- establish search input treatment
- establish keyboard navigation
- establish result hierarchy
- establish empty/loading states
- establish how search connects to the approved Knowledge List shell

Current reference direction:

- Search lives at `/search` and accepts an initial `q` query parameter so searches can be reached directly from the Knowledge List search field
- the Knowledge List search field submits to `/search?q=...` rather than pretending to be an inactive local field
- the Search page reuses the approved workspace shell, 1080px content frame, page-title hierarchy and warm-neutral + petrol/teal visual system
- Search is exposed as a route-aware primary sidebar destination alongside All notes; the sidebar must not leave All notes visually selected while Search is active
- the search field is the dominant interaction on the page but remains a flat bordered control, not a floating command-card treatment
- the current prototype searches title, description/summary, collection and tags; relevance prioritizes title matches, then collection/tag matches, then description matches
- results reuse `KnowledgeListItem` and therefore keep the approved editorial rows, metadata hierarchy, dividers and subtle hover/focus treatment rather than introducing search-result cards
- keyboard navigation is first-class: Arrow Down from the search field moves into the result links, Arrow Up/Down moves between result links, Enter follows the focused result, and Escape returns focus to the search field
- result rows use `focus-within` feedback so keyboard navigation receives the same quiet row emphasis as pointer hover
- the page exposes quiet states for an empty query, searching, no matches and matched results; do not introduce illustrated or promotional empty states
- the empty-query state explains searchable fields and gives representative technical terms rather than showing every note by default
- the query is reflected in the browser URL without creating a new history entry for each keystroke
- the current search implementation uses mock/client-side data only; production MVP search must still follow the architecture decision to use PostgreSQL Full Text Search, and client filtering must not become the final backend search architecture

Search results should remain information-dense and consistent with Knowledge List. Do not add result cards, category dashboards or decorative filter panels unless a later search requirement clearly needs them.

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
