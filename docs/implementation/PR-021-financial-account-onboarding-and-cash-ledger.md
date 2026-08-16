# PR-021 — Financial-account onboarding and immutable cash ledger

Status: **ACTIVE**

## Goal

Deliver the first usable financial-truth vertical slice. An authenticated user can create owner-scoped cash, brokerage, card, or loan accounts with an explicit zero or non-zero opening state; record idempotent deposits, withdrawals, and same-currency owned-account transfers; correct facts without destructive edits; and read native-currency balances with explicit historical coverage. Every balance comes from immutable postings, and concurrent/retried commands cannot duplicate or silently overspend.

## Sizing and boundary rationale

- Comparison baseline: accepted PR-018 (`d1eea9a`) added 381 production Java lines across 12 production files.
- Expected production surface: approximately 40–55 production Java/migration files and 2,300–3,300 gross production lines across the V3 ledger schema, financial value types, account/activity/posting mappings, idempotent transactional workflows, locking/projection SQL, owner-scoped read models, HTTP contracts, and error handling. Tests and documentation do not count.
- Combined behaviors: an account without an opening-state contract is financially ambiguous; an opening amount without immutable postings creates a second balance truth; postings without idempotency, reversals, locking, and balance reads are unsafe. These pieces therefore form one coherent capability rather than mechanical per-layer PRs.
- Review boundary: this PR covers only owner-scoped, single-native-currency account onboarding and the minimal manual cash ledger. Reconciliation, imports, pending settlement, investments, categories/spending, household sharing, FX, providers, and background execution are independent capabilities.
- This is domain implementation, not generic infrastructure. No scheduler, workflow engine, rules engine, money library, event bus, or abstraction framework is introduced. JDK, Spring Boot, Hibernate, Jackson, PostgreSQL, and Flyway cover the technical plumbing; repository code owns only the product-specific accounting semantics.

## Source documents

- `docs/review/backend-master-plan.md` — architecture, R3, Stage 2, and PR execution model.
- `docs/review/accounting-contract.md` — §§2–8, 13, 17–19, and 21.
- `docs/review/cash-accounts-and-funding-design.md` — account kinds, cash pockets, posting signs, negative policies, funding, concurrency, balances, and the recommended first slice.
- `docs/engineering/coding-standards.md` — dependency/reuse policy, transaction ownership, mapping, API, security, and PostgreSQL test standards.

## Starting state

- PR-020 is accepted in commit `3f45a8c`.
- Flyway owns V1 identity/platform and V2 reference-catalog schema; Hibernate validates mappings and Open Session in View is off.
- Authenticated identity and owner-scoped security behavior are established. Active TRY/USD/EUR/GBP currencies are available by stable code.
- No `ledger` table, financial account, opening-state fact, money posting, idempotency record, balance projection, or financial endpoint exists.
- The existing `platform.job` table/entity/repository are unused scaffolding. This PR neither implements a custom worker nor adds another job dependency; asynchronous execution will be selected only with its first concrete workload after a build-versus-buy review.

## Scope

1. Add one forward-only `V3__financial_account_cash_ledger.sql` migration containing only the account/cash-ledger structures required by this slice.
2. Implement canonical exact decimal parsing/serialization for financial amounts, stable account/activity/posting/policy/tracking/source/coverage enums, and account capability rules.
3. Implement owner-scoped financial-account creation with either an explicit full-ledger opening assertion (including exact zero) or an explicitly untracked-cash holdings-only brokerage mode.
4. Persist opening state as an immutable `OPENING_BALANCE` activity and posting, not as a mutable account balance or income/spending event.
5. Implement account detail/list, metadata update, policy update, and archive. Kind, tracking mode, native currency, owner, and historical boundary remain immutable after creation.
6. Implement idempotent manual `CASH_DEPOSIT` and `CASH_WITHDRAWAL` activities with one native-currency posting and explicit current-action versus historical-fact policy behavior.
7. Implement idempotent same-currency `OWNED_TRANSFER` with one source and one destination posting in one transaction. It is never income or spending.
8. Implement advisory transfer preview using current projection versions, exact before/after balances, and policy results. Commit always re-reads and revalidates locked state; preview is never authorization.
9. Implement append-only reversal for deposit, withdrawal, and transfer facts, plus atomic opening-state amount correction through reversal and replacement at the unchanged coverage boundary.
10. Maintain a synchronous rebuildable current-balance projection while retaining immutable postings as authority. Provide current and historical-as-of balance reads with explicit coverage.
11. Add PostgreSQL locking, idempotency, constraint, mapping, transaction, ownership, HTTP/security, and hand-worked accounting proof.

## Explicit non-goals

- No custom job/scheduler/batch framework and no use of the unused `platform.job` runtime scaffold.
- No Spring Batch, db-scheduler, Quartz, JobRunr, broker, outbox, event sourcing framework, or generic workflow engine. There is no asynchronous workload in this PR.
- No multi-currency pocket creation, FX conversion, invisible conversion, reporting-currency valuation, or provider balance.
- No securities, portfolios, positions, trades, lots, cost basis, dividends, or brokerage imports. A holdings-only brokerage account only states that cash is untracked.
- No pending/authorized/unsettled postings, reservations, commitments, statement reconciliation, unexplained adjustment, file import, or provider synchronization.
- No spending categories, income classification, bills, cards purchases/payments, loan schedules/payments, claims, shared expenses, household access, or planning/scenario behavior.
- No destructive activity update/delete/undo. No account hard delete once financial history exists.
- No frontend work and no compatibility endpoint for the legacy React API.
- No generic money/accounting library. The required sign, coverage, idempotency, and correction rules are product contracts rather than commodity arithmetic.

## Database changes

Migration:

- `V3__financial_account_cash_ledger.sql`

Create exactly these `ledger` tables:

1. `financial_account`
2. `account_cash_pocket`
3. `activity`
4. `money_posting`
5. `idempotency_record`
6. `account_balance_projection`

Do not create `security_posting`, `activity_split`, `reconciliation`, import, spending, investment, household, observation, or job tables in this migration.

### `ledger.financial_account`

Required columns include:

- UUID primary key and `owner_user_account_id` FK;
- trimmed display name plus normalized name;
- textual `account_kind` and `tracking_mode`, plus nullable `negative_balance_policy`;
- native/base `currency_code` FK to `reference.currency`;
- IANA `time_zone` text;
- nullable exact `authorized_limit` used only by the corresponding policy/kind;
- nullable `current_opening_activity_id` FK added after `activity` exists;
- `archived_at`, `created_at`, `updated_at`, and primitive optimistic `version`.

Migration-owned rules:

- initial kinds are `CASH_CURRENT`, `CASH_SAVINGS`, `CASH_WALLET`, `BROKERAGE`, `CREDIT_CARD`, and `LOAN`;
- tracking modes are `FULL_LEDGER` and `HOLDINGS_ONLY`;
- only `BROKERAGE` may be `HOLDINGS_ONLY` in this slice;
- holdings-only cash is untracked and therefore has no cash pocket/opening activity;
- full-ledger asset accounts require a negative-balance policy; holdings-only brokerage and liability accounts prohibit one in this slice;
- `AUTHORIZED_LIMIT` requires a strictly positive limit and is initially supported only by `CASH_CURRENT`; every other kind/policy combination prohibits a limit;
- `CASH_SAVINGS`, `CASH_WALLET`, and full-ledger `BROKERAGE` support `HARD_FLOOR`, `SOFT_FLOOR`, or `TRACK_REALITY`; `CASH_CURRENT` additionally supports `AUTHORIZED_LIMIT`;
- `CREDIT_CARD` and `LOAN` are opening-state/read-only liability accounts in this slice, so their later credit/principal-limit policies are not misrepresented as asset negative-balance policy;
- active normalized names are unique per owner through a named partial unique index; archived names may be reused;
- owner/name list and owner/id detail/update access paths have query-driven indexes;
- user deletion follows the repository's accepted owner-data deletion policy.

### `ledger.account_cash_pocket`

- UUID primary key, owner ID, account ID, native currency, coverage status/from time, created/updated timestamps, and projection version support as required by the chosen mapping.
- A full-ledger account has exactly one pocket in its immutable account currency; the application transaction establishes this aggregate invariant.
- Named uniqueness on `(account_id, currency_code)`.
- Composite owner/account FKs prevent attaching another user's pocket.
- Coverage status is `KNOWN_FROM_OPENING` for full-ledger accounts and the opening effective time is mandatory.

### `ledger.activity`

- UUID primary key, owner ID, stable client event ID, operation scope, non-negative command sequence, textual activity type and recording mode, `effective_at`, `recorded_at`, optional economic sequence, source kind, policy decision, bounded correction reason, and immutable reversal/supersession links.
- Activity types: `OPENING_BALANCE`, `CASH_DEPOSIT`, `CASH_WITHDRAWAL`, `OWNED_TRANSFER`, and `REVERSAL`.
- Source kind is `USER_ENTERED` only in this PR.
- Recording modes are `CURRENT_ACTION` and `HISTORICAL_FACT`; this provenance survives independently of the current account policy.
- Policy decisions are `NOT_APPLICABLE`, `ALLOWED`, `CONFIRMED_BREACH`, and `HISTORICAL_BREACH_RECORDED`; database checks constrain the decision shapes that each activity/mode may store.
- Named unique `(owner_user_account_id, operation_scope, client_event_id, command_sequence)` mirrors the idempotency namespace and is the final duplicate-activity barrier. Ordinary commands use sequence zero; the atomic opening-correction command uses deterministic distinct sequence values for its inverse and replacement activities.
- `reverses_activity_id` is unique and cannot self-reference; a reversal cannot itself be reversed in this slice.
- `supersedes_activity_id` is used only by corrected opening assertions.
- No update/delete path exists for posted activity facts.

### `ledger.money_posting`

- UUID primary key, owner ID, activity ID, account ID, cash-pocket ID, currency code, exact `numeric(38,18)` amount, textual posting role, and created timestamp.
- Posting roles: `OPENING`, `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_SOURCE`, `TRANSFER_DESTINATION`, and `REVERSAL`.
- Composite FKs enforce one owner across activity, account, and cash pocket.
- Pocket/account/currency consistency is protected by named composite keys/FKs rather than application convention alone.
- Zero is allowed only for the explicit zero-opening assertion and its reversal/replacement path; ordinary deposit/withdrawal/transfer inputs are strictly positive and produce non-zero signed postings.
- Posted rows are immutable. There is no mutable balance column here.

### `ledger.idempotency_record`

- Owner ID, operation scope, client request/event UUID, canonical request SHA-256, result resource kind/ID, a bounded canonical JSON result snapshot, creation timestamp, and primary/unique identity.
- Unique `(owner_user_account_id, operation_scope, client_request_id)`.
- Replays compare canonical semantic input, not raw JSON/key order/decimal scale.
- The result snapshot contains only the successful response fields needed to return the original semantic result; it is size-bounded, owner-scoped, never accepts arbitrary client JSON, and establishes the repository's tested JSONB write semantics.
- The idempotency row commits atomically with its account/activity/posting/projection result.

### `ledger.account_balance_projection`

- One row per cash pocket with owner/account/currency, exact ledger balance, an unambiguous last-applied `(recorded_at, activity_id)` watermark, `updated_at`, and primitive optimistic version.
- Named unique projection key and composite owner/pocket FK.
- It is a rebuildable current projection, never the financial source of truth.
- No direct seed is allowed. Account onboarding creates/rebuilds it through the same application workflow as normal data.

### Numeric and textual storage

- Money uses `numeric(38,18)` without database rounding.
- API request/response amounts are canonical decimal strings.
- Application enums persist as text with stable names; Flyway checks only this slice's accepted values.
- All constraints/indexes/FKs have deterministic names and are asserted in PostgreSQL migration tests.

## Application changes

Use one coarse `dev.canverse.stocks.ledger` capability with only the needed `domain`, `application`, `infrastructure`, `input`, `output`, `error`, and `web` packages.

### Domain and value types

- Add an exact financial decimal value type that parses plain decimal strings, rejects exponent/plus/grouping syntax, values outside `numeric(38,18)`, and forbidden signs without rounding, and emits one canonical `toPlainString` form with insignificant trailing zeros removed and every numeric zero rendered as `0`. Numerically equal accepted scales hash and serialize identically. Do not adopt a general money library or attach currency to every arithmetic operation through a speculative abstraction.
- Account-kind capability methods decide asset/liability presentation, cash funding eligibility, holdings-only eligibility, and supported policy combinations. Do not scatter `if kind == ...` across controllers/services.
- `FinancialAccount` owns metadata/policy/archive invariants and optimistic versioning. It never exposes a setter or mutable posting collection.
- `Activity` and `MoneyPosting` use factories for the five accepted activity shapes and exact signed posting rules. Once persisted, their economic fields have no mutator.
- Response factories own transformations from exact read-model records. JPA entities are never serialized.

### Account onboarding

- `CreateFinancialAccountRequest` contains `clientRequestId`, name, kind, tracking mode, currency, time zone, policy/limit, and conditional opening state.
- A full-ledger account requires an opening state with canonical amount and `effectiveAt`; exact zero is a real assertion.
- Opening and posted activity `effectiveAt` values cannot be later than the command's single injected-clock observation. Future schedules/plans are not posted money and remain out of scope.
- Asset opening amount uses the stored balance direction directly and may be negative because historical reality must remain representable. Liability opening amount is the amount owed and is normally non-negative; any accepted exceptional negative reality is explicit and warned, never silently normalized.
- A holdings-only brokerage prohibits opening cash and creates no cash pocket/projection. Its API reports cash coverage `UNTRACKED`.
- Validate active currency and IANA zone before writes.
- In one transaction create account, pocket, opening activity/posting, the exact current projection initialized from that posting (including exact zero), and the idempotency record. Exact replay returns the original resource; changed reuse returns `IDEMPOTENCY_CONFLICT` and changes nothing.

### Account metadata, policy, and archive

- Owner-scoped metadata update may change only display name and time zone with a required client version.
- A policy update may change only the future current-action policy/authorized limit with a client version; it does not reinterpret historical facts.
- Archive is explicit, idempotent, and versioned. Archived accounts remain readable and in historical balances but cannot accept a new deposit/withdrawal/transfer. Corrections/reversals remain available.
- Kind, tracking mode, currency, owner, and opening boundary are not generic metadata updates.

### Cash activities and policy behavior

- Request amounts for deposit, withdrawal, and transfer are positive canonical decimals. Services derive posting signs.
- Deposit: one positive destination posting.
- Withdrawal: one negative source posting.
- A standalone deposit or withdrawal is an unclassified external cash movement in this slice. It changes cash but is not automatically income or spending; later classification must link to the original activity rather than duplicate it.
- Same-currency owned transfer: equal-magnitude negative source and positive destination postings in one activity/transaction.
- Transfer source and destination must differ, be owner-visible, full-ledger, active, cash-funding capable, and use the same currency.
- `recordingMode=CURRENT_ACTION` applies current policy. `recordingMode=HISTORICAL_FACT` preserves historical reality and records a policy warning rather than falsifying/rejecting an observed negative balance.
- Both modes prohibit future effective times. A current action is admitted against the locked current projection; a historical fact may be backdated and is never presented as a scheduled action.
- `HARD_FLOOR` rejects a current action below zero.
- `SOFT_FLOOR` requires explicit confirmation and records `CONFIRMED_BREACH` when below zero.
- `AUTHORIZED_LIMIT` rejects a current asset action below `-authorizedLimit`; unused capacity is reported separately from cash/net worth.
- `TRACK_REALITY` records actual/historical state with an explicit warning and is never presented as unlimited safe spending.
- Liability accounts are opening-state/read-only in this slice; generic cash deposit/withdrawal/transfer endpoints reject them with a stable capability error.

### Idempotency and concurrency

- Every retryable financial mutation carries a principal-scoped client UUID and operation scope.
- Canonical request hashing includes normalized amounts, IDs, enum values, effective time, and relevant flags; it excludes transport-only representation differences.
- Exact replay returns the original result and does not re-run policy checks against later state. Conflicting reuse returns `IDEMPOTENCY_CONFLICT`.
- Account/projection rows are locked in ascending account UUID order before validating withdrawal/transfer/reversal effects. No database lock is held while parsing or doing external work; this PR has no network calls.
- Preview returns source/destination projection versions. Commit may accept expected versions for a fast stable `BALANCE_VERSION_CONFLICT`, but always locks and recomputes authoritative balances/policies.
- A transaction persists activity, postings, projection changes, and idempotency result atomically. Any failure rolls them all back.

### Reversal and opening correction

- Reversal creates a new `REVERSAL` activity and exact inverse postings linked to the original. It never updates/deletes the original.
- A deposit, withdrawal, or transfer can be reversed once. Cross-owner/missing targets are indistinguishable; a second different reversal is `ACTIVITY_ALREADY_REVERSED`.
- Reversal locks every affected account in deterministic order and updates projections atomically. Reversing an archived account's historical fact is permitted.
- Generic reversal does not reverse an opening state.
- Opening correction uses a dedicated command on the owning account. It retains the original coverage boundary, creates an inverse reversal of the current opening assertion plus a replacement `OPENING_BALANCE`, links supersession, updates the current-opening pointer, and adjusts/rebuilds the projection in one idempotent transaction.
- Opening correction is historical restatement and is not rejected merely because the corrected truth would have caused a later current-action policy warning or rejection. It does not retroactively rewrite those later activities or their recorded decisions; the resulting current breach is exposed explicitly.

### Balance and history reads

- Current balance reads use the projection and expose `projectionStatus=CURRENT`, watermark, and native currency.
- Historical `asOf` reads sum immutable postings with `effective_at <= asOf`; they do not trust the current projection.
- A balance response distinguishes at least `ledgerBalance`, `clearedBalance`, `cashHeld`, `liabilityOutstanding`, `overdraftUsed`, and `creditAvailable` when meaningful. Because this slice has only immediately posted/cleared facts, `ledgerBalance == clearedBalance`; do not add fake pending/reserved/committed values.
- Negative asset balance contributes zero positive cash plus a separate overdraft/liability amount. Credit/authorized limits never enter assets or net worth.
- Every response includes requested/actual as-of time, native currency, coverage status/from time, source kind, and projection status/watermark where applicable.
- Account and activity lists use owner-filtered keyset pagination with canonical JSON Base64url cursors. Reuse the established cursor contract/pattern; do not introduce a generic pagination framework or silently copy the legacy pipe cursor.

## API contract

All routes require the existing bearer authentication and derive owner identity server-side.

### Accounts

- `POST /api/v1/accounts`
- `GET /api/v1/accounts?includeArchived=&limit=&cursor=`
- `GET /api/v1/accounts/{accountId}`
- `PUT /api/v1/accounts/{accountId}` — name/time-zone metadata only, expected version required
- `PUT /api/v1/accounts/{accountId}/policy` — future-action policy only, expected version required
- `POST /api/v1/accounts/{accountId}/archive`
- `GET /api/v1/accounts/{accountId}/balance?asOf=`
- `PUT /api/v1/accounts/{accountId}/opening-state` — immutable correction workflow, not an in-place update

### Activities and transfers

- `POST /api/v1/accounts/{accountId}/activities` — deposit or withdrawal
- `GET /api/v1/activities?accountId=&limit=&cursor=`
- `GET /api/v1/activities/{activityId}`
- `POST /api/v1/activities/{activityId}/reversals`
- `POST /api/v1/transfers/previews`
- `POST /api/v1/transfers`

Success responses containing balances or financial mutations use `Cache-Control: no-store`/`Pragma: no-cache`. Creation responses include stable resource identity/location; exact idempotent replay returns the original semantic result without creating another resource.

### Stable ledger error codes

- `ACCOUNT_NOT_FOUND`
- `ACCOUNT_NAME_CONFLICT`
- `ACCOUNT_VERSION_CONFLICT`
- `BALANCE_VERSION_CONFLICT`
- `ACCOUNT_ARCHIVED`
- `ACCOUNT_ACTION_NOT_SUPPORTED`
- `ACCOUNT_CURRENCY_UNSUPPORTED`
- `ACCOUNT_LIMIT_EXCEEDED`
- `INSUFFICIENT_FUNDS`
- `POLICY_BREACH_CONFIRMATION_REQUIRED`
- `IDEMPOTENCY_CONFLICT`
- `ACTIVITY_NOT_FOUND`
- `ACTIVITY_ALREADY_REVERSED`
- `OPENING_STATE_CONFLICT`

Validation errors continue to use `VALIDATION_FAILED`. Cross-owner IDs return the same not-found code as unknown IDs. Problem responses never expose balances/accounts outside the caller's scope.

## Financial invariants

- Apply `accounting-contract.md` directly for decimal, time, opening-state, immutability, signs, balances, coverage, idempotency, and concurrency semantics; this specification does not redefine them.
- Opening state is an assertion at the coverage boundary, not income, spending, contribution, performance, or a fabricated prior transaction.
- Standalone cash deposits and withdrawals are not automatically classified as income or spending; this PR has no income/spending projection from which to invent that meaning.
- Money-posting amount direction is the represented account balance direction: positive increases cash owned or liability owed; negative decreases it.
- A transfer's two postings have equal magnitude/opposite cash effect and do not create income/spending/net-worth change.
- The projection equals the sum of every committed posting after all commands; reversal postings provide the offset. Reversal/supersession links classify correction history but never make original postings disappear from replay.
- Current-action policies control admission but do not delete or falsify historical reality.
- Financial facts are append-only; correction is reversal/supersession.
- Same-instant order uses an explicit economic sequence only when material. Stable ID tie-breaking is allowed only for commutative cash additions covered by the accounting contract.
- No balance, limit, or amount arithmetic uses `double`, `float`, scale-sensitive equality, or implicit rounding.

## Required tests

### Pure/domain

- Canonical financial decimal parsing/serialization: zero, negative, 38/18 boundaries, alternate scales, exponent notation, overflow, forbidden sign, and no rounding.
- Exact Jackson string representation for all public financial decimals and stable enum names/unknown rejection.
- Account-kind/tracking/policy capability matrix, including holdings-only brokerage and liability restrictions.
- Opening, deposit, withdrawal, transfer, reversal, and corrected-opening posting equations using hand-worked values.
- Negative-policy boundary equality for hard, soft, authorized-limit, and track-reality paths.
- Asset/liability/cash/overdraft/credit presentation with credit excluded from wealth.
- Canonical idempotency hash equality across harmless representation changes and inequality across semantic changes.
- Cursor canonicalization/filter binding and malformed/unknown-version rejection.

### PostgreSQL/Testcontainers

- Empty-to-V3 and V2-to-V3 migration, exact six-table inventory, Hibernate validation, named constraints/indexes/FKs, and no unrelated tables.
- Numeric precision/scale, enum checks, normalized-name uniqueness, policy combinations, tracking/kind constraints, composite owner/account/pocket/currency integrity, reversal uniqueness, idempotency uniqueness, and bounded canonical JSONB result snapshots.
- Atomic account onboarding for non-zero, exact-zero, negative asset reality, liability opening, and holdings-only untracked cash.
- Active/inactive/unknown currency, invalid time-zone, and future-effective-time behavior with no partial writes.
- Exact and conflicting sequential/concurrent idempotency replay for account, activity, transfer, reversal, and opening correction.
- Coordinated concurrent withdrawals at the hard-floor/authorized-limit boundary prove a serially valid winner set and no overspend/lost update.
- Coordinated opposite-direction transfers prove deterministic lock ordering and no deadlock.
- Transaction rollback on posting/projection/idempotency failure.
- Reversal and opening correction retain original facts, replay every original/inverse/replacement posting, rebuild exact balances, and preserve corrected historical truth even when current policy is breached.
- Current projection equals authoritative posting sum after normal, backdated, reversed, and corrected facts.
- Historical as-of/coverage equality boundaries and no activity before coverage falsely implied.
- Owner deletion behavior and cross-owner SQL isolation.

### HTTP/security

- Real bearer filter for every route; missing/malformed/expired/revoked credentials retain the existing exact errors and create no servlet session.
- Exact create/list/detail/update/policy/archive/balance/activity/transfer/preview/reversal/opening-correction JSON contracts and no-store headers.
- Financial decimals are JSON strings, never floating JSON numbers.
- Cross-owner account/activity targets are indistinguishable from unknown; aggregate/list queries never leak another owner.
- Malformed enums/amounts/timestamps/cursors, missing fields, inactive currency, version conflicts, policy errors, archived accounts, idempotency conflicts, and reversal conflicts map to exact stable codes.
- Idempotent replay returns the original resource/balance result and row counts prove no duplicate fact.
- No legacy frontend route is made public or added as compatibility scope.

## Acceptance criteria

1. Flyway migrates fresh PostgreSQL from empty through V3 and upgrades a V2 database; exactly the six specified ledger tables are added and Hibernate validates them.
2. An authenticated owner can create a full-ledger account with explicit zero/non-zero opening state or a holdings-only brokerage with cash explicitly `UNTRACKED`.
3. Opening state is represented by immutable activity/posting facts with coverage metadata and is absent from income/spending/performance semantics.
4. Account metadata/policy/archive changes enforce ownership and optimistic versioning without changing immutable identity or financial facts.
5. Deposit, withdrawal, and same-currency owned transfer commands persist exact signed postings and update projections atomically.
6. Transfer postings are equal/opposite and neutral to aggregate net worth/income/spending; no invisible FX occurs.
7. Hard/soft/authorized/track-reality behaviors meet exact boundaries, preserve historical truth, and expose safe warnings/conflicts.
8. Exact retries return one original result; conflicting key reuse is rejected; coordinated concurrent retries create one fact.
9. Concurrent spending and transfers lock affected accounts deterministically and cannot silently overspend, lose updates, or deadlock.
10. Reversal and opening correction append linked facts, never update/delete originals, and restore/restate exact projections.
11. Current and historical balance reads reconcile to authoritative postings and expose native currency, as-of, coverage, projection, cash/liability/overdraft/credit distinctions.
12. Owner scoping is enforced in SQL/services and real HTTP security tests; cross-owner and unknown identifiers are indistinguishable.
13. No custom asynchronous infrastructure, library dependency, provider/network call, frontend change, later financial capability, or generic framework is added.
14. The focused pure/PostgreSQL/HTTP/security gate, complete suite, Spotless, and Maven `verify` pass with no skipped required tests.
15. Completion records report actual production sizing, deviations, database version, tests, and any build-versus-buy decision; no later PR is drafted or activated.

## Documentation completion

Before this implementation unit is considered complete:

- fill in this specification's Completion Record;
- update `docs/implementation/STATE.md` with V3 tables, implemented account/ledger behavior, financial decisions, test totals, and deferred work;
- update `docs/review/progress-report.md` with PR-021 implementation/review status;
- update `docs/review/accounting-contract.md` first if implementation discovers a genuinely cross-cutting semantic change;
- keep `CURRENT.md` pointing to PR-021 through implementation and review.

## Verification commands

```bash
./mvnw "-Dtest=LedgerValueObjectTest,FinancialAccountMigrationTest,FinancialAccountMappingTest,FinancialAccountServiceTest,CashActivityServiceTest,CashLedgerConcurrencyTest,FinancialAccountHttpTest,CashActivityHttpTest,ApiBearerSecurityHttpTest" test
./mvnw test
./mvnw verify
git status --short
git diff --check
```

The focused class names may be adjusted to the final idiomatic test split, but every required proof category must remain and the completion record must list the exact command.

## Completion record

Fill this before marking the PR complete.

### Implemented

- PR-021 financial-account and ledger capability: not implemented; this specification remains active.
- Separate current-worktree backend standardization cleanup: implemented against the accepted PR-020 identity/reference/platform baseline. It does not add V3 tables, ledger behavior, routes, or financial semantics, and it does not satisfy any PR-021 acceptance criterion.
- The cleanup records controller-only validation, standard Spring Security JWT validators plus lexical compatibility checks, Boot-managed Micrometer W3C tracing with the existing UUID correlation contract, centralized database-constraint/optimistic-lock handling, application-owned search criteria, and current model/SQL ownership conventions in the authoritative standards/state/progress documents.

### Deviations from specification

- None for PR-021; the active financial specification remains unchanged. The requested cross-cutting cleanup is explicitly tracked as out-of-scope working-tree work rather than folded into the financial capability.

### New decisions

- No new financial decisions. The cleanup uses one Boot-managed Micrometer OpenTelemetry bridge without exporters, keeps W3C native trace IDs separate from the UUID `X-Trace-Id` compatibility value, and keeps the small known constraint map in one static registry.

### Tests executed

- PR-021 verification: not executed; specification only.
- Standardization focused gate: `./mvnw -q "-Dtest=LocalAccessTokenDecoderTest,GlobalExceptionHandlerIntegrationTest,RequestTraceFilterTest,ReferenceCatalogQueryTest,LocalAccountRegistrationServiceTest,ManualInstrumentServiceTest,MicrometerTracingHttpTest" test` (passed).
- Standardization complete suite: `./mvnw -q "-Dlogging.level.root=ERROR" test` (262 tests, 0 failures, 0 errors, 0 skipped); `./mvnw -q "-Dlogging.level.root=ERROR" verify` (passed); `./mvnw -q spotless:check` (passed).

### Follow-up work

- Statement reconciliation/adjustments, pending/settlement states, file import, multi-currency/FX, investing/funding integration, and richer account kinds remain separate capabilities.
- Select a maintained async/batch library only alongside the first concrete workload; do not revive the retired custom-worker design.
