# PR-031 - Portfolio reporting groups and account-scoped investment views

Status: **ACTIVE**

## Goal

An authenticated owner can create, inspect, rename, reconfigure, and archive a portfolio as a reporting group over owned financial accounts, then use that portfolio to filter the existing trade history and open-position views. Portfolio membership is owner-safe, atomically replaceable, optimistic-concurrency protected, and incapable of creating, moving, valuing, or duplicating financial facts.

## Capability and review boundary

- Coherent capability: this PR completes the first usable portfolio/report-group boundary. It combines portfolio lifecycle, account membership, and portfolio-scoped trade/position reads so the result is an observable reporting capability rather than an unused metadata table.
- Combined behaviors: the `ledger` schema, investing-owned aggregate and membership lifecycle, owner validation, optimistic conflict handling, `/api/v1/portfolios` contract, and `portfolioId` filtering of existing investing reads are one unit. Splitting them would leave either unqueryable grouping metadata or filters with no managed grouping authority.
- Excluded neighbor: CSV trade/activity import, including file parsing, preview, fingerprints, duplicate detection, matching, reconciliation, and commit, remains the next independent R4 ingestion capability and is not started here.
- Focused review: one reviewer can trace portfolio creation, atomic membership replacement, archive behavior, and the resulting trade/position query scope through two small tables, one aggregate workflow, explicit SQL joins, and owner/concurrency tests without reviewing any financial calculation or import pipeline.
- Execution-self-contained: the schema, lifecycle, ownership, ordering, transaction, error, API, and acceptance contracts are specified below. The source documents provide provenance and are not required implementation reading.
- This PR is backend-only. Do not inspect or modify `web/`, regenerate a frontend client, or add compatibility endpoints.

## Source documents

- `docs/review/backend-master-plan.md` - fixed portfolio/account distinction; R4 items 1 and 9; R4 initial endpoints; Stage 2 portfolio endpoint; first-backlog item 16.
- `docs/review/cash-accounts-and-funding-design.md` - portfolio versus financial-account distinction and reporting-group examples.
- `docs/review/business-logic-and-analytics-design.md` - accounts hold value while portfolios group accounts/activities for reporting.
- `docs/review/accounting-contract.md` - sections 5 and 13-14: reporting configuration does not create actual facts, and valuation/conversion remains owned by the later valuation service.
- `docs/engineering/coding-standards.md` - capability packaging, Flyway/JPA ownership, direct application services, `JdbcClient` reporting reads, HTTP/errors/security, optimistic versions, and PostgreSQL testing.

## Starting state

- PR-030 is complete and user-accepted in implementation commit `5a8bc48`. Flyway V6, immutable trade cash/security facts, deterministic `position_projection`, owner-scoped trade reads, and open/exact position reads are implemented and verified.
- No portfolio table, membership table, portfolio aggregate, or `/api/v1/portfolios` endpoint exists. Existing trade and open-position lists accept account/instrument filters only.
- `ledger.financial_account` is the authority for where value is held or owed. Accounts are owner-scoped, versioned, and archivable; account lists are naturally small complete lists.
- Trade and position reporting uses explicit `JdbcClient` SQL. Trades and positions remain keyed to their actual brokerage account and instrument; no financial fact is keyed to a portfolio.
- The latest database migration is V6. The planning working tree was clean before this specification and pointer transition were created.

## Scope

1. Add an investing-owned `Portfolio` aggregate stored in the `ledger` schema. A portfolio is mutable reporting configuration only; it never owns a cash pocket, activity, posting, position projection, currency balance, or valuation.
2. Add explicit many-to-many membership between portfolios and complete financial accounts. One portfolio may contain zero or more accounts, and one account may belong to zero or more portfolios. Every membership row must carry the same owner as both referenced rows.
3. Support create, complete owner-scoped list, detail, atomic rename/membership replacement, and one-way archive. Keep list/detail collections unpaged because owner portfolios and one portfolio's account members are naturally small configuration collections.
4. Allow any owned financial-account kind, tracking mode, and lifecycle state as a member. Archived accounts remain eligible so a reporting group can retain historical account scope; account archive never silently removes membership.
5. Add an optional `portfolioId` filter to the existing trade-history and open-position list endpoints. The filter selects facts/projections whose `financial_account_id` is a current member of that one owned portfolio. When `accountId` is also present, apply the intersection. Preserve all existing pagination, allowed sorts, response shapes, and deterministic tie-breakers.
6. Preserve owner isolation at the query boundary. A missing or cross-owner portfolio identifier returns the same stable portfolio-not-found problem; it must not be interpreted as an empty valid scope or disclose existence.
7. Preserve all accepted trade, position, cash, reversal, reconciliation, and account behavior. Portfolio create/update/archive and membership replacement must not mutate or rebuild any immutable fact or balance/position projection.

## Explicit non-goals

- No CSV/file import, batch/row/issue/match model, fingerprint, duplicate detection, reconciliation matching, broker/provider connection, or background job.
- No portfolio valuation, reporting currency, NAV, performance, TWR/XIRR, allocation percentage, target allocation, benchmark, dashboard, chart, net worth, or cross-portfolio total. Mixed native currencies are not summed.
- No per-position, per-instrument, tax-lot, quantity, percentage, or monetary allocation within a portfolio. A member account contributes its complete matching trade and position set to each portfolio that contains it.
- No effective-dated membership history. Membership is current reporting configuration; portfolio-filtered historical trades use the membership set at query time.
- No portfolio-owned cash, implicit cash balance, cloned position, copied activity, or reassignment of a trade/position from its financial account.
- No delete, restore/unarchive, default portfolio, color, description, institution, tax wrapper, goal, household sharing, role/permission, or dashboard membership.
- No changes to trade commands, cost-basis replay, position identity, account onboarding, account archive behavior, or accounting formulas.
- No frontend files, generated frontend schema, UI workflow, or frontend tests.

## Database changes

Migration: `V7__portfolio_reporting_groups.sql`.

Create `ledger.portfolio` with exactly:

- `id uuid` primary key generated by the application;
- `owner_user_account_id uuid NOT NULL`;
- `name text NOT NULL` and `name_normalized text NOT NULL`;
- nullable `archived_at timestamptz`;
- `created_at timestamptz NOT NULL`, `updated_at timestamptz NOT NULL`;
- `version bigint NOT NULL DEFAULT 0` for optimistic concurrency.

Portfolio constraints and indexes:

- `pk_ledger_portfolio` on `id`;
- `fk_ledger_portfolio_owner` to `identity.user_account(id)` with `ON DELETE CASCADE`;
- `uq_ledger_portfolio_owner_id` on `(owner_user_account_id, id)` for owner-safe composite references;
- `ck_ledger_portfolio_name`: `name = btrim(name)` and `char_length(name) BETWEEN 1 AND 160`;
- `ck_ledger_portfolio_name_normalized`: `name_normalized = upper(name)` and `char_length(name_normalized) BETWEEN 1 AND 160`; application normalization uses the established trimmed display name plus `Locale.ROOT` uppercase convention;
- `ck_ledger_portfolio_version_non_negative`: `version >= 0`;
- `uix_ledger_portfolio_active_name` on `(owner_user_account_id, name_normalized) WHERE archived_at IS NULL`; an archived name may be reused by a new active portfolio;
- `ix_ledger_portfolio_owner_name` on `(owner_user_account_id, name_normalized, id)` for deterministic active and include-archived lists.

Create `ledger.portfolio_account_membership` with exactly:

- application-generated `id uuid` primary key;
- `owner_user_account_id uuid NOT NULL`;
- `portfolio_id uuid NOT NULL`;
- `financial_account_id uuid NOT NULL`;
- `created_at timestamptz NOT NULL`.

Membership constraints and indexes:

- `pk_ledger_portfolio_account_membership` on `id`;
- `fk_ledger_portfolio_account_membership_owner` to `identity.user_account(id)` with `ON DELETE CASCADE`;
- `fk_ledger_portfolio_account_membership_portfolio` on `(owner_user_account_id, portfolio_id)` to `ledger.portfolio(owner_user_account_id, id)` with `ON DELETE CASCADE`;
- `fk_ledger_portfolio_account_membership_account` on `(owner_user_account_id, financial_account_id)` to `ledger.financial_account(owner_user_account_id, id)` with `ON DELETE CASCADE`;
- `uq_ledger_portfolio_account_membership` on `(owner_user_account_id, portfolio_id, financial_account_id)` so an account appears at most once in a portfolio;
- `ix_ledger_portfolio_account_membership_account` on `(owner_user_account_id, financial_account_id, portfolio_id)` for portfolio-filtered account/fact joins; the unique membership key already supports portfolio-to-account lookup.

Do not alter `activity`, `money_posting`, `security_posting`, `account_balance_projection`, or `position_projection`. Do not add portfolio IDs to financial facts/projections and do not create import, valuation, allocation, dashboard, or membership-history tables.

Migration proof must cover an empty database through V7 and a real V6-to-V7 upgrade containing accounts, cash/security postings, reconciliations, open and closed position projections, and owner/global instruments. Existing V6 facts/projections must remain byte-for-byte semantically unchanged, Hibernate validation must pass, and future-table absence assertions must remain intact except for the two tables authorized here.

## Application changes

- Keep portfolio ownership in the existing `investing` capability. Add only the direct domain, application, infrastructure, error, and web types needed for this aggregate; do not introduce a generic grouping framework.
- Model `Portfolio` as mutable JPA metadata with `@Version`, intent-named rename/archive behavior, and no public mutable collection. Model account membership as an explicit owned row rather than a cascading many-to-many association that could mutate `FinancialAccount`.
- One cohesive portfolio application service may own create, update, archive, list, and detail. Use JPA for aggregate writes/simple ownership checks and an explicit `JdbcClient` read shape where it avoids N+1 member loading.
- Reuse the ledger's owner-scoped account authority through the smallest direct collaboration. Resolve every distinct requested account ID in one bounded owner-scoped query and require the resolved ID set to equal the requested set; a missing or cross-owner member fails with `LedgerErrorCode.ACCOUNT_NOT_FOUND` before any portfolio or membership change commits.
- Create is one transaction: normalize/validate the name, validate the complete account set, persist the portfolio and all memberships, flush required constraints, then return the created detail.
- Update is one transaction: load the owned portfolio for update, reject an archived portfolio, compare the client version, validate the complete replacement account set, rename, replace all memberships, update `updatedAt`, and increment the aggregate version exactly once, including for an otherwise semantically identical PUT. Any failure rolls back the name, parent version, and complete membership replacement.
- Archive is one transaction: load the owned portfolio for update, reject an already archived portfolio, compare the client version, set `archivedAt`/`updatedAt`, and increment the version exactly once. Retain all memberships.
- Use explicit client version preconditions and ORM optimistic locking. Concurrent update/update and update/archive attempts from the same starting version must produce one committed winner and one `PORTFOLIO_VERSION_CONFLICT`, never a merged or partially replaced membership set.
- These are non-financial configuration mutations. Do not write `ledger.idempotency_record` or acquire the financial command advisory lock. A retried create may receive the stable name conflict; stale update/archive retries receive the stable version conflict.
- Extend the existing investing read repository/query service directly for portfolio filters. Validate portfolio ownership once, then use an `EXISTS` membership predicate or equivalent join that cannot duplicate a trade/position row even when the account belongs to multiple portfolios. Do not add a second trade/position read stack.

## API contract

All endpoints are authenticated, owner-scoped, and return the existing no-store headers under `/api/v1`.

- `POST /api/v1/portfolios` accepts `name` and non-null `accountIds`. `accountIds` may be empty but may not contain nulls or duplicates. It returns `201`, a `Location: /api/v1/portfolios/{portfolioId}` header, and `PortfolioResponse` at version `0`.
- `GET /api/v1/portfolios?includeArchived=false` returns the complete owner-scoped `List<PortfolioSummaryResponse>`, ordered by normalized name ascending then portfolio ID ascending. The default excludes archived portfolios; `includeArchived=true` includes both.
- `GET /api/v1/portfolios/{portfolioId}` returns an active or archived owned portfolio detail.
- `PUT /api/v1/portfolios/{portfolioId}` accepts the complete replacement `name`, non-null unique `accountIds`, and required non-negative `version`; it returns the updated `PortfolioResponse`.
- `POST /api/v1/portfolios/{portfolioId}/archive` accepts required non-negative `version`; it returns the archived `PortfolioResponse`. There is no delete or unarchive endpoint.
- `GET /api/v1/trades` adds optional `portfolioId` alongside existing `accountId`, `instrumentId`, and Spring `Pageable` parameters.
- `GET /api/v1/investing/positions` adds optional `portfolioId` alongside existing `accountId` and Spring `Pageable` parameters.

`PortfolioSummaryResponse` contains required `id`, `name`, `accountCount`, `archived`, `version`, `createdAt`, and `updatedAt`, plus nullable `archivedAt`. `PortfolioResponse` contains the same fields plus required `accounts`. Each `PortfolioAccountResponse` contains required `id`, `name`, `kind`, `trackingMode`, `currency`, and `archived`, plus nullable `archivedAt`. Member accounts are ordered by normalized account name ascending then account ID ascending; `accountCount` exactly equals `accounts.size()` in detail responses.

An owned valid portfolio with no members or no matching rows returns an empty successful list/slice. When both `portfolioId` and `accountId` are supplied, only rows satisfying both are returned; a valid but non-member account therefore produces an empty slice. Existing trade/position response records and sort rules do not change.

Add stable investing errors:

- `PORTFOLIO_NOT_FOUND` - HTTP 404 for a missing or cross-owner portfolio;
- `PORTFOLIO_NAME_CONFLICT` - HTTP 409 for an active normalized-name collision;
- `PORTFOLIO_VERSION_CONFLICT` - HTTP 409 for a stale explicit version or optimistic-lock race;
- `PORTFOLIO_ARCHIVED` - HTTP 409 when update/archive is attempted on an already archived portfolio.

Null/blank/oversized names, a normalized name over 160 characters, null account IDs, duplicate account IDs, and negative versions use `CommonErrorCode.VALIDATION_FAILED` with safe field details. Normalization-sensitive name failures use field `name` and key `error.fields.investing.invalid_portfolio_name`; duplicate IDs use field `accountIds` and key `error.fields.investing.duplicate_portfolio_account`; ordinary null/range violations retain the shared built-in validation keys. Missing or cross-owner member accounts use the existing owner-safe `ACCOUNT_NOT_FOUND`. The four portfolio errors require no interpolation params. Register only `uix_ledger_portfolio_active_name` for safe `PORTFOLIO_NAME_CONFLICT` translation; never expose SQL or constraint names.

## Business invariants

- A portfolio and every membership have exactly one owner. Database composite foreign keys make cross-owner membership impossible even through raw SQL.
- A portfolio is reporting configuration, not financial truth. Creating, renaming, reconfiguring, or archiving it changes no account balance, activity, posting, reconciliation, security quantity, basis, realized P&L, or projection version.
- Membership is many-to-many and non-exclusive. The same account may appear in multiple portfolios, but a portfolio query includes each matching trade/position once.
- Membership is whole-account scope. Every trade and position in a member account is in scope; this PR does not allocate part of an account or holding to a portfolio.
- Membership is current, not effective-dated. Replacing membership changes which historical trades are returned by later portfolio-filtered queries; it does not rewrite those trades.
- Portfolios may contain accounts of different currencies, kinds, tracking modes, and archive states. This PR returns native account/position fields only and never sums or converts them.
- Portfolio archive is one-way in this API, retains membership, permits detail and portfolio-filtered reads, hides the portfolio from the default list, and releases its normalized name for reuse.
- Account archive does not remove membership. Owner deletion removes portfolios and memberships through database cascades; ordinary portfolio lifecycle never deletes financial facts.
- Duplicate requested account IDs are rejected rather than silently deduplicated. Membership order in a request has no semantic effect; response order is server-defined and deterministic.
- Update and archive require the exact current portfolio version. Concurrent mutations have one winner; the loser receives a stable conflict and leaves no partial membership changes.

## Required tests

### Pure/domain

- Portfolio name trimming/uppercase normalization and display/normalized length boundaries.
- Create, rename, archive, already-archived rejection, and immutable response/member collection behavior.
- Membership request normalization proves order-insensitivity while duplicate IDs remain invalid.

### PostgreSQL/Testcontainers

- Fresh V7 migration, V6-to-V7 preservation, and Hibernate mapping validation with existing trade/reconciliation/open-and-closed-position fixtures intact.
- Raw SQL acceptance/rejection for name normalization/length, non-negative version, active-name uniqueness, archived-name reuse, duplicate membership, owner mismatch, missing portfolio/account, and owner cleanup cascades.
- Create with empty/one/multiple accounts, one account in multiple portfolios, archived-account membership, deterministic list/detail ordering, and archive retention.
- Atomic replacement: invalid or cross-owner membership leaves the old name, version, and complete old membership unchanged.
- Concurrent update/update and update/archive from one version produce one winner and one stable version conflict without lost or mixed membership.
- Portfolio-filtered trade and open-position SQL covers empty membership, multiple member accounts, account/portfolio intersection, archived portfolio/account, existing trade/position pagination and sorts, and no duplicate rows when an account belongs to multiple portfolios.
- Owner deletion removes portfolio metadata/memberships while accepted owner cleanup behavior for existing ledger facts remains green. No financial fact/projection changes merely because membership changes.

### HTTP/security

- Authenticated create/list/detail/update/archive happy paths, `201`/`Location`, no-store headers, exact response fields, version progression, member ordering, default archive exclusion, and `includeArchived=true`.
- Structural validation for blank/oversized/normalization-expanded names, null/duplicate account IDs, null lists, and negative versions.
- Stable active-name, stale-version, already-archived, missing-member, and missing-portfolio Problem Details with safe codes/details.
- Unauthenticated rejection and cross-owner portfolio/member isolation on every command/detail/filter path without existence disclosure.
- `portfolioId` trade/position filters, combined account intersection, empty valid scope, archived scope, unchanged response/pagination/sort contracts, and generated OpenAPI required/nullable fields.
- Existing investing trade/position, financial-account, bearer-security, global error, and OpenAPI tests remain green.

## Acceptance criteria

1. An owner can create a named empty or populated portfolio, list/detail it deterministically, and see exact owned account membership without pagination or N+1 loading.
2. The owner can atomically rename and replace all membership using the current version; invalid membership and concurrent races cannot leave a partial or silently merged result.
3. The owner can archive a portfolio without deleting membership or financial data; default list visibility, name reuse, detail access, and archived portfolio filtering follow the specified lifecycle.
4. Existing trade history and open-position lists accept an owned `portfolioId` and return exactly the current member-account intersection while preserving all accepted sorts, pagination, response fields, and owner isolation.
5. Portfolio/membership operations create no cash, postings, positions, valuation, implicit currency total, or projection rebuild. Many-to-many membership cannot duplicate rows within one filtered result.
6. PostgreSQL enforces owner alignment, duplicate prevention, active normalized-name uniqueness, delete behavior, and version shape; fresh-to-V7 and V6-to-V7 paths pass with Hibernate and preserve V6 facts.
7. No import, holdings allocation/opening, valuation/performance, dashboard, tax, FX, income/corporate-action, pending-settlement, background-runtime, or frontend work enters the change surface.
8. The focused domain/migration/service/concurrency/HTTP/OpenAPI gate and the single full Maven exit gate pass with no skipped required tests.

## Documentation completion

Before this implementation unit is considered complete:

1. Update `docs/implementation/STATE.md` with the implemented portfolio/report-group capability, V7 schema, current query semantics, deferred CSV import, and latest useful verification state. Do not mark import, valuation, allocation, or later R4/R5 work complete.
2. Replace or remove obsolete STATE assertions rather than appending a history trail. Keep detailed implementation evidence in this specification's Completion Record and Git history.
3. Update `docs/review/progress-report.md` for the verified R4 portfolio checkpoint. Update long-lived design/accounting documents only if implementation proves an authoritative ownership or financial semantic must change.
4. Move only reusable Windows, Maven, Docker/Testcontainers, or tool-output lessons to `docs/engineering/codex-command-playbook.md`.
5. Do not update `docs/implementation/web/STATE.md` or any file under `web/`; this PR is backend-only.

## Verification commands

### Inner-loop verification

Run the smallest focused gate from `server/` while implementing:

```powershell
.\mvnw.cmd "-Dtest=PortfolioDomainTest,PortfolioMigrationTest,PortfolioServiceTest,PortfolioConcurrencyTest,PortfolioHttpTest,OpenApiHttpTest" test
```

Do not run the complete test suite or Maven `verify` during normal inner-loop development.

### Exit gate

Run once only after implementation and inner-loop verification are complete:

```powershell
.\mvnw.cmd verify
```

Run `.\mvnw.cmd spotless:apply` beforehand if formatting adjustments are needed.

## Completion record

Fill this before marking the PR complete.

### Implemented

- Added V7 owner-scoped portfolio and portfolio-account membership tables, active-name uniqueness, composite ownership constraints, duplicate prevention, and cascade cleanup. Fresh V7 and V6-to-V7 migration tests cover the schema and preserve existing V6 facts.
- Added portfolio domain, persistence, application, request/response, HTTP, and stable error handling for authenticated create/list/detail/update/archive operations. Membership replacement is atomic, version-checked, and resolves requested accounts in one owner-scoped query; archived portfolios retain their memberships.
- Added `portfolioId` filters to the existing trade-history and open-position endpoints. Owner checks and membership `EXISTS` predicates preserve account intersection, paging, sort behavior, and row uniqueness.
- Expanded HTTP/security, raw PostgreSQL, migration, domain, service, concurrency, and OpenAPI coverage, including same-value update version progression and archived account membership.

### Deviations from specification

- None.

### New decisions

- None beyond the specified contracts. Advancing `updatedAt` by at least one database precision unit on an otherwise identical PUT is the implementation detail that persists the required aggregate version increment exactly once.

### Tests executed

- `.\mvnw.cmd "-Dtest=PortfolioDomainTest,PortfolioMigrationTest,PortfolioServiceTest,PortfolioConcurrencyTest,PortfolioHttpTest,OpenApiHttpTest" test` — 15 tests, 0 failures, 0 errors, 0 skipped.
- `.\mvnw.cmd spotless:apply` — completed successfully.
- `.\mvnw.cmd verify` — Maven log reports `BUILD SUCCESS`; 422 tests, 0 failures, 0 errors, 0 skipped. Spotless check and executable repackaging passed. Surefire logged its 30-second post-exit fork shutdown message; the exec wrapper returned 1 despite Maven's success summary.

### Follow-up work

- CSV/file import, holdings-only opening positions, valuation/performance, allocation, and other broader R4 capabilities remain deferred. No subsequent PR was activated.
