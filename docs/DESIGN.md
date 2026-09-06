# Design Constitution

This document defines the visual language of Knowledge Application. AI coding agents and human contributors must read it before making UI changes.

## 1. Official visual direction

The official design language is **Technical Editorial Workspace** with the following intentional composition:

- **60% Editorial Technical**
- **25% Linear-style productivity**
- **15% Swiss typography / International Typographic Style**

These percentages describe influence, not separate themes. The product must feel like one coherent system.

### 60% Editorial Technical

This is the foundation.

Use it for:

- content hierarchy
- readable knowledge lists and articles
- generous but controlled whitespace
- strong title/body relationships
- dividers instead of floating cards
- calm long-form reading
- technical documentation patterns

The content itself remains the primary visual element.

### 25% Linear-style productivity

Use this influence for interaction and daily-use efficiency:

- compact application chrome
- narrow navigation
- high scanning speed
- subtle hover/selected states
- information density suitable for professional daily use
- keyboard/search-oriented interaction when functionality is implemented
- restrained motion
- responsive behavior that removes unnecessary chrome

Do not copy Linear visually. Borrow the discipline and interaction density.

### 15% Swiss typography

Use this influence selectively to give the product a distinct visual identity:

- disciplined grid and alignment
- strong typography rather than decoration
- small uppercase labels
- deliberate tracking
- numeric/index elements where they carry real information
- asymmetric alignment when it improves hierarchy
- restrained, high-contrast use of the accent color

Do not turn the application into a poster or magazine layout. Swiss influence should sharpen the workspace, not reduce usability.

## 2. Product character

This is a personal technical knowledge workspace used primarily for daily software-development notes and reference material.

The interface should feel like:

- a well-designed technical publication
- a developer notebook
- a precise productivity tool
- a quiet editorial knowledge workspace

It must not feel like:

- a SaaS admin dashboard
- an AI startup landing page
- a generic component-library showcase
- a Notion clone
- a marketing website

## 3. Core visual principles

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

## 4. Color philosophy

The product is **not a black-and-white interface**, but it is intentionally color-restrained.

Pure monochrome can become visually flat in a knowledge workspace, while a highly colorful interface competes with technical content and quickly resembles generic SaaS UI. The preferred solution is a warm neutral foundation with one recognizable product accent and a very small semantic color layer.

Target visual distribution:

- **85–90% warm neutrals** for backgrounds, text, dividers and surfaces
- **8–12% primary accent** for interaction, selection and identity
- **0–3% semantic colors** for actual state/meaning

### Light palette

| Token | Value | Purpose |
| --- | --- | --- |
| Background | `#F7F5F0` | Warm paper-like application background |
| Surface | `#FCFBF7` | Inputs and elevated-but-flat working surfaces |
| Surface muted | `#F1F0EA` | Quiet secondary surface |
| Sidebar | `#EFEEE8` | Navigation separation without a hard panel look |
| Text | `#1D211E` | Primary ink |
| Text muted | `#606861` | Secondary copy |
| Text subtle | `#858C85` | Metadata and tertiary labels |
| Border | `#D9D8D0` | Normal divider |
| Border strong | `#C5C7BF` | Controls and stronger separation |
| Accent | `#2F6863` | Deep petrol/teal product accent |
| Accent strong | `#24534F` | High-emphasis accent text |
| Accent muted | `#557E79` | Low-emphasis metadata/tags |
| Accent soft | `#DDEAE6` | Selected and focused background tint |
| Row hover | `#F1F5F2` | Subtle productivity-style hover feedback |

The deep petrol/teal accent is selected because it feels technical and calm without falling into the common purple/bright-blue AI-startup palette.

### Semantic colors

Semantic colors must communicate actual meaning, never decoration:

- success/public: muted green
- warning/unlisted/attention: muted amber
- destructive/error: muted red

Do not assign different colors to collections or cards merely to make the interface lively.

### Color usage rules

Use the primary accent for:

- active navigation indicator
- primary action
- links and link hover
- focus state
- selection
- meaningful editorial index/count
- collection or metadata emphasis when useful for scanning

Keep ordinary tags low-saturation. Avoid rainbow tags.

Use semantic colors only when a state really has semantic meaning, such as `Public`, `Unlisted`, validation errors, success or destructive actions.

## 5. Anti-patterns

Do not use by default:

- gradient backgrounds
- glassmorphism
- decorative blobs
- oversized rounded cards
- cards for every section
- KPI/dashboard cards
- excessive shadows
- excessive border radius
- purple/bright-blue AI-startup visual language
- giant marketing headings
- floating containers without semantic purpose
- icons beside every label
- unnecessary badges and pills
- rainbow tag systems

Avoid excessive use of Tailwind patterns such as:

- `rounded-xl`
- `rounded-2xl`
- `shadow-lg`
- `shadow-xl`
- `bg-gradient-*`

A component must not become a card merely because it contains multiple pieces of information.

## 6. Layout

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

## 7. Typography

Typography should create most of the hierarchy.

Preferred direction:

- interface/content sans: Geist or the established project sans-serif
- code: Geist Mono, JetBrains Mono, or the established project monospace

Approximate hierarchy:

- article/page title: 30–34px, medium/semibold
- H1: 24–28px
- H2: 19–22px
- H3: 16–18px
- body: 16px with roughly 1.65–1.75 line-height
- navigation: 14px
- metadata: 12–13px
- code: 13–14px

Swiss influence may use small uppercase labels with moderate tracking and numeric/tabular data where meaningful. Do not uppercase body copy or ordinary navigation merely for style.

Do not use bold weight everywhere.

## 8. Spacing

Use a deliberate spacing scale primarily based on:

```text
4, 8, 12, 16, 24, 32, 48, 64px
```

Avoid arbitrary spacing unless alignment requires it.

Editorial whitespace must not become marketing-page emptiness. Daily-use density still matters.

## 9. Borders, radius and shadows

### Borders

- subtle 1px neutral borders
- prefer dividers between list items over fully boxed items
- accent-colored borders are reserved for selection/focus or meaningful emphasis

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

## 10. Knowledge lists

Knowledge lists should usually be editorial lists separated by dividers, not grids of floating cards.

A list item may contain:

- title
- concise description
- collection/tags
- visibility
- updated date

Scanning speed matters more than visual decoration.

Linear-style influence may appear as a very subtle full-row hover background, but the item must still read as a list row rather than a card.

## 11. Sidebar

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

Active states should use a thin accent indicator and a soft accent tint. Avoid oversized pill navigation.

## 12. Editor

The writing surface is the focus.

- minimize permanent toolbar chrome
- formatting controls should be compact
- contextual controls are preferred where practical
- title/content hierarchy should feel editorial

Avoid a giant toolbar containing every possible feature.

## 13. Public reading page

Public knowledge pages should look like technical editorial pages, not marketing landing pages.

Avoid:

- hero marketing sections
- gradient headers
- newsletter blocks by default
- multiple CTA panels
- large card grids of related content

Public and private experiences should share typography, spacing, colors and component DNA.

## 14. Reusable primitives

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

## 15. Dark mode

Dark mode is not yet an approved reference theme. Do not automatically invert the light palette or independently invent a dark palette.

When dark mode is implemented, it must preserve the same warm-neutral hierarchy, restrained accent usage and readability goals, and it should be reviewed as a separate reference state.

## 16. AI-generated UI review rule

After implementing a UI, explicitly review it for generic AI-generated SaaS patterns.

Ask:

- Did we add a card where a divider/list would work better?
- Is radius being used excessively?
- Are icons decorative rather than informative?
- Is there unnecessary color?
- Is the interface so monochrome that hierarchy/identity feels flat?
- Is accent color being used for meaning rather than decoration?
- Is spacing oversized like a marketing page?
- Is typography strong enough to carry hierarchy without decoration?
- Does mobile feel intentionally designed?
- Does the result still reflect the 60/25/15 design direction?

Remove generic patterns before considering the UI complete.
