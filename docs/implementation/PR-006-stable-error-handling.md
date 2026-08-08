# PR-006 — Stable RFC 9457 error handling

Status: **COMPLETE**

## Goal

Implement the backend's global HTTP error-handling contract on top of the exception/error-code foundation already introduced by PR-005.

The design is adapted from the user's existing project's error system:

- structured `ErrorCode` values;
- `AppException` carrying an error code and interpolation params;
- deterministic/derived message keys;
- RFC 9457 `ProblemDetail` responses;
- one global `ResponseEntityExceptionHandler`;
- consistent Bean Validation errors;
- safe handling of Spring MVC/framework errors;
- persistence conflicts mapped to stable conflict errors;
- a request correlation/trace identifier;
- no leakage of internal exception details for 5xx responses.

This PR is infrastructure only. It does not implement authentication, authorization, financial behavior, or new domain workflows.

The implementation agent does **not** need access to the external reference project. The relevant contract has been distilled into this specification.

## Source documents

Read and follow:

- `AGENTS.md`
- `docs/engineering/coding-standards.md`
  - current Lombok conventions are already accepted;
  - current module-subpackage conventions are already accepted;
  - RFC 9457/stable error-code rules;
- `docs/implementation/STATE.md`
- `docs/review/backend-master-plan.md`
- the completed PR-005 specification and Completion Record;
- the current implementation under `dev.canverse.stocks.platform.error` and any existing exception/error packages.

`docs/review/accounting-contract.md` is not required because this PR introduces no financial behavior.

Before editing, inspect the actual repository after PR-005. Reuse the accepted exception/error-code model rather than creating parallel replacements. For the duplicate-email case, the requested PR-006 adjustment removes the redundant `EmailAlreadyRegisteredException` wrapper and uses `IdentityErrorCode.EMAIL_ALREADY_REGISTERED` directly with `AppException`.

## Starting state

Treat the repository after PR-005 as authoritative.

Known accepted state:

- Lombok conventions are already implemented;
- purpose-based subpackages inside modules are already implemented/accepted;
- PR-005 added exception/error-code types and some entities;
- there is not yet a complete global HTTP error-handling implementation;
- the backend already has an application `Clock`;
- the backend already has an application `IdGenerator`;
- Spring MVC and Bean Validation are available;
- Spring Security behavior is not part of this PR.

There may still be a minimal `ApiExceptionHandler` left from the early backend foundation. If so, it must not coexist with the completed global handler after this PR.

## Design principles

### One error contract

There must be one client-facing problem contract.

Controllers/services do not return ad-hoc error DTOs and do not throw `ResponseStatusException` for expected application failures.

Expected application failures use the accepted PR-005 `AppException` / `ErrorCode` model.

### Stable machine code, separate display key

Clients branch on:

```text
code
```

Clients may use:

```text
key
params
```

for localized/display messaging.

Clients must not parse English exception messages.

### Internal descriptions stay internal

Developer descriptions, exception messages, causes, stack traces, SQL messages, constraint names, and internal invariant text are never exposed as client-facing 5xx details.

### 5xx params are never returned

Even if an internal error carries params for logging, those params remain server-side.

### One validation shape

Bean Validation failures and method/parameter validation failures use the same structure:

```json
{
  "code": "VALIDATION_FAILED",
  "key": "error.common.validation_failed",
  "params": {
    "errors": [
      {
        "field": "email",
        "key": "error.fields.common.not_blank",
        "detail": "must not be blank"
      }
    ]
  }
}
```

A validation error entry may additionally contain a `params` object containing safe constraint attributes such as `min`, `max`, or `value`.

## Scope

### 1. Reuse and complete the PR-005 exception contract

Inspect the error/exception classes already created by PR-005.

The accepted model should provide the equivalent responsibilities of:

```text
dev.canverse.stocks.platform.error
├── ErrorCode
├── CommonErrorCode
├── AppException
└── Params            # if already chosen/needed by the accepted model
```

Do not create duplicates under different names.

The final contract must support the following behaviors.

#### `ErrorCode`

An error code exposes:

- HTTP status;
- developer-facing description;
- exact required interpolation-param keys;
- enum/code name;
- derived message key.

Domain-specific error-code enums live under their owning module's `error` package.

Naming rule for domain-specific enums:

```text
IdentityErrorCode.INVALID_CREDENTIALS
```

produces:

```text
error.identity.invalid_credentials
```

Do not repeat the domain in the enum constant:

```text
IdentityErrorCode.IDENTITY_INVALID_CREDENTIALS   # wrong
```

`CommonErrorCode` is the cross-cutting exception and therefore keeps fully descriptive constant names.

#### Exact param contract

`AppException` must reject a throw site whose supplied param-key set differs from the `ErrorCode`'s required param-key set.

Missing keys and extra keys are both programmer errors.

This must fail immediately with `IllegalStateException`; it is not converted into a user-facing 4xx error.

`AppException` params must be immutable after construction.

Cause-supporting constructors may exist, but causes are never exposed over HTTP.

If PR-005 already implements all of this, do not rewrite it. Add tests only where current behavior is not already covered.

### 2. Keep `CommonErrorCode` focused on cross-cutting errors

Reuse existing PR-005 constants where present.

For this PR, the common error catalogue must be sufficient to represent the framework cases handled below.

The required conceptual codes are:

| Code | HTTP status | Required params |
|---|---:|---|
| `ENTITY_NOT_FOUND` | 404 | `entity`, `id` |
| `MALFORMED_REQUEST` | 400 | none |
| `MISSING_REQUEST_VALUE` | 400 | `parameter` |
| `REQUEST_BINDING_FAILED` | 400 | none |
| `METHOD_NOT_ALLOWED` | 405 | none |
| `NOT_ACCEPTABLE` | 406 | none |
| `PAYLOAD_TOO_LARGE` | 413 | none |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | none |
| `RESOURCE_NOT_FOUND` | 404 | none |
| `SERVICE_UNAVAILABLE` | 503 | none |
| `INVALID_STATUS` | 409 | `entity`, `actual`, `expected` |
| `DUPLICATE_ENTITY` | 409 | `entity`, `field`, `value` |
| `STATE_CONFLICT` | 409 | none |
| `VALIDATION_FAILED` | 422 | `errors` |
| `INACTIVE_RESOURCE` | 422 | `entity` |
| `INTERNAL_ERROR` | 500 | `detail` |

Do not add authentication/authorization codes solely for this PR.

If PR-005 already contains additional accepted common codes, keep them. Do not delete accepted codes just to make this table exact.

### 3. Implement request trace correlation

Add a small request trace component under the already accepted platform package conventions.

Preferred location:

```text
dev.canverse.stocks.platform.web.trace.RequestTraceFilter
```

Use the existing application `IdGenerator`.

Requirements:

- run once per HTTP request;
- generate a server-owned trace ID before continuing the filter chain;
- make it retrievable by the global exception handler from the current request;
- return the same trace ID in response header:

```text
X-Trace-Id
```

- every ProblemDetail produced by the global handler includes the same value as:

```text
traceId
```

The trace ID is for request correlation, not authentication or authorization.

Do not trust an arbitrary inbound client header as the authoritative trace ID in this PR.

A short UUID-derived ID is acceptable if that matches the accepted PR-005/reference convention. Tests must not rely on randomness; assert shape/nonblankness and equality between header/body.

Do not add distributed tracing/OpenTelemetry/Micrometer tracing in this PR.

### 4. Replace the minimal exception handler with one global handler

Create or complete:

```text
dev.canverse.stocks.platform.error.GlobalExceptionHandler
```

It must:

```java
@RestControllerAdvice
```

and extend:

```java
ResponseEntityExceptionHandler
```

Use the repository's accepted Lombok constructor-injection convention where appropriate.

There must be exactly one global exception advice after the PR.

If the early `ApiExceptionHandler` still exists and would overlap, remove/replace it rather than keeping two competing handlers.

### 5. Standard ProblemDetail shape

Every response owned by `GlobalExceptionHandler` uses RFC 9457 `ProblemDetail`.

Standard RFC fields:

```text
type
title
status
instance
```

Application extension fields:

```text
code
key
traceId
timestamp
params     # only when applicable and safe
```

#### `type`

Use one stable project-owned base URI constant.

If the repository already defines an error/problem URI base, reuse it.

Otherwise use:

```text
https://canverse.dev/problems/
```

and append the lower-kebab representation of the error code.

Example:

```text
STATE_CONFLICT
```

becomes:

```text
https://canverse.dev/problems/state-conflict
```

Do not copy `netcontrol.com` from the reference project.

#### `title`

Use the HTTP status reason phrase.

#### `status`

Must equal the HTTP response status.

#### `instance`

Use the request URI.

#### `code`

Use the exact `ErrorCode` constant name.

#### `key`

Use `ErrorCode`'s derived message key.

#### `traceId`

Use the value generated by `RequestTraceFilter`.

#### `timestamp`

Use:

```java
Instant.now(clock)
```

with the existing injected application `Clock`.

Do not call `Instant.now()` directly.

#### `params`

For 4xx client/application errors, include params only when non-empty.

For every 5xx response, omit params regardless of what the exception contains.

### 6. Handle `AppException`

Add:

```java
@ExceptionHandler(AppException.class)
```

Behavior:

- status comes from the `ErrorCode`;
- produce the standard ProblemDetail shape;
- 4xx:
  - log at WARN;
  - include non-empty safe params;
- 5xx:
  - log at ERROR with the exception/stack trace;
  - log params server-side when useful;
  - never return params;
  - never return exception message/cause/dev-description as `detail`.

Do not expose stack traces or Java class names.

### 7. Handle Bean Validation consistently

Override handling for:

```text
MethodArgumentNotValidException
```

and handle:

```text
ConstraintViolationException
```

Also support Spring Framework 7 method-validation errors (`HandlerMethodValidationException`) when they can occur through the MVC stack.

All validation paths return:

```text
CommonErrorCode.VALIDATION_FAILED
HTTP 422
```

with:

```text
params.errors[]
```

Each entry contains:

```text
field
key
detail
```

and may contain safe annotation attributes under nested:

```text
params
```

#### Built-in validation-key map

Provide reusable keys for the built-in Jakarta constraints currently useful to the application, including:

```text
NotNull        -> error.fields.common.not_null
NotBlank       -> error.fields.common.not_blank
NotEmpty       -> error.fields.common.not_empty
Size           -> error.fields.common.size
Min            -> error.fields.common.min
Max            -> error.fields.common.max
DecimalMin     -> error.fields.common.decimal_min
DecimalMax     -> error.fields.common.decimal_max
Positive       -> error.fields.common.positive
PositiveOrZero -> error.fields.common.positive_or_zero
Negative       -> error.fields.common.negative
NegativeOrZero -> error.fields.common.negative_or_zero
Email          -> error.fields.common.email
Pattern        -> error.fields.common.pattern
Past           -> error.fields.common.past
Future         -> error.fields.common.future
Digits         -> error.fields.common.digits
```

When a constraint uses an explicit application message template such as:

```text
{error.fields.identity.password_too_short}
```

use that key after validating its shape.

Unknown/unregistered constraint templates must not leak arbitrary templates to clients.

Use safe fallback:

```text
error.fields.common.unmapped_constraint
```

Log the unmapped condition as an implementation/configuration problem.

### 8. Validate outgoing validation-key shape

The handler must validate keys before sending them to clients.

Accepted forms include:

```text
error.fields.common.not_blank
error.fields.identity.password_too_short
error.identity.invalid_credentials
```

Do not send:

- Jakarta/Hibernate default bundle keys;
- malformed placeholders;
- arbitrary Java messages as `key`;
- class names.

Keep a package-private key-validation helper if useful for direct unit testing.

This key validation is about contract integrity; it is not an i18n implementation.

Do not add translation bundles in this PR.

### 9. Handle Spring MVC/framework failures

Map the following framework conditions into the same ProblemDetail contract.

At minimum:

| Framework condition | Code |
|---|---|
| unreadable/malformed request body | `MALFORMED_REQUEST` |
| type mismatch | `MALFORMED_REQUEST` |
| unsupported HTTP method | `METHOD_NOT_ALLOWED` |
| unsupported request media type | `UNSUPPORTED_MEDIA_TYPE` |
| unacceptable response media type | `NOT_ACCEPTABLE` |
| missing query/form parameter | `MISSING_REQUEST_VALUE` |
| missing multipart request part | `MISSING_REQUEST_VALUE` |
| generic servlet request binding failure | `REQUEST_BINDING_FAILED` |
| no controller/static API resource | `RESOURCE_NOT_FOUND` |
| upload too large | `PAYLOAD_TOO_LARGE` |
| asynchronous request timeout | `SERVICE_UNAVAILABLE` |
| missing server-side path variable binding | `INTERNAL_ERROR` |
| unsupported server-side conversion | `INTERNAL_ERROR` |
| response serialization/write failure | `INTERNAL_ERROR` |

When the framework error is a server-side 5xx condition:

- log at ERROR;
- do not expose framework messages;
- do not include internal params.

Do not rely on the framework's default English `detail` string as the application's external contract.

### 10. Handle persistence conflicts safely

Handle at minimum:

```text
DataIntegrityViolationException
ObjectOptimisticLockingFailureException
```

as:

```text
CommonErrorCode.STATE_CONFLICT
HTTP 409
```

Do not expose:

- SQL;
- constraint names;
- database messages;
- entity internals.

A future domain/service PR may translate known constraint violations into more specific domain codes before they reach this fallback.

This PR only provides the safe global fallback.

### 11. Catch unhandled exceptions

Add one final:

```java
@ExceptionHandler(Exception.class)
```

Behavior:

- log at ERROR with full server-side stack trace;
- return `CommonErrorCode.INTERNAL_ERROR`;
- HTTP 500;
- include standard fields:
  - type
  - title
  - status
  - instance
  - code
  - key
  - traceId
  - timestamp
- omit:
  - exception message
  - detail
  - params
  - cause
  - stack trace
  - Java exception class.

### 12. Do not copy security-specific handlers yet

The reference project also handles:

```text
AuthenticationException
AccessDeniedException
```

Do **not** add those handlers in PR-006 unless Spring Security and its accepted authentication boundary already exist in the current repository.

Authentication/authorization behavior belongs to the later security PR.

When Spring Security is introduced, it must integrate into this same ProblemDetail contract rather than creating a second error format.

### 13. Do not add the reference project's validation framework

The reference project also contains:

```text
SelfValidating
SelfValidatingValidator
Validatable
ValidationError
```

Those are request/domain-validation helpers, not required to establish the global error boundary.

Do not copy them in this PR.

If a later request DTO genuinely needs cross-field validation, introduce the minimum validation abstraction then.

### 14. Do not add `ApiError` unless the repository has a real consumer

The reference project has an `ApiError` record that mirrors the ProblemDetail contract but is explicitly not used as the actual HTTP response body.

Do not create an unused duplicate wire model in this project.

`ProblemDetail` plus integration tests are the authoritative runtime contract for PR-006.

A typed OpenAPI-facing error schema may be introduced later if the API-generation workflow actually needs one.

## Application changes

Expected new/changed production area:

```text
src/main/java/dev/canverse/stocks/platform/
├── error/
│   ├── ...existing PR-005 exception/error-code files...
│   └── GlobalExceptionHandler.java
└── web/
    └── trace/
        └── RequestTraceFilter.java
```

Potential removal/replacement:

```text
platform/web/ApiExceptionHandler.java
```

or its current equivalent, if it overlaps with the new handler.

Do not assume the exact pre-PR-006 paths without inspecting the repository.

No entity/repository behavior should change.

## API contract

All globally handled errors use:

```text
Content-Type: application/problem+json
```

Representative application error:

```json
{
  "type": "https://canverse.dev/problems/entity-not-found",
  "title": "Not Found",
  "status": 404,
  "instance": "/api/v1/example/00000000-0000-0000-0000-000000000001",
  "code": "ENTITY_NOT_FOUND",
  "key": "error.common.entity_not_found",
  "params": {
    "entity": "Example",
    "id": "00000000-0000-0000-0000-000000000001"
  },
  "traceId": "a1b2c3d4",
  "timestamp": "2026-08-08T12:00:00Z"
}
```

Representative validation error:

```json
{
  "type": "https://canverse.dev/problems/validation-failed",
  "title": "Unprocessable Content",
  "status": 422,
  "instance": "/api/v1/example",
  "code": "VALIDATION_FAILED",
  "key": "error.common.validation_failed",
  "params": {
    "errors": [
      {
        "field": "email",
        "key": "error.fields.common.not_blank",
        "detail": "must not be blank"
      }
    ]
  },
  "traceId": "a1b2c3d4",
  "timestamp": "2026-08-08T12:00:00Z"
}
```

Representative internal error:

```json
{
  "type": "https://canverse.dev/problems/internal-error",
  "title": "Internal Server Error",
  "status": 500,
  "instance": "/api/v1/example",
  "code": "INTERNAL_ERROR",
  "key": "error.common.internal_error",
  "traceId": "a1b2c3d4",
  "timestamp": "2026-08-08T12:00:00Z"
}
```

The 500 response must not include the underlying internal detail param even if the `AppException` contains one.

## Database changes

None.

Do not add or edit Flyway migrations.

No database table is required for error handling or request trace IDs.

## Required tests

### Pure/unit

Add focused tests for the existing PR-005 error model where equivalent coverage does not already exist.

At minimum prove:

1. `ErrorCode` message-key derivation:
   - common code -> `error.common.<condition>`;
   - a test/domain code -> `error.<domain>.<condition>`.
2. `AppException` accepts the exact required param set.
3. Missing required params fail immediately.
4. Extra params fail immediately.
5. `AppException` stores an immutable copy of params.
6. validation-key shape accepts the supported application forms.
7. malformed validation keys are rejected/fall back safely.

Do not duplicate tests already present from PR-005 merely to increase test count.

### HTTP / MockMvc integration

Add a test-only controller under test sources. Do not create production endpoints solely for testing.

Use MockMvc against the real `GlobalExceptionHandler` and `RequestTraceFilter`.

Required cases:

1. **AppException / 4xx**
   - correct HTTP status;
   - `application/problem+json`;
   - correct `code`;
   - correct `key`;
   - safe params included;
   - `instance` matches request;
   - `traceId` present;
   - `timestamp` present.

2. **Trace correlation**
   - `X-Trace-Id` response header exists;
   - body `traceId` equals the response header.

3. **Internal AppException**
   - returns 500/internal code;
   - no params;
   - no internal detail/message in response.

4. **Unhandled exception**
   - returns 500/internal code;
   - no exception message/class/stack in response.

5. **Bean Validation**
   - returns 422;
   - `VALIDATION_FAILED`;
   - `params.errors[]`;
   - expected field and application validation key.

6. **Malformed JSON**
   - returns 400 / `MALFORMED_REQUEST`;
   - parser exception text is absent.

7. **Missing request parameter**
   - returns 400 / `MISSING_REQUEST_VALUE`;
   - safe `parameter` param is present.

8. **Method not allowed**
   - returns 405 / `METHOD_NOT_ALLOWED`.

9. **Unsupported media type**
   - returns 415 / `UNSUPPORTED_MEDIA_TYPE`.

10. **Persistence conflict fallback**
    - a test endpoint that throws `DataIntegrityViolationException` maps to 409 / `STATE_CONFLICT`;
    - database/exception message is not exposed.

Where practical, also cover Spring Framework 7 method-parameter validation so it produces the same 422 error-list shape.

### Existing tests

All PR-001 through PR-005 tests remain green.

No PostgreSQL Testcontainer is required for the new handler tests themselves unless the existing test architecture already mandates it.

## Explicit non-goals

PR-006 does **not** implement:

- Lombok conventions — already implemented;
- package-structure cleanup — already implemented;
- authentication;
- authorization;
- Spring Security filters/configuration;
- `AuthenticationException` handling;
- `AccessDeniedException` handling;
- JWTs;
- refresh sessions;
- login/register/logout endpoints;
- security-event persistence behavior;
- request throttling;
- translations/resource bundles;
- frontend locale updates;
- `SelfValidating` or another cross-field validation framework;
- domain-specific finance errors;
- database constraint-name parsing;
- per-constraint SQL-to-domain-error translation;
- OpenTelemetry/distributed tracing;
- a persistent error/audit log;
- a new `ApiError` mirror record with no consumer;
- new entities/repositories;
- new Flyway migrations;
- frontend changes;
- Git operations.

## Acceptance criteria

PR-006 is ready for human review only when all of the following are true:

1. There is exactly one active global REST exception handler.
2. The handler extends `ResponseEntityExceptionHandler`.
3. Expected `AppException` failures produce RFC 9457 ProblemDetail responses.
4. Every owned ProblemDetail has stable `code`, `key`, `traceId`, and `timestamp`.
5. `X-Trace-Id` equals the ProblemDetail `traceId`.
6. `timestamp` uses the injected application `Clock`.
7. 4xx application params are included only when non-empty.
8. 5xx params are never returned.
9. Internal exception messages, causes, stack traces, SQL, and constraint names are never exposed by the catch-all/persistence fallback.
10. Bean Validation uses one `VALIDATION_FAILED` / `params.errors[]` contract.
11. Validation keys are mapped/validated before they leave the backend.
12. Malformed requests, missing values, method/media errors, missing resources, upload limits, timeouts, and internal framework failures map to stable common codes.
13. Persistence conflicts safely fall back to `STATE_CONFLICT`.
14. No Spring Security-specific handler is added unless security already exists as an accepted dependency/boundary.
15. No unused `ApiError` mirror type is added.
16. No `SelfValidating` framework is copied.
17. No migration/schema change is made.
18. No entity/repository behavior is changed.
19. `src/main/web` has no source changes.
20. All existing tests remain green.
21. New unit/error-contract tests pass.
22. New MockMvc error-handler integration tests pass.
23. `./mvnw spotless:check` passes.
24. `./mvnw test` passes.
25. `./mvnw verify` passes.
26. `git diff --check` passes.
27. Completion Record is filled accurately.
28. `docs/implementation/STATE.md` is updated with the accepted error-handling contract.
29. `docs/implementation/CURRENT.md` still points to PR-006.
30. The implementation agent performs no Git mutations.

## Documentation completion

Before claiming completion:

- fill this PR's Completion Record;
- update `docs/implementation/STATE.md` with:
  - PR-005 completed;
  - PR-006 error-handling foundation implemented;
  - stable ProblemDetail extension fields;
  - trace-ID behavior;
  - 5xx non-leakage rule;
  - validation-error shape;
  - note that Spring Security integration is deferred;
- update `docs/review/progress-report.md` if required by `AGENTS.md`;
- do not create PR-007;
- do not advance `CURRENT.md`.

## Verification commands

```bash
./mvnw spotless:check
./mvnw test
./mvnw verify

git status --short
git diff --check
```

## Review guide

Review this PR in the following order:

1. **Wire contract**
   - inspect actual JSON for 404/422/500;
   - confirm stable machine fields;
   - confirm `application/problem+json`.

2. **Data leakage**
   - deliberately inspect 500 and persistence-conflict responses;
   - ensure no exception/database/internal detail escaped.

3. **Validation**
   - verify all validation paths use the same `params.errors[]` shape;
   - verify key mapping cannot ship arbitrary templates.

4. **Trace correlation**
   - response header and body must match;
   - handler must still work for every tested framework failure.

5. **Handler ownership**
   - ensure the old minimal handler is removed/replaced;
   - ensure there are not two competing global advices.

6. **Scope**
   - reject authentication/security/domain work mixed into this PR.

## Completion record

Fill before claiming PR-006 complete.

### Starting commit

- `aab588f` (`pr-005`)

### Implemented

- Added `ErrorCode`, `CommonErrorCode`, and strict, deeply immutable `AppException` contracts under `platform.error`, including all required cross-cutting codes and derived message keys.
- Removed the redundant PR-005 `EmailAlreadyRegisteredException` wrapper; `LocalAccountRegistrationService` now throws `AppException` directly with `IdentityErrorCode.EMAIL_ALREADY_REGISTERED`, preserving the safe description and cause behavior.
- Replaced the early `ApiExceptionHandler` with one `GlobalExceptionHandler` extending `ResponseEntityExceptionHandler` and returning `application/problem+json` ProblemDetail responses.
- Added injected-clock timestamps, stable problem type/code/key fields, server-owned `X-Trace-Id` correlation, validation-key mapping, framework failure mappings, persistence conflict fallbacks, and 5xx detail suppression.
- Added focused error-contract tests and standalone MockMvc integration tests for application errors, trace correlation, 4xx/5xx leakage, validation, malformed requests, missing values, method/media failures, and persistence conflicts.

### Deviations from specification

- At the user's direction, the thin `EmailAlreadyRegisteredException` wrapper was removed. Duplicate-email failures now use the shared `AppException` boundary with `IdentityErrorCode.EMAIL_ALREADY_REGISTERED` directly; the former lowercase legacy `getCode()` value is no longer retained.

### New decisions

- Domain-specific duplicate-email failures use `IdentityErrorCode.EMAIL_ALREADY_REGISTERED` directly with `AppException`. The HTTP boundary therefore exposes the enum code `EMAIL_ALREADY_REGISTERED` and derived key `error.identity.email_already_registered` without a redundant exception subclass.
- MockMvc tests use standalone setup because this Maven project does not include Spring Boot's test-autoconfigure module; they still exercise the real handler and trace filter.

### Tests executed

- `./mvnw spotless:check` (PowerShell: `mvnw.cmd spotless:check`) -> BUILD SUCCESS.
- `./mvnw -Dtest=ErrorContractTest,GlobalExceptionHandlerIntegrationTest,LocalAccountRegistrationServiceTest test` (PowerShell equivalent) -> 22 tests, 0 failures, 0 errors.
- `./mvnw test` (PowerShell equivalent) -> 51 tests, 0 failures, 0 errors.
- `./mvnw verify` (PowerShell equivalent) -> BUILD SUCCESS, including Spotless and all 51 tests.
- `git diff --check` (PowerShell: per-command `safe.directory` override because of sandbox ownership) -> PASSED.
- `git status --short` (read-only) -> working-tree changes remain uncommitted and limited to PR-006 implementation/tests/documentation plus the pre-existing active-spec pointer state.

### Follow-up work

- Do not define the exact next PR here.
- After user review and acceptance, design the next small capability just-in-time from the committed repository state.
