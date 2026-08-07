# Prioritized implementation roadmap

## Status: historical roadmap

This document is retained for the reasoning, audit links and exit criteria behind the original correctness-first approach. It is **not** the executable implementation plan after the 2026-08-06/07 scratch-rewrite decision.

[backend-master-plan.md](backend-master-plan.md) is authoritative. Because the application is undeployed and `extreme_accounting` is disposable, instructions here that assume in-place migration, repair of the current `V2`–`V14` schema, legacy-row backfill, or old/new shadow projections are superseded. Reuse their tests/definitions where useful, but execute R0–R16 from the master plan.

Shared financial semantics are governed by [accounting-contract.md](accounting-contract.md).

## Current product-scope clarification

The current scratch-rewrite technology target also supersedes older toolchain assumptions in this historical roadmap: use Java 25 and Spring Boot 4.1.0 with Boot-managed Spring Data/Hibernate dependencies and Jakarta Persistence 3.2. Do not recreate the legacy Java 21/Spring Boot 3.5 baseline as an intermediate step.

For current implementation, manual entry and file import are permanent primary workflows. Bank/Open Banking connections, card/broker synchronization and payment initiation are deferred beyond the authoritative R0–R16 plan. Any historical item below that assumes connected-account ingestion or in-place legacy-data repair must not be used to introduce speculative connection infrastructure. Opening balances/state and explicit historical-coverage metadata are the supported way for a user to begin from current known financial state.

## Ordering principle

Build in this order:

1. Contain security/data-recovery risk.
2. Make current financial results correct and testable.
3. Establish ledger and time-series foundations.
4. Add explainable analytics and the differentiated comparison engine.
5. Freeze a mobile-safe API contract and build Expo against it.

Adding more charts before steps 1–3 increases the amount of UI built on definitions that must later change.

## Stage 0 — Immediate containment

### 0.1 Rotate and externalize JWT signing keys

Related finding: `SEC-001`.

Exit criteria:

- Old production/shared key no longer verifies tokens.
- No production private key exists in the repository/image/classpath.
- Key ID and rotation runbook exist.
- Local development has a safe documented key-generation flow.

### 0.2 Correct Google verification

Related finding: `SEC-002`.

Exit criteria:

- Issuer, exact allowed audience/client IDs, expiry, and verified email are checked.
- A token issued to another client is rejected in an automated security test.
- Web/iOS/Android client identities are modeled explicitly.

### 0.3 Freeze claims that are known to be false

Until fixed:

- Remove/hide the hardcoded “Outperforming S&P 500” result.
- Mark mixed-currency portfolio aggregation unsupported rather than summing unlike values.
- Show missing/stale price coverage rather than falling back silently to average cost.
- State that commissions are not correctly included, or fix them before exposing realized results to more users.

Exit criteria: the UI never presents a known-placeholder result as calculated financial fact.

## Stage 1 — Reproducible database and test harness

### 1.1 Establish the canonical deployed schema

Related findings: `DB-001` through `DB-007`.

Tasks:

- Take a schema-only dump of a sanitized production-equivalent database, including constraints, functions, enums, indexes, defaults, and Flyway history.
- Diff it against all JPA mappings and migrations.
- Decide the safe Flyway baseline/repair procedure for existing environments.
- Create a reviewed empty-database baseline and upgrade migrations.
- Seed countries/currencies/markets/market currencies deterministically.
- Repair V11-derived transaction values from replayable history or mark unrecoverable rows for user review.
- Remove/replace legacy snapshot SQL.

Exit criteria:

- A Testcontainers PostgreSQL database migrates empty → latest and the Spring context starts.
- A copied pre-change schema upgrades → latest without manual SQL.
- `/api/instruments` returns seeded supported currencies.
- Hibernate validation passes.
- A backup/restore rehearsal is documented.

### 1.2 Pin the build toolchain and add CI gates

Related findings: `OPS-001` through `OPS-003`.

Tasks:

- Enforce Java 25 with Maven Enforcer/toolchains; disable preview features.
- Declare required annotation processors explicitly or fail clearly on unsupported JDKs.
- Add backend unit and PostgreSQL integration jobs.
- Add empty/upgrade migration jobs.
- Add React typecheck/build (including the CSS typo fix).
- Build and smoke-test the container without `-DskipTests` being the only gate.
- Add dependency/secret scanning and OpenAPI compatibility later in this stage.

Exit criteria: a clean checkout builds reproducibly in CI and locally with one documented command.

### 1.3 Create financial characterization fixtures

Do this before large refactors. Capture current intended behavior and explicitly mark current bugs.

Fixtures:

- buy/sell/full close/reopen;
- multiple buys and partial sell;
- commissions on both sides;
- decimal quantities with different scales;
- bulk imports and same-timestamp order;
- backdated insert;
- two currencies and missing snapshots;
- cross-user access attempts;
- onboarding repeated request;
- clear-my-data with child transactions.

Exit criteria: every P0/P1 financial finding has a failing regression test or a documented reason it cannot yet be represented.

## Stage 2 — Repair the current accounting path

### 2.1 Define the accounting contract

Write ADRs for:

- economic ordering and tie-breakers;
- weighted average versus lot policies;
- buy/sell fee and tax treatment;
- trade versus settlement date;
- rounding/scale;
- corrections/reversals;
- reporting-currency and historical FX selection;
- definitions of balance, P&L, daily change, TWR, and XIRR.

Exit criteria: product labels, Java methods, SQL, and test fixtures use the same definitions.

### 2.2 Fix fee/currency/numeric defects in existing APIs

Related findings: `FIN-001`, `FIN-008`, `FIN-009`, `API-006`.

Tasks:

- Persist commission exactly and apply the approved basis/proceeds treatment.
- Make fee non-null and validate its currency.
- Use numeric comparisons (`compareTo`) for zero/equality.
- Carry currency through AI preview, bulk request, domain, and response.
- Normalize/validate currency codes.
- Remove the sell form's quantity cap of five; expose available quantity from the server.
- Reconcile all existing rows and decide how unknown legacy commission is represented.

Exit criteria: cash/profit reconciliation tests pass and no import silently changes currency.

### 2.3 Make current trade writes deterministic and safe

Related findings: `FIN-002`, `FIN-004`, `FIN-011`, `API-005`.

Tasks:

- Add unique position identity and optimistic/pessimistic locking.
- Add client event ID/idempotency.
- Replay a position in economic order after any write.
- Replace destructive “undo latest” with correction/reversal and audit trail.
- Validate historical quantity at each event.
- Return conflict/problem codes that clients can handle.

Exit criteria:

- Adding the same request twice creates one economic event.
- Parallel buys do not lose quantity/cost.
- Adding a backdated trade produces the same projection as importing the full history in correct order.
- Corrections preserve the original fact and audit relationship.

### 2.4 Fix account/onboarding lifecycle

Tasks:

- Make onboarding idempotent and atomic.
- Either process initial trades or remove them from the request contract.
- Validate portfolio/dashboard names, colors, currency, and portfolio membership.
- Make clear-my-data an explicit scoped deletion workflow with FK-safe ordering and an audit/security event.
- Add export, full account deletion, logout, refresh-session listing/revocation.
- Define archive/unarchive/delete behavior and dashboard membership cleanup.

Exit criteria: repeated/retried lifecycle calls are safe and integration-tested.

## Stage 3 — Ledger and market-data foundation

### 3.1 Introduce accounts and immutable activities

Follow [business-logic-and-analytics-design.md](business-logic-and-analytics-design.md) and the account/funding rules in [cash-accounts-and-funding-design.md](cash-accounts-and-funding-design.md).

Start with:

- cash deposit/withdrawal;
- buy/sell with cash legs;
- fee/tax;
- transfer;
- dividend/interest;
- correction/reversal.

Backfill current transactions, run the new projection in shadow mode, and produce a reconciliation report per position/user.

Exit criteria:

- Security quantity, cash, cost basis, and realized P&L reconcile for golden fixtures.
- Existing and new projections match or every difference is classified.
- Current APIs can be served from the new projection through adapters.

### 3.2 Add immutable prices and FX history

Related findings: `FIN-003`, `FIN-006`, `OPS-005`, `OPS-006`.

Tasks:

- Provider/series/observation/ingestion-run schema.
- Historical adjusted price and FX adapters.
- Latest projection for fast screens.
- Market calendars, timezone, holiday/missing-data rules.
- Source licensing/attribution and retention record.
- Staleness/coverage monitoring.
- Corporate actions or a documented adjusted-total-return source.

Exit criteria:

- Any valuation can state as-of/source/quality.
- A historical transaction uses date-appropriate FX.
- Source revisions trigger deterministic recalculation.
- Missing price data remains visible, not replaced with cost.

### 3.3 Add rebuildable daily valuation

Tasks:

- Daily/end-of-period account and portfolio NAV.
- Native/reporting currency values.
- Cash/liabilities/unpriced coverage.
- Rebuild range after backdated activity or market-data revision.
- Job locks and observable rebuild state.

Exit criteria: opening value + flows + decomposed P&L reconciles to closing value for every test period.

## Stage 4 — Honest analytics

### 4.1 Replace dashboard statistics

Related finding: `FIN-005`.

Deliver:

- NAV change and net external flow separately;
- asset-price, FX, income, fee, and tax contributions;
- TWR and XIRR for declared periods;
- realized/unrealized P&L under named basis policy;
- valuation as-of and coverage.

Exit criteria: no dashboard metric is computed independently in React, and all values trace to tested backend definitions.

### 4.2 Add benchmark and risk analytics

Deliver only after total-return/time-series foundations:

- aligned benchmark total return in reporting currency;
- drawdown/recovery, volatility/downside deviation;
- concentration and currency exposure;
- fee/tax/cash drag;
- arbitrary date ranges.

Exit criteria: benchmark results state data/methodology and cannot use future observations.

## Stage 5 — Decision Replay flagship

### 5.1 Scenario engine MVP

Deliver:

- one amount/start/end/reporting currency;
- one actual or selected asset;
- market instrument, FX holding, gold/index, and simple deposit alternatives;
- nominal/real ending value, delta, timeline, max drawdown;
- assumptions and source panel;
- saved, immutable scenario version.

Exit criteria:

- Pure calculation core passes golden examples.
- Same cash flow and point-in-time data are used for every alternative.
- Every output links to a calculation/data version.

### 5.2 Real cash-flow replay and localized alternatives

Add:

- actual account/portfolio cash-flow timeline;
- rolling/fixed deposit schedules, compounding, day count, withholding;
- debt prepayment;
- dividends/fees/taxes and reinvestment choices;
- personal inflation/goal baskets;
- path comparison and time-in-front;
- decision journal review.

Exit criteria: the user's example (share vs deposit vs currency vs other asset) is reproducible, auditable, and honest about assumptions.

## Stage 6 — Stable API and Expo app

Complete the checklist in `mobile-api-readiness.md`:

- `/api/v1`, opaque string IDs, decimal strings;
- device refresh sessions and Google PKCE/OIDC;
- cursor pagination and stable problems;
- client idempotency/outbox and sync change feed;
- OpenAPI compatibility CI;
- compact mobile home/read projections;
- import preview/background resume;
- export/delete/session management.

Build the first Expo experience around high-value low-risk jobs:

1. Secure login/session control.
2. Home valuation with freshness.
3. Activity/transaction entry that works offline and retries safely.
4. Positions/activity feed.
5. Saved Decision Replay viewing and simple scenario creation.

Avoid implementing complex accounting in the app; it should display backend-calculated results.

## Post-core vertical sequence — Everyday money, then physical-asset TCO

The broad feature catalogue is in [implementable-features.md](implementable-features.md). After Stages 0–6 prove the trusted ledger, valuation, calculation, and mobile contracts, expand in this dependency order:

1. Reconciled spending/income, bills, cards/installments, documents, and account funding.
2. Receipt/purchase lifecycle, refunds/recoveries, insurance/protection links, and manual illiquid-asset valuations.
3. The vehicle-first [Real Asset Lifecycle and TCO design](real-asset-lifecycle-tco-design.md): acquisition/funding/debt, mileage, linked costs, current value, economic cost per kilometre/month, service/warranty state, and disposal.
4. Keep/repair/replace scenarios and then one validated non-vehicle template at a time.

Do not start with telematics, automatic valuation, predictive maintenance, or a generic physical-asset catalogue. The first exit gate is a manually operable vehicle workflow in which cash burden, economic TCO, net-worth value, forecast, usage denominator, and every source fact reconcile independently.

## First concrete backlog

This is the suggested first set of tickets, in order:

1. Security incident task: rotate the tracked RSA key and externalize key loading.
2. Google auth: enforce issuer/audience/verified-email and add rejection fixtures.
3. Database archaeology: capture production-equivalent schema/Flyway history and write the baseline plan.
4. Testcontainers: empty-to-latest migration smoke test.
5. Toolchain: Java 25 enforcement, Spring Boot 4.1.0 baseline, Boot-managed dependency versions and explicit annotation processing; Maven CI job.
6. Domain tests: fee-aware buy/partial sell/full sell with scale variations.
7. Fix commission persistence/calculation and migrate/reconcile affected records.
8. Add/seed `market_currencies` and currency FKs/normalization.
9. Fix bulk/import currency propagation and file validation/reconciliation behavior.
10. Add position uniqueness, locking, and idempotency storage.
11. Implement deterministic replay for backdated inserts and replacement for undo.
12. Add current dashboard valuation coverage/as-of metadata; remove false UI fallbacks/benchmark claim.
13. Design ledger/account/activity schema ADR and migration.
14. Design immutable price/FX observation schema and provider policy.
15. Publish API v1/decimal/error/idempotency ADR before Expo feature work.

## Release gates

### Gate A — Safe to trust current tracking

- Key/auth P0s closed.
- Fresh/upgrade migrations pass.
- Fees, currency, economic order, and concurrency tests pass.
- No known false aggregate is displayed.

### Gate B — Safe to call it analytics

- Cash/external flows represented.
- Historical price/FX and daily NAV exist.
- TWR/XIRR/decomposition fixtures reconcile.
- Missing/stale data is visible.

### Gate C — Safe to market Decision Replay

- Scenario calculation is reproducible/versioned.
- Alternatives use fair cash flows and point-in-time data.
- Deposit/tax/FX assumptions are visible.
- Historical facts and forward assumptions are clearly separated.

### Gate D — Safe for a public mobile client

- Versioned compatible API.
- Device-aware revocable auth.
- Idempotent writes and sync tested under offline/retry.
- Decimal/time/error contracts stable.
- Privacy, export, deletion, and import retention flows work.
