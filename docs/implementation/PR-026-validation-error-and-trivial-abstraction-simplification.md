# PR-026 - Cleanup C validation, error, and trivial-abstraction simplification

Status: **ACTIVE**

## Goal

Confine generic field-oriented validation failures to HTTP/request-shape problems, give the ledger's future-time business rule one stable capability error, and remove low-value validation/normalization wrappers without changing any financial, reconciliation, ownership, security, persistence, pagination, or fingerprint behavior.

## Capability and review boundary

- Coherent capability: the only current business condition emitted through `ValidationErrors.invalidField(...)` is the ledger-wide prohibition on future effective/as-of times. Its seven direct `LedgerTimingRules` consumers span the already-coupled account, cash activity, transfer, balance, and reconciliation workflows, so changing the error contract and deleting that helper belongs in one unit.
- Combined behaviors: adding the capability code, converting every future-time path, preserving exact pre-write/pre-query validation order, deleting `LedgerTimingRules`, inlining nearby one-condition wrappers, retaining structural request validation, and migrating behavior-focused error assertions must land together. Splitting them would leave either a partially converted public contract or a deletion-only follow-up.
- Reconciliation structural validation remains layered correctly: the HTTP boundary retains field-oriented request feedback, while non-controller execution paths must retain an independent ordering invariant and must not rely exclusively on controller `validate()`.
- Small adjacent cleanup: one one-use reference exception factory may be inlined because the reference audit found no error-classification redesign.
- Identity and platform validation/security helpers remain untouched because they express lifecycle, security-event, abuse-protection, token-configuration, or exception-contract concepts.
- Excluded neighbor: Cleanup D owns View/Response mapping, fingerprint readability, and fingerprint-adjacent normalization cleanup. `ReconciliationCommandService.normalized` remains unchanged in Cleanup C.
- MyBatis/read-persistence work and R4 investing remain later independent decisions.
- Focused review: one reviewer can inspect one new ledger error constant, the affected public ledger temporal request contexts, one deleted helper class, local private-helper removals in the owning workflows/domain factory, and the existing ledger behavior tests. No schema, repository-query, transaction-boundary, service-ownership, central exception architecture, or fingerprint construction changes.
- Cleanup C fits one PR. The repository has 26 `ValidationErrors.invalidField(...)` call sites, but 25 express structural input concerns and do not require capability redesign; identity has zero consumers and reference has no business/domain misuse. A C1/C2 split would create a ledger PR plus an unnecessary identity/reference cleanup unit.

## Source documents

- `AGENTS.md` - planning/implementation isolation, active-spec lifecycle, Git ownership, repository constraints, and context maintenance.
- `docs/implementation/README.md` and `docs/implementation/PR-TEMPLATE.md` - specification structure and manual activation workflow.
- `docs/implementation/STATE.md` and `docs/implementation/CURRENT.md` - current repository reality and active implementation pointer.
- `docs/engineering/coding-standards.md` - sections 2-4, 8-11, 13, and 14: directness, validation ownership, stable errors, service boundaries, security, and behavior-focused tests.
- `docs/review/backend-master-plan.md` - current backend standardization conventions, API invariants, file economy, R1-R3, R16, and testing strategy.
- `docs/review/accounting-contract.md` - sections 3, 4, 6, 7, 13, 17, 18, and 21: injected clocks, economic time, opening coverage, immutable correction, balance meaning, projections, idempotency/concurrency, and stable API errors.
- `docs/implementation/PR-023-governing-simplicity-standards.md` - accepted authority for structural-versus-business validation, capability error codes, direct/local code, and concept-oriented extraction.
- `docs/implementation/PR-024-ledger-pagination-simplification.md` and `docs/implementation/PR-025-identity-reference-pagination-simplification.md` - accepted Cleanup B direction, endpoint-local sort validation, compact slice contracts, cursor deletion, and explicit separation of Cleanup C/D.

## Starting state

- PR-023 through PR-025 are implemented in the current repository state.
- PR-025 is accepted and committed, and PR-026 is active under the user's manual lifecycle control; Cleanup D and R4 remain inactive.
- The application has 18 controller `@Valid @RequestBody` boundaries, three nested/cascading `@Valid` request components, 20 request-record files with Jakarta constraints, and no production `@Validated` usage.
- No application service, repository, domain, infrastructure, or configuration method has a Bean Validation `@Valid` parameter.
- The only six production calls to request-record `validate()` are in controllers after `@Valid` binding.
- Response records use `@NotNull` only as outbound contract metadata under the coding standard; no response type is a Bean Validation entry point.
- `ValidationErrors.invalidField(...)` has 26 production call sites: 18 in ledger and eight in reference.
- Twenty-five are structural request/query concerns; one is the business rule implemented by `LedgerTimingRules.rejectFuture(...)`.
- `LedgerTimingRules` is a one-method, package-private class. It has seven direct consumers across six classes; reconciliation currently adds another local wrapper around it.
- `LedgerErrorCode` already has stable codes for account state/capability/currency/limits, insufficient funds, policy confirmation, idempotency, activity lifecycle, opening correction, reconciliation coverage/opening/resolution/supersession, and projection/account version conflicts.
- `LedgerErrorCode` currently has no code for rejecting future ledger time.
- `IdentityErrorCode` and `ReferenceErrorCode` already cover their currently implemented business conditions. Neither needs a new code for this unit.
- The centralized `AppException` and `GlobalExceptionHandler` already translate capability codes and structural `VALIDATION_FAILED` errors into the existing trace-correlated RFC 9457 contract. That architecture is not a cleanup target.
- Fingerprint construction and canonical-input normalization are unchanged in Cleanup C.

## Repository-backed audit

### Bean Validation and request validation boundary

| Current construct                                | Location / consumers                                                                                                                                       | Classification                                                                 | Proposed action                                                 |
| ------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ | --------------------------------------------------------------- |
| `@Validated`                                     | No production or test occurrence                                                                                                                           | Compliant absence                                                              | Keep absent; add no service-method validation mechanism.        |
| Controller `@Valid @RequestBody`                 | 18 methods across identity registration/login/refresh/logout, ledger account/activity/transfer/reconciliation, and reference manual-instrument controllers | HTTP structural boundary                                                       | Keep all.                                                       |
| Nested `@Valid`                                  | `CreateFinancialAccountRequest.openingState`; alias elements in `ManualInstrumentCreateRequest` and `ManualInstrumentUpdateRequest`                        | Structural cascading                                                           | Keep all.                                                       |
| Jakarta request constraints                      | 20 request files; no constraint import outside `web/request` or `web/response`                                                                             | HTTP shape, required values, malformed patterns, lengths, and ranges           | Keep. Do not translate domain/lifecycle rules into annotations. |
| Response `@NotNull`                              | 21 response files                                                                                                                                          | Outbound required-field contract metadata, not an inbound validation mechanism | Keep; outside Cleanup C behavior.                               |
| Service/repository/domain/config Bean Validation | None                                                                                                                                                       | Compliant absence                                                              | Keep absent.                                                    |
| Request `validate()` invocation                  | Six controller calls; no service invocation                                                                                                                | Controller-owned structural consistency                                        | Keep controller ownership.                                      |

### `ValidationErrors.invalidField(...)` consumers

The audit counts invocation sites, not runtime paths. Sort factories and the temporal helper each serve multiple callers but count once at their definition.

| Current construct                         | Location / consumers                                                                                                         | Classification                                                             | Proposed action                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| ----------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Unsupported resolved `Pageable.sort`      | `LedgerReadRepository.invalidSort`, `ReconciliationReadRepository.invalidSort`, `ReferenceCatalogReadRepository.invalidSort` | HTTP/request shape                                                         | **KEEP** `VALIDATION_FAILED` with field `sort`; keep each local sort-policy helper.                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| Manual-instrument normalized bounds       | `ManualInstrumentCreateRequest` (3 sites), `ManualInstrumentUpdateRequest` (2 sites)                                         | HTTP/request shape                                                         | **KEEP** field errors. Inline only the one-use `invalidSymbol()` exception factory; preserve normalized Unicode-length and duplicate-alias semantics.                                                                                                                                                                                                                                                                                                                                                                               |
| Reference endpoint-local query validation | `ReferenceCatalogQueryService.validateDateRange`, `InstrumentSearchService.normalizeQuery`                                   | HTTP query shape                                                           | **KEEP** generic validation and current range/query semantics.                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| Ledger amount parsing                     | `LedgerAmountParser.exact` and `positive`                                                                                    | HTTP decimal lexical/range shape translated to `FinancialAmount`           | **KEEP** shared parser and field errors; exact plain-decimal and positive parsing are genuine repeated operations.                                                                                                                                                                                                                                                                                                                                                                                                                  |
| Ledger IANA timezone translation          | `FinancialAccountOnboardingService.validateTimeZone`, `FinancialAccountSettingsService.validateTimeZone`                     | Malformed HTTP value with domain integrity recheck                         | **KEEP** field errors and `FinancialAccount.requireIanaTimeZone`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| Account opening-state request consistency | `CreateFinancialAccountRequest.validate`                                                                                     | Cross-field HTTP structural consistency                                    | **KEEP**: tracking mode and opening-state presence must agree.                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| Reconciliation request consistency        | `ReconciliationPreviewRequest`, `ReconciliationCommitRequest`, `ReconciliationCorrectionRequest`                             | Cross-field/conditional HTTP structural consistency                        | **KEEP** controller-invoked request checks.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| Reconciliation service period check       | `ReconciliationCommandService.statementValues`                                                                               | Duplicate HTTP-oriented representation of an underlying ordering invariant | **REMOVE the duplicate `ValidationErrors.invalidField` representation only after verifying that every non-controller execution path retains an independent ordering invariant. Preview must be checked explicitly because it may not construct `Reconciliation`. If preview has no downstream invariant guard, retain the minimum application/domain ordering check there without HTTP field metadata. Keep request-period validation for HTTP feedback and keep `Reconciliation.create` integrity validation for creation paths.** |
| Future financial effective/as-of time     | `LedgerTimingRules.rejectFuture`                                                                                             | Business/application rule                                                  | **CONVERT** to `AppException(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED)` and delete field/message/fallback metadata.                                                                                                                                                                                                                                                                                                                                                                                                                  |

All 26 original invocation sites must remain accounted for after implementation. Structural validation remains structural; future-time rejection becomes capability-coded; no business rule is silently dropped.

### Request-record `validate()` methods

| Request record                    | Current checks                                                                     | Classification                                                      | Proposed action                                  |
| --------------------------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------- | ------------------------------------------------ |
| `CreateFinancialAccountRequest`   | Opening state required for `FULL_LEDGER` and prohibited for `HOLDINGS_ONLY`        | **KEEP** - cross-field structural consistency                       | Keep controller call and generic field errors.   |
| `ReconciliationPreviewRequest`    | Opening instant precedes closing instant                                           | **KEEP** - cross-field structural consistency                       | Keep.                                            |
| `ReconciliationCommitRequest`     | Ordered period; adjustment reason required/prohibited according to resolution      | **KEEP** - cross-field/conditional structural consistency           | Keep cohesive period/reason phases.              |
| `ReconciliationCorrectionRequest` | Ordered period; adjustment reason consistency; nonblank supplied correction reason | **KEEP** - cross-field/conditional structural consistency           | Keep.                                            |
| `ManualInstrumentCreateRequest`   | Symbol lexical validity; normalized name/alias bounds; duplicate submitted aliases | **KEEP** - normalization-sensitive and duplicate-item request shape | Keep; inline only the trivial exception factory. |
| `ManualInstrumentUpdateRequest`   | Normalized name/alias bounds; duplicate submitted aliases                          | **KEEP** - normalization-sensitive and duplicate-item request shape | Keep unchanged.                                  |

All six remain structural and controller-owned.

No service may start calling request-record `validate()`.

### Reconciliation period invariant boundary

HTTP request validation and application/domain integrity serve different purposes.

The final structure must be:

```text
HTTP request
    ↓
request.validate()
    ↓
field-oriented VALIDATION_FAILED for malformed period

application/domain execution
    ↓
independent period-order invariant
    ↓
never relies exclusively on controller validation
```

For commit/correction paths, verify whether `Reconciliation.create` already supplies the independent invariant.

For preview, verify the complete execution path. If preview does not construct `Reconciliation` or pass through an equivalent invariant owner, retain the smallest local application/domain period check.

That retained internal guard:

- must not use `ValidationErrors.invalidField`;
- must not recreate HTTP field/message metadata;
- must not introduce a generic validation helper/framework;
- may use the existing domain invariant mechanism or a direct internal precondition appropriate to the execution boundary;
- must not change the public HTTP response for normal controller-originated malformed requests, because request validation should reject those first.

### Temporal validation helpers

| Current construct                            | Location / consumers                                                                                                                                                                         | Classification                                                                | Proposed action                                                                             |
| -------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| `LedgerTimingRules.rejectFuture`             | `CashActivityCommandService`, `CashTransferService`, `FinancialAccountLifecycleService`, `FinancialAccountOnboardingService`, `FinancialAccountQueryService`, `ReconciliationCommandService` | One-condition domain-named wrapper with no independent ledger model/algorithm | Delete class. Keep direct checks at workflow positions and throw `FUTURE_TIME_NOT_ALLOWED`. |
| `ReconciliationCommandService.rejectFuture`  | Opening/closing checks for preview, commit, correction                                                                                                                                       | Wrapper around wrapper                                                        | Delete. Observe clock once per workflow and compare both instants directly.                 |
| Generic `requireNotAfter`/validation utility | None                                                                                                                                                                                         | No demonstrated need                                                          | Do not introduce one.                                                                       |

`LedgerTimingRules` must not survive Cleanup C.

The rule itself remains in every current workflow.

### Private validation/helper audit

| Current construct                                                                                          | Location / consumers                                                                                                           | Classification                                            | Proposed action                                                         |
| ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------- | ----------------------------------------------------------------------- |
| `requireCashActivityType`, `requireAllowed`, `requireExpectedVersion`                                      | `CashActivityCommandService`; one call each                                                                                    | Trivial one-condition wrappers                            | **INLINE** at existing workflow positions.                              |
| `requireCashActionAccount`                                                                                 | `CashActivityCommandService`                                                                                                   | Cohesive archived/tracking/kind/funding eligibility phase | **KEEP**.                                                               |
| `validateTransferShape`                                                                                    | `CashTransferService`; preview and commit                                                                                      | Repeated cohesive transfer eligibility/currency phase     | **KEEP**.                                                               |
| `requireAllowed`, `requireExpectedVersion`                                                                 | `CashTransferService`; adjacent source/destination checks                                                                      | Navigation ceremony around direct conditions              | **CONSOLIDATE/INLINE** into local source/destination validation blocks. |
| `requireVersion`, `requireOpeningVersion`                                                                  | `FinancialAccountLifecycleService`                                                                                             | Trivial wrappers with distinct existing error semantics   | **INLINE** where account state is loaded.                               |
| `FinancialAccountSettingsService.requireVersion`                                                           | Metadata and policy workflows                                                                                                  | Repeated meaningful compare-and-swap condition            | **KEEP**.                                                               |
| `ReconciliationCommandService.requireReconciliationAccount`, `requireFullLedger`, `requireExpectedVersion` | Simple call sites inside fragmented validation chain                                                                           | Trivial eligibility/version wrappers                      | **INLINE** at current validation points.                                |
| `requireCoverage`, `requireOpeningContinuity`, `requireResolution`                                         | `ReconciliationCommandService`                                                                                                 | Meaningful financial invariants                           | **KEEP**.                                                               |
| `statementValues`, `comparison`, `writeReconciliation`, `reverseAdjustment`, `saveResult`                  | `ReconciliationCommandService`                                                                                                 | Cohesive conversion/query/transactional phases            | **KEEP**.                                                               |
| `ReconciliationCommandService.normalized`                                                                  | Nullable trimming used directly in fingerprint canonical inputs                                                                | Fingerprint-adjacent helper                               | **DEFER TO CLEANUP D. Preserve unchanged in Cleanup C.**                |
| `Reconciliation.requireAmount`                                                                             | Seven null-check calls in domain factory                                                                                       | One-line `Objects.requireNonNull` wrapper                 | **INLINE** while retaining every null/invariant check.                  |
| `ManualInstrumentCreateRequest.invalidSymbol`                                                              | One catch path                                                                                                                 | One-use exception factory                                 | **INLINE**; retain meaningful structural phases.                        |
| Identity/session/security helpers                                                                          | Session rotation, security-event detail shape, abuse-source normalization, PEM normalization, `AppException.validateParamKeys` | Lifecycle/security/configuration/strict-contract concepts | **KEEP**.                                                               |

The deletion/inlining set is one class plus **fourteen private methods**.

It adds no replacement helper, validator, strategy, factory, interface, annotation, or framework.

### String normalization audit

| Current construct                                     | Location / consumers                                                        | Classification                                  | Proposed action                             |
| ----------------------------------------------------- | --------------------------------------------------------------------------- | ----------------------------------------------- | ------------------------------------------- |
| `FinancialAccountOnboardingService.normalizeCurrency` | One call; trim + `Locale.ROOT` uppercase                                    | One-off primitive relocation                    | **INLINE**.                                 |
| `ReconciliationCommandService.normalized`             | Fingerprint canonical inputs                                                | Tiny helper, but fingerprint identity-adjacent  | **DEFER TO CLEANUP D. Preserve unchanged.** |
| `InstrumentSearchService.normalizeQuery`              | Optional input, trim, normalized-length validation, `Locale.ROOT` uppercase | Endpoint-owned normalization/validation concept | **KEEP**.                                   |
| `FinancialAccount.normalizeName`                      | Create/update bounded display name                                          | Domain-owned normalization                      | **KEEP**.                                   |
| `FinancialAmount.normalize`                           | Canonical numeric scale/precision/integer-digit contract                    | Domain-owned financial normalization            | **KEEP**.                                   |
| `AuthenticationAbuseProtection.normalizeSource`       | Security-key paths with fail-closed unknown source                          | Security-owned normalization                    | **KEEP**.                                   |
| `AccessTokenProperties.normalizeKeyPem`               | Private/public configuration pair                                           | Configuration-owned normalization               | **KEEP**.                                   |
| `FinancialAccount.requireIanaTimeZone`                | Create/update domain integrity and service translation                      | Domain-owned IANA-zone semantics                | **KEEP**.                                   |

No shared string utility is introduced.

### Capability error-code audit

- `LedgerErrorCode` already distinguishes current account, balance, policy, idempotency, activity, opening, and reconciliation business conditions.
- Add exactly one code:

```text
FUTURE_TIME_NOT_ALLOWED
```

- HTTP status: `422`.
- No interpolation params.
- Safe description covers future effective or as-of ledger time.
- Existing error-key convention derives:

```text
error.ledger.future_time_not_allowed
```

- Do not create separate future codes per endpoint or request field.
- `IdentityErrorCode`, `ReferenceErrorCode`, and `CommonErrorCode` remain unchanged.
- Do not introduce a validation-code hierarchy.

### Audited implementation surface

Expected production changes are limited to:

- `LedgerErrorCode`
- `CashActivityCommandService`
- `CashTransferService`
- `FinancialAccountLifecycleService`
- `FinancialAccountOnboardingService`
- `FinancialAccountQueryService`
- `ReconciliationCommandService`
- `Reconciliation`
- `ManualInstrumentCreateRequest`

Delete:

- `LedgerTimingRules`

Expected test-edit surface is primarily:

- `FinancialAccountServiceTest`
- `FinancialAccountHttpTest`
- `CashActivityServiceTest`
- `CashActivityHttpTest`
- `LedgerReconciliationServiceTest`
- `LedgerReconciliationHttpTest`

Other domain, concurrency, rollback, migration, bearer-security, identity, reference, and platform tests are verification coverage rather than an expected edit surface.

If implementation materially exceeds this scope, stop and reassess rather than silently broadening Cleanup C.

## Scope

1. Add `LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED` with HTTP 422, no interpolation params, and one safe capability description.

2. Replace all future-time `ValidationErrors.invalidField(...)` outcomes with:

```java
new AppException(LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED)
```

while preserving the exact injected-clock observation and check-before-lock/write/query ordering.

3. Delete `LedgerTimingRules.java`.

Do not replace it with:

- `ValidationUtils`
- `BusinessRules`
- `RuleEngine`
- `ValidatorService`
- `requireTrue`
- `throwIf`
- `requireNotAfter`
- annotation-driven validation
- another helper class

4. In `CashActivityCommandService`, keep future-time, supported activity type, account eligibility, optional balance-version, and policy-rejection rules at their existing workflow positions. Inline only the audited trivial wrappers; retain cohesive cash-account eligibility.

5. In `CashTransferService`, preserve one clock observation, temporal validation, owner-scoped account loading/locking, transfer shape/currency rules, source/destination version checks, and independent policy results. Inline only adjacent one-condition version/policy wrappers; retain `validateTransferShape`.

6. In account onboarding, lifecycle, and balance reads, preserve future-time rejection at the same pre-write/pre-query positions. Inline one-use currency normalization and lifecycle version wrappers. Preserve timezone validation, currency state checks, idempotency, opening correction, repeatable-read balance behavior, and current capability errors.

7. In reconciliation preview, commit, and correction:
   - observe the clock once per workflow;
   - compare both statement instants to that same value;
   - throw `FUTURE_TIME_NOT_ALLOWED` if either is future;
   - delete the future-time wrapper chain.

8. Remove the duplicate service-level reconciliation period `ValidationErrors.invalidField` representation **only where an independent non-controller period-order invariant remains**.

   Specifically verify preview.

   If preview does not reach `Reconciliation.create` or another equivalent invariant boundary, retain the minimum local application/domain period-order check there.

   That internal guard must:
   - not emit field-oriented validation metadata;
   - not call `ValidationErrors.invalidField`;
   - not introduce a reusable validation framework;
   - preserve application correctness for direct/non-controller execution.

   HTTP request validation remains responsible for the user-facing field-oriented malformed-period response.

9. Keep reconciliation coverage, opening continuity, resolution admissibility, archived full-ledger historical behavior, expected projection version, supersession, adjustment eligibility, reversal, replacement, and rollback ordering unchanged.

10. Inline only the audited trivial reconciliation account/type/version wrappers. Retain meaningful reconciliation phases.

11. Inline `Reconciliation.requireAmount` into the domain factory without weakening null, equation, period, resolution/adjustment, supersession, text, count, or ownership-shape checks.

12. Inline `ManualInstrumentCreateRequest.invalidSymbol()` into its single catch path. Preserve reference structural validation, normalized bounds, duplicate aliases, capability errors, search/query behavior, sort validation, visibility, and manual-instrument semantics.

13. Leave `ReconciliationCommandService.normalized` and all fingerprint input/canonicalization behavior unchanged. Fingerprint cleanup remains Cleanup D.

14. Update existing service and HTTP tests to assert HTTP 422 plus `FUTURE_TIME_NOT_ALLOWED` for future-time behavior while keeping structural errors on `VALIDATION_FAILED` with field information.

15. Preserve all accounting, concurrency, owner-isolation, security, idempotency, rollback, and transaction tests. Add no tests whose purpose is private helper structure.

## Explicit non-goals

- No pagination, cursor, `Pageable`, `SliceResponse`, sort-policy, or Cleanup B behavior change.
- No Cleanup D View/Response mapping, DTO restructuring, mapping cleanup, or fingerprint readability work.
- No fingerprint canonical-input normalization cleanup. `ReconciliationCommandService.normalized` remains unchanged.
- No generic DTO/model restructuring.
- No service merging.
- No repository merging/splitting/redesign.
- No `JdbcClient` redesign.
- No MyBatis/QueryDSL migration.
- No JPA change.
- No dependency or build-configuration change.
- No migration, schema, table, column, constraint, trigger, index, seed, or data change.
- No RFC 9457 translation redesign.
- No Problem Detail shape change.
- No trace-correlation redesign.
- No persistence exception translation change.
- No authentication exception handling/security entry-point change.
- No cookie or no-store policy change.
- No generic validation framework.
- No rule engine.
- No validator service.
- No assertion utility.
- No error-wrapper hierarchy.
- No annotation-driven domain validation.
- No custom Bean Validation annotation.
- No change to posting signs, immutable facts, economic ordering, reversal/correction, opening coverage, balance arithmetic, reconciliation equations/lifecycle, historical policy evaluation, idempotency, deterministic locks, optimistic versions, owner scope, or rollback.
- No identity/session lifecycle redesign.
- No reference-catalog behavior redesign.
- No frontend change.
- No OpenAPI tooling.
- No async/job infrastructure.
- No Cleanup D activation.
- No R4 investing implementation.
- No Git operation.

## Deletion plan

### Production file

Delete:

```text
src/main/java/dev/canverse/stocks/ledger/application/LedgerTimingRules.java
```

after all consumers are removed.

### Private methods

Remove or inline:

- `CashActivityCommandService.requireCashActivityType`
- `CashActivityCommandService.requireAllowed`
- `CashActivityCommandService.requireExpectedVersion`
- `CashTransferService.requireAllowed`
- `CashTransferService.requireExpectedVersion`
- `FinancialAccountLifecycleService.requireVersion`
- `FinancialAccountLifecycleService.requireOpeningVersion`
- `FinancialAccountOnboardingService.normalizeCurrency`
- `ReconciliationCommandService.requireReconciliationAccount`
- `ReconciliationCommandService.requireFullLedger`
- `ReconciliationCommandService.requireExpectedVersion`
- `ReconciliationCommandService.rejectFuture`
- `Reconciliation.requireAmount`
- `ManualInstrumentCreateRequest.invalidSymbol`

Do **not** remove:

```text
ReconciliationCommandService.normalized
```

It remains unchanged for Cleanup D.

Delete no test class.

Delete no:

- `ValidationErrors`
- `AppException`
- global exception handler
- capability error enum
- request `validate()` method
- amount parser
- domain value type
- meaningful reconciliation phase
- identity/security helper
- reference normalization helper

## Database changes

Migration(s):

- None.

Tables/columns/constraints/indexes introduced or changed:

- None.

## Application and domain changes

- Application workflows express the future-time business condition directly where their injected `Clock` has been observed.
- The future-time rule uses the capability code and no field/message/fallback metadata.
- Direct temporal checks remain before the same locks, idempotency writes/replays, owned aggregate reads, projection mutations, reconciliation reversals/writes, and balance queries as today.
- Reconciliation retains its staged workflow: request conversion, temporal validation, comparison, eligibility/coverage, version/opening/resolution validation, optional reversal, recomparison, replacement/adjustment write, and idempotent result.
- Removing tiny helpers must not collapse meaningful reconciliation phases.
- HTTP request records retain all six structural `validate()` methods.
- Controllers remain their sole callers after `@Valid` binding.
- Non-controller application/domain execution must retain independent integrity guards where required.
- In particular, reconciliation preview must not rely solely on controller `validate()` for period ordering.
- Domain entities retain independent integrity guards even when HTTP already validates equivalent malformed shape.
- Domain guards continue using their existing internal invariant mechanism and are not converted into HTTP field metadata.
- Reference and identity capability errors remain unchanged.
- No cross-capability error utility is added.
- Fingerprint canonicalization and `ReconciliationCommandService.normalized` remain unchanged.

## API and error contract changes

All routes, request bodies, success responses, authentication, owner resolution, trace correlation, no-store headers, and existing HTTP status families remain unchanged.

The following future-time rejections change from generic `VALIDATION_FAILED` plus field metadata to:

```text
FUTURE_TIME_NOT_ALLOWED
HTTP 422
```

Contexts include:

- account creation with future opening-state `effectiveAt`;
- opening-state replacement with future `effectiveAt`;
- future balance `asOf`;
- future cash-activity `effectiveAt`;
- transfer preview with future `effectiveAt`;
- transfer commit with future `effectiveAt`;
- reconciliation preview with future statement opening or closing;
- reconciliation commit with future statement opening or closing;
- reconciliation correction with future replacement opening or closing.

Expected Problem Detail uses the existing standard envelope and includes:

```json
{
  "status": 422,
  "code": "FUTURE_TIME_NOT_ALLOWED",
  "key": "error.ledger.future_time_not_allowed"
}
```

No future-time field-error params are required.

Structural failures remain:

```text
VALIDATION_FAILED
```

with field information, including:

- malformed/excess financial decimals;
- invalid IANA timezone;
- unordered statement request periods;
- resolution/reason structural inconsistency;
- account opening-state presence contradiction;
- normalized manual-instrument bounds;
- calendar/search query ranges;
- unsupported pageable sort shapes.

A malformed reconciliation request period sent through HTTP must continue to receive the existing field-oriented structural response because controller request validation occurs first.

The internal non-controller invariant is not a second public HTTP validation contract.

## Preserved business and security invariants

- `accounting-contract.md` remains unchanged.
- Future actual ledger facts and future balance-as-of reads remain forbidden relative to the injected clock.
- Reconciliation opening and closing times are compared against one shared clock observation per workflow.
- All amount parsing remains exact, plain-decimal, scale/precision bounded, and positive where required.
- Deposit/withdrawal/transfer posting signs and transfer neutrality remain exact.
- Policy evaluation and funding/balance arithmetic remain unchanged.
- Activities, postings, and reconciliation evidence remain immutable.
- Reversal, opening correction, adjustment correction, and superseding replacement retain exact audit relationships.
- Reconciliation period arithmetic, coverage, opening continuity, closing difference, adjustment eligibility, archived historical evaluation, projection version, supersession, and staleness semantics remain unchanged.
- Owner scope remains in the same queries/locks.
- No cleanup creates a global lookup or changes cross-owner not-found semantics.
- Idempotency fingerprints and canonical inputs remain unchanged.
- Operation scopes, command sequences, replay results, lock order, flush order, projection updates, optimistic conflicts, and rollback behavior remain unchanged.
- Identity validation, access-token checks, abuse protection, session lifecycle, security-event validation, cookies, bearer handling, trace correlation, and centralized safe-error translation remain unchanged.
- Structural request failures remain HTTP-boundary concerns.
- Business/application/domain failures continue through capability `ErrorCode` plus `AppException`.

## Test migration

### Error-contract changes

- Replace future-time assertions expecting generic `VALIDATION_FAILED` with assertions for `FUTURE_TIME_NOT_ALLOWED`.
- At HTTP level assert:
  - HTTP 422;
  - `code=FUTURE_TIME_NOT_ALLOWED`;
  - `key=error.ledger.future_time_not_allowed`.

- Do not expect field-error metadata for future-time business rejection.
- Continue expecting field entries for structural period, amount, timezone, sort, and cross-field request errors.
- Exercise both future statement opening and future statement closing through reconciliation behavior without duplicating every internal branch.

### Reconciliation structural/invariant preservation

Explicitly prove the distinction:

```text
unordered/reversed/equal malformed HTTP request period
→ VALIDATION_FAILED + field-oriented feedback

ordered period extending into the future
→ FUTURE_TIME_NOT_ALLOWED
```

Also preserve an application/domain-level test proving preview cannot execute an invalid ordered-period shape merely because controller validation was bypassed.

Do not assert private helper names or implementation structure.

### Structural validation preservation

- Keep existing Bean Validation tests for required fields, enum/instant binding, lengths, patterns, nonnegative versions, nested requests, and exact decimal parsing.
- Keep all six request `validate()` behaviors.
- Preserve missing/forbidden opening-state checks.
- Preserve reconciliation period and resolution/reason consistency.
- Preserve normalized Unicode expansion bounds and duplicate aliases.

### Financial/security preservation

Reuse existing:

- `FinancialAccountServiceTest`
- `FinancialAccountHttpTest`
- `CashActivityServiceTest`
- `CashActivityHttpTest`
- `LedgerReconciliationServiceTest`
- `LedgerReconciliationHttpTest`

for future-time behavior and existing financial semantics.

Preserve:

- `LedgerDomainInvariantTest`
- concurrency suites
- transaction rollback tests
- bearer-security tests
- identity tests
- reference tests
- full suite

Modify unrelated tests only when they directly own the intentional future-time error-code change.

Add no test for:

- number of private helpers;
- names of inlined methods;
- complete enum membership.

## Risk analysis

- **Missed temporal path:** deleting a shared helper could leave one workflow without the rule. Use the known consumer inventory and final source search.
- **Clock inconsistency:** calling the clock separately for reconciliation opening and closing can produce boundary inconsistency. Observe once per workflow.
- **Controller-only invariant:** deleting the service period check without checking preview could make direct application invocation accept an invalid period. Verify all non-controller execution paths and retain the minimum internal invariant where necessary.
- **Validation-order regression:** moving future checks after locks, idempotency writes, reads, reversals, projections, or queries can change side effects/information exposure. Preserve existing ordering.
- **Wrong error granularity:** do not create per-field or per-endpoint future codes. Use one capability condition.
- **Structural/business conflation:** malformed periods and amounts remain structural; coverage, opening mismatch, resolution, lifecycle, policy, ownership, and future-time rejection remain capability/domain outcomes.
- **Reconciliation rollback regression:** correction may reverse an adjustment before recomputation inside one transaction. Do not move post-reversal financial checks or weaken rollback behavior.
- **Helper over-deletion:** coverage, continuity, resolution, transfer shape, cash-account eligibility, amount parsing, normalization, security-event shape, and session rotation remain meaningful concepts.
- **Exception-boundary expansion:** changing `AppException` or the global handler is unnecessary and prohibited.
- **Fingerprint drift:** avoid entirely by leaving fingerprint construction and `ReconciliationCommandService.normalized` unchanged in Cleanup C.
- **Scope creep:** mapping, repository, persistence, pagination, schema, frontend, Cleanup D, MyBatis, and R4 remain untouched.

## Required tests

### Pure/domain

- Existing `FinancialAmount`, `Activity`, `MoneyPosting`, `FinancialAccount`, and `Reconciliation` invariant tests remain green.
- Existing request-record structural tests remain green.
- Non-controller reconciliation period integrity remains protected.
- No test is added solely for private structure.

### PostgreSQL/Testcontainers

- Future opening creation/correction reject before ledger/projection mutation with `FUTURE_TIME_NOT_ALLOWED`.
- Future activity and transfer preview/commit reject before activity/posting/idempotency/projection mutation.
- Accepted activity/transfer posting signs, policy outcomes, replay, and balances remain unchanged.
- Future balance `asOf` rejects with the capability code.
- Current/historical repeatable-read balances and reconciliation metadata remain unchanged.
- Reconciliation preview/commit/correction reject future opening or closing times with the capability code before mutation.
- Ordered historical periods continue through normal coverage/opening/resolution logic.
- An invalid reconciliation period remains protected in non-controller execution, including preview.
- Existing coverage, continuity, archived account, adjustment/reversal/replacement, supersession, projection-version, concurrency, idempotency, and rollback tests remain green.
- No migration/schema expectation changes.

### HTTP/security

- Representative account, cash/transfer, balance, and reconciliation future requests return trace-correlated RFC 9457 HTTP 422 with:
  - `code=FUTURE_TIME_NOT_ALLOWED`
  - `key=error.ledger.future_time_not_allowed`

- Structural amount/timezone/period/resolution/opening-state/sort/reference validation remains:
  - HTTP 422
  - `VALIDATION_FAILED`
  - field information

- Malformed JSON/enum/instant binding retains current boundary behavior.
- Existing bearer authentication, typed principal, owner isolation, cross-owner not-found, statelessness, no-store, cookie/session, and security response tests remain green.

## Acceptance criteria

1. Production source has no `@Validated` and no service/repository/domain/infrastructure/configuration Bean Validation entry point.

2. Existing controller `@Valid @RequestBody` and nested cascading boundaries remain intact.

3. All six request-record `validate()` methods remain structural and controller-owned.

4. All original `ValidationErrors.invalidField(...)` sites are accounted for:
   - structural uses remain structural;
   - duplicate service-level reconciliation HTTP field-error representation is removed where an independent invariant remains;
   - future-time business rejection is converted to capability error.

5. `LedgerErrorCode.FUTURE_TIME_NOT_ALLOWED` is the only new capability error code.

6. Every existing future effective/as-of ledger path uses `FUTURE_TIME_NOT_ALLOWED`.

7. `LedgerTimingRules` and all references are deleted.

8. No generic temporal/validation/assertion replacement is introduced.

9. Reconciliation preview, commit, and correction compare both statement instants against one observed clock value per workflow.

10. HTTP reconciliation period errors remain field-oriented structural failures.

11. Every non-controller reconciliation execution path retains an independent period-order invariant. Preview must not rely solely on controller `validate()`.

12. The one class and fourteen private methods in the deletion plan are removed/inlined without adding replacement abstractions.

13. `ReconciliationCommandService.normalized` remains unchanged.

14. Fingerprint key order, values, null/trim semantics, hashing, and canonicalization remain unchanged.

15. Meaningful reconciliation, cash-account, transfer-shape, amount, normalization, identity, security, and exception-contract helpers remain.

16. No service is merged or redesigned.

17. Reconciliation preview/commit/correction, adjustment reversal/replacement, coverage, continuity, archived history, resolution, versioning, projection, idempotency, locks, and rollback remain behaviorally unchanged.

18. Cash activity/transfer/opening/balance financial arithmetic, immutable facts, posting signs, policy, owner scope, idempotency, locks, and historical behavior remain unchanged.

19. `AppException`, `ValidationErrors`, `GlobalExceptionHandler`, persistence translation, Problem Detail schema, trace/security boundaries, and `CommonErrorCode` remain architecturally unchanged.

20. No pagination, Cleanup D, mapping cleanup, fingerprint cleanup, DTO restructuring, MyBatis/JPA/schema, async/job, frontend, OpenAPI, provider, or R4 change is introduced.

21. Focused tests, full suite, Maven `verify`, and Spotless pass with no unresolved `MUST FIX`.

22. `STATE.md` describes final validation/error reality only after implementation verification and user-controlled activation/completion.

## Documentation completion

Before this implementation unit is considered complete:

1. PR-026 must first be explicitly activated by the user after PR-025 acceptance/commit.

2. On activation:
   - set PR-026 status to `ACTIVE`;
   - point `CURRENT.md` to PR-026;
   - update `STATE.md` to record PR-025 as accepted and PR-026 as active.

3. At implementation completion, update `STATE.md` with current repository reality only:
   - future ledger time is capability-coded;
   - structural request failures remain field-oriented;
   - `LedgerTimingRules` is removed;
   - reconciliation retains non-controller period integrity.

4. Replace superseded current statements; do not append cleanup history/helper inventory.

5. Do not update `accounting-contract.md`, `backend-master-plan.md`, or coding standards unless implementation discovers a genuine contract conflict.

6. Do not update `progress-report.md` merely for routine Cleanup C completion unless project-level status materially changes.

7. Keep reusable Windows/sandbox/Maven/Docker/Testcontainers/output lessons in the command playbook.

8. Record exact implementation changes, deviations, decisions, and verification in this specification's Completion Record.

9. Leave lifecycle transition after implementation under user control.

10. Do not activate Cleanup D or R4 automatically.

## Verification commands

Focused behavior gate:

```powershell
.\mvnw.cmd "-Dtest=FinancialAccountServiceTest,FinancialAccountHttpTest,CashActivityServiceTest,CashActivityHttpTest,LedgerReconciliationServiceTest,LedgerReconciliationHttpTest,LedgerDomainInvariantTest,ApiBearerSecurityHttpTest" test
```

Then:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spotless:check
```

Source audits:

```powershell
rg -n "LedgerTimingRules|error\.fields\.ledger\.future_time" src/main/java src/test/java
rg -n "FUTURE_TIME_NOT_ALLOWED" src/main/java src/test/java
rg -n "@Validated|@Valid|jakarta\.validation|ValidationErrors\.invalidField|\.validate\(\)" src/main/java
rg -n "private .*\b(require|check|validate|ensure|assert)[A-Z][A-Za-z0-9_]*\s*\(" src/main/java/dev/canverse/stocks/ledger src/main/java/dev/canverse/stocks/reference src/main/java/dev/canverse/stocks/identity src/main/java/dev/canverse/stocks/platform
rg -n "ReconciliationCommandService.*normalized|normalized\(" src/main/java/dev/canverse/stocks/ledger
```

Interpretation:

- The `LedgerTimingRules`/old future-field-error search must return no match.
- `FUTURE_TIME_NOT_ALLOWED` must cover every audited temporal workflow.
- Validation searches must still show expected controller `@Valid`, request constraints, structural `ValidationErrors.invalidField`, and controller request `validate()` calls.
- Meaningful validation/security helpers are expected to remain.
- `ReconciliationCommandService.normalized` must still exist unchanged for later Cleanup D handling.

## Completion record

Fill this before marking the PR complete.

### Implemented

- Activated PR-026, pointed `CURRENT.md` to it, and recorded PR-025 acceptance/commit plus Cleanup B completion in `STATE.md`; PR-026 remains ACTIVE.
- Added the sole ledger capability error `FUTURE_TIME_NOT_ALLOWED` with HTTP 422 and the derived key `error.ledger.future_time_not_allowed`, and converted all ledger future effective/as-of rejection paths: account opening, opening correction, cash activity, transfer preview, transfer commit, account balance as-of, and reconciliation preview/commit/correction.
- Deleted `LedgerTimingRules` and inlined the specified workflow/domain/reference trivial helpers without introducing a replacement validation abstraction.
- Reconciliation preview, commit, and correction each observe the injected clock once and compare both statement boundaries against that same instant.
- Preserved reconciliation period integrity outside controllers: preview has a direct local ordering guard because it does not construct `Reconciliation`; commit and correction retain the `Reconciliation.create` invariant. Controller request validation remains the first HTTP boundary for malformed periods.
- Preserved structural field validation, lifecycle/security behavior, financial behavior, idempotency/locking order, and fingerprint construction. `ReconciliationCommandService.normalized` is unchanged.
- Added/updated exact service and HTTP assertions for capability-coded future rejection, including opening correction, balance as-of, cash activity, transfer preview/commit, and reconciliation opening/closing across preview/commit/correction; retained the non-controller preview period-ordering test.

### Deviations from specification

- None.

### New decisions

- Preview retains the smallest local `IllegalArgumentException` period-ordering guard because preview stops at its read/comparison model and does not cross the `Reconciliation.create` invariant boundary. Commit and correction continue to rely on that domain invariant.

### Tests executed

- Focused PR-026 gate: `.\mvnw.cmd "-Dtest=FinancialAccountServiceTest,FinancialAccountHttpTest,CashActivityServiceTest,CashActivityHttpTest,LedgerReconciliationServiceTest,LedgerReconciliationHttpTest,LedgerDomainInvariantTest,ApiBearerSecurityHttpTest" test` — 87 tests passed.
- Full suite: `.\mvnw.cmd test` — 370 tests passed.
- Maven verification: `.\mvnw.cmd verify` — 370 tests passed; package/repackage and Spotless verification passed.
- Formatting: `.\mvnw.cmd spotless:check` — 262 Java files clean (199 main, 63 test).
- Source audits: no `LedgerTimingRules` or old ledger future-time field-error key; all nine audited workflow contexts use `FUTURE_TIME_NOT_ALLOWED`; controller/request validation, structural `ValidationErrors.invalidField(...)`, meaningful helpers, and unchanged `normalized` remain present.

### Follow-up work

- Cleanup D remains the final planned cleanup boundary before returning to R4 investing.
- Fingerprint readability and `ReconciliationCommandService.normalized` remain deferred to Cleanup D.
