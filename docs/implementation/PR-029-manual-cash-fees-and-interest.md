# PR-029 - Manual cash fees and interest credits

Status: **COMPLETE**

## Goal

An authenticated owner can record a real account fee or credited cash interest on an eligible full-ledger account, then see the exact signed effect in the account balance and activity history, inspect it, and reverse an erroneous entry. This completes the remaining manual cash activity types named in R3. Existing reconciliation-backed adjustments already supply R3's explicit adjustment behavior and must remain distinct.

## Capability and review boundary

- Coherent capability: two single-account, native-currency, manually entered cash facts with explicit economic meaning, shared ledger safeguards, and usable account-page actions.
- Combined behaviors: Flyway activity/posting checks, domain factories, transactional command and reversal handling, HTTP/OpenAPI, PostgreSQL and security proof, and the current account UI belong together. A backend enum without a way to enter or inspect the action would leave this product boundary unfinished.
- Excluded neighbor: transfer fees, trade commissions, dividends, tax/withholding, loan/card interest and fees, rate accrual, automatic imports, classification reports, and generic unexplained adjustments remain with their own workflows. Reconciliation adjustments remain available only through reconciliation and correction.
- Focused review: one reviewer can trace each new type from a manual form through one immutable activity and signed posting to the balance, history, reversal, and reconciliation staleness behavior.
- This is a deliberately bounded cross-stack exception to the separate `PR-xxx` and `UI-xxx` tracks because the user requested an R3 product-completion specification after substantial UI work. Do not use it to reopen UI-001 foundation or redesign account screens.

## Source documents

- `docs/review/backend-master-plan.md` - R3 item 8 and Stage 2 cash-account exit gate.
- `docs/review/accounting-contract.md` - sections 2, 4, 6-8, 17-19, and 21.
- `docs/review/cash-accounts-and-funding-design.md` - action posting behavior, negative-balance policy, and reconciliation/adjustment boundary.
- `docs/engineering/coding-standards.md` - migration ownership, financial amounts, error/HTTP contracts, and testing.
- `docs/engineering/frontend-standards.md` and `docs/engineering/ui-design-guidelines.md` - existing typed API, forms, query invalidation, and usable mobile account actions.
- `AGENTS.md`, `server/AGENTS.md`, and `web/AGENTS.md`.

## Starting state

- Planning snapshot: clean working tree at `17d571b`; backend accepted through PR-028 and database migration V4. Confirm the actual base again when this draft is activated.
- `POST /api/v1/accounts/{accountId}/activities` currently accepts `CASH_DEPOSIT` and `CASH_WITHDRAWAL`; its service supplies owner checks, policy evaluation, projection update, idempotency, and activity reads. Generic reversal excludes opening and reconciliation-adjustment activities.
- V4 activity and money-posting checks enumerate permitted types, policy shapes, roles, and signs. Reconciliation already records `RECONCILIATION_ADJUSTMENT` with a reason, signed `ADJUSTMENT` posting, and dedicated correction lifecycle. Do not create a second unexplained-adjustment path.
- The current frontend uses generated OpenAPI types, the `RecordCashActivity` overlay, account quick/actions menus, activity list/detail/reversal, and TanStack Query invalidation. `docs/implementation/web/STATE.md` describes an older foundation snapshot and is not evidence that these current screens are absent. The frontend `CURRENT.md` still points to UI-001; the user owns any pointer transition before cross-stack implementation.

## Scope

1. Add two explicit ledger activity types: `CASH_FEE` for a positive entered amount producing a negative cash posting, and `CASH_INTEREST_CREDIT` for a positive entered amount producing a positive cash posting. Add matching posting roles `FEE` and `INTEREST_CREDIT`.
2. Extend the existing account cash-activity command and request contract for these two types. Preserve deposit/withdrawal behavior and the existing endpoint shape. No separate command framework or duplicate activity service.
3. Support `CURRENT_ACTION` and `HISTORICAL_FACT` with the same future-time, coverage/account eligibility, policy, version, idempotency, lock, and exact decimal rules already used by cash movements. A fee is an outflow subject to the account's negative-balance policy; an interest credit is an inflow. Neither is a deposit/withdrawal or an opening state.
4. Persist one owner/account/pocket/currency-consistent activity and one non-zero signed posting per successful command. Update the native balance projection atomically. Preserve normal activity list/detail and as-of balance behavior.
5. Permit a fee or interest-credit activity to use the existing generic reasoned reversal. Its reversal retains the original effective instant and creates the inverse posting; it cannot mutate/delete the original. A reconciled period affected by a new or reversed backdated entry must derive `STALE` status under the existing reconciliation contract.
6. Regenerate the frontend OpenAPI schema from the implemented backend contract. Extend the current account activity UI to record the two manual actions from an eligible account, show the correct type/sign in recent and paginated history and detail, and offer reversal where allowed. Reuse the existing account overlay, typed API mutations, error presentation, and query infrastructure; keep the action reachable on phone-sized screens without a visual redesign.
7. After successful entry or reversal, refresh account list/detail, current and as-of balances, activity list/detail, and affected reconciliation data. Show the server result as success only after a successful response. Keep one client request ID for an uncertain retry of the same payload; use a new ID after changing the payload.

## Explicit non-goals

- No standalone `ADJUSTMENT` command, arbitrary balance overwrite, or generic reversal of a reconciliation adjustment.
- No accrued/automatically calculated interest, rates, compounding, card/loan interest, tax, dividend, trade fee, transfer fee, multi-currency/FX, settlement, import, or provider integration.
- No spending/income categorization, analytics, portfolio valuation, redesign, general state manager, generic mapper layer, or MyBatis work.
- No change to existing accepted deposit, withdrawal, transfer, opening-state, or reconciliation semantics except the integration needed to keep their shared reads and checks valid.

## Database changes

Migration: `V5__manual_cash_fees_and_interest.sql` (next actual Flyway version; roadmap increment numbers do not determine migration numbers).

- Extend `ck_ledger_activity_type` and `ck_ledger_activity_policy_shape` for the two new types using the existing current/historical policy decisions. Preserve the special historical, reasoned reconciliation-adjustment shape.
- Extend `ck_ledger_money_posting_role` and `ck_ledger_money_posting_role_sign`: `FEE` requires `amount < 0`, and `INTEREST_CREDIT` requires `amount > 0`. Existing zero rules, owner/account/pocket/currency foreign keys, and all existing activity/posting shapes remain enforced.
- No new table, free-form memo column, mutable balance authority, or speculative classification structure is required for this bounded manual entry.
- Prove both empty-to-V5 migration and V4-to-V5 upgrade against PostgreSQL with Hibernate validation. Raw SQL constraint tests must show invalid new role/sign/policy combinations fail while existing V3/V4 facts remain valid.

## Application changes

- Keep the existing ledger application service as the transactional command owner. Add only the type-specific factories and sign selection needed for the two new facts; retain the established idempotency scope, fingerprint behavior, lock order, error codes, and response assembly.
- Continue using immutable `Activity`/`MoneyPosting` facts and the rebuildable projection. Do not interpret positive interest as a generic deposit or negative fee as a generic withdrawal in persisted type/role or future reporting.
- Reuse the existing owner-scoped list/detail, reversal, balance, and derived reconciliation reads. Extend only genuine type-specific assumptions or allowlists found in those paths.
- In `web/`, adapt the existing account activity flow and type presentation rather than adding a parallel financial workflow. Use the backend's canonical decimal strings for commands; display formatting must not become calculation authority.

## API contract

- `POST /api/v1/accounts/{accountId}/activities` continues to take `clientRequestId`, `activityType`, positive decimal-string `amount`, `recordingMode`, `effectiveAt`, `confirmPolicyBreach`, and optional `expectedBalanceVersion`. It now accepts `CASH_FEE` and `CASH_INTEREST_CREDIT` in addition to existing deposit/withdrawal types. Other activity types remain rejected at this endpoint.
- The successful response remains `201 ActivityResponse` with no-store headers and a detail `Location`. Activity list/detail and reversal endpoints expose the new enum values and posting roles through the existing response shape; no fabricated success or separate fake feed.
- Preserve stable validation, future-time, archived/ineligible-account, ownership/not-found, policy rejection/confirmation, balance-version conflict, and idempotency-conflict behavior. The generated OpenAPI schema and frontend typed client must agree with the running backend.

## Business invariants

- Entered amount is strictly positive and uses exact decimal strings. A fee posts exactly its negative; an interest credit posts exactly its positive in the account's native currency. There is no implicit second leg, rate calculation, rounding, or classification by sign.
- Only an active, owner-held, full-ledger cash-capable asset account can receive these manual commands; holdings-only and liability accounts remain ineligible in this slice.
- Current-action fee debits obey hard/soft/authorized/reality policy decisions; historical fees record historical reality and warnings according to the existing evaluator. Backdated postings and reversals affect as-of balances and reconciliation staleness by effective time, not record time.
- Same principal/scope/request ID and same material payload replay the original response without duplicate money. Changed payload with a used ID conflicts. A failed transaction leaves no activity, posting, projection change, or idempotency record.
- Reconciliation adjustments remain unexplained statement evidence; neither new type can be used to disguise or bypass an opening-continuity failure.

## Required tests

### Pure/domain

- Exact positive input to negative fee and positive interest posting, zero/negative input rejection, role/sign factories, policy decisions at hard/soft/authorized/reality boundaries, decimal-scale equivalence, and reversal sign/effective-time behavior.

### PostgreSQL/Testcontainers

- Fresh V5 and V4-upgrade migration/mapping proof; valid new facts and raw invalid type/policy/role/sign/zero/owner/pocket rows; no regression of existing adjustment constraints.
- Command atomicity, exact current/as-of balances, idempotent replay and changed-key conflict, concurrent fee spending/version conflict, reversal and double-reversal rejection, rollback, backdated reconciliation staleness, and cross-owner isolation.

### HTTP/security

- Authenticated fee and interest creation, owner-only list/detail/reversal, unsupported type/account rejection, 201/Location/no-store and canonical decimal response, 422 validation/future/policy failures, 409 conflicts, and absence of sensitive details in errors.

### Frontend integration

- Use existing frontend test files only if a focused test is needed for a concrete behavior; do not add speculative test files. Verify the new actions submit to the real endpoint, expose a server failure instead of success, invalidate affected queries, show the correct history/detail labels and signs, and do not offer generic reversal for reconciliation adjustments. Confirm the generated schema matches the backend OpenAPI output.

## Acceptance criteria

1. From an eligible account in the current UI, an owner can record a manual fee and an interest credit against the real API; the returned activity and refreshed balance/history show the exact signed effects.
2. Both actions are owner-scoped, idempotent, version-safe, reversible with a reason, and correct under current/historical policy and time rules. Backdated actions or reversals make affected reconciliation evidence stale.
3. PostgreSQL checks and JPA mapping accept the new valid shapes and reject invalid ones without weakening V3/V4 facts or reconciliation-adjustment protection.
4. The activity endpoint rejects every type outside its four allowed manual cash actions; no standalone adjustment path is introduced.
5. Frontend and generated OpenAPI contracts reflect the implemented backend, with usable errors and post-success refresh. No mock action is used for the new behavior.
6. Focused tests, complete backend `test` and `verify`, backend formatting, frontend typecheck, changed-file Biome check, applicable frontend tests, build, and OpenAPI drift check pass. Run and report the full frontend Biome check too; distinguish any pre-existing violations in untouched files from this unit's changes and do not expand the PR into unrelated formatting cleanup.

## Documentation completion

Before this implementation unit is considered complete:

1. Update `docs/implementation/STATE.md` with current backend reality, V5 schema, and current verified status; remove obsolete assertions instead of appending history.
2. Update `docs/implementation/web/STATE.md` only for verified frontend capability and verification reality; do not preserve the stale foundation-only account of the current UI.
3. Update authoritative architecture/accounting documents only if verified behavior changes their contract. Record any material project-level scope decision in `docs/review/progress-report.md`.
4. Put detailed implementation and test evidence in this specification's Completion Record. Leave `CURRENT.md` transitions to the user.

## Verification commands

Run from `server/` (include focused fee/interest, migration, reconciliation, and HTTP/security tests before the full gates):

```powershell
.\mvnw.cmd spotless:check
.\mvnw.cmd test
.\mvnw.cmd verify
```

Run from `web/` against a fresh backend OpenAPI contract:

```powershell
npm.cmd run generate:openapi
npm.cmd run check:openapi
npm.cmd run typecheck
npx.cmd biome check ./src
npm.cmd run test
npm.cmd run build
```

Do not edit generated schema by hand or weaken/skip database tests to pass verification.
If the full Biome check reports existing violations outside this unit, record the exact files and results separately; all files changed by this unit must pass.

## Completion record

Fill this before marking the PR complete.

### Implemented

- Added `CASH_FEE` and `CASH_INTEREST_CREDIT` activity types, `FEE` and `INTEREST_CREDIT` posting roles, exact-sign domain factories, and the existing cash-activity command/reversal path for current and historical entries.
- Preserved the existing owner, eligibility, future-time, policy, version, idempotency, lock-order, transaction, decimal-string, projection, as-of balance, list/detail, and reconciliation-staleness behavior. Current-action entry captures submission time together with its request ID and payload fingerprint; unchanged retries reuse the tuple, while material payload changes rotate both ID and time. Fees remain policy-governed outflows; interest credits remain positive inflows.
- Added Flyway V5 checks for the new activity/policy shapes and posting roles/signs without adding tables or weakening V3/V4 constraints. The V4-to-V5 upgrade test now seeds balanced and adjusted V4 reconciliations, an adjustment activity and posting, and supersession linkage before migration, then verifies preservation and constraint enforcement.
- Extended the existing activity request/response/OpenAPI contract and regenerated `web/src/api/schema.d.ts`. Added account-page fee and interest actions, record-form choices and confirmations, labels/icons/sign styling, history/detail support, generic reasoned reversal support with an activity/reason-keyed retry ID, mobile reachability, and activity list/detail plus account/balance/reconciliation invalidation after entry/reversal.
- Updated the backend/frontend implementation state and this completion record; `CURRENT.md` remains user-managed and was not advanced.

### Deviations from specification

- The user selected PR-029 explicitly while `CURRENT.md` still reported `NONE`; the pointer was intentionally left unchanged because the implementation contract says to leave `CURRENT.md` transitions to the user and the invocation forbids advancing it.
- The disallowed-origin preflight in `LocalRefreshHttpTest` is rejected by the configured CORS layer with HTTP 403 before bearer authentication; the test expected 401 despite also asserting no `Access-Control-Allow-Origin`. The expectation now matches that existing security contract; no production security behavior changed.
- The full frontend Biome gate reports 12 pre-existing formatting violations in untouched files: `account-hero-card.tsx`, `account-layout-header.tsx`, `dashboard-page.tsx`, `marketing-faq.tsx`, `marketing-features.tsx`, `marketing-footer.tsx`, `hero-terminal-card.tsx`, `marketing-hero.tsx`, `responsive-drawer.tsx`, `overlay-host.tsx`, `css-variables.ts`, and `theme.ts`. Every PR-029-changed TypeScript/TSX file passes the targeted check; generated schema and CSS are excluded by the repository Biome configuration.
- No new frontend test file was added; the existing frontend suite, typecheck, generated-contract check, targeted formatter, and production build cover the changed UI contract without introducing a speculative test surface.

### New decisions

- The request DTO uses an explicit OpenAPI allowable-value schema for the four supported manual cash actions while retaining the Jackson enum contract, so generated clients cannot advertise opening, transfer, reconciliation, or other activity types on this endpoint.
- The existing generic reasoned reversal and query invalidation infrastructure is reused for fees and interest credits. Entry retries retain `{id, effectiveAt, fingerprint}` for an unchanged payload and rotate both the ID and current-action effective time after material changes; reversal retries retain one ID per activity and trimmed correction reason.
- OpenAPI generation used a fresh, explicitly named PostgreSQL 17 container and an isolated application port 8081 because port 8080 was occupied by an unrelated process; the temporary container and application were stopped and the package configuration was restored.

### Tests executed

- Focused backend PR-029 gate: `mvnw.cmd "-Dtest=LedgerDomainInvariantTest,FinancialAccountMigrationTest,FinancialAccountMappingTest,CashActivityServiceTest,CashLedgerConcurrencyTest,LedgerTransactionRollbackTest,CashActivityHttpTest,LedgerReconciliationServiceTest" test` — 93 tests passed, 0 failures, 0 errors, 0 skipped, using PostgreSQL 17 Testcontainers.
- Backend formatting: `mvnw.cmd spotless:check` — passed after formatting the modified files.
- Backend focused correction gate: `mvnw.cmd "-Dtest=FinancialAccountMigrationTest,LocalRefreshHttpTest" test` — 21 tests passed, 0 failures, 0 errors, 0 skipped.
- Backend full `mvnw.cmd test` — 382 tests passed, 0 failures, 0 errors, 0 skipped.
- Backend `mvnw.cmd verify` — 382 tests passed, 0 failures, 0 errors, 0 skipped; executable archive repackaged successfully.
- OpenAPI: `npm.cmd run generate:openapi` and `npm.cmd run check:openapi` — passed against the running PR-029 backend on the isolated verification port; generated schema exposes the four manual cash request types and the 201 response contract.
- Frontend: `npm.cmd run typecheck` — passed; targeted `npx.cmd biome check` on all changed TypeScript/TSX files — passed; full required `npx.cmd biome check ./src` — failed only on the 12 untouched files listed above; `npm.cmd run test` — 62 tests passed; `npm.cmd run build` — passed.
- Migration and mapping proof includes fresh V5, V4-to-V5 upgrade, valid fee/interest facts, invalid raw policy/sign combinations, Hibernate validation, rollback, hard-floor concurrency, signed as-of balances, reversal, and backdated reconciliation staleness.

### Follow-up work

- Human review/acceptance of the working-tree implementation and the documented pre-existing full-frontend Biome violations in untouched files.
- If accepted, the user should advance `docs/implementation/CURRENT.md` manually; no later PR was drafted or activated by this implementation.
