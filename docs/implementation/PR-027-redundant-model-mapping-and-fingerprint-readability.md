# PR-027 - Cleanup D redundant model, mapping, and fingerprint readability

Status: **ACTIVE**

## Goal

Complete the final planned cleanup boundary before R4 by removing the proven endpoint-only model/mapping chains, trimming unused read-projection components, and extracting only the long workflow-specific canonical fingerprint blocks whose inline form obscures orchestration. Preserve every genuine domain, aggregate, query, lifecycle, calculation, projection, HTTP, accounting, security, and idempotency concept and make no observable API or financial behavior change.

## Capability and review boundary

- Coherent capability: the remaining accidental ceremony is one small directness cleanup across endpoint-only response mapping and ledger idempotency readability. The model removals and fingerprint extractions apply the same accepted PR-023 rule: extract concepts, not lines, and do not introduce an intermediate application type solely for layer purity.
- Combined behaviors: delete the redundant transfer preview model/factory, map the three endpoint-only reference lists directly to their response records, trim unused projection components, perform five workflow-specific fingerprint extractions, inline the deferred reconciliation nullable trim inside those fingerprint methods, and remove one evaluator-pass-through helper.
- Excluded neighbor: R4 investing is the next product boundary. Cleanup D does not design or implement it. The parked MyBatis/read-persistence audit remains much later and is not a prerequisite for R4.
- Focused review: one reviewer can verify one deleted production file, three deleted nested row records, four removed response factories, five workflow-specific fingerprint extractions, two removed reconciliation helpers, unchanged ordered canonical values, and behavior-focused tests as one unit.
- One PR is preferable. The proven model/mapping deletions and fingerprint placement changes are small, share the same directness authority, require no schema or API change, and remain human-reviewable together. Splitting them would create a mechanical fingerprint-only follow-up without an independent product boundary.
- Do not use file-count reduction as a success metric. Every retained model and boundary below has repository-backed independent value.

## Source documents

- `AGENTS.md` - planning/implementation isolation, active-spec lifecycle, Git ownership, repository constraints, and context maintenance.
- `docs/implementation/README.md` and `docs/implementation/PR-TEMPLATE.md` - specification structure and manual activation workflow.
- `docs/implementation/STATE.md` and `docs/implementation/CURRENT.md` - current repository reality and active pointer.
- `docs/engineering/coding-standards.md` - sections 2-4, 6, 8, 9.5, 11, 13, and 14: directness, model placement, response mapping, query projections, service boundaries, and behavior-focused tests.
- `docs/review/backend-master-plan.md` - current standardization conventions, architecture/API invariants, file economy, R3-R4 boundary, pull-request execution, and testing strategy.
- `docs/review/accounting-contract.md` - sections 3, 4, 6-8, 13, 17, 18, and 21: clocks/economic ordering, opening coverage, immutable correction, signed postings, balance meaning, projections, idempotency/concurrency, and API contracts.
- `docs/implementation/PR-023-governing-simplicity-standards.md` - accepted authority for concept-oriented extraction, pragmatic model/mapping boundaries, exact fingerprint readability, and evidence-based service/repository granularity.
- `docs/implementation/PR-024-ledger-pagination-simplification.md` - accepted direct response mapping for endpoint-only ledger reads and confirmed removal of `PostingView`, `ActivityView`, and `ReconciliationView`.
- `docs/implementation/PR-025-identity-reference-pagination-simplification.md` - accepted preservation of `DeviceSessionFamilyRecord` and `ReferenceCatalogReadRepository.InstrumentView` as genuine aggregates.
- `docs/implementation/PR-026-validation-error-and-trivial-abstraction-simplification.md` - approved Cleanup C boundary and explicit deferral of fingerprint readability and `ReconciliationCommandService.normalized` to Cleanup D.

## Starting state

- At specification time PR-026 is still the active pointer, although its implementation and review are complete and approved. The user will accept/commit PR-026 before manually activating PR-027.
- PR-023 through PR-025 are accepted and committed. Cleanup B is complete; custom ordinary-list cursor infrastructure is absent.
- PR-026 implementation is verified: future ledger time is capability-coded, `LedgerTimingRules` and fourteen audited trivial helpers are removed, structural request validation remains at HTTP boundaries, and reconciliation retains non-controller period integrity.
- Current production source contains exactly five ledger application `*View` records plus the nested reference `InstrumentView`: `FinancialAccountView`, `BalanceView`, `LastReconciliationSummaryView`, `ReconciliationPreviewView`, `TransferPreviewView`, and `ReferenceCatalogReadRepository.InstrumentView`.
- `PostingView`, `ActivityView`, and `ReconciliationView` have no production or test occurrence and must remain absent.
- `DeviceSessionFamilyRecord` remains an infrastructure aggregate even though its name does not end in `View`.
- `CanonicalFingerprint` has ten `fingerprint.values(...)` construction sites across account onboarding/lifecycle/settings, cash activity/reversal, transfer, and reconciliation commit/correction. Its implementation and public methods are not cleanup targets.
- Flyway V1-V4, one PostgreSQL database, one Spring Boot process, current JPA/JdbcClient boundaries, and all current API/security/accounting contracts remain sufficient. No migration, dependency, configuration, or frontend change is required.

## Repository findings

### Remaining View and known-model audit

| Candidate                                           | Producers                                                                                                                                        | Consumers                                                                                                                                                | Independent semantics?                                                                                                                                             | Decision        | Proposed change                                                                                                                                              |
| --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `TransferPreviewView`                               | Constructed once in `CashTransferService.preview` after balance/policy calculation                                                               | Immediately copied by `TransferPreviewResponse.from`, then returned over HTTP                                                                            | No. It has the same components as the response except `currencyCode`/`currency` naming and has no second consumer or calculation behavior.                         | **SIMPLIFY**    | Construct `TransferPreviewResponse` directly in the service; delete the view and its factory.                                                                |
| `FinancialAccountView`                              | `LedgerReadRepository.mapAccount` for detail/list/balance reads                                                                                  | `FinancialAccountResponse.from` across query and command services; `LedgerReadRepository.findBalance/toBalance` calculations                             | Yes. It is reused by multiple workflows and supplies account kind, policy, limit, coverage, and currency inputs to balance calculation.                            | **KEEP**        | Retain the model and factory; remove only the unused `nameNormalized` component and unnecessary selected value. SQL ordering still uses `a.name_normalized`. |
| `BalanceView`                                       | `LedgerReadRepository.findBalance/toBalance`                                                                                                     | `BalanceResponse.from`                                                                                                                                   | Yes. It is a computed aggregate with coverage, projection state, watermark, account-kind balance decomposition, policy breach, and latest reconciliation metadata. | **KEEP**        | No structural change.                                                                                                                                        |
| `LastReconciliationSummaryView`                     | `ReconciliationReadRepository.findLatestSummary/mapSummary` over the latest non-superseded derived lifecycle row                                 | `LedgerReadRepository.findBalance/toBalance`, then nested balance response mapping                                                                       | Yes. It is a compact derived lifecycle aggregate feeding another application/query aggregate.                                                                      | **KEEP**        | Retain the view and `LastReconciliationSummaryResponse.from`.                                                                                                |
| `ReconciliationPreviewView`                         | `ReconciliationReadRepository.findPreview/mapPreview` combines SQL aggregates with statement inputs and derived differences/resolutions/warnings | `ReconciliationCommandService` preview output, coverage/opening/resolution eligibility, adjustment amount, counts, and persisted reconciliation creation | Yes. Application logic operates on its financial values.                                                                                                           | **KEEP**        | Retain the model, immutable lists, mapper, and response factory.                                                                                             |
| `DeviceSessionFamilyRecord`                         | `DeviceSessionReadRepository.mapFamilyRow` for list and detail family aggregation                                                                | `DeviceSessionQueryService` list/detail and `DeviceSessionResponse.from` lifecycle/status calculation                                                    | Yes. It represents a multi-generation logical session-family aggregate reused by two queries and status logic.                                                     | **KEEP**        | No structural change.                                                                                                                                        |
| `ReferenceCatalogReadRepository.InstrumentView`     | Detail and search compose one `InstrumentRow` with bounded ordered aliases                                                                       | `InstrumentResponse.from` and `InstrumentSummaryResponse.from`                                                                                           | Yes. It is a shared row-plus-alias multi-row aggregate with two materially different HTTP consumers.                                                               | **KEEP**        | Retain it; trim only unused normalized components from its underlying row records.                                                                           |
| `PostingView`, `ActivityView`, `ReconciliationView` | None                                                                                                                                             | None                                                                                                                                                     | PR-024 already proved them endpoint-only and removed them.                                                                                                         | **KEEP ABSENT** | Do not recreate a replacement view/result/DTO.                                                                                                               |

### Response factory audit

| Factory group                                                                               | Source value                                                                        | Mapping value                                                                                                                  | Decision                                                                                                        |
| ------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------- |
| `TransferPreviewResponse.from`                                                              | `TransferPreviewView` exists only for this call                                     | Field-for-field copy with one naming difference                                                                                | **REMOVE WITH SOURCE MODEL**.                                                                                   |
| `CountryResponse.from`                                                                      | `CountryRow` exists only between one JdbcClient query and this response             | Field-for-field copy                                                                                                           | **REMOVE WITH SOURCE MODEL**; repository returns the response record directly.                                  |
| `CurrencyResponse.from`                                                                     | `CurrencyRow` exists only between one JdbcClient query and this response            | Field-for-field copy with widening of the small integer component                                                              | **REMOVE WITH SOURCE MODEL**; repository maps directly and preserves the public integer field.                  |
| `MarketResponse.from`                                                                       | `MarketRow` exists only between one aggregate SQL query and this response           | Field-for-field copy                                                                                                           | **REMOVE WITH SOURCE MODEL**; keep the meaningful SQL-array mapping phase but target `MarketResponse` directly. |
| `FinancialAccountResponse.from`                                                             | Reused `FinancialAccountView` also feeds balance calculations and several workflows | Renames fields and derives `archived`                                                                                          | **KEEP**.                                                                                                       |
| `BalanceResponse.from`                                                                      | Computed `BalanceView`                                                              | Maps nested latest reconciliation and stable HTTP shape                                                                        | **KEEP**.                                                                                                       |
| `LastReconciliationSummaryResponse.from`                                                    | Derived latest-summary aggregate                                                    | Preserves stable nested HTTP representation                                                                                    | **KEEP**.                                                                                                       |
| `ReconciliationPreviewResponse.from`                                                        | Financial calculation model used by application logic                               | Canonicalizes seven `FinancialAmount` values and copies immutable lists                                                        | **KEEP**.                                                                                                       |
| `DeviceSessionResponse.from`                                                                | Reused multi-generation family aggregate                                            | Validates family expiry consistency and derives lifecycle status/ended time from one observed clock value                      | **KEEP**.                                                                                                       |
| `InstrumentResponse.from`, `InstrumentSummaryResponse.from`, `InstrumentAliasResponse.from` | Shared instrument/alias aggregate rows                                              | Produce two different public shapes, hide normalized/internal columns, derive owner-managed state, and preserve alias ordering | **KEEP**.                                                                                                       |
| `MarketCalendarResponse.from`, `MarketCalendarSessionResponse.from`                         | Header, stored rows, derived coverage, and missing dates                            | Cohesive multi-source composition; stored rows also feed coverage calculation                                                  | **KEEP**.                                                                                                       |
| `LocalLoginResponse.from`, `LocalRefreshResponse.from`                                      | Genuine authentication results                                                      | Add boundary-owned server time and optional refresh-token transport without exposing application internals                     | **KEEP**.                                                                                                       |

No response record is deleted. No JPA entity is serialized directly.

### Transient row/record audit

| Record                                        | Actual role                                                                                                        | Decision                                                                                                                                                                                   |
| --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `CountryRow`, `CurrencyRow`, `MarketRow`      | Endpoint-only copies immediately mapped to one response type                                                       | **SIMPLIFY**: delete the nested records and map the queries directly to `CountryResponse`, `CurrencyResponse`, and `MarketResponse`.                                                       |
| `MarketCalendarHeader`, `CalendarRow`         | Header plus stored rows used to validate timezone, compute stored/missing dates and coverage, and compose sessions | **KEEP**.                                                                                                                                                                                  |
| `InstrumentRow`, `AliasRow`, `InstrumentView` | Bounded two-query detail/search composition and two response shapes                                                | **KEEP**; remove unused `marketCodeNormalized`, `symbolNormalized`, `nameNormalized`, and `normalizedValue` components/select projections while retaining normalized SQL predicates/order. |
| `ProjectionRow`                               | Current projection balance plus exact watermark tuple used together in balance construction                        | **KEEP**.                                                                                                                                                                                  |
| `ActivityRow`, `PostingRow`                   | Two-query activity/posting composition, deterministic posting grouping, and detail/list reuse                      | **KEEP**.                                                                                                                                                                                  |
| `RefreshSessionOwnerProjection`               | Selective JPA projection used across refresh-session security control flow and its test seam                       | **KEEP**.                                                                                                                                                                                  |
| `DeviceSessionFamilyRecord`                   | Multi-row session lifecycle aggregate                                                                              | **KEEP**.                                                                                                                                                                                  |
| `StatementValues`                             | Reused normalized/parsed statement concept across preview, commit, correction, comparison, and persistence         | **KEEP**.                                                                                                                                                                                  |

The meaningful mapper helpers `mapAccount`, `mapActivityRow`, `mapActivities`, `findPostings`, `toBalance`, `mapPreview`, `mapDetail`, `mapSummary`, `mapFamilyRow`, `mapInstrumentRow`, and `mapAliasRow` remain. They group rows, derive values, normalize database types, establish deterministic composition, or serve multiple operations. `mapMarketRow` changes its target/name to direct `MarketResponse` mapping; the SQL-array conversion phase remains because it is substantive.

### Fingerprint audit

`CanonicalFingerprint`, `values(...)`, `hash(...)`, SHA-256, Jackson serialization, linked insertion order, and every operation scope remain unchanged.

| Workflow                  | Current ordered construction                                                                                                                                                                                                       | Risk                                                   | Decision                                                                                       |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------ | ---------------------------------------------------------------------------------------------- |
| account create            | Existing `requestHash` method: `name`, `kind`, `trackingMode`, `currency`, `timeZone`, `policy`, `authorizedLimit`, `openingAmount`, `openingEffectiveAt`                                                                          | High consequence; already isolated and explicit        | **KEEP EXISTING EXTRACTION** unchanged. Do not rename merely for style.                        |
| cash activity             | Inline: `accountId`, `activityType`, `amount`, `recordingMode`, `effectiveAt`, `confirmPolicyBreach`, `expectedBalanceVersion`                                                                                                     | High; long block interrupts command orchestration      | **EXTRACT** `cashActivityFingerprint(...)` with the exact same values, conversions, and order. |
| activity reversal         | Inline: `activityId`, `correctionReason`                                                                                                                                                                                           | High consequence but tiny/readable surface             | **KEEP INLINE**.                                                                               |
| account archive           | Inline: `accountId`, `version`                                                                                                                                                                                                     | High consequence but tiny/readable surface             | **KEEP INLINE**.                                                                               |
| opening correction        | Inline: `accountId`, `amount`, `effectiveAt`, `correctionReason`, `version`                                                                                                                                                        | High; sits inside a long reversal/replacement workflow | **EXTRACT** `openingCorrectionFingerprint(...)` exactly.                                       |
| transfer                  | Inline: `sourceAccountId`, `destinationAccountId`, `amount`, `recordingMode`, `effectiveAt`, `confirmPolicyBreach`, `expectedSourceBalanceVersion`, `expectedDestinationBalanceVersion`                                            | High; long block obscures transfer orchestration       | **EXTRACT** `transferFingerprint(...)` exactly.                                                |
| account metadata          | Inline: `accountId`, `name`, `timeZone`, `version`                                                                                                                                                                                 | Moderate surface and still locally readable            | **KEEP INLINE**.                                                                               |
| account policy            | Inline: `accountId`, `policy`, `authorizedLimit`, `version`                                                                                                                                                                        | Moderate surface and still locally readable            | **KEEP INLINE**.                                                                               |
| reconciliation commit     | Inline: `accountId`, `statementReference`, `statementOpeningAt`, `statementClosingAt`, `statementOpeningBalance`, `statementClosingBalance`, `expectedBalanceVersion`, `resolution`, `adjustmentReason`                            | Highest; large block plus nullable trim semantics      | **EXTRACT** `reconciliationCommitFingerprint(...)` with the complete ordered map explicit.     |
| reconciliation correction | Inline: `reconciliationId`, `statementReference`, `statementOpeningAt`, `statementClosingAt`, `statementOpeningBalance`, `statementClosingBalance`, `expectedBalanceVersion`, `resolution`, `adjustmentReason`, `correctionReason` | Highest; largest block plus nullable trim semantics    | **EXTRACT** `reconciliationCorrectionFingerprint(...)` with the complete ordered map explicit. |

Every extracted method returns the final hash and contains the full `fingerprint.hash(fingerprint.values(...))` expression. Do not split key/value production across shared helpers or collections.

Workflow-specific fingerprint methods own canonical identity construction only. They must receive values already parsed, validated, normalized, or canonicalized by the owning workflow where those values already exist. Do not move request parsing, financial amount parsing, validation, clock observation, policy calculation, locking, domain mutation, or other workflow logic into fingerprint methods. For example, `openingCorrectionFingerprint(...)` receives the already-prepared `UUID`, `FinancialAmount`, `Instant`, correction reason, and version; it does not parse or validate the request itself.

The extraction must preserve exactly:

- operation scopes and client request IDs used by locking/replay/save;
- key spelling and insertion order;
- UUID `.toString()` representation;
- enum `.name()` representation;
- instant `.toString()` representation;
- `FinancialAmount.canonical()` values;
- request trimming and nullable handling;
- primitive/boxed/null values;
- hash algorithm, serialization, and replay scope.

### Reconciliation `normalized` decision

`ReconciliationCommandService.normalized` has exactly two consumers, both inside the commit/correction fingerprint blocks, and implements only:

```java
value == null ? null : value.trim()
```

Delete the helper when the two workflow-specific fingerprint methods are introduced. Each method spells the exact ternary directly beside the `adjustmentReason` key. Do not use `Objects`, `Optional`, blank-to-null conversion, a shared normalization helper, or any change to null-versus-empty behavior.

### Historical adjustment policy decision

`historicalAdjustmentDecision` has one consumer. That call obtains its evaluation from:

```java
LedgerPolicyEvaluator.evaluate(..., RecordingMode.HISTORICAL_FACT, false)
```

The evaluator's historical branch returns only `PolicyDecision.ALLOWED` or `PolicyDecision.HISTORICAL_BREACH_RECORDED`. `Activity.reconciliationAdjustment` independently accepts exactly those same historical decisions. Therefore pass `evaluation.decision()` directly and delete `historicalAdjustmentDecision`.

Do not move the ternary to `PolicyDecision`, change the evaluator, widen the allowed set, or weaken the domain factory guard.

### Service and repository granularity

- Keep `CashActivityCommandService`, `CashTransferService`, `ReconciliationCommandService`, financial-account services, `DeviceSessionQueryService`, and reference query/search services.
- Keep `LedgerReadRepository`, `ReconciliationReadRepository`, `DeviceSessionReadRepository`, and `ReferenceCatalogReadRepository` as their existing query-family boundaries.
- Removing `TransferPreviewView` does not make `CashTransferService` a pass-through; it still owns transfer eligibility, policy evaluation, locking, postings, projections, and idempotency.
- Direct reference response mapping does not make `ReferenceCatalogQueryService` redundant; it remains the transaction and calendar validation/coverage boundary.
- No service or repository merge, split, interface, adapter, mapper, or replacement model is justified.

## Scope

1. Delete `TransferPreviewView` and construct `TransferPreviewResponse` directly from the already-calculated preview values in `CashTransferService.preview`.
2. Delete `TransferPreviewResponse.from` and its source-model import. Preserve the exact thirteen response components, names, values, policy decisions, versions, and `allowed` calculation.
3. Change active country, currency, and market reference queries to return their stable response records directly.
4. Delete `CountryRow`, `CurrencyRow`, and `MarketRow`; delete `CountryResponse.from`, `CurrencyResponse.from`, and `MarketResponse.from`.
5. Preserve immutable outer list boundaries when `ReferenceCatalogQueryService` no longer obtains them through `Stream.toList()`. Keep market timezone validation before returning market responses.
6. Retarget the meaningful market SQL-array mapper directly to `MarketResponse`; retain quotation-currency ordering, primary quotation currency, immutable component lists, active/source fields, and normalized market ordering.
7. Retain every other response factory listed as KEEP.
8. Remove the unused `FinancialAccountView.nameNormalized` component and its selected/mapped value. Keep `a.name_normalized` in account ordering and uniqueness semantics.
9. Remove unused `InstrumentRow.marketCodeNormalized`, `InstrumentRow.symbolNormalized`, `InstrumentRow.nameNormalized`, and `AliasRow.normalizedValue` components and unnecessary SELECT-list values. Keep every normalized SQL filter, prefix match, alias order, and deterministic instrument order unchanged.
10. Retain all genuine read/aggregate/transient records listed as KEEP.
11. Extract the cash-activity, transfer, opening-correction, reconciliation-commit, and reconciliation-correction canonical blocks behind the exact workflow-specific methods identified in the audit.
12. Keep account-create extraction and the five short inline fingerprints unchanged in placement and content.
13. Delete `ReconciliationCommandService.normalized` only by placing the exact nullable-trim ternary inside both reconciliation fingerprint methods.
14. Pass `evaluation.decision()` directly to `Activity.reconciliationAdjustment` and delete `historicalAdjustmentDecision`, relying on the verified evaluator contract and retained domain guard.
15. Update only existing behavior tests needed to prove the removed transfer mapping and extracted transfer/opening fingerprints. Preserve the exhaustive existing reconciliation fingerprint tests.
16. Preserve all accounting, owner, security, transaction, concurrency, rollback, idempotency, pagination, query-count, deterministic-order, and response-shape behavior.
17. openingCorrectionFingerprint(...) must receive already-normalized/canonical workflow values where those values already exist; the extraction must not move parsing, trimming, amount canonicalization, clock observation, or validation into the fingerprint method

## Deletion plan

### Production file

Delete:

```text
src/main/java/dev/canverse/stocks/ledger/application/model/TransferPreviewView.java
```

### Nested production records

Delete from `ReferenceCatalogReadRepository`:

- `CountryRow`
- `CurrencyRow`
- `MarketRow`

### Response factories

Delete:

- `TransferPreviewResponse.from`
- `CountryResponse.from`
- `CurrencyResponse.from`
- `MarketResponse.from`

### Private helpers

Delete:

- `ReconciliationCommandService.normalized`
- `ReconciliationCommandService.historicalAdjustmentDecision`

Replace `mapMarketRow` with a direct response-target mapper; do not delete the SQL-array mapping phase.

Delete no test class, response record, domain model, JPA entity, repository, service, aggregate/query model, or platform fingerprint primitive.

## Explicit non-goals

- No Cleanup C validation/error work.
- No pagination, cursor, sort, page-size, response-envelope, or query-count redesign.
- No schema, migration, table, column, constraint, trigger, index, seed, or data change.
- No financial-domain, posting, balance, reconciliation, policy, opening-state, coverage, projection, correction, or lifecycle change.
- No new investing functionality and no R4 implementation or specification expansion.
- No service architecture redesign or broad service/repository merging.
- No repository architecture redesign.
- No MyBatis migration or read-persistence migration audit.
- No JdbcClient replacement, jOOQ, QueryDSL, parallel read path, or JPA redesign.
- No JPA entity serialization and no removal of stable HTTP response records.
- No frontend or `src/main/web` change.
- No OpenAPI tooling, annotations, or generated schema.
- No async/job/queue infrastructure.
- No generic DTO/read-model/result/mapper framework.
- No MapStruct introduction.
- No reflection-based or serializer-discovered mapping.
- No `hashValues(Object...)`, `FingerprintBuilder`, `FingerprintCommand`, fingerprint command hierarchy, annotations, reflection, Map-based unordered construction, or fingerprint mini-framework.
- No change to `CanonicalFingerprint`, ObjectMapper configuration, hash algorithm, canonical JSON behavior, or operation scopes.
- No speculative abstraction or cleanup for file/method-count reduction.
- No Git operation; the user owns acceptance, commit, and pointer transitions.

## Database changes

Migration(s):

- None.

Tables/columns/constraints/indexes introduced or changed:

- None.

SQL changes are limited to removing unused SELECT-list projections. All predicates, joins, grouping, aliases, filters, owner scope, ordering, limits/offsets, locks, and aggregate expressions remain unchanged.

## Application and mapping changes

- `CashTransferService.preview` returns the stable HTTP response directly after completing the same domain/application calculation. This is an accepted pragmatic boundary and does not expose a persistence entity.
- `ReferenceCatalogReadRepository` may return `CountryResponse`, `CurrencyResponse`, and `MarketResponse` directly because each query exists solely for its endpoint, no application calculation consumes a distinct read model, and the repository already returns HTTP response records for endpoint-specific instrument search.
- `ReferenceCatalogQueryService` retains transaction ownership, immutable result boundaries, market timezone validation, and calendar calculation/composition.
- `FinancialAccountView`, `BalanceView`, `LastReconciliationSummaryView`, `ReconciliationPreviewView`, `DeviceSessionFamilyRecord`, and `InstrumentView` remain in their current semantic roles.
- Response factories remain on genuine entity/read-model boundaries.
- Workflow-specific fingerprint methods are private instance methods because they use the existing injected `CanonicalFingerprint`. They are not exposed, shared, generalized, or represented as new types.
- No application type is introduced solely to avoid a repository import of a response record.

## API contract

No endpoint, route, request parameter/body, response record, JSON field, status code, error code, header, authentication rule, pagination contract, or serialization contract changes.

Specifically:

- transfer preview retains the exact `TransferPreviewResponse` JSON shape and values;
- country, currency, and market endpoints retain their exact top-level arrays, item fields, order, source flags, currency arrays, and null handling;
- account, balance, reconciliation preview/detail/list, instrument detail/search, calendar, session, login, and refresh contracts remain unchanged;
- JPA entities remain excluded from JSON serialization.

## Preserved accounting, security, and idempotency invariants

- Apply `accounting-contract.md` unchanged.
- Posted activities, postings, and reconciliation evidence remain immutable.
- Deposit/withdrawal signs, transfer equal-and-opposite neutrality, adjustment signs, reversal, supersession, opening correction, and balance decomposition remain unchanged.
- Requested/actual as-of, coverage, projection watermark/status, policy breach, and last-reconciliation semantics remain unchanged.
- Reconciliation opening/closing arithmetic, admissible resolution, warnings, posting counts, adjustment eligibility, lifecycle, staleness, and archived historical behavior remain unchanged.
- The historical evaluator result passed to a reconciliation adjustment remains exactly `ALLOWED` or `HISTORICAL_BREACH_RECORDED`; the domain guard remains authoritative.
- Owner scope remains in every existing query, lock, and follow-up composition query. Cross-owner not-found behavior remains unchanged.
- Bearer authentication, typed principals, session-family aggregation/status, no-store headers, statelessness, cookies, refresh lifecycle, and security events remain unchanged.
- Fingerprint keys, values, insertion order, normalization, null representation, decimal canonicalization, UUID/enum/instant string representation, SHA-256 result, operation scope, command sequence, lock ordering, replay lookup, persisted response snapshot, and conflict behavior remain unchanged.
- Repeating the same client request and semantic values returns the original result and creates one economic fact; materially changed reuse remains `IDEMPOTENCY_CONFLICT`.
- Transaction boundaries, flush ordering, projection mutation, optimistic version behavior, concurrency behavior, and rollback remain unchanged.

## Test migration

### Model and mapping simplification

- Extend the existing `CashActivityServiceTest` transfer-preview scenario to assert every `TransferPreviewResponse` component, not only balances and `allowed`, so direct construction proves the complete removed mapping contract.
- Keep the existing `CashActivityHttpTest` transfer preview/commit response assertions unchanged; the response record itself and its serialized component names do not change.
- Reuse `ReferenceCatalogQueryTest` and the exact-array/JSON assertions in `ReferenceCatalogHttpTest` to prove country, currency, and market values/order remain identical. No new reference mapping test class is needed.
- Reuse existing financial-account list/detail/balance tests to prove removing the unused `nameNormalized` component changes no public or calculated field.
- Reuse existing instrument detail/search/filter/order/alias/query-count tests to prove trimming unused normalized row components changes no visibility, search, ordering, alias, or response behavior.
- Do not add tests for deleted class names, record component counts, mapper names, or private method names.

### Fingerprint readability

- Preserve the existing cash-activity exact replay/material-reuse conflict tests.
- Add one behavior-focused transfer test in `CashActivityServiceTest`: exact retry replays the original activity without a second posting/projection effect, while reuse of the same key with one materially changed semantic field conflicts.
- Add one behavior-focused opening-correction test in `FinancialAccountServiceTest`: exact retry returns the original corrected account result despite later state, while materially changed reuse conflicts and creates no additional reversal/replacement.
- Preserve `LedgerReconciliationServiceTest.commitRejectsEveryChangedFingerprintField`, `correctionRejectsEveryChangedFingerprintField`, exact retry after later state change, and numerically equivalent decimal-scale replay coverage.
- Retain account-create replay/conflict tests and all short-inline fingerprint workflow tests unchanged.
- Do not call or reflect on private fingerprint helper names. A hard-coded hash snapshot is not required when the complete ordered source construction remains reviewable and the behavior suites prove replay/conflict semantics.

### Historical adjustment pass-through

- In existing reconciliation service coverage, assert that a historical adjustment which remains nonnegative records `ALLOWED` and a historical adjustment which produces a negative balance records `HISTORICAL_BREACH_RECORDED`.
- Preserve the `Activity.reconciliationAdjustment` domain invariant test/coverage that rejects invalid historical decisions.

### Preserve all other coverage

- Preserve all accounting golden tests, migration/mapping tests, idempotency tests, concurrency tests, rollback tests, owner-isolation tests, bearer/security/session tests, pagination/order tests, and query-count assertions.
- Modify no unrelated test merely to mirror implementation structure.

## Risk analysis

- **Fingerprint drift:** a moved key, renamed key, changed conversion, changed nullable trim, or altered order changes canonical identity even when business data is the same. Move each block intact, keep every ordered pair visible in one method, and review old/new expressions side by side.
- **Null-versus-empty drift:** replacing the deferred ternary with blank normalization, `Optional`, or unconditional trim changes replay identity. Spell `value == null ? null : value.trim()` exactly in both reconciliation methods.
- **Replay-scope drift:** changing operation scope, client ID, lock order, or save scope would alter idempotency beyond readability. Those call sites remain outside extracted methods and unchanged.
- **Transfer response drift:** direct construction can transpose source/destination values or versions. Assert every response component and retain HTTP serialization coverage.
- **Reference response drift:** direct repository mapping can widen/narrow the currency minor unit incorrectly, lose list immutability, skip market timezone validation, reorder quotation currencies, or change null fields. Retain explicit mapping where conversion/composition is nontrivial and run exact query/HTTP tests.
- **Over-trimming query projections:** normalized database columns remain required in WHERE/ORDER BY even when no longer returned as Java components. Remove only SELECT-list/accessor values proven unused by source search.
- **Aggregate over-deletion:** last-reconciliation, session-family, instrument-alias, activity/posting, preview, and balance models protect real composition/calculation semantics. Keep the audited set and stop if implementation suggests deleting another genuine model.
- **Policy contract drift:** direct passing is safe only because the evaluator call uses `HISTORICAL_FACT` and the domain factory accepts the identical two-value set. Do not alter either contract.
- **Boundary leakage:** direct response mapping is allowed only on the three endpoint-specific reference queries and transfer preview; no JPA entity, reusable query model, or application calculation model becomes an HTTP type.
- **Scope creep:** service/repository consolidation, persistence migration, schema work, validation cleanup, frontend work, and investing remain excluded.

## Required tests

### Pure/domain

- Existing financial amount, account, activity, posting, policy-decision, and reconciliation invariants remain green.
- `Activity.reconciliationAdjustment` continues to accept only the two historical decisions.
- No pure test is added for private helper structure.

### PostgreSQL/Testcontainers

- Transfer preview returns every unchanged calculated field; transfer exact replay creates one activity and two postings and applies projection movement once; materially changed reuse conflicts.
- Opening correction exact replay creates only the original reversal/replacement pair and projection effect; materially changed reuse conflicts.
- Account list/detail/balance queries retain response values, coverage, ordering, and balance calculations after unused projection removal.
- Country/currency/market queries retain exact values, deterministic ordering, quotation-currency composition, primary currency, active/source fields, and timezone validation.
- Instrument detail/search retains owner/global visibility, filters, normalized SQL predicates/orders, bounded alias loading/order, response shapes, and query counts.
- Reconciliation commit/correction exact replay, every changed fingerprint field, decimal-scale equivalence, adjustment policy result, persistence, and rollback remain green.
- Existing V1-V4 migration/schema/index expectations remain unchanged.

### HTTP/security

- Transfer preview JSON remains exact, with no new wrapper or missing/renamed component.
- Country, currency, and market arrays remain exact.
- Financial account, balance, reconciliation, instrument, calendar, and session response contracts remain unchanged.
- Existing bearer authentication, typed principal, owner isolation, cross-owner not-found, no-store, statelessness, cookie/session, and trace-correlated error tests remain green.

## Acceptance criteria

1. `TransferPreviewView` is deleted and no replacement application view/result/DTO/mapper is introduced.
2. `CashTransferService.preview` constructs the unchanged `TransferPreviewResponse` directly, and complete behavior/HTTP assertions pass.
3. `CountryRow`, `CurrencyRow`, and `MarketRow` plus their three response factories are deleted; the endpoint-specific repository queries return stable response records directly.
4. Country/currency/market arrays, values, list immutability, market timezone validation, quotation-currency composition/order, and public JSON are unchanged.
5. `FinancialAccountView`, `BalanceView`, `LastReconciliationSummaryView`, `ReconciliationPreviewView`, `DeviceSessionFamilyRecord`, and `InstrumentView` remain.
6. `PostingView`, `ActivityView`, and `ReconciliationView` remain absent.
7. The unused `FinancialAccountView.nameNormalized` component/select value is removed without changing normalized SQL ordering.
8. The four unused normalized instrument/alias row components/select values are removed while all normalized SQL predicates and ordering remain exact.
9. Calendar header/rows, instrument/alias rows, projection/activity/posting rows, refresh owner projection, session-family record, and reconciliation statement values remain for their audited semantics.
10. Only `TransferPreviewResponse.from`, `CountryResponse.from`, `CurrencyResponse.from`, and `MarketResponse.from` are removed. Every other audited response factory remains.
11. Cash activity, transfer, opening correction, reconciliation commit, and reconciliation correction use private workflow-specific fingerprint methods containing their complete ordered canonical construction.
12. Account create remains in its existing extracted method; activity reversal, account archive, account metadata, and account policy remain inline.
13. `CanonicalFingerprint` and its hash/values implementation are unchanged. No fingerprint framework, builder, command, reflection, annotation, implicit serialization, unordered map construction, or shared key/value helper is introduced.
14. Every fingerprint key, order, value, conversion, normalization, null representation, amount representation, UUID/enum/instant representation, scope, and SHA-256 result is unchanged.
15. `ReconciliationCommandService.normalized` is deleted, and both reconciliation fingerprint methods contain exactly `value == null ? null : value.trim()` semantics for adjustment reason.
16. `historicalAdjustmentDecision` is deleted; `evaluation.decision()` is passed directly only from the verified `HISTORICAL_FACT` evaluator call, and the domain guard remains unchanged.
17. Transfer and opening-correction exact replay/material-reuse tests pass; existing cash-activity, account-create, and exhaustive reconciliation idempotency tests remain green.
18. No service or repository is merged, split, replaced, or redesigned.
19. No response record or JPA entity is deleted/exposed, and no API contract changes.
20. No accounting, reconciliation, owner-scope, security, idempotency, concurrency, rollback, transaction, pagination, persistence, schema, dependency, configuration, frontend, or R4 behavior changes.
21. No MyBatis/read-persistence migration or audit is included.
22. Focused tests, the full suite, Maven `verify`, and Spotless pass with no unresolved `MUST FIX` findings.
23. After Cleanup D is accepted, stop cleanup work and return to R4 investing. Do not discover or activate Cleanup E.

Any additional cleanup discovered during implementation is ordinary technical debt and is deferred unless required for correctness or proven to make R4 materially harder.

## Documentation completion

Before this implementation unit is considered complete:

1. PR-026 must be accepted/committed by the user before PR-027 activation.
2. On explicit user activation, set PR-027 to `ACTIVE`, point `docs/implementation/CURRENT.md` to this specification, and update `docs/implementation/STATE.md` with PR-026 acceptance and Cleanup D as the active boundary.
3. After successful implementation verification, update `STATE.md` with current reality only: the exact redundant models/factories removed, genuine read models retained, workflow-specific fingerprint placement, unchanged canonical identity, and Cleanup D as the final cleanup before R4.
4. Replace superseded current statements; do not append detailed helper inventories or a cleanup history.
5. Update `docs/review/progress-report.md` because completion of the final planned cleanup and return to R4 is a material project checkpoint. Do not design or activate an R4 PR there.
6. Update `backend-master-plan.md`, `accounting-contract.md`, or coding standards only if implementation discovers a genuine authority conflict. No routine contract rewrite is expected.
7. Store reusable Windows/sandbox/Maven/Docker/Testcontainers/output lessons only in `docs/engineering/codex-command-playbook.md`.
8. Record exact implementation changes, deviations, decisions, and verification in this specification's Completion Record.
9. Leave `CURRENT.md` pointed at PR-027 until the user accepts the implementation and controls the next transition.
10. After Cleanup D is accepted, stop cleanup work and return to R4 investing. Do not create Cleanup E; defer incidental cleanup unless it is required for correctness or materially blocks R4.

## Verification commands

Use the command playbook before retrying a known Windows, Maven, Docker/Testcontainers, or output failure.

Focused behavior gate:

```powershell
.\mvnw.cmd "-Dtest=FinancialAccountServiceTest,FinancialAccountHttpTest,CashActivityServiceTest,CashActivityHttpTest,LedgerReconciliationServiceTest,LedgerReconciliationHttpTest,ReferenceCatalogQueryTest,ReferenceCatalogHttpTest,ApiBearerSecurityHttpTest" test
```

Then:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spotless:check
```

Source audits:

```powershell
rg -n "TransferPreviewView|CountryRow|CurrencyRow|MarketRow|historicalAdjustmentDecision|private static String normalized" src/main/java src/test/java
rg -n "PostingView|ActivityView|ReconciliationView" src/main/java src/test/java
rg -n "FinancialAccountView|BalanceView|LastReconciliationSummaryView|ReconciliationPreviewView|DeviceSessionFamilyRecord|InstrumentView" src/main/java src/test/java
rg -n "static .* from\(" src/main/java/dev/canverse/stocks/ledger/web/response src/main/java/dev/canverse/stocks/identity/web/response src/main/java/dev/canverse/stocks/reference/web/response
rg -n "CanonicalFingerprint|fingerprint\.values\(|fingerprint\.hash\(" src/main/java/dev/canverse/stocks/ledger src/main/java/dev/canverse/stocks/platform
rg -n "cashActivityFingerprint|transferFingerprint|openingCorrectionFingerprint|reconciliationCommitFingerprint|reconciliationCorrectionFingerprint" src/main/java/dev/canverse/stocks/ledger/application
rg -n "market_code_normalized|symbol_normalized|name_normalized|alias_normalized" src/main/java/dev/canverse/stocks/reference/infrastructure/ReferenceCatalogReadRepository.java
```

Interpretation:

- The first search must return no match.
- `PostingView`, `ActivityView`, and `ReconciliationView` must remain absent.
- The retained-model search must show the genuine producer/consumer paths from the audit.
- The factory search must show every KEEP factory and none of the four removed factories.
- Fingerprint construction must still use the unchanged platform primitive. The five named workflow methods must each show one complete explicit ordered construction; short audited fingerprints and account create remain in their specified placement.
- Normalized instrument/reference columns must remain in SQL predicates/order clauses even after their unused Java SELECT projections/components are removed.

## Completion record

Fill this before marking the PR complete.

### Implemented

- Activated PR-027, pointed `CURRENT.md` to it, and recorded PR-026 acceptance/commit plus Cleanup C completion in `STATE.md`; PR-027 remains `ACTIVE` for user acceptance.
- Deleted `TransferPreviewView` and its response factory; `CashTransferService.preview` now constructs the unchanged complete `TransferPreviewResponse` directly.
- Deleted the three endpoint-only reference row records and their response factories; country, currency, and market queries now return immutable response records directly while preserving values, ordering, timezone validation, and PostgreSQL array handling.
- Removed only the unused financial-account, instrument, and alias normalized read projections/components; normalized SQL predicates and ordering remain in place, and genuine read models remain.
- Extracted exactly five private workflow-specific fingerprint methods for cash activity, transfer, opening correction, reconciliation commit, and reconciliation correction. Their complete ordered canonical values are unchanged, and the reconciliation historical policy decision is passed through directly from the verified evaluator result.
- Extended transfer preview assertions to the complete response and added transfer replay/conflict coverage; extended opening-correction coverage to prove exact replay, one projection effect, and changed-request conflict behavior.
- Added the existing positive historical-adjustment assertion that reads the persisted activity policy decision and requires `ALLOWED`; the existing negative historical-adjustment test remains unchanged and requires `HISTORICAL_BREACH_RECORDED`.
- Corrected the current progress-report coverage wording to `bounded pageable instrument search and filter binding`.

### Deviations from specification

- No implementation-scope deviations. Docker Desktop/Testcontainers was available for final verification on 2026-09-07, and all required PostgreSQL-backed gates ran against the final working tree.

### New decisions

- No new product or accounting decisions. Fingerprint helpers receive already parsed, validated, normalized, or canonicalized workflow values and contain identity construction only, as required by the specification.

### Tests executed

- Focused gate: `.\mvnw.cmd "-Dtest=FinancialAccountServiceTest,FinancialAccountHttpTest,CashActivityServiceTest,CashActivityHttpTest,LedgerReconciliationServiceTest,LedgerReconciliationHttpTest,ReferenceCatalogQueryTest,ReferenceCatalogHttpTest,ApiBearerSecurityHttpTest" test` — passed on 2026-09-07 against the final working tree with Docker Desktop/Testcontainers PostgreSQL 17: 99 tests passed, 0 failures, 0 errors, 0 skips.
- Full suite: `.\mvnw.cmd test` — passed against the final working tree: 371 tests passed, 0 failures, 0 errors, 0 skips.
- Maven verification: `.\mvnw.cmd verify` — passed against the final working tree: 371 tests passed, 0 failures, 0 errors, 0 skips; packaging and Spotless completed successfully.
- Formatting: `.\mvnw.cmd spotless:check` — passed; Spotless reported 261 Java files clean (198 production and 63 test), and also passed inside `verify`.
- Docker/Testcontainers: Docker Desktop local `desktop-linux` engine was available; PostgreSQL 17 containers started successfully. No required tests were skipped, mocked, replaced, or disabled.
- Static audits: passed; deleted symbols/helpers and `PostingView`, `ActivityView`, and `ReconciliationView` are absent; genuine retained models and KEEP factories are present, while the four removed response factories are absent; exactly five named workflow fingerprint methods remain; normalized SQL predicates/order clauses remain; `CanonicalFingerprint` is unchanged; only approved unused SELECT projections are removed.
- Java file counts: 198 production, 63 test, 261 total.
- `git diff --check`: passed with no whitespace errors.

### Follow-up work

- User review/acceptance of PR-027, followed by the user-controlled lifecycle transition. Do not activate Cleanup E or R4 from this unit.
