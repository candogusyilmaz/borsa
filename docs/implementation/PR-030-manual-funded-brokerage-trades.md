# PR-030 - Manual funded brokerage trades and deterministic position projection

Status: **COMPLETE**

## Goal

An authenticated owner can preview and commit a same-currency, fee-aware buy or sell against cash already held in an active full-ledger brokerage account, inspect the immutable trade and current position, and reverse an erroneous trade. Cash, security quantity, weighted-average economic basis, and realized economic P&L remain exact, owner-scoped, idempotent, concurrency-safe, and deterministic under backdated replay.

## Capability and review boundary

- Coherent capability: this is the first usable investing slice. It takes a manually entered funded trade from preview through immutable cash/security facts to a queryable position instead of stopping at a table, DTO, posting leg, or projection in isolation.
- Combined behaviors: trade activity shapes, exact gross/commission cash postings, security postings, synchronous `WEIGHTED_AVERAGE_ECONOMIC_V1` replay, selected brokerage-cash policy checks, reversal, owner-scoped read models, HTTP/OpenAPI, and PostgreSQL proof belong together. Splitting them would either allow unrepresented positions or expose trades that do not reconcile to cash.
- Excluded neighbor: portfolio/report grouping and account membership are independently useful reporting behavior and remain later R4 work. CSV imports, holdings-only opening positions, tax components, dividends/corporate actions, multi-currency/FX, and pending settlement also remain separate capabilities.
- Focused review: one reviewer can trace a bank-to-brokerage transfer already supported by the ledger, a buy, a partial/full sell, and a reversal through the exact cash and quantity legs, projection replay, owner-scoped reads, and golden fixtures without reviewing import or valuation infrastructure.
- This PR is strictly backend-only. Do not inspect or modify `web/`, regenerate a frontend client, or add UI compatibility work.

## Source documents

- `docs/review/backend-master-plan.md` - R4 items 2-3, the trade-replay part of item 5, the trade/position parts of items 9-10, Stage 2, and first-backlog items 14-16.
- `docs/review/accounting-contract.md` - sections 2-4, 6-11, and 17-21, including the `WEIGHTED_AVERAGE_ECONOMIC_V1` allocation rule dated 2026-09-19.
- `docs/review/cash-accounts-and-funding-design.md` - activity/posting behavior, trade funding selection, practical first-release settlement, concurrency/idempotency, projections, and golden fixtures.
- `docs/review/business-logic-and-analytics-design.md` - ledger facts and deterministic position/cost-basis projection.
- `docs/engineering/coding-standards.md` - capability packaging, directness, Flyway/JPA ownership, `JdbcClient` reads, HTTP/error/security contracts, and PostgreSQL testing.

## Starting state

- PR-029 is accepted and committed in `c01d708`; R3's manual native-currency cash facts are complete and the planning working tree was clean before this specification was created.
- Flyway V5 owns `ledger.financial_account`, native `account_cash_pocket`, immutable `activity`/`money_posting`, idempotency, cash balance projection, reconciliation, and append-only protection. No `security_posting`, position projection, portfolio, trade, lot, import, pending-settlement, price, or FX table exists.
- An owner can create a `FULL_LEDGER` brokerage account with known native cash, fund it through the existing same-currency owned transfer, and apply the existing negative-balance policy, balance-version, idempotency, locking, reversal, as-of balance, and reconciliation-staleness contracts.
- The reference catalogue supplies global and owner-entered instruments with owner visibility, activity state, instrument type, market, and quotation currency. No trade workflow currently consumes them.
- `HOLDINGS_ONLY` brokerage accounts deliberately have untracked cash and no opening-position/cost-basis workflow. They are not eligible for this PR.

## Scope

1. Introduce an `investing` capability root for manual trade commands, security/position domain behavior, investing persistence/read models, HTTP contracts, and stable investing error codes. Keep common financial-account, activity, money-posting, policy, idempotency, and reconciliation authority in `ledger`; use direct cross-capability collaboration permitted by the modular-monolith rules and do not duplicate those mechanisms.
2. Add immutable `SECURITY_BUY` and `SECURITY_SELL` activity shapes. Each committed trade has exactly one security posting and a separate gross cash posting; add a `FEE` cash posting only when the non-negative commission is non-zero. The persisted trade currency is the brokerage cash-pocket and instrument quotation currency.
3. Support `EQUITY` and `ETF` instruments only. The instrument must be global or owned by the authenticated owner. A current buy requires an active instrument; a sell of an existing position and a historical fact may reference an inactive but still visible instrument so tracked truth can be closed or reconstructed. Indexes and every other instrument type remain unsupported in this slice.
4. Calculate manual-trade settled gross from positive exact quantity and unit price under accounting-contract section 9: round `quantity * unitPrice` once to the currency minor unit using `HALF_EVEN`, persist the result, and expose it in preview/commit/read responses. Commission is an explicit non-negative same-currency amount at or within the currency minor-unit scale. Reject values that overflow `numeric(38,18)` or require hidden truncation. A sell's net proceeds must remain strictly positive.
5. Provide a read-only preview that resolves the owner-visible instrument and eligible brokerage account, calculates gross, commission, signed cash delta, current/after cash, policy decision, current/after quantity, current/after remaining basis, and—for sells—allocated basis and realized economic P&L. Return the cash balance and position projection versions needed by commit. An absent position has preview version `0`.
6. Commit the previewed trade atomically and idempotently. A buy posts `-(gross + commission)` to brokerage cash and increases security quantity; its economic basis increases by gross plus commission. A sell posts `+(gross - commission)`, decreases quantity, allocates weighted-average basis, and adds `gross - commission - allocatedBasis` to cumulative realized economic P&L. There is no implicit bank funding: the existing owned-transfer workflow remains the separate way to move bank cash into brokerage cash.
7. Rebuild the affected `(owner, brokerageAccount, instrument)` projection synchronously from immutable facts on every trade or trade reversal. Replay uses `effectiveAt`, required non-negative `economicSequence`, and immutable identity only after the explicit order. Reject a duplicate `(account, instrument, effectiveAt, economicSequence)` original-trade key and reject any candidate history that becomes negative at any replay point. Request arrival or database insertion order must not affect the result.
8. Implement `WEIGHTED_AVERAGE_ECONOMIC_V1` exactly as the accounting contract now defines it: fee-capitalized buys, scale-18 `HALF_EVEN` partial-disposal allocation with remainder retained in the open position, exact full-close remainder consumption, zero quantity/basis after close, and a fresh basis cycle on reopen. Do not add lots or a jurisdictional tax basis.
9. Extend the existing reasoned activity reversal for trade activities. A reversal retains the original effective time, creates inverse cash and security postings linked to their originals, leaves both facts auditable, rebuilds the position as if the original trade were absent, and stales affected reconciliation evidence by effective time. Preserve the existing `NOT_APPLICABLE` reversal policy rather than treating correction as a new current-action spend; corrected cash may reveal negative reality, but the remaining security history may never be negative. Reject double reversal and any reversal whose remaining economic history would become invalid; do not delete or mutate the original trade.
10. Add owner-scoped trade and position reads using explicit `JdbcClient` SQL where the joined read model is clearer than JPA. Trade detail/history expose immutable trade facts, commission, cash/security effects, recording/policy/reversal metadata, and deterministic order. Current-position reads expose account/instrument identity, quantity, remaining economic basis, cumulative realized economic P&L, basis-policy version, projection status/as-of/watermark/build metadata, and version. Position lists omit zero-quantity closed positions; exact detail remains available for a closed `(account, instrument)` projection.
11. Extend the existing generic activity detail/list response with a non-null security-posting collection (empty for existing cash-only activities) so a trade is not represented as only a cash movement. Preserve existing money-posting response behavior and avoid N+1 query paths.
12. Preserve the existing current/historical recording distinction. Future effective times are rejected. Current buys apply the brokerage cash negative-balance policy and confirmation lifecycle; historical facts preserve real negative cash with the existing warning decision. Short selling is unsupported in both modes, so no historical or current sell may make quantity negative.

## Explicit non-goals

- No frontend files, generated frontend schema, overlays, navigation, forms, client cache work, or UI tests.
- No portfolio/report grouping, account membership, household/shared visibility, target allocation, valuation, performance return, price observation, or net-worth aggregation.
- No CSV/file import, import batch/row/issue/match model, fingerprint duplicate detection across external records, broker connection, provider synchronization, or background job.
- No holdings-only opening position, unknown/partial cost-basis coverage, inferred historical cash, or conversion of an existing holdings-only account.
- No tax/levy/withholding component, dividend/income, stock split, merger, spinoff, return of capital, or other corporate action.
- No multi-currency trade, FX execution/reference rate, foreign cash pocket, spread, margin, short sale, option/derivative, bond/fund/crypto/commodity/currency trade, or multiple security legs.
- No pending/unsettled cash receivable/payable. For this manual first release, security and cleared brokerage-cash effects both occur at `effectiveAt`; there is no distinct `settlementAt`.
- No lot projection, FIFO/specific-lot/tax policy, generic projection framework, asynchronous rebuild runtime, outbox, event bus, MyBatis, or speculative abstraction for later R4 work.

## Database changes

Migration: `V6__manual_funded_brokerage_trades.sql`.

Extend existing structures:

- Add `SECURITY_BUY` and `SECURITY_SELL` to the activity type/policy checks. Original trade activities require a non-null, non-negative `economic_sequence`; existing activity shapes and reversal behavior remain valid.
- Add `TRADE_PURCHASE` (strictly negative gross) and `TRADE_PROCEEDS` (strictly positive gross) money-posting roles. Reuse `FEE` for a non-zero negative commission. Do not weaken existing role/sign/zero constraints.
- Extend append-only and activity-shape protection so trade activities and their money/security postings cannot be updated or deleted outside the already supported owner-cleanup behavior.

Create `ledger.security_posting` as immutable fact storage with:

- UUID identity; owner, activity, financial-account, instrument, and trade-currency identity; signed `quantity_delta`; original-trade `unit_price` and settled `gross_amount`; copied `effective_at` and `economic_sequence` for constrained deterministic ordering; posting role; optional referenced original security posting for reversal; and `created_at`.
- composite owner/account/activity integrity, reference-instrument and currency foreign keys, exactly one security posting per trade/reversal activity in this slice, unique reversal, and a unique original-trade economic key `(owner, account, instrument, effective_at, economic_sequence)`.
- checks requiring positive buy quantity, negative sell quantity, positive unit price/gross, exact currency shape, inverse non-zero reversal quantity, and valid original/reversal role shapes. Database enforcement must prevent a raw SQL row from pairing a buy role with a sell activity, drifting copied time/order from its activity, or linking a reversal to another owner/account/instrument.

Create mutable `ledger.position_projection` with:

- UUID identity; owner, brokerage account, instrument, and currency; non-negative current quantity and remaining economic basis; cumulative realized economic P&L; calculation policy `WEIGHTED_AVERAGE_ECONOMIC_V1`; projection status; `as_of`, input watermark/activity identity, `last_successful_build_at`, optional `stale_from`, `updated_at`, and optimistic version.
- one row per `(owner, account, instrument)`, matching owner/account/instrument/currency foreign keys, non-negative/version/status checks, and the full-close invariant that zero quantity implies zero remaining basis.
- owner cleanup behavior consistent with existing identity/ledger cascades while global reference instruments remain protected. No portfolio, lot, trade-import, observation, valuation, or future settlement table is created.

Migration proof must cover an empty database through V6 and an actual V5-to-V6 upgrade containing V5 fee/interest activities, postings, current/adjusted reconciliations, and owner/global instruments. Hibernate validation and the repository's future-table absence assertions must be updated only for the two new tables now authorized.

## Application changes

- `investing/domain` owns security-posting and position-projection mappings plus the direct weighted-average replay calculation. Keep the calculation independently testable and version-named; do not create a generic calculation engine.
- One cohesive investing application service owns preview/commit transaction orchestration. It may use the existing request records directly where that remains clear. Reuse the established ledger command lock, account/projection locking, policy evaluation, canonical fingerprint, idempotency snapshot, activity/money-posting repositories, and `Clock`/`IdGenerator` through the smallest concrete visibility or collaboration change; do not copy their logic into a parallel investing framework.
- Lock/replay order must be deterministic. Serialize the owner-scoped idempotency key first, then lock the owned brokerage account/cash projection and affected position key/row in one documented order. The unique projection identity and unique security economic key remain database barriers when a row does not yet exist.
- Idempotency fingerprint material includes account, instrument, side, canonical quantity/unit price/commission, recording mode, effective time, economic sequence, confirmation, and expected cash/position versions. Same key and material request replays the original response before current-version checks; changed material conflicts.
- The position projection is derived state. Build a complete candidate replay in memory, validate every intermediate quantity/basis state, then persist the projection and facts in the same transaction. A replay/calculation/persistence failure leaves no partial activity, posting, projection, cash change, or idempotency record.
- Trade reads are read models, not mutable position authority. Continue to expose canonical decimal strings and relevant projection metadata; do not serialize JPA entities.
- Extend the existing generic reversal transaction rather than adding destructive undo or a second correction framework. Keep opening and reconciliation-adjustment restrictions intact.

## API contract

All endpoints are authenticated, owner-scoped, no-store financial responses under `/api/v1`.

- `POST /api/v1/trades/previews` accepts `accountId`, `instrumentId`, `side` (`BUY` or `SELL`), positive decimal-string `quantity`, positive decimal-string `unitPrice`, non-negative decimal-string `commissionAmount`, `recordingMode`, non-future `effectiveAt`, non-negative `economicSequence`, and `confirmPolicyBreach`. It returns `200` with canonical inputs/calculated gross, currency, signed cash effect and before/after balance, policy decision/allowed flag, before/after position and basis, sell allocation/P&L where applicable, calculation-policy version, cash-balance version, and position version. Existing negative-balance policy rejection is represented by `allowed=false` in preview and by the stable ledger problem on commit; malformed/unsupported shape, currency or precision and an oversell return their stable problem instead of a fabricated after-state.
- `POST /api/v1/trades` accepts the same material fields plus `clientRequestId`, required non-negative `expectedCashBalanceVersion`, and required non-negative `expectedPositionVersion`. It returns `201`, no-store headers, a `Location` at `/api/v1/trades/{activityId}`, and the committed trade response.
- `GET /api/v1/trades/{activityId}` returns one owner-visible trade or the stable not-found problem.
- `GET /api/v1/trades` returns `SliceResponse<TradeSummaryResponse>` using Spring `Pageable`, optional `accountId` and `instrumentId` filters, and a deterministic default of effective time descending, economic sequence descending, then activity ID. Only documented effective/recorded-time sort keys are accepted; no custom cursor or total-count query is added.
- `GET /api/v1/investing/positions` returns open positions as `SliceResponse<PositionResponse>` using Spring `Pageable` and optional `accountId`, with deterministic account/instrument tie-breakers and only documented account-name/instrument-symbol sort keys.
- `GET /api/v1/investing/positions/{accountId}/{instrumentId}` returns the exact current or closed projection for that owner-visible pair.
- Existing `POST /api/v1/activities/{activityId}/reversals` accepts trade activities and returns the extended activity response with inverse money/security postings. Existing list/detail endpoints expose the new types and security postings without changing their pagination semantics.

Use stable investing problem codes for unsupported instrument/type, trade currency mismatch, invalid settled precision, insufficient position quantity, duplicate/ambiguous economic order, position-version conflict, and trade/position not found. Continue using existing ledger codes for account ownership/eligibility, future time, balance version, policy/funds, idempotency, reversal, and generic state conflicts. Cross-owner identifiers must not disclose resource existence.

## Business invariants

- A trade belongs to one owner, one active full-ledger brokerage account, its one native cash pocket, and one owner-visible `EQUITY`/`ETF`. Account, instrument quotation, gross, fee, and posting currencies are identical in this slice.
- A buy has one positive security quantity, one negative gross cash posting, and zero or one negative fee posting. A sell has one negative security quantity, one positive gross cash posting, and zero or one negative fee posting. Their signed sum equals the returned cash delta exactly.
- Quantity and unit price are positive; commission is non-negative and minor-unit compliant; calculated gross is positive; sell net proceeds are positive. Zero commission creates no zero-value posting.
- Cash comes only from or returns only to the selected brokerage cash pocket. A bank-to-broker transfer is a distinct existing activity; a trade is not income, spending, or a cash transfer.
- Buy commission increases economic basis. Sell commission reduces net proceeds and realized economic P&L but never remaining basis. Tax meaning is not inferred from commission or sign.
- Position replay never permits negative quantity. Partial sale allocation, full close, reopen, backdated insertion, and reversal follow `WEIGHTED_AVERAGE_ECONOMIC_V1`; identical economic facts/order always yield identical quantity, remaining basis, and cumulative realized P&L regardless of commit order.
- Original trade and security/money postings are immutable. Reversal preserves audit links and causes the same current projection as omitting the original while leaving the original/reversal visible.
- Current-action cash debits honor hard/soft/authorized/reality policy behavior. Historical trade cash truth may record a policy breach, but historical mode never authorizes short inventory or an ambiguous economic order.
- A used idempotency key with the same material payload returns one original trade snapshot; a changed payload conflicts. Concurrent commits cannot overspend cash, oversell quantity, create duplicate economic order, or lose a projection update.
- Backdated trade/reversal cash effects participate in exact as-of balances and make affected reconciliation evidence stale by effective time. The position projection is current only after successful synchronous replay; immutable facts remain authoritative if a later read detects a stale/failed projection state.

## Required tests

### Pure/domain

- Currency-minor-unit `HALF_EVEN` gross settlement, canonical decimal scale equivalence, zero/negative/overflow rejection, zero versus non-zero commission posting shape, and exact buy/sell cash equations.
- Hand-worked `WEIGHTED_AVERAGE_ECONOMIC_V1` fixtures: one buy/sell with fees; multiple buys then partial sell; scale-18 allocation remainder; exact full close; reopen; gain and loss; fractional quantity; different decimal scales; backdated insertion; same-time explicit order; reversal equivalence; and invalid short history.
- Pin the allocation remainder oracle: a position with quantity `3` and basis `1.000000000000000000` sells one unit to allocate `0.333333333333333333`, leaving `0.666666666666666667`; a second one-unit sale allocates `0.333333333333333334` by `HALF_EVEN`, leaving `0.333333333333333333`; the final full close consumes that exact remainder.
- Projection metadata/status transitions and deterministic replay independent of request insertion order.

### PostgreSQL/Testcontainers

- Fresh V6 and V5-to-V6 migration plus Hibernate mapping validation; preservation of V5 cash/reconciliation facts and owner/global instruments.
- Raw SQL acceptance/rejection for activity type/policy/order, money role/sign/zero, security role/quantity/price/gross/time/order/owner/account/instrument/currency/reversal shapes, projection uniqueness/non-negative/full-close/status/version constraints, and append-only triggers.
- Atomic funded buy, partial/full sell, close/reopen, historical policy breach, backdated rebuild, trade reversal, double reversal, invalid dependent reversal rollback, idempotent retry/material conflict, and reconciliation staleness.
- Concurrency proof for first-position creation, simultaneous buys against cash, simultaneous sells against quantity, stale cash/position preview versions, duplicate economic order, deterministic lock behavior, and full transaction rollback.
- Owner cleanup/cascade behavior and cross-owner account/instrument/trade/position isolation.
- `JdbcClient` trade/position filters, deterministic pagination/tie-breakers, closed-position list omission/detail availability, and no N+1 activity/security-posting reads.

### HTTP/security

- Authenticated preview/commit/list/detail/reversal happy paths for buy, partial sell, full close, reopen, fee/no-fee, current, historical, global instrument, and owner instrument.
- Exact canonical string fields, calculated gross, cash/position versions, projection metadata, `201`/`Location`, no-store headers, extended activity security postings, and generated OpenAPI required fields.
- Unauthenticated rejection; owner-only account/instrument/trade/position access; inactive-current-buy rules; unsupported account/tracking mode/instrument type; currency mismatch; future time; invalid decimal/precision; insufficient funds/position; policy confirmation; version/economic-order/idempotency conflicts; reversal restrictions; and safe RFC 9457 details.
- Existing cash activity, transfer, account, reconciliation, reference, and bearer-security HTTP tests remain green. No frontend verification is part of this backend-only unit.

## Acceptance criteria

1. After funding an owned full-ledger brokerage account through the existing transfer API, an owner can preview and commit an `EQUITY`/`ETF` buy and see exact native cash, quantity, and fee-capitalized economic basis effects.
2. The owner can preview and commit fee-aware partial and full sells; quantity cannot go negative, allocated basis follows the documented scale-18 rule, full close leaves zero quantity/basis, and reopen starts a new basis cycle.
3. Backdated trades and reasoned reversals rebuild the same projection for the same effective history and explicit order regardless of request insertion order; invalid candidate history rolls back atomically.
4. Trade commands are owner-scoped, selected-brokerage-cash funded, policy-aware, version-checked, idempotent, and concurrency-safe. They cannot silently use a bank account, invent cash, duplicate a trade, overspend, oversell, or lose an update.
5. PostgreSQL owns and enforces the authorized activity/money/security/projection shapes and append-only facts. Empty-to-V6 and V5-to-V6 paths validate with Hibernate while preserving accepted V5 data.
6. Owner-scoped trade/activity detail and paginated history expose complete cash and security facts; open-position list and exact position detail expose current calculation/projection metadata without portfolio, valuation, import, or frontend work.
7. No tax, FX, pending settlement, holdings-only opening, portfolio grouping, corporate action, import, background runtime, or speculative future table/abstraction enters the change surface.
8. Focused financial/migration/concurrency/HTTP gates, complete backend `test` and `verify`, Spotless, OpenAPI tests, and diff checks pass with no skipped required tests.

## Documentation completion

Before this implementation unit is considered complete:

1. Update `docs/implementation/STATE.md` with implemented trade/position capability, V6 schema, verified projection/rounding decisions, deferred neighboring R4 work, and the latest useful backend verification state. Do not mark later R4 behavior complete.
2. Replace or remove obsolete STATE assertions rather than appending a history trail. Keep detailed implementation evidence in this specification's Completion Record and Git history.
3. Update `docs/review/progress-report.md` for the verified R4/Stage 2 checkpoint. Change `accounting-contract.md` only if implementation proves that an authoritative shared semantic must change, and update its hand-worked fixture rule before code relies on the change.
4. Move only reusable Windows, Maven, Docker/Testcontainers, or tool-output lessons to `docs/engineering/codex-command-playbook.md`.
5. Do not update `docs/implementation/web/STATE.md` or any file under `web/`; this PR is backend-only.

## Verification commands

Run focused investing, ledger-regression, migration, concurrency, and HTTP/security tests first from `server/` using the concrete test class names created by the implementation, then run the full backend gates:

```powershell
.\mvnw.cmd spotless:check
.\mvnw.cmd test
.\mvnw.cmd verify
```

Also run the repository's OpenAPI HTTP/configuration tests and `git diff --check`. Do not start the frontend toolchain, edit generated frontend schema, skip PostgreSQL/Testcontainers tests, or substitute an in-memory database.

## Completion record

Fill this before marking the PR complete.

### Implemented

- Added V6 with immutable owner-scoped security postings and mutable synchronous position projections, currency/account/instrument constraints, unique economic ordering, reversal links, append-only protections, and deferred trade/reversal shape checks. The V5-to-V6 fixture preserves fee/interest facts, reconciliation history, and owner/global instruments; JPA validates both tables.
- Added exact minor-unit `HALF_EVEN` settlement and deterministic `WEIGHTED_AVERAGE_ECONOMIC_V1` replay, including fee capitalization, scale-18 disposal allocation, full close/reopen, backdated insertion, and reversal equivalence.
- Added owner-scoped preview/commit and trade/position reads. Commits reuse the existing brokerage cash pocket, policy evaluation, idempotency snapshots, locking, cash-balance version, and transaction boundary. Existing activity reversal now writes linked inverse money/security facts and rebuilds the position atomically.
- Extended activity detail/history with security postings and added authenticated pageable trade history, open-position list, exact current/closed position detail, stable errors, no-store responses, `201`/`Location`, and OpenAPI contract coverage.
- Updated the reference schema inventory test for the V6 composite instrument/currency key required by security-posting currency integrity.

### Deviations from specification

- None. The implementation stays backend-only and does not introduce the specified non-goal capabilities.

### New decisions

- Normalize request `effectiveAt` to PostgreSQL `timestamptz` microsecond precision before future-time validation, idempotency hashing, and replay so persisted order and request identity use the same instant.
- Enforce Java/PostgreSQL `HALF_EVEN` agreement in the deferred gross check explicitly; PostgreSQL's normal numeric `round` rounds midpoint values differently. The checked midpoint fixture persists `1.025` as `1.02` for a two-decimal currency.
- Translate arithmetic overflow or unrepresentable settled cash/position values to stable `INVALID_SETTLED_PRECISION` before facts can commit.

### Tests executed

- `.\mvnw.cmd "-Dtest=FinancialAccountMigrationTest,InvestingTradeHttpTest,InvestingTradeConcurrencyTest,WeightedAverageEconomicV1Test,OpenApiHttpTest,FinancialAccountServiceTest,FinancialAccountHttpTest,FinancialAccountMappingTest,CashActivityServiceTest,CashActivityHttpTest,CashLedgerConcurrencyTest,LedgerReconciliationServiceTest,LedgerReconciliationHttpTest,LedgerReconciliationConcurrencyTest,LedgerTransactionRollbackTest,LedgerDomainInvariantTest,LedgerValueObjectTest,ContextSmokeTest,ReferenceCatalogMigrationTest" test` — 169 passed, 0 failures/errors/skips against PostgreSQL 17 Testcontainers.
- `.\mvnw.cmd test` — 408 passed, 0 failures/errors/skips against PostgreSQL 17 Testcontainers.
- `.\mvnw.cmd spotless:check` — passed; all 290 Java files clean.
- `.\mvnw.cmd verify` — passed; 408 tests, 0 failures/errors/skips, including Spotless and executable archive packaging.
- `.\mvnw.cmd "-Dtest=ReferenceCatalogMigrationTest" test` — 8 passed, 0 failures/errors/skips after updating its V6 reference constraint/index inventory.
- `git diff --check` — passed after the final documentation and code changes.

### Follow-up work

- None within PR-030. Portfolio grouping, imports, holdings-only openings, tax, FX/multi-currency, pending settlement, valuation, corporate actions/income, asynchronous infrastructure, and frontend work remain deferred as specified.
