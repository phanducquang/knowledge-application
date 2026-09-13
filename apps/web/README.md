# Web Application

Next.js application for the Knowledge Application workspace.

## Stack

- Next.js 16.3.4
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
- safe GFM rendering, dynamic H2-H4 table of contents and calculated read time
- Full Search and typed Quick Search consume the same backend-ranked PostgreSQL search through a focused same-origin Route Handler
- empty Quick Search continues to show recently updated owner Knowledge
- server-side private-route protection backed by Spring Security `/api/auth/me`
- focused Google login/logout routes, HttpOnly session forwarding and CSRF-aware Server Actions
- anonymous, dynamic `/k/{slug}` Reading Page for already-persisted PUBLIC Knowledge
- anonymous, dynamic `/s/{shareToken}` Reading Page backed by the dedicated UNLISTED bearer-token endpoint
- one persisted Share Dialog shared by Reading/Edit, with real PUBLIC links and backend-managed UNLISTED link retrieval/confirmed rotation
- protected `/knowledge/{slug}/history` with paginated checkpoints, selected full-Markdown preview and confirmed restore
- persisted Crepe Edit mode with secure image upload and stable `attachment://<UUID>` references rendered in owner, PUBLIC, UNLISTED and History contexts
- shared fenced-code syntax highlighting for private, PUBLIC, UNLISTED and History rendering

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

Every backend read forwards the incoming session cookie on the Next.js server. `POST`/`PUT`/`PATCH` Server Actions first fetch `/api/auth/csrf` using the same cookie and then attach the returned header/token. Client Components do not read the HttpOnly session or receive OAuth secrets.

Interactive search calls the same-origin `/api/knowledge-search` BFF route. It is intentionally narrow: the Spring Boot base URL remains server-only, typed searches are debounced and bounded, stale requests are aborted/versioned, and API failures show an error state rather than falling back to browser-side ranking.
The BFF forwards the incoming authenticated session and returns `401` rather than bypassing backend security when the session is absent.

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

## Validate

```bash
npm test
npm run lint
npm run build
```

## Reference-screen rule

The approved screens intentionally use typography, alignment, whitespace and dividers instead of a card-based dashboard layout. Integration work must preserve these established patterns unless the design documentation is explicitly changed.
