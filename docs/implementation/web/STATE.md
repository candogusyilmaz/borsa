# Frontend implementation state

Last updated: 2026-09-20

## Technology baseline

- React 19 (`19.2.8`), TypeScript strict (`5.9.3`), Vite (`8.2.2`)
- Mantine UI (`9.6.0`: `@mantine/core`, `@mantine/hooks`, `@mantine/notifications`, `@mantine/charts`, `@mantine/carousel`)
- TanStack Router (`1.170.33`, file-based via `@tanstack/router-plugin`)
- TanStack Query (`5.102.8`)
- TanStack Form (`1.2.0`)
- openapi-fetch (`0.17.0`) / openapi-react-query (`0.5.4`)
- openapi-typescript (`7.13.0`)
- Biome (`2.5.12`)
- React Compiler (`@vitejs/plugin-react` target 19 + `oxc-transform-react`)
- @phosphor-icons/react (`2.1.7`)

## Repository location

- Frontend application resides at `web/` in a plain monorepo layout.
- Application operates independently from `server/`.

## Architecture reality

- **Routing**: File-based TanStack Router (`web/src/routes/`). Root route (`__root.tsx`) provides navigation, auth guards, head metadata, and the overlay host.
- **Page Composition**: Feature-local pages exist for several destinations:
  - `/` -> `MarketingPage` (`@/features/marketing`)
  - `/login` -> `LoginPage` (`@/features/auth`)
  - `/register` -> `RegisterPage` (`@/features/auth`)
  - `/app` -> `DashboardPage` (`@/features/dashboard`)
  - `/app/investing` -> `InvestingPage` (`@/features/investing`)
  Other destinations still compose significant screen structure directly inside route files (e.g., `/app/accounts/$accountId`, `/app/instruments`, `/app/sessions`).
- **Overlays**: Contextual workflows and detail views are managed through the centralized overlay store and registry (`@/shared/overlay`):
  - Trading: `TradeOverlay`, `TradeDetailOverlay`
  - Accounts: `CreateAccountOverlay`, `AccountDetailOverlay`, `ArchiveAccountOverlay`, `AccountSettingsOverlay`, `AccountPickerOverlay`
  - Cash Activities: `RecordCashActivityOverlay`, `ActivityDetailOverlay`, `ReverseActivityOverlay`, `TransferOverlay`, `OpeningCorrectionOverlay`, `ReconciliationOverlay`
  - Instruments: `ManualInstrumentCreateOverlay`, `ManualInstrumentUpdateOverlay`, `InstrumentDetailOverlay`
  - Reference: `ReferenceCatalogOverlay`
- **Server State**: Server data is owned by TanStack Query through the typed `$api` client (`openapi-react-query`). Mutations use direct query client invalidations.
- **Transitional Structures**: Feature internals currently have structural inconsistencies targeted for incremental alignment:
  - Account feature components directory contains mixed responsibilities (screen layouts, multi-step workflows, and UI cards).
  - Generic formatters (`formatCurrency`, `formatDateTime`, `toRelativeTime`) reside in `features/account/utils/` and are consumed by other features (`dashboard`, `investing`).
  - Cross-feature deep imports exist in isolated places (e.g. `ReverseActivityOverlay`).
  - Cache invalidation groups are repeated across multiple mutation handlers.

## Implemented capabilities

- **Authentication & Sessions**: Local password registration and login, session resolution, device session management, and session revocation.
- **Financial Accounts**: Checking, savings, cash wallet, and brokerage account onboarding; balance display, account settings, and archiving.
- **Cash Ledger & Activities**: Deposits, withdrawals, transfers, opening corrections, statement reconciliations, activity detail view, and activity reversals.
- **Instruments & Catalog**: Instrument catalog browsing, type and valuation filtering, manual instrument creation and updates, and alias management.
- **Investing & Positions**: Manual funded brokerage trading (BUY/SELL), 3-step trade workflow (configure -> preview -> success), trade detail with multi-leg postings (cash, trade, commission, security) and reversal support, open positions projection with account-level filtering, and trade history.

## Verification state

- `npm run typecheck` (`tsc -b`): passes with 0 errors.
- `npm run lint` (`biome lint ./src`): passes with 0 errors across all source files.
- `npm run test` (`vitest run`): passes 62/62 tests with 0 failures.
- `npm run build` (`tsc -b && vite build`): passes and generates production bundles cleanly.
