# UI/UX review and implementation — 2026-10-28

## Scope and design decisions

Review the existing Knowledge Application using Hallmark v2 and UI/UX Pro Max.
The approved Technical Editorial Workspace in `DESIGN.md` remains the locked
visual system: warm neutrals, petrol/teal interaction, editorial rows, compact
sidebar and flat reading/writing surfaces. This is an in-place polish, not a
replacement component tree. No routes, product capabilities or business rules
are added or removed. No backend, API transport, persistence, search ranking,
AI invocation, visibility or authentication code is changed.

Existing reference screens supply the material and audience: a personal
technical notebook for daily engineering work. No photography, synthetic
metrics, testimonials or marketing sections are appropriate.

The existing untracked `.agents/` and `apps/api/bin/` directories are left alone.

## Audit and implementation plan

| Priority | Existing surfaces | Finding | Planned correction |
| --- | --- | --- | --- |
| P1 | Shared shell, mobile drawer | Missing focus containment, Escape and restoration; no skip link | Make existing navigation keyboard accessible, isolate background, restore focus, add skip link |
| P1 | Shared controls and content | Inconsistent focus treatment, pale small metadata and field borders | Shared instant focus ring, readable semantic tokens, pressed state and disabled cursor |
| P1 | Editor, collection forms, Ask, Quick Search | Labels/errors not consistently associated with controls | Accessible names, described errors, status announcements and keyboard navigation for existing controls |
| P1 | Share and overlays | Short viewports can clip dialog controls | Viewport-bound scrolling, untabbable backdrop and usable targets |
| P2 | All existing pages and reference screens | Repeated 10–11px tracked uppercase labels | Sentence-case labels at 12px, consistent with Hallmark; preserve content and hierarchy |
| P2 | Reading, lists, search, history, Graph | Long technical titles/queries can overflow, narrow action rows | Wrapping and responsive action geometry; preserve readable article width and horizontal code/table scrolling |
| P2 | Mobile forms/navigation | Tiny targets and text fields can be uncomfortable | Larger hit areas and 16px mobile text-entry controls without widening desktop chrome |
| P3 | Verification | Private pages need authenticated data to render | Local synthetic backend only; desktop/mobile captures, keyboard and existing-state checks |

## Files and boundaries

Edit `apps/web/src/app/globals.css`, existing page presentation and existing
components under layout, knowledge, search, collections, ask and graph.
Create this review record and `.hallmark/preflight.json`/run records. Delete no
files. Keep all routes, links, form fields, callbacks and request boundaries.
Use existing Geist fonts, palette and reusable primitives. The already-installed
CodeMirror language and Lezer highlight packages are explicitly declared at
their existing versions to configure the existing editor's syntax colors through
public APIs. No package version is upgraded and the lockfile changes only the
two direct dependency declarations. Browser QA tooling lives only in `/private/tmp`.

Hallmark's label rule supersedes the optional uppercase micro-label treatment
in the earlier references for this review. Dark mode remains outside scope
because it is not an existing approved capability. Placeholder `#` destinations
in the sidebar are not implemented as new features by this review.

## Verification record

Initial static Hallmark scan: **67 FAIL, 1 REFLEX, 4 WARN** across 44 source
files. Findings were chiefly label typography, eyebrow styling, missing root
horizontal clipping, literal shadow values and Quick Search metadata punctuation.
The first render attempt could not run because Puppeteer was absent; browser
tooling was subsequently installed in `/private/tmp`, outside app dependencies.

## Completed implementation and validation — 2026-10-29

All planned UI surfaces were reviewed and updated. No business feature was added.
The final diff changes no backend, transport/API, hooks, persistence actions,
domain types, search ranking, AI request rules or existing tests. No route or
component tree is removed. The editor still uses Milkdown + Crepe and CodeMirror;
private/public/shared/history reading still uses the single Markdown renderer.

Additional browser findings were corrected: readable CodeMirror token colors on
the light surface; actual Geist/Geist Mono token resolution; an offset sticky
editor toolbar; keyboard activation and accessible names for existing formatting
buttons; semantics for generated editor lists/table controls; keyboard-scrollable
code/tables; hidden table handles; and Graph title clamping that previously lost
to an explicit `block` utility.

| Check | Result |
| --- | --- |
| `npm test` | 97 passed, 0 failed; no tests modified |
| `npm run lint` | Passed |
| `npm run build` | Passed; production build and TypeScript completed |
| Hallmark static + `--kept` | 0 FAIL, 0 REFLEX, 0 WARN across 44 source files; 21/21 recorded links, 3/3 fields and 91/91 original handlers retained |
| Browser responsive matrix | 14 routes × 6 widths (320, 375, 768, 1280, 1440, 1920): 84 checks, no page overflow, missing/duplicate main or JavaScript exception; final focused checks cover subsequent code-scroll and Graph fixes |
| axe WCAG scan | 14 pages/states including Editor, History, Reading, Graph, mobile drawer, Quick Search, Share and Login: 0 violations |
| Drawer / Share | Focus containment, Escape, focus restoration checked; Share stays within a 667×375 viewport and scrolls |
| Editor | Keyboard Bold activates; initial mount makes 0 mutation requests; computed reading and CodeMirror fonts resolve to Geist Mono |
| Independent Hallmark finish review | One typography finding, fixed and scored resolved |

The final Hallmark render generated 35 captures over seven screens. It reports
one FAIL (73) and one REFLEX (72) on Graph because its text `Range` measurement
includes lines hidden by CSS two-line clamping, treating those invisible lines
as painted beneath the collection label. These are recorded rather than presented
as a clean automated render pass: screenshots show the correct clamp, and
`graph-verification.json` checks the visible title/metadata bounds at mobile and
desktop widths. Article-title wrapping is explicitly waived under 49b because
titles are preserved content, not footer/tab/breadcrumb labels.

Local review artifacts:

- `.hallmark/compare.html`: before/after desktop and mobile comparison
- `.hallmark/browser-review.json`: responsive and drawer/landscape results
- `.hallmark/accessibility-review.json`: final axe results
- `.hallmark/editor-verification.json`: computed fonts, keyboard and mount checks
- `.hallmark/graph-verification.json`: visible Graph node bounds
- `.hallmark/before/`, `.hallmark/shots/`, `.hallmark/review/`: synthetic-data captures

Copy facts are preserved. Quick Search's trailing arrow was removed, and keyboard
hint separators were replaced with spacing; Ask keyboard help is displayed in
separate spans. Labels keep their words and now use sentence case instead of CSS
uppercase/tracking. No privacy, consent or provider disclaimer was rewritten.

The checks use a loopback synthetic backend, not a real OAuth session, production
database, object storage or Gemini. CI was not run because this work was not
committed or pushed. Existing sidebar `#` placeholders and unapproved dark mode
remain outside the task; no functionality was inferred for them.
