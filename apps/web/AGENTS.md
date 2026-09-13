# Web Agent Instructions

Before any UI change, read these repository-level documents in order:

1. `../../docs/DESIGN.md`
2. `../../docs/REFERENCE_SCREENS.md`
3. `../../docs/AI_CODING_GUIDELINES.md`

The Knowledge List, Knowledge Reading Page, Knowledge Editor, Search Page, Quick Search Overlay and Share Dialog are **approved** reference implementations of the visual language. Reuse their typography, spacing, warm-neutral + petrol/teal palette, dividers, sidebar density, content widths, action hierarchy, control radius, responsive behavior, editable-field affordances, authoring patterns, search surfaces, result-state treatment and overlay interaction patterns rather than inventing a new visual system.

Approved shell rule:

- desktop: persistent sidebar + page content, no global fixed top navbar
- mobile: minimal top application bar + navigation drawer + page content
- page-specific context/actions belong in the page header inside the content area
- route-aware primary navigation may expose destinations such as All notes and Search without introducing a second global navigation system
- navigation destinations and shell commands must remain semantically distinct: `Search` in primary navigation routes to `/search`, while Quick Search is exposed as a shell utility/keyboard accelerator

The current reference UI phase is complete. Future implementation work should preserve the approved interaction and visual language unless a real product/backend constraint requires a documented change.

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
- Crepe `ImageBlock` is enabled only for persisted Knowledge with secure object-storage upload; explicit new-note mode keeps it disabled until a server ID exists. Crepe AI remains disabled until the AI phase has a real provider and product flow
- theme Crepe through its CSS variables and scoped `.knowledge-markdown-editor` overrides so it uses the established warm-neutral + petrol/teal visual system rather than introducing a second product theme

Search responsibility:

- `/search` is the approved full Search route and accepts an initial `q` query parameter
- the sidebar `Search` item is a route-aware navigation destination and must continue to navigate to `/search`
- the broad centered search surface, distinct idle/searching/no-match states, and editorial result rows are approved reference patterns
- search results reuse the approved Knowledge List row treatment rather than introducing search-specific cards
- every result row keeps consistent hover/focus geometry, including the first and last row
- broad active/hover fills remain warm-neutral and restrained; petrol/teal is concentrated in indicators, active text and focus treatment
- keyboard navigation remains first-class: the search field can move focus into results and result links support moving up/down and returning to the search field
- Quick Search and `/search` share the same owner-scoped PostgreSQL Full Text Search semantics through the focused same-origin search boundary
- preserve backend result order; do not introduce client-side relevance ranking or a local fallback when search fails
- keep interactive queries debounced and protect state from stale responses

API integration responsibility:

- keep `KNOWLEDGE_API_BASE_URL` server-only; do not expose it through `NEXT_PUBLIC_*`
- fetch mutable owner Knowledge with dynamic/no-store semantics
- use Server Components for reads and Server Actions for browser-triggered mutations
- map API enums/nullability/timestamps at the typed transport boundary rather than inside visual components
- interactive search may use only the focused same-origin `/api/knowledge-search` Route Handler; do not add a generic catch-all proxy
- image upload/content may use only the focused same-origin attachment Route Handlers; Markdown stores `attachment://<UUID>`, never raw object-storage or presigned URLs
- keep object-storage endpoints, access keys, secret keys and object keys server-only; never introduce `NEXT_PUBLIC_*` storage configuration
- never send `ownerId`, slug or timestamps in create/update requests
- existing-note autosave must remain serialized/coalescing so only one PUT is in flight and the newest dirty draft is eventually persisted
- `/knowledge/new` is an explicit unsaved create mode; do not create placeholder records with permanent placeholder slugs

Authentication responsibility:

- Spring Security and `/api/auth/me` are authoritative; never authorize a workspace route merely because a cookie exists
- protect private pages server-side and send backend `401`/`403` outcomes to `/login`
- forward the incoming HttpOnly session cookie only in server-only API code; never expose it to Client Components
- obtain `/api/auth/csrf` with the same session before every state-changing backend call and use the returned header name/token
- keep OAuth client credentials, allowed owner email and backend locations out of `NEXT_PUBLIC_*` variables
- keep `/api/auth/login`, `/api/auth/logout` and `/api/knowledge-search` focused; do not introduce a generic catch-all proxy
- logout must invalidate the backend session and forward its clearing cookie before returning to `/login`
- existing PUBLIC/UNLISTED visibility does not make an owner workspace route anonymous; `/k/{slug}` and `/s/{shareToken}` use separate external read models

Public reading responsibility:

- `/k/{slug}` is anonymous and must remain outside `requireCurrentUser` and `WorkspaceShell`
- call only the dedicated public backend endpoint through server-only, `no-store` transport; do not forward an owner session or expose the backend URL
- reuse `KnowledgeMarkdown`, `ArticleToc`, heading extraction, read-time and established article typography rather than creating a second rendering pipeline
- render no private sidebar, All notes navigation, Edit/Share controls, Quick Search, owner email or logout action
- PRIVATE, UNLISTED and missing public slugs must share one not-found experience and noindex metadata
- the Share Dialog's `/k/{slug}` is active only after its focused visibility mutation has persisted PUBLIC; never show a link for an unconfirmed local selection
- do not add public listing/Search or merge `/s/{shareToken}` into public direct-slug reading

Unlisted reading responsibility:

- `/s/{shareToken}` is anonymous, dynamic and outside `requireCurrentUser` and `WorkspaceShell`
- fetch only the dedicated shared backend endpoint with `no-store`; never forward the owner session
- reuse the external article presentation shared with `/k`, including Markdown, TOC, anchors, read time and metadata footer
- keep `noindex, nofollow, noarchive`, `Referrer-Policy: no-referrer` and private no-store headers scoped to `/s/**`
- do not reveal token state: invalid, inactive and rotated tokens share one not-found experience
- never put the bearer token into generic Knowledge/Search/Public state or client logs

Quick Search responsibility:

- Quick Search is an approved overlay for fast navigation; `/search` remains the full search/exploration surface
- Quick Search is a shell utility/command, not a primary-navigation destination
- `Cmd/Ctrl + K` is the main desktop accelerator; a compact shell search utility may open it for discoverability, and mobile exposes an equivalent search utility in the top application bar
- do not make the primary sidebar `Search` item open Quick Search; it must navigate to `/search`
- do not bind `/` globally because it conflicts with Markdown/Crepe slash-command authoring
- opening the overlay autofocuses search, locks background scrolling and traps focus; closing restores focus to the previous trigger when it still exists
- Escape and backdrop interaction close the overlay
- Arrow Up/Down traverses quick actions and Enter opens the selected result; pointer hover/focus updates the same selected state
- selecting a knowledge result opens that note directly; do not route an identified result through full Search
- `/search?q=...` is reserved for the explicit `View all results` action when the user wants the full search/exploration surface
- an empty query shows a compact recently-updated list rather than a large empty-state page
- typed queries show at most a small quick-result set plus `View all results`, which transfers the query to `/search?q=...`
- Quick Search uses one restrained layered surface; result hover and keyboard selection reuse the approved Knowledge List/Search row grammar: `--row-hover` for the broad surface and `--accent-strong` for title/action text
- do not use a sidebar-style petrol left indicator or `--active` fill for Quick Search result rows; those semantics belong to active navigation, not knowledge-result hover/selection
- if another control/editor has already consumed `Cmd/Ctrl + K`, Quick Search must not override that handled shortcut

Share responsibility:

- Share Dialog is an approved reference overlay opened from Reading and Editor page-local actions, not a standalone page or sidebar destination
- Reading and Editor reuse the same Share interaction rather than maintaining separate dialog variants
- expected visibility choices are Private, Unlisted and Public; reuse the established lightweight visibility-radio pattern from Editor
- Private has no external link, Unlisted exposes a `/s/{shareToken}` secret-link shape, and Public exposes a `/k/{slug}` public-link shape
- backend UNLISTED tokens are server-generated, secret and persisted; retrieve them only through the focused owner action and rotate them only after inline confirmation
- copy-link belongs beside the read-only URL and uses quiet inline success/failure feedback rather than a large toast
- focus must be trapped while open, initial focus should land on the selected visibility option, background scrolling is locked, and focus returns to the triggering Share action on close
- Escape, explicit close and backdrop interaction close the approved dialog
- use one restrained dialog surface with warm-neutral sections/dividers; do not nest dashboard-style cards inside the dialog
- a restrained backdrop, shadow and small dialog radius are allowed because this is genuinely layered UI
- password-protected sharing is a later enhancement, not part of the approved initial Share Dialog
- Reading, Editor settings and Share stay synchronized with persisted backend visibility; Editor share writes must remain serialized with autosave so stale PUT snapshots cannot revert them

Do not introduce a generic `Card` component unless a future semantic use case explicitly requires a contained surface.

<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->
