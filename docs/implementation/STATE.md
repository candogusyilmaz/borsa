# Backend implementation state

Last updated: 2026-09-07

## Technology baseline

- Java 25 and Spring Boot 4.1.x.
- One Maven modular monolith, one Spring Boot process, and one PostgreSQL database.
- Flyway owns migrations and DDL; Hibernate/JPA validates the mapped schema.
- Testcontainers provides PostgreSQL integration coverage.

## Accepted and active implementation baseline

- The accepted baseline through PR-026 includes the identity/session security lifecycle, canonical offline reference catalogue, owner-scoped immutable native-currency cash ledger, cash-statement reconciliation, the governing simplicity standards, Cleanup B pagination simplification, and Cleanup C validation/error/trivial-abstraction simplification.
- PR-023 is accepted and committed. Its directness, bounded-list, Spring `Pageable`, `Slice`/`Page`, and evidence-based abstraction rules are authoritative; it changed documentation only and did not change runtime behavior.
- PR-024 is accepted and committed. Financial accounts are a complete owner-scoped list; ledger activities and reconciliations use Spring `Pageable` with compact project-owned `SliceResponse<T>` results and no ledger cursor infrastructure.
- PR-025 is accepted and committed. Cleanup B is complete.
- PR-026 is accepted and committed. Cleanup C validation/error and trivial-abstraction simplification is complete.
- PR-027 is the active implementation specification. Cleanup D redundant model, mapping, and fingerprint readability work is implemented in the working tree and remains active until user acceptance.

## Implemented capabilities

### Identity and authentication

- Local account registration and password authentication are transactional and owner-safe.
- Access-token issuance/validation, opaque refresh sessions, rotation, reuse response, hardened body/cookie delivery, and a stateless bearer boundary are implemented.
- Authenticated identity resolution, `/me`, owner-scoped device-session list/detail, current/all/selected logout and revocation are implemented with durable security events and bounded process-local abuse protection.
- The shared RFC 9457 Problem Detail, validation, persistence-error, trace-correlation, and no-store response contracts are in use.

### Reference catalogue

- V2 provides deterministic offline countries, currencies, markets, market-currency relationships, instruments, aliases, and explicit market-calendar coverage.
- Authenticated reads expose stable reference data and calendar coverage without schedule inference or live providers.
- Owner-derived manual instruments support atomic alias replacement, owner/global visibility, prefix search with Spring `Pageable` and compact `SliceResponse<InstrumentSummaryResponse>` results, and optimistic version conflicts.

### Ledger

- V3 provides owner-scoped cash, brokerage, card, and loan account onboarding, explicit opening-state coverage, cash pockets, immutable activities/postings, and a rebuildable native balance projection.
- Deposits, withdrawals, same-currency owned transfers and previews, policy evaluation, idempotent retries, deterministic locking, reversal, opening correction, current/as-of balance reads, an unpaged owner-scoped account list, and owner-scoped activity/reconciliation `Pageable`/`Slice` reads are implemented.
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
- PR-023 adopted the governing simplicity direction in the current standards: direct/local code, no pagination for naturally small bounded collections, Spring `Pageable` for ordinary pagination, and custom cursor/keyset infrastructure only for a demonstrated requirement. Ledger accounts are now unpaged, ledger activities/reconciliations use compact project-owned `SliceResponse<T>` results, and PR-025 now applies the same direct approach to complete device-session lists and instrument search. The ordinary-list cursor infrastructure has been removed after source search proved it dead.
- `platform.job` is unused storage scaffolding only. No scheduler, worker, batch, queue, retry framework, or generic workflow runtime is part of the current implementation.
- The preserved frontend still targets legacy APIs and is outside the backend rewrite baseline.

## Active implementation scope

- PR-025 is accepted and committed; Cleanup B is complete. Device-session listing is a complete owner-scoped logical-family array, instrument search uses Spring `Pageable` plus compact `SliceResponse<InstrumentSummaryResponse>` results, and the remaining session/instrument/generic cursor stack was deleted after consumer removal.
- PR-026 is accepted and committed; Cleanup C is complete. PR-027 / Cleanup D is active and owns only redundant model/mapping removal, unused projection trimming, workflow-specific fingerprint readability, and the audited historical-policy pass-through. Its implementation is present in the working tree; Cleanup E, migrations, frontend changes, MyBatis/read-persistence work, and investing/R4 remain outside PR-027.

## Deferred capabilities

- Statement-line/file import, matching and duplicate detection, pending or settlement states, and provider synchronization.
- Investments, trades, positions, cost basis, multi-currency, FX, rates, prices, observations, and valuation/performance features.
- Spending, income classification, bills, card and debt workflows, planning/scenarios, households, shared expenses, claims, and settlements.
- Global reference administration, persistent signing keys, OIDC/recovery/MFA, roles/permissions, cross-site deployment hardening, and account export/deletion.
- Background execution and commodity async infrastructure until a concrete workload establishes its requirements.
- Frontend migration to the current API.

## Verification state

PR-026 is accepted and committed; Cleanup C is complete. PR-027 / Cleanup D is active and implemented: the redundant preview/reference row/model/factory surfaces and unused read projections are removed, genuine read models remain, five workflow-specific fingerprint methods preserve the existing canonical identity, and the historical policy decision is passed through unchanged. The positive historical-adjustment test explicitly proves persisted `ALLOWED`, while the negative historical-adjustment test continues to prove `HISTORICAL_BREACH_RECORDED`. The focused PR-027 gate passed 99 tests, and the full suite plus Maven `verify` passed 371 tests each with 0 failures, 0 errors, and 0 skips against the final working tree. Spotless passed, Docker Desktop/Testcontainers PostgreSQL 17 was available, and no required tests were skipped or replaced. Static audits pass, including no deleted-symbol or removed-view matches, unchanged normalized predicates/order clauses, unchanged `CanonicalFingerprint`, and 261 Java files (198 production and 63 test).

Last updated: 2026-09-07

## Resume context

- Operating contract and context router: [AGENTS.md](../../AGENTS.md)
- Active pointer: [CURRENT.md](CURRENT.md)
- Active scope: [PR-027 - Cleanup D redundant model, mapping, and fingerprint readability](PR-027-redundant-model-mapping-and-fingerprint-readability.md)

Load only the standards, contracts, design sections, and repository code relevant to the current role and affected behavior.
