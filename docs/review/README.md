# Project review: backend, analytics, product direction

Review dates: 2026-08-04 to 2026-08-05

This folder preserves a backend-first review of the Spring Boot application and a lighter review of the React client under `src/main/web`. No production code was changed as part of the review.

## Documents

- [backend-master-plan.md](backend-master-plan.md) — the authoritative scratch-rewrite plan targeting `extreme_accounting`: exact R0–R16 implementation increments, supersession rules, migration-owned DDL/minimal JPA mapping, pragmatic modular-monolith packages, ledger/data architecture, deterministic fake/manual data, all 32 features, tests and gates.
- [accounting-contract.md](accounting-contract.md) — the shared implementation contract for financial semantics: numeric/time ordering, immutable corrections, posting signs, fees/tax, cost basis, FX/source selection, balances, valuation/performance, projection states, idempotency and golden fixtures.
- [../engineering/coding-standards.md](../engineering/coding-standards.md) — repository-wide Java 25, Spring Boot 4, JPA/Hibernate, SQL/Flyway, API and testing conventions used by coding agents.
- [../implementation/README.md](../implementation/README.md) — incremental PR execution rules; `CURRENT.md` identifies the active implementation specification so prompts do not need to repeat it.
- [progress-report.md](progress-report.md) — the current checkpoint covering the repository PostgreSQL dump, `extreme_accounting` target, repository inventory, completed design work, unresolved risks, rewrite-increment status, and exact next-session starting point.
- [db-dump.sql](db-dump.sql) — the legacy PostgreSQL schema snapshot retained as evidence only; it must not be executed as the new baseline.
- [implementable-features.md](implementable-features.md) — a research-backed catalogue of 32 implementable investment and broader-money features, including spending, personal IOUs, shared expenses, multi-account cash/funding, bills/subscriptions, shopping/receipts, cards, income, physical-asset ownership/TCO, documents, backend/API rules, sequencing, and validation.
- [cash-accounts-and-funding-design.md](cash-accounts-and-funding-design.md) — the detailed multi-account cash model, funding-source selection, account postings, balance definitions, overdraft/negative behavior, trade/bill/card examples, API shape, migration, and tests.
- [real-asset-lifecycle-tco-design.md](real-asset-lifecycle-tco-design.md) — the vehicle-first, reusable physical-asset lifecycle/TCO model: cash versus economic cost, depreciation and valuation, usage meters, maintenance, warranty, financing, disposal, scenarios, APIs, and invariants.
- [backend-audit.md](backend-audit.md) — current architecture, correctness and security findings, schema/API/operations risks, and the small set of confirmed frontend issues that expose backend contract problems.
- [business-logic-and-analytics-design.md](business-logic-and-analytics-design.md) — a proposed ledger, valuation, performance, and counterfactual-comparison model.
- [product-direction.md](product-direction.md) — differentiated product ideas, competitor baseline, broader asset coverage, and staged product scope.
- [mobile-api-readiness.md](mobile-api-readiness.md) — changes worth making before an Expo/React Native client makes the current API expensive to evolve.
- [prioritized-roadmap.md](prioritized-roadmap.md) — the original correctness-first roadmap retained as historical analysis; any in-place repair/backfill steps that conflict with the scratch rewrite are superseded by `backend-master-plan.md`.

## Current implementation assumptions

Manual entry and file import are permanent primary workflows. The near-term/R0–R16 roadmap does not include Open Banking, bank/card synchronization, broker synchronization, or payment initiation. Users can start from explicit opening balances/state with a historical-coverage boundary, then optionally import prior history. Planned payments may reference intended funding accounts for reminders/forecasting, but actual payment is recorded/confirmed manually.

## Rewrite technology baseline

The legacy backend being replaced is Spring Boot 3.5.3/Java 21, but the scratch rewrite starts directly on the current stable modern baseline:

- Java 25; final features allowed, preview/incubator/experimental JDK features off by default;
- Spring Boot 4.1.0 pinned as the starting framework release;
- Spring Framework 7.0.8, Spring Data JPA 4.1.0 and Hibernate ORM 7.4.1.Final through Spring Boot dependency management;
- Jakarta Persistence 3.2 stable specification and its stable modern features where useful;
- no Jakarta Persistence 4, Hibernate ORM 8, framework snapshots/milestones/RCs, or opportunistic overrides of Boot-managed Spring/Hibernate/Jackson versions in normal feature PRs.

Root `AGENTS.md` is the coding-agent entry point. It delegates implementation style to `docs/engineering/coding-standards.md` and execution scope to the PR selected by `docs/implementation/CURRENT.md`, so these requirements do not need to be repeated in each coding prompt.

## Executive assessment

The project is a promising modular monolith. It already has useful boundaries for accounts, portfolios, instruments, market data, dashboards, and integrations. PostgreSQL, Flyway, `BigDecimal`, OpenAPI-generated client types, owner-filtered queries, and thin controllers are good foundations.

The next milestone should be **trustworthy financial accounting**, not more dashboard cards. Several current calculations can produce convincing but incorrect results:

1. Commissions enter the API but are always stored as zero and do not affect cost basis or profit.
2. Backdated trades are applied to the current position in request/insertion order, not rebuilt in economic-date order.
3. Dashboard daily change mixes market performance with cash flows and uses record creation time for position history.
4. Historical returns are converted with today's single mutable FX rate; historical FX performance cannot be calculated.
5. Portfolio screens add positions with different currencies as if their amounts were comparable.
6. Concurrent or retried trade requests can create duplicate positions/trades or lose updates.

There are also immediate platform risks:

- A private JWT signing key is committed to the repository. If it has ever been used outside disposable local development, rotate it and invalidate the corresponding tokens.
- Google ID token verification does not visibly enforce this application's Google client ID/audience.
- A fresh database cannot be reconstructed from the repository: there is no `V1` baseline, while `V2` starts by altering pre-existing tables.
- The migration that creates `instrument.market_currencies` does not populate it, although the instrument list uses an inner join to that table.
- The only backend test is `contextLoads`; no accounting, authorization, migration, or API behavior is protected.

## Recommended product position

Do not try to differentiate mainly through portfolio totals, dividends, allocation charts, generic AI commentary, or basic benchmark charts. Current portfolio products already cover much of that.

A stronger product thesis is:

> A personal financial decision engine that reconstructs what actually happened, explains why it happened, and compares it fairly with the alternatives the user realistically had.

The flagship feature can be **Decision Replay**:

- Replay the user's real cash-flow dates.
- Compare the actual choice with a share, index, gold, FX conversion, term deposit, inflation, debt repayment, or a user-defined asset.
- Apply fees, taxes, dividends, corporate actions, compounding rules, and point-in-time FX.
- Show not only ending value but purchasing power, drawdown, volatility, time ahead/behind, and the contribution of asset return versus FX versus fees and timing.
- State data sources, missing observations, and assumptions in every result.

This is more defensible than a one-off “what if I invested” calculator because it uses the user's actual history and can become a decision journal, behavioral feedback loop, and goal-planning system.

## First actions

1. Treat the current JWT private key as exposed, rotate it, and move signing material out of the classpath.
2. Replace the legacy toolchain with a reproducible Java 25 + Spring Boot 4.1.0 build and a disposable PostgreSQL/Testcontainers integration-test path.
3. Create and verify a complete database baseline; do not add new domain tables before fresh-database recovery is reliable.
4. Lock down the accounting contract: fee treatment, cost-basis method, economic ordering, rounding, FX timing, and definition of each dashboard metric.
5. Add characterization tests for existing trades, then replace insertion-order position mutation with deterministic ledger replay/projection.
6. Only after those foundations, build historical prices/FX and the first Decision Replay vertical slice.

## Verification performed during this review

- Read the README, Maven/Docker/configuration files, all migrations, backend entities/services/controllers/repositories, MyBatis queries, scheduled integrations, account/security code, OpenAPI usage, and the main React portfolio/trade/dashboard/auth flows.
- `npm.cmd run build` completed successfully. It reported one CSS typo (`paddig-top`) and generated a production PWA bundle.
- A plain `.\mvnw.cmd test` failed during compilation under the active JDK 25.0.3 because Lombok annotation processing did not run. With `MAVEN_OPTS=-Dmaven.compiler.proc=full`, compilation and the single `contextLoads` test passed against the existing local PostgreSQL 15.1 database. The legacy Docker build uses Java 21, so this was a toolchain/reproducibility issue in the code being replaced; the scratch rewrite target is Java 25 + Spring Boot 4.1.0.
- The successful context test validates the existing local schema enough for startup but does not prove that an empty database can migrate. Findings that depend on the undocumented pre-Flyway baseline or destructive behavior are explicitly marked as requiring a dedicated integration test/schema inspection.
