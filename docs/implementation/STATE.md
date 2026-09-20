# Backend implementation state

Last updated: 2026-09-20

## Technology baseline

- Java 25 and Spring Boot 4.1.x.
- One Maven modular monolith rooted in `server/`, one Spring Boot process, and one PostgreSQL database.
- Repository structure: plain monorepo separating `server/` (Spring Boot backend) and `web/` (React frontend).
- Flyway owns migrations and DDL; Hibernate/JPA validates the mapped schema.
- Testcontainers provides PostgreSQL integration coverage.
- Springdoc OpenAPI `3.1.0` publishes a deterministic OpenAPI 3.0.1 contract for public `/api/v1/**` paths at `/v3/api-docs`.
- Apache Commons CSV `1.14.1` parses the fixed `FUNDED_TRADE_CSV_V1` RFC 4180 profile; multipart upload is bounded to a 1 MiB file and a 2 MiB request.

## Accepted implementation baseline

- The implemented and user-accepted baseline through PR-031 includes the identity/session security lifecycle, canonical offline reference catalogue, owner-scoped immutable native-currency cash ledger, cash-statement reconciliation, manual cash fees and interest credits, same-currency manually funded brokerage trades with deterministic weighted-average positions, portfolio reporting groups with account membership and filtered investment reads, the governing simplicity standards, Cleanup B pagination simplification, Cleanup C validation/error/trivial-abstraction simplification, Cleanup D redundant model/mapping and fingerprint readability simplification, and the identity authentication-boundary consolidation.
- PR-023 is accepted and committed. Its directness, bounded-list, Spring `Pageable`, `Slice`/`Page`, and evidence-based abstraction rules are authoritative; it changed documentation only and did not change runtime behavior.
- PR-024 is accepted and committed. Financial accounts are a complete owner-scoped list; ledger activities and reconciliations use Spring `Pageable` with compact project-owned `SliceResponse<T>` results and no ledger cursor infrastructure.
- PR-025 is accepted and committed. Cleanup B is complete.
- PR-026 is accepted and committed. Cleanup C validation/error and trivial-abstraction simplification is complete.
- PR-027 is accepted and committed in `4e3108d`. Cleanup D redundant model, mapping, and fingerprint readability simplification is complete.
- PR-028 is accepted and committed in `ac4d7e7`. Identity authentication-boundary consolidation is complete.
- PR-029 is accepted and committed in `c01d708`. Manual cash fees and interest credits complete the implemented R3 manual cash-activity set.
- PR-030 is complete and user-accepted in implementation commit `5a8bc48`. Same-currency manually funded brokerage trades, immutable security facts, deterministic weighted-average position projection, reversal, and owner-scoped trade/position reads are implemented.
- PR-031 is complete and user-accepted in implementation commit `cb16393`: owners can manage portfolios with atomic account membership and use portfolio membership to filter existing trade and open-position reads.

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

### Investing — manual funded trade and position slice

- V6 adds immutable `ledger.security_posting` facts linked to ledger activities, selected brokerage cash, owner-visible equity/ETF instruments, native currency, economic order, and exact reversal links. Trade cash stays in the selected account's existing cash pocket.
- Authenticated preview/commit supports same-currency manual buys and sells for active, full-ledger brokerage accounts, using existing cash policy, idempotency, cash projection/version, and deterministic command locking. Historical facts may record the allowed cash-policy breach state; all modes reject short inventory or duplicate economic order.
- `WEIGHTED_AVERAGE_ECONOMIC_V1` synchronously rebuilds one versioned projection per owner/account/instrument from immutable postings. Buy commission increases basis; sell commission reduces net proceeds and realized economic P&L; allocations retain the documented scale-18 `HALF_EVEN` remainder, and full close resets remaining basis to zero.
- Backdated trades and generic reasoned activity reversals replay the complete affected position in the same transaction. Owner-scoped trade/activity reads expose cash and security facts; pageable open-position reads omit closed positions while exact detail retains them.

### Investing — reviewed funded-trade CSV import

- V8 adds owner-scoped import batches, source rows, static issue evidence, and one `FILE_IMPORTED` activity link per imported trade. Exact file bytes are hashed and discarded after parsing; the supported nine-column format accepts only same-currency funded brokerage buys and sells.
- Authenticated upload, repeatable-read preview, and atomic commit provide persisted normalization evidence, current duplicate/order/cash/position simulation, deterministic preview tokens, idempotent retries, and deterministic owner/account/cash/position/instrument locking.
- Commit idempotency uses a compact import-specific snapshot so a maximum 500-row/500-instrument response replays exactly within the existing 32,768-byte database limit.
- Every committed row uses the shared manual/import posting path and existing settlement/weighted-average rules. Trade detail and history expose nullable batch, row, and external-ID provenance; manual activities retain `USER_ENTERED` with null import provenance.
- Invalid, duplicate, stale, ambiguous-order, short-producing, overflowing, or persistence-failing batches create no partial financial facts. Reversal leaves the original import link available for future duplicate detection.

### Portfolio reporting groups

- V7 adds owner-scoped `ledger.portfolio` and `ledger.portfolio_account_membership` tables with active normalized-name uniqueness, composite owner-alignment constraints, membership uniqueness, and cleanup cascades.
- Portfolio create/list/detail/update/archive uses complete owner-scoped account membership, deterministic response ordering, optimistic versions, stable conflict errors, and atomic replacement. Archived portfolios retain members, and archived accounts remain valid members.
- Existing trade-history and open-position queries accept `portfolioId` and use membership `EXISTS` predicates, preserving account intersection, pagination, sorting, and single-row results without changing financial facts or projections.

## Current database

Migration: `V8` (`V1__foundation.sql`, `V2__reference_catalog.sql`, `V3__financial_account_cash_ledger.sql`, `V4__cash_statement_reconciliation.sql`, `V5__manual_cash_fees_and_interest.sql`, `V6__manual_funded_brokerage_trades.sql`, `V7__portfolio_reporting_groups.sql`, `V8__reviewed_funded_trade_csv_import.sql`).

Schemas: `identity`, `reference`, `ledger`, `data`, `money`, `analysis`, `asset`, `platform`.

Tables:

- `identity`: `user_account`, `auth_identity`, `device_session`
- `platform`: `security_event`, `job`
- `reference`: `country`, `currency`, `market`, `market_currency`, `instrument`, `instrument_alias`, `market_calendar`
- `ledger`: `financial_account`, `account_cash_pocket`, `activity`, `money_posting`, `idempotency_record`, `account_balance_projection`, `reconciliation`, `security_posting`, `position_projection`, `portfolio`, `portfolio_account_membership`, `trade_import_batch`, `trade_import_row`, `trade_import_issue`
- Currently empty schemas: `data`, `money`, `analysis`, `asset`

## Current cross-cutting repository state

- Current capability roots are `identity`, `reference`, `ledger`, `investing`, and `platform`; HTTP records use capability-owned `web/request` and `web/response` packages, and use-case models use `application/model` where needed.
- The application uses controller-bound request validation, typed authenticated principals, one stateless bearer chain, centralized persistence/error translation, and UUID compatibility correlation alongside native tracing.
- Multipart import endpoints use explicit 1 MiB file and 2 MiB request limits, no-store responses, and an OpenAPI multipart contract. Import rows retain safe source fields; raw file bytes are not persisted.
- Spring Boot owns Spring Data web configuration, including the 100-row maximum page size and stable DTO page serialization. Generated OpenAPI pagination schemas mark all guaranteed page and project-owned `SliceResponse<T>` fields as required.
- PR-023 adopted the governing simplicity direction in the current standards: direct/local code, no pagination for naturally small bounded collections, Spring `Pageable` for ordinary pagination, and custom cursor/keyset infrastructure only for a demonstrated requirement. Ledger accounts are now unpaged, ledger activities/reconciliations use compact project-owned `SliceResponse<T>` results, and PR-025 now applies the same direct approach to complete device-session lists and instrument search. The ordinary-list cursor infrastructure has been removed after source search proved it dead.
- `platform.job` is unused storage scaffolding only. No scheduler, worker, batch, queue, retry framework, or generic workflow runtime is part of the current implementation.
- The frontend account workflow now consumes the current generated API contract for ledger activities, including the PR-029 fee and interest actions; unrelated frontend migration work remains outside this backend state document.

## Current implementation scope

- PR-025 through PR-028 are accepted and committed; the governing pagination, validation/error, redundant-model/mapping, fingerprint-readability, and identity authentication-boundary cleanup is complete.
- PR-029 is accepted and committed in `c01d708`; the implemented R3 manual cash activity, reconciliation, and native-balance boundary is complete.
- PR-030 completes the accepted backend-only R4 slice for same-currency manually funded brokerage buys/sells, deterministic weighted-average position projection, reversal, and owner-scoped trade/position reads.
- PR-031 completes the accepted owner-scoped portfolio lifecycle, atomic account membership, and portfolio-filtered trade/position reads.
- PR-032 implements and verifies the fixed reviewed same-currency funded-trade CSV workflow through V8; it is the current implementation pointer pending user acceptance. No subsequent PR is activated.

## Deferred capabilities

- General statement-line import, matching, pending or settlement states, and provider synchronization remain deferred; reviewed funded-trade CSV import is implemented in PR-032.
- Broader investment analytics, provider-sourced trades, holdings-only opening positions, lots/tax, multi-currency, FX, rates, prices, observations, and valuation/performance features remain deferred. Portfolio reporting groups are implemented in PR-031.
- Spending, income classification, bills, card and debt workflows, planning/scenarios, households, shared expenses, claims, and settlements.
- Global reference administration, persistent signing keys, OIDC/recovery/MFA, roles/permissions, cross-site deployment hardening, and account export/deletion.
- Background execution and commodity async infrastructure until a concrete workload establishes its requirements.
- Frontend migration outside the implemented account cash workflow.

## Verification state

PR-031 verification remains recorded in its Completion Record. PR-032's specified focused gate passed with 30 tests; the final `verify` gate passed with 451 tests, 0 failures, 0 errors, and 0 skipped. Spotless apply and the `verify` Spotless check passed. The gates include maximum-batch snapshot-size and exact-replay coverage, late-failure rollback, reversal rebuild/provenance, trade-history provenance, valid normalized-row/raw-SQL constraints, and exact V8 schema inventories. Migration preservation evidence and detailed feature/test decisions remain in the PR-032 Completion Record.

Last updated: 2026-09-20

## Resume context

- Operating contract and context router: [server/AGENTS.md](../../server/AGENTS.md) (repository router: [AGENTS.md](../../AGENTS.md))
- Active pointer: [CURRENT.md](CURRENT.md)
- Active pointer: [PR-032 - Reviewed funded-trade CSV import and atomic commit](PR-032-reviewed-funded-trade-csv-import.md). Its V8 backend implementation is complete and verified, pending user acceptance; no next PR has been activated.
- Last completed scope: [PR-031 - Portfolio reporting groups and account-scoped investment views](PR-031-portfolio-reporting-groups.md), user-accepted in implementation commit `cb16393`.

Load only the standards, contracts, design sections, and repository code relevant to the current role and affected behavior.
