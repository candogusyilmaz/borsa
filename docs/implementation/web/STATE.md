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

- The UI-001 foundation remains in place, and PR-030 extends the web frontend with complete manual funded brokerage trading and position projection capabilities.
- The typed API client and generated `src/api/schema.d.ts` expose the full PR-030 OpenAPI specification, including `/api/v1/trades`, `/api/v1/trades/previews`, and `/api/v1/investing/positions`.
- A dedicated `/app/investing` route provides top-level access to open positions and trade history with account-level filtering. Desktop navigation includes "Investing", and mobile bottom navigation replaces "Sessions" with "Investing" (relocating Sessions access into the user drawer).
- The 3-step `TradeOverlay` (Configure $\rightarrow$ Preview $\rightarrow$ Success) enables manual BUY/SELL trade recording with validation, optimistic balance/position version capture, policy breach overrides, and robust idempotency.
- The `TradeDetailOverlay` renders comprehensive trade facts, timestamps, economic sequences, multi-leg postings (cash, trade, commission, security), and supports reversing security trades.
- Brokerage accounts include a "Trade" quick action in `AccountQuickActions` and an `AccountPositionsCard` within the account detail stack. Instrument details for `EQUITY` and `ETF` include direct "Trade" shortcuts.
- Reversal of trades and activities invalidates trades, positions, accounts, balances, and reconciliations via TanStack Query.
- UI changes remain strictly contained within `web/`; TanStack Query remains the sole remote-state mechanism and no new frontend test files were added.

## Verification state

- `npm.cmd run typecheck` passes with 0 errors.
- `npm.cmd run lint` (`biome lint ./src`) passes with 0 errors and 0 warnings across all files.
- `npx.cmd biome check` passes with 0 errors on all touched files.
- `npm.cmd run test` (`vitest run`) passes 62/62 tests with 0 failures.
- `npm.cmd run build` (`tsc -b && vite build`) passes and generates production bundles cleanly.
