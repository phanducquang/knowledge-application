# Web Agent Instructions

Before any UI change, read these repository-level documents in order:

1. `../../docs/DESIGN.md`
2. `../../docs/REFERENCE_SCREENS.md`
3. `../../docs/AI_CODING_GUIDELINES.md`

The Knowledge List, Knowledge Reading Page, Knowledge Editor and Search Page are **approved** reference implementations of the visual language. Reuse their typography, spacing, warm-neutral + petrol/teal palette, dividers, sidebar density, content widths, action hierarchy, control radius, responsive behavior, editable-field affordances, authoring patterns, search surfaces and result-state treatment rather than inventing a new visual system.

Approved shell rule:

- desktop: persistent sidebar + page content, no global fixed top navbar
- mobile: minimal top application bar + navigation drawer + page content
- page-specific context/actions belong in the page header inside the content area
- route-aware primary navigation may expose destinations such as All notes and Search without introducing a second global navigation system
- navigation destinations and shell commands must remain semantically distinct: `Search` in primary navigation routes to `/search`, while Quick Search is exposed as a shell utility/keyboard accelerator

The **Quick Search Overlay (4B)** is the current reference interaction under review. It complements the approved `/search` page rather than replacing it. Share Dialog remains the next reference overlay after 4B is approved.

Editor responsibility:

- user-editable Knowledge fields belong in the editor: title, summary, Markdown content, visibility, collection and tags
- system-managed fields such as updated timestamps remain read-only
- slug is not a routine editor field; sharing/link-token mechanics belong to the Share flow
- editable article-setting values use one neutral hierarchy; accent indicates interaction/focus rather than arbitrary importance between fields
- the Markdown Content authoring engine is **Milkdown + Crepe**; do not replace it with a hand-built textarea toolbar without an explicit architecture decision
- enable Crepe authoring interactions that materially improve writing, including the top bar, floating selection toolbar, slash/block menu, block handles, drag/drop, lists, tables, links and CodeMirror code blocks
- the general product rule to avoid becoming a Notion clone does **not** prohibit proven block-editor interaction patterns inside the Markdown Content editor; slash commands, block handles, drag/drop, floating/contextual toolbars and similar authoring affordances are allowed when they materially improve writing efficiency
- this exception is scoped to the Content authoring surface only; it must not spread Notion-style layout, navigation, cards or visual language into the rest of the workspace
- preserve Markdown as the canonical content format even when Crepe presents WYSIWYG/block-style interactions
- Crepe `ImageBlock` remains disabled until object-storage upload/persistence is implemented; Crepe AI remains disabled until the AI phase has a real provider and product flow
- theme Crepe through its CSS variables and scoped `.knowledge-markdown-editor` overrides so it uses the established warm-neutral + petrol/teal visual system rather than introducing a second product theme

Search responsibility:

- `/search` is the approved full Search route and accepts an initial `q` query parameter
- the sidebar `Search` item is a route-aware navigation destination and must continue to navigate to `/search`
- the broad centered search surface, distinct idle/searching/no-match states, and editorial result rows are approved reference patterns
- search results reuse the approved Knowledge List row treatment rather than introducing search-specific cards
- every result row keeps consistent hover/focus geometry, including the first and last row
- broad active/hover fills remain warm-neutral and restrained; petrol/teal is concentrated in indicators, active text and focus treatment
- keyboard navigation remains first-class: the search field can move focus into results and result links support moving up/down and returning to the search field
- Quick Search and `/search` must share the same search-ranking logic while the prototype still uses mock data
- the current implementation searches mock client data only; production search should follow the architecture decision to use PostgreSQL Full Text Search for MVP rather than treating client-side filtering as final backend behavior

Quick Search responsibility:

- Quick Search is an overlay for fast navigation; `/search` remains the full search/exploration surface
- Quick Search is a shell utility/command, not a primary-navigation destination
- `Cmd/Ctrl + K` is the main desktop accelerator; a compact shell search utility may open it for discoverability, and mobile exposes an equivalent search utility in the top application bar
- do not make the primary sidebar `Search` item open Quick Search; it must navigate to `/search`
- do not bind `/` globally because it conflicts with Markdown/Crepe slash-command authoring
- opening the overlay autofocuses search, locks background scrolling and traps focus; closing restores focus to the previous trigger when it still exists
- Escape and backdrop interaction close the overlay
- Arrow Up/Down traverses quick actions and Enter opens the selected result; pointer hover/focus updates the same selected state
- selecting a knowledge result must navigate directly to that note's Reading Page; do not route an identified result back through `/search?q=...`
- `/search?q=...` is reserved for the explicit `View all results` action when the user wants the full search/exploration surface
- an empty query shows a compact recently-updated list rather than a large empty-state page
- typed queries show at most a small quick-result set plus `View all results`, which transfers the query to `/search?q=...`
- Quick Search uses one restrained layered surface; result hover and keyboard selection reuse the approved Knowledge List/Search row grammar: `--row-hover` for the broad surface and `--accent-strong` for title/action text
- do not use a sidebar-style petrol left indicator or `--active` fill for Quick Search result rows; those semantics belong to active navigation, not knowledge-result hover/selection
- if another control/editor has already consumed `Cmd/Ctrl + K`, Quick Search must not override that handled shortcut

Share responsibility:

- Share is a modal/overlay opened from Reading and Editor page-local actions, not a standalone page or sidebar destination
- expected visibility choices are Private, Unlisted and Public; reuse the established visibility semantics
- Private has no external link, Unlisted exposes a secret share link/token, and Public exposes the public article URL
- copy-link belongs inside the Share flow and uses quiet success feedback
- focus must be trapped while open and restored to the triggering Share action when closed
- Escape closes the dialog; backdrop dismissal is allowed when no destructive or unsaved operation is in progress
- a restrained backdrop, shadow and dialog radius are allowed because this is genuinely layered UI
- do not nest dashboard-style cards inside the dialog and do not introduce a separate visual theme for sharing
- password-protected sharing is a later enhancement, not part of the initial reference overlay

Do not introduce a generic `Card` component unless a future semantic use case explicitly requires a contained surface.
