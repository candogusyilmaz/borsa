# PR-023 - Governing simplicity standards

Status: **ACTIVE**

## Goal

Replace the repository's cursor-first and abstraction-friendly implementation guidance with one concise, authoritative simplicity contract before production cleanup begins. Future work should default to direct, local code; use no pagination for naturally small collections and Spring `Pageable` when pagination is needed; and add custom cursor, mapping, helper, or layering abstractions only for a demonstrated requirement.

## Capability and review boundary

- Coherent capability: this is one cross-cutting documentation decision that must be internally consistent before cleanup changes remove the existing cursor and ceremonial abstraction code.
- Combined behaviors: pagination defaults, validation/error ownership, helper extraction, model/mapping boundaries, fingerprint readability, and service/repository granularity all express the same implementation principle: prefer the fewest concepts that preserve real behavior.
- Excluded neighbor: Cleanup B removes cursor pagination, Cleanup C changes validation/errors and trivial abstractions, and Cleanup D changes redundant models/mapping and fingerprint placement. None of those production or test changes belongs here.
- Focused review: one reviewer can verify the governing documents for consistency, confirm that historical implementation records remain historical, and prove that no production, test, migration, dependency, configuration, or frontend file changed.
- This documentation-only unit is intentionally small. It establishes the rules required to review the following cleanup units and is not permission to start them.

## Source documents

- `docs/engineering/coding-standards.md` - repository-wide implementation, persistence, API, error, model, and PR rules.
- `docs/review/backend-master-plan.md` - authoritative architecture, API invariants, file economy, roadmap endpoint examples, and stage gates.
- `docs/review/accounting-contract.md` - API summary only; no financial semantic rule changes.
- `docs/review/mobile-api-readiness.md` - list pagination guidance and the distinction between ordinary collection pagination and a future synchronization continuation token.
- `docs/review/real-asset-lifecycle-tco-design.md` - future list/timeline endpoint examples that currently prescribe cursor parameters without a demonstrated requirement.
- `docs/implementation/README.md` - active-spec workflow and review-boundary guidance.
- `docs/review/progress-report.md` and `docs/implementation/STATE.md` - current checkpoint/state only, not historical implementation-detail rewrites.
- The completed accidental-complexity audit and the user's final pagination, compatibility, validation, directness, model, fingerprint, and service-boundary decisions.

## Starting state

- PR-021 is accepted in implementation commit `e08f2c2`.
- PR-022 implementation and review are complete in the working tree, with its focused gate, full 377-test suite, Maven `verify`, and Spotless checks recorded green; its Git commit decision remains user-owned.
- The application has not been released, the preserved frontend will be rewritten, and the current cursor wire contracts have no compatibility requirement.
- Current production code still contains custom cursor/keyset pagination for device sessions, instrument search, financial accounts, ledger activities, and reconciliations. That is current repository reality until Cleanup B changes it.
- Existing authoritative guidance still mandates cursor pagination for potentially unbounded collections and contains cursor-specific SQL/API recommendations. That guidance would cause later agents to recreate the complexity targeted by the cleanup initiative.
- The accounting, ownership, idempotency, concurrency, projection, and security mechanisms identified by the audit remain presumptively valuable and are not being weakened by this documentation unit.

## Scope

1. Update `docs/engineering/coding-standards.md` with one compact directness and abstraction policy:
   - prefer direct, local, readable code and extract concepts, not lines;
   - do not create a method or class solely to wrap one primitive expression, null check, conversion, delegation, or `if` plus exception;
   - retain helpers that name a meaningful workflow phase, algorithm, transactional substep, cohesive validation phase, or substantial repeated logic;
   - permit a small shared primitive helper only after multiple real call sites exist, without creating a generic assertion/validation framework;
   - do not introduce abstractions merely to conform to generic Clean Code or Clean Architecture conventions.
2. Replace cursor-first pagination rules across the authoritative guidance with this hierarchy:
   - return naturally small bounded collections without pagination;
   - use Spring `Pageable` when ordinary collection pagination is needed;
   - prefer `Slice` semantics when the client needs only bounded results and `hasNext`;
   - use `Page` only when totals or total pages have a demonstrated product value;
   - allow `JdbcClient` queries to consume `Pageable` size, offset, and allowed sort values directly rather than replacing JDBC;
   - do not create a custom pagination abstraction around Spring pagination;
   - permit custom cursor/keyset pagination only for a documented product or measured performance requirement;
   - prohibit speculative cursor models, codecs, opaque collection tokens, filter digests, and keyset SQL.
3. Clarify that a future synchronization change feed may define its own continuation token when that feature is implemented because continuation is part of the synchronization protocol. It must not establish cursor pagination as the default for ordinary collection endpoints.
4. Update validation/error guidance:
   - Bean Validation and request validation own structural shape, malformed input, length, and range errors;
   - meaningful application/domain rejection uses the owning capability's `ErrorCode` through `AppException`;
   - do not manufacture field-centric validation metadata for a business rule solely because a request field triggered it;
   - do not create a generic internal validation framework.
5. Update model and coupling guidance:
   - do not add `View`, `Command`, `Result`, projection, mapper, or wrapper types solely to cross a package/layer boundary;
   - `Response.from(entity)` and `Response.from(readModel)` are allowed;
   - an application service may use request/response records directly when that removes meaningless mapping and creates no concrete problem;
   - JPA entities remain excluded from direct JSON exposure;
   - genuine aggregate SQL, query, calculation, lifecycle, and projection read models remain allowed.
6. Add the fingerprint rule:
   - long canonical fingerprint construction may move behind a workflow-specific method so the main workflow remains readable;
   - the method must keep the exact canonical input names, values, normalization, and ordering explicit and reviewable;
   - prohibit reflection, implicit serialization, annotations, builders, and generic command/fingerprint hierarchies for this purpose.
7. Reconcile service/repository guidance:
   - preserve coherent workflow, transaction, security, lifecycle, aggregate, and query-family boundaries;
   - do not merge or split services/repositories solely to reduce file count or method count;
   - reassess granularity only after accidental concepts and local ceremony have been removed and a concrete ownership/readability problem remains.
8. Apply those rules consistently to the currently authoritative guidance:
   - `docs/engineering/coding-standards.md`;
   - `docs/review/backend-master-plan.md`, including standardization conventions, API invariants, reference endpoint examples, R16/Stage 12 API gates, and file-economy guidance;
   - the API summary in `docs/review/accounting-contract.md` without changing financial semantics;
   - ordinary list guidance and checklist items in `docs/review/mobile-api-readiness.md` while preserving a future sync-token decision as feature-local;
   - cursor-specific ordinary list/timeline examples in `docs/review/real-asset-lifecycle-tco-design.md`;
   - `docs/implementation/README.md` only as needed to allow an explicitly authorized governing-document unit before implementation cleanup and to reject mechanical all-layer work.
9. Update the current checkpoint in `docs/review/progress-report.md` and current reality in `docs/implementation/STATE.md` when this unit is completed. Replace superseded current guidance; do not rewrite historical PR completion records merely because they accurately describe the code that existed at acceptance.
10. Leave completed PR-019 through PR-022 specifications unchanged as historical implementation records. Leave older review evidence such as `backend-audit.md` and the historical `prioritized-roadmap.md` unchanged unless a link or statement falsely claims to be current authority.

## Explicit non-goals

- No production Java changes.
- No test changes, test deletion, or test expectation changes.
- No migration, schema, dependency, application configuration, generated source, or frontend changes.
- No removal of cursor classes, codecs, filter digests, keyset SQL, page responses, error codes, or cursor tests; Cleanup B owns those changes.
- No endpoint, request, response, OpenAPI, or runtime pagination behavior change.
- No implementation of the temporal validation helper, capability error codes, helper inlining, model removal, mapping changes, or fingerprint extraction; Cleanups C and D own those changes.
- No broad service or repository consolidation.
- No change to immutable financial facts, correction/reversal semantics, idempotency, owner scoping, deterministic locking, exact decimal handling, coverage, projection correctness, reconciliation invariants, authentication/session correctness, PostgreSQL constraints, or integration coverage.
- No design or activation of Cleanup B, C, or D specifications. Stop after Cleanup A completion and return control to the user.
- No next investing feature specification in this unit.
- No Git operations.

## Database changes

Migration(s):

- None.

Tables/columns/constraints/indexes introduced or changed:

- None.

## Application changes

None. This PR changes governing documentation and current planning/checkpoint documents only.

No production package, class, test, migration, dependency, configuration, generated source, or frontend file may change.

## API contract

None. This PR documents the desired pagination and API-design defaults; Cleanup B will define and implement endpoint-specific contract changes. Because the application is unreleased and the frontend will be rewritten, that later cleanup does not need a cursor migration, deprecation, or compatibility layer.

## Business invariants

- No financial, accounting, owner-scope, concurrency, idempotency, projection, reconciliation, or security behavior changes.
- Simplicity rules never authorize weakening a correctness mechanism that protects a demonstrated requirement.
- Cursor/keyset pagination is not itself a correctness invariant for any currently implemented ordinary collection.
- Historical specifications remain evidence of what their accepted implementations required; current governing documents own future implementation direction.

## Required tests

### Pure/domain

- None; no production or test code changes.

### PostgreSQL/Testcontainers

- None; no database or persistence behavior changes.

### HTTP/security

- None; no runtime API or security behavior changes.

### Documentation consistency

- Inspect every modified document for one consistent pagination hierarchy and directness policy.
- Search the current authoritative files for obsolete cursor-as-default, keyset-as-convention, cursor-compatibility, and mandatory core-list cursor wording.
- Confirm any remaining cursor language is either historical evidence or a feature-local future synchronization continuation requirement, not the default for ordinary collections.
- Confirm historical PR-019 through PR-022 completion records were not rewritten.
- Confirm the change surface contains documentation files only.

## Acceptance criteria

1. Current authoritative guidance defaults naturally small collections to no pagination and ordinary paginated collections to Spring `Pageable`.
2. Current guidance explicitly prefers `Slice` when only `hasNext` is needed and permits `Page` only when totals have demonstrated value.
3. Current guidance permits `JdbcClient` to consume `Pageable` directly and forbids a custom Spring-pagination wrapper.
4. Custom cursor/keyset pagination requires a documented product or measured requirement; speculative cursor codecs, tokens, models, filter digests, and keyset SQL are prohibited.
5. Current guidance says to prefer direct, local code and extract concepts, not lines, with explicit rules against semantically empty one-method helpers/classes.
6. Structural input errors remain request validation, while business/application rejections use capability `ErrorCode` plus `AppException` without manufactured field metadata.
7. Current guidance rejects intermediate models created only for layer purity, permits `Response.from(entity)` and direct request/response use where pragmatic, and preserves genuine aggregate read models without exposing JPA entities as JSON.
8. Current guidance permits workflow-specific fingerprint methods while requiring exact canonical inputs to remain explicit and prohibits reflective or framework-style fingerprint abstractions.
9. Current guidance rejects broad service/repository merging or splitting based only on file or method count.
10. `backend-master-plan.md`, `accounting-contract.md`, `mobile-api-readiness.md`, and the relevant future endpoint examples no longer mandate cursor pagination for ordinary collections.
11. A future sync continuation token, if still documented, is clearly local to the sync protocol and does not create a general list-pagination rule.
12. Completed PR specifications remain unchanged as historical records, while `STATE.md`, `CURRENT.md`, and the current progress checkpoint accurately identify Cleanup A and current production reality.
13. Only documentation/planning files change; Cleanup B, C, D, and the next investing work remain unimplemented and unspecified.

## Documentation completion

Before this implementation unit is considered complete:

1. Update `docs/implementation/STATE.md` with current repository reality only: the governing simplicity rules are adopted, runtime cursor behavior is still present pending Cleanup B, and no product capability changed.
2. Replace or remove superseded current statements; do not append a historical narrative.
3. Update the current standardization checkpoint in `docs/review/progress-report.md` without rewriting accurate historical PR completion evidence.
4. Keep detailed rule choices and verification in this specification's Completion Record rather than duplicating them into `STATE.md`.
5. Leave `CURRENT.md` pointed at PR-023 until the user accepts this documentation unit and explicitly decides the next pointer transition.

## Verification commands

No Maven or Testcontainers command is required for a documentation-only change. Use focused documentation checks:

```powershell
rg -n -i "cursor|keyset|Pageable|Slice|Page" docs/engineering/coding-standards.md docs/review/backend-master-plan.md docs/review/accounting-contract.md docs/review/mobile-api-readiness.md docs/review/real-asset-lifecycle-tco-design.md docs/implementation/README.md
rg -n -i "extract concepts|Response\.from|field-centric|workflow-specific|file count" docs/engineering/coding-standards.md docs/review/backend-master-plan.md
```

Review the resulting matches in context; the presence of the word `cursor` is acceptable only where the rule rejects speculation or describes a feature-local synchronization continuation contract.

## Completion record

Fill this before marking the PR complete.

### Implemented

- Replaced current cursor-first pagination guidance with the bounded-collection/`Pageable`/`Slice`/`Page` hierarchy, opt-in custom cursor/keyset requirements, and a feature-local synchronization continuation-token distinction across the authoritative documents.
- Established direct/local implementation, concept-oriented extraction, pragmatic model/mapping, explicit fingerprint, validation/error ownership, and evidence-based service/repository granularity rules.
- Updated the current backend plan, accounting API summary, mobile-readiness guidance, future physical-asset endpoint examples, and implementation workflow to agree with the new authority.
- Updated `STATE.md` and the current progress checkpoint; kept `CURRENT.md` pointed at PR-023 and preserved historical PR specifications and historical implementation evidence.
- Confirmed the runtime cursor implementation, tests, and all Cleanup B/C/D work remain unchanged and out of scope.

### Deviations from specification

- None.

### New decisions

- None beyond the governing decisions already recorded in the PR-023 scope and acceptance criteria.

### Tests executed

- Required pagination/simplicity `rg` searches passed; obsolete current-authority phrase search found no matches.
- `git diff --check` passed.
- Historical PR-019 through PR-022 diff check passed; the final status scan contained documentation paths only and no production, test, migration, configuration, dependency, or frontend paths.
- No Maven/Testcontainers command was required or run for this documentation-only unit.

### Follow-up work

- Cleanup B, C, and D remain subject to separate user-controlled planning and activation.
