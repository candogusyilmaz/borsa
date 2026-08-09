# Backend master implementation plan

Plan date: 2026-08-06

Clarified: 2026-08-07

Technology baseline refreshed: 2026-08-07

Status: implementation-ready and authoritative plan for a development-only reset. Backend scope only. This document consolidates the database dump review, modular-monolith direction, data-unavailability strategy, the 32-feature catalogue, and the existing correctness/security roadmap.

## Authority and superseded plans

This document is the authoritative backend implementation plan.

Because the application has not been deployed and the target database is disposable, the backend is being rebuilt from scratch. Any older review section that describes migrating existing application data, backfilling legacy transactions, repairing `V2`–`V14` in place, running old and new accounting projections in parallel, or shadow-mode reconciliation against production data is retained only as historical analysis and is superseded by this plan.

Legacy code, schema, migrations and identifiers remain available through Git and the review documents as behavioral/schema evidence only. They are not compatibility constraints for the scratch rewrite.

Financial semantics that affect more than one slice are governed by [accounting-contract.md](accounting-contract.md). If implementation requires changing one of those semantics, update the contract and its golden fixtures before implementing the new behavior.

## Technology baseline

The scratch rewrite starts directly on the modern stable Java/Spring stack rather than recreating the legacy Boot 3/Java 21 environment first.

- Java: **25**. Use final Java 25 language/runtime features where they improve clarity. Preview, incubator, or experimental JDK features are disabled unless a later PR explicitly approves one.
- Spring Boot: **4.1.0** as the pinned starting release. Upgrade Boot deliberately in a dedicated maintenance PR rather than silently changing framework versions during a feature PR.
- Spring Framework, Spring Data, Hibernate ORM, Hibernate Validator, Jackson and other platform libraries: use the versions managed by the Spring Boot 4.1.0 dependency management/BOM unless a documented compatibility defect requires an override.
- Spring Data JPA: Boot-managed **4.1.0** with Spring Boot 4.1.0.
- Jakarta Persistence: **3.2** stable specification. Modern stable features such as record embeddables, `Instant` support and `getSingleResultOrNull()` may be used where they make the model simpler.
- Hibernate ORM: Boot-managed **7.4.1.Final** with Spring Boot 4.1.0. Hibernate-specific stable features are allowed when they solve a real requirement, but Hibernate remains a runtime mapper/query implementation rather than the DDL authority.
- PostgreSQL, Flyway and Testcontainers remain the database/migration/test foundation.
- Do not adopt Jakarta Persistence 4, Hibernate ORM 8, framework snapshots/milestones/RCs, or Java preview/incubator APIs in ordinary feature PRs.
- Do not pin transitive Spring/Hibernate/Jackson versions individually just to obtain a newer number; Spring Boot's tested dependency set is the default compatibility contract.

Repository-wide implementation style is governed by [../engineering/coding-standards.md](../engineering/coding-standards.md). Pull-request execution is governed by [../implementation/README.md](../implementation/README.md) and the active specification referenced from `docs/implementation/CURRENT.md`. These rules are intentionally discoverable through root `AGENTS.md` so normal coding prompts do not need to repeat them.

## Intended outcome

Build one Spring Boot application that can eventually support the complete personal-money product described in [implementable-features.md](implementable-features.md), without creating microservices, independent Maven modules, or rigid internal boundaries that slow down development.

The backend should be able to:

1. Reconstruct what happened to cash, investments, debt, spending, claims, purchases, and physical assets.
2. Rebuild balances and analytics deterministically after corrections or backdated facts.
3. Run every calculation with manual, imported, or clearly synthetic inputs when live data is unavailable.
4. Add a real provider later without rewriting the calculation or business domain.
5. Serve the React client now and an Expo/React Native client later through a stable `/api/v1` contract.

This plan assumes the database is disposable. No production data or deployed environment needs an in-place upgrade. The clean rewrite targets the user-created PostgreSQL database `extreme_accounting`.

## Scratch-rewrite cutover strategy

This is a **clean replacement**, not an in-place refactor or strangler migration.

1. Create a `backend-rewrite` branch/tag so the current implementation remains available in Git.
2. Keep `src/main/web` untouched; temporary API incompatibility is acceptable because the application is not deployed.
3. Retain review documents and [db-dump.sql](db-dump.sql) as behavioral/schema evidence only.
4. Remove the old backend entities, services, controllers, repositories, integrations, mapper XML, migrations, empty `schema.sql`, and unused dependencies once the minimal replacement application compiles in the same commit series.
5. Keep the existing Java base package `dev.canverse.stocks` initially. Renaming packages/product identifiers does not improve business correctness and can be a separate mechanical change after the new backend is stable.
6. Change the default local datasource to `jdbc:postgresql://localhost:5432/extreme_accounting` while preserving `DB_URL` override support.
7. Start the new application with health/security/error/reference foundations only, then add one buildable vertical slice at a time.
8. Do not copy legacy rows or preserve legacy IDs. Use deterministic reference seeds and synthetic demo loading against the new model.
9. Delete an obsolete legacy class as soon as its required behavior is represented by the new slice; do not maintain old and new financial truth systems in parallel.
10. Keep every commit/build in a startable or intentionally test-failing-then-fixed state; avoid a weeks-long unbuildable rewrite branch.

### Dependency reset

Start with the smallest useful dependency set:

- Spring Boot web, validation, security, OAuth2 resource-server/client only if used, Data JPA, JDBC, Actuator and OpenAPI;
- PostgreSQL driver, Flyway PostgreSQL, Lombok if desired;
- Spring Boot test, Spring Security test, Testcontainers JUnit/PostgreSQL;
- Java 25 compiler/toolchain enforcement; preview/incubator/experimental JDK features disabled by default.

Remove QueryDSL, MyBatis/XML mappers, Google Gemini, authorization-server, Jackson Hibernate integration, compression/import libraries, and caching from the initial rewrite unless the first slice actually uses them. Add a dependency only with the feature that needs it. Use JPA for commands and `JdbcClient` for report SQL before adding another persistence framework.

### Per-slice implementation loop

Every numbered rewrite increment follows the same order. Implementation work is delivered through small human-reviewable PR specifications rather than handing an entire R-stage to an agent. Before editing code, the agent follows root `AGENTS.md`, [../engineering/coding-standards.md](../engineering/coding-standards.md), and the active PR specification referenced by `docs/implementation/CURRENT.md`.

1. State the actual/obligation/plan/scenario boundary, check the affected rules in [accounting-contract.md](accounting-contract.md), and define only slice-specific invariants not already covered there.
2. Write a failing pure calculation/domain test when business logic exists.
3. Write the Flyway migration with all database constraints/indexes and a Testcontainers migration test.
4. Add minimal JPA entities containing mapping annotations only.
5. Add repository/query code and one cohesive transactional service.
6. Add `/api/v1` command/query records and controller endpoints.
7. Add owner/household authorization, idempotency, correction, and concurrency behavior as applicable.
8. Add manual/file input plus deterministic synthetic fixture coverage.
9. Add integration/API tests and source/coverage fields.
10. Run the slice exit gate, update [progress-report.md](progress-report.md), and update [accounting-contract.md](accounting-contract.md) first if the slice introduced or changed a cross-cutting financial rule; then proceed.

## Decisions to keep fixed

### Architecture

- Keep **one Maven project, one Spring Boot process, one PostgreSQL database, and one deployable artifact**.
- Organize Java by coarse business capability rather than the current global `entity/service/repository/rest` layers.
- Direct Java service calls, JPA relationships across packages, shared transactions, SQL joins, and database foreign keys across schemas are allowed.
- A module owns writes to its tables, but other modules may read through a service, repository projection, or deliberate query. This is a maintainability convention, not a distributed-systems boundary.
- Do not add Kafka, a message broker, service discovery, an API gateway, distributed transactions, or separate deployments.
- Use a small durable job table plus a scheduled worker for imports/rebuilds. Do not retain Spring Batch unless a real batch job needs its restart/chunk semantics.
- Introduce an interface only when there are multiple implementations, an external provider boundary, or a difficult-to-test side effect. Do not create `Service` + `ServiceImpl` + `UseCase` + `Port` + `Adapter` for one code path.

### API invariants from the first endpoint

All newly implemented APIs use these rules from their first release; R16 audits/completes them rather than introducing them late:

- `/api/v1` paths;
- opaque UUID/string external identifiers;
- canonical decimal strings for money, prices, quantities, rates and percentages;
- RFC 9457-compatible stable problem codes with safe client messages;
- cursor pagination for potentially unbounded collections;
- explicit owner/household authorization on detail and aggregate reads;
- idempotency for retryable financial mutations;
- optimistic version/conflict semantics where mutable metadata can race;
- source/quality/coverage/calculation-version metadata for derived financial results.

### Database and accounting

- Replace the legacy migration chain with a reviewed, fresh `V1` baseline; do not turn [db-dump.sql](db-dump.sql) into `V1` unchanged.
- Flyway is the only schema creator. Hibernate remains `ddl-auto: validate`.
- Posted financial facts are immutable. Correct them by reversal/supersession, not update/delete.
- Balances, positions, lots, daily valuation, performance, recurring detections, and TCO totals are rebuildable projections.
- A portfolio is a reporting grouping. A `FinancialAccount` is where cash, securities, or liabilities are actually held.
- Plans, expected bills, scenarios, and unreviewed document extractions do not alter actual balances.
- Keep native currency and quantity exactly. Convert only with an explicit as-of/source policy.
- Cross-cutting posting, time, fee/tax, FX, balance, valuation, performance and projection semantics live in [accounting-contract.md](accounting-contract.md); feature code must not invent parallel definitions.

### Data availability and user financial-data acquisition

- No feature may require an internet provider to start, test, or demonstrate.
- **Manual entry and file import are primary permanent product workflows, not temporary fallbacks until account connectivity exists.**
- The application must remain fully usable without bank, card, broker, Open Banking, or payment-initiation connections.
- Core financial workflows support manual account creation, explicit opening state, manual activities, file imports, reconciliation, and manual confirmation/recording of payments and settlements.
- External market/FX/index/document/valuation data families start with a manual/import path and a deterministic fake implementation.
- Synthetic data is isolated and marked at row, dataset, API, and UI-contract level. It must never look like real financial truth.
- A feature engine may be complete with sample policies while real geographic/provider coverage is incomplete. The API must report that distinction.
- Tax, benefits, warranties, insurance, consumer rights, and government calculations remain informational/sample-only until a jurisdiction pack is validated and maintained.
- Bank/Open Banking synchronization, broker synchronization and payment initiation are explicitly deferred beyond this roadmap. The domain may remain compatible with them, but no speculative connector/token/webhook framework is built now.

### File economy

- Prefer one cohesive service per aggregate/workflow, not one class per operation.
- Keep API request/response records in the owning capability's directional `input`/`output` packages; keep application commands and query results with the application workflow that owns them.
- Do not create a repository interface and wrapper repository for the same table.
- Use JPA for aggregate writes/simple reads and Spring `JdbcClient`/explicit SQL for complex read models. Do not restore MyBatis/QueryDSL in the scratch rewrite unless a concrete query proves they add value.
- Add one Flyway migration per coherent release slice, not one migration file per table.
- Keep architectural decisions and progress in these review documents instead of creating dozens of ADR files during early development.

### JPA annotation and DDL ownership policy

Flyway SQL is the complete authority for database design. JPA entities describe runtime persistence mapping; they are not a second schema-definition language.

Keep only mapping/behavior annotations that Hibernate actually needs, such as:

- `@Entity` and `@Table(name = ..., schema = ...)`;
- `@Id`, `@GeneratedValue` where applicable, and `@Version`;
- `@Column(name = ...)` only when an explicit name or special runtime type mapping is needed;
- `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`, `@JoinColumn`, `@EmbeddedId`, and `@MapsId` for object relationships;
- `@Enumerated`, JSON/type mapping, fetch/cascade/orphan behavior where required;
- Lombok/JPA constructors/getters/setters or explicit accessors, without exposing setters that bypass financial invariants.

Do **not** put database optimization or integrity DDL in entities:

- no `@Table(indexes = ...)`;
- no entity `@Table(uniqueConstraints = ...)` as the database constraint definition;
- no Hibernate check/index/foreign-key DDL annotations;
- no generated-column expression, SQL default, trigger, partial index, or PostgreSQL DDL in `columnDefinition`;
- no delete/update action that exists only as an object cascade assumption.

All primary/foreign keys, unique constraints, checks, defaults, generated values/identity details, indexes, delete/update actions, extensions, triggers, and database comments are declared and named in Flyway migrations. A relationship annotation may coexist with a migration-owned foreign key because JPA needs the object mapping; Hibernate remains `ddl-auto: validate` and never generates it.

Use Bean Validation and domain methods for friendly command errors, while PostgreSQL constraints remain the final integrity barrier. Avoid repeating length, precision, scale, or nullability metadata in `@Column` when Hibernate does not need it for correct mapping; the migration and domain value type are authoritative.

Example entity mapping:

```java
@Entity
@Table(name = "financial_account", schema = "ledger")
@Getter
class FinancialAccount {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserAccount owner;

    @Version
    private long version;
}
```

The corresponding Flyway SQL—not the entity—declares `owner_id NOT NULL`, the named foreign key and its delete action, unique/check constraints, and any query-driven index. `@JoinColumn` only tells Hibernate which column maps the relationship.

## Supplied dump assessment

The repository file [db-dump.sql](db-dump.sql) has SHA-256 `FABDA56B6737FA710A37436FFA731BDFB8852EB5275BDF5000C981C1FD2AE8BD`. It is a PostgreSQL 15.1 schema dump containing 28 `CREATE TABLE` statements and no `COPY` or `INSERT INTO` data statements.

| Schema       | Tables | Content                                                                                 |
| ------------ | -----: | --------------------------------------------------------------------------------------- |
| `account`    |      5 | Users, roles, permissions, join tables                                                  |
| `instrument` |      6 | Markets, instruments, stock/crypto subtype tables, current snapshots, market currencies |
| `portfolio`  |      8 | Portfolios, positions, transactions, histories/snapshots/performance, dashboards        |
| `public`     |      9 | Countries, currencies, Flyway history structure, and six Spring Batch tables            |

It is useful as an inventory, not as a restorable target model.

### Confirmed dump/migration problems

| ID     | Finding                                                                                                                                                                                                                                     | Decision                                                                                                                    |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| DBP-01 | The repository starts at `V2`; migrations depend on pre-existing `users`, `holdings`, `countries`, `stocks`, and other tables.                                                                                                              | Replace `V2`–`V14` with a new development baseline and later coherent migrations. Git history is the legacy record.         |
| DBP-02 | Flyway is disabled in the default development profile and only enabled in production.                                                                                                                                                       | Enable Flyway in development, test, and production; test empty-to-latest on every build.                                    |
| DBP-03 | The repository dump contains six Spring Batch tables although no corresponding backend batch job was found. The earlier pasted attachment had a duplicated line, but the repository dump itself has one `status` column per relevant table. | Remove legacy Spring Batch tables/config unless Spring Batch is deliberately adopted later.                                 |
| DBP-04 | `convert_currency` and `currencies.exchange_rate` contain one mutable latest rate and implicitly normalize through USD.                                                                                                                     | Replace them with immutable FX series/observations and an application calculation policy.                                   |
| DBP-05 | `instrument_snapshots` stores only one row per instrument/currency.                                                                                                                                                                         | Keep an optional latest projection, but make immutable observations authoritative.                                          |
| DBP-06 | `positions.currency_code`, `instrument_snapshots.currency_code`, and `market_currencies.currency_code` have no currency foreign key.                                                                                                        | Use a canonical ISO-like currency code reference with foreign keys everywhere.                                              |
| DBP-07 | `positions.instrument_id` is nullable in the dump despite the JPA association being mandatory.                                                                                                                                              | Make it non-null and enforce unique position identity in the replacement projection.                                        |
| DBP-08 | `positions.total` is `numeric(20,8)` while the entity requests `numeric(38,18)`; timestamps mix with/without timezone.                                                                                                                      | Choose one numeric/time policy and make migration, JPA, and API agree.                                                      |
| DBP-09 | `position_daily_snapshots.quantity` is an integer although holdings support decimal quantities; several generated results round to scale 2.                                                                                                 | Remove this table from the trusted model. Build exact daily valuation projections from ledger + observations.               |
| DBP-10 | Generated percentage columns can produce `NULL` while declared `NOT NULL`; persisted performance categories can become stale.                                                                                                               | Calculate versioned analytics in application/read models and store inputs/results with provenance when caching.             |
| DBP-11 | There is no uniqueness constraint for `(portfolio, instrument, currency)` and no idempotency/concurrency primitive.                                                                                                                         | Add immutable client event IDs, deterministic replay, and projection locking/versioning.                                    |
| DBP-12 | Duplicate indexes exist on portfolio user and position portfolio columns.                                                                                                                                                                   | Keep only indexes demonstrated by constraints/query plans.                                                                  |
| DBP-13 | Role/permission join tables lack pair uniqueness; many foreign keys have generated names and no deliberate deletion policy.                                                                                                                 | Add meaningful names, pair uniqueness, and explicit restrict/cascade behavior.                                              |
| DBP-14 | Public PostgreSQL enums include a now-unused `tag_type`; instrument types will expand substantially.                                                                                                                                        | Prefer application enums stored as constrained text or reference rows; avoid PostgreSQL enums for rapidly growing concepts. |
| DBP-15 | The dump contains both transaction `notes` and JSON metadata despite the migration intending to replace notes.                                                                                                                              | Replace the transaction model with canonical activity provenance/notes/tags rather than preserve drift.                     |
| DBP-16 | Market currencies are created but never populated by `V13`; instrument queries can consequently return nothing.                                                                                                                             | Seed deterministic reference/market-currency relationships in the new baseline.                                             |
| DBP-17 | `V5` hardcodes market ID `3` for BIST and depends on tables not created by the migration chain.                                                                                                                                             | Seed by stable code/natural key, never generated numeric IDs.                                                               |
| DBP-18 | The schema dump contains no actual data or Flyway-history rows.                                                                                                                                                                             | Treat it as schema evidence only; it is not a backup of user/reference data.                                                |

### What should be retained conceptually

- Users, roles, sessions, portfolios, dashboards/views, countries, currencies, markets, instruments, and instrument subtypes are valid domain concepts.
- PostgreSQL, Flyway, JPA, Spring `JdbcClient` for complex reads, `BigDecimal`, Bean Validation, and thin HTTP controllers remain appropriate.
- User ownership filters and the existing separation between account/instrument/portfolio concepts are useful starting instincts.

### What should be retired

- Mutable `Position.buy/sell` as the financial source of truth.
- Destructive `undo` and insertion-order state mutation.
- Latest-only exchange rate and instrument snapshot as historical truth.
- `position_history`, the current generated daily snapshot design, and transaction-performance category as authoritative records.
- Spring Batch tables that have no corresponding backend job.
- The empty `schema.sql` initializer and any schema creation outside Flyway.
- PostgreSQL enums for product concepts expected to grow.

## Pragmatic modular-monolith layout

The package names below are coarse. They deliberately do not create one module per feature.

| Java package | Owns                                                                                                                         | Main feature families                                        |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| `identity`   | User, credentials/external identities, device sessions, household membership/grants                                          | Authentication, FT-13 permissions, export/deletion ownership |
| `reference`  | Countries, currencies, instruments, markets, calendars, providers, policy identities                                         | Shared reference data for every module                       |
| `ledger`     | Financial accounts, cash pockets, immutable activities/postings, corrections, idempotency, balances, reconciliation, imports | FND-01, FT-01, FT-31 and the truth behind all posted money   |
| `investing`  | Portfolio groupings, trade commands, position/lot projections, investment income/actions                                     | Current share tracking, FT-02, FT-11                         |
| `data`       | Observation series, market/FX/rate/CPI observations, ingestion runs, manual/synthetic/provider sources, quality              | FND-02, all analytics and scenarios                          |
| `money`      | Spending/income/categories, contracts/bills/cards/debt, people/claims, purchases/recoveries, documents/projects              | FT-03/15–30 except planning-heavy parts                      |
| `analysis`   | Valuation, daily NAV, performance/decomposition, calculation runs, scenarios, Decision Replay, goals/resilience/briefs       | FND-03/04/05, FT-02/04–12/14                                 |
| `assets`     | Physical-asset lifecycle, meters, consumption, cost links, service/warranty, valuations, TCO/disposal                        | FT-32                                                        |
| `platform`   | Security configuration, file storage abstraction, durable jobs, clock/ID support, demo-data loader, API errors               | Cross-cutting infrastructure only                            |

### Sub-package structure within each capability

Every capability module uses these fixed sub-packages:

```text
dev.canverse.stocks
  identity/
    domain/          ← JPA entities, value objects
    application/     ← transactional services, use cases
    infrastructure/  ← Spring Data repositories, external adapters
    configuration/   ← Spring @Configuration classes
    input/            ← inbound HTTP/API request records
    output/           ← outbound HTTP/API response records
    web/              ← controllers
```

Omit a sub-package entirely when a capability has no code in that layer yet — do not create empty packages. A further feature-group split inside a sub-package (e.g. `money/application/spending/`) is acceptable when the package becomes large.

### Coupling rules

Allowed:

- `assets.AssetService` loading ledger activities and protection contracts directly through application services/repositories.
- `money.BillService` calling `ledger.LedgerService` in the same database transaction to post a payment.
- JPA foreign keys from `asset.asset_cost_link` to `ledger.activity`.
- Analysis SQL joining ledger, observations, and investing projections.
- Direct construction/use of shared value objects such as `Money`, `MeasuredQuantity`, and `EffectiveTime`.

Avoid even though technically possible:

- A module updating another module's tables with ad hoc SQL and bypassing its invariants.
- Circular service calls (`LedgerService -> BillService -> LedgerService`). Put orchestration in the initiating workflow service.
- Reimplementing currency conversion, balance rules, or correction logic in several modules.
- Returning JPA entities from controllers.

### Minimal code pattern

For a normal workflow, start with:

- one aggregate/entity file per real persisted concept;
- one Spring Data repository where CRUD is needed;
- one service containing transaction and invariant logic;
- one controller for related endpoints;
- API request records in the capability `input` package and response records in `output`;
- one query/read-model class only when the screen/report justifies it.

Do not automatically add a domain interface, implementation, mapper, factory, command handler, query handler, and event class for every operation.

### Synchronous and asynchronous work

Use direct calls in one `@Transactional` boundary for user commands. Use a simple durable `platform_job` record for work that can outlive the request:

- statement/document imports;
- projection rebuilds after backdated facts;
- observation ingestion;
- scenario/TCO calculation batches;
- notification delivery.

The worker claims jobs with `FOR UPDATE SKIP LOCKED`, records attempts/error/heartbeat, and is safe to retry. Domain writes still require idempotency. An outbox can be added to the same table pattern if an external message destination ever exists.

## Target PostgreSQL organization

Database schemas are coarse ownership aids and do not need to match every Java package.

| Schema      | Intended records                                                                                                                                                     |
| ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `identity`  | Users, auth identities, device sessions, roles, households, members, scoped grants                                                                                   |
| `reference` | Currency/country/market/instrument identities, aliases, calendars, source/policy catalogues                                                                          |
| `ledger`    | Financial accounts/cash pockets, activities, money/security postings, idempotency, reconciliation/import state, portfolio groupings and balance/position projections |
| `data`      | Datasets/providers, series, immutable price/FX/rate/CPI/manual-value observations, ingestion runs and latest-observation projections                                 |
| `money`     | Categories/rules, obligations/contracts/bills, debt/card terms, income, claims, purchases/receipts, documents, projects                                              |
| `analysis`  | Valuations/NAV, calculation runs, scenarios, plans/goals, decision journal and insights                                                                              |
| `asset`     | Physical assets, lifecycle/interest, meters/readings, consumption/cost links, service/warranty, valuations/disposal                                                  |
| `platform`  | Durable jobs, audit/security events and storage-object metadata; business document metadata remains in `money`                                                       |

`public` should contain only Flyway/extension metadata that cannot be placed elsewhere. Cross-schema foreign keys are expected.

### Type policies

- Use `uuid` primary keys for user-owned aggregates, immutable activities, imports, documents, scenarios, and physical assets. Generate IDs in Java so future offline clients can supply correlation/idempotency IDs without a database sequence dependency.
- Stable reference rows use codes where the code is genuinely the identity (`TRY`, `USD`, ISO country code); otherwise use UUID.
- Use `numeric(38,18)` for authoritative monetary/quantity inputs unless a domain needs a stricter limit. Never use floating point.
- API decimal values are strings. Display rounding is not storage/calculation rounding.
- Use `timestamptz` for instants and retain relevant zone/calendar separately. Use `date` for true date-only obligations.
- Store status/type as text with application validation and stable database checks only where values are mature.
- Store structured provider payload/evidence in JSONB only when its schema is variable. Do not hide core calculation fields in JSONB.
- Add `version` for optimistic locking to mutable aggregate metadata and projections.

### Canonical activity model

The minimum trusted core is:

- `financial_account`: ownership/scope, kind, tracking mode, native/display preferences, liquidity, capabilities, negative/credit policy, archived state;
- `account_cash_pocket`: `(account, currency)` identity for accounts that hold cash;
- `activity`: immutable envelope with type/state, effective/recorded time, user/scope, source, client/external ID, correction/reversal/group relation, provenance and schema version;
- `money_posting`: signed native amount to an account/pocket with posting role and settlement state;
- `security_posting`: instrument quantity/basis/proceeds components;
- `activity_split`: spending/income/category/project/asset/claim allocation without copying the activity;
- `account_balance_projection`, `position_projection`, and optional lot projection;
- `reconciliation` and explicit adjustment activity;
- `idempotency_record` scoped to principal + operation + key.

Activity-type invariants are explicit. A transfer must have source/destination legs; a card purchase records spending once; a card payment moves value and liability without spending again; borrowing/principal repayment is not income/expense; a trade moves cash and security; FX conversion retains both currencies, executed/reference rate, spread, and fees.

The detailed rules remain in [cash-accounts-and-funding-design.md](cash-accounts-and-funding-design.md) and [business-logic-and-analytics-design.md](business-logic-and-analytics-design.md).

### Projection strategy

1. Commit the immutable fact and idempotency record atomically.
2. Lock affected account/instrument projection keys in a deterministic order.
3. For ordinary forward facts, update from the latest checkpoint.
4. For backdated/corrected facts, mark the earliest affected date and rebuild synchronously when small or enqueue a durable rebuild.
5. Store projection `as_of`, input watermark/version, and status.
6. Never serve a stale projection as current without a coverage/rebuild flag.

Use exact golden fixtures before optimizing. A full replay is preferable to an incorrect incremental algorithm.

### Projection state contract

Every rebuildable projection uses the same externally meaningful state model:

- `CURRENT` — reflects every committed relevant input up to its input watermark;
- `STALE` — a relevant fact/observation/policy changed and the projection requires rebuild;
- `REBUILDING` — a rebuild has been claimed and is in progress;
- `FAILED` — the last rebuild attempt failed; previous output must not be presented as current.

Projection-backed API responses expose at least `projectionStatus`, `asOf`, `inputWatermark`, `lastSuccessfulBuildAt`, and `staleFrom` when applicable. A previous successful value may be displayed as historical/stale evidence, but never silently as current truth.

## Fresh-database migration plan

### Reset procedure to implement

1. Tag or branch the current code and retain [db-dump.sql](db-dump.sql) plus its hash as legacy-schema evidence.
2. Stop the application and use the already-created empty `extreme_accounting` database as the rewrite target; drop/recreate it explicitly whenever a pre-release reset is needed.
3. Remove legacy `V2`–`V14` from the active migration path; rely on Git history instead of an `old/` folder.
4. Remove `schema.sql`, legacy Spring Batch initialization/config, and unneeded batch tables.
5. Add a reviewed `V1__foundation.sql` for schemas, identity/auth/session and the minimal platform job/audit foundation.
6. Add `V2__reference.sql` with canonical reference tables and stable-code seeds.
7. Refactor JPA mappings and services in the same branch so `ddl-auto: validate` succeeds against the new schema.
8. Enable Flyway by default; set `baseline-on-migrate: false`, `validate-on-migrate: true`, and keep clean disabled in normal application startup.
9. Add a PostgreSQL Testcontainers empty-to-latest integration test and container startup smoke test.
10. Only then add feature migrations as their vertical slices are implemented.

Do not pre-create hundreds of speculative columns/tables. The target table descriptions in the feature docs are a design catalogue, not a command to create all tables in `V1`.

### Coherent future migration slices

| Migration slice            | Creates/changes                                                                                                                |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `V1__foundation`           | Schemas, users/auth identities/device sessions, audit/security events and durable platform jobs                                |
| `V2__reference`            | Countries, currencies, markets, instruments/aliases/calendars and stable-code reference seeds                                  |
| `V3__ledger_investing`     | Financial accounts/cash pockets, immutable activities/postings/idempotency/reconciliation, portfolio groupings and projections |
| `V4__observations`         | Datasets/providers/series/immutable price, FX, rate, CPI and manual-value observations plus latest projections                 |
| `V5__analysis`             | Daily valuation/performance, calculation runs, scenarios and dependency manifests                                              |
| `V6__everyday_money`       | Categories/rules, recurring expectations, contracts/bills, cards/debt, income and documents/actions                            |
| `V7__planning`             | Goals/resilience, decision journal/briefs and plan calculation snapshots                                                       |
| `V8__people_household`     | Counterparties, claims/shared expenses, households/members/scoped grants and family flows                                      |
| `V9__purchases_protection` | Purchases/receipts/recoveries/stored value, utilities, projects, protection and invoices                                       |
| `V10__policy_calendar`     | Versioned jurisdiction/policy/rule/calendar records                                                                            |
| `V11__physical_assets`     | Physical-asset lifecycle, interests, meters/consumption, costs, service/warranty, valuations and disposal                      |

Names/versions may change as implementation progresses. The rule is one coherent, deployable, tested slice per migration—not one giant future schema or one file per table.

### Seed policy

- Versioned Flyway seeds contain only stable reference facts required for the application to start.
- Demo users, transactions, prices, bills, claims, and assets never enter normal Flyway migrations.
- A `demo-data` profile/command loads one versioned synthetic scenario dataset and can delete/reload that dataset safely.
- Normal user/manual data and demo data must not share an owner/scope.

### Demo loader implementation rules

The demo loader is an application-level scenario runner, not a SQL/JPA seed script.

It must:

1. run only when demo loading is explicitly enabled;
2. refuse to run when a production profile/environment guard is active;
3. create a dedicated synthetic owner/household that never shares a normal user scope;
4. use deterministic IDs, a fixed clock and a fixed seed per dataset version;
5. invoke normal application command services for financial facts;
6. never directly insert balance, position, valuation, performance or other derived projections;
7. ingest synthetic observations through the same observation-ingestion path used by manual/file/provider inputs;
8. be safe to delete and reload by dataset code/version;
9. mark created facts/observations as `SYNTHETIC` and surface dataset/version in derived API output;
10. exercise the same validation, authorization, idempotency, correction and rebuild invariants as real commands.

Direct insertion is reserved for stable reference facts owned by Flyway. The demo loader must not use repository `save(...)` or ad hoc SQL to create states that a real application workflow could not create.

Store a small dataset manifest such as `dataset_code`, `dataset_version`, `seed`, `fixed_clock`, `loaded_at`, and `status` so reloads and screenshots/tests can prove which universe produced a result.

### Demo dataset evolution

Build the coherent demo universe incrementally rather than blocking early slices on the complete 24-month scenario:

- `demo-v1-ledger` — accounts, deposits, withdrawals, transfers and reversals;
- `demo-v2-investing` — brokerage funding, trades, fees, dividends and backdated corrections;
- `demo-v3-observations` — price/FX/rate/CPI series, gaps, stale data and revisions;
- `demo-v4-analysis` — valuation, decomposition, TWR/XIRR and Decision Replay inputs;
- `demo-v5-everyday-money` — salary, spending, bills, cards/debt and documents;
- later versions — household/claims, purchases/recovery, policies and physical assets.

These development versions may later converge into one user-facing versioned household demo dataset. The full household dataset is an integration/end-to-end fixture, not the sole proof of calculation correctness.

## Provider-independent and fake-data strategy

### Data modes

Every imported or observed fact has a source mode:

| Mode             | Meaning                                                    | Allowed use                                          |
| ---------------- | ---------------------------------------------------------- | ---------------------------------------------------- |
| `USER_ENTERED`   | User entered/reviewed the value                            | Actual user history, with manual quality label       |
| `FILE_IMPORTED`  | CSV/JSON/statement file with retained batch/row provenance | Actual history after validation/review               |
| `REFERENCE_SEED` | Stable application reference/policy sample                 | Startup/reference behavior                           |
| `SYNTHETIC`      | Deterministic generated/test/demo value                    | Demo and automated tests only; always visibly fake   |
| `PROVIDER`       | Value obtained from a real external provider               | Actual analytics subject to licence/quality/coverage |

Common observation/provenance fields include dataset/source ID, source kind, provider key, effective time, publication/ingestion time, revision, original unit/currency, normalized value, quality flags, confidence, licence/retention metadata, and `synthetic` status.

### Source boundaries worth interfaces

Only real replaceable side effects needed by the current roadmap get ports:

- `MarketDataSource` for price/corporate-action series;
- `FxRateSource` and `IndexRateSource` for FX, CPI, deposit/reference rates;
- `DocumentExtractor` for OCR/structured extraction;
- `AssetValuationSource` for vehicle/property/product estimates;
- `NotificationSender` for email/push;
- optional `PolicyPackSource` for jurisdiction/tariff/rule updates.

Each starts with manual/file and synthetic implementations where applicable. Calculation services consume stored normalized data, never call HTTP directly.

### Deferred financial-account connectivity

The following are **not part of R0–R16** and must not drive current abstractions:

- `AccountFeedSource` or equivalent bank-feed ports;
- Open Banking connection/token/consent flows;
- bank/card synchronization or webhook ingestion;
- broker account synchronization;
- payment initiation or automatic bank transfers.

Manual entry and file import remain valid long-term workflows even if one of these integrations is added later. A future integration should normalize into the same import/reconciliation/ledger paths rather than become a parallel financial truth system.

### Source-selection policy

Once more than one source can supply the same observation family, selection is an explicit application policy rather than an ad hoc query. At minimum the policy defines:

- acceptable source kinds and source priority;
- requested/as-of time and no-look-ahead publication rules;
- maximum staleness;
- missing-data behavior;
- whether manual values may override or act as fallback;
- interpolation/carry-forward rules when explicitly allowed;
- required quality/revision state.

No valuation, performance or scenario service may independently invent its own price/FX fallback logic. The selected source/observation and fallback decision remain traceable in calculation dependencies and API coverage metadata.

### Fake providers versus HTTP mocks

Synthetic/manual implementations are in-process implementations of provider interfaces. They do not run standalone fake HTTP servers.

Example:

```text
MarketDataSource
  - SyntheticMarketDataSource
  - CsvMarketDataSource
  - RealProviderMarketDataSource
```

Use HTTP mocking such as WireMock only when testing a real HTTP adapter. The layers are:

1. pure/domain/application tests — in-process fake implementations, no HTTP;
2. provider-adapter contract tests — WireMock or equivalent with saved/sanitized provider payload fixtures, including `429`, `5xx`, timeouts, malformed rows, partial data and revisions;
3. optional live smoke tests — real sandbox/provider credentials, never required for normal CI.

Business, valuation and scenario tests must not require an HTTP mock server.

### Offline/fake replacement matrix

| Needed data                         | First implementation without internet           | Synthetic/test implementation                                      | Later provider path                                               |
| ----------------------------------- | ----------------------------------------------- | ------------------------------------------------------------------ | ----------------------------------------------------------------- |
| Instrument prices/total-return data | CSV/manual observation import                   | Seeded scripted/random-walk series with gaps, splits and revisions | Licensed market-data adapter                                      |
| FX                                  | Manual executed rates plus CSV reference series | Deterministic TRY/USD/EUR/GBP series                               | Central-bank/licensed FX adapter                                  |
| Deposit/rate products               | User-entered terms and sample policy            | Fixed/rolling sample schedules                                     | Bank/product/rate source                                          |
| CPI/inflation                       | CSV/manual index values and personal basket     | Deterministic monthly index                                        | Official statistics source                                        |
| Bank/broker activity                | Manual entry and reviewed CSV import            | Synthetic statements containing duplicates/corrections             | Deferred future account/broker connectivity; outside this roadmap |
| Corporate actions                   | Manual reviewed event                           | Split/dividend/merger fixtures                                     | Licensed corporate-action source                                  |
| Bills/subscriptions/contracts       | Manual entry/CSV                                | Synthetic recurring contracts and price changes                    | Bank/email/biller discovery later                                 |
| Receipts/documents                  | Manual structured form and file attachment      | Fixture extraction JSON and stub OCR results                       | OCR/document provider                                             |
| Product prices/return windows       | Manual receipt lines/policies                   | Sample catalogue and recovery cases                                | Licensed retailer/product source                                  |
| Utility use/tariffs                 | Manual meter/tariff entries and CSV             | Synthetic seasonal usage and tiered tariff                         | Utility/open-data/provider adapter                                |
| Vehicle/asset valuations            | Manual appraisal/range/offer                    | Synthetic depreciation/market-value observations                   | Licensed valuation/VIN source                                     |
| Tax/government rules                | Versioned sample policy pack                    | Edge-case sample jurisdiction                                      | Official content plus specialist review                           |
| Notifications                       | In-app action queue                             | Capturing/log sender                                               | Email/push provider                                               |
| AI categorization/import            | Deterministic rules and review form             | Fixed classifier/extraction fixtures                               | Optional model provider                                           |
| Authentication                      | Local email/password/dev tokens                 | Test principals/JWT fixtures                                       | Google/OIDC and future providers                                  |

### One coherent demo universe

Do not generate unrelated random rows. Build one deterministic 24-month household scenario from a fixed seed and version:

- two household members with private and shared views;
- TRY salary/current/savings accounts, USD cash, two brokerage accounts, one credit card, a personal loan and term deposit;
- synthetic BIST-like share, index, gold, FX, deposit-rate, and CPI series;
- buys/sells/dividends/fees/taxes, bank-to-broker transfers, a backdated correction, stale/missing observation, and an unreconciled import;
- salary/payslip, ordinary spending, annual insurance, subscriptions with price changes, bills, refund, cash purchase, gift card and installment;
- friend IOU, shared trip, employer reimbursement, invoice, and selective household permission;
- goals, emergency reserve, irregular-income month, Decision Replay and decision review;
- a vehicle with financed acquisition, odometer/fuel/service/insurance/warranty/valuation/disposal-ready history for FT-32;
- documents/actions including return deadline, expiring warranty, renewal and claim.

The same scenario supplies demo screens, end-to-end/integration fixtures and future mobile offline tests. Values must be labeled synthetic in every response. Use a fixed clock and seed so test results never drift. Pure financial calculations additionally use small hand-worked golden fixtures whose expected result can be independently verified; do not make TWR, XIRR, cost basis, FX attribution, deposit/debt calculations or similar mathematics depend exclusively on the large demo universe.

### What “implement every feature” means without real data

It is valid to complete:

- the domain model and commands;
- manual/CSV workflows;
- calculation engine;
- source/provenance/coverage contract;
- deterministic sample policies/data;
- tests and synthetic demo experience.

It is not valid to claim:

- current market value from a synthetic series;
- an official tax/benefit result from a sample policy;
- guaranteed warranty/insurance eligibility;
- comprehensive product/merchant coverage without a licensed catalogue;
- live account balance without a provider or reconciliation.

Feature completeness and real-data coverage are separate release dimensions and must be reported separately.

## Pull-request execution model

R0–R16 are roadmap increments, **not pull-request sizes**. Implementation must be split into small, independently reviewable PRs. A PR should normally introduce one coherent user capability, invariant, migration step, or infrastructure behavior that a human can review without reconstructing several later features at once.

Rules:

1. Write the next PR specification just in time, normally one to three PRs ahead, rather than pre-specifying the entire rewrite.
2. `docs/implementation/CURRENT.md` identifies the active PR specification. Agents should discover it through root `AGENTS.md`; the user should not need to restate the coding standards or active-spec rule in every prompt.
3. A PR specification references this master plan, [accounting-contract.md](accounting-contract.md), and [../engineering/coding-standards.md](../engineering/coding-standards.md); it does not duplicate or silently redefine those long-lived rules.
4. Every PR lists explicit scope, non-goals, database/API changes, invariants, required tests, acceptance criteria, and verification commands.
5. If a PR becomes difficult to review, split it even if an agent could implement the larger change correctly. Human reviewability is a design constraint.
6. If implementation discovers a cross-cutting rule change, update the authoritative contract first, then the PR spec, then code.
7. At completion, record implemented scope, deviations, tests and follow-ups in the PR specification and update [progress-report.md](progress-report.md).

## Exact scratch-rewrite execution checklist

The increments below are the implementation order. Complete each numbered item inside an increment before its exit gate. The later “Implementation stages” section summarizes the product gates; this section is the coding checklist.

### R0 — Preserve evidence and replace the backend skeleton

1. Create the rewrite branch/tag and record the current commit in [progress-report.md](progress-report.md).
2. Confirm `extreme_accounting` is empty/disposable; do not execute [db-dump.sql](db-dump.sql) against it.
3. Record the current HTTP route list solely as compatibility reference.
4. Remove legacy Java backend packages after Git preservation; keep `src/main/web` and review documents untouched.
5. Remove old mapper XML, `schema.sql`, `V2`–`V14`, certificate files, and unused data/integration resources from the active application.
6. Reduce `pom.xml` to the dependency set in the cutover strategy; pin Spring Boot 4.1.0, enforce Java 25, rely on Boot-managed dependency versions, configure explicit annotation processing only where required, disable preview features, and add Testcontainers.
7. Create one `SpringBootApplication`, one global problem-details handler, and minimal security/configuration under `dev.canverse.stocks`.
8. Configure the default datasource as `extreme_accounting`, Flyway enabled, `baseline-on-migrate=false`, Hibernate `ddl-auto=validate`, Open Session in View off, and Spring Batch initialization absent.
9. Externalize signing keys/credentials; create a documented disposable local-key flow.
10. Add fixed-clock and ID-generation test support plus a container health/context smoke test.

Exit gate: the reduced application compiles on Java 25; the deliberately added empty-database startup test fails solely because `V1` is not yet present; no legacy entity or mapper is being scanned.

### R1 — Foundation, identity, authentication, sessions, and jobs (`V1`)

1. Create schemas `identity`, `reference`, `ledger`, `data`, `money`, `analysis`, `asset`, and `platform` in Flyway.
2. Create migration-owned `identity.user_account`, `identity.auth_identity`, `identity.device_session`, `platform.security_event`, and `platform.job` tables.
3. Put UUID keys, uniqueness, foreign keys, checks, timestamps, revocation rules, and required indexes entirely in `V1__foundation.sql`.
4. Map minimal identity/session JPA entities without index/constraint/DDL annotations.
5. Implement local email/password registration/login for development, password hashing, refresh-session rotation, logout and session revocation.
6. Model Google as an external auth identity but keep it disabled until issuer/audience/email verification tests pass.
7. Implement principal/owner helpers and stable RFC 9457-style problem codes.
8. Implement the durable job claim/retry state machine without Spring Batch.
9. Add authorization, duplicate-email/identity, refresh-reuse, revoked-session, cross-user, and job-lock integration tests.
10. Add minimal authentication abuse protection: login/register throttling, authentication-failure security events, configurable progressive delay or temporary lock behavior, and tests proving disabled/revoked users are rejected on token conversion/refresh.

Initial endpoints:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET/DELETE /api/v1/auth/sessions/{id}`
- `GET /api/v1/me`

Exit gate: an empty `extreme_accounting` database migrates, starts, registers a user, rotates/revokes a device session, and rejects cross-user access.

### R2 — Canonical reference model and deterministic seeds (`V2`)

1. Create `reference.country`, `currency`, `market`, `market_currency`, `instrument`, `instrument_alias`, and `market_calendar` tables.
2. Use stable codes for currency/country and UUIDs for markets/instruments; never reference generated numeric seed IDs.
3. Store evolving types as application text values with migration-owned checks only when sufficiently stable; do not recreate PostgreSQL enums.
4. Seed only necessary stable data: at minimum TRY/USD/EUR/GBP, Turkey/UK/US, a manual market, and BIST reference identity if needed.
5. Implement manual instrument/market administration and alias lookup; do not fetch network data.
6. Add currency/unit value objects, normalization and exact decimal JSON tests.
7. Add referential-integrity, duplicate symbol+market/alias, inactive-reference, and timezone/calendar tests.

Initial endpoints:

- `GET /api/v1/reference/currencies`
- `GET /api/v1/reference/markets`
- `GET /api/v1/instruments?query=&cursor=`
- owner/admin-only manual instrument commands

Exit gate: all stable references are recreated solely from Flyway, no query depends on a hardcoded database ID, and entity mappings contain no database index/constraint definitions.

### R3 — Financial accounts, opening state, immutable ledger, funding, and balances (`V3`, FT-31)

1. Apply the signed-posting, opening-state/coverage, balance, time/economic-ordering and correction rules from [accounting-contract.md](accounting-contract.md); define only account-specific invariants not already covered there.
2. Create `ledger.financial_account`, `account_cash_pocket`, `account_limit_policy`, `activity`, `money_posting`, `security_posting`, `activity_split`, `idempotency_record`, `account_balance_projection`, and `reconciliation`.
3. Add migration-owned uniqueness for user/account identity, activity client IDs, account+currency pockets, and projection keys.
4. Implement account kinds/tracking modes/capabilities and `HARD_FLOOR`, `SOFT_FLOOR`, `AUTHORIZED_LIMIT`, and `TRACK_REALITY` negative behavior.
5. Implement account onboarding with either zero opening state or an explicit user-entered opening state as of a chosen effective time. Support cash/account balances and liability balances here; later modules extend the same opening-state contract to holdings and receivables/payables. Opening state is not income, spending or investment performance.
6. Persist historical-coverage metadata so reads/calculations can state that history before the opening-state boundary is unknown/incomplete rather than inferred.
7. Implement immutable activity commit, correction/reversal relationship, deterministic ordering, and account lock order.
8. Implement deposit, withdrawal, owned transfer, fee, interest and explicit adjustment activities.
9. Implement funding preview with before/after ledger, cleared, pending, available, reserved, committed, overdraft, and credit values clearly separated.
10. Implement statement-balance reconciliation without silently replacing ledger balance.
11. Add idempotency, retry, concurrent spending, negative-policy, transfer-neutrality, reversal, backdating, currency, opening-state/coverage and owner tests.

Initial endpoints:

- account CRUD/archive/balance/reconciliation under `/api/v1/accounts`
- account opening-state command/query under `/api/v1/accounts/{id}/opening-state` or equivalent cohesive onboarding API
- `POST /api/v1/transfers/previews` and idempotent commit
- `POST /api/v1/activities` for supported low-level/manual facts
- correction/reversal endpoints; no destructive undo

Exit gate: a new user can start from current known balances without reconstructing prior history; deposits, withdrawals and transfers replay exactly; concurrent/retried commands cannot duplicate or silently overspend; history coverage is explicit; this is the only cash truth used by later modules.

### R4 — Investing parity, funded trades, projections, and imports

1. Add portfolio/report grouping and account membership; portfolios never hold implicit cash.
2. Add rebuildable `position_projection` using the initial `WEIGHTED_AVERAGE_ECONOMIC_V1` policy from [accounting-contract.md](accounting-contract.md); add `lot_projection`/disposal allocation records only when a concrete lot/tax policy requires them.
3. Implement owned cash-account-to-brokerage funding as a transfer and buy/sell as linked cash/security/fee/tax postings; no live bank/broker connection is assumed.
4. Make commission and tax currency/treatment explicit; implement dividends, withholding and simple corporate actions.
5. Implement deterministic replay for backdated trades, full close/reopen, corrections and corporate actions.
6. Treat imports as a primary ingestion workflow: file -> parse -> normalize -> preview -> issue/duplicate detection -> matching/reconciliation -> user confirmation -> normal application command services -> ledger. Reuse common `ImportBatch`/row/issue/match/commit concepts without building a generic framework beyond demonstrated formats.
7. Implement manual trade entry and CSV import with validation totals, fingerprints, duplicate detection and atomic commit.
8. Implement holdings-only import/opening-holding mode with explicit historical/cash/cost-basis coverage instead of inventing money.
9. Create portfolio/position/trade read models with `JdbcClient`; do not recreate mutable `Position.buy/sell` authority.
10. Add fee-aware buy/partial-sell/full-sell, fractional quantity, multi-currency, import retry, opening-holding coverage, concurrency and access golden tests.

Initial endpoints:

- `/api/v1/portfolios`
- `/api/v1/investing/positions`
- funded trade preview/commit under `/api/v1/trades`
- `/api/v1/imports` preview/status/commit

Exit gate: a selected broker cash pocket funds every full-ledger trade, cash/quantity/basis/proceeds reconcile, and inserting the same economic history in another request order yields the same projection.

### R5 — Observation platform, manual data, and the synthetic universe (`V4`)

1. Create `data.dataset`, `source`, `series`, `observation`, `ingestion_run`, and `latest_observation` projection.
2. Support price/total-return, FX, rate, CPI/index, manual asset value and corporate-action observation families with original unit/currency retained.
3. Store effective/publication/ingestion times, revision, quality, confidence, licence/retention and synthetic/manual/provider provenance.
4. Implement manual entry and CSV/JSON observation import first.
5. Implement provider interfaces but only manual/file/synthetic implementations; calculations read normalized stored observations, never HTTP.
6. Establish the fixed-seed 24-month demo-universe timeline/manifest and load the portions supported through R5 (identity/reference, ledger, investing and observations). Later slices extend the same coherent universe through normal command services rather than pre-seeding unimplemented feature tables.
7. Prevent synthetic datasets from sharing a normal user scope and include `synthetic=true`/dataset version in every derived API response.
8. Implement latest projection, missing/stale policies, revision supersession and affected-date invalidation.
9. Implement the explicit observation source-selection policy (priority, staleness, no-look-ahead, missing/fallback/manual-override behavior) and persist selection dependencies for derived calculations.
10. Implement the demo scenario runner through normal command/ingestion services; never seed financial facts or projections directly through repositories/SQL.
11. Add tests with networking disabled for gaps, weekends/calendars, revision, no-look-ahead publication time, stale coverage, source selection and deterministic reload/delete.

Exit gate: all subsequent analytics can run entirely from manual/fixture data; any value can be traced to dataset/source/date/revision/quality, and no synthetic value can masquerade as user/provider truth.

### R6 — Reconciled timeline, net worth, and investment truth (`V5`, FT-01/02/11)

1. Create calculation-run/dependency records and daily account/portfolio/household valuation projections.
2. Implement native and reporting-currency valuations using effective-date FX observations.
3. Implement FT-01 current/historical assets, liabilities and net worth plus a traceable money timeline.
4. Implement opening value + external flows + price + FX + income − fees − tax + residual = closing value.
5. Implement TWR, XIRR, realized/unrealized basis, benchmark alignment, drawdown and concentration only after golden definitions pass.
6. Implement FT-02 explanation endpoints with as-of/source/coverage/calculation version.
7. Implement FT-11 target allocation and contribution-first rebalance proposals; proposals never post trades.
8. Add cash-flow timing, mixed-currency, missing prices, stale FX, fee/tax, backdated correction and no-look-ahead tests.

Exit gate: every headline metric traces to facts/observations, reconciliation residual is explicit, and synthetic/manual coverage is visible.

### R7 — Decision Replay, alternatives, purchasing power, and decision learning (FT-06/07/08/09/12)

1. Create immutable scenario, scenario version, strategy configuration, run, result and dependency records.
2. Implement a pure common dated-cash-flow simulation interface.
3. Add market instrument/index, gold, FX holding, fixed/rolling deposit, debt prepayment and inflation-deflator strategies.
4. Add effective-dated sample/manual policies for day count, compounding, withholding, spreads, fees and debt interest.
5. Implement FT-06 identical-cash-flow comparisons and path metrics.
6. Implement FT-07 deposit/debt/FX engines, FT-08 purchasing power and personal baskets, and one validated FT-09 purchase/life template at a time.
7. Implement FT-12 decision reason/expectation/alternative snapshots and scheduled outcome review.
8. Export exact inputs/assumptions/results; prohibit a saved run from silently adopting newer data.
9. Add reproducibility, no-look-ahead, cash-flow equality, missing-data, policy-version, early-break/repayment and sensitivity tests.

Exit gate: actual versus alternatives is reproducible from exported inputs, clearly historical/hypothetical, and works with the synthetic universe without claiming advice.

### R8 — Spending truth, recurring commitments, bills, and monthly close (`V6`, FT-15/03/18)

1. Create category hierarchy, merchant/counterparty identity, activity classification/split and ordered rule tables.
2. Build an inbox for unresolved transfer/spending/income/refund/card-payment classification.
3. Implement FT-15 spending/income reports and a month-close reconciliation over canonical activities.
4. Create recurring pattern, planned occurrence, contract, price phase, bill/obligation and bill-payment matching records.
5. Implement FT-03 prediction/calendar separately from FT-18 issued/owed contract and bill truth.
6. Ensure expected bills affect forecast/commitment only; only a posted payment changes account balance.
7. Allow an obligation to record an intended funding account and payment rule (for example, "pay Amex statement in full from Monzo") for forecasting/reminders only. Selecting that account does not authorize or initiate payment.
8. When due, support manual recording/confirmation of the actual payment and link that posted transfer/payment to the obligation; derive upcoming/due/overdue/partially-settled/settled state from dates and settlement allocations where possible.
9. Implement trial, notice, renewal, cancellation and price-change action dates.
10. Add transfer/card/refund/reimbursement/non-cash obligation/double-count, planned-versus-actual payment and recurrence-confidence tests.

Exit gate: the demo month closes with spending, income, investments, transfers, refunds and open bills reconciled, while forecasts never alter history.

### R9 — Income, cards/debt/installments, documents, and actions (FT-20/29/23)

1. Create income stream, pay statement, pay component and reimbursement-claim records; reconcile gross, deductions and net.
2. Create card account/statement/statement-line/payment requirement, loan/debt terms, rate period, installment and BNPL schedule records.
3. Implement FT-29 principal/interest/fee/grace/limit/overdraft behavior with selected funding accounts.
4. Implement FT-20 salary, irregular income, benefits and employer reimbursements without treating transfers/borrowed principal as income.
5. Create document metadata, storage object, source evidence, extraction preview and action queue records.
6. Implement local file storage for development with type/size/hash/malware-hook controls; structured manual entry remains available.
7. Implement a deterministic stub extractor; extracted fields cannot create financial facts before review/commit.
8. Add card purchase/payment, loan draw/principal/interest, statement reconciliation, payslip and document authorization/deletion tests.

Exit gate: cards, loans, income and documents reconcile with the ledger; a card payment or loan principal cannot distort consumption/income.

### R10 — Commitments, resilience, goals, irregular income, and briefs (`V7`, FT-04/05/10/14)

1. Create plan, goal, target schedule, account allocation, reserve policy and calculation snapshot records.
2. Implement FT-04 liquid runway and available-after-commitments from actual balances plus explicit committed/expected flows.
3. Implement FT-05 goals/sinking funds as labels/reservations unless an actual transfer posts.
4. Implement FT-10 conservative irregular-income baseline, high-cost-month and sample tax reserve policies.
5. Implement FT-14 deterministic evidence-backed brief facts; generated prose may explain but never calculate/invent.
6. Add confidence/sensitivity, missing-contract, reserved-versus-held, goal double-count and stale-input tests.

Exit gate: every prepare number traces to a fact, obligation or versioned plan and is visibly not a guaranteed balance.

### R11 — Households, people, IOUs, shared expenses, and family flows (`V8`, FT-13/16/17/28)

1. Create household, member, scoped grant, counterparty, claim, immutable claim-event, settlement-allocation and resolution records as needed.
2. Implement mine/yours/shared ownership and field/aggregate authorization without requiring all counterparties to register.
3. Implement FT-16 lend/borrow principal, partial repayment, interest-free first, forgiveness/waiver, dispute and write-off behavior with optional valuation confidence.
4. Implement FT-17 payer/share allocation and exact minor-unit rounding. A shared expense retains provenance to the originating canonical activity/split so the payer's personal consumption is counted once and other participants' shares become receivables/payables rather than extra spending/income.
5. Model repayment as ordinary cash activity linked through settlement allocation to claims. Repayment of a receivable is not income; settlement of a payable is not new spending.
6. Support one payment settling multiple claims and one claim being settled by multiple payments. Derive outstanding amount from original claim plus immutable settlement/resolution events instead of a mutable `paid` flag.
7. Support clear lifecycle/result semantics such as `OPEN`, `PARTIALLY_SETTLED`, `SETTLED`, `DISPUTED`, `WAIVED`, and `WRITTEN_OFF`, deriving statuses from amounts/events where possible rather than duplicating state.
8. Extend the opening-state contract so a user can start with an existing receivable/payable as of the tracking-coverage boundary without reconstructing its original historical purchase/loan.
9. Implement FT-28 family support, allowances, gifts, giving and recurring commitments using canonical transfers/activities.
10. Add lender/borrower, partial/multi-claim settlement, waiver/write-off/dispute, foreign-currency claim, opening receivable/payable, privacy, invitation and household aggregation tests.

Exit gate: every claim can trace to an originating activity or explicit opening-state assertion; claims reconcile with cash/spending/net worth; partial/multi-claim settlements work without double-counting; no permission path leaks private activities or aggregates.

### R12 — Purchases, receipts, recovery, and restricted value (`V9`, FT-19/22/24)

1. Create shopping list/item/trip, receipt/line, purchase/item/payment and ownership-event records linked to canonical ledger facts.
2. Implement manual receipt entry and stub-extraction review; match payment totals without copying expenses.
3. Implement FT-19 item price history, return/warranty dates and durable-purchase promotion.
4. Create recovery case/event records for FT-22 refund, dispute, return, warranty, insurance and reimbursement flows.
5. Implement deadlines/actions/evidence and confirmed recovery posting; expected recovery never changes cash.
6. Implement FT-24 physical cash, gift card, store credit and rewards accounts with restriction/expiry/valuation confidence.
7. Add split tender, partial return, refund to card/store credit, warranty reimbursement, receipt allocation and restricted-liquidity tests.

Exit gate: receipt/payment/item/recovery totals reconcile end to end and no refund or card settlement is counted twice.

### R13 — Utilities, projects, protection, and freelancer money (`V9`, FT-25/26/27/21)

1. Add utility meter/reading, tariff version/tier and bill-allocation records; start with manual/sample tariffs.
2. Implement FT-25 normalized usage, effective tariff calculations and scenario comparison with source/sample labels.
3. Add project membership/allocation/budget records over existing facts; implement FT-26 travel/event/move/renovation views.
4. Add protection policy/coverage/beneficiary/renewal links; implement FT-27 gaps/actions without suitability or claim guarantees.
5. Add invoice/client/payment/allocation records; implement FT-21 receivable, overdue action, partial payment, expense and reserve views.
6. Reuse claims, documents, contracts, categories and ledger rather than creating separate sub-ledgers.
7. Add tiered tariff, meter correction, project allocation, policy expiry/claim and invoice partial-payment tests.

Exit gate: each vertical works manually with sample data, links to canonical money, and states regulatory/data limitations.

### R14 — Versioned tax/government policy calendar (`V10`, FT-30)

1. Create jurisdiction, policy pack/version, rule, source citation, applicability answer, calendar occurrence and review-status records.
2. Implement a small deterministic policy expression/calculation interface; do not create arbitrary executable scripts.
3. Load one explicitly sample jurisdiction pack for automated tests.
4. Implement user-confirmed calendar/reserve/document actions, not filing or automatic eligibility/liability promises.
5. Suppress or strongly warn on stale/unreviewed/non-applicable rules.
6. Add effective-date boundary, stale-pack, applicability, calculation-version and source-trace tests.

Exit gate: FT-30 is fully demonstrable with a sample pack while the API cannot mislabel it as official current law.

### R15 — Physical-asset lifecycle and TCO (`V11`, FT-32)

1. Create the exact common records from [real-asset-lifecycle-tco-design.md](real-asset-lifecycle-tco-design.md): physical asset/interest/lifecycle, identifiers, meters/readings, resource consumption, cost links, valuations, depreciation policy, maintenance/service, warranty and disposal.
2. Promote a durable purchase into an asset without duplicating its acquisition/payment.
3. Link acquisition cash, loan principal/liability, interest, insurance, fuel/energy, maintenance, repair, claims/recoveries and resale.
4. Implement cash burden, economic TCO, net-worth contribution and forecast as separate calculations.
5. Implement cost per month/km/mile/hour/cycle with explicit numerator, denominator, period and coverage.
6. Implement manual valuation ranges and synthetic depreciation/usage series before any valuation/VIN/telematics provider.
7. Implement service/warranty/action status and sale/trade/disposal; then keep/repair/replace and own/lease/rent scenarios.
8. Add financed acquisition, annual premium recognition, card repair/refund, shared energy, meter reset, multi-currency, valuation range, negative-equity trade-in and disposal tests.

Exit gate: the complete vehicle MVP reconciles ledger, liability, usage, current value and TCO without adding purchase price, principal and depreciation twice.

### R16 — Stable API, export/deletion, operational hardening, and optional providers

1. Audit all endpoints for `/api/v1`, string UUIDs, decimal strings, cursor pagination, optimistic versions and stable problem codes.
2. Add compact home/timeline/action/read projections and sync/change-feed semantics for future Expo clients without assuming a connected bank feed.
3. Complete account/household export, attachment export, scoped clear/delete, retention, consent and security audit flows.
4. Add import/rebuild/scenario job status, retries, dead-letter/manual recovery and monitoring.
5. Add OpenAPI compatibility, migration, security, no-network, container, backup/restore and performance smoke gates.
6. Make the Docker build run tests and use generated/environment secrets only.
7. Add optional real provider implementations only for current-roadmap external data families (market data, FX/rates/indexes, document extraction, asset valuation, notifications/policy sources as justified). Validate licence, consent where relevant, retry, revision, deletion and fallback behavior before enabling.
8. Explicitly keep Open Banking, bank/card synchronization, broker synchronization and payment initiation outside R16. Do not create speculative financial-connection infrastructure for them.
9. Run the full deterministic household scenario and every feature exit gate.

Exit gate: the scratch backend is the only backend, all 32 feature engines have manual/file/synthetic coverage, no optional internet provider is needed for startup or core tests, and no feature depends on financial-account connectivity.

## Implementation stages and product exit gates

No calendar estimates are assigned. Each stage ends with evidence and can be delivered through several small pull requests.

### Stage 0 — Contain risk and lock contracts

Deliver:

- rotate/remove the tracked RSA private key and create environment-based local key generation;
- fix Google audience/issuer/verified-email validation or temporarily disable Google in favor of local development auth;
- enforce Java 25 and make the build reproducible;
- pin Spring Boot 4.1.0 as the rewrite baseline, use Jakarta Persistence 3.2 through the Boot-managed Hibernate 7.4.x stack, and prohibit speculative dependency overrides/preview APIs;
- approve rules for IDs, decimal/rounding, timestamps, economic ordering, reversals, fees/tax, FX, valuation, calculation versions, and data coverage;
- mark the README demo/deployment claims as stale if the application is not deployed;
- snapshot/branch current code before the database reset.

Exit gate: no known secret/false claim remains, and the core contracts in this plan are accepted.

### Stage 1 — Fresh baseline and test harness

Deliver:

- replace migrations with `V1__foundation.sql`, `V2__reference.sql`, and later slice-owned migrations;
- enable Flyway in all relevant profiles;
- remove empty `schema.sql` and unused Spring Batch bootstrap;
- add PostgreSQL Testcontainers and empty-to-latest startup tests;
- standardize IDs, exact numeric types, `timestamptz`, FK names, indexes, and delete behavior;
- seed reference currencies/countries/markets by stable code;
- add deterministic clock/ID/test fixtures.

Exit gate: deleting the database and starting the backend reconstructs it fully; Hibernate validation and all migration tests pass without an existing local database.

### Stage 2 — Accounts, immutable ledger, and current trade cutover

Deliver:

- financial accounts/cash pockets, tracking modes, negative/credit policies;
- immutable activity, cash/security postings, idempotency, correction/reversal;
- deposit/withdrawal, transfer, FX, fee/tax, buy/sell, dividend/interest;
- cash/position projection and deterministic backdated rebuild;
- funding preview and selected brokerage cash for trades;
- manual/CSV import preview, duplicate fingerprints, and reconciliation;
- new `/api/v1` portfolio/trade endpoints and only deliberately chosen temporary compatibility routes for the existing React client.

Exit gate: all FT-31/cash-account invariants and fee/backdated/concurrent/retry fixtures pass. A full-ledger buy cannot occur without stated funding; holdings-only migration/import remains explicit.

### Stage 3 — Observation platform and synthetic data foundation

Deliver:

- provider/dataset/series/immutable observation/ingestion-run schema;
- price, adjusted-return/corporate-action, FX, rate, CPI and manual-value observation types;
- latest observation projection, quality/staleness/licence metadata;
- manual CSV import and deterministic demo sources;
- affected-range invalidation and data coverage response contract;
- provider interfaces with disabled/no-network defaults.

Exit gate: any valuation states date/source/revision/quality/synthetic status; tests run with networking disabled; revisions and missing observations rebuild or warn deterministically.

### Stage 4 — Reconciled truth and investment analytics

Deliver:

- current/historical account, portfolio, household assets/liabilities/net worth (FT-01);
- daily NAV and reconciled opening + flows + P&L = closing bridge;
- TWR, XIRR, realized/unrealized basis, income/fees/tax/cash drag;
- return decomposition into price, FX, income, fees, tax and residual (FT-02);
- benchmark/risk/concentration only from compatible total-return series;
- contribution-first rebalancing plan that does not trade automatically (FT-11).

Exit gate: golden multi-currency and cash-flow fixtures reconcile exactly; no metric silently substitutes current FX, cost, or future observations.

### Stage 5 — Decision Replay and localized comparison engine

Deliver:

- common immutable calculation/scenario run and dependency manifest;
- actual-flow replay plus strategies for instrument/index, gold, FX, deposit, debt prepayment and inflation;
- point-in-time fees/spreads/tax assumptions, purchasing power, path/drawdown/time-in-front;
- purchase/life-decision templates and decision journal/review;
- manual/sample policy packs with effective dates and clear synthetic/sample labels.

Exit gate: FT-06/07/08/09/12 results reproduce from exported inputs, use identical dated flows, and contain no look-ahead data.

### Stage 6 — Everyday-money truth

Deliver:

- category/merchant/activity splits, ordered classification rules and transaction inbox;
- correct transfer/card payment/refund/reimbursement characterization;
- spending reports and reconciled monthly close (FT-15);
- service contracts, recurring patterns, bills, subscriptions, trials, renewals and Money Calendar (FT-03/18);
- income/pay statements/benefits/reimbursements (FT-20);
- credit-card statements, overdraft, installment, BNPL and debt schedules (FT-29);
- secure attachment/document metadata, extraction preview, and Money Action Queue (FT-23).

Exit gate: a synthetic/manual month containing cash, cards, transfers, refunds, bills, investments and debt closes with no double counting.

### Stage 7 — Prepare, goals, and irregular income

Deliver:

- available-after-commitments and runway/resilience (FT-04);
- goals/sinking funds linked to real accounts without pretending the allocation moved cash (FT-05);
- conservative irregular-income and reserve planning (FT-10);
- evidence-backed deterministic Money Brief (FT-14).

Exit gate: every forecast term traces to an actual fact, contract, plan, or sample assumption and cannot affect posted balance.

### Stage 8 — People and household money

Deliver:

- counterparties and receivable/payable claims with event history;
- personal IOUs/private loans with principal/interest/repayment semantics (FT-16);
- shared expenses, deterministic rounding, reimbursements and settlement (FT-17);
- selective household ownership/visibility/grants (FT-13);
- family support, allowances, gifts and giving (FT-28).

Exit gate: lender/borrower/shared purchase examples reconcile cash, spending, claims/liabilities and net worth; authorization tests prove no private-data leakage.

### Stage 9 — Purchases, recovery, work, utilities, and protection

Deliver:

- shopping lists, receipts/lines, purchases/items/payment matching and ownership events (FT-19);
- refunds, disputes, returns, warranties, insurance/reimbursement claims (FT-22);
- physical cash, gift cards, store credit/rewards restrictions (FT-24);
- utility meters, bills, manual/sample tariffs and cost comparisons (FT-25);
- project/event money over canonical records (FT-26);
- insurance/protection map and coverage actions (FT-27);
- invoices and lightweight freelancer receivables using existing income/claim/reserve primitives (FT-21).

Exit gate: receipt/payment/item/refund/claim/project totals reconcile, extracted data remains preview until confirmed, and sample tariff/protection results are labeled.

### Stage 10 — Local policy calendar

Deliver:

- versioned policy/rule/source records;
- user-confirmed tax/government calendar and reserve/document preparation (FT-30);
- stale-policy suppression and last-reviewed/owner metadata;
- sample jurisdiction pack for full automated tests.

Exit gate: the engine works against versioned sample rules but cannot present unvalidated rules as official liability, eligibility, filing, or advice.

### Stage 11 — Physical-asset lifecycle and TCO

Implement [real-asset-lifecycle-tco-design.md](real-asset-lifecycle-tco-design.md):

- vehicle-first asset identity/lifecycle/interest and purchase/funding/debt links;
- usage meters/readings, fuel/charge/resource events, costs and recognition periods;
- manual/synthetic valuation range, depreciation policy, maintenance, warranty, insurance and disposal;
- cash burden, economic TCO, net-worth context, forecast and cost per kilometre/month;
- keep/repair/replace and own/lease/rent scenario strategies;
- later reusable appliance/tool/HVAC/electronics templates.

Exit gate: FT-32 acquisition, loan, annual insurance recognition, card-paid repair/refund, meter reset, multi-currency, valuation range, trade-in/negative-equity and disposal fixtures pass without double counting.

### Stage 12 — API/mobile/operational hardening

Deliver:

- complete `/api/v1`, UUID/string IDs, decimal strings, cursor pagination, stable problem codes and idempotency;
- device refresh sessions, revocation, OIDC/PKCE identities, sync change feed and compact mobile read models;
- import/job progress and resume behavior;
- export/delete/retention/consent/audit flows;
- OpenAPI compatibility checks;
- monitoring for job failures, stale observations, reconciliation gaps, calculation failures and security events;
- container build that runs tests before packaging.

Exit gate: [mobile-api-readiness.md](mobile-api-readiness.md) is satisfied and the backend works under offline/retry/concurrency tests without placing financial logic in React or Expo.

## Complete feature-to-stage map

The detailed behavior and acceptance criteria live in [implementable-features.md](implementable-features.md). This table ensures no planned feature is lost.

| Feature                                    | Stage | Primary module     | Works without internet through                     |
| ------------------------------------------ | ----: | ------------------ | -------------------------------------------------- |
| FT-01 Reconciled Money Timeline/net worth  |     4 | ledger/analysis    | Manual/CSV activities and manual valuations        |
| FT-02 Investment Truth/return explanation  |     4 | investing/analysis | Synthetic/manual price/FX series                   |
| FT-03 Money Calendar                       |     6 | money              | User-entered contracts/recurrence                  |
| FT-04 Available after commitments/runway   |     7 | analysis           | Account balances + entered commitments             |
| FT-05 Goals/sinking funds                  |     7 | analysis           | User plans and manual target values                |
| FT-06 Decision Replay                      |     5 | analysis           | Synthetic/manual observations                      |
| FT-07 Deposit/debt/FX engines              |     5 | analysis           | User terms + sample policy packs                   |
| FT-08 Personal purchasing power            |     5 | analysis           | Personal basket + synthetic/manual CPI             |
| FT-09 Purchase/life decisions              |     5 | analysis           | User assumptions + sample templates                |
| FT-10 Irregular income/tax reserve         |     7 | money/analysis     | Manual income and sample reserve policy            |
| FT-11 Contribution-first rebalancing       |     4 | investing/analysis | Entered target allocation                          |
| FT-12 Decision journal/review              |     5 | analysis           | User-entered decision and saved scenario           |
| FT-13 Selective household money            |     8 | identity           | Local household/test principals                    |
| FT-14 Evidence-backed Money Brief          |     7 | analysis           | Deterministic read models/rules                    |
| FT-15 Spending/monthly close               |     6 | money/ledger       | Manual/CSV transactions                            |
| FT-16 Personal IOUs/private loans          |     8 | money              | Manual counterparties/claims                       |
| FT-17 Shared expenses/settlement           |     8 | money              | Manual groups/allocations                          |
| FT-18 Bills/subscriptions/contracts        |     6 | money              | Entered contracts and sample recurrence            |
| FT-19 Shopping/receipts/purchases          |     9 | money              | Manual lines + fixture extractor                   |
| FT-20 Income/payslips/benefits             |     6 | money              | Manual/CSV statement components                    |
| FT-21 Freelancer invoices                  |     9 | money              | Manual invoices/claims                             |
| FT-22 Refunds/disputes/warranties/claims   |     9 | money              | Manual cases/documents                             |
| FT-23 Document vault/action queue          |     6 | money/platform     | Local files + stub extraction                      |
| FT-24 Cash/gift cards/store credit/rewards |     9 | ledger/money       | Manual restricted-value accounts                   |
| FT-25 Utility/tariff intelligence          |     9 | money/analysis     | Manual meters + sample tariff                      |
| FT-26 Event/project money                  |     9 | money              | User projects over canonical records               |
| FT-27 Insurance/protection map             |     9 | money              | Manual policies/documents                          |
| FT-28 Family support/gifts/giving          |     8 | money              | Manual transfers/claims                            |
| FT-29 Cards/BNPL/overdraft/installments    |     6 | ledger/money       | User-entered terms/statements                      |
| FT-30 Tax/government calendar              |    10 | money/analysis     | Versioned sample/manual policy pack                |
| FT-31 Multi-account cash/funding           |     2 | ledger             | Manual/CSV accounts and postings                   |
| FT-32 Physical-asset lifecycle/TCO         |    11 | assets             | Manual meters/costs/value + synthetic asset series |

## Testing strategy

### Test layers

1. **Pure domain/calculation tests:** no Spring and no database for posting invariants, cost basis, TWR/XIRR, scenario strategies, debt/deposit policies, allocation, claims, recurrence and TCO.
2. **PostgreSQL integration tests:** Testcontainers for migrations, constraints, JPA mappings, SQL/`JdbcClient` reads, transactions, locks, idempotency and rebuild jobs.
3. **HTTP/security tests:** authorization/ownership, validation, problem codes, decimal/time serialization, pagination, idempotency and optimistic conflicts.
4. **Provider contract tests:** saved fixtures only; tests never call the internet. A live-provider smoke test is opt-in and not a merge gate.
5. **Small hand-worked golden fixtures:** independently auditable examples prove cost basis, posting, FX attribution, valuation, TWR/XIRR, deposit/debt and other calculation mathematics.
6. **Golden end-to-end scenarios:** the deterministic household dataset validates integration across month close, investment decomposition, Decision Replay, claims, purchase recovery and vehicle TCO.

### Required global invariants

- Retrying an idempotent command creates one economic fact.
- Backdated insertion plus rebuild equals initial insertion in economic order.
- Reversal plus replay removes the original effect without deleting its audit history.
- Transfers do not become income/spending/performance except declared fees/FX.
- A card purchase and later payment record spending once.
- Borrowed/lent principal is not income/consumption.
- Native currencies are never arithmetically added without conversion context.
- No calculation uses an observation published after the calculation's decision/as-of policy permits.
- Manual/synthetic/provider sources remain distinguishable in every derived result.
- Actual, obligation, plan and scenario states cannot alter each other's balances.
- Owner/household authorization applies to details and aggregates.
- Identical facts, observations, policies and calculation version produce identical output.

### CI gates by maturity

- From Stage 1: Java 25 build, formatting, unit tests, empty migration and context startup.
- From Stage 2: ledger golden/concurrency/idempotency tests.
- From Stage 3: provider fixture and no-network tests.
- From Stage 4: reconciliation/calculation golden tests.
- From Stage 6: monthly-close whole-money scenario.
- From Stage 8: household privacy tests.
- From Stage 11: full asset-TCO suite.
- Before mobile/public use: OpenAPI compatibility, export/delete/security and container smoke tests.

## First implementation backlog

Execute in this order; do not start feature-table expansion first.

1. Create a database-reset branch/tag and record that existing local data is disposable.
2. Remove/rotate tracked RSA keys and add a safe local key-generation/environment flow.
3. Fix/disable incomplete Google verification for development.
4. Pin/enforce Java 25 and explicit annotation processing; do not enable preview features.
5. Add Testcontainers PostgreSQL dependency and empty-database smoke test.
6. Finalize [accounting-contract.md](accounting-contract.md): posting signs, time/economic ordering, fees/tax, cost basis, FX selection, balances, valuation/performance and projection states.
7. Decide UUID generation and write shared exact decimal/time serialization tests.
8. Replace the migration chain with reviewed `V1__foundation.sql` and `V2__reference.sql` seeds.
9. Enable Flyway by default; remove baseline-on-migrate, empty `schema.sql`, Spring Batch initialization and legacy batch tables.
10. Create new minimal mapping-only entities for the clean baseline and pass Hibernate validation; do not copy legacy entity DDL annotations.
11. Add `FinancialAccount`, cash pocket, tracking/negative policies and owner constraints.
12. Add immutable activity, money/security postings, client idempotency and reversal relation.
13. Implement deposit/withdrawal and owned transfer with exact projection/reconciliation tests.
14. Implement bank-to-broker funding and fee-aware buy/sell cash/security legs.
15. Add unique projection identity, deterministic ordering, locks/version and backdated rebuild.
16. Implement new portfolio/trade reads over the ledger projections and restore only required endpoint parity; the legacy `Position.buy/sell/undo` model is not brought into the rewrite.
17. Add CSV import batch/row preview, fingerprints and reconciliation.
18. Add observation source/series/revision/quality model and manual price/FX imports.
19. Add deterministic synthetic household loader with fixed clock/seed and clear dataset isolation.
20. Add one synthetic total-return instrument, FX series, deposit policy and CPI series.
21. Build current/historical valuation and coverage; then TWR/XIRR/decomposition fixtures.
22. Build calculation-run/scenario foundation and first Decision Replay vertical slice.
23. Validate the flagship with users before expanding everyday-money screens.
24. Continue Stages 6–12 in dependency order, updating [progress-report.md](progress-report.md) after every implementation session.

## Definition of done for each vertical slice

A backend feature is not complete because its tables and endpoint exist. It must have:

- a user outcome and state boundary consistent with the feature catalogue;
- an owner/household authorization rule;
- exact validation, idempotency and correction behavior;
- a migration plus empty-to-latest coverage;
- entity mappings comply with the minimal-JPA policy: the migration, not annotations, contains database constraints/indexes/defaults/DDL;
- manual/file input when external data would otherwise be required;
- deterministic synthetic fixtures and no-network tests;
- source/quality/coverage fields in derived output;
- pure business tests and PostgreSQL integration tests proportional to risk;
- `/api/v1` request/response/error/OpenAPI contract;
- rebuild/invalidation behavior for backdated facts or revised observations;
- export/delete/retention handling for its records;
- an update to the progress report and relevant acceptance checklist.

## Explicit non-goals during this plan

- Microservices or independent module deployment.
- Real trade execution, payment initiation, debt collection, tax filing, insurance brokerage, or regulated personalized recommendations.
- Near-term Open Banking, bank/card synchronization, broker synchronization, payment-initiation infrastructure, or speculative `AccountFeedSource`/connection-token/webhook frameworks.
- Training a proprietary market-prediction model.
- Hiding missing data behind synthetic/live-like values.
- Building all provider integrations before core calculations work manually.
- Enterprise ERP/fleet/accounting functionality.
- A generic plugin/event framework before two concrete integrations need it.
- Frontend refactoring except when an API compatibility decision must be documented; implementation scope is backend.

## Plan governance

This document is the master order. Detailed calculations and feature behavior remain in the linked review documents. When implementation reveals a better decision:

1. Change the decision here.
2. If it changes cross-cutting financial semantics, update [accounting-contract.md](accounting-contract.md) and the relevant hand-worked golden fixtures first.
3. Record the reason/date in [progress-report.md](progress-report.md).
4. Update affected feature acceptance criteria.
5. Do not preserve a known-wrong design merely because an earlier phase named it.

The immediate next action is Stage 0 followed by the fresh `V1`/Testcontainers vertical slice. No broader feature should be implemented on top of the supplied schema.
