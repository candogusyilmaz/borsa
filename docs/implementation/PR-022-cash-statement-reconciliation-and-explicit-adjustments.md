# PR-022 — Cash statement reconciliation and explicit adjustments

Status: **COMPLETED**

## Goal

Allow an authenticated owner to compare a full-ledger financial account with a manually entered statement boundary, commit immutable reconciliation evidence when the opening boundary is continuous, explicitly accept a closing difference as an unexplained adjustment, correct a prior reconciliation without destructive edits, and see when later backdated facts make the recorded result stale.

## Capability and review boundary

- Coherent capability: a computed ledger balance and an external statement are different truths. Comparing them, preserving the comparison, explicitly posting any accepted difference, and invalidating confidence after later historical changes form one independently useful reconciliation lifecycle.
- Combined behaviors: the V4 reconciliation record, adjustment activity/posting shape, exact comparison rules, idempotent preview/commit/correction workflows, account locking, staleness query, owner-scoped HTTP reads, balance metadata, and their tests are coupled. Splitting the adjustment from its reconciliation evidence would create unexplained money; splitting staleness/correction would make a committed reconciliation falsely durable after history changes.
- Excluded neighbor: statement-line or CSV import, transaction matching, duplicate detection, pending/settlement state, and provider synchronization remain a later ingestion capability. Fee/interest classification and investing are also independent later economic capabilities.
- Focused review: one reviewer can verify the statement equations, immutable evidence/adjustment links, concurrency and idempotency boundary, superseding correction, staleness derivation, ownership, and API contract without also reviewing parsers, provider data, pending-money semantics, or investment accounting.

## Source documents

- `docs/review/backend-master-plan.md` — R3 statement reconciliation, immutable ledger/correction rules, modular-monolith boundaries, and Stage 2 trust gate.
- `docs/review/accounting-contract.md` — sections 2–8, 13, 17–21 for exact decimals, time, actual/evidence separation, coverage, corrections, signed postings, balance labels, projection consistency, idempotency, provenance, and API metadata.
- `docs/review/cash-accounts-and-funding-design.md` — reconciliation/adjustment workflow, full-ledger account semantics, negative-reality rules, concurrency, and PostgreSQL proof.
- `docs/engineering/coding-standards.md` — Flyway ownership, capability packaging, transactions, persistence queries, API/error/security boundaries, and test layers.

## Starting state

- PR-021 is accepted in implementation commit `e08f2c2`. Repository commit `bf1852c` changes agent/documentation guidance but adds no later product capability.
- Flyway V3 owns the six accepted `ledger` tables. Full-ledger accounts have one native-currency cash pocket, explicit `KNOWN_FROM_OPENING` coverage, immutable activities/postings, and a current balance projection; holdings-only brokerage cash is `UNTRACKED`.
- Native current/as-of balances, deposits, withdrawals, same-currency transfers, reversal, opening correction, policy evaluation, idempotency snapshots, sorted locking, typed authenticated ownership, no-store responses, and keyset activity/account reads are implemented.
- All currently posted money is treated as ledger/cleared money; no pending or settlement state exists. No reconciliation or import table exists.
- The PR-021 focused 61-test gate, full 319-test suite, Maven `verify`, and Spotless check are recorded green with no unresolved `MUST FIX` finding.

## Scope

1. Add one forward-only `V4__cash_statement_reconciliation.sql` migration. Do not edit or renumber accepted V1–V3 migrations; the master plan's original broad V3 grouping was delivered incrementally, so actual Flyway history determines the next version.
2. Add immutable owner-scoped statement reconciliation records for full-ledger financial accounts in their native currency.
3. Implement a read-only preview that compares exact stated opening/closing balances with ledger balances at the requested inclusive instants and reports the period's posted movement.
4. Implement idempotent reconciliation commit. Exact opening continuity is mandatory. A zero closing difference records a balanced reconciliation; a non-zero difference requires explicit adjustment acceptance and a bounded reason.
5. Represent an accepted difference as one immutable `RECONCILIATION_ADJUSTMENT` historical activity with one signed `ADJUSTMENT` posting. The posting changes the ledger by exactly `statementClosingBalance - ledgerClosingBalanceBeforeAdjustment` and is never silently classified as income, spending, fee, interest, or investment performance.
6. Implement a dedicated idempotent correction workflow that supersedes the latest reconciliation, reverses its prior adjustment when present, recomputes from authoritative postings, and records the replacement reconciliation and any replacement adjustment atomically.
7. Derive `CURRENT`, `STALE`, or `SUPERSEDED` reconciliation lifecycle state without rewriting statement evidence. Any additional immutable posting later recorded for the account at or before the statement closing instant makes a non-superseded reconciliation stale; later effective activity does not.
8. Add owner-scoped reconciliation list/detail reads and expose a nullable last-reconciliation summary on account balance responses.
9. Add domain, PostgreSQL/Testcontainers, transaction/concurrency/idempotency, HTTP/security, and hand-worked reconciliation proof.

## Explicit non-goals

- No statement-line storage, CSV/OFX/QIF parser, import batch/row/issue model, duplicate fingerprinting, automatic matching, missing-transaction workflow, or document upload.
- No pending, authorized, reserved, committed, clearing, or settlement-state model. In this PR every existing money posting remains posted/cleared under the accepted PR-021 contract.
- No bank, card, broker, Open Banking, payment-initiation, or other provider connection.
- No generic reconciliation engine across securities, positions, claims, bills, households, or documents. This slice is owner-scoped native-balance reconciliation for existing full-ledger financial accounts.
- No standalone fee, interest, dividend, tax, spending, income, category, card-purchase/payment, loan-payment, trade, security-posting, position, cost-basis, FX, or multi-currency behavior.
- No mutation or deletion of statement evidence, original activities, postings, or completed reconciliation rows. No generic reversal of a reconciliation adjustment outside the dedicated correction workflow.
- No asynchronous scheduler, worker, queue, batch framework, outbox, or rebuild runtime. The bounded comparison and projection changes are synchronous.
- No frontend or legacy API compatibility work.

## Database changes

Migration:

- `V4__cash_statement_reconciliation.sql`

### `ledger.reconciliation`

Create one table with:

- UUID primary key, `owner_user_account_id`, `financial_account_id`, `cash_pocket_id`, and native `currency_code`;
- bounded nonblank `statement_reference`;
- `statement_opening_at` and `statement_closing_at` as `timestamptz` with opening strictly before closing;
- exact `numeric(38,18)` statement opening/closing balances, ledger opening balance, ledger closing balance before adjustment, period net posted amount, closing difference, and nullable adjustment amount;
- non-negative period posting count and total ledger posting count through the closing instant;
- textual resolution `BALANCED` or `ADJUSTED`;
- nullable unique `adjustment_activity_id` and nullable unique `supersedes_reconciliation_id`;
- source kind fixed to `USER_ENTERED` in this PR, nullable bounded adjustment reason, and `created_at`.

Migration-owned rules:

- composite owner/account/pocket/currency foreign keys reuse the accepted V3 aggregate identities and prevent cross-owner or cross-currency attachment;
- the account must have a pocket in the application workflow; holdings-only accounts therefore cannot be reconciled;
- self-supersession is prohibited and at most one direct replacement may supersede a reconciliation; a later correction may supersede the current replacement, forming an append-only chain;
- `BALANCED` requires exact zero difference, no adjustment amount/activity, and no adjustment reason;
- `ADJUSTED` requires a non-zero difference, an equal adjustment amount, an adjustment activity, and a 1–500 character reason;
- `ledgerClosingBalanceBeforeAdjustment + adjustmentAmount = statementClosingBalance` for adjusted rows, while the balanced shape requires the pre-adjustment ledger closing balance to equal the statement closing balance;
- `ledgerOpeningBalance + periodNetPostedAmount = ledgerClosingBalanceBeforeAdjustment` before a new adjustment is applied;
- period posting count/net amount describe the ledger before this reconciliation's new adjustment; the total through-close posting count is captured after that adjustment, when present, and is the immutable staleness baseline;
- reconciliation rows are immutable after insert. Supersession is derived from a replacement row rather than an update to the original;
- add a query-driven owner/account ordering index for `(owner_user_account_id, financial_account_id, statement_closing_at DESC, id DESC)`. Reuse the accepted activity/posting indexes for bounded staleness lookup unless query evidence requires one narrowly scoped additional index;
- all new constraints and indexes have deterministic names and are asserted by migration tests.

### Accepted table extensions

- Extend `ledger.activity.activity_type` to include `RECONCILIATION_ADJUSTMENT`.
- A reconciliation adjustment is `HISTORICAL_FACT`, uses source `USER_ENTERED`, carries the bounded adjustment reason, has policy decision `ALLOWED` or `HISTORICAL_BREACH_RECORDED`, and is neither a reversal nor an opening supersession.
- Extend `ledger.money_posting.posting_role` to include `ADJUSTMENT`. Its amount must be non-zero but may have either sign.
- Extend only the existing check constraints needed to admit this exact activity/posting shape. Do not weaken the accepted opening, deposit, withdrawal, transfer, reversal, zero-amount, owner, or idempotency constraints.
- Add the reconciliation-to-adjustment and reconciliation self-link foreign keys only after their referenced structures exist. Do not create import, statement-item, match, pending, document, security-posting, split, or projection tables.

## Application changes

Keep the work in the existing `dev.canverse.stocks.ledger` capability. Add only reconciliation-focused domain/application/infrastructure/web types; reuse the accepted amount parser, idempotency store, lock repository, account access, policy evaluator, response factories, cursor transport, and typed principal boundary. Do not introduce a generic workflow, evidence, mapper, or reconciliation framework.

### Exact comparison model

- Statement balances use the account's normalized stored-balance direction: positive asset value increases an asset balance and positive liability outstanding increases a liability balance. The server does not infer or flip signs from account kind.
- `statementOpeningAt` and `statementClosingAt` use the accepted inclusive as-of rule. Period movement includes postings with `effectiveAt > statementOpeningAt` and `effectiveAt <= statementClosingAt`.
- `openingDifference = statementOpeningBalance - ledgerOpeningBalance`.
- `periodNetPostedAmount = ledgerClosingBalanceBeforeAdjustment - ledgerOpeningBalance`.
- `closingDifference = statementClosingBalance - ledgerClosingBalanceBeforeAdjustment`.
- Every value uses the accepted canonical financial decimal parser and response serialization with no rounding.
- The opening instant cannot precede the account's coverage boundary, the closing instant cannot be later than the command's one observed clock value, and opening must be strictly before closing.
- Exact opening equality is required to commit. An opening mismatch remains visible in preview and commit fails with `RECONCILIATION_OPENING_MISMATCH`; the user must reconcile/correct the preceding boundary or later use a reviewed import workflow. This PR does not bury an earlier gap in a closing adjustment.

### Preview and commit

- Preview is side-effect free and returns the account/currency/coverage, stated and ledger opening/closing balances, both differences, period posting count/net amount, total posting count through close, current balance projection version, and whether `CONFIRM_BALANCED` or `CREATE_ADJUSTMENT` is currently admissible.
- Commit acquires the accepted principal/scope/client-request advisory lock and checks idempotent replay before reading mutable account, version, or reconciliation state, so an exact retry returns its original result even after later facts.
- Commit requires the projection version returned by preview, locks the owner/account/projection, and recomputes every value. A changed version returns the accepted `BALANCE_VERSION_CONFLICT`; the lock/recompute remains authoritative even when versions match.
- `CONFIRM_BALANCED` is valid only when both differences are zero and writes one reconciliation row without a financial activity.
- `CREATE_ADJUSTMENT` is valid only when opening difference is zero and closing difference is non-zero. It requires a trimmed 1–500 character reason and writes the adjustment activity/posting, projection update, reconciliation row, and idempotency result in one transaction.
- Adjustment `effectiveAt` is exactly `statementClosingAt`. It preserves historical reality, is allowed on an archived full-ledger account, and uses historical policy evaluation so a negative result is recorded/warned rather than falsified or rejected as a new current action.
- The through-close posting count is captured after every adjustment activity in the transaction. Exact idempotent replay returns the original snapshot without re-evaluating later ledger state; materially changed reuse returns `IDEMPOTENCY_CONFLICT`.

### Correction and staleness

- Correction accepts a complete replacement statement contract, a new client request UUID, expected balance version, and a required correction reason. It is allowed only for the current end of a supersession chain.
- Correction performs the same command-lock and replay check before testing whether the target has since been superseded; an exact retry returns the original replacement, while a different request against the old target receives the superseded conflict.
- Lock the target reconciliation and account/projection deterministically. If the target already has a replacement, return `RECONCILIATION_ALREADY_SUPERSEDED` without changing facts.
- If the target has an adjustment, create its exact inverse as a `REVERSAL` at the original adjustment effective instant within the correction command. The correction's deterministic command sequences distinguish this reversal from any replacement adjustment under the same operation/client ID.
- After removing the old adjustment's effect, recompute the replacement statement from authoritative postings. Apply the same coverage, time, opening-continuity, resolution, and historical-policy rules as first commit.
- Insert a new reconciliation whose `supersedes_reconciliation_id` points to the prior row, plus a replacement adjustment when required. The prior statement and adjustment remain readable. The reversal, replacement activity/posting, projection update, superseding reconciliation, and idempotency result commit or roll back together.
- Generic `/activities/{id}/reversals` rejects `RECONCILIATION_ADJUSTMENT`; the dedicated correction workflow is the only supported way to replace it while retaining coherent reconciliation evidence.
- A non-superseded reconciliation is `STALE` when the current count of immutable postings for the account with `effectiveAt <= statementClosingAt` exceeds its stored through-close count. Otherwise it is `CURRENT`. Because postings are append-only, this detects every later backdated fact even when multiple activities share one clock instant. Any reconciliation with a replacement is `SUPERSEDED`, regardless of later facts. Lifecycle status is derived; it never rewrites the immutable statement snapshot.
- Activity inserted later with `effectiveAt > statementClosingAt` does not stale that reconciliation. Reversal and opening correction use their accepted original effective-time semantics and therefore stale every affected reconciliation whose closing boundary includes them.

### Reads and responsibility boundaries

- Use a focused reconciliation write repository for aggregate locking/persistence and `JdbcClient` for the joined preview, staleness, latest-summary, list, and detail read shapes when clearer than JPA.
- List rows by `(statementClosingAt DESC, id DESC)` with bounded cursor pagination (default 25, accepted 1–100); bind the cursor to the account filter using the accepted canonical cursor/fingerprint rules.
- Detail/list responses expose immutable statement inputs, computed snapshots, resolution, adjustment activity ID/amount/reason when present, superseded predecessor ID, derived lifecycle status, through-close posting count, source, and creation time.
- Balance responses add a nullable last-reconciliation summary containing reconciliation ID, statement closing instant/balance, resolution, derived lifecycle status, and creation time. Existing ledger/cleared/native-balance fields retain their accepted meaning.
- Cross-owner and missing reconciliation IDs are indistinguishable. Never log statement balances, statement references, adjustment reasons, or arbitrary request bodies.

## API contract

All routes require the accepted bearer authentication, typed principal, owner-scoped queries, stateless handling, and no-store headers.

### Preview

`POST /api/v1/accounts/{accountId}/reconciliation-previews`

Request:

- `statementReference` — required trimmed nonblank text, maximum 200 characters;
- `statementOpeningAt`, `statementClosingAt` — required instants;
- `statementOpeningBalance`, `statementClosingBalance` — required canonical decimal strings.

Return `200 OK` with the exact comparison model, projection version, admissible resolutions, and stable warnings. Preview persists nothing and is never authorization for commit.

### Commit

`POST /api/v1/accounts/{accountId}/reconciliations`

Request adds to the preview fields:

- `clientRequestId` — required UUID;
- `expectedBalanceVersion` — required non-negative long;
- `resolution` — exact `CONFIRM_BALANCED` or `CREATE_ADJUSTMENT`;
- `adjustmentReason` — required only for `CREATE_ADJUSTMENT` and otherwise prohibited.

Return `201 Created`, a route-relative reconciliation `Location`, and the committed reconciliation detail.

### Read and correct

- `GET /api/v1/accounts/{accountId}/reconciliations?limit=&cursor=` — owner-scoped page.
- `GET /api/v1/reconciliations/{reconciliationId}` — owner-scoped detail.
- `POST /api/v1/reconciliations/{reconciliationId}/corrections` — same complete statement/resolution fields as commit, plus `clientRequestId`, `expectedBalanceVersion`, and required `correctionReason`; return `201 Created` with the replacement location/detail.

Stable capability errors added as needed:

- `RECONCILIATION_NOT_FOUND`;
- `RECONCILIATION_COVERAGE_GAP`;
- `RECONCILIATION_OPENING_MISMATCH`;
- `RECONCILIATION_RESOLUTION_REQUIRED`;
- `RECONCILIATION_ALREADY_SUPERSEDED`.

Reuse `ACCOUNT_NOT_FOUND`, `ACCOUNT_ACTION_NOT_SUPPORTED`, `BALANCE_VERSION_CONFLICT`, `IDEMPOTENCY_CONFLICT`, shared validation/malformed-request errors, and safe persistence fallbacks where their accepted meaning is exact. Do not expose the existence of another owner's account/reconciliation or any SQL/constraint detail.

## Business invariants

- A statement balance is external evidence and never overwrites a ledger balance.
- A committed reconciliation always starts from exact known coverage and exact opening continuity.
- A balanced reconciliation creates no money. An adjusted reconciliation creates exactly one non-zero signed adjustment equal to the closing difference.
- An adjustment is explicitly unexplained and does not become income, spending, fee, interest, transfer, or performance by sign inference.
- Statement evidence, activities, and postings are append-only. Correction is reversal plus superseding replacement, never update/delete.
- At most one replacement directly supersedes a reconciliation, and only the current chain end may be corrected.
- Later historical facts cannot leave a reconciliation falsely `CURRENT`; later facts outside its closing boundary do not invalidate it.
- Reconciliation commit/correction, projection mutation, immutable financial facts, and idempotency result are atomic.
- Exact retries create one reconciliation/economic effect; conflicts and failures create none.
- Full-ledger ownership, account/pocket/currency identity, deterministic locking, historical policy behavior, numeric precision, and financial time semantics remain governed by the accounting contract and PR-021 accepted invariants.

## Required tests

### Pure/domain

- Hand-worked zero-difference and positive/negative closing-difference examples prove every equation and canonical decimal response.
- Asset and liability statement inputs retain their normalized stored signs; no account-kind sign flip occurs.
- Preview admissibility covers balanced, closing-difference, opening-mismatch, coverage-gap, future-close, equal/reversed instants, and numeric boundary cases.
- `RECONCILIATION_ADJUSTMENT` and `ADJUSTMENT` factories accept only the historical, non-zero, bounded-reason shape; generic reversal eligibility excludes reconciliation adjustments.
- Canonical fingerprints treat numerically equal decimal scales identically and bind every semantic statement/resolution/correction field.
- Cursor round-trip, malformed/extra-field rejection, ordering, and account-filter binding follow the accepted ledger cursor contract.

### PostgreSQL/Testcontainers

- Fresh V1-to-V4 migration, V3-to-V4 upgrade, Hibernate validation, exact new constraint/index inventory, and absence of excluded tables.
- Mapping and database-constraint tests cover owner/account/pocket/currency consistency, time ordering, numeric bounds, resolution/difference/adjustment shapes, reason/reference bounds, self/double supersession, adjustment links, and extended activity/posting checks without weakening V3 shapes.
- Preview/commit proves exact opening and closing as-of semantics, `(opening, closing]` period movement, archived full-ledger support, holdings-only rejection, liability direction, and latest balance reconciliation metadata.
- Balanced commit creates one reconciliation and no activity/posting; adjusted commit creates exactly one reconciliation/activity/posting and changes the projection by the exact difference.
- Retry tests prove exact replay after later state change and conflict on materially changed reuse.
- Transaction rollback tests inject reconciliation, activity, posting, projection, and idempotency persistence failures and prove no partial evidence or money survives.
- Concurrency tests prove two commits/corrections against one account serialize, stale expected versions fail safely, only one superseder wins, duplicate retries create one result, and no lost projection update occurs.
- Staleness tests prove later-recorded backdated deposit/withdrawal/transfer/reversal/opening correction at or before close makes the reconciliation stale, while later effective activity does not.
- Correction tests prove old adjustment reversal, replacement difference, supersession chain, immutable prior rows, through-close posting-count capture, archived-account behavior, and atomic rollback.
- Owner deletion follows the accepted cascade policy without weakening cross-owner foreign keys.

### HTTP/security

- Real bearer-filter tests cover preview, balanced commit, adjusted commit, list, detail, correction, and the nullable balance last-reconciliation summary with canonical decimal strings and no-store headers.
- Missing, malformed, invalid, and revoked bearer credentials receive the accepted 401; no route becomes public and exactly one security chain remains.
- Cross-owner account/reconciliation access returns the same safe not-found behavior as missing resources for preview, commit, list, detail, and correction.
- Request validation covers missing/null fields, blank/oversized reference/reasons, malformed decimals/UUIDs/instants/enums, negative versions, conditional reason rules, future/invalid periods, opening mismatch, wrong resolution, holdings-only accounts, idempotency conflict, version conflict, and already-superseded correction.
- Responses expose no servlet session, JPA entity, SQL/constraint detail, statement data in errors, or legacy endpoint.

## Acceptance criteria

1. PR-021 is marked complete in accepted implementation commit `e08f2c2`, `CURRENT.md` points only to this specification, and repository/Git state remains user-owned.
2. V4 migrates cleanly from both an empty database and accepted V3, Hibernate validates it, and V1–V3 are unchanged.
3. An authenticated owner can preview a statement comparison for an owned full-ledger account with exact coverage, boundary, period-movement, and difference semantics.
4. Exact opening continuity is mandatory; an earlier mismatch cannot be hidden in a closing adjustment.
5. A balanced commit records immutable reconciliation evidence without creating money.
6. A non-zero closing difference commits only after explicit adjustment selection and reason, and creates exactly one separately identifiable unexplained historical adjustment equal to that difference.
7. Statement evidence never replaces the ledger; balances continue to derive from immutable postings and the accepted projection.
8. Correction reverses any old adjustment and creates one superseding reconciliation/replacement effect atomically; original rows remain readable and cannot be generically reversed or edited.
9. Lifecycle reads report `CURRENT`, `STALE`, and `SUPERSEDED` correctly for later historical versus later effective activity.
10. Owner scoping, no-store delivery, Problem Details, exact decimals, idempotent replay/conflict, deterministic locking, version conflict, rollback, archive handling, and liability sign behavior satisfy the specified tests.
11. Account balance reads expose truthful nullable last-reconciliation metadata without changing accepted ledger/cleared balance meanings.
12. No statement-line/import/pending/provider, fee/interest classification, investing, multi-currency/FX, generic framework, async infrastructure, frontend, or unrelated cleanup is added.
13. The focused gate, full test suite, Maven `verify`, Spotless, `git diff --check`, and status inspection pass with no failures, errors, skipped required tests, or unresolved `MUST FIX` findings.
14. Completion documentation records actual implementation, deviations, decisions, migration state, exact verification commands/results, and remaining neighboring work without marking later capabilities implemented.

## Documentation completion

Before this implementation unit is considered complete:

1. Update `docs/implementation/STATE.md` with current repository reality only: implemented reconciliation behavior, V4 schema state, verified decisions, deferred work, and latest useful verification state.
2. Replace or remove obsolete STATE statements; do not append a history trail or preserve old test totals.
3. Update `docs/review/accounting-contract.md` first only if implementation discovers a genuinely cross-cutting semantic change; do not restate PR-local mechanics there.
4. Update `docs/review/backend-master-plan.md`, the cash-account design, or `progress-report.md` only when verified behavior materially changes their authority/current project checkpoint.
5. Move reusable Windows, sandbox, Maven, PostgreSQL/Testcontainers, or output lessons to `docs/engineering/codex-command-playbook.md`.
6. Keep detailed implementation history, deviations, and exact verification results in this specification's Completion Record and Git history.
7. Keep `CURRENT.md` pointing to PR-022 throughout implementation and review; lifecycle transition remains user-controlled.

## Verification commands

```bash
./mvnw "-Dtest=LedgerReconciliationMigrationTest,LedgerReconciliationMappingTest,LedgerReconciliationServiceTest,LedgerReconciliationConcurrencyTest,LedgerReconciliationHttpTest,LedgerTransactionRollbackTest,ApiBearerSecurityHttpTest" test
./mvnw test
./mvnw verify
./mvnw spotless:check
git status --short --untracked-files=all
git diff --check
```

Focused class names may be adjusted to the final idiomatic test split, but every required proof category must remain and the Completion Record must list the exact commands and results.

## Completion record

Fill this before marking the PR complete.

### Implemented

- Added forward-only V4 Flyway migration with database-protected append-only reconciliation evidence, owner/account/pocket/currency identity constraints, exact opening-continuity and resolution-shape equations, supersession uniqueness, exact adjustment-activity typing, activity/posting extensions, and the owner/account closing-order index.
- Added exact domain/application behavior for inclusive as-of preview, mandatory opening continuity, balanced evidence, explicit signed historical adjustments, idempotent commit, correction reversal plus superseding replacement, derived current/stale/superseded lifecycle, owner-scoped reads, cursor pagination, and nullable balance metadata.
- Aligned the persistence boundary with that immutability: `Reconciliation` is Hibernate `@Immutable`, its repository exposes only save/read operations, and the PostgreSQL trigger rejects direct updates/deletes and all non-owner cascades while permitting only cascades whose owning `identity.user_account` row has actually been deleted.
- Added authenticated no-store HTTP routes for preview, commit, list, detail, and dedicated correction; the wire action values are `CONFIRM_BALANCED` and `CREATE_ADJUSTMENT`, mapped to persisted `BALANCED` and `ADJUSTED` resolutions.
- Added PostgreSQL/Testcontainers migration and mapping proof for V3-to-V4 preservation, every reconciliation numeric shape, raw owner/account/pocket/currency/equation/bounds/supersession/link and exact adjustment-activity-shape violations, direct valid mutation rejection, non-owner account/pocket/activity cascade rejection, equal/reversed instants, and owner-cascade deletion through an adjusted chain. Added hand-worked domain equations; parameterized commit and correction fingerprint tests covering every fingerprinted field, account/target binding, and scale-equivalent decimal replay; exact correction replay after supersession; duplicate concurrent commit and correction retry; raw V3 posting role/sign/zero preservation plus positive and negative adjustment postings and zero rejection; maximum reconciliation numeric insertion and integer-overflow rejection; activity/posting/projection/idempotency and correction rollback; longer supersession chains; archived commit/correction; withdrawal/transfer/reversal/opening-correction staleness; a negative historical adjustment crossing the hard floor with `HISTORICAL_BREACH_RECORDED`; concurrent commit/correction locking; authenticated HTTP/security validation, ownership, conflict, nullable pre-reconciliation and post-correction balance metadata, no-sensitive-error checks, and cursor/filter-binding coverage.

### Deviations from specification

- The final idiomatic test split retained the existing `FinancialAccountMigrationTest` and `FinancialAccountMappingTest` for V4 inventory and JPA round-trip assertions, rather than introducing separate classes with the provisional migration/mapping names. The required proof categories are explicitly covered by those tests and the expanded reconciliation service, concurrency, rollback, HTTP, security, domain, and cursor cases.
- Direct reconciliation mutation failures surface through the repository's general `DataAccessException` boundary because the append-only trigger deliberately raises PostgreSQL SQLSTATE `55000`; this does not weaken the required persistence proof.
- No architecture or accounting-contract document was changed because the implementation stays within the existing ledger capability and applies the already authoritative exact-decimal, coverage, correction, projection, locking, and idempotency semantics.

### New decisions

- Reconciliation adjustment activity reasons use the accepted bounded activity correction-reason column while the reconciliation row retains its own immutable adjustment reason; both are normalized and constrained.
- The adjustment link uses a generated constant activity-type column plus a unique owner/id/type activity key, so a reconciliation can reference only an exact `RECONCILIATION_ADJUSTMENT` owned by the same user.
- The database append-only trigger permits a cascaded reconciliation delete only after its owning `identity.user_account` row is absent; direct deletes and cascades from an account, pocket, or adjustment activity remain rejected while owner cleanup remains valid.
- Supersession is bound by `(owner_user_account_id, financial_account_id, reconciliation_id)` so a replacement cannot cross the owner’s financial-account boundary.
- Balance last-reconciliation metadata reads only non-superseded chain ends and orders by closing instant, creation instant, and UUID; balance reads use a repeatable-read transaction across account, projection, and reconciliation queries.
- Request records own structural reason length through `@Size`; custom validation owns only conditional presence, blankness, and normalization rules.
- Lifecycle is derived in focused SQL from replacement existence and immutable through-close posting-count comparison; statement evidence is never updated after insertion.
- The correction path validates the old adjustment posting’s account, pocket, and currency identity before applying its inverse to the locked projection.

### Tests executed

- `.\mvnw.cmd "-Dtest=FinancialAccountMigrationTest,FinancialAccountMappingTest,LedgerReconciliationServiceTest,LedgerReconciliationConcurrencyTest,LedgerReconciliationHttpTest,LedgerTransactionRollbackTest,ApiBearerSecurityHttpTest,LedgerDomainInvariantTest,LedgerCursorCodecTest" test` — passed with Docker Desktop/Testcontainers PostgreSQL 17 available; 80 tests ran with 0 failures, 0 errors, and 0 skips.
- `.\mvnw.cmd test` — passed; 377 tests ran with 0 failures, 0 errors, and 0 skips.
- `.\mvnw.cmd verify` — passed; 377 tests ran with 0 failures, 0 errors, and 0 skips, the jar was packaged and repackaged successfully, and the lifecycle Spotless check passed.
- `.\mvnw.cmd spotless:check` — passed; all 281 Java files were clean.
- `git -c safe.directory=C:/Users/Vintage/Documents/stocks status --short --untracked-files=all` — passed; no staged changes, `CURRENT.md` still points to PR-022, and the active PR files remain available in the working tree alongside preserved pre-existing changes.
- `git -c safe.directory=C:/Users/Vintage/Documents/stocks diff --check` — passed; Git reported only existing LF/CRLF normalization warnings.
- Maven initially encountered the documented sandbox network `Permission denied: getsockopt` dependency-resolution failure; the approved escalated invocation completed the focused gate, full suite, and verify successfully after Docker Desktop was started and its `docker_engine` named pipe became available. The runs emitted only existing framework/JVM and Hikari teardown warnings; no test failures, errors, or skips were reported.

### Follow-up work

- Statement-line/file import, matching/duplicates, pending/settlement states, fee/interest classification, investing, multi-currency/FX, providers, and asynchronous workloads remain separate capabilities.

Last updated: 2026-08-25.
