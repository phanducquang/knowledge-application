# Web Agent Instructions

Before any UI change, read these repository-level documents in order:

1. `../../docs/DESIGN.md`
2. `../../docs/REFERENCE_SCREENS.md`
3. `../../docs/AI_CODING_GUIDELINES.md`

The Knowledge List, Knowledge Reading Page and Knowledge Editor are **approved** reference implementations of the visual language. Reuse their typography, spacing, warm-neutral + petrol/teal palette, dividers, sidebar density, content widths, action hierarchy, control radius, responsive behavior, editable-field affordances and authoring patterns rather than inventing a new visual system.

Approved shell rule:

- desktop: persistent sidebar + page content, no global fixed top navbar
- mobile: minimal top application bar + navigation drawer + page content
- page-specific context/actions belong in the page header inside the content area
- route-aware primary navigation may expose destinations such as All notes and Search without introducing a second global navigation system

The Search screen is the current reference screen under review. It must inherit the approved list/result hierarchy and remain information-dense, keyboard-friendly and flat rather than becoming a search dashboard or card grid.

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

- `/search` is the reference Search route and accepts an initial `q` query parameter
- search results reuse the approved Knowledge List row treatment rather than introducing search-specific cards
- keyboard navigation must remain first-class: the search field can move focus into results and result links support moving up/down and returning to the search field
- empty, searching and no-match states should stay quiet and useful rather than becoming illustrated or promotional empty states
- the current implementation searches mock client data only; production search should follow the architecture decision to use PostgreSQL Full Text Search for MVP rather than treating client-side filtering as final backend behavior

Do not introduce a generic `Card` component unless a future semantic use case explicitly requires a contained surface.
