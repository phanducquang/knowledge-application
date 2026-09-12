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

**Status: Approved reference screen — September 7, 2026.**

Purpose:

- establish search input treatment
- establish keyboard navigation
- establish result hierarchy
- establish empty/loading states
- establish how search connects to the approved Knowledge List shell

Approved decisions:

- Search lives at `/search` and accepts an initial `q` query parameter so searches can be reached directly from the Knowledge List search field
- the Knowledge List search field submits to `/search?q=...` rather than pretending to be an inactive local field
- the Search page reuses the approved workspace shell, 1080px content frame, page-title hierarchy and warm-neutral + petrol/teal visual system
- Search is exposed as a route-aware primary sidebar destination alongside All notes; the sidebar must not leave All notes visually selected while Search is active
- the main search surface is centered and broad within the content frame so it visually owns the page rather than appearing as a narrow left-aligned form
- the search field remains a flat bordered control, not a floating command-card treatment
- the reference implementation searches title, description/summary, collection and tags; relevance prioritizes title matches, then collection/tag matches, then description matches
- results reuse `KnowledgeListItem` and therefore keep the approved editorial rows, metadata hierarchy, dividers and subtle hover/focus treatment rather than introducing search-result cards
- every result row uses the same vertical padding and interactive geometry; first/last rows must not change the hover/focus surface shape merely to adjust list spacing
- broad row hover and navigation-active surfaces stay warm-neutral and restrained; petrol/teal remains concentrated in indicators, active text and focus treatment rather than filling large areas
- keyboard navigation is first-class: Arrow Down from the search field moves into the result links, Arrow Up/Down moves between result links, Enter follows the focused result, and Escape returns focus to the search field
- result rows use `focus-within` feedback so keyboard navigation receives the same quiet row emphasis as pointer hover
- Search has distinct visual modes rather than representing every state as a result list: idle, searching, no matches, and matched results are separate workspace states
- the `Search results` heading, result count divider and editorial result rows render only when there are actual matched results
- the idle state uses a centered, open workspace treatment with concise guidance, representative search suggestions and the available-note count; it must not resemble a list row or a bordered result container
- the no-match state uses the same independent workspace-state grammar, clearly names the unsuccessful query and exposes lightweight alternative searches plus a clear-search action
- searching uses a quiet centered progress state rather than temporarily rendering a result-list shell with no results
- these idle/searching/no-match states remain content-first and flat; do not turn them into large cards, illustrated SaaS empty states or promotional panels
- the query is reflected in the browser URL without creating a new history entry for each keystroke
- the approved ranking interaction is currently applied client-side to the real owner-scoped Knowledge list; production MVP search must still follow the architecture decision to use PostgreSQL Full Text Search, and client filtering must not become the final backend search architecture

The approved Search screen is the reference for future find/filter flows: broad search surface, keyboard-first result traversal, editorial rows, and distinct non-list empty states.

## 4B. Quick Search Overlay

**Status: Approved reference interaction — September 9, 2026.**

Purpose:

- establish a fast global knowledge-navigation overlay
- complement full `/search` without replacing it
- establish keyboard-first command/search behavior
- establish the first reusable lightweight search overlay pattern
- validate desktop/mobile overlay density before Share Dialog

Approved decisions:

- Quick Search is for fast navigation while `/search` remains the full search/exploration page
- primary navigation and Quick Search have different responsibilities: the sidebar `Search` item always navigates to `/search`; Quick Search is a shell utility/command rather than a navigation destination
- `Cmd/Ctrl + K` is the primary desktop accelerator when that shortcut has not already been consumed by another control/editor
- desktop also exposes a compact search utility in the sidebar header for discoverability; mobile exposes an equivalent search utility in the top application bar
- do not bind `/` globally because Milkdown/Crepe uses slash-command authoring inside Content and the global shortcut must not interfere with editor interaction
- opening the overlay closes the mobile navigation drawer when necessary, locks background scrolling, autofocuses the search field and traps focus inside the overlay
- closing with Escape, the explicit `Esc` action or backdrop interaction restores focus to the previous trigger when that element still exists
- an empty query shows a compact `Recently updated` result set instead of reproducing the large idle state from the full Search page
- typed queries reuse the same relevance/ranking function as `/search` and show at most a small quick-result set so the overlay remains a navigation tool rather than a second full results page
- selecting a knowledge result navigates directly to that note's Reading Page; an identified result must not bounce through `/search?q=...` and force a second selection
- `View all results for “<query>”` is the explicit bridge to `/search?q=...` when the user wants the full search/exploration surface
- Arrow Up/Down changes the selected quick action, Enter opens the selected result from the search input, and pointer hover/focus updates the same selected state
- desktop uses one centered layered surface around 720px maximum width; mobile uses a near-full-width surface with controlled viewport height rather than a tiny centered desktop dialog
- the overlay uses a restrained backdrop, 6px-scale radius and layered shadow because it is genuinely elevated UI
- result rows remain flat inside the overlay and reuse the approved Knowledge List/Search interaction grammar: broad hover and keyboard-selection feedback use `--row-hover`, while the title/action text may move to `--accent-strong`
- do not use a sidebar-style petrol left indicator or `--active` navigation fill on Quick Search result rows; navigation selection and knowledge-result interaction are different semantics
- the compact metadata line uses collection/tags for scanning, while updated date stays quiet and right-aligned
- no-result state inside Quick Search remains compact and points to full Search instead of expanding into the full Search page's centered no-match canvas
- mock/prototype data should expose real Reading Page destinations for displayed results rather than using Search Page as a fallback for missing routes

Quick Search is the approved fast keyboard navigation layer over the workspace; it is not a second page compressed into a modal and not a replacement for the Search navigation destination.

## 5. Share Dialog

**Status: Approved reference overlay — September 9, 2026.**

Purpose:

- establish modal/overlay visual language
- establish visibility selection
- establish copy-link behavior
- establish form controls and primary/secondary actions
- establish keyboard/focus dismissal behavior
- establish responsive dialog behavior

Approved decisions:

- Share is an overlay/dialog opened from the page-local `Share` action on Reading and Editor screens; it is not a standalone workspace page or a new global navigation destination
- Reading and Editor reuse one `KnowledgeShareAction` interaction so sharing does not develop separate visual/behavior variants
- desktop and mobile use one centered, near-full-width-at-small-screens dialog surface; do not introduce a separate bottom-sheet language unless later testing demonstrates a clear reachability need
- opening Share locks background scrolling, traps focus inside the dialog, focuses the currently selected visibility control and restores focus to the triggering Share action on close
- Escape, the explicit close action and backdrop interaction close the dialog because the approved flow has no destructive or unsaved modal-local operation
- visibility choices are `Private`, `Unlisted`, and `Public`; the control reuses the Editor's lightweight text-radio treatment with a bottom selected indicator rather than boxed choices
- `Private` communicates that no external link is available and does not render a fake disabled URL control
- `Unlisted` exposes a secret-link shape under `/s/{shareToken}` and communicates that it is not listed or indexed
- `Public` exposes a public-link shape under `/k/{slug}` and communicates that it may be indexable
- backend UNLISTED tokens are secret and server-managed; the integrated dialog retrieves an existing token without rotation and rotates only after inline confirmation
- the share URL is read-only and selecting/focusing it may select the full value for manual copy
- Copy belongs beside the URL in the same flat bordered control; success/failure feedback remains quiet and inline rather than becoming a large toast
- the dialog uses one meaningful contained surface with a restrained backdrop, 6px-scale radius and layered shadow; internal sections are separated with dividers/spacing instead of nested cards
- password-protected sharing remains a later enhancement and must not complicate the approved initial Share Dialog
- persisted backend visibility is the source of truth shared by Reading metadata, Editor settings and Share; an external link appears only after the corresponding mutation succeeds

Expected visibility options:

- Private
- Unlisted
- Public

With Share Dialog approved, the current reference UI phase is complete. Subsequent implementation work should preserve these approved references and move toward real backend persistence, authorization, search and sharing behavior rather than creating additional mock reference screens without a product need.

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
