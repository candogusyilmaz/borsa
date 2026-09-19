# Backend implementation state

Last updated: 2026-09-19

## Technology baseline

- Java 25 and Spring Boot 4.1.x.
- One Maven modular monolith rooted in `server/`, one Spring Boot process, and one PostgreSQL database.
- Repository structure: plain monorepo separating `server/` (Spring Boot backend) and `web/` (React frontend).
- Flyway owns migrations and DDL; Hibernate/JPA validates the mapped schema.
- Testcontainers provides PostgreSQL integration coverage.
- Springdoc OpenAPI `3.1.0` publishes a deterministic OpenAPI 3.0.1 contract for public `/api/v1/**` paths at `/v3/api-docs`.

## Accepted implementation baseline

- The accepted baseline through PR-029 includes the identity/session security lifecycle, canonical offline reference catalogue, owner-scoped immutable native-currency cash ledger, cash-statement reconciliation, manual cash fees and interest credits, the governing simplicity standards, Cleanup B pagination simplification, Cleanup C validation/error/trivial-abstraction simplification, Cleanup D redundant model/mapping and fingerprint readability simplification, and the identity authentication-boundary consolidation.
- PR-023 is accepted and committed. Its directness, bounded-list, Spring `Pageable`, `Slice`/`Page`, and evidence-based abstraction rules are authoritative; it changed documentation only and did not change runtime behavior.
- PR-024 is accepted and committed. Financial accounts are a complete owner-scoped list; ledger activities and reconciliations use Spring `Pageable` with compact project-owned `SliceResponse<T>` results and no ledger cursor infrastructure.
- PR-025 is accepted and committed. Cleanup B is complete.
- PR-026 is accepted and committed. Cleanup C validation/error and trivial-abstraction simplification is complete.
- PR-027 is accepted and committed in `4e3108d`. Cleanup D redundant model, mapping, and fingerprint readability simplification is complete.
- PR-028 is accepted and committed in `ac4d7e7`. Identity authentication-boundary consolidation is complete.
- PR-029 is accepted and committed in `c01d708`. Manual cash fees and interest credits complete the implemented R3 manual cash-activity set.

## Implemented capabilities

### Identity and authentication

- Local account registration and password authentication are transactional and owner-safe.
- Access-token issuance/validation, opaque refresh sessions, rotation, reuse response, hardened body/cookie delivery, and a stateless bearer boundary are implemented.
- Authenticated identity resolution, `/me`, owner-scoped device-session list/detail, current/all/selected logout and revocation are implemented with durable security events and bounded process-local abuse protection.
- Registration, login, and refresh share one local-authentication HTTP controller and one non-transactional attempt-policy service; login and refresh share one successful credential result/response shape. Logout is colocated with device-session HTTP operations, while transactional core workflows and device-session query/revocation services remain separate.
- The shared RFC 9457 Problem Detail, validation, persistence-error, trace-correlation, and no-store response contracts are in use.

### Reference catalogue

- V2 provides deterministic offline countries, currencies, markets, market-currency relationships, instruments, aliases, and explicit market-calendar coverage.
- Authenticated reads expose stable reference data and calendar coverage without schedule inference or live providers.
- Owner-derived manual instruments support atomic alias replacement, owner/global visibility, prefix search with Spring `Pageable` and compact `SliceResponse<InstrumentSummaryResponse>` results, and optimistic version conflicts.

### Ledger

- V3 provides owner-scoped cash, brokerage, card, and loan account onboarding, explicit opening-state coverage, cash pockets, immutable activities/postings, and a rebuildable native balance projection.
- Deposits, withdrawals, same-currency owned transfers and previews, policy evaluation, idempotent retries, deterministic locking, reversal, opening correction, current/as-of balance reads, an unpaged owner-scoped account list, and owner-scoped activity/reconciliation `Pageable`/`Slice` reads are implemented.
- Owner-entered `CASH_FEE` and `CASH_INTEREST_CREDIT` facts use signed `FEE`/`INTEREST_CREDIT` postings, the existing current/historical policy and idempotency lifecycle, generic reasoned reversal, exact as-of effects, and reconciliation staleness by effective time.
- The ledger exposes the required authenticated HTTP boundaries. Immutable postings remain the financial fact authority; projections are derived state.

### Reconciliation

- V4 adds owner-scoped native-currency reconciliation evidence protected as append-only by PostgreSQL and Hibernate, exact opening continuity, explicit `RECONCILIATION_ADJUSTMENT` activities linked by exact owner/id/type identity, signed `ADJUSTMENT` postings, atomic correction by reversal plus superseding replacement, account-bound supersession, owner-only cleanup cascades, derived lifecycle staleness, owner-scoped reads, and deterministic balance last-reconciliation metadata. Balance reads use repeatable-read snapshots across the account, projection, and reconciliation queries.
- Preview and commit use exact inclusive as-of arithmetic, mandatory opening continuity, projection version checks, advisory/account/projection locking, idempotent snapshots, and historical policy evaluation for archived full-ledger accounts. Holdings-only cash remains unsupported.

## Current database

Migration: `V5` (`V1__foundation.sql`, `V2__reference_catalog.sql`, `V3__financial_account_cash_ledger.sql`, `V4__cash_statement_reconciliation.sql`, `V5__manual_cash_fees_and_interest.sql`).

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
- Spring Boot owns Spring Data web configuration, including the 100-row maximum page size and stable DTO page serialization. Generated OpenAPI pagination schemas mark all guaranteed page and project-owned `SliceResponse<T>` fields as required.
- PR-023 adopted the governing simplicity direction in the current standards: direct/local code, no pagination for naturally small bounded collections, Spring `Pageable` for ordinary pagination, and custom cursor/keyset infrastructure only for a demonstrated requirement. Ledger accounts are now unpaged, ledger activities/reconciliations use compact project-owned `SliceResponse<T>` results, and PR-025 now applies the same direct approach to complete device-session lists and instrument search. The ordinary-list cursor infrastructure has been removed after source search proved it dead.
- `platform.job` is unused storage scaffolding only. No scheduler, worker, batch, queue, retry framework, or generic workflow runtime is part of the current implementation.
- The frontend account workflow now consumes the current generated API contract for ledger activities, including the PR-029 fee and interest actions; unrelated frontend migration work remains outside this backend state document.

## Current implementation scope

- PR-025 through PR-028 are accepted and committed; the governing pagination, validation/error, redundant-model/mapping, fingerprint-readability, and identity authentication-boundary cleanup is complete.
- PR-029 is accepted and committed in `c01d708`; the implemented R3 manual cash activity, reconciliation, and native-balance boundary is complete.
- `CURRENT.md` now activates PR-030, a backend-only R4 slice for same-currency manually funded brokerage buys/sells, deterministic weighted-average position projection, reversal, and owner-scoped trade/position reads. This is specification state only; no PR-030 production code or V6 migration is implemented yet. Portfolio grouping, imports, holdings-only opening positions, tax, income/corporate actions, FX/multi-currency, pending settlement, valuation, and frontend work remain deferred.

## Deferred capabilities

- Statement-line/file import, matching and duplicate detection, pending or settlement states, and provider synchronization.
- Investments, trades, positions, cost basis, multi-currency, FX, rates, prices, observations, and valuation/performance features.
- Spending, income classification, bills, card and debt workflows, planning/scenarios, households, shared expenses, claims, and settlements.
- Global reference administration, persistent signing keys, OIDC/recovery/MFA, roles/permissions, cross-site deployment hardening, and account export/deletion.
- Background execution and commodity async infrastructure until a concrete workload establishes its requirements.
- Frontend migration outside the implemented account cash workflow.

## Verification state

PR-029 is accepted and committed in `c01d708`. Its focused fee/interest/migration/reconciliation/HTTP gate passed 93 tests with 0 failures, errors, or skips against PostgreSQL 17 Testcontainers; fresh V5 and V4-to-V5 migration paths, including seeded V4 balanced/adjusted reconciliations and their adjustment activity/posting/linkage, passed with Hibernate validation. `spotless:check`, full Maven `test`, and full Maven `verify` passed; both full lifecycles ran 382 tests with 0 failures, errors, or skips. OpenAPI generation and drift checking passed against the PR-029 backend. Frontend verification associated with that accepted cross-stack unit is retained in its Completion Record; PR-030 and subsequent work are backend-only unless the user explicitly changes scope.

PR-028 is accepted and committed in `ac4d7e7`. Registration/login/refresh use one HTTP boundary and one non-transactional attempt-policy boundary; logout is colocated with device-session HTTP operations; the transactional registration/login/rotation workflows and separate device-session query/revocation boundaries are preserved. Duplicate login/refresh result and response records and superseded operation-specific controllers/attempt wrappers are removed. The focused identity/security gate passed 108 tests, and the full suite plus Maven `verify` passed 371 tests each with 0 failures, 0 errors, and 0 skips against PostgreSQL 17 Testcontainers. Spotless passed across 255 Java files (192 production and 63 test), the executable archive was repackaged, and no required tests were skipped or replaced. Static audits pass: exactly three identity REST controllers and ten identity `@Service` classes remain, no deleted symbols remain, `AuthenticationAttemptService` has no transaction annotation, and `git diff --check` is clean.

PR-026 and PR-027 are accepted and committed; Cleanup C and Cleanup D are complete. PR-027 was accepted in commit `4e3108d`: the redundant preview/reference row/model/factory surfaces and unused read projections are removed, genuine read models remain, five workflow-specific fingerprint methods preserve the existing canonical identity, and the historical policy decision is passed through unchanged.

WORKSPACE-001 repository restructuring verified: full Maven lifecycle (`.\mvnw.cmd verify` without test skipping) from `server/` passed with 371 tests (0 failures, 0 errors, 0 skips), Spotless passed with 261 files clean, and Spring Boot executable archive repackaged successfully. Root Docker build (`docker build -t stocks-workspace-check .`) verified cleanly.

Last updated: 2026-09-19

## Resume context

- Operating contract and context router: [server/AGENTS.md](../../server/AGENTS.md) (repository router: [AGENTS.md](../../AGENTS.md))
- Active pointer: [CURRENT.md](CURRENT.md)
- Active scope: [PR-030 - Manual funded brokerage trades and deterministic position projection](PR-030-manual-funded-brokerage-trades.md), backend-only and not yet implemented.
- Last completed scope: [PR-029 - Manual cash fees and interest credits](PR-029-manual-cash-fees-and-interest.md), accepted in `c01d708`.

Load only the standards, contracts, design sections, and repository code relevant to the current role and affected behavior.
