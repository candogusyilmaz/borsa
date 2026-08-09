# PR-008 — Local password credential verification

Status: **ACTIVE**

## Goal

Add one application-layer workflow that verifies a local email/password credential against the accepted `LOCAL` authentication identity and returns the matched opaque user ID. Unknown emails, wrong passwords, unusable local identities, and disabled accounts fail through one indistinguishable `INVALID_CREDENTIALS` application error.

This PR establishes credential verification only. It does not expose HTTP login, establish a principal, or issue or persist any token or session.

## Source documents

- `AGENTS.md`
- `docs/implementation/STATE.md`
- `docs/implementation/PR-005-atomic-local-account-registration.md` — accepted local identity, normalization, password-hash, and persistence behavior
- `docs/implementation/PR-006-stable-error-handling.md` — accepted `AppException` / `IdentityErrorCode` contract
- `docs/implementation/PR-007-http-local-account-registration.md` — accepted registration boundary; login remains separate
- `docs/review/backend-master-plan.md`
  - R1 item 5: local email/password login
  - security and testing rules
- `docs/review/backend-audit.md`
  - SEC-004 disabled-account rejection
  - SEC-005 authentication abuse controls, which remain deferred
- `docs/review/mobile-api-readiness.md`
  - authentication/session separation and disabled-user rejection
- `docs/engineering/coding-standards.md`
  - application-service transactions, errors, security, packages, and PostgreSQL testing

`docs/review/accounting-contract.md` is not required because this PR introduces no financial behavior.

## Starting state

Treat the reviewed PR-007 repository state as authoritative.

Already implemented and not to be redesigned here:

- local registration persists one `UserAccount` and one `LOCAL` `AuthIdentity` atomically;
- local provider subjects and account lookup emails use `Locale.ROOT` lowercase normalization;
- local password hashes use the configured delegating Spring Security `PasswordEncoder` and carry a self-describing `{id}` prefix;
- `AuthIdentity.passwordHash` is nullable because the table also supports external identities;
- `UserAccount.disabledAt` represents the existing disabled-account state;
- `AppException`, `IdentityErrorCode`, the global HTTP error boundary, and request tracing already exist;
- `uq_auth_identity_provider_subject` uniquely indexes `(provider, provider_subject)` and supports the required credential lookup;
- no Spring Security web filter chain, login endpoint, token issuer, authenticated principal, or device-session write workflow exists.

No migration, web endpoint, token design, or session infrastructure is a prerequisite for verifying credentials at the application boundary.

## Scope

### 1. Add the invalid-credentials error code

Add exactly one capability error:

```text
IdentityErrorCode.INVALID_CREDENTIALS
```

It must have:

- HTTP metadata `401 Unauthorized` for the later HTTP boundary;
- no interpolation parameters;
- a generic safe description that does not distinguish an unknown email, wrong password, missing password hash, non-local identity, or disabled account;
- the derived key `error.identity.invalid_credentials` through the existing error-code mechanism.

Do not add separate public errors for unknown email, wrong password, disabled account, or missing local identity.

### 2. Add one local-identity lookup

Add exactly one repository query to `AuthIdentityRepository`:

```java
Optional<AuthIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
```

The service calls it with provider `LOCAL` and the normalized email. Do not add a custom repository, native query, entity graph, locking query, user-account lookup, or a second existence check.

The existing migration-owned unique constraint/index is sufficient. Do not add another index.

### 3. Add one credential-verification service

Create one concrete Spring service:

```text
dev.canverse.stocks.identity.application.LocalPasswordAuthenticationService
```

Expose exactly one public method:

```java
UUID authenticate(String email, String rawPassword)
```

Use constructor injection for:

- `AuthIdentityRepository`;
- the existing `PasswordEncoder`.

Use method validation and a read-only transaction. Apply the currently accepted local email/password structural bounds:

```java
@NotBlank @Email @Size(max = 320) @Pattern(regexp = "\\S(?:.*\\S)?") String email
@NotBlank @Size(min = 12, max = 128) String rawPassword
```

For structurally valid input, the workflow must:

1. Normalize the email once with `email.toLowerCase(Locale.ROOT)`; do not trim it.
2. Look up exactly provider `LOCAL` plus the normalized email.
3. Call `PasswordEncoder.matches(rawPassword, encodedHash)` exactly once.
4. Use the stored hash when a local identity has a non-null hash; otherwise use one service-owned dummy encoded hash so the missing-identity/null-hash path still performs password-hash work.
5. After the match attempt, reject with `new AppException(IdentityErrorCode.INVALID_CREDENTIALS)` when:
   - no matching local identity exists;
   - its password hash is null;
   - the password does not match; or
   - the associated user account has non-null `disabledAt`.
6. Return only the associated `UserAccount` UUID when the local identity exists, its hash matches, and the account is enabled.

Create the dummy encoded hash once per service instance from the configured `PasswordEncoder` and a fixed non-secret dummy input. Do not persist it, expose it, regenerate it per login attempt, or claim exact constant-time behavior. Its purpose is only to avoid the obvious branch where an unknown email skips password-hash work entirely.

Do not call `encode(rawPassword)` during authentication. Do not compare raw strings or expose the entity outside the transaction.

### 4. Preserve read-only behavior

Authentication in this PR performs no write:

- do not update `last_used_at`, `updated_at`, password hashes, or account state;
- do not create a device session, security event, token, audit row, job, or principal;
- do not upgrade/re-hash an otherwise valid stored credential;
- do not lock identity rows.

A later token/session workflow must re-check account eligibility in its own transaction; this credential-verification result is not a durable authorization grant.

## Explicit non-goals

- `POST /api/v1/auth/login` or any other endpoint;
- request/response DTOs or controller changes;
- `spring-boot-starter-security`, `SecurityFilterChain`, `AuthenticationManager`, `UserDetailsService`, HTTP Basic, form login, CSRF, CORS, or request authorization;
- access tokens, JWT/JOSE dependencies, claims, signing keys, issuers, audiences, cookies, or bearer-token parsing;
- refresh tokens, `DeviceSession` creation, rotation, reuse detection, logout, session listing, or revocation;
- a current-principal helper or `GET /api/v1/me`;
- Google/OIDC/OAuth authentication;
- password-hash upgrading, password change/reset, email verification, or account recovery;
- registration behavior or password-policy changes;
- rate limiting, progressive delay, temporary lockout, failed-attempt counters, CAPTCHA, or IP/device reputation;
- `SecurityEvent` writes or authentication notifications;
- roles, permissions, households, or owner-scope infrastructure;
- a generic credentials interface, authentication-provider catalogue, strategy hierarchy, command handler, façade, or result wrapper;
- schema, migration, entity-mapping, or entity-mutation changes;
- frontend changes;
- financial/reference/ledger work;
- Git operations.

## Database changes

None.

Do not add or modify a Flyway migration, table, column, constraint, or index. Do not change a JPA entity mapping.

`identity.auth_identity` already has the migration-owned unique index needed for `(provider, provider_subject)` lookup. The nullable `password_hash` remains unchanged because non-local identities do not require a local password.

## Application changes

Expected production-code surface:

```text
src/main/java/dev/canverse/stocks/identity/
├── application/
│   └── LocalPasswordAuthenticationService.java
├── error/
│   └── IdentityErrorCode.java                  # INVALID_CREDENTIALS only
└── infrastructure/
    └── AuthIdentityRepository.java             # one lookup method
```

Expected test surface:

```text
src/test/java/dev/canverse/stocks/identity/
├── LocalPasswordAuthenticationServiceTest.java
└── LocalPasswordAuthenticationTimingTest.java
```

Keep the PostgreSQL integration cases in the service test and the non-Spring control-flow case in the timing test. Do not introduce reusable authentication test infrastructure for this one workflow.

## API contract

None.

This PR adds application error metadata for `INVALID_CREDENTIALS`, but no HTTP route exposes it yet. The PR-006 global error format is unchanged.

## Business invariants

- Only a `LOCAL` identity with a matching password can authenticate through this workflow.
- Email lookup is case-insensitive through the same `Locale.ROOT` normalization used by registration.
- Unknown email, wrong password, null local hash, and disabled account expose the same `INVALID_CREDENTIALS` application failure with no params.
- Every structurally valid attempt performs exactly one password match, including an unknown identity, without claiming strict constant-time equivalence.
- Authentication returns an opaque user ID only; it does not establish a principal, authorization, token, or session.
- Credential verification is read-only and leaves all persisted identity data unchanged.
- Raw passwords never enter an entity, query parameter, result, exception parameter/message, response, or log.

## Required tests

### Pure/domain

Add one focused non-Spring test for the missing-identity timing fallback. Using a test double or Mockito is appropriate here because the test proves application control flow rather than persistence behavior.

Prove that a structurally valid unknown email:

- causes the dummy hash to be encoded once when the service is constructed and not once per attempt;
- still causes exactly one `PasswordEncoder.matches(...)` call;
- uses the service-owned dummy encoded hash rather than encoding the submitted password;
- throws `AppException` with `IdentityErrorCode.INVALID_CREDENTIALS`;
- does not expose the submitted email or password in the exception.

Do not assert elapsed milliseconds or claim cryptographic constant-time behavior.

### PostgreSQL/Testcontainers

Add focused service integration coverage using the real Spring context, migrated PostgreSQL, the real delegating `PasswordEncoder`, and committed setup state. Do not use H2 or mock the repository for these cases.

1. **Valid local credentials**
   - establish a user through the accepted registration service;
   - authenticate with different email casing and the correct password;
   - receive the registered user UUID;
   - verify user/auth-identity row counts, password hash, and timestamps are unchanged.

2. **Wrong password and unknown email are indistinguishable**
   - authenticate an existing email with a wrong valid-length password;
   - authenticate an unknown valid email with a valid-length password;
   - assert both throw `AppException` with exactly `INVALID_CREDENTIALS`, no params, and the same safe description;
   - verify neither submitted email nor password appears in either exception;
   - verify no identity row changes.

3. **Disabled account fails closed**
   - register a valid account, then set its existing `disabled_at` column through deliberate test fixture SQL;
   - authenticate with the correct password;
   - receive the same `INVALID_CREDENTIALS` failure;
   - verify no persisted value changes during the authentication attempt.

4. **Unusable local identity fails closed**
   - establish a `LOCAL` identity with a null password hash through deliberate fixture SQL;
   - authenticate its provider subject;
   - receive `INVALID_CREDENTIALS`, not a null-pointer/internal failure;
   - verify no data changes.

5. **Method validation rejects structurally invalid input**
   - cover blank/malformed/leading-or-trailing-whitespace/overlong email;
   - cover blank, 11-character, and 129-character passwords;
   - assert method validation rejects every case and persisted state remains unchanged.

Use explicit cleanup between cases and no test-level transaction that could hide the service's read-only transaction behavior.

### HTTP/security

No new HTTP/security integration test is required because this PR adds no endpoint, filter chain, principal, token, or session.

All accepted registration HTTP tests and PR-006 error-contract tests must remain green.

## Acceptance criteria

1. `IdentityErrorCode` adds exactly `INVALID_CREDENTIALS` with 401 metadata, no params, and derived key `error.identity.invalid_credentials`.
2. `AuthIdentityRepository` adds only `findByProviderAndProviderSubject(...)` and the implementation calls it with `LOCAL` plus `Locale.ROOT` lowercase email.
3. Exactly one new production service exposes `UUID authenticate(String email, String rawPassword)`.
4. The service uses method validation and a read-only application transaction.
5. Valid credentials return the associated enabled user's UUID.
6. Unknown email, wrong password, null local hash, and disabled account all throw the same parameterless `INVALID_CREDENTIALS` error.
7. Every structurally valid attempt calls `PasswordEncoder.matches(...)` exactly once; missing/unusable identities use one service-owned dummy hash.
8. The submitted raw password is never encoded for storage, persisted, returned, logged, or included in an exception.
9. Authentication does not modify any identity row or create a session, token, event, job, or principal.
10. No dependency, migration, constraint, index, entity, controller, DTO, global-error-handler, trace-filter, or frontend change is made.
11. No Spring Security web/authentication framework, token/session behavior, external provider, abuse-control, or authorization work is added.
12. The pure timing-fallback test and all required PostgreSQL cases pass.
13. All PR-001 through PR-007 tests remain green.
14. `./mvnw spotless:check`, focused tests, `./mvnw test`, and `./mvnw verify` pass.
15. `git diff --check` passes.
16. The Completion Record is accurate and `CURRENT.md` still points to PR-008 pending user review.
17. The implementation agent performs no Git mutations.

## Documentation completion

Before this implementation unit is considered complete:

- fill in this specification's Completion Record;
- update `docs/implementation/STATE.md` with the implemented credential-verification behavior and deferred HTTP/session boundary;
- update `docs/review/progress-report.md` with the implementation and verified test count;
- keep `docs/implementation/CURRENT.md` pointing to PR-008 pending user review.

Do not mark HTTP login, authentication filters, tokens, sessions, security events, or abuse controls as implemented.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=LocalPasswordAuthenticationServiceTest,LocalPasswordAuthenticationTimingTest,LocalAccountRegistrationServiceTest,LocalAccountRegistrationHttpTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required by its parser.

## Completion record

Fill this before marking PR-008 complete.

### Starting commit

- `0046bd0` (`pr-007`), the user-created commit containing the reviewed PR-007 state.

### Implemented

- Added `IdentityErrorCode.INVALID_CREDENTIALS` with HTTP 401 metadata, no parameters, and the derived key `error.identity.invalid_credentials`.
- Added the single `AuthIdentityRepository.findByProviderAndProviderSubject(...)` lookup.
- Added read-only `LocalPasswordAuthenticationService.authenticate(...)` with `LOCAL`/`Locale.ROOT` lookup, one password match per attempt, dummy-hash fallback, disabled-account rejection, and UUID-only success.
- Added the pure dummy-hash control-flow test and four PostgreSQL/Testcontainers service cases for success, indistinguishable failures, disabled accounts, and null hashes.

### Deviations from specification

- Per explicit user direction, the service does not use `@Validated` or method-parameter validation annotations. Structural input validation remains deferred to the future HTTP login boundary; the service-level structural-validation test is therefore not included.

### New decisions

- None.

### Tests executed

- `./mvnw.cmd spotless:check` → BUILD SUCCESS.
- `./mvnw.cmd -Dtest=LocalPasswordAuthenticationServiceTest,LocalPasswordAuthenticationTimingTest,LocalAccountRegistrationServiceTest,LocalAccountRegistrationHttpTest test` → 13 tests, 0 failures, 0 errors.
- `./mvnw.cmd test` → 60 tests, 0 failures, 0 errors.
- `./mvnw.cmd verify` → BUILD SUCCESS, including Spotless and all 60 tests.
- `git diff --check` → PASSED.

### Follow-up work

- HTTP login, principals, tokens, device sessions, Spring Security web configuration, security events, abuse controls, and all later authentication work remain deferred to separately scoped units.
