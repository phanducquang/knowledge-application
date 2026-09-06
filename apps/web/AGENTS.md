# Web Agent Instructions

Before any UI change, read these repository-level documents in order:

1. `../../docs/DESIGN.md`
2. `../../docs/REFERENCE_SCREENS.md`
3. `../../docs/AI_CODING_GUIDELINES.md`

The Knowledge List and Knowledge Reading Page are **approved** reference implementations of the visual language. Reuse their typography, spacing, warm-neutral + petrol/teal palette, dividers, sidebar density, content widths, action hierarchy, control radius and responsive behavior rather than inventing a new visual system.

Approved shell rule:

- desktop: persistent sidebar + page content, no global fixed top navbar
- mobile: minimal top application bar + navigation drawer + page content
- page-specific context/actions belong in the page header inside the content area

The Knowledge Editor is the current reference screen under review. It must inherit the approved list/reading primitives and may add only editor-specific patterns such as compact formatting controls, save-state feedback and document settings.

Do not introduce a generic `Card` component unless a future semantic use case explicitly requires a contained surface.
