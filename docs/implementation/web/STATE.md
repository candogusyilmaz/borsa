# Frontend implementation state

Last updated: 2026-09-08

## Technology baseline

- React 19 (`19.2.8`), TypeScript strict (`5.9.3`), Vite (`8.2.2`)
- Mantine UI (`9.6.0`: `@mantine/core`, `@mantine/hooks`, `@mantine/notifications`)
- TanStack Router (`1.170.33`, file-based)
- TanStack Query (`5.102.8`)
- TanStack Form (`1.2.0`)
- openapi-fetch (`0.17.0`) / openapi-react-query (`0.5.4`)
- Biome (`2.5.12`)
- React Compiler (`babel-plugin-react-compiler` + `oxc-transform-react`)
- @phosphor-icons/react (`2.1.7`)

## Repository location

- Frontend application resides at `web/` in a plain monorepo layout.
- Application operates independently from `server/`.

## Active implementation scope

- Unit UI-001 (Frontend Foundation Rebuild) is currently active.
- Legacy frontend has been purged and replaced with a clean foundation:
  - Centralized typed API client (`src/api/client.ts`) with RFC 7807 `ProblemDetail` error normalization and Bearer auth middleware.
  - Core design tokens, semantic CSS variables, and Mantine v9 alpha theme (`src/app/theme.ts`, `src/index.css`).
  - Strict scope discipline: all UI work strictly contained within `web/`.
  - State management rule: TanStack Query exclusively owns remote state; no Zustand, no global stores.

## Verification state

- Foundation baseline verification against `web/`:
  - `npm run typecheck` (`tsc -b`) verified with 0 errors.
  - `npx biome check ./src` verified with 0 errors.
  - `npm run build` (`tsc -b && vite build`) passes cleanly.
  - Pre-commit hooks verify TypeScript and Biome via `npm run check:fix`.
