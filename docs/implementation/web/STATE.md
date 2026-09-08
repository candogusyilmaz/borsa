# Frontend implementation state

Last updated: 2026-09-07

## Technology baseline

- Vite, React 19, TypeScript
- Mantine UI (v9 alpha)
- TanStack Router and TanStack Query
- OpenAPI Fetch client
- Zustand state management
- Biome for formatting and linting

## Repository location

- Preserved frontend application resides at `web/` in a plain monorepo layout.
- Application operates independently from `server/`.

## Active implementation scope

- No UI unit is currently active.
- Legacy frontend is preserved as-is from `src/main/web`.
- Template migration and UI modernization will be planned under future UI units (starting with UI-001).

## Verification state

- Restructuring verification passed against `web/`:
  - `npm run typecheck` (`tsc -b`) passed with 0 errors.
  - `npx biome check ./src` checked 77 files with 0 errors / no fixes needed.
  - `npm run build` (`tsc -b && vite build`) produced production distribution in `web/dist` successfully.
  - `npm run prepare` (`cd .. && husky`) verified successfully from `web/`.
