# PR-025 - Cleanup B2 identity/reference pagination simplification

Status: **ACTIVE**

## Goal

Finish Cleanup B by removing the remaining premature custom cursor/keyset pagination. `GET /api/v1/auth/sessions` becomes one complete owner-scoped logical-family list, while `GET /api/v1/reference/instruments` uses Spring `Pageable`, offset SQL, `Slice` semantics, and the compact shared `SliceResponse<T>` HTTP representation. After both paths change, delete the now-unconsumed generic cursor transport and all endpoint-specific cursor models, codecs, errors, wrappers, and tests without changing session security or instrument search meaning.

## Capability and review boundary

- Coherent capability: device-session list and instrument search are the only production cursor consumers left after PR-024. Changing both completes the accepted Cleanup B pagination policy and makes final shared cursor transport deletion possible.
- Combined behaviors: session direct-list conversion, instrument `Pageable` binding, explicit sort-to-SQL mapping, offset/size-plus-one handling, compact response conversion, endpoint cursor deletion, final `CursorTokenCodec` deletion, and behavior-focused test migration must land together or the repository would retain dead or partially used cursor infrastructure.
- Excluded neighbor: Cleanup C validation/error simplification and Cleanup D model/mapping/fingerprint work remain separate. This PR removes only models and errors whose sole purpose disappears with pagination.
- Focused review: one reviewer can inspect two controllers, two query services, two `JdbcClient` read paths, the exact deletion set, and the directly affected identity/reference tests as one bounded unit. Session security and reference search have different domain semantics, but their change surfaces are small and tightly coupled by final cursor-stack deletion.
- One PR is preferable because splitting the endpoints again would either leave `CursorTokenCodec` temporarily alive without a meaningful future boundary or create a third mechanical deletion-only unit.

## Source documents

- `AGENTS.md` - planning, scope isolation, Git ownership, and context-maintenance rules.
- `docs/implementation/README.md` and `docs/implementation/PR-TEMPLATE.md` - active-spec lifecycle and specification format.
- `docs/implementation/STATE.md` and `docs/implementation/CURRENT.md` - accepted baseline and active pointer.
- `docs/engineering/coding-standards.md` - sections 2-4, 6, 8, 10, 11, and 13.
- `docs/review/backend-master-plan.md` - standardization conventions, API invariants, R1-R2, R16, and Stage 12.
- `docs/implementation/PR-019-authenticated-identity-and-session-security-lifecycle.md` - historical authority for logical session-family aggregation, status, ownership, detail, revocation, cookie, and security behavior; its cursor contract is historical rather than a compatibility requirement.
- `docs/implementation/PR-020-canonical-reference-catalog-and-manual-instruments.md` - historical authority for owner/global visibility, manual instruments, search filters, alias behavior, bounded query count, and search summary shape; its cursor contract is historical rather than a compatibility requirement.
- `docs/implementation/PR-023-governing-simplicity-standards.md` - accepted no-pagination/`Pageable`/`Slice`/`Page` hierarchy and evidence-based abstraction rule.
- `docs/implementation/PR-024-ledger-pagination-simplification.md` - accepted Cleanup B1 behavior, global maximum-page-size configuration, compact `SliceResponse<T>`, local sort validation, direct response construction, and final B2 boundary.
- Current Spring Data documentation/source for `Pageable`, `@PageableDefault`, and `Slice` semantics. A slice fetches one look-ahead row and determines `hasNext` without the count query required by a `Page`.

## Starting state

- PR-023 and PR-024 are accepted and committed. PR-024 already changed financial accounts to a direct list; changed activities and reconciliations to `Pageable` plus compact `SliceResponse<T>`; deleted ledger cursor infrastructure; and configured `spring.data.web.pageable.max-page-size: 100`.
- The application is unreleased and the preserved frontend will be rewritten. Existing session/instrument `limit`, `cursor`, `nextCursor`, cursor error, cursor payload, and cursor filter-digest contracts require no compatibility alias, migration, or deprecation period.
- `GET /api/v1/auth/sessions` still returns `DeviceSessionPageResponse`, defaults `limit` to 25, accepts 1-100, decodes a `SessionCursor`, fetches `limit + 1`, and applies a family keyset predicate.
- `GET /api/v1/reference/instruments` still returns `InstrumentPageResponse`, defaults `limit` to 25, accepts 1-100, stores pagination fields in `InstrumentSearchCriteria`, hashes normalized filters, decodes `InstrumentSearchCursor`, fetches `limit + 1`, and applies a symbol/market/ID keyset predicate.
- Flyway V1-V4, the current identity/session lifecycle, V2 reference catalogue, and existing indexes remain sufficient. No schema, migration, index, dependency, or additional pagination configuration is required.

## Repository findings

### Device sessions

- `DeviceSessionController.listSessions` currently binds nullable `limit` and `cursor` request parameters and returns `DeviceSessionPageResponse` with no-store headers.
- `DeviceSessionQueryService.listSessions` owns the 25/1/100 limit constants, cursor decoding, look-ahead trimming, `nextCursor` construction, one clock observation, and mapping from `DeviceSessionFamilyRecord` to `DeviceSessionResponse`.
- `DeviceSessionReadRepository.findFamilies` duplicates the family CTE for cursor and first-page branches. Both aggregate one row per `family_id`; the cursor branch adds the keyset predicate and both branches apply `LIMIT`.
- Actual deterministic family order is `MIN(s.created_at) DESC, family_id DESC`. The response `createdAt` is the earliest generation creation time, not an arbitrary generation timestamp.
- The family aggregate also preserves the latest terminal generation ID/label/revocation data, latest `last_used_at`, minimum/maximum expiry consistency check, and `BOOL_OR(s.id = :currentSessionId)` current-family marker.
- `DeviceSessionResponse.from` maps exact statuses `ACTIVE`, `EXPIRED`, `REVOKED`, and `COMPROMISED`; replacement remains generation history inside one logical family rather than a separate public family status.
- List and detail each currently execute one SQL statement. Detail, selected revocation, logout, cookie clearing, and bearer eligibility do not depend on pagination.

### Instrument search

- `ManualInstrumentController.search` currently binds `query`, `marketId`, `type`, `includeInactive`, `limit`, and `cursor`; it returns `InstrumentPageResponse`.
- `InstrumentSearchCriteria` is a genuine use-case filter record, but its `limit` and `cursor` components are transport pagination fields. Its retained business fields are `query`, `marketId`, `type`, and `includeInactive`.
- `InstrumentSearchService` trims and uppercases the optional query with `Locale.ROOT`, enforces the normalized 1-64 character rule, computes a cursor filter digest, decodes/encodes cursors, trims the look-ahead result, maps summaries, and constructs the cursor page response.
- `ReferenceCatalogReadRepository.searchInstruments` enforces owner/global visibility in SQL: active global rows plus the current owner's active rows by default, with `includeInactive=true` adding only that owner's inactive rows. Cross-owner and inactive global rows remain excluded.
- Search uses optional exact market/type filters and literal prefix matching over normalized symbol, name, or alias. `escapeLikePrefix` escapes `\`, `%`, and `_`, and SQL uses an explicit escape character.
- Current production order is `symbol_normalized ASC, market.code_normalized ASC, instrument.id ASC`; current code has no client-visible sort input. Accepted PR-024/master-plan direction intentionally makes `name ASC` the new pageable default while retaining the current catalogue order under explicit `sort=symbol,...`.
- Search rows are `InstrumentSummaryResponse`, not `InstrumentResponse`. The summary includes `ownerManaged` and omits owner ID, version, and timestamps; the detail response has the inverse detail-oriented shape. Pagination cleanup must retain the summary item contract.
- `ReferenceCatalogReadRepository.InstrumentView` is a genuine infrastructure-owned row-plus-alias aggregate used by both visible detail (`InstrumentResponse`) and search (`InstrumentSummaryResponse`). It is not an HTTP-only pagination wrapper and remains.
- Alias loading is bounded to one instrument query plus one alias query for a non-empty result, independent of result count. Alias order is `(instrument_id, alias_type, alias_normalized, id)` and aliases are bounded by the existing 32-alias invariant.

### Final cursor infrastructure

- Production-wide source search finds only the device-session and instrument-search cursor paths described above.
- `CursorTokenCodec` has exactly one remaining production consumer: `InstrumentSearchCursorCodec`. Once that codec is deleted, `CursorTokenCodec` has no production consumer and must be deleted.
- `SessionCursorCodec` is standalone and used only by the session list path and `SessionCursorCodecTest`.
- `IdentityErrorCode.INVALID_SESSION_CURSOR` and `ReferenceErrorCode.INVALID_INSTRUMENT_CURSOR` are referenced only by their respective cursor codecs/tests and become dead.
- `CanonicalFingerprint` remains required by six ledger idempotency workflows. Delete the instrument cursor's fingerprint use, but do not change or delete `CanonicalFingerprint` or its real idempotency consumers.
- `ObjectMapper` remains broadly used and is not part of the deletion decision.

## Endpoint decision table

| Endpoint                            | Current behavior                                            | Target behavior                            | Default / maximum | Accepted sort                                      | Complete stable SQL order                                                                 | HTTP response                                      | Decision |
| ----------------------------------- | ----------------------------------------------------------- | ------------------------------------------ | ----------------- | -------------------------------------------------- | ----------------------------------------------------------------------------------------- | -------------------------------------------------- | -------- |
| `GET /api/v1/auth/sessions`         | cursor page, default 25, maximum 100                         | complete owner-scoped `List`               | N/A               | none                                               | family `created_at DESC, family_id DESC`                                                  | direct JSON array of `DeviceSessionResponse`       | Logical device families are naturally small; totals and continuation have no demonstrated value. |
| `GET /api/v1/reference/instruments` | cursor page ordered by normalized symbol/market/ID, 25/100  | Spring `Pageable` with `Slice` semantics   | 25 / 100          | exactly one of `name` or `symbol`, either direction | `name_normalized, id` in the requested direction; or `symbol_normalized, market.code_normalized, id` in the requested direction | `SliceResponse<InstrumentSummaryResponse>`         | Search is an ordinary potentially large collection; `hasNext` is sufficient and totals have no demonstrated value. |

Neither endpoint uses `Page`, total elements, total pages, or a count query. Device sessions do not bind `Pageable` merely for consistency.

## Scope

1. Change `GET /api/v1/auth/sessions` to bind no pagination parameter and return the complete owner-scoped `List<DeviceSessionResponse>` as a direct JSON array.
2. Simplify `DeviceSessionQueryService.listSessions` to accept only owner and current-generation IDs, observe the clock once, map every returned logical family, and return an immutable list. Remove limit constants, validation, cursor decoding, look-ahead trimming, and `nextCursor` construction.
3. Collapse `DeviceSessionReadRepository.findFamilies` to one owner-scoped family aggregate query with no cursor parameter, keyset predicate, `fetchLimit`, or `LIMIT`. Preserve `ORDER BY created_at DESC, family_id DESC` and every aggregate expression used by list/detail status mapping.
4. Leave `GET /api/v1/auth/sessions/{familyId}`, `DELETE /api/v1/auth/sessions/{familyId}`, logout scopes, revocation transactions/locking, security events, refresh rotation/reuse handling, cookie behavior, and bearer security unchanged.
5. Change `GET /api/v1/reference/instruments` to bind Spring `Pageable` with endpoint-local default page 0, size 25, and `name,ASC`. Reuse the existing Boot global maximum of 100.
6. Retain `InstrumentSearchCriteria` with only `query`, `marketId`, `type`, and `includeInactive`; pass `Pageable` separately through the search service to the read repository.
7. Preserve query normalization and every visibility/filter rule. Remove limit validation, cursor/filter-digest calculation, cursor decode/encode work, and cursor collaborators from `InstrumentSearchService`.
8. Change `ReferenceCatalogReadRepository.searchInstruments` to consume `Pageable`, validate exactly one supported sort order locally, select one complete hard-coded SQL order clause, and use `LIMIT :fetchLimit OFFSET :offset` where `fetchLimit = pageable.getPageSize() + 1` and `offset = pageable.getOffset()`.
9. Determine `hasNext` before trimming. Exclude the look-ahead row before alias loading and response mapping so aliases remain bounded to response items. Preserve at most one instrument statement plus one alias statement for a non-empty slice and no count statement.
10. Return the existing shared `SliceResponse<InstrumentSummaryResponse>` containing only `items`, `page`, `size`, and `hasNext`. The endpoint-specific read path may construct it directly; do not introduce Spring `Slice`, `SliceImpl`, `Page`, `PageImpl`, or an application pagination view solely for layering.
11. Accept only one primary sort property: `name` or `symbol`, with `ASC` or `DESC`, `ignoreCase=false`, and native null handling. Reject unsupported properties, compound orders, ignore-case, and non-native null handling through the existing `VALIDATION_FAILED` architecture with field `sort`.
12. Map sorts only through a local `switch` to the complete constant fragments specified below. Never interpolate a client property or free-form direction into SQL.
13. Delete every cursor-only production/test artifact listed in the deletion plan after repository-wide consumer searches prove it is dead.
14. Preserve the existing `spring.data.web.pageable.max-page-size: 100` setting. Add no Java pageable configuration, custom resolver, dependency, migration, index, or frontend change.

## Explicit non-goals

- No Cleanup C work.
- No Cleanup D work beyond deleting tiny models/wrappers whose only purpose is the removed pagination transport.
- No generic validation redesign, business-error redesign, or new error framework.
- No service or repository merging/splitting based on file count.
- No removal of `InstrumentSearchCriteria`, `InstrumentView`, `InstrumentResponse`, `InstrumentSummaryResponse`, `DeviceSessionFamilyRecord`, or other genuine query/domain models.
- No fingerprint cleanup, extraction, relocation, or `CanonicalFingerprint` implementation change.
- No MyBatis migration or audit; the read-persistence/MyBatis question remains parked for much later.
- No `JdbcClient` redesign or parallel read implementation.
- No schema, migration, constraint, trigger, seed, or index change absent measured evidence.
- No total count, total pages, Spring `Page`, `COUNT(*)`, custom pagination abstraction, custom `Pageable`, generic query result, shared sorter, sort mapper hierarchy, or pagination test framework.
- No cursor compatibility alias, deprecated cursor parameter, legacy limit alias, token-to-page translation, or `nextCursor` compatibility field.
- No synchronization/change-feed token or ordinary-list cursor replacement.
- No identity/session lifecycle redesign, physical session deletion/history compaction, new session count limit, or device metadata expansion.
- No instrument search redesign, fuzzy/full-text/trigram search, new visibility rule, global administration, provider/network behavior, or manual-instrument command change.
- No frontend or preserved `src/main/web` change.
- No Springdoc/OpenAPI dependency, tooling, annotations, or generated schema.
- No investing/R4 implementation.
- No automatic activation of Cleanup C or Cleanup D.
- No Git operation; the user owns commit and lifecycle transitions.

## Deletion plan

### Production files

- `src/main/java/dev/canverse/stocks/identity/application/SessionCursorCodec.java`
- `src/main/java/dev/canverse/stocks/identity/application/model/SessionCursor.java`
- `src/main/java/dev/canverse/stocks/identity/web/response/DeviceSessionPageResponse.java`
- `src/main/java/dev/canverse/stocks/reference/application/InstrumentSearchCursorCodec.java`
- `src/main/java/dev/canverse/stocks/reference/application/model/InstrumentSearchCursor.java`
- `src/main/java/dev/canverse/stocks/reference/web/response/InstrumentPageResponse.java`
- `src/main/java/dev/canverse/stocks/platform/application/CursorTokenCodec.java`, only after the instrument codec is removed and a production-wide search confirms no consumer

### Error constants

- `IdentityErrorCode.INVALID_SESSION_CURSOR`
- `ReferenceErrorCode.INVALID_INSTRUMENT_CURSOR`

### Tests and cursor-only test content

- Delete `src/test/java/dev/canverse/stocks/identity/SessionCursorCodecTest.java` completely.
- Remove the cursor codec fixture and the two cursor-specific methods from `ReferenceValueObjectTest`; preserve all non-cursor value-object, enum, entity, and time-zone tests.
- Remove cursor traversal, malformed-cursor, filter-digest, `limit`, `nextCursor`, and cursor ownership fixtures/assertions from `DeviceSessionQueryServiceTest`, `DeviceSessionHttpTest`, `ReferenceCatalogQueryTest`, and `ManualInstrumentHttpTest`; replace them with the behavior tests below.
- Remove no test merely because a fixture display value contains the word `cursor`; delete or rename only content whose behavior is cursor-specific.

### Must remain

- `src/main/java/dev/canverse/stocks/platform/application/CanonicalFingerprint.java` and all ledger idempotency consumers.
- `src/main/java/dev/canverse/stocks/platform/web/SliceResponse.java`.
- `InstrumentSearchCriteria`, `ReferenceCatalogReadRepository.InstrumentView`, `InstrumentResponse`, `InstrumentSummaryResponse`, `DeviceSessionFamilyRecord`, and their genuine consumers.
- All detail, mutation, revocation, cookie, security, manual-instrument, and reference-catalog error codes/tests unrelated to cursor transport.

## Database and repository/SQL changes

Migration(s):

- None.

Tables/columns/constraints/indexes introduced or changed:

- None.

### Device-session family list

- Remove the cursor branch and keyset predicate:

```sql
created_at < :cursorCreatedAt
OR (created_at = :cursorCreatedAt AND family_id < :cursorFamilyId)
```

- Remove `LIMIT :fetchLimit` and all cursor/fetch-limit parameters.
- Keep the owner predicate inside the family CTE:

```sql
WHERE s.user_account_id = :userAccountId
```

- Keep one row per logical family and the exact aggregate semantics for earliest creation, latest use, common expiry validation, terminal generation/label/revocation state, and current-family membership.
- Keep the final order exactly:

```sql
ORDER BY created_at DESC, family_id DESC
```

- Existing `ix_device_session_user_account_id`, `ix_device_session_family_id`, and `uix_device_session_active_family` remain unchanged. The collection is deliberately unpaged and naturally small; add no speculative list-order index.

### Instrument search

- Preserve the current visibility predicate, optional normalized query predicate, alias `EXISTS`, exact market/type filters, and bound parameters.
- Remove the tuple keyset predicate and its parameters:

```sql
(i.symbol_normalized, m.code_normalized, i.id)
    > (:cursorSymbol, :cursorMarketCode, :cursorInstrumentId)
```

- Use exactly these complete hard-coded order clauses:

| Client sort  | SQL order clause |
| ------------ | ---------------- |
| `name,asc`   | `ORDER BY i.name_normalized ASC, i.id ASC` |
| `name,desc`  | `ORDER BY i.name_normalized DESC, i.id DESC` |
| `symbol,asc` | `ORDER BY i.symbol_normalized ASC, m.code_normalized ASC, i.id ASC` |
| `symbol,desc` | `ORDER BY i.symbol_normalized DESC, m.code_normalized DESC, i.id DESC` |

- Append only parameterized pagination SQL:

```sql
LIMIT :fetchLimit OFFSET :offset
```

- Bind `fetchLimit` to effective page size plus one and `offset` to `pageable.getOffset()` without recomputing it from the look-ahead size.
- Determine `hasNext` from `rows.size() > pageSize`, trim to at most `pageSize`, then load aliases only for retained instrument IDs. Empty slices perform no alias query.
- Preserve alias order and immutable grouping. Do not introduce an N+1 query.
- Existing global/owner visibility, name-prefix, market/type, and alias-prefix indexes remain unchanged. The default name sort has no measured offset-performance problem requiring a new compound index; measure before any later index proposal.

## Application changes

- `DeviceSessionController.listSessions` returns `ResponseEntity<List<DeviceSessionResponse>>`, retains typed `@AuthenticationPrincipal`, no-store headers, status 200, and stateless behavior, and binds neither `limit`, `cursor`, nor `Pageable`.
- `DeviceSessionQueryService` remains the clock/status mapping boundary and returns the complete mapped list. `DeviceSessionReadRepository` remains the SQL family-aggregation owner. Do not merge list/detail/revocation services.
- `ManualInstrumentController.search` binds business filters plus endpoint-local `@PageableDefault(size = 25, sort = "name", direction = Sort.Direction.ASC)` and returns `ResponseEntity<SliceResponse<InstrumentSummaryResponse>>`.
- `InstrumentSearchService` remains the owner of optional query normalization/validation and passes normalized criteria plus `Pageable` to the read repository. It no longer injects a cursor codec.
- `ReferenceCatalogReadRepository` owns local sort validation, complete order-fragment selection, offset/limit work, look-ahead trimming, bounded alias loading, mapping to `InstrumentSummaryResponse`, and direct `SliceResponse` construction because it owns the pagination query.
- Keep `InstrumentView` as the genuine internal row-plus-alias aggregate shared by detail and search mapping. Do not preserve or add a separate application slice/view solely for layer purity.
- Use the existing `ValidationErrors.invalidField` path for rejected sort shapes and a capability-local key such as `error.fields.reference.invalid_sort`. Do not add a new `ReferenceErrorCode` or redesign validation.
- `SliceResponse<T>` remains unchanged: it copies `items`, reports the zero-based resolved page, the effective/capped size, and `hasNext` from the extra row.

## API contract

All routes retain bearer authentication, server-derived owner identity, `Cache-Control: no-store`, `Pragma: no-cache`, and no servlet session creation.

### Device sessions

```http
GET /api/v1/auth/sessions
```

Success is a direct JSON array:

```json
[
  {
    "familyId": "<family UUID>",
    "latestGenerationId": "<terminal generation UUID>",
    "deviceLabel": "<nullable label>",
    "createdAt": "<initial generation instant>",
    "lastUsedAt": "<nullable latest use instant>",
    "expiresAt": "<absolute family expiry>",
    "endedAt": null,
    "status": "ACTIVE",
    "current": true
  }
]
```

The endpoint declares no `page`, `size`, `sort`, `limit`, or `cursor` input. The response has no wrapper, `sessions`, `page`, `size`, `hasNext`, `cursor`, or `nextCursor` field. Do not add special rejection or compatibility behavior for undeclared legacy query parameters.

Session detail and selected revocation contracts are unchanged:

```http
GET /api/v1/auth/sessions/{familyId}
DELETE /api/v1/auth/sessions/{familyId}
```

### Instrument search

```http
GET /api/v1/reference/instruments?query=&marketId=&type=&includeInactive=false&page=0&size=25&sort=name,asc
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

- `page` is zero-based.
- Default effective size is 25; the existing Boot resolver caps requested sizes at 100.
- Default sort is `name,asc`. Accepted client sorts are exactly one `name` or `symbol` order in either direction.
- Item JSON remains the existing `InstrumentSummaryResponse` shape, including `ownerManaged` and aliases. It does not become the richer `InstrumentResponse` detail shape.
- The response contains only `items`, `page`, `size`, and `hasNext`; there is no `instruments`, `nextCursor`, total count, total pages, or serialized Spring `Slice` graph.
- The endpoint declares no legacy `limit` or `cursor` input and provides no compatibility alias.
- Unsupported sort shape returns the existing RFC 9457 `422 VALIDATION_FAILED` contract for field `sort`. Malformed enum/UUID/query inputs retain their existing boundary behavior.

## Business and security invariants

### Device sessions

- Authenticated owner/current-generation identity comes only from the validated bearer principal.
- The list/detail queries remain owner-scoped in SQL; cross-owner family detail remains indistinguishable from missing as `404 SESSION_NOT_FOUND`.
- One list row still represents one logical family and collapses every refresh-token generation without losing current, expiry, revocation, or compromise meaning.
- Family `createdAt`, `lastUsedAt`, `expiresAt`, `latestGenerationId`, label, `endedAt`, status, and current marker retain the exact accepted PR-019 derivation.
- The list and detail each observe the injected clock once. Pagination removal must not cause per-family queries or entity loading.
- No generation is deleted or rewritten. Refresh rotation, reuse response, owner locking, selected/current/all revocation, security events, bearer invalidation, idempotent selected deletion, and exact cookie clearing remain unchanged.

### Instrument search

- SQL returns only active global instruments plus the authenticated owner's allowed instruments. `includeInactive` exposes only the requesting owner's inactive instruments and never inactive global or cross-owner rows.
- Optional `query` remains trimmed, normalized with `Locale.ROOT`, limited to 1-64 characters after trimming, and applied as a literal prefix to normalized symbol, name, and alias.
- Optional `marketId` and `type` remain exact filters. Manual instruments, active flags, source provenance, owner-managed marker, and alias contents/order remain unchanged.
- Alias collisions continue to return all visible candidates and never select one implicitly.
- Default and explicit sorts are deterministic for a fixed dataset and use normalized stored values plus complete tie-breakers.
- Offset pagination intentionally does not promise cursor-stable continuation across concurrent inserts or updates. No demonstrated product requirement justifies that guarantee.
- Client-controlled values can select only one predeclared SQL fragment or a validation error; they can never become an SQL identifier or direction fragment.

## Test migration

### Device sessions

- Delete `SessionCursorCodecTest` and remove invalid-cursor/list-limit tests when their production contracts disappear.
- Rewrite `DeviceSessionQueryServiceTest.keysetPaginationReturnsPagesWithoutGapsOrDuplicates` as complete-list coverage over multiple families: exact row count, no duplicate family IDs, and exact `createdAt DESC, familyId DESC` ordering, including equal-created-at UUID ties.
- Update aggregation/list tests for the direct `List<DeviceSessionResponse>` return while preserving rotated-family collapse, latest-generation selection, device label, last use, expiry consistency, current marker, and one-statement assertion.
- Preserve or strengthen status mapping coverage for active, expiry equality/expired, user-revoked, and reuse-compromised families. Replacement remains covered through multi-generation aggregation rather than a nonexistent public `REPLACED` status.
- Preserve owner-isolated list/detail behavior and cross-owner `SESSION_NOT_FOUND`.
- Rewrite `DeviceSessionHttpTest` list assertions to require an exact direct-array shape, deterministic order, complete family set, no duplicate family, no pagination wrapper/fields, no-store headers, bearer authentication, and no servlet session.
- Preserve all session detail, current/all/selected revocation, idempotent deletion, refresh/cookie, event, concurrency, statelessness, and bearer-security tests. Do not broaden them merely to test unpaged list size mechanics.

### Instrument search

- Remove the cursor codec fixture and two cursor-only tests from `ReferenceValueObjectTest`; retain every other value/domain test.
- Rewrite `ReferenceCatalogQueryTest.instrumentSearchCursorsHaveNoGapsAndCannotCrossOwnerVisibility` as pageable offset coverage using fixed IDs and `Pageable`: first, second, and past-end pages; exact `page`/`size`/`hasNext`; no duplicates within each page; and no cross-owner rows.
- Retain the existing search-filter test and adapt it to `SliceResponse.items`. Preserve symbol/name/alias prefix matches, market/type filters, owner/global visibility, owner-only inactive inclusion, exclusion of inactive global/cross-owner rows, and literal `%`, `_`, and `\` escaping.
- Add exact deterministic order fixtures for `name` ASC/DESC using normalized name plus ID ties and for `symbol` ASC/DESC using normalized symbol, normalized market code, and ID ties.
- Prove the default page/size/sort, requested sizes capped to 100, first/second/past-end slices, and compact four-field response shape in `ManualInstrumentHttpTest`.
- Prove unsupported property and compound sort rejection through HTTP. Prove ignore-case and non-native null-handling rejection at the service/repository boundary if those variants cannot be expressed reliably through the HTTP resolver.
- Preserve the bounded query-count invariant: one instrument statement plus one alias statement for a non-empty slice, no alias N+1, no alias query for an empty slice, and no count query. Ensure the look-ahead row's aliases are not loaded.
- Preserve manual create/detail/update, alias replacement, optimistic conflicts, owner isolation, inactive/manual behavior, malformed enum/UUID/query handling, no-store, statelessness, bearer security, and offline/no-provider tests except for necessary search call/shape updates.
- Consolidate new assertions into the existing test classes. Do not introduce a pagination test framework, generic fixtures, or Spring resolver reimplementation.

## Risk analysis

- **Session family regression:** simplifying the duplicated query could accidentally change `MIN`/`MAX`/terminal-generation/current aggregation. Retain the exact CTE projections and prove multi-generation/status behavior.
- **Session order regression:** the public family timestamp is the earliest generation time. Sort the aggregate alias `created_at`, then `family_id`, both descending; do not sort by latest generation or last use.
- **Security/cookie regression:** list signature changes share a controller with detail/revocation. Keep revocation service calls, current-family cookie clearing, typed principal, owner predicates, and no-store headers untouched and run the existing security/revocation suites.
- **Intentional default-order change:** current instrument search defaults to symbol/market/ID; B2 intentionally adopts `name ASC` from the accepted PR-024/master-plan direction. Pin both the new default and the retained explicit symbol order so this is reviewable rather than accidental.
- **Response-item widening:** `InstrumentResponse` is not the current search item. Using it would expose owner/version/timestamps and lose `ownerManaged`; retain `InstrumentSummaryResponse` inside `SliceResponse`.
- **SQL injection through sort:** never append `Sort.Order.property()` or free-form direction. Use four complete constant fragments and reject every other order shape.
- **Offset mistakes:** use `pageable.getOffset()` unchanged. Add one only to `LIMIT`, never to page or offset; determine `hasNext` before trimming.
- **Look-ahead alias work:** loading aliases before trimming would include the extra row. Trim row IDs first, then perform the one bounded alias query.
- **Owner/filter leakage:** deleting filter digests must not delete SQL filters. Keep owner/global, active, query, market, type, and alias predicates independently tested.
- **Stable-order mistakes:** use normalized name/symbol/market columns and apply the same requested direction to every tie-break column.
- **Large offsets/default-name performance:** offset cost can grow and name sorting has no new composite owner index, but the repository has no measured problem. Do not retain keysets or add an index speculatively; measure before a later change.
- **Over-deletion:** `CursorTokenCodec` becomes dead, but `CanonicalFingerprint`, `InstrumentView`, both instrument response types, and session/read models have independent consumers and must remain.
- **Framework-shape leakage:** returning Spring `Slice` would expose an unstable framework JSON graph; construct the project-owned four-field response directly.

## Required tests

### Pure/domain

- Delete pure cursor codec coverage whose production code is deleted.
- Preserve session family status derivation for active, expired-at-equality, revoked, and compromised results.
- Preserve all non-cursor reference value-object, enum, normalization, instrument lifecycle, and alias invariant tests.

### PostgreSQL/Testcontainers

- Device-session list returns every owned logical family exactly once, aggregates generations correctly, marks the current family, maps lifecycle state correctly, and orders by initial creation/ID descending in one SQL statement.
- Session detail remains one owner-scoped SQL statement and cross-owner/missing behavior remains exact.
- Instrument slices prove correct offset, size-plus-one trimming, `hasNext`, empty/past-end behavior, default name order, name ASC/DESC, symbol ASC/DESC, normalized tie-breaks, and no count query.
- Instrument search preserves owner/global/inactive visibility, query/market/type filters, alias matching, literal wildcard/backslash escaping, summary item mapping, deterministic alias order, and owner isolation.
- Non-empty instrument slices use exactly two bounded statements independent of item count; empty slices use no alias query and the look-ahead row is not alias-loaded.
- Existing V1-V4 migration/index inventory remains unchanged.

### HTTP/security

- Exact session direct-array response, complete logical-family contents/order, absence of pagination fields/wrapper, current marker/status, no-store headers, bearer enforcement, and statelessness.
- Existing session detail/revocation/logout/cookie/concurrency/security tests remain green.
- Exact instrument `items/page/size/hasNext` response, default page 0/size 25/name ascending, effective maximum 100, first/second/past-end behavior, and no cursor/page-specific legacy fields.
- Supported name/symbol directions work; unsupported/compound sorts return the existing trace-correlated `422 VALIDATION_FAILED` contract for `sort`.
- Existing instrument filter, alias, inactive/manual, owner isolation, create/detail/update, conflict, no-store, bearer, stateless, and no-network behavior remains green.

## Acceptance criteria

1. `GET /api/v1/auth/sessions` returns one complete owner-scoped direct array of `DeviceSessionResponse` and declares no pagination input.
2. Session list order is exactly initial family `createdAt DESC, familyId DESC`; logical generation aggregation, current marker, status/ended-at mapping, one-query behavior, detail, revocation, cookie, and security semantics are unchanged.
3. Session controller/service/repository code contains no limit/cursor decoding, keyset predicate, look-ahead pagination, or `nextCursor` path.
4. `GET /api/v1/reference/instruments` binds Spring `Pageable` with default page 0, size 25, `name ASC`, effective maximum 100, and no legacy limit/cursor contract.
5. Instrument search uses `Slice` semantics with offset SQL and no count query. It reports correct `page`, effective `size`, and `hasNext`, and never returns the look-ahead row.
6. Instrument response JSON contains only `items`, `page`, `size`, and `hasNext`; each item retains the exact `InstrumentSummaryResponse` search shape.
7. Instrument filters, query normalization, literal wildcard escaping, alias matching/loading/order, owner/global visibility, owner-only inactive behavior, manual/source semantics, and owner isolation are unchanged.
8. Default/name sorting uses normalized name plus ID; symbol sorting preserves normalized symbol, normalized market code, and ID. ASC/DESC applies consistently to the complete order.
9. Exactly one supported sort order is accepted. Unsupported properties, compound orders, ignore-case, and non-native null handling cannot reach SQL and use the existing validation boundary.
10. Instrument SQL uses only four complete hard-coded order fragments plus parameterized `LIMIT/OFFSET`; no client property/direction is interpolated.
11. `InstrumentSearchCriteria` remains with business filters only; `Pageable` is separate. `InstrumentView`, `InstrumentResponse`, and `InstrumentSummaryResponse` remain for their proven independent semantics.
12. `SessionCursorCodec`, `SessionCursor`, `DeviceSessionPageResponse`, `InstrumentSearchCursorCodec`, `InstrumentSearchCursor`, `InstrumentPageResponse`, `CursorTokenCodec`, both cursor error constants, `SessionCursorCodecTest`, and cursor-only reference/session test content are deleted with no source reference remaining.
13. `CanonicalFingerprint` and every real idempotency consumer remain unchanged.
14. `spring.data.web.pageable.max-page-size: 100` is reused; no `PageableConfiguration`, custom resolver, Spring `Slice` intermediate, generic sorter/pagination helper, migration, index, dependency, frontend, OpenAPI, Cleanup C, Cleanup D, sync-token, or investing change is introduced.
15. Focused identity/reference PostgreSQL/HTTP tests, the full suite, Maven `verify`, and Spotless pass with no unresolved `MUST FIX` findings.
16. `STATE.md` describes actual B1/B2 pagination reality at implementation completion, and `CURRENT.md` remains pointed at PR-025 until the user accepts and controls the next transition.

## Documentation completion

Before this implementation unit is considered complete:

1. Update `docs/implementation/STATE.md` with current repository reality only:
   - PR-024 ledger accounts remain direct-list and activities/reconciliations remain `Pageable`/compact `SliceResponse`;
   - device sessions are now a direct complete list;
   - instrument search is now `Pageable`/compact `SliceResponse<InstrumentSummaryResponse>`;
   - custom ordinary-list cursor infrastructure is fully removed, if the final source search proves that statement.
2. Replace superseded statements that identity/reference cursors remain; do not append a historical narrative.
3. Update authoritative architecture/domain documents only if implementation discovers a real contract change not already governed by PR-023/PR-024.
4. Keep reusable Windows, sandbox, Maven, Docker/Testcontainers, or output lessons in `docs/engineering/codex-command-playbook.md`, not in `STATE.md`.
5. Record exact deletions, deviations, tests, and any new decisions in this specification's Completion Record.
6. Leave `docs/implementation/CURRENT.md` pointed at PR-025 throughout implementation and review. Do not mark PR-025 accepted or automatically activate Cleanup C, Cleanup D, or investing work.
7. The user owns commit and pointer transition decisions.

## Verification commands

Use the command playbook before retrying a known Windows, Maven, Docker, Testcontainers, or output failure.

Focused identity/reference gate:

```powershell
.\mvnw.cmd "-Dtest=DeviceSessionLifecycleTest,DeviceSessionQueryServiceTest,DeviceSessionRevocationServiceTest,DeviceSessionHttpTest,ReferenceValueObjectTest,ReferenceCatalogQueryTest,ManualInstrumentServiceTest,ManualInstrumentHttpTest,ApiBearerSecurityHttpTest" test
```

Then:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spotless:check
rg -n "SessionCursorCodec|SessionCursor|DeviceSessionPageResponse|InstrumentSearchCursorCodec|InstrumentSearchCursor|InstrumentPageResponse|CursorTokenCodec|INVALID_SESSION_CURSOR|INVALID_INSTRUMENT_CURSOR|nextCursor" src/main/java src/test/java
rg -n "RequestParam.*(cursor|limit)|cursorCreatedAt|cursorFamilyId|cursorSymbol|cursorMarketCode|cursorInstrumentId" src/main/java/dev/canverse/stocks/identity src/main/java/dev/canverse/stocks/reference
rg -n "CanonicalFingerprint" src/main/java/dev/canverse/stocks/ledger src/main/java/dev/canverse/stocks/platform
```

The first two final searches must return no matches. The fingerprint search must still show the platform implementation and real ledger idempotency consumers. Review pageable SQL and tests in context to confirm there is no `COUNT(*)`, Spring `Slice`/`SliceImpl` construction, client-property interpolation, or alias N+1 query.

## Completion record

Fill this before marking the PR complete.

### Implemented

- Device-session listing now returns a direct owner-scoped `List<DeviceSessionResponse>` from one family-aggregation SQL statement, with deterministic `created_at DESC, family_id DESC` ordering and no pagination transport.
- Instrument search now binds endpoint-local Spring `Pageable` defaults of page `0`, size `25`, and `name,asc`, uses the existing maximum page size of `100`, and returns a direct `SliceResponse<InstrumentSummaryResponse>`.
- Instrument search criteria retain only `query`, `marketId`, `type`, and `includeInactive`; repository pagination uses `pageSize + 1`, `pageable.getOffset()`, bounded alias loading, and hard-coded `name`/`symbol` sort fragments.
- Removed session cursor/page transport (`SessionCursorCodec`, `SessionCursor`, `DeviceSessionPageResponse`, `INVALID_SESSION_CURSOR`, and `SessionCursorCodecTest`), instrument cursor/page transport (`InstrumentSearchCursorCodec`, `InstrumentSearchCursor`, `InstrumentPageResponse`, and `INVALID_INSTRUMENT_CURSOR`), and the now-dead `CursorTokenCodec`.
- Preserved `CanonicalFingerprint` and its six ledger idempotency consumers, `InstrumentView`, session family/security behavior, instrument filtering/visibility/alias behavior, and the existing pageable maximum configuration.

### Deviations from specification

- None.

### New decisions

- None.

### Tests executed

- Focused gate: `.\mvnw.cmd "-Dtest=DeviceSessionLifecycleTest,DeviceSessionQueryServiceTest,DeviceSessionRevocationServiceTest,DeviceSessionHttpTest,ReferenceValueObjectTest,ReferenceCatalogQueryTest,ManualInstrumentServiceTest,ManualInstrumentHttpTest,ApiBearerSecurityHttpTest" test` - 64 tests passed.
- Full suite: `.\mvnw.cmd test` - 364 tests passed.
- Verification: `.\mvnw.cmd verify` - build passed with 364 tests and Spotless clean.
- Formatting: `.\mvnw.cmd spotless:check` - passed; 263 Java files clean.
- Final source searches found no session/reference/generic cursor transport symbols or legacy cursor/keyset request fragments; `CanonicalFingerprint` remains referenced by six ledger services.

### Follow-up work

- SOL actual-diff review and user acceptance remain manual follow-up. Cleanup C and later cleanup/R4 work remain inactive.
