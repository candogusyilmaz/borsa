# Backend transformation progress report

Report date: 2026-08-07

Scope: Spring Boot backend, PostgreSQL dump, database migration strategy, modular-monolith design, offline/fake data approach, and implementation readiness. React was not reviewed or changed in this update.

## At a glance

| Area | Status | Evidence/result |
|---|---|---|
| Existing backend/dump discovery | Complete | Current Java/resources, migrations, configuration and supplied PostgreSQL schema dump inventoried |
| Backend correctness/security audit | Complete as design review | [backend-audit.md](backend-audit.md) |
| Business/analytics target design | Complete as design review | [business-logic-and-analytics-design.md](business-logic-and-analytics-design.md) |
| Feature catalogue | Complete as plan | 32 features in [implementable-features.md](implementable-features.md) |
| Cash/account/funding design | Complete as plan | [cash-accounts-and-funding-design.md](cash-accounts-and-funding-design.md) |
| Physical-asset/TCO design | Complete as plan | [real-asset-lifecycle-tco-design.md](real-asset-lifecycle-tco-design.md) |
| Consolidated backend scratch-rewrite plan | Complete and authoritative | [backend-master-plan.md](backend-master-plan.md), including exact R0–R16 implementation increments and supersession rules |
| Accounting contract | Initial implementation contract complete | [accounting-contract.md](accounting-contract.md): shared posting/time/fee/cost-basis/FX/balance/valuation/performance/projection semantics |
| New target database | Created by user, not yet initialized/verified by this review | PostgreSQL database name `extreme_accounting` |
| Fresh database baseline | Not started | Current active migrations remain `V2`–`V14`; no replacement `V1` exists |
| Modular-monolith package refactor | Not started | Current global layer packages remain unchanged |
| Ledger/account implementation | Not started | Current model remains position/BUY/SELL-centric |
| Offline/synthetic data framework | Not started | Design exists; no dataset/source abstraction or loader yet |
| Feature implementation beyond current tracker | Not started | Planned in dependency stages; no new production code in review work |
| Automated backend coverage | Critical gap | One `contextLoads` test only |

Overall status: **discovery/design and implementation-contract harmonization are complete enough to begin implementation; production-code transformation is 0% started.**

## Inputs analyzed

### Repository database dump

- Repository path: [db-dump.sql](db-dump.sql)
- SHA-256: `FABDA56B6737FA710A37436FFA731BDFB8852EB5275BDF5000C981C1FD2AE8BD`
- Dumped from/by PostgreSQL 15.1.
- Approximately 47.1 KB.
- 28 table declarations: `account` 5, `instrument` 6, `portfolio` 8, `public` 9.
- No `COPY` or `INSERT INTO` statements: the supplied file contains schema, not application/reference/user data.
- The dump has clean/drop statements and is useful for inventory, but it is not accepted as the future baseline. The new clean rewrite targets the separately created `extreme_accounting` database.

### Repository backend

Inventory at report time:

- 127 Java source files under `src/main/java`;
- 24 JPA entity classes;
- 12 REST controllers;
- 13 Spring service classes;
- approximately 15 Spring Data repository/MyBatis mapper types;
- one Java test, `ServerTests.contextLoads`;
- 13 Flyway scripts, versions `V2` through `V14`;
- one Spring Boot/Maven module;
- legacy backend currently declares Java 21 in Maven;
- legacy backend currently uses Spring Boot 3.5.3, PostgreSQL, JPA/Hibernate, MyBatis, Flyway and Spring Security.

These are observations about the code being replaced, not the rewrite target.

Current coarse code areas are `config`, `domain`, `integration`, `repository`, `rest`, `security`, and `service`, with nested account/instrument/portfolio concerns.

## Confirmed strengths

- A single Spring Boot deployable is already the right physical architecture.
- PostgreSQL and Flyway are appropriate for the target accounting/data model.
- `BigDecimal` is used for trades, positions, rates, and prices.
- JPA entities have owner relationships and several repositories already filter by principal/user.
- Controllers are generally thinner than services.
- Instrument/market subtype concepts can evolve into the target reference model.
- Spring's JDBC stack is available for concise complex read models; the scratch plan removes the extra MyBatis/QueryDSL layer unless later evidence justifies it.
- OpenAPI generation and the existing React API-client approach can support a later stable `/api/v1` contract.

## Confirmed critical gaps

### Database/bootstrap

- There is no `V1`; `V2` begins by altering tables that are absent from the repository migration history.
- Flyway is disabled in default development configuration and enabled only in the production profile.
- `baseline-on-migrate: true` can hide an unowned schema instead of proving reconstruction.
- The dump includes six legacy Spring Batch tables even though no corresponding backend batch job was found.
- Spring Batch tables/config exist despite no corresponding backend batch-job implementation being part of the reviewed code.
- `schema.sql` contains only a semicolon and should not participate in initialization.
- Stable seed data depends on legacy state and hardcoded IDs, including BIST market ID `3`.

### Financial correctness

- Commission reaches requests but `Transaction.buy/sell` stores `BigDecimal.ZERO`; `Position.buy/sell` excludes it from totals.
- A buy/sell mutates the current position immediately; there is no canonical cash account or funding source.
- Backdated transactions do not rebuild in economic order.
- Destructive `undo` removes the latest list entries instead of creating a correction/reversal fact.
- `BigDecimal.equals` is used in zero/full-close paths where scale-sensitive equality can be wrong.
- There is no idempotency key or concurrency-safe unique projection identity.
- Position, transaction, history, and snapshot records duplicate/derive overlapping state without a reliable rebuild contract.
- Card, bank, debt, bill, spending, income, claim, and transfer semantics do not yet exist.

### Valuation/data

- One mutable `currencies.exchange_rate` and `convert_currency` function cannot support historical FX.
- `instrument_snapshots` stores latest state only and no provider/revision/quality/licence history.
- Current data integrations depend on external Forex, BIST/Sabah, and Gemini paths and have no common manual/synthetic normalized source.
- Market, snapshot, and position currency columns lack complete foreign-key/reference enforcement.
- Missing/stale/synthetic/manual coverage is not a first-class API result.

### Schema/entity drift

- The dump makes `positions.instrument_id` nullable while JPA declares the association mandatory.
- Position total precision differs between dump and entity.
- Timestamps mix `timestamp without time zone` and `timestamptz` even though Java uses `Instant`.
- Daily snapshot quantity is integer while positions support decimal quantity.
- Generated daily totals round to scale 2 and percentage expressions can yield null despite `NOT NULL` declarations.
- Duplicate indexes exist for portfolio/user and position/portfolio.
- Transaction notes/metadata state differs from the apparent migration intention.
- A PostgreSQL `tag_type` remains despite its table being removed.

### Security/operations

- RSA private/public key material is tracked in the repository even though `.gitignore` now lists it.
- Google token verification does not visibly enforce the application's exact client audience in the reviewed flow.
- Default local database password and provider placeholder keys remain in configuration; acceptable only for disposable local development, not a deployment contract.
- Docker packaging skips tests.
- The MyBatis `type-handlers-package` points to `dev.canverse.finances.infrastructure.mybatis`, while code uses `dev.canverse.stocks...`.
- README claims about a live demo/deployment conflict with the user's current statement that the project is not deployed; this should be corrected during Stage 0.

## Decisions recorded in this update

| Decision | State |
|---|---|
| Development database can be dropped/recreated | Accepted from user request |
| New rewrite database is `extreme_accounting` | Accepted from user update |
| Use one deployable modular monolith | Accepted |
| Direct cross-module references/coupling are allowed | Accepted |
| Avoid excessive files/layers/interfaces | Accepted |
| Rewrite is a clean cut, not an in-place legacy-data migration | Accepted from latest request |
| Flyway owns all DDL; JPA contains mapping annotations only | Accepted from latest request |
| Indexes, keys/FKs, unique/check constraints, defaults/generated SQL and cascades stay out of entities | Accepted from latest request |
| Use JPA plus `JdbcClient`; omit MyBatis/QueryDSL unless later justified | Recommended for the minimal scratch rewrite |
| Replace legacy migration chain with clean baseline | Recommended and placed first in implementation plan |
| Use immutable activities plus rebuildable projections | Recommended and already detailed in prior design docs |
| Implement every feature with manual/import/synthetic data before relying on providers | Accepted and planned |
| Keep synthetic data clearly isolated/labeled | Required safety/trust rule |
| Manual entry and file import are permanent primary financial-data workflows | Accepted from latest product decision |
| Bank/Open Banking, card/broker synchronization and payment initiation are outside the near-term/R0–R16 roadmap | Accepted from latest product decision |
| Onboarding supports explicit opening state and historical-coverage boundaries | Accepted from latest product decision |
| Planned obligations may name intended funding accounts but actual payments are manually recorded/confirmed ledger facts | Accepted from latest product decision |
| Shared-expense claims retain originating-payment provenance and support partial/multi-claim settlement without income/spending double-counting | Accepted from latest product decision |
| Backend only for implementation planning | Accepted |
| Do not create microservices or separate Maven modules | Fixed plan decision |

## Decisions confirmed before implementation

The 2026-08-07 document harmonization establishes these implementation rules:

- Rewrite technology baseline: Java 25 + Spring Boot 4.1.0. Spring Framework/Spring Data/Hibernate/Jackson versions remain Boot-managed by default (Spring Framework 7.0.8, Spring Data JPA 4.1.0 and Hibernate ORM 7.4.1.Final in Boot 4.1.0); Jakarta Persistence 3.2 is the stable persistence contract.
- Stable Java 25 and Jakarta Persistence 3.2 features are encouraged when useful; Java preview/incubator features, Jakarta Persistence 4, Hibernate ORM 8 and snapshot/milestone/RC framework dependencies are excluded from normal feature PRs.
- [../engineering/coding-standards.md](../engineering/coding-standards.md) is the repository-wide coding standard. Root [AGENTS.md](../../AGENTS.md) is the agent entry point and directs coding agents to those standards plus the active PR spec automatically.
- R0–R16 remain roadmap increments, not PR sizes. Implementation is delivered through small human-reviewable specifications under `docs/implementation/`, with the current spec selected by `docs/implementation/CURRENT.md`.

- [backend-master-plan.md](backend-master-plan.md) is authoritative for implementation order; older in-place repair/backfill/shadow-mode instructions are historical analysis only.
- The rewrite is a clean replacement. Legacy application rows/IDs are not migration requirements because the application is undeployed and the target database is disposable.
- Cross-cutting financial semantics live in [accounting-contract.md](accounting-contract.md); a code change that changes those semantics updates the contract and small golden fixtures first.
- Demo financial data is loaded through normal application command services; synthetic observations use the normal observation-ingestion path. Demo code never directly seeds derived projections.
- Synthetic/manual providers are in-process implementations. Standalone mock HTTP servers are not part of the demo architecture.
- WireMock/equivalent is reserved for tests of real HTTP provider adapters; business/calculation tests remain no-network.
- The full household demo is an end-to-end/integration fixture. Small hand-worked fixtures remain the authoritative proof of calculation mathematics.
- Observation source selection is an explicit policy covering source priority, staleness, no-look-ahead and missing/fallback behavior.
- Rebuildable projections use `CURRENT`, `STALE`, `REBUILDING`, and `FAILED` semantics and surface consistency metadata.
- `/api/v1`, decimal strings, stable problem codes, owner authorization, idempotency and source/coverage metadata are invariants from the first relevant endpoint, not features postponed to R16.
- Manual account setup and file import are permanent primary workflows; the product must remain fully usable without live financial-account connections.
- Onboarding supports explicit opening balances/state and reports incomplete historical coverage rather than fabricating prior activity/performance.
- Planned card/bill payments may reference an intended funding account, but the app does not initiate payment; the user records/confirms the actual settlement and the ledger links it to the obligation.
- Claims/shared expenses retain provenance to the original payment/split and support partial, multi-payment and multi-claim settlement; repayments are not income and do not duplicate spending.
- Open Banking, bank/card synchronization, broker synchronization and payment initiation are explicitly deferred beyond R0–R16; no speculative connector framework is built.

## Implementation stage status

| Stage | Result | Status |
|---:|---|---|
| 0 | Risk containment and contract lock | Not started |
| 1 | Fresh Flyway baseline and Testcontainers harness | Not started |
| 2 | Financial accounts, immutable ledger and current trade cutover | Not started |
| 3 | Observation platform and deterministic synthetic dataset | Not started |
| 4 | Reconciled net worth and honest investment analytics | Not started |
| 5 | Decision Replay and localized comparison policies | Not started |
| 6 | Everyday spending/income/bills/cards/documents | Not started |
| 7 | Commitments, resilience, goals, irregular income and briefs | Not started |
| 8 | IOUs, shared expenses and selective household money | Not started |
| 9 | Purchases/recovery/utilities/projects/protection/freelancer flows | Not started |
| 10 | Versioned tax/government calendar policy engine | Not started |
| 11 | Physical-asset lifecycle and TCO | Not started |
| 12 | Stable mobile API and operational hardening | Not started |

### Scratch-rewrite increment status

| Increment | Implementation result | Status |
|---:|---|---|
| R0 | Preserve evidence and replace backend skeleton | Not started |
| R1 | Foundation, identity, auth, sessions and jobs | Not started |
| R2 | Canonical references and deterministic seeds | Not started |
| R3 | Accounts/ledger/funding/balances — FT-31 | Not started |
| R4 | Investing parity, funded trades and imports | Not started |
| R5 | Observation platform and synthetic universe | Not started |
| R6 | Timeline/net worth/investment truth — FT-01/02/11 | Not started |
| R7 | Decision Replay and comparison — FT-06/07/08/09/12 | Not started |
| R8 | Spending/recurrence/bills — FT-15/03/18 | Not started |
| R9 | Income/cards/debt/documents — FT-20/29/23 | Not started |
| R10 | Resilience/goals/irregular income/brief — FT-04/05/10/14 | Not started |
| R11 | Household/people/claims/shared money — FT-13/16/17/28 | Not started |
| R12 | Purchases/recovery/restricted value — FT-19/22/24 | Not started |
| R13 | Utilities/projects/protection/freelancer — FT-25/26/27/21 | Not started |
| R14 | Tax/government policy calendar — FT-30 | Not started |
| R15 | Physical-asset lifecycle/TCO — FT-32 | Not started |
| R16 | Stable API, export/delete, operations and optional providers | Not started |

## Documentation progress preserved

The review folder now contains:

- [README.md](README.md) — index and executive assessment;
- [backend-master-plan.md](backend-master-plan.md) — authoritative implementation sequence and supersession rules;
- [accounting-contract.md](accounting-contract.md) — authoritative shared financial semantics and golden-fixture contract;
- [progress-report.md](progress-report.md) — this status/checkpoint;
- [backend-audit.md](backend-audit.md) — code/schema/security findings;
- [business-logic-and-analytics-design.md](business-logic-and-analytics-design.md) — ledger/valuation/calculation target;
- [implementable-features.md](implementable-features.md) — FT-01 through FT-32;
- [cash-accounts-and-funding-design.md](cash-accounts-and-funding-design.md) — account/funding/negative-balance details;
- [real-asset-lifecycle-tco-design.md](real-asset-lifecycle-tco-design.md) — physical-asset/TCO details;
- [mobile-api-readiness.md](mobile-api-readiness.md) — future Expo-safe contract;
- [prioritized-roadmap.md](prioritized-roadmap.md) — original correctness-first roadmap retained as historical analysis; implementation steps that conflict with the scratch rewrite are superseded;
- [product-direction.md](product-direction.md) — product differentiation.
- [../engineering/coding-standards.md](../engineering/coding-standards.md) — Java 25/Spring Boot 4/JPA/Hibernate/Spring/database/API/test coding standards used by implementation agents.
- [../implementation/README.md](../implementation/README.md) — small-PR execution model, active-spec mechanism and PR specification rules.

These documents are complementary. The master plan is the order of execution; `accounting-contract.md` governs shared financial semantics; the feature/TCO/cash documents contain deeper acceptance behavior.

Root [AGENTS.md](../../AGENTS.md) is the automatic agent entry point. It tells compatible coding agents to load the repository coding standard and `docs/implementation/CURRENT.md` before implementation, so normal prompts do not need to repeat those instructions.

## Immediate next-session checklist

Start here and do not begin broader feature work first:

1. Review/finalize [accounting-contract.md](accounting-contract.md), including opening-state/coverage and planned-payment semantics, before writing financial tables.
2. Create the rewrite branch/tag and record its commit.
3. Confirm connectivity to the user-created empty `extreme_accounting` database without applying the legacy dump.
4. Execute R0 from the master plan: dependency/backend skeleton reset while leaving `src/main/web` unchanged.
5. Remove/rotate tracked RSA signing material and make local keys environment/generated.
6. Pin Spring Boot 4.1.0, enforce Java 25, keep Boot-managed dependency versions, disable preview features, and add Testcontainers PostgreSQL.
7. Write an empty-database migration/context integration test that currently fails because replacement `V1` does not exist.
8. Implement `V1__foundation.sql`, with every key/FK/index/check/default defined in SQL and only persistence mapping annotations in JPA.
9. Implement `V2__reference.sql` and stable reference seeds.
10. Begin R3 with `FinancialAccount`, cash pockets, immutable activity/postings and small hand-worked deposit/transfer/idempotency fixtures.

The first meaningful checkpoint is not “all tables created.” It is:

> A clean checkout can start on an empty PostgreSQL database, then post and replay a deposit, transfer, broker funding and fee-aware trade exactly once.

## Risks to monitor

| Risk | Current level | Next control |
|---|---|---|
| Building features on an unreconstructable schema | Critical | Fresh V1 + empty migration test |
| Incorrect financial results becoming harder to migrate | Critical | Ledger cutover before new money features |
| Tracked signing key reuse | Critical if ever shared/deployed | Rotate/remove immediately |
| Fake data mistaken for real data | High for future demo | Dataset isolation, synthetic provenance and command-path-only demo loader from Stage 3 |
| Scope expansion across 32 features | High | Enforce stage exit gates and one vertical slice at a time |
| Too many architectural files/interfaces | Medium | Follow minimal code pattern; `accounting-contract.md` is the only new cross-cutting contract file |
| No live data/provider access | Expected, not a blocker | Manual/CSV/synthetic implementations first |
| Jurisdiction rules becoming stale or misleading | High | Version/source/review/sample labels; suppress stale policies |
| Almost no automated tests | Critical | Testcontainers and golden fixtures before refactor |
| Untracked review documents being lost | High | Review and commit `docs/review/` with the implementation branch |

## Verification performed for this checkpoint

- Read the complete repository schema dump and extracted schemas, tables, types, function, constraints, indexes and foreign keys.
- Confirmed the repository dump contains no data statements and calculated its SHA-256.
- Read all active Flyway migrations `V2`–`V14` and relevant application/Flyway/Batch configuration.
- Compared dump columns with key current JPA entities for position, transaction, snapshots, instruments and market currencies.
- Counted the current backend/test/controller/service/repository inventory.
- Confirmed tracked certificate files, the current root `AGENTS.md`, and the current untracked documentation worktree state.
- Reused the earlier full backend review instead of re-reading the React application, consistent with backend-only scope.

No database was connected to, dropped, restored or initialized; the existence of `extreme_accounting` is accepted from the user's update. No migration was replaced, no dependencies were installed, and no Java/application behavior was changed during this checkpoint. Runtime tests were not rerun because this task requested a plan/progress report; the previous build verification remains documented in [README.md](README.md).

## Update protocol for future sessions

At the end of every implementation session, update this file with:

1. date and stage;
2. files/migrations changed;
3. tests run and exact result;
4. newly completed acceptance criteria;
5. discovered schema/business changes;
6. next three concrete tasks;
7. whether the worktree contains uncommitted/untracked progress.

Do not mark a stage complete until its exit gate in [backend-master-plan.md](backend-master-plan.md) is satisfied.
