# Web Agent Instructions

Before any UI change, read these repository-level documents in order:

1. `../../docs/DESIGN.md`
2. `../../docs/REFERENCE_SCREENS.md`
3. `../../docs/AI_CODING_GUIDELINES.md`

The Knowledge List, Knowledge Reading Page, Knowledge Editor and Search are **approved** reference implementations of the visual language. Reuse their typography, spacing, warm-neutral + petrol/teal palette, dividers, sidebar density, content widths, action hierarchy, control radius, responsive behavior, editable-field affordances, authoring patterns, search surfaces and result-state treatment rather than inventing a new visual system.

Approved shell rule:

- desktop: persistent sidebar + page content, no global fixed top navbar
- mobile: minimal top application bar + navigation drawer + page content
- page-specific context/actions belong in the page header inside the content area
- route-aware primary navigation may expose destinations such as All notes and Search without introducing a second global navigation system

The Share Dialog is the current reference overlay under review. It must inherit the approved workspace palette/control language while introducing only the layering patterns genuinely required by a modal/dialog.

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

- `/search` is the approved Search route and accepts an initial `q` query parameter
- the broad centered search surface, distinct idle/searching/no-match states, and editorial result rows are approved reference patterns
- search results reuse the approved Knowledge List row treatment rather than introducing search-specific cards
- every result row keeps consistent hover/focus geometry, including the first and last row
- broad active/hover fills remain warm-neutral and restrained; petrol/teal is concentrated in indicators, active text and focus treatment
- keyboard navigation remains first-class: the search field can move focus into results and result links support moving up/down and returning to the search field
- the current implementation searches mock client data only; production search should follow the architecture decision to use PostgreSQL Full Text Search for MVP rather than treating client-side filtering as final backend behavior

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
