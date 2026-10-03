# Web Application

Next.js application for the Knowledge Application workspace.

## Stack

- Next.js 16.3.8
- React 19.2.8
- TypeScript
- App Router
- Tailwind CSS 4.3.3
- React Markdown with GitHub-flavored Markdown
- ESLint

## Current scope

- approved Knowledge List, Reading, Editor, Search, Quick Search and Share reference UI preserved
- server-rendered Knowledge List and Reading routes backed by Spring Boot/PostgreSQL
- explicit `/knowledge/new` creation flow through a Server Action
- persisted Edit initialization plus serialized, coalescing 700 ms autosave
- visibility, collection and tags persist with full replacement semantics
- owner-scoped `/collections` management with create, rename and delete; sidebar counts include empty collections, and `/collections/{id}` filters notes without changing the approved reading/editor layout
- owner Reading resolves `[[stable-slug]]` prose references to owned notes and lists backlinks below the article; unresolved references remain literal, and external PUBLIC/UNLISTED readers never receive owner-only navigation
- owner Reading adds a flat Related notes list after Backlinks, with current titles/summaries and relation reasons; empty lists are omitted. The no-store owner API supplies up to five notes ranked by wiki/backlink, shared persisted tags and actual Collection, without AI/embeddings or a dedicated relationship index. External PUBLIC/UNLISTED readers never load or render it
- private `/graph` with every current owned note (isolated notes included), directed canonical wiki edges, Collection filtering, pan/zoom/fit, click-to-read and direct-neighbor focus; no PUBLIC/UNLISTED graph
- safe GFM rendering, dynamic H2-H4 table of contents and calculated read time
- Full Search and typed Quick Search consume the same backend-ranked PostgreSQL search through a focused same-origin Route Handler
- empty Quick Search continues to show recently updated owner Knowledge
- `/search` offers default live Keyword FTS and explicit-submit Semantic search, with current best-chunk context; Semantic is owner-only and requires configured backend embeddings
- server-side private-route protection backed by Spring Security `/api/auth/me`
- focused Google login/logout routes, HttpOnly session forwarding and CSRF-aware Server Actions
- anonymous, dynamic `/k/{slug}` Reading Page for already-persisted PUBLIC Knowledge
- anonymous, dynamic `/s/{shareToken}` Reading Page backed by the dedicated UNLISTED bearer-token endpoint
- one persisted Share Dialog shared by Reading/Edit, with real PUBLIC links and backend-managed UNLISTED link retrieval/confirmed rotation
- protected `/knowledge/{slug}/history` with paginated checkpoints, selected full-Markdown preview and confirmed restore
- persisted Crepe Edit mode with secure image upload and stable `attachment://<UUID>` references rendered in owner, PUBLIC, UNLISTED and History contexts
- shared fenced-code syntax highlighting for private, PUBLIC, UNLISTED and History rendering
- lazy browser-side Mermaid rendering for explicit `mermaid` fences through the same shared Markdown renderer

Before implementing or modifying UI, read:

1. `../../docs/DESIGN.md`
2. `../../docs/REFERENCE_SCREENS.md`
3. `../../docs/AI_CODING_GUIDELINES.md`
4. `AGENTS.md`

## Run locally

```bash
npm install
npm run dev
```

Then open `http://localhost:3000`.

The Spring Boot API must be available at `http://localhost:8080` by default. Override it only on the Next.js server:

```text
KNOWLEDGE_API_BASE_URL=http://localhost:8080
KNOWLEDGE_API_BROWSER_BASE_URL=http://localhost:8080
```

Do not use a `NEXT_PUBLIC_*` variable for either backend URL or for Google credentials. `KNOWLEDGE_API_BASE_URL` is the origin reachable by the Next.js server; `KNOWLEDGE_API_BROWSER_BASE_URL` is used only by the focused login Route Handler to redirect the browser into Spring Security. They are normally identical on localhost. Knowledge pages use dynamic rendering and `no-store` API reads.

Open `http://localhost:3000`. Unauthenticated workspace requests are resolved against the authoritative backend session and redirect to `/login`. The browser enters Google login through `/api/auth/login`; after an authorized login, Spring redirects to `/`. The sidebar posts logout through `/api/auth/logout`, which obtains a CSRF token server-side, invalidates the backend session and forwards the clearing cookie before redirecting to `/login`.

Every backend read forwards the incoming session cookie on the Next.js server. `POST`/`PUT`/`PATCH`/`DELETE` Server Actions first fetch `/api/auth/csrf` using the same cookie and then attach the returned header/token. Client Components do not read the HttpOnly session or receive OAuth secrets.

Collection summaries come from the backend independently of the current note list. This keeps empty collections visible in the sidebar and selection controls. The management page confirms deletion inline; deleting a Collection unfiles its notes rather than deleting them. Collection routes and the workspace shell remain private and server-rendered.

Interactive search calls the same-origin `/api/knowledge-search` BFF route. It is intentionally narrow: the Spring Boot base URL remains server-only, typed searches are debounced and bounded, stale requests are aborted/versioned, and API failures show an error state rather than falling back to browser-side ranking.
The BFF forwards the incoming authenticated session and returns `401` rather than bypassing backend security when the session is absent.

## Semantic Search

`/search?q=redis` and absent/invalid `mode` remain Keyword, including the unchanged 180ms debounce and backend order. `/search?mode=semantic&q=cache+invalidation` executes only one initial server semantic query. The compact Keyword/Semantic selector stays in the approved Search workspace; Quick Search is unchanged FTS.

Semantic typing/mode switching never sends a provider request. Enter/Search explicitly submits; draft text is separate from submitted text, and the URL updates only on submit. Duplicate in-flight submits are suppressed; a different submit aborts/ignores stale browser responses without assuming the upstream provider was canceled. No automatic retry or silent Keyword fallback is performed.

The focused `/api/knowledge-semantic-search` BFF uses server-only cookie forwarding, no-store and the existing backend URL boundary; read-only GET needs no CSRF. Results reuse editorial rows/navigation and display `Matched context` as escaped plain text, never rendered Markdown/HTML. Disabled, temporarily unavailable, loading and empty/not-indexed states offer explicit Keyword alternatives; provider failures do not affect stored notes. Query text is sent to the configured provider and may appear in protected operational URL logs. No raw vector/model/distance/key is exposed; no hybrid rank, anonymous semantic search or ANN is added.

## Ask My Knowledge

`/ask` is a protected dynamic/no-store workspace page using the existing sidebar/mobile navigation. One multiline Question and explicit Ask button or Cmd/Ctrl + Enter submit; ordinary Enter adds a line. Mounting, typing, navigation and changing a draft never call AI. Active duplicate submits are suppressed; Cancel view/unmount abort browser requests and stale responses cannot overwrite current state. Upstream work may already have happened, so cancel does not promise a quota refund.

The question-only `/api/ask-my-knowledge` POST BFF bounds JSON at 16 KiB, trims/max-validates 2000 characters, rejects unknown fields/cross-origin requests and delegates only to Spring `/api/ask`. Session cookie and CSRF header are forwarded by existing server-only transport. Origin is checked against incoming `Host` (Next's internal request URL may differ); deployment proxies must preserve the validated external Host. The backend URL, Gemini key/model/context and vectors are never browser configuration. Questions stay out of URLs/storage; no conversations/history are saved.

Answer Markdown allows only simple paragraphs/lists/emphasis/code: raw HTML/images/active arbitrary links and Mermaid are not rendered/executed. Only structured validated source slugs link to `/knowledge/{slug}`; excerpts are plain text. Sources describe context notes, not verified per-claim citations. Loading, no-context, disabled, retrieval error, generation error and explicit retry retain the editorial layout and question draft. A privacy warning explains Gemini receives private text and Free Tier data handling.

Enable optional embedding and generation in the API environment only; both default off. See [Ask configuration/privacy/quotas](../../docs/ASK_MY_KNOWLEDGE.md). Web tests use fake transports; browser QA uses a synthetic loopback backend, not Gemini or a production OAuth session.

## Knowledge Graph

`/graph` is a protected Server Component route. Its server-only, no-store transport fetches compact current Knowledge nodes and explicit directed wiki edges, then hands the DTO to a focused graph Client Component. Graph is available in the existing desktop/mobile sidebar; unrelated Library/Reading routes remain server-rendered.

React Flow `12.12.0` supplies pan, pinch/button zoom, fit view, directed arrows and node interaction; Dagre `3.1.1` supplies deterministic presentation-only layout. No physics engine, persisted positions, graph editing, edge creation, database migration, relationship index, AI or embeddings is added. Collection/Tags are node metadata only, not nodes or edge signals. Tags remain available in each node's tooltip; Related Articles keeps its existing ranking unchanged.

The Collection control filters by actual Collection ID, retaining only edges with both endpoints visible. Empty collections are selectable and show a restrained empty state. Unfiled notes are included in All Collections and are not related by null membership. Focus note highlights one node, its direct incoming/outgoing neighbors and incident edges, then fits them into view; unrelated nodes/edges are dimmed. Clear focus restores the full view. `/graph?focus=stable-slug` initializes focus; unknown/non-visible slugs do not reveal additional nodes. Node titles link to `/knowledge/{slug}`; a body click also navigates. The fixed-height responsive viewport and wrapping controls preserve the existing mobile drawer.

The graph includes isolated notes even when the entire library has no edges; an empty library omits the canvas and shows quiet guidance. Knowledge and Collection mutations revalidate `/graph`, so the next route read reflects edits, renames, deletion and revision restore. No external PUBLIC/UNLISTED route fetches or renders graph data.

Graph validation includes Node tests for filters, layout, focus, real React Flow node/edge rendering, navigation and private transport wiring. Browser QA was not executed for this milestone because browser tooling was unavailable; pan/zoom/touch and narrow-screen interaction still need a browser smoke check when tooling is enabled.

## Dependency security maintenance

The 2026-10-03 audit reported eight vulnerable package entries (one critical, six high, one low), including transitive tooling parents. Minimal compatible patches were applied without changing React/ReactDOM `19.2.8`, graph dependencies or application behavior:

| Dependency / path | Before → after | Advisory / scope |
| --- | --- | --- |
| `next`; paired `eslint-config-next` | `16.3.4` → `16.3.8` | [GHSA-vcvr-r3jv-pc5j](https://github.com/advisories/GHSA-vcvr-r3jv-pc5j), critical runtime; affected Next `>=16.2.0 <16.3.6`, patched from `16.3.6` |
| Mermaid / Milkdown → `dompurify` | `3.4.15` → `3.4.16` | [GHSA-p98j-92pf-mc4p](https://github.com/advisories/GHSA-p98j-92pf-mc4p), low runtime; affected `3.4.13–3.4.15`, patched `3.4.16` |
| ESLint / typescript-eslint → minimatch → `brace-expansion` | `1.1.18` / `5.0.9` → `1.1.21` / `5.0.12` | Dev-only DoS: [GHSA-q2hr-2g5m-vwhr](https://github.com/advisories/GHSA-q2hr-2g5m-vwhr) (moderate), [GHSA-qhr7-859c-m2p7](https://github.com/advisories/GHSA-qhr7-859c-m2p7) and [GHSA-6j4f-fj2g-mc7p](https://github.com/advisories/GHSA-6j4f-fj2g-mc7p) (high); both installed branches now patched |

`npm audit --omit=dev` now reports **zero runtime vulnerabilities**. Full `npm audit` still reports **five high dev-only entries**, all from the single unpatched [GHSA-vfj7-8cjw-p6xm / CVE-2026-93687](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm): `eslint-config-next → @next/eslint-plugin-next → fast-glob → micromatch → braces@3.0.3`. All published braces versions through `3.0.3` are affected and no patched version is available. npm's proposed force fix downgrades Next's ESLint configuration to `14.2.35`; this breaking downgrade was deliberately not applied. This path handles tooling file patterns, not runtime Knowledge input; monitor upstream for a compatible patch.

The application does not use `next/og` Node `ImageResponse`, `next/image`, custom image loaders or `remotePatterns`. Attachment delivery forwards raw access-scoped backend responses; it does not invoke Next image generation/optimization. The framework was patched despite non-use of that advisory's affected surface. Application code defines no DOMPurify `IN_PLACE`/node-removal hooks, but Mermaid and Milkdown use DOMPurify transitively and received the patch. Owner session/CSRF forwarding, anonymous PUBLIC/UNLISTED reads, no-store behavior and the production webpack build remain unchanged.

## Public reading

`/k/{slug}` is a Server Component route outside the private workspace guard. It calls only the dedicated `/api/public/knowledge/{slug}` backend endpoint, sends no owner session, and uses `no-store` so revoking PUBLIC visibility takes effect immediately. The page reuses the approved Markdown renderer, H2–H4 TOC, read-time and article typography, but renders no sidebar, Edit/Share action, Quick Search, owner email or logout control.

Valid public articles generate title/summary metadata with `index,follow`. PRIVATE, UNLISTED and missing slugs all render the same public not-found state with `noindex`. No public listing, Search or client-side backend URL is introduced.

## Unlisted reading

`/s/{shareToken}` is also outside the workspace guard and uses the same external article presentation as `/k`, including Markdown, H2–H4 TOC, read time, collection and tags. It calls only `GET /api/shared/knowledge/{shareToken}` through the server-only anonymous client and does not forward owner cookies, so the result is identical for logged-in and logged-out viewers.

The route is dynamic and non-cacheable. Route-specific headers set `Cache-Control: private, no-store, max-age=0`, `Referrer-Policy: no-referrer` and `X-Robots-Tag: noindex, nofollow, noarchive`; metadata carries the same robots prohibition. It has no WorkspaceShell, private navigation, Edit/Share action, Search, owner identity or logout control.

The approved Share Dialog is used by both Reading and Edit. Visibility changes are persisted through the focused owner-scoped PATCH before a link is shown. Opening an UNLISTED note retrieves its existing link and never rotates it; explicit regeneration uses an inline two-step confirmation, and a failed request keeps the old link visible. Tokens exist only in dialog memory and are not placed in generic Knowledge state or browser storage.

On Edit, the sharing coordinator waits for an in-flight autosave, flushes pending draft fields, and then applies the visibility PATCH. Autosave remains held during that sequence and any later PUT contains the newly confirmed visibility, preventing stale snapshots from reverting the share decision. Reading applies the same mutation and refreshes its server-rendered metadata after success.

## Revision history

History is a secondary action from Reading and Edit. The editor flushes pending autosave work before leaving for `/knowledge/{slug}/history`. The server-rendered route loads only compact newest-first rows for the list and full Markdown for the selected preview; “Load older revisions” requests the next bounded page.

Restore uses an inline confirmation rather than a modal. Its Server Action obtains CSRF in the same way as other mutations and then returns to Edit. The API preserves slug and current sharing state, so public or unlisted routes continue to use the same URL/token with the restored authoring content.

Multiple editor tabs still use last-write-wins autosave. History can help recover an overwritten authoring state, but it does not detect conflicts or merge concurrent drafts.

## Image attachments

Crepe ImageBlock is enabled only when editing an already-persisted Knowledge item. Create mode has no server Knowledge ID, so it does not advertise image upload. The client posts multipart data only to the focused same-origin `/api/knowledge/{knowledgeId}/attachments/images` Route Handler; server-only transport forwards the authenticated session and obtains CSRF before calling Spring Boot.

Markdown stores `attachment://<UUID>`, never a MinIO/R2 URL or temporary signature. Crepe `proxyDomURL` and the shared Markdown renderer resolve that stable reference to the appropriate same-origin owner, PUBLIC or UNLISTED image route. Revision History uses the owner context, so an old revision can render the same retained object. Object-storage endpoints and credentials remain backend-only and must never use `NEXT_PUBLIC_*`.

The frontend intentionally does not implement generic files, attachment browsing/deletion, image processing or direct object-storage access. Upload/stream validation and authorization remain authoritative in Spring Boot.

## Syntax highlighting

`KnowledgeMarkdown` remains the only persisted-Markdown renderer. It uses lowlight 3.3/highlight.js 11.11 with a controlled 16-grammar bundle and converts the resulting HAST text/span nodes directly to React. It does not inject generated HTML, enable raw Markdown HTML, auto-detect languages or execute code.

The initial set covers Java, JavaScript/JSX, TypeScript/TSX, JSON, YAML, SQL, Bash/shell, Python, HTML/XML, CSS, Markdown, Kotlin, Dockerfile, properties/INI, Gradle and Nginx. Common aliases such as `js`, `ts`, `sh`, `shell`, `yml`, `html`, `properties`, `docker`, `jsx` and `tsx` normalize to those grammars. Unknown and missing language identifiers render as plain fenced code without failing; inline code retains its compact existing style.

History is a Client Component and imports `KnowledgeMarkdown`, so the highlighter is intentionally compatible with both client and server rendering rather than server-only. Private, PUBLIC and UNLISTED routes still server-render their initial article output through that same component. Only the selected grammars enter the client graph; the editor keeps Crepe's existing CodeMirror behavior and canonical fenced Markdown unchanged.

## Mermaid diagrams

`KnowledgeCodeBlock` intercepts only fences explicitly labelled `mermaid` before the normal lowlight path and delegates them to a focused Client Component. The component dynamically imports Mermaid 11.17.2 on mount, so the Mermaid runtime and individual diagram definitions are not part of the ordinary article's initial route payload. Private, PUBLIC, UNLISTED and History rendering all inherit this behavior from `KnowledgeMarkdown`; no route owns a separate diagram pipeline.

Mermaid is initialized once per browser runtime with `securityLevel: "strict"`, `htmlLabels: false`, `startOnLoad: false`, disabled Mermaid error rendering, bounded text/edge limits and a restrained application-aligned base theme. Click behavior is not enabled. The SVG string returned by the official local renderer is inserted only inside the dedicated diagram component; arbitrary raw Markdown HTML remains disabled and `rehype-raw` is not used. No source or generated output is sent to or persisted by a remote rendering service.

Each component derives an opaque render ID from React `useId()` plus a render attempt, never from article data or source. Source changes clear the old output and use an attempt guard so stale asynchronous results cannot replace the latest diagram. Invalid syntax shows a quiet error plus the unchanged Mermaid source. Loading is local to the diagram, and the scoped container prevents page-width overflow on narrow screens.

The editor remains canonical fenced Markdown with Crepe's existing CodeMirror behavior; there is no visual diagram editor or live preview. Mermaid supports the diagram types provided by the installed official package; flowchart, sequence and class diagrams are the representative verified types. Diagram links, export, copy, zoom/pan and server-side SVG persistence are intentionally deferred.

## Validate

```bash
npm test
npm run lint
npm run build
```

## Reference-screen rule

The approved screens intentionally use typography, alignment, whitespace and dividers instead of a card-based dashboard layout. Integration work must preserve these established patterns unless the design documentation is explicitly changed.
