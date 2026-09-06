# Design Constitution

This document defines the visual language of Knowledge Application. AI coding agents and human contributors must read it before making UI changes.

## 1. Product character

This is a personal technical knowledge workspace used primarily for daily software-development notes and reference material.

The interface should feel like:

- a well-designed technical publication
- a developer notebook
- a quiet professional writing tool
- an editorial knowledge workspace

It must not feel like:

- a SaaS admin dashboard
- an AI startup landing page
- a generic component-library showcase
- a Notion clone
- a marketing website

The content itself is the primary visual element.

## 2. Core visual principles

Prefer:

- **content over chrome**
- **typography over decoration**
- **alignment over containers**
- **whitespace over cards**
- **dividers over cards**
- **borders over shadows**
- **hierarchy over excessive color**
- **neutral surfaces over decorative surfaces**
- useful information density suitable for daily professional use

When choosing between a decorative solution and a typographic solution, choose the typographic solution.

## 3. Anti-patterns

Do not use by default:

- gradient backgrounds
- glassmorphism
- decorative blobs
- oversized rounded cards
- cards for every section
- KPI/dashboard cards
- excessive shadows
- excessive border radius
- purple/blue AI-startup visual language
- giant marketing headings
- floating containers without semantic purpose
- icons beside every label
- unnecessary badges and pills

Avoid excessive use of Tailwind patterns such as:

- `rounded-xl`
- `rounded-2xl`
- `shadow-lg`
- `shadow-xl`
- `bg-gradient-*`

A component must not become a card merely because it contains multiple pieces of information.

## 4. Layout

### Desktop workspace

- persistent, relatively narrow navigation sidebar
- main reading/writing area
- clear alignment grid
- compact application chrome
- content takes visual priority

Do not center the entire application like a landing page.

### Article content

Target readable article width around 720–780px.

Article sections normally have:

- no card background
- no shadow
- no rounded outer container

### Mobile

Design mobile behavior intentionally rather than shrinking the desktop layout.

- navigation may become a drawer/sheet
- reading width becomes naturally fluid
- primary actions remain reachable
- unnecessary chrome is reduced

## 5. Typography

Typography should create most of the hierarchy.

Preferred direction:

- Interface/content sans: Geist or the established project sans-serif
- Code: JetBrains Mono or the established project monospace

Approximate hierarchy:

- article title: 30–34px, medium/semibold
- H1: 24–28px
- H2: 19–22px
- H3: 16–18px
- body: 16px with roughly 1.65–1.75 line-height
- navigation: 14px
- metadata: 12–13px
- code: 13–14px

Do not use bold weight everywhere.

## 6. Spacing

Use a deliberate spacing scale primarily based on:

```text
4, 8, 12, 16, 24, 32, 48, 64px
```

Avoid arbitrary spacing unless alignment requires it.

## 7. Borders, radius and shadows

### Borders

- subtle 1px neutral borders
- prefer dividers between list items over fully boxed items

### Radius

- buttons/inputs: approximately 4–6px
- menus/popovers: approximately 6px
- dialogs: approximately 8px maximum unless a specific design decision says otherwise

### Shadows

Avoid shadows in normal page content.

Use shadows only for truly layered UI such as:

- dropdowns
- popovers
- dialogs

## 8. Color

Use a predominantly neutral palette.

Color communicates:

- interaction
- selection
- state
- meaning

Color should not be added simply to make a page look more lively.

Use one main accent color for things such as:

- links
- primary actions
- selected states
- focus states

Avoid assigning unrelated decorative colors to sections/cards.

## 9. Knowledge lists

Knowledge lists should usually be editorial lists separated by dividers, not grids of floating cards.

A list item may contain:

- title
- concise description
- collection/tags
- updated date

Scanning speed matters more than visual decoration.

## 10. Sidebar

Sidebar should resemble a notebook/document navigator rather than an admin dashboard.

Representative hierarchy:

```text
Knowledge

All notes

Collections
  Backend
  Database
  DevOps

Tags

Private
Shared
Drafts

New note
```

Active states should be subtle. Avoid oversized pill navigation.

## 11. Editor

The writing surface is the focus.

- minimize permanent toolbar chrome
- formatting controls should be compact
- contextual controls are preferred where practical
- title/content hierarchy should feel editorial

Avoid a giant toolbar containing every possible feature.

## 12. Public reading page

Public knowledge pages should look like technical editorial pages, not marketing landing pages.

Avoid:

- hero marketing sections
- gradient headers
- newsletter blocks by default
- multiple CTA panels
- large card grids of related content

Public and private experiences should share typography, spacing, colors and component DNA.

## 13. Reusable primitives

Prefer a small set of reusable primitives such as:

- Button
- Input
- SearchInput
- SidebarItem
- Tag
- Divider
- Dropdown
- Dialog

Do not create a generic `Card` abstraction unless there is a semantic contained-surface use case that actually needs it.

## 14. AI-generated UI review rule

After implementing a UI, explicitly review it for generic AI-generated SaaS patterns.

Ask:

- Did we add a card where a divider/list would work better?
- Is radius being used excessively?
- Are icons decorative rather than informative?
- Is there unnecessary color?
- Is spacing oversized like a marketing page?
- Is typography strong enough to carry hierarchy without decoration?
- Does mobile feel intentionally designed?

Remove generic patterns before considering the UI complete.
