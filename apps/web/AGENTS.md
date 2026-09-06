# Web Agent Instructions

Before any UI change, read these repository-level documents in order:

1. `../../docs/DESIGN.md`
2. `../../docs/REFERENCE_SCREENS.md`
3. `../../docs/AI_CODING_GUIDELINES.md`

The Knowledge List page is an **approved** reference implementation of the visual language. Reuse its typography, spacing, warm-neutral + petrol palette, dividers, sidebar density, control radius and responsive behavior rather than inventing a new visual system.

Approved shell rule:

- desktop: persistent sidebar + page content, no global fixed top navbar
- mobile: minimal top application bar + navigation drawer + page content
- page-specific context/actions belong in the page header inside the content area

The Knowledge Reading Page is the next reference screen and may add only reading-specific primitives such as article typography, code blocks, tables, quotes and table-of-contents behavior.

Do not introduce a generic `Card` component unless a future semantic use case explicitly requires a contained surface.
