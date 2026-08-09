# PR-007 — HTTP local account registration

Status: **COMPLETE**

## Goal

Expose the accepted atomic local-account registration workflow through one versioned HTTP endpoint. A valid request creates the existing `UserAccount` and `LOCAL` `AuthIdentity` transactionally and returns the new opaque user ID; invalid or duplicate requests use the shared PR-006 RFC 9457 error contract.

This PR is deliberately an HTTP-boundary slice. It does not authenticate the new user or issue any token/session.

## Source documents

- `AGENTS.md`
- `docs/implementation/STATE.md`
- `docs/implementation/PR-005-atomic-local-account-registration.md` — accepted registration behavior and persistence invariants
- `docs/implementation/PR-006-stable-error-handling.md` — accepted ProblemDetail, validation, and trace contract
- `docs/review/backend-master-plan.md`
  - API invariants from the first endpoint
  - R1 item 5 and the initial `POST /api/v1/auth/register` endpoint
- `docs/engineering/coding-standards.md`
  - API, error, security, testing, and package rules

`docs/review/accounting-contract.md` is not required because this PR introduces no financial behavior.

## Starting state

Treat the repository after PR-006 as authoritative.

Already implemented and not to be redesigned here:

- `LocalAccountRegistrationService.register(String email, String rawPassword)` validates input, normalizes email, hashes the password, and atomically persists one user and one local identity;
- duplicate email paths throw `AppException` with `IdentityErrorCode.EMAIL_ALREADY_REGISTERED`;
- `GlobalExceptionHandler` maps application and validation failures to the shared RFC 9457 contract;
- `RequestTraceFilter` provides the server-owned `X-Trace-Id` / `traceId` correlation contract;
- PostgreSQL V1 constraints and PR-005 Testcontainers coverage are authoritative;
- there is no Spring Security filter chain, login workflow, token issuer, or device-session behavior.

No prerequisite migration or authentication infrastructure is missing for this unauthenticated registration endpoint.

## Scope

### 1. Add one registration controller

Create one controller in:

```text
dev.canverse.stocks.identity.web
```

Use one cohesive class, preferably:

```text
LocalAccountRegistrationController
```

It must:

- be a Spring REST controller;
- use constructor injection for `LocalAccountRegistrationService`;
- expose exactly one handler:

```text
POST /api/v1/auth/register
```

- accept `application/json`;
- produce `application/json` on success;
- delegate registration exactly once to `LocalAccountRegistrationService.register(...)`;
- contain no persistence, normalization, password hashing, duplicate lookup, transaction, or exception-translation logic.

Do not add a controller interface, mapper, command handler, façade, generic response envelope, or authentication abstraction.

### 2. Add request and response records in the identity DTO packages

Place the HTTP request record in `dev.canverse.stocks.identity.input` and the response record in `dev.canverse.stocks.identity.output`, following the repository's directional API DTO convention. Do not add a parallel generic `dto` package or mapper abstraction.

Request JSON:

```json
{
  "email": "Alice.Example@example.com",
  "password": "correct horse battery staple"
}
```

The request record has exactly:

```java
String email
String password
```

Apply Jakarta Validation at the HTTP boundary using the same structural rules already enforced by the application service:

```java
@NotBlank @Email @Size(max = 320) @Pattern(regexp = "\\S(?:.*\\S)?") String email
@NotBlank @Size(min = 12, max = 128) String password
```

Use `@Valid` on the request body. Do not trim, normalize, log, return, or otherwise transform either request field in the controller. The service remains the behavior owner.

Successful response JSON:

```json
{
  "userId": "10000000-0000-0000-0000-000000000001"
}
```

The response record has exactly one `UUID userId` component. Do not return email, normalized email, password/hash, identity ID, roles, authentication state, tokens, or session data.

### 3. Define the success status narrowly

A successful request returns:

```text
HTTP 201 Created
Content-Type: application/json
```

Return the response body above. Do not add a `Location` header because no user-detail resource is introduced by this PR.

The existing `RequestTraceFilter` may add `X-Trace-Id` to the success response; do not add controller-specific trace logic.

### 4. Reuse the existing error boundary unchanged

The controller must allow the accepted exception paths to reach `GlobalExceptionHandler`:

- invalid request DTO -> `422 VALIDATION_FAILED` with `params.errors[]`;
- duplicate email -> `409 EMAIL_ALREADY_REGISTERED` with key `error.identity.email_already_registered`;
- malformed JSON -> the existing `400 MALFORMED_REQUEST` mapping;
- unexpected failures -> the existing safe `500 INTERNAL_ERROR` mapping.

Do not catch `AppException`, `ConstraintViolationException`, `DataIntegrityViolationException`, or generic exceptions in the controller.

Do not modify `ErrorCode`, `AppException`, `GlobalExceptionHandler`, `ValidationKeySupport`, or `RequestTraceFilter` unless an HTTP integration test exposes an objective PR-006 defect. If that occurs, keep the correction minimal and record it as a deviation in this specification.

### 5. Add PostgreSQL-backed HTTP integration coverage

Add one focused integration test, preferably:

```text
src/test/java/dev/canverse/stocks/identity/LocalAccountRegistrationHttpTest.java
```

Use:

- the real Spring application context;
- the real controller, registration service, global exception handler, and request trace filter;
- MockMvc built from the real web application context if the current dependency set does not provide Boot's MockMvc test auto-configuration;
- PostgreSQL Testcontainers with Flyway and Hibernate validation;
- the existing production `PasswordEncoder`;
- a fixed test `Clock`;
- deterministic test IDs without changing production ID-generation behavior;
- committed transactions, not a test-level rollback that could hide endpoint transaction behavior.

`RequestTraceFilter` and the registration service intentionally share the application `IdGenerator`. If the test uses a queued deterministic generator, account for call order explicitly: an HTTP registration consumes the request trace ID first, then the user-account ID, then the auth-identity ID. Validation and malformed-body requests consume only their request trace ID because they never enter the registration workflow.

Do not mock JPA repositories or `LocalAccountRegistrationService` in the PostgreSQL-backed acceptance tests.

## Explicit non-goals

- password login or credential verification;
- access tokens, JWT/JOSE dependencies, claims, signing keys, key generation, issuers, audiences, or bearer-token parsing;
- refresh tokens or `DeviceSession` writes;
- automatic login/session creation after registration;
- logout, refresh, session listing, or session revocation;
- `GET /api/v1/me` or any current-principal helper;
- `spring-boot-starter-security`, `SecurityFilterChain`, CSRF, CORS, HTTP Basic, form login, or request authorization;
- roles, permissions, households, or owner-scope infrastructure;
- Google/OIDC/OAuth;
- email verification, password reset/change, rate limiting, progressive delay, lockout, or security-event writes;
- idempotency-key infrastructure for registration;
- changing password policy, email normalization, duplicate semantics, or registration transaction behavior from PR-005;
- changing PR-006 error codes, ProblemDetail fields, validation shape, trace behavior, or logging policy;
- a generic auth controller/service interface or shared API response wrapper;
- OpenAPI generation/schema tooling;
- frontend changes or legacy `/api/auth/register` compatibility;
- schema changes or a new Flyway migration;
- entity/repository changes;
- financial/reference/ledger behavior;
- Git operations.

## Database changes

None.

Do not add or modify a Flyway migration, constraint, index, entity mapping, or repository method. The V1 schema and PR-005 persistence behavior remain authoritative.

## Application changes

Expected production-code surface:

```text
src/main/java/dev/canverse/stocks/identity/
├── input/
│   └── RegistrationRequest.java
├── output/
│   └── RegistrationResponse.java
└── web/
    └── LocalAccountRegistrationController.java
```

Expected test surface:

```text
src/test/java/dev/canverse/stocks/identity/
└── LocalAccountRegistrationHttpTest.java
```

Documentation changes are limited to the normal completion record, `STATE.md`, and `progress-report.md` updates after verification.

## API contract

### `POST /api/v1/auth/register`

Request:

```http
Content-Type: application/json
```

```json
{
  "email": "Alice.Example@example.com",
  "password": "correct horse battery staple"
}
```

Successful response:

```text
HTTP 201 Created
Content-Type: application/json
```

```json
{
  "userId": "10000000-0000-0000-0000-000000000001"
}
```

Duplicate normalized email:

```text
HTTP 409 Conflict
Content-Type: application/problem+json
code: EMAIL_ALREADY_REGISTERED
key: error.identity.email_already_registered
```

The duplicate response has no `params` and does not contain the submitted email or password.

Structurally invalid input:

```text
HTTP 422 Unprocessable Content
Content-Type: application/problem+json
code: VALIDATION_FAILED
key: error.common.validation_failed
params.errors[]: existing PR-006 validation-entry shape
```

Every problem response retains the PR-006 `type`, `title`, `status`, `instance`, `traceId`, and `timestamp` contract, and its `X-Trace-Id` header equals body `traceId`.

No compatibility route under `/api/auth` is added.

## Business invariants

- HTTP registration delegates to the PR-005 application workflow; the controller does not become a second behavior owner.
- A 201 response means both the user account and local auth identity committed.
- Validation failure, duplicate email, or any failed registration creates no additional identity row.
- Email uniqueness remains case-insensitive through the accepted normalized-email behavior and PostgreSQL constraints.
- The raw password never appears in a response, exception parameter, log statement, or persisted column.
- Registration returns identity only; it does not establish an authenticated principal or session.

## Required tests

### Pure/domain

None. PR-005 already covers registration behavior and PR-006 already covers the reusable error contract.

Do not add a mocked controller unit test merely to duplicate the required HTTP/PostgreSQL coverage.

### PostgreSQL/Testcontainers

The HTTP integration test must prove all of the following against migrated PostgreSQL:

1. **Successful HTTP registration**
   - submit a mixed-case valid email and valid password;
   - receive 201 and exactly one `userId` field;
   - receive `application/json`;
   - receive a nonblank server-owned `X-Trace-Id`;
   - verify one `user_account` and one `LOCAL` `auth_identity` committed;
   - verify normalized email/provider subject and the foreign-key relationship;
   - verify the stored password is encoded, matches the submitted password through `PasswordEncoder`, and is not equal to the raw password;
   - verify neither raw password nor password hash appears in the response.

2. **Case-insensitive duplicate through HTTP**
   - establish the first registration through the real service or endpoint;
   - retry through HTTP using different email casing;
   - receive 409 `EMAIL_ALREADY_REGISTERED` and `error.identity.email_already_registered`;
   - verify `application/problem+json`;
   - verify `X-Trace-Id` equals body `traceId`;
   - verify no `params`, submitted email, password, SQL, or constraint name appears;
   - verify the original two rows remain unchanged and no additional row exists.

3. **HTTP validation before writes**
   - cover blank/malformed/leading-or-trailing-whitespace/overlong email inputs;
   - cover blank, 11-character, and 129-character passwords;
   - receive 422 `VALIDATION_FAILED` with the expected field/key entries;
   - verify the response never echoes the raw password;
   - verify neither identity table receives a row for any invalid request.

4. **Malformed request body**
   - send malformed JSON;
   - receive 400 `MALFORMED_REQUEST` through the real global handler;
   - verify parser/internal details and the submitted fragment are not exposed;
   - verify no identity row is created.

Use explicit cleanup between test cases because the tests must observe committed state.

### HTTP/security

The PostgreSQL-backed MockMvc tests above are the HTTP acceptance tests.

No Spring Security test is required because introducing Spring Security is an explicit non-goal. The endpoint is intentionally reachable in the current no-filter-chain application.

All PR-001 through PR-006 tests must remain green.

## Acceptance criteria

1. Exactly one new production controller exposes `POST /api/v1/auth/register`.
2. The endpoint consumes JSON and returns 201 JSON with exactly one `userId` UUID field on success.
3. Request and response models are records kept in `identity.input` and `identity.output`, respectively.
4. HTTP validation exactly matches the accepted PR-005 email/password structural rules.
5. The controller delegates once to `LocalAccountRegistrationService` and contains no domain/persistence behavior.
6. A 201 response is backed by committed `user_account` and `LOCAL auth_identity` rows in PostgreSQL.
7. Duplicate casing produces 409 `EMAIL_ALREADY_REGISTERED` without new rows or sensitive-data leakage.
8. Invalid input produces the PR-006 422 validation shape without database writes.
9. Malformed JSON produces the PR-006 safe 400 contract without database writes.
10. Problem responses preserve PR-006 trace correlation and standard fields.
11. Raw passwords and password hashes are absent from every response.
12. No migration, entity, repository, application-service, error-contract, or trace-contract behavior changes.
13. No login, token, session, Spring Security, authorization, rate-limit, frontend, or legacy-route work is added.
14. The focused test uses the real Spring web context, real service, real repositories, real global handler/filter, and PostgreSQL Testcontainers.
15. Existing PR-005 registration tests and all PR-006 error tests remain green.
16. `./mvnw spotless:check`, focused tests, `./mvnw test`, and `./mvnw verify` pass.
17. `git diff --check` passes.
18. The Completion Record is accurate and `CURRENT.md` still points to PR-007.
19. The implementation agent performs no Git mutations.

## Documentation completion

Before this implementation unit is considered complete:

- fill in this specification's Completion Record;
- update `docs/implementation/STATE.md` with the implemented versioned registration endpoint and its response/error behavior;
- update `docs/review/progress-report.md` with the new endpoint and test count;
- keep `docs/implementation/CURRENT.md` pointing to PR-007 pending user review.

Do not mark login, authentication, tokens, sessions, or Spring Security as implemented.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=LocalAccountRegistrationHttpTest,LocalAccountRegistrationServiceTest,GlobalExceptionHandlerIntegrationTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required by its parser.

## Completion record

Fill this before marking PR-007 complete.

### Starting commit

- `a0bcd9bca6bcd6cbb099b815056acf6fb0281330` (`pr-006`)

### Implemented

- Added `LocalAccountRegistrationController` with the single `POST /api/v1/auth/register` JSON handler and HTTP-boundary validation.
- Added one-use request and response records under `identity.input` and `identity.output`; successful responses contain only the opaque `userId` and return HTTP 201 JSON without a `Location` header.
- Added PostgreSQL/Testcontainers MockMvc coverage using the real Spring web context, controller, registration service, repositories, global handler, and trace filter for success, duplicate, validation, and malformed-body behavior.
- Preserved the PR-005 registration workflow and PR-006 error/trace contracts without changing entities, repositories, application services, migrations, or schema.

### Deviations from specification

- None.

### New decisions

- Capability-owned HTTP request DTOs reside in directional `input` packages and response DTOs in `output` packages; the coding standards and backend master plan now define this convention.
- The HTTP integration test builds MockMvc from the real `WebApplicationContext` and explicitly registers the production `RequestTraceFilter` because the current test setup does not auto-configure MockMvc filters.
- The registration handler uses `@PostMapping("register")` and `@ResponseStatus(HttpStatus.CREATED)` with a direct response body; Spring infers JSON conversion and content negotiation without explicit `consumes` or `produces` mapping attributes.
- Error-case requests advertise `application/json` while asserting the global handler's `application/problem+json` responses; this lets Spring reach body validation/parsing while keeping the success mapping media-type-neutral.

### Tests executed

- `./mvnw spotless:check` (PowerShell: `mvnw.cmd spotless:check`) → BUILD SUCCESS.
- `./mvnw -Dtest=LocalAccountRegistrationHttpTest,LocalAccountRegistrationServiceTest,GlobalExceptionHandlerIntegrationTest test` (PowerShell equivalent) → 18 tests, 0 failures, 0 errors.
- `./mvnw test` (PowerShell equivalent) → 55 tests, 0 failures, 0 errors.
- `./mvnw verify` (PowerShell equivalent) → BUILD SUCCESS, including Spotless and all 55 tests.
- `git diff --check` → PASSED.

### Follow-up work

- PR-007 was reviewed and accepted by the user. Credential verification, HTTP login, tokens, sessions, Spring Security, and later authentication work remain separate implementation units.
