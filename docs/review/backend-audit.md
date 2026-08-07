# Backend audit

Review date: 2026-08-04

## Scope and confidence labels

This audit prioritizes business correctness over style. It covers Java/Spring code, SQL migrations, MyBatis/JPA/JDBC persistence, security, integrations, the public API contract, operations, tests, and frontend behavior that reveals a backend contract issue.

Labels used below:

- **Confirmed** — directly visible in the code or reproduced by a build.
- **Probable** — the code strongly implies the result, but a real PostgreSQL/integration test is needed because the initial schema is missing.
- **Strategic gap** — not necessarily a bug in the current scope, but required before the product can make broader financial claims.

Severity:

- **P0** — security incident potential, unrecoverable data/bootstrap failure, or materially wrong financial result.
- **P1** — high user impact or a blocker for dependable growth/mobile work.
- **P2** — maintainability, UX, or operational quality issue.

## Current architecture

The application is a single Spring Boot 3.5.3 / Java 21 service with an embedded React/Vite project.

Backend responsibilities currently include:

- Local email/password and Google authentication with RSA-signed JWTs.
- User onboarding, portfolios, dashboards, positions, and buy/sell transactions.
- Stock and crypto instrument subtypes and per-market supported currencies.
- Current instrument snapshots and current currency rates.
- BIST instrument import and scheduled BIST price updates.
- Dashboard balance, daily change, realized P&L, and trade history.
- AI-assisted statement parsing with Gemini.

Persistence is intentionally mixed:

- JPA/Hibernate for entities and writes.
- QueryDSL for selected projections and aggregate reads.
- MyBatis for position, instrument, and transaction list views.
- `NamedParameterJdbcTemplate` for dashboard statistics.
- `JdbcTemplate` batch upserts for market snapshots.

This is a reasonable modular-monolith approach. The main weakness is not the technology mix; it is that the same financial definitions are distributed across entities, SQL, QueryDSL, and the React client with no test suite enforcing one accounting contract.

The main schemas are:

- `account`: users, roles, permissions.
- `portfolio`: portfolios, dashboards, positions, transactions, histories/performance.
- `instrument`: markets, instruments, subtype tables, supported currencies, current snapshots.
- `public`: currencies, countries, and the `convert_currency` function.

## What is already good

- Money and quantities use `BigDecimal` in the Java domain and high-precision PostgreSQL numerics in the newer migrations.
- Most portfolio access paths filter or validate by the authenticated user's ID and intentionally return not-found for foreign resources.
- Controllers are generally thin and write transactions are mostly service-bound.
- `open-in-view` is disabled, which prevents accidental lazy-loading APIs.
- Database migrations are separated from entity validation in production.
- Dashboard currency conversion is centralized in a database function instead of being copied into each UI component.
- The market updater abstraction can support additional provider adapters.
- The React client consumes generated OpenAPI types and its production build succeeds.

These are worth keeping while the accounting core is rebuilt.

## P0 findings

### FIN-001 — Commission is accepted and then discarded

**Status:** Confirmed.

Evidence:

- `TradeRequest` and `BulkTransactionRequest` contain `commission`.
- `Position.buy(...)` and `Position.sell(...)` receive it.
- `Transaction.buy(...)` and `Transaction.sell(...)` explicitly set `transaction.commission = BigDecimal.ZERO`.
- Position cost, realized profit, return percentage, dashboard realized gains, and trade totals all exclude commission.
- The React buy/sell forms also submit `commission: 0` regardless of their form state.

Impact:

- Stored facts are false.
- Cost basis, realized profit, total return, win rate, and comparisons are overstated.
- The original commission cannot be recovered later from persisted data.

Required decision:

- Define whether buy fees are capitalized into cost basis, sell fees reduce proceeds, and taxes are distinct cash flows. This may vary by jurisdiction, so store raw fee/tax facts even if the displayed calculation policy is configurable.

Minimum acceptance tests:

- A buy with a fee stores that exact fee and increases configured cost basis.
- A partial sell allocates the correct remaining basis and subtracts the sell fee from realized P&L.
- Full lifecycle net profit reconciles cash outflows and inflows exactly.

### FIN-002 — Backdated trades corrupt materialized position state

**Status:** Confirmed by inspection.

`Position.buy` and `Position.sell` mutate today's `quantity` and `total` immediately. `actionDate` is stored on the transaction but does not determine replay order. The bulk endpoint sorts only the submitted batch; it does not merge it chronologically with transactions already stored for that position.

Example failure:

1. A user has buys and sells through June.
2. In August they add a forgotten February buy.
3. The February event is applied after the June state, so every later average-cost and realized-profit calculation remains based on the wrong sequence.

The same problem affects statement imports, edited transactions, and future broker synchronization.

Required fix:

- Treat transactions/activities as the source of truth.
- On insert, edit, delete, import, or corporate action, replay the affected position in `(effectiveAt, deterministic tie-breaker)` order and atomically replace its projections.
- Reject a sell that becomes invalid at any point in that replay, not merely against the current quantity.

### FIN-003 — Current FX rates are used for historical performance

**Status:** Confirmed.

`currencies.exchange_rate` stores one mutable rate per currency. `public.convert_currency` always reads that value. Realized gains from old trades and dashboard transaction profit are therefore translated using today's rate, not the trade-date rate. The model cannot show the FX contribution to return or accurately compare a historical asset with holding another currency.

Required fix:

- Store timestamped FX observations with source, base/quote convention, observation time, and quality.
- Define point-in-time lookup rules for market holidays and missing observations.
- Keep original-currency amounts in all facts. Convert only in valuation/reporting projections.

### DB-001 — A fresh database cannot be created from this repository

**Status:** Confirmed from the tracked migrations.

- `schema.sql` contains only `;`.
- There is no `V1` migration.
- `V2__portfolios.sql` immediately alters `holdings` and `holding_history` and references `users` and `trades` that must already exist.
- Flyway is enabled in production, so a disaster recovery or clean environment depends on undocumented database history.

This is a release/recovery blocker. Export a reviewed baseline from the intended schema, test `migrate` from empty PostgreSQL, and separately test upgrading a copy of the existing production schema. Do not casually insert a new `V1` into a database whose Flyway history is already live; use Flyway baseline/repair deliberately and rehearse it on a copy.

### DB-002 — The V11 transaction backfill invents position state

**Status:** Confirmed.

`V11__transaction_position_values.sql` adds `new_quantity` and `new_total` to every existing transaction with `NOT NULL DEFAULT 1`. It does not reconstruct the actual post-transaction values.

Consequences:

- Historical transaction rows contain fabricated state.
- `TradeMapper.fetchActiveTrades` finds the last transaction with `new_quantity = 0`; migrated transactions defaulted to `1`, so old closed cycles may be treated as active forever.
- Any later replay or audit that trusts those columns is unsafe.

Write a repair migration/job that replays each position's transactions in economic order and records computed values. First decide how to handle legacy commission and action-time ambiguities.

### DB-003 — Supported market currencies are created but not seeded

**Status:** Confirmed from repository migrations; verify deployed data.

`V13__multi_currency_instrument.sql` creates `instrument.market_currencies` but inserts no rows. `InstrumentMapper.xml` uses an inner join to that table, so a database produced only by these migrations would return no instruments from `/api/instruments`. Trade creation also requires a matching `MarketCurrencyId`.

Seed valid rows for every market in the migration/baseline and add a startup/integration assertion that every active tradable instrument's market has at least one supported currency.

### SEC-001 — A JWT private signing key is committed

**Status:** Confirmed.

`src/main/resources/certs/private.pem` is tracked, and `application.yml` loads it from the classpath. A comment already acknowledges the problem.

Actions:

1. Assume the key is exposed if it was used in any shared or production environment.
2. Rotate it and invalidate tokens signed with the old key.
3. Load signing keys from an external secret/keystore/KMS, identify keys with `kid`, and document rotation.
4. Keep only an explicitly disposable development key outside version control, or generate one during local setup.

Do not merely delete it in a future commit; Git history retains it.

### SEC-002 — Google token audience is not visibly restricted to this app

**Status:** Confirmed from configuration; validate framework defaults in a security test.

`TokenController` creates a `NimbusJwtDecoder` from Google's JWK set URI and reads `email`/`name`. No validator for the configured Google client ID (`aud`) is installed, and `email_verified` is not checked. Signature and expiry alone do not prove that the token was issued for this application.

Required fix:

- Use Google's supported verification flow or a decoder with explicit issuer, audience/client ID, time, and nonce requirements.
- Check `email_verified` where appropriate.
- Test a valid token for another Google OAuth client and require rejection.

## P1 financial and domain findings

### FIN-004 — No concurrency or retry protection on trades

**Status:** Confirmed design gap.

- `Position` has no `@Version` optimistic-lock column and trade creation does not lock a position.
- There is no database unique constraint on `(portfolio_id, instrument_id, currency_code)`.
- Mutating endpoints accept no idempotency key or client transaction ID.

Concurrent buys can lose an update or create duplicate position rows. A mobile retry after a timeout can duplicate a trade.

Add the unique constraint, optimistic or pessimistic concurrency around projection rebuilds, and a user-scoped idempotency record/client event ID.

### FIN-005 — “Daily change” includes external cash flow and loses FX movement

**Status:** Confirmed by SQL inspection.

`StatisticsRepository.getDailyChange` compares:

- current price × current quantity, with
- previous close × a quantity taken from `position_history` before the current calendar date.

Problems:

- A purchase today raises current value but is excluded from previous value, so the contribution looks like investment performance.
- History uses `PositionHistory.createdAt`, not the transaction's `actionDate`; imported/backdated trades are assigned to the import day.
- Both sides use the same current FX rate, hiding reporting-currency movement.
- Calendar-day truncation uses database time rather than the market, portfolio, or user's timezone.

Define separate metrics:

- absolute NAV change;
- net external flow;
- market/price P&L;
- FX P&L;
- income/fee/tax P&L;
- time-weighted daily return.

Do not label NAV movement as performance without cash-flow adjustment.

### FIN-006 — Missing prices are silently omitted or replaced with cost

**Status:** Confirmed.

- Dashboard SQL inner-joins `instrument_snapshots`; a position with no matching snapshot disappears from total balance.
- The React portfolio overview falls back from `last` to `avgCost`, making an unknown market value appear unchanged.
- There is no aggregate price-as-of timestamp, stale flag, or coverage count in dashboard responses.

Return valuation quality with every aggregate: valued amount, unpriced amount/positions, oldest observation, source status, and whether a fallback was used. Unknown is better than a plausible false number.

### FIN-007 — Cost basis is implicit and not tax-capable

**Status:** Strategic gap with current correctness impact.

The current algorithm uses one weighted-average cost and proportionally reduces remaining total cost on a sale. That may be suitable for a chosen market/report, but it is not named, versioned, or configurable. It cannot support FIFO, LIFO, specific-lot selection, jurisdictional pooling, wash-sale rules, or lot transfers.

Store immutable lots/allocations or derive them from the ledger under a named `CostBasisPolicy`. Record which policy/version produced every tax report. Keep economic-performance accounting separate from jurisdictional tax accounting.

### FIN-008 — Zero checks use scale-sensitive `BigDecimal.equals`

**Status:** Confirmed.

`Position.getAveragePrice` compares quantity with `BigDecimal.ZERO` using `equals`, and full-sale detection also uses `equals`. `0`, `0.0`, and database-loaded `0E-18` are numerically equal but not `equals`-equal. Use `compareTo(BigDecimal.ZERO) == 0` consistently and test values at different scales.

### FIN-009 — Bulk/import currency is discarded

**Status:** Confirmed.

- Gemini's preview model includes `currency`.
- `parseImportedTransactions` drops it when creating `BulkTransactionRequest`.
- `BulkTransactionRequest.toTransactionRequest()` hardcodes `"TRY"`.
- The manual bulk table also submits no currency and displays its total as USD regardless of row currencies.

This can put a valid transaction in the wrong currency or reject it against the market currency table. Currency must be an explicit required field per imported row, with a review step for unresolved values.

### FIN-010 — The current model cannot reconcile total wealth

**Status:** Strategic gap.

Only security positions are represented. There are no cash accounts, deposits/withdrawals, dividend/interest receipts, tax payments, FX conversion legs, transfers, debt balances, or corporate actions. “Total balance” is therefore market value of priced positions, not portfolio cash balance or net worth.

Before adding broader assets, introduce a cash/activity ledger so every trade can reconcile cash and every return can distinguish contribution from performance.

### FIN-011 — Undo and “latest” use insertion identity, not economic order

**Status:** Confirmed.

Position collections are ordered by descending database ID, `undo()` removes the first entries, and trade history calls a transaction “latest” when no higher ID exists. `actionDate` is not part of the definition. The undo URL's `portfolioId` is also ignored; access is checked against the position's actual portfolio, so a mismatched URL still succeeds.

Replace destructive “undo latest” with transaction correction/reversal plus deterministic replay. Preserve an audit trail and support safe correction of any transaction.

### FIN-012 — Statement import is not yet safe for financial records

**Status:** Confirmed.

- There is no server-side file size/type/page limit.
- The UI accepts CSV/JSON while the prompt describes PDF content.
- `marketCode` and `currency` are described as optional, but parsing requires the market in a QueryDSL equality and later discards currency.
- Unknown instruments are silently skipped.
- The file is sent to an external AI provider without an explicit consent/data-retention workflow visible in the application.
- Output has no source-page/row provenance, confidence, duplicate fingerprint, or reconciliation totals.

Keep AI as a parser assistant, not the authority. Extract deterministic formats directly, make AI imports an explicit preview, show every skipped/ambiguous row, reconcile counts/totals, fingerprint duplicates, and retain user-approved provenance. Add privacy disclosure and a non-AI import path.

### FIN-013 — Empty dashboards can return null total-balance amounts

**Status:** Confirmed by SQL inspection.

`getTotalBalance` returns early only when the dashboard has no portfolio IDs. A newly onboarded dashboard normally has a portfolio but no positions. PostgreSQL still returns one aggregate row, with `SUM(...)` values of null; unlike `getDailyChange`, the mapper does not coalesce those values before constructing `TotalBalance`. The React dashboard asserts that `value` is non-null.

Coalesce aggregate values in SQL/service responses, test the newly onboarded empty state, and distinguish zero value from unpriced value.

### FIN-014 — Clear-my-data is unsafe until tested against real foreign keys

**Status:** Probable; requires the missing baseline/deployed schema.

`clearMyData` issues a JPQL bulk delete of `Position` rows. Bulk JPQL bypasses JPA cascade/orphan-removal behavior, while positions have transaction/history/snapshot children. Unless the undocumented database foreign keys use cascading deletion, the request will fail; if they do cascade, it permanently deletes financial history without an export/reconfirmation/audit workflow. The repository query also ignores its `userId` argument and instead references `principal.id` through SpEL.

Replace this with an explicit account-data deletion service, define retention/export behavior, and integration-test the exact child deletion order and user scope.

## P1 persistence and migration findings

### DB-004 — Daily snapshot repository still targets deleted table names

**Status:** Confirmed.

`PositionDailySnapshotRepository.generateDailyHoldingSnapshots()` inserts into `holding_daily_snapshots` and joins `holdings`, `stock_snapshots`, and `stock_id`, all renamed or removed by V3/V6. If invoked, it should fail. No current caller was found, which makes it dead but dangerous code.

Replace it with the new valuation-history design or delete it after confirming no external invocation.

### DB-005 — Currency referential integrity is weak

**Status:** Confirmed.

`positions.currency_code`, `instrument_snapshots.currency_code`, and `market_currencies.currency_code` have no visible foreign key to `currencies.code`. Codes are accepted as free strings and are not normalized to uppercase. Add ISO/custom-currency reference integrity or an explicit supported-code domain.

### DB-006 — Projection invariants exist only in Java

**Status:** Probable; the tracked migrations do not establish them, but the missing baseline must be inspected.

The database lacks visible checks/constraints for positive transaction price/quantity, nonnegative current quantity/cost, unique position identity, and valid dashboard/portfolio ownership relationships. Bean validation is helpful but does not protect imports, migrations, scripts, race conditions, or future services.

Enforce stable invariants in PostgreSQL as well as Java.

### DB-007 — Migration defaults and schema/entity drift need a full audit

**Status:** Confirmed risk; exact deployed drift needs a schema dump.

Examples include nullable `archived`, duplicate portfolio indexes, daily snapshot types that no longer match the main `BigDecimal` quantity model, and the absent initial definitions for generated snapshot columns/FKs. Generate a schema diff between a production copy and Hibernate's expected schema, then encode every intended difference in migrations.

## P1 security and account findings

### SEC-003 — Access and refresh JWTs are indistinguishable

**Status:** Confirmed.

Both tokens use the same signing key and claims; only expiration differs. There is no token-type claim, audience separation, refresh-session record, `jti`, rotation/reuse detection, device identity, revocation, or logout endpoint. The refresh endpoint accepts any locally signed JWT supplied in the refresh cookie.

Use short-lived access tokens and opaque or explicitly typed refresh sessions stored hashed server-side. Rotate on use, revoke by device/session, detect reuse, and clear the cookie on logout. For mobile, do not require browser cookie semantics; see `mobile-api-readiness.md`.

### SEC-004 — Disabled users and changed permissions can remain active

**Status:** Confirmed design issue.

Password login uses `UserDetails` account flags, but JWT conversion simply loads the user and creates an authenticated token. It does not visibly reject `isEnabled = false`. User/permission lookups are cached for five minutes without eviction on role/account changes.

Reject disabled/locked users on every JWT conversion and evict/version auth caches when security state changes.

### SEC-005 — Authentication abuse controls are absent

**Status:** Confirmed gap.

There is no visible rate limit, progressive delay, account lockout, breached-password check, refresh-session limit, or security event log. Registration also restricts email to five consumer domains, which blocks legitimate private-domain users and Google users even though it is not a meaningful abuse defense.

Add rate limiting and auditable auth events. Replace the email allowlist with explicit product policy or normal email verification.

### SEC-006 — Error responses can leak internal details

**Status:** Confirmed.

Generic runtime errors return `ex.getMessage()` and the exception class title. `NullPointerException` and `IndexOutOfBoundsException` are classified as client `400` errors. This both leaks implementation details and hides server defects as bad requests.

Return stable problem codes and safe user messages; log correlation IDs and full exceptions only server-side.

### SEC-007 — CORS and public documentation settings need environment tests

**Status:** Confirmed configuration concern.

The development allowed origin is `http://localhost:5173/` with a trailing slash, while browser `Origin` values normally omit it. Swagger/OpenAPI and actuator choices are largely static rather than profile-driven. Add security tests for exact origins, preflights, credential use, and production exposure.

## P1 API and mobile-contract findings

### API-001 — Financial decimals become JavaScript numbers

**Status:** Confirmed in generated-client usage.

The React code treats `BigDecimal` response fields as TypeScript `number` and performs financial arithmetic client-side. JavaScript floating-point cannot exactly represent many decimal amounts and has integer precision limits.

For public API DTOs, serialize monetary amounts, prices, quantities, rates, and percentages as canonical decimal strings (or well-defined minor units where appropriate). Provide shared client decimal handling. Do not expose JPA precision as an invitation to use binary floating point downstream.

### API-002 — IDs are inconsistent

**Status:** Confirmed.

Some DTO IDs are strings, path/request IDs are numbers, and entity IDs are `Long`. Standardize on opaque string IDs in external APIs (UUID/ULID or decimal strings). This avoids JavaScript safe-integer limits and decouples mobile contracts from database identity.

### API-003 — No versioning or deprecation policy

**Status:** Confirmed.

`TradeController` and `TradeControllerV2` already show an API evolution split, but both expose unversioned `/api/...` routes. Released mobile clients cannot be updated in lockstep with the server. Establish `/api/v1`, additive-change rules, deprecation headers/telemetry, and a minimum supported app version before shipping mobile.

### API-004 — Lists are unbounded and filtered in the client

**Status:** Confirmed.

Instrument, transaction, position, and dashboard transaction endpoints return full lists. The web client paginates/filter/sorts locally. This will grow slowly and then fail abruptly on bandwidth, heap, SQL, and mobile rendering.

Add server-side cursor pagination, stable sorting, date/portfolio/type filters, search limits, and compact summary endpoints.

### API-005 — Mutation requests are not idempotent

**Status:** Confirmed.

Buy, sell, bulk import, onboarding, create portfolio/dashboard, archive, clear, and undo have no client request ID/idempotency key. Network retry behavior is unsafe. Require user-scoped idempotency for financial/activity mutations and return the original result on replay.

### API-006 — Validation is inconsistent

**Status:** Confirmed.

Examples:

- `CreatePortfolioRequest` and `CreateDashboardRequest` have no effective field constraints.
- Login and Google token requests have no validation annotations.
- Commission can be null.
- Currency codes are not normalized/validated consistently.
- Future action dates, tag count/length, note length, batch size, and upload limits are unrestricted.
- Onboarding can be called repeatedly and its requested initial trades are ignored.

Create endpoint-specific request contracts, reject unknown/invalid values deterministically, and use domain error codes instead of relying on persistence exceptions.

### API-007 — Read responses do not state valuation context

**Status:** Strategic gap.

Dashboard responses need at least:

- reporting currency;
- valuation instant and market timezone;
- price/FX sources and oldest observation;
- missing/unpriced coverage;
- cost-basis/performance methodology version;
- whether figures are nominal or inflation-adjusted.

Without that context, a number cannot be audited or compared fairly.

## Operations and integration findings

### OPS-001 — Test coverage is effectively zero

**Status:** Confirmed.

The only test is `@SpringBootTest contextLoads()`. There is no test configuration or Testcontainers database. Priority suites should cover:

- ledger replay and cost basis with partial/full sells, fees, scale differences, backdating, and corrections;
- multi-currency point-in-time valuation;
- TWR/XIRR/daily-flow calculations against hand-worked fixtures;
- every owner boundary with user A/user B;
- Google audience validation and refresh rotation/revocation;
- Flyway empty-to-latest and production-copy upgrade;
- concurrent trade creation and idempotent retry;
- provider contract fixtures and stale/missing data behavior;
- OpenAPI compatibility snapshots.

### OPS-002 — Developer build is not toolchain-enforced

**Status:** Reproduced.

The project targets Java 21 and Docker uses Java 21. Under the active JDK 25.0.3, a plain `.\mvnw.cmd test` fails during compilation because Lombok annotations are not processed, producing missing getter/logger/constructor errors. When `-Dmaven.compiler.proc=full` is supplied through `MAVEN_OPTS`, compilation succeeds and the single context test passes. Add Maven Enforcer/toolchains and explicit annotation processor configuration, or clearly fail when the runtime JDK is unsupported. CI should run the exact supported Java version and optionally a next-JDK compatibility job.

### OPS-003 — No backend test gate in the visible GitHub workflow

**Status:** Confirmed.

The visible workflow runs Qodana but no Maven unit/integration tests, migration test, dependency/security scan, frontend check, or container smoke test. The Dockerfile skips tests. A successful image build therefore does not prove financial behavior or database compatibility.

### OPS-004 — Scheduled jobs are single-process assumptions

**Status:** Confirmed design issue.

Schedulers run in every application instance and have no distributed lock or leader election. Scaling the backend can duplicate downloads and updates. Import/provider calls have limited timeout/retry/circuit/rate-limit handling, and no provider-health or data-freshness SLO is exposed.

Use a job lock/queue, provider-specific resilience policy, ingestion run records, metrics, and alerts. Preserve raw provider payload/fingerprint where licensing permits so transformed data is auditable.

### OPS-005 — Historical market data does not exist

**Status:** Confirmed strategic blocker.

`instrument_snapshots` stores only the latest row per instrument/currency. That is enough for a current-price screen, not performance history, benchmark comparison, drawdown, volatility, or Decision Replay. Add immutable time-series observations before building those features.

### OPS-006 — Provider/licensing strategy is fragile

**Status:** Confirmed risk.

BIST prices are parsed from a media endpoint and instrument data is downloaded from an exchange ZIP. Before commercial or broad use, document each provider's permission, attribution, delay, redistribution, symbol coverage, correction policy, and fallback. The parser should also handle malformed fields per record rather than aborting the whole response, and previous close of zero must not divide the whole batch by zero.

### OPS-007 — Deployment relationship between React and Spring is unclear

**Status:** Confirmed from build files.

The Spring MVC config serves `classpath:/static`, but the Dockerfile only runs Maven and the POM has no frontend build/copy plugin. The Vite build outputs `src/main/web/dist`, not Spring static resources. If frontend and backend are intentionally deployed separately, document that and remove misleading coupling. If one image should serve both, add an explicit reproducible build stage and cache headers/PWA routing tests.

### OPS-008 — Configuration has unsafe or noisy defaults

**Status:** Confirmed.

Examples include a default local DB password, placeholder external API keys that permit startup but fail later, SQL logging enabled by default, Flyway disabled in development, and a production BIST timezone placeholder with no default. Fail fast for required production secrets, use profile-specific logging, and make local schema setup one command.

## Confirmed React issues relevant to backend design

The React project is not the main audit scope, but these behaviors should shape the backend contract:

1. **Mixed-currency portfolio totals:** portfolio value and earnings sum raw per-position values and label the result with the first position currency. Aggregation belongs in a backend valuation service with an explicit reporting currency.
2. **Hardcoded benchmark claim:** the portfolio card says it is “Outperforming” the S&P 500 without fetching or calculating a benchmark. Remove it until a real, date-aligned, total-return comparison exists.
3. **Sell quantity capped at five:** `SellTransactionForm` has `max={5}`, regardless of owned quantity.
4. **Refresh state bug:** the refresh hook writes a new token only to `localStorage`; `AuthenticationContext` keeps the old token and schedules logout at its expiry. A user can be logged out even though refresh succeeded.
5. **Local storage access token:** convenient but exposed to XSS. A web-specific in-memory access token plus protected refresh session is safer.
6. **Import mismatch:** the file picker accepts CSV/JSON while the backend AI prompt describes PDF, and multipart typing is bypassed with a cast.
7. **Precision:** the UI calculates totals and returns using binary floating-point numbers.
8. **CSS build warning:** `auth.module.css` contains `paddig-top`, likely intended to be `padding-top`.

## Endpoint inventory and notable gaps

Current public/authenticated surface:

- Auth: register, password token, Google token, refresh token.
- Account: current user, clear position data.
- Onboarding: status, complete.
- Reference: currencies, instruments.
- Portfolios: list, create, archive.
- Trades: buy, sell, bulk, import preview, undo latest, list by portfolio/all/active cycle.
- Positions: list.
- Analytics: monthly realized overview.
- Dashboards: list, current/default detail, create, delete, transaction series.

Important missing lifecycle operations:

- Account logout/session management, export, and full account deletion.
- Portfolio rename/color/reporting currency/unarchive/delete/transfer.
- Dashboard edit/default change/portfolio membership update.
- Transaction correction/reversal and import reconciliation.
- Cash/deposit/withdrawal/transfer/dividend/interest/fee/tax/corporate-action activities.
- Price history, FX history, benchmarks, valuation history, and data-quality endpoints.
- User preferences, timezone, base currency, tax/cost-basis policy.

## Architectural recommendation

Remain a modular monolith. Microservices would add failure modes without fixing the accounting model. Reorganize around bounded capabilities while keeping one deployable unit:

- `identity` — users, sessions, devices, auth events.
- `ledger` — accounts, activities, transaction groups, corrections, lots.
- `portfolio` — portfolio membership and current projections.
- `reference` — instruments, currencies, markets, calendars, corporate-action identities.
- `marketdata` — providers, immutable observations, ingestion runs, quality.
- `valuation` — point-in-time prices/FX and NAV projections.
- `performance` — returns, attribution, risk, benchmarks.
- `scenario` — deposits, alternative assets, counterfactual timelines.
- `importing` — deterministic parsers, AI preview, reconciliation.
- `notification` — alerts/device preferences later.

Use one definition of each metric in backend code with golden fixtures. SQL may optimize a proven definition, but it should not invent a parallel one.
