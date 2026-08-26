# PR-024 - Cleanup B1 ledger pagination simplification

Status: **ACTIVE**

## Goal

Remove premature custom cursor/keyset pagination from the current ledger HTTP reads. Financial accounts become one complete owner-scoped list, while ledger activities and reconciliations use Spring `Pageable` with `Slice` semantics, explicit safe sorting, offset SQL, and one compact shared `SliceResponse<T>` HTTP representation. The observable result contains no ledger cursor parameter, token, filter digest, or `nextCursor` field and does not change accounting behavior.

## Capability and review boundary

- Coherent capability: the account, activity, and reconciliation list endpoints share `LedgerCursorCodec`, ledger cursor models, owner-scoped `JdbcClient` read paths, response conventions, and ledger HTTP tests. Removing that stack is one independently meaningful ledger cleanup.
- Combined behaviors: account list simplification, activity/reconciliation `Pageable` binding, whitelisted SQL ordering, `LIMIT/OFFSET`, `Slice` construction, cursor deletion, response changes, and behavior-focused tests must land together or the ledger API would retain a partial pagination mechanism.
- Excluded neighbor: device-session and instrument-search cursor removal is the second Cleanup B boundary. It remains unimplemented and unspecified as an active PR. Cleanup C validation/error work and Cleanup D model/mapping/fingerprint work are also excluded.
- Focused review: one reviewer can inspect the three ledger query paths, their two retained filters, five stable order clauses, cursor deletions, four directly affected ledger test classes, and the unchanged accounting/security behavior as one review unit.
- Cleanup B is split into two PRs because the complete audit spans five endpoints, about thirty production files, and ten test files across ledger, identity, reference, and platform. The ledger half has higher accounting regression risk and strong internal coupling; identity/reference has a separate session-security/search-visibility surface and will own final deletion of the shared cursor transport after its last consumer is removed.

## Source documents

- `docs/engineering/coding-standards.md` - sections 3, 6, 8, 10, 11, and 13.
- `docs/review/backend-master-plan.md` - current standardization conventions, API invariants, file economy, R2-R3, R16, and testing strategy.
- `docs/review/accounting-contract.md` - sections 4, 7, 13, 17, 18, and 21; pagination changes do not redefine their financial semantics.
- `docs/implementation/PR-019-authenticated-identity-and-session-security-lifecycle.md` through `PR-022-cash-statement-reconciliation-and-explicit-adjustments.md` - historical evidence for current endpoint behavior and tests, not compatibility constraints.
- `docs/implementation/PR-023-governing-simplicity-standards.md` - accepted authority for no-pagination/`Pageable`/`Slice`/`Page` selection and deletion of speculative cursor infrastructure.
- Spring Data Commons 4.1 source/documentation for `Pageable`, `PageRequest`, `SliceImpl`, `@PageableDefault`, and the web argument resolver. Spring's resolver uses zero-based pages, exposes `getOffset()`, applies a configured maximum page size, and normalizes malformed/negative page and size inputs rather than providing the project's former cursor validation contract.

## Starting state

- PR-023 is accepted and committed. Its simplicity rules are authoritative, but current production code still uses custom cursor/keyset pagination.
- The application is unreleased and the preserved frontend will be rewritten. Existing `limit`, `cursor`, `nextCursor`, cursor error, and cursor payload contracts have no compatibility requirement.
- Flyway V1-V4 and the accepted identity, reference, ledger, and reconciliation behavior remain current. No schema or index change is required for this cleanup.
- Current ledger pagination is:
  - accounts: `(name_normalized, id) ASC` keyset, default 50, maximum 100, `includeArchived` filter digest;
  - activities: `(recorded_at, id) DESC` keyset, default 50, maximum 100, optional `accountId` filter digest;
  - reconciliations: `(statement_closing_at, id) DESC` keyset, default 25, maximum 100, required account filter digest.
- `LedgerCursorCodec` owns all three ledger payloads and depends on the shared `CursorTokenCodec` plus `CanonicalFingerprint`. `CanonicalFingerprint` also has real idempotency consumers and must remain. `CursorTokenCodec` remains temporarily because instrument search still consumes it until the second Cleanup B PR.

## Cleanup B repository findings

### Current cursor/keyset consumers

| Endpoint                                           | Controller                             | Service                                                                                             | Cursor/model and codec                                                                  | Repository/query                                   | Response                       | Cursor-focused tests                                                                                                                     |
| -------------------------------------------------- | -------------------------------------- | --------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- | -------------------------------------------------- | ------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `GET /api/v1/accounts`                             | `FinancialAccountController.list`      | `FinancialAccountQueryService.list`                                                                 | `AccountCursor`; shared `LedgerCursorCodec` account payload/filter digest               | `LedgerReadRepository.findAccounts`                | `FinancialAccountPageResponse` | `FinancialAccountHttpTest.accountCursorsAreStableAndBoundToTheirFilter`; shared `LedgerCursorCodecTest`                                  |
| `GET /api/v1/activities`                           | `CashActivityController.list`          | `CashActivityQueryService.list`                                                                     | `ActivityCursor`; shared `LedgerCursorCodec` activity payload/filter digest             | `LedgerReadRepository.findActivities`              | `SliceResponse<ActivityResponse>` | pagination portion of `CashActivityHttpTest.activityRoutesRecordReplayListReverseAndReadTheProjection`; shared `LedgerCursorCodecTest`   |
| `GET /api/v1/accounts/{accountId}/reconciliations` | `ReconciliationController.list`        | `ReconciliationReadService.list`                                                                    | `ReconciliationCursor`; shared `LedgerCursorCodec` reconciliation payload/filter digest | `ReconciliationReadRepository.findPage`            | `SliceResponse<ReconciliationResponse>` | `LedgerReconciliationHttpTest.reconciliationListUsesStableMultiRowCursorOrderingAndAccountFilterBinding`; shared `LedgerCursorCodecTest` |
| `GET /api/v1/auth/sessions`                        | `DeviceSessionController.listSessions` | `DeviceSessionQueryService.listSessions`                                                            | `SessionCursor`; standalone static `SessionCursorCodec`                                 | `DeviceSessionReadRepository.findFamilies`         | `DeviceSessionPageResponse`    | `SessionCursorCodecTest`; cursor/pagination portions of `DeviceSessionQueryServiceTest` and `DeviceSessionHttpTest`                      |
| `GET /api/v1/reference/instruments`                | `ManualInstrumentController.search`    | `InstrumentSearchService.search`; `InstrumentSearchCriteria` currently carries `limit` and `cursor` | `InstrumentSearchCursor`; `InstrumentSearchCursorCodec` with filter digest              | `ReferenceCatalogReadRepository.searchInstruments` | `InstrumentPageResponse`       | cursor portions of `ReferenceValueObjectTest`, `ReferenceCatalogQueryTest`, and `ManualInstrumentHttpTest`                               |

No cursor class has a non-list consumer. There is no synchronization/change-feed continuation token in production. The future sync-token allowance in the master plan is feature-local future guidance and does not retain any current cursor file.

### Shared and surviving dependencies

- `CursorTokenCodec` is shared only by `LedgerCursorCodec` and `InstrumentSearchCursorCodec`. PR-024 removes the ledger consumer; the class remains until instrument search changes in the second Cleanup B PR, then it becomes dead and must be deleted.
- `CanonicalFingerprint` is not cursor-only. Account, settings, lifecycle, cash-activity, transfer, and reconciliation idempotency fingerprints are real consumers, so the class and its non-cursor tests remain unchanged.
- Jackson `ObjectMapper` is broadly used and is not a cursor dependency.
- `InstrumentSearchCriteria` remains a meaningful use-case filter record, but the second Cleanup B PR removes its `limit` and `cursor` components and passes `Pageable` separately.
- `DeviceSessionFamilyRecord`, `InstrumentView`, `FinancialAccountView`, and the reconciliation preview/summary read models remain genuine aggregate/query read models. Activity and reconciliation list/detail reads map directly to their stable HTTP responses; they are not pagination wrappers.
- `ReferenceCatalogController` already returns countries, currencies, and markets as deterministic direct lists. Market-calendar output is an explicitly bounded date-range response. None uses cursor infrastructure or changes in Cleanup B.

## Cleanup B endpoint decision table

| Endpoint                                           | Current             | Target  | Default / maximum | Default sort                             | Allowed client sort                                            | Stable tie-break                                                          | HTTP response                                                        | Rationale                                                                                                                                                                                                                                                                          |
| -------------------------------------------------- | ------------------- | ------- | ----------------- | ---------------------------------------- | -------------------------------------------------------------- | ------------------------------------------------------------------------- | -------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET /api/v1/accounts`                             | cursor page, 50/100 | `List`  | N/A               | `name_normalized ASC, id ASC`            | none                                                           | `id ASC`                                                                  | JSON array of `FinancialAccountResponse`                             | A user's financial-account collection is naturally small; totals and continuation have no demonstrated value. Preserve archive filtering and current order.                                                                                                                        |
| `GET /api/v1/auth/sessions`                        | cursor page, 25/100 | `List`  | N/A               | family `created_at DESC, family_id DESC` | none                                                           | `family_id DESC`                                                          | JSON array of `DeviceSessionResponse`                                | The query returns logical device families, not generations. It includes ended history, but normal owner scope is naturally small and theoretical growth alone does not justify pagination. Preserve every status/detail/security semantic. Implemented in the second Cleanup B PR. |
| `GET /api/v1/reference/instruments`                | cursor page, 25/100 | `Slice` | 25 / 100          | `name ASC`                               | exactly one of `name` or `symbol`, either direction            | `id` in the primary direction; symbol order retains market code before ID | `{ "instruments": [], "page": 0, "size": 25, "hasNext": false }`     | Search is an ordinary potentially large collection; the client needs continuation, not totals. Name ordering follows the current authoritative API direction; symbol sorting preserves the existing catalogue order when requested. Implemented in the second Cleanup B PR.        |
| `GET /api/v1/activities`                           | cursor page, 50/100 | `Slice` | 50 / 100          | `recordedAt DESC`                        | exactly one of `recordedAt` or `effectiveAt`, either direction | `id` in the primary direction                                             | `{ "items": [], "page": 0, "size": 50, "hasNext": false }`      | Owner activity history is an ordinary collection. No product use for total count/pages is demonstrated; preserve reverse-recorded default ordering and account filtering.                                                                                                          |
| `GET /api/v1/accounts/{accountId}/reconciliations` | cursor page, 25/100 | `Slice` | 25 / 100          | `statementClosingAt DESC`                | only `statementClosingAt`, either direction                    | `id` in the primary direction                                             | `{ "items": [], "page": 0, "size": 25, "hasNext": false }` | Reconciliation history is an ordinary collection. `hasNext` is sufficient; a count query would add no demonstrated product value.                                                                                                                                                  |

No affected endpoint uses `Page`: the repository contains no consumer of total elements or total pages, and Cleanup B must not add `COUNT(*)` work.

For pageable endpoints, use Spring's zero-based `page`, `size`, and repeatable `sort=property,direction` request semantics. Bind `Pageable` in controllers with endpoint-local `@PageableDefault`; configure Spring Data web's effective maximum page size as 100. Do not add a project pagination type or custom argument resolver. Preserve Spring Data 4.1 resolver behavior for malformed/negative page and size values: page falls back/normalizes to zero, non-positive or malformed size uses the endpoint default, and size above 100 is capped at 100. Tests must pin that chosen contract without testing unrelated framework internals.

Each pageable endpoint accepts at most one primary sort order. Validate the resolved property, direction, ignore-case/null-handling flags, and order count before SQL construction. Unsupported or compound sorts use the existing `VALIDATION_FAILED` response architecture with field `sort`; do not add cursor errors or a new error framework. Map accepted properties through a local `switch` to complete hard-coded SQL fragments. Never concatenate a client property into SQL.

## Scope

1. Change `GET /api/v1/accounts` to accept only the existing `includeArchived` business filter and return `List<FinancialAccountResponse>` directly.
2. Remove account cursor decoding, filter hashing, limit-plus-one work, `nextCursor`, and the account keyset predicate. Preserve owner SQL scoping, `includeArchived`, account response mapping, and `(name_normalized ASC, id ASC)` ordering.
3. Bind Spring `Pageable` on `GET /api/v1/activities` with default page 0, size 50, and `recordedAt,DESC`; enforce effective size 100 and the explicit sort policy above.
4. Replace the activity cursor predicate with whitelisted ordering plus `LIMIT pageSize + 1 OFFSET pageable.offset`. Preserve owner and optional `accountId` predicates, `DISTINCT` activity selection, and bounded posting composition.
5. Bind Spring `Pageable` on `GET /api/v1/accounts/{accountId}/reconciliations` with default page 0, size 25, and `statementClosingAt,DESC`; enforce effective size 100 and the explicit sort policy above.
6. Replace the reconciliation cursor predicate with whitelisted ordering plus `LIMIT pageSize + 1 OFFSET pageable.offset`. Preserve the required owner/account filter, the pre-query owned-account check, detail SQL, lifecycle/staleness derivation, and response mapping.
7. Repository/query boundaries own the pageSize + 1 lookup, hasNext calculation, trimming, and construction of the final `SliceResponse<ActivityResponse>` or `SliceResponse<ReconciliationResponse>` because they own the pagination SQL. Endpoint-specific read paths may map directly to the stable response contract where no independent application read model exists. Do not add PaginationMetadata, an envelope base type, mapper, utility, service, or repository abstraction.
8. Introduce one small shared `dev.canverse.stocks.platform.web.SliceResponse<T>` record with `items`, `page`, `size`, and `hasNext`, plus a `from(Slice<T>)` factory. Delete `ActivityPageResponse` and `ReconciliationPageResponse`; do not serialize Spring's full `Slice` representation or retain feature-named collection fields.
9. Delete the ledger cursor codec/models and the account cursor response. Remove ledger cursor imports, constructor dependencies, filter-digest code, error keys used only by cursors, and cursor tests.
10. Configure the Spring Boot pageable maximum through `spring.data.web.pageable.max-page-size: 100`. Do not add a dependency, Java configuration class, `@EnableSpringDataWebSupport`, or custom resolver; Spring Data Commons is already present through the pinned Spring Data JPA starter.
11. Preserve all financial/accounting, owner, immutable fact/posting, posting composition, reconciliation lifecycle, idempotency, locking, version, projection, and no-store behavior.

## Explicit non-goals

- No device-session or instrument-search production/test change; that is the second Cleanup B implementation unit.
- No `CursorTokenCodec` deletion yet because instrument search remains a real consumer after PR-024.
- No migration, table, column, constraint, index, trigger, or seed change.
- No frontend, dependency, generated OpenAPI, or provider/network change.
- No total count, total pages, `Page`, count query, custom pagination wrapper, generic dynamic SQL sorter, or cursor compatibility layer.
- No accepted `cursor` alias, deprecated cursor parameter, `nextCursor` compatibility field, or translation from old cursors to page numbers.
- No change to accounting semantics, immutable activities/postings, reversal/correction, projection arithmetic, opening coverage, reconciliation rules, idempotency, locking, optimistic versions, activity response composition, or balance metadata.
- No validation/error redesign beyond rejecting unsupported resolved sorts through the existing boundary; Cleanup C remains separate.
- No service granularity, View/Command/Result, mapper, fingerprint, `LedgerTimingRules`, or business-error conversion cleanup; Cleanup D and other later work remain separate.
- No speculative index. Retain and measure the existing indexes before any future performance change.
- No Git operation.

## Deletion plan

### Delete in PR-024

- `src/main/java/dev/canverse/stocks/ledger/application/LedgerCursorCodec.java`
- `src/main/java/dev/canverse/stocks/ledger/application/model/AccountCursor.java`
- `src/main/java/dev/canverse/stocks/ledger/application/model/ActivityCursor.java`
- `src/main/java/dev/canverse/stocks/ledger/application/model/ReconciliationCursor.java`
- `src/main/java/dev/canverse/stocks/ledger/web/response/FinancialAccountPageResponse.java`
- `src/main/java/dev/canverse/stocks/ledger/web/response/ActivityPageResponse.java`
- `src/main/java/dev/canverse/stocks/ledger/web/response/ReconciliationPageResponse.java`
- `src/test/java/dev/canverse/stocks/ledger/LedgerCursorCodecTest.java`

`src/main/java/dev/canverse/stocks/platform/web/SliceResponse.java` is the shared HTTP response contract for the two PR-024 slices and remains available for the later Cleanup B2 decision.

### Delete in the second Cleanup B PR

- `src/main/java/dev/canverse/stocks/identity/application/SessionCursorCodec.java`
- `src/main/java/dev/canverse/stocks/identity/application/model/SessionCursor.java`
- `src/main/java/dev/canverse/stocks/identity/web/response/DeviceSessionPageResponse.java`
- `src/main/java/dev/canverse/stocks/reference/application/InstrumentSearchCursorCodec.java`
- `src/main/java/dev/canverse/stocks/reference/application/model/InstrumentSearchCursor.java`
- `src/main/java/dev/canverse/stocks/platform/application/CursorTokenCodec.java`, after the instrument codec is gone
- `src/test/java/dev/canverse/stocks/identity/SessionCursorCodecTest.java`
- cursor-specific methods/fixtures inside reference and session test classes as mapped below
- `IdentityErrorCode.INVALID_SESSION_CURSOR`
- `ReferenceErrorCode.INVALID_INSTRUMENT_CURSOR`

`InstrumentPageResponse` remains and changes to the compact slice contract. `InstrumentSearchCriteria` remains with business filters only. The family/instrument read models and repositories remain.

## Database and repository/SQL changes

Migration(s):

- None.

Tables/columns/constraints/indexes introduced or changed:

- None.

### Financial accounts

- Current predicate: owner scope plus `(:includeArchived OR archived_at IS NULL)` and, after the first page, `(a.name_normalized, a.id) > (:cursorName, :cursorId)`.
- Target: remove the tuple predicate and `LIMIT`; return all matching owner rows ordered by `a.name_normalized ASC, a.id ASC`.
- Preserve joins that populate coverage, projection balance, archive state, and policy-breach response fields.
- Existing `ix_ledger_financial_account_owner_name (owner_user_account_id, name_normalized, id)` remains sufficient. The active-name unique partial index remains a write invariant and is unchanged.

### Ledger activities

- Current predicate: owner scope, optional `p.financial_account_id = :accountId`, and `(a.recorded_at, a.id) < (:cursorRecordedAt, :cursorId)`; order is recorded time/ID descending.
- Target: no keyset predicate. Apply one hard-coded accepted order and `LIMIT :fetchLimit OFFSET :offset`, where `fetchLimit = pageable.getPageSize() + 1` and `offset = pageable.getOffset()`.
- Default/recorded sort SQL: `ORDER BY a.recorded_at <direction>, a.id <direction>`.
- Effective sort SQL: `ORDER BY a.effective_at <direction>, a.id <direction>`.
- Preserve `SELECT DISTINCT`, the owner-scoped activity/posting join, the optional account predicate, and the second bounded owner-scoped posting query. Trim the extra activity before composing postings so the look-ahead row is not included in response mapping.
- Existing `ix_ledger_activity_owner_recorded`, `ix_ledger_activity_owner_effective`, `ix_ledger_money_posting_account`, and `ix_ledger_money_posting_activity` cover the accepted access/filter/order paths sufficiently for current scale. Add no index.

### Reconciliations

- Current predicate: owner scope in `detailSql`, required `r.financial_account_id = :accountId`, and `(r.statement_closing_at, r.id) < (:cursorClosingAt, :cursorId)`; order is closing time/ID descending.
- Target: no keyset predicate. Apply `ORDER BY r.statement_closing_at <direction>, r.id <direction> LIMIT :fetchLimit OFFSET :offset` with size plus one.
- Preserve `accountAccess.owned(...)` before the list query, owner/account SQL predicates, all immutable snapshot columns, replacement/staleness subqueries, lifecycle precedence, and response calculations.
- Existing `ix_ledger_reconciliation_owner_account_closing (owner_user_account_id, financial_account_id, statement_closing_at DESC, id DESC)` exactly covers the default path and remains usable for reverse traversal. Add no index.

### Second Cleanup B query changes, recorded but not active

- Device sessions: remove `(created_at < cursorCreatedAt OR (created_at = cursorCreatedAt AND family_id < cursorFamilyId))` and `LIMIT`; retain the owner-scoped family CTE, complete generation aggregation, and `ORDER BY created_at DESC, family_id DESC`. Existing owner/family indexes remain sufficient for a naturally small list.
- Instrument search: remove tuple `> (cursorSymbol, cursorMarketCode, cursorInstrumentId)`; use `LIMIT pageSize + 1 OFFSET pageable.offset` and explicit `name`/`symbol` order fragments. Preserve owner/global visibility, active rules, query/market/type filters, literal prefix escaping, alias `EXISTS`, and bounded alias loading. Existing visibility, symbol/name-prefix, market/type, and alias-prefix indexes remain sufficient absent measured evidence.

## Application changes

- Controllers bind `Pageable` only on the two PR-024 ordinary collections. Keep controller methods thin and retain typed `@AuthenticationPrincipal` ownership.
- Query services no longer inject `LedgerCursorCodec`. They pass `Pageable` to the owning read repository and return the repository's final `SliceResponse<ActivityResponse>` or `SliceResponse<ReconciliationResponse>` directly. The endpoint-specific activity and reconciliation read paths map directly to response records; the account service returns an immutable complete list.
- Repositories consume `pageable.getPageSize()`, `pageable.getOffset()`, and the validated single `Sort.Order` directly. Each repository owns its small local property-to-SQL `switch`; do not create a cross-capability sorter. Sort validation and property-to-SQL mapping remain local to each affected endpoint/query. Do not introduce a shared sort policy, pagination helper, sort enum hierarchy, mapper, or utility as part of Cleanup B.
- The two repositories use only `Pageable` as input and the project-owned `SliceResponse<T>` as output. No Spring `Slice`/`SliceImpl`, `Page`, `PageImpl`, count supplier, or `COUNT(*)` query is introduced.
- `SliceResponse<T>` keeps an immutable list copy. `page` is the zero-based requested page, `size` is the effective/capped size, and `hasNext` is derived only from the extra row.

## API contract

All routes retain bearer authentication, server-derived owner identity, stateless handling, and no-store headers.

### Financial accounts

```http
GET /api/v1/accounts?includeArchived=false
```

Success is a direct JSON array:

```json
[]
```

There is no `limit`, `cursor`, page metadata, wrapper object, or `nextCursor` contract.

### Ledger activities

```http
GET /api/v1/activities?accountId=&page=0&size=50&sort=recordedAt,desc
```

Success:

```json
{
  "items": [],
  "page": 0,
  "size": 50,
  "hasNext": false
}
```

`accountId` remains optional. Supported primary sorts are `recordedAt` and `effectiveAt`; only one may be supplied.

### Reconciliations

```http
GET /api/v1/accounts/{accountId}/reconciliations?page=0&size=25&sort=statementClosingAt,desc
```

Success:

```json
{
  "items": [],
  "page": 0,
  "size": 25,
  "hasNext": false
}
```

Only `statementClosingAt` is accepted as the primary sort property.

### OpenAPI implications

- The repository currently has no Springdoc/OpenAPI dependency, annotations, generated specification, or checked-in schema to modify.
- The Java controller signatures and response records become the future source contract: pageable endpoints expose `page`, `size`, and `sort`; direct lists expose arrays; no cursor schema or `nextCursor` field remains.
- Do not add Springdoc or a generated schema in Cleanup B. Future OpenAPI work must describe the shared compact `SliceResponse<T>` rather than Spring's full serialized `Slice` representation.

## Business invariants

- Apply `accounting-contract.md` unchanged. Pagination selects already-readable facts; it never changes economic ordering, posting signs, immutability, correction, balance, coverage, or projection meaning.
- Owner scope remains in the first SQL predicate for every list and every posting-composition query.
- `includeArchived`, `accountId`, and reconciliation account filters retain their exact meanings.
- Default order and stable UUID tie-breaks are deterministic for a fixed dataset.
- Offset pagination intentionally does not promise cursor-style stable continuation under concurrent inserts. The current manual/file-driven owner-scoped product has no demonstrated requirement for that guarantee.
- Unsupported sort input can select only an error response, never an SQL identifier or fragment.

## Test migration

### Delete

- Delete `LedgerCursorCodecTest` completely; it proves only Base64 transport, canonical payloads, filter digests, and tamper/schema rejection for deleted code.
- Remove assertions/fixtures whose only purpose is `cursor`, `nextCursor`, filter-digest binding, or keyset continuation.
- In the second Cleanup B PR, delete `SessionCursorCodecTest`; remove the two cursor-only methods from `ReferenceValueObjectTest`; remove invalid cursor cases/error-code assertions from `DeviceSessionQueryServiceTest`, `DeviceSessionHttpTest`, and `ManualInstrumentHttpTest`.

### Rewrite in PR-024

- `FinancialAccountHttpTest.accountCursorsAreStableAndBoundToTheirFilter` becomes a complete-list test: active-only default, `includeArchived=true`, owner isolation, normalized name order, stable ID tie-break for equal normalized names where the data contract permits, empty list, direct-array shape, and absence of pagination fields.
- In `CashActivityHttpTest.activityRoutesRecordReplayListReverseAndReadTheProjection`, replace cursor traversal with page 0/page 1 offset assertions over a fixed dataset, exact `page`/`size`/`hasNext`, no duplicate within a page, account filter preservation, and unchanged posting composition. Keep the command/replay/reversal/balance assertions.
- `LedgerReconciliationHttpTest.reconciliationListUsesStableMultiRowCursorOrderingAndAccountFilterBinding` becomes a slice test covering same-closing-time ID tie order, page 0/page 1 offsets, `hasNext` true/false, account isolation, unsupported sort rejection, and empty/past-end behavior. Keep lifecycle/statement fields unchanged.
- Add focused pageable HTTP coverage for default sizes, effective maximum 100, Spring normalization/fallback for invalid page/size, supported ascending/descending sorts, rejected unsupported/compound sorts, and compact response shapes. Consolidate assertions in the existing HTTP classes rather than creating a pagination test framework.
- Add repository/Testcontainers assertions where HTTP setup cannot precisely prove the `LIMIT/OFFSET` boundary or stable ID tie. Test a fixed dataset; do not simulate Spring itself or concurrent-write cursor guarantees.

### Preserve in PR-024

- Preserve all ledger domain, migration, mapping, command-service, concurrency, rollback, idempotency, reversal, opening-correction, balance, reconciliation equation/lifecycle, and bearer-security tests except for necessary list-call/response-shape updates.
- Preserve existing cross-owner detail/mutation tests and extend list assertions only where needed to prove aggregate isolation.
- Preserve current exact activity posting order and reconciliation response arithmetic assertions.

### Second Cleanup B test mapping, recorded but not active

- Rewrite `DeviceSessionQueryServiceTest.keysetPaginationReturnsPagesWithoutGapsOrDuplicates` as complete logical-family list/order/no-duplicate coverage; retain aggregation, owner isolation, detail, status, current flag, and one-query assertions.
- Rewrite `DeviceSessionHttpTest` list assertions to a direct array and remove limit/cursor validation. Preserve detail and every revocation/cookie/security test.
- Rewrite `ReferenceCatalogQueryTest.instrumentSearchCursorsHaveNoGapsAndCannotCrossOwnerVisibility` for page offsets, `hasNext`, deterministic name/ID and symbol/market/ID orders, owner isolation, and the existing bounded two-query alias load.
- Rewrite the two search methods in `ManualInstrumentHttpTest` for `page`/`size`/`sort`, compact response fields, filters, literal wildcard escaping, aliases, inactive-owner visibility, second-page offsets, and owner isolation. Preserve all create/update/detail/validation/security behavior not tied to cursors.
- Preserve `ReferenceValueObjectTest` non-cursor value/entity tests and every reference migration/manual-instrument/security test not tied to pagination implementation details.

## Risk analysis

- **Ordering regressions:** losing direction or UUID tie order can reorder equal timestamps/names. Use complete hard-coded order clauses and fixed-ID tests for ties.
- **Offset mistakes:** using `page` as an offset or multiplying after requesting `size + 1` causes gaps. Always use `pageable.getOffset()` unchanged and apply plus one only to `LIMIT`.
- **`Slice.hasNext` mistakes:** returning the look-ahead row, checking after trimming, or using `>=` is wrong. Determine `hasNext` from `rows.size() > pageSize`, then trim to exactly `pageSize`.
- **Owner/filter leakage:** removing filter digests must not remove SQL filters. Keep owner predicates, `includeArchived`, activity account filtering, reconciliation account filtering, and owner-scoped posting/alias follow-up queries explicit.
- **Lost archive/search filters:** accounts must retain active-only default and opt-in archived rows; the second PR must retain every instrument query/visibility/alias filter independently of pagination.
- **SQL injection through sort:** never append `Sort.Order.property()` or direction text. Match accepted enum directions/properties to complete constant fragments and reject every other shape.
- **Response-shape mistakes:** direct lists must serialize as arrays; both slices must contain only `items`, `page`, `size`, and `hasNext`; no feature-named aliases, `nextCursor`, totals, or full Spring pagination graph may leak.
- **Accidental accounting changes:** do not touch command services, posting SQL, balance/reconciliation arithmetic, lifecycle subqueries, locks, idempotency snapshots, versions, or migrations. Focused and full ledger suites must stay green.
- **Accidental security changes:** typed principals, owner scope, no-store headers, bearer rules, cross-owner not-found behavior, session detail/revocation, and current-session status semantics remain unchanged.
- **Large-offset performance:** offset work can grow with page number, but current owner-scoped manual/file-driven scale has no measured problem. Do not retain keysets or add indexes speculatively; measure before any future optimization.

## Required tests

### Pure/domain

- None for financial mathematics; no domain behavior changes.
- Delete cursor codec unit coverage. A tiny local sort-policy unit test is optional only if the sort selection is not already proved clearly through repository/HTTP tests.

### PostgreSQL/Testcontainers

- Complete account list preserves owner/archive filters and deterministic name/ID order.
- Activity slices prove correct offset, plus-one trimming, `hasNext`, stable recorded/effective time plus ID order, posting composition, account filter, owner filter, and empty/past-end behavior.
- Reconciliation slices prove correct offset, plus-one trimming, `hasNext`, stable closing-time/ID order, account/owner isolation, and unchanged lifecycle mapping.
- Existing index inventory remains unchanged; no migration test expectation changes except confirming no new migration/index exists if the suite already inventories them.

### HTTP/security

- Exact account direct-array response with no pagination fields.
- Exact shared `items`-based activity/reconciliation slice responses, default/effective maximum paging, supported sorts, unsupported sort validation, first/second/empty page behavior, and no duplicates within a page.
- Existing bearer, no-store, statelessness, cross-owner not-found/isolation, command, balance, and reconciliation behavior remains green.

## Acceptance criteria

1. `GET /api/v1/accounts` returns the complete owner-scoped collection as a direct list, preserves `includeArchived`, and has deterministic name/ID order.
2. `GET /api/v1/activities` uses Spring `Pageable` and `Slice` semantics with default 50, effective maximum 100, zero-based offsets, explicit recorded/effective sort support, stable ID tie-breaks, and no count query.
3. `GET /api/v1/accounts/{accountId}/reconciliations` uses Spring `Pageable` and `Slice` semantics with default 25, effective maximum 100, explicit closing-time sort, stable ID tie-break, and no count query.
4. Activity and reconciliation responses use the same `SliceResponse<T>` JSON shape containing only `items`, `page`, `size`, and `hasNext`; account responses are arrays. No ledger response contains `nextCursor`.
5. Ledger controllers/services/repositories accept no cursor or legacy limit contract and contain no cursor decode/filter-binding path.
6. `LedgerCursorCodec`, all three ledger cursor records, `FinancialAccountPageResponse`, `ActivityPageResponse`, `ReconciliationPageResponse`, and `LedgerCursorCodecTest` are deleted; no production or test reference remains.
7. `CursorTokenCodec` and `CanonicalFingerprint` remain only for their real surviving consumers; no premature shared deletion breaks instrument search or idempotency.
8. SQL uses `Pageable` size/offset and complete whitelisted order fragments. No client-controlled property is interpolated into SQL.
9. Owner, archive, account, posting-composition, reconciliation lifecycle/staleness, immutable fact, correction, idempotency, locking, projection, balance, and security semantics are unchanged.
10. No migration, schema/index, dependency, frontend, OpenAPI generator, Cleanup C, or Cleanup D change is introduced.
11. Focused ledger PostgreSQL/HTTP tests, the full suite, Maven `verify`, and Spotless pass with no unresolved `MUST FIX` findings.
12. `STATE.md` is updated at implementation completion to describe actual list/`Pageable` behavior and remaining identity/reference cursor reality; the second Cleanup B PR is not activated automatically.

## Documentation completion

Before this implementation unit is considered complete:

1. Update `docs/implementation/STATE.md` with current repository reality only: ledger accounts are unpaged, ledger activities/reconciliations use compact Spring slice semantics, and identity/reference cursors still remain pending the second Cleanup B unit.
2. Replace the superseded ledger cursor statement; do not append implementation history.
3. Update authoritative architecture/domain documents only if implementation discovers a real contract change. The accepted governing pagination direction already covers this work, so no routine rewrite is expected.
4. Keep detailed deletions, deviations, tests, and decisions in this specification's Completion Record.
5. Leave `CURRENT.md` pointed to PR-024 until the user accepts implementation and controls the next transition.

## Verification commands

Use the command playbook before retrying a known Windows, Maven, Docker, Testcontainers, or output failure.

Focused pure/static and PostgreSQL/HTTP gate:

```powershell
.\mvnw.cmd "-Dtest=FinancialAccountHttpTest,CashActivityHttpTest,LedgerReconciliationHttpTest,FinancialAccountServiceTest,CashActivityServiceTest,LedgerReconciliationServiceTest,ApiBearerSecurityHttpTest" test
```

Then:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spotless:check
rg -n "LedgerCursorCodec|AccountCursor|ActivityCursor|ReconciliationCursor|nextCursor|RequestParam.*cursor|invalid_cursor" src/main/java/dev/canverse/stocks/ledger src/test/java/dev/canverse/stocks/ledger
```

The final ledger cursor search must return no production or test match. Search the full repository separately to confirm remaining matches belong only to the explicitly deferred identity/reference endpoint until its Cleanup B PR.

## Completion record

Fill this before marking the PR complete.

### Implemented

- `GET /api/v1/accounts` now returns the complete owner-scoped `List<FinancialAccountResponse>`, preserving `includeArchived` and `(name_normalized ASC, id ASC)` ordering.
- Activity and reconciliation controllers bind endpoint-local Spring `Pageable` defaults; Spring Boot YAML configuration caps effective sizes at 100.
- Ledger read repositories validate the resolved single sort order locally, map accepted properties to complete hard-coded SQL clauses, fetch `pageSize + 1` rows with `LIMIT/OFFSET`, trim lookahead rows, and construct the shared project-owned `SliceResponse<T>` HTTP contract directly. No Spring `Slice`/`SliceImpl` intermediate remains.
- Added `dev.canverse.stocks.platform.web.SliceResponse<T>` with `items`, `page`, `size`, `hasNext`, and immutable list-copying construction; removed its unused Spring `Slice` factory. Deleted the feature-specific `ActivityPageResponse` and `ReconciliationPageResponse` records; both pageable endpoints expose the same JSON field names.
- Activity and reconciliation read repositories now own size-plus-one lookup, trimming, `hasNext`, direct response mapping, and final `SliceResponse<T>` construction. Deleted `ActivityView`, `ReconciliationView`, and the HTTP-only `PostingView`; retained `FinancialAccountView` for account and balance consumers.
- Deleted `LedgerCursorCodec`, the three ledger cursor records, `FinancialAccountPageResponse`, `LedgerCursorCodecTest`, and `PageableConfiguration`. Retained shared `CursorTokenCodec` and `CanonicalFingerprint` for their remaining consumers. Added the Spring Boot `spring.data.web.pageable.max-page-size: 100` property.
- Extended `CashActivityHttpTest` with owner isolation, two-account filtering, posting composition, recorded-time ascending order, and effective-time UUID-tie coverage. Added representative lifecycle, resolution, and calculated-field assertions to the reconciliation list test.

### Deviations from specification

- The confirmed review requested removal of the redundant Spring `Slice` intermediate and HTTP-only activity/reconciliation read models; this is a directness correction within PR-024 and does not change query or financial behavior.

### New decisions

- Spring Boot configuration applies the 100-item resolver cap through `spring.data.web.pageable.max-page-size` in `src/main/resources/application.yml`; no Java configuration class or manual Spring Data web enablement remains.

### Tests executed

- `.\mvnw.cmd "-Dtest=FinancialAccountHttpTest,CashActivityHttpTest,LedgerReconciliationHttpTest,FinancialAccountServiceTest,CashActivityServiceTest,LedgerReconciliationServiceTest,ApiBearerSecurityHttpTest" test` — 77 passed, 0 failures, 0 errors, 0 skipped.
- `.\mvnw.cmd test` — 376 passed, 0 failures, 0 errors, 0 skipped.
- `.\mvnw.cmd verify` — 376 passed, 0 failures, 0 errors, 0 skipped; package and Spotless verification passed.
- `.\mvnw.cmd spotless:check` — 271 Java files clean.
- Final ledger cursor search — no matches; repository-wide cursor consumers are limited to the deferred identity/reference endpoints and the shared `CursorTokenCodec`.

### Follow-up work

- Cleanup B2: device-session direct list, instrument-search pageable slice, final shared cursor transport/error/test deletion.
- Cleanup C and Cleanup D remain separately user-controlled.
