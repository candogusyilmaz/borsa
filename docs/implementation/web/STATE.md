# Frontend implementation state

Last updated: 2026-09-19

## Technology baseline

- React 19 (`19.2.8`), TypeScript strict (`5.9.3`), Vite (`8.2.2`)
- Mantine UI (`9.6.0`: `@mantine/core`, `@mantine/hooks`, `@mantine/notifications`)
- TanStack Router (`1.170.33`, file-based)
- TanStack Query (`5.102.8`)
- TanStack Form (`1.2.0`)
- openapi-fetch (`0.17.0`) / openapi-react-query (`0.5.4`)
- openapi-typescript (`7.13.0`)
- Biome (`2.5.12`)
- React Compiler (`babel-plugin-react-compiler` + `oxc-transform-react`)
- @phosphor-icons/react (`2.1.7`)

## Repository location

- Frontend application resides at `web/` in a plain monorepo layout.
- Application operates independently from `server/`.

## Active implementation scope

- The UI-001 foundation remains in place, and PR-029 extends the current account-ledger workflow rather than creating a parallel financial flow.
- The typed API client and generated `src/api/schema.d.ts` now expose the four supported manual cash activity request types, including `CASH_FEE` and `CASH_INTEREST_CREDIT`.
- Eligible account actions can open the existing record overlay for fees and interest credits; the form preserves canonical decimal strings, current/historical modes, policy confirmation, and server errors. Current-action effective time is captured at first submission with its request ID and reused for an unchanged uncertain retry; both rotate after a material payload change.
- Account quick actions, empty states, recent/paginated history, detail, signed amount/type formatting, and generic reasoned reversal expose the two new facts. Reversal request IDs are retained by activity and trimmed correction reason. Successful entry/reversal invalidates account, balance, activity list/detail, and reconciliation queries through TanStack Query.
- UI changes remain contained within `web/`; TanStack Query remains the sole remote-state mechanism and no new frontend test file was added.

## Verification state

- `npm.cmd run generate:openapi` and `npm.cmd run check:openapi` pass against the PR-029 backend contract; the generated schema is not hand-edited.
- `npm.cmd run typecheck` passes with 0 errors.
- The changed TypeScript/TSX files pass the targeted `npx.cmd biome check` gate. The full `npx.cmd biome check ./src --reporter=summary` gate reports 12 pre-existing formatter violations in untouched account/marketing/dashboard/shared/theme files; the exact list is recorded in the PR-029 completion record.
- `npm.cmd run test` passes 62 tests with 0 failures, and `npm.cmd run build` (`tsc -b && vite build`) passes.
