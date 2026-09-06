# Web Application

Next.js reference implementation for the Knowledge Application frontend.

## Stack

- Next.js 16.3.4
- React 19.2.8
- TypeScript
- App Router
- Tailwind CSS 4.3.3
- ESLint

## Current scope

Only the first visual reference screen is implemented:

- Knowledge List
- mock data only
- responsive desktop/mobile workspace navigation
- no API calls
- no authentication
- no backend integration
- no persistence

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

## Reference-screen rule

The Knowledge List page is the first reference implementation. It intentionally uses typography, alignment, whitespace and dividers instead of a card-based dashboard layout. Future screens should reuse these established patterns unless the design documentation is explicitly changed.
