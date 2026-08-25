# Backend implementation state

Last updated: 2026-08-25

## Technology baseline

- Java 25 and Spring Boot 4.1.x.
- One Maven modular monolith, one Spring Boot process, and one PostgreSQL database.
- Flyway owns migrations and DDL; Hibernate/JPA validates the mapped schema.
- Testcontainers provides PostgreSQL integration coverage.

## Accepted and active implementation baseline

- The accepted baseline through PR-021 includes the identity/session security lifecycle, canonical offline reference catalogue, and owner-scoped immutable native-currency cash ledger.
- PR-021 was accepted in implementation commit `e08f2c2`; later repository commit `bf1852c` changes agent and documentation guidance without adding another product capability.
- PR-022 is the active implementation specification. Its implementation and review are complete in the working tree and await the user-owned commit decision; `CURRENT.md` remains user-controlled and still points to PR-022.

## Implemented capabilities

### Identity and authentication

- Local account registration and password authentication are transactional and owner-safe.
- Access-token issuance/validation, opaque refresh sessions, rotation, reuse response, hardened body/cookie delivery, and a stateless bearer boundary are implemented.
- Authenticated identity resolution, `/me`, owner-scoped device-session list/detail, current/all/selected logout and revocation are implemented with durable security events and bounded process-local abuse protection.
- The shared RFC 9457 Problem Detail, validation, persistence-error, trace-correlation, and no-store response contracts are in use.

### Reference catalogue

- V2 provides deterministic offline countries, currencies, markets, market-currency relationships, instruments, aliases, and explicit market-calendar coverage.
- Authenticated reads expose stable reference data and calendar coverage without schedule inference or live providers.
- Owner-derived manual instruments support atomic alias replacement, owner/global visibility, prefix search, bounded cursor pagination, and optimistic version conflicts.

### Ledger

- V3 provides owner-scoped cash, brokerage, card, and loan account onboarding, explicit opening-state coverage, cash pockets, immutable activities/postings, and a rebuildable native balance projection.
- Deposits, withdrawals, same-currency owned transfers and previews, policy evaluation, idempotent retries, deterministic locking, reversal, opening correction, current/as-of balance reads, and owner-scoped activity/account cursors are implemented.
- The ledger exposes the required authenticated HTTP boundaries. Immutable postings remain the financial fact authority; projections are derived state.

### Reconciliation

- V4 adds owner-scoped native-currency reconciliation evidence protected as append-only by PostgreSQL and Hibernate, exact opening continuity, explicit `RECONCILIATION_ADJUSTMENT` activities linked by exact owner/id/type identity, signed `ADJUSTMENT` postings, atomic correction by reversal plus superseding replacement, account-bound supersession, owner-only cleanup cascades, derived lifecycle staleness, owner-scoped reads, and deterministic balance last-reconciliation metadata. Balance reads use repeatable-read snapshots across the account, projection, and reconciliation queries.
- Preview and commit use exact inclusive as-of arithmetic, mandatory opening continuity, projection version checks, advisory/account/projection locking, idempotent snapshots, and historical policy evaluation for archived full-ledger accounts. Holdings-only cash remains unsupported.

## Current database

Migration: `V4` (`V1__foundation.sql`, `V2__reference_catalog.sql`, `V3__financial_account_cash_ledger.sql`, `V4__cash_statement_reconciliation.sql`).

Schemas: `identity`, `reference`, `ledger`, `data`, `money`, `analysis`, `asset`, `platform`.

Tables:

- `identity`: `user_account`, `auth_identity`, `device_session`
- `platform`: `security_event`, `job`
- `reference`: `country`, `currency`, `market`, `market_currency`, `instrument`, `instrument_alias`, `market_calendar`
- `ledger`: `financial_account`, `account_cash_pocket`, `activity`, `money_posting`, `idempotency_record`, `account_balance_projection`, `reconciliation`
- Currently empty schemas: `data`, `money`, `analysis`, `asset`

## Current cross-cutting repository state

- Current capability roots are `identity`, `reference`, `ledger`, and `platform`; HTTP records use capability-owned `web/request` and `web/response` packages, and use-case models use `application/model` where needed.
- The application uses controller-bound request validation, typed authenticated principals, one stateless bearer chain, centralized persistence/error translation, and UUID compatibility correlation alongside native tracing.
- `platform.job` is unused storage scaffolding only. No scheduler, worker, batch, queue, retry framework, or generic workflow runtime is part of the current implementation.
- The preserved frontend still targets legacy APIs and is outside the backend rewrite baseline.

## Active implementation scope

- The active PR-022 scope is manual statement-balance reconciliation and explicit unexplained adjustments.

## Deferred capabilities

- Statement-line/file import, matching and duplicate detection, pending or settlement states, and provider synchronization.
- Investments, trades, positions, cost basis, multi-currency, FX, rates, prices, observations, and valuation/performance features.
- Spending, income classification, bills, card and debt workflows, planning/scenarios, households, shared expenses, claims, and settlements.
- Global reference administration, persistent signing keys, OIDC/recovery/MFA, roles/permissions, cross-site deployment hardening, and account export/deletion.
- Background execution and commodity async infrastructure until a concrete workload establishes its requirements.
- Frontend migration to the current API.

## Verification state

Latest PR-022 verification is green: the focused PostgreSQL/Testcontainers gate passed 80 tests, the full suite passed 377 tests, and the unskipped Maven `verify` lifecycle passed 377 tests, all with 0 failures, 0 errors, and 0 skips. Spotless passed for all 281 Java files; Docker Desktop/Testcontainers PostgreSQL 17 was available through the approved elevated invocation.

Last updated: 2026-08-25

## Resume context

- Operating contract and context router: [AGENTS.md](../../AGENTS.md)
- Active pointer: [CURRENT.md](CURRENT.md)
- Active scope: [PR-022 - Cash statement reconciliation and explicit adjustments](PR-022-cash-statement-reconciliation-and-explicit-adjustments.md)

Load only the standards, contracts, design sections, and repository code relevant to the current role and affected behavior.
