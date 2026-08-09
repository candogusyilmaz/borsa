# PR-012 — Atomic local login orchestration

Status: **COMPLETE**

## Goal

Add one application-layer local-login workflow that verifies an email/password, creates the initial opaque refresh session, and issues its short-lived access token within one database transaction. A successful call returns both raw tokens and their exact expiries once; failure after session persistence rolls the session back.

This PR establishes transactionally atomic orchestration only. It does not expose an HTTP endpoint or choose web/mobile token-delivery semantics.

## Source documents

- `AGENTS.md`
- `docs/implementation/STATE.md`
- `docs/implementation/PR-008-local-password-authentication.md` — accepted credential-verification and uniform-failure behavior
- `docs/implementation/PR-010-initial-refresh-session-issuance.md` — accepted initial opaque refresh-session behavior
- `docs/implementation/PR-011-local-access-token-issuance.md` — accepted session-bound access-token behavior
- `docs/review/backend-master-plan.md`
  - R1 item 5: local email/password login and sessions
  - direct transactional orchestration, security, testing, and pull-request rules
- `docs/review/backend-audit.md`
  - SEC-003: distinguish access tokens from refresh sessions
  - SEC-004: disabled-account rejection
- `docs/review/mobile-api-readiness.md`
  - recommended access-token and device-refresh-session model
- `docs/engineering/coding-standards.md`
  - transaction boundaries, application result placement, security, and PostgreSQL testing
- Spring Framework 7 transaction documentation for default `REQUIRED` participation and unchecked-exception rollback

`docs/review/accounting-contract.md` is not required because this PR introduces no financial behavior.

## Starting state

Treat local commit `6aa57b6` as authoritative.

Already implemented and not to be redesigned here:

- `LocalPasswordAuthenticationService.authenticate(email, rawPassword)` returns only an enabled user UUID and uses uniform parameterless `INVALID_CREDENTIALS` failures;
- `RefreshSessionIssuanceService.issue(userAccountId, deviceLabel)` creates and flushes one initial device session, returns its raw refresh token once, and persists only its hash;
- `AccessTokenIssuanceService.issue(sessionId)` loads the eligible session and returns one exact RS256 access token and whole-second expiry without writes;
- all three services use default `REQUIRED` transaction propagation, so calls through their Spring bean proxies can participate in one outer transaction;
- no HTTP login request/response contract, cookie policy, bearer decoder, authenticated principal, or authorization filter chain exists.

## Scope

### 1. Add one immutable application result

Add:

```text
dev.canverse.stocks.identity.application.LocalLoginResult
components, in order:
  sessionId: UUID
  accessToken: String
  accessTokenExpiresAt: Instant
  refreshToken: String
  refreshTokenExpiresAt: Instant
```

Require every component to be non-null in the compact constructor. This is an application result, not an HTTP DTO; keep it in `identity.application` and do not add serialization or Bean Validation annotations.

Do not include user email, password, password hash, stored refresh-token hash, user ID, roles, permissions, JPA entities, or signing-key information.

### 2. Add one atomic local-login service

Add one concrete Spring service:

```text
dev.canverse.stocks.identity.application.LocalLoginService
```

It must use constructor injection for exactly:

- `LocalPasswordAuthenticationService`;
- `RefreshSessionIssuanceService`;
- `AccessTokenIssuanceService`.

Expose one public workflow method:

```java
LocalLoginResult login(String email, String rawPassword, String deviceLabel)
```

Apply one ordinary write `@Transactional` boundary to this method. Do not select `REQUIRES_NEW`, add manual transaction APIs, or catch and suppress downstream failures.

The method must:

1. treat null `email` and null `rawPassword` as internal caller contract violations via `Objects.requireNonNull` before invoking a downstream service;
2. allow nullable `deviceLabel`, matching accepted initial-session issuance;
3. call `LocalPasswordAuthenticationService.authenticate(email, rawPassword)` exactly once;
4. pass the returned user UUID and unchanged `deviceLabel` to `RefreshSessionIssuanceService.issue(...)` exactly once;
5. pass the returned session UUID to `AccessTokenIssuanceService.issue(...)` exactly once;
6. construct `LocalLoginResult` directly from the two accepted issuance results, preserving token strings, session UUID, and expiry instants exactly;
7. return no value until all three calls succeed.

Do not duplicate email normalization, password matching, user/session eligibility checks, refresh-token generation/hashing, JWT construction, or expiry calculations. Do not call repositories directly. Do not add `@Validated` or parameter Bean Validation annotations; structural input validation belongs to the later HTTP boundary.

Default Spring `REQUIRED` propagation must keep credential reads, the flushed session insert, and access-token issuance in the outer login transaction. Any unchecked failure from access-token issuance must escape unchanged and roll back the new session row. Non-transactional generators may consume values during a failed attempt; no generated raw token or identifier may be returned or persisted as a committed session.

## Explicit non-goals

- No controller, route, input/output API record, JSON contract, status code, cookie, header, CORS, or CSRF policy.
- No access-token decoder, resource server, bearer filter, principal, authorization rule, or `/api/v1/me` behavior.
- No refresh-token lookup, refresh rotation, reuse detection, logout, revocation, session listing, or session deletion.
- No persistent signing key, JWK endpoint, key rotation, provider/OIDC/Google login, role, or permission work.
- No new error code, exception hierarchy, authentication-event recording, throttling, progressive delay, or lockout.
- No migration, entity/mapping mutation, repository query, dependency, runtime property, frontend, or financial behavior.
- No refactor of the three accepted component services.

## Database changes

Migration(s): none.

Tables/columns/constraints/indexes introduced or changed: none.

The workflow writes only the already-defined initial row in `identity.device_session` through `RefreshSessionIssuanceService`. All other database state remains unchanged.

## Application changes

Expected production files are limited to:

- `identity/application/LocalLoginResult.java`;
- `identity/application/LocalLoginService.java`.

The service is the initiating orchestration boundary. It delegates domain/security details to the three accepted services and owns only call order, result assembly, and transaction atomicity.

## API contract

None. No incoming or outgoing HTTP contract is added or changed.

## Business invariants

- Invalid local credentials produce the existing parameterless `INVALID_CREDENTIALS` outcome and create no refresh session.
- Exactly one initial refresh session is committed only when credential verification, refresh issuance, and access issuance all succeed.
- Access-token issuance is bound to the exact session created by the same login attempt.
- A downstream unchecked failure after `saveAndFlush` commits no device-session row and returns no tokens.
- Successful output preserves the accepted session ID, token strings, and expiry instants exactly; the orchestration does not recompute or transform them.
- Passwords, tokens, hashes, signing material, and sensitive failure details are not logged.

## Required tests

### Pure/domain

None. The new behavior is Spring transaction composition and requires the PostgreSQL integration test below.

### PostgreSQL/Testcontainers

Add one `LocalLoginServiceTest` using the real Spring application services and PostgreSQL. Do not use a test-level transaction; clean fixtures explicitly so committed and rolled-back state is observable.

Use real password encoding, credential verification, refresh-session issuance, and access-token issuance. Replace only the `JwtEncoder` side-effect boundary with a deterministic controllable test bean that can return a token or throw an unchecked encoding failure. Do not mock JPA repositories or any of the three composed application services.

Cover at least:

1. **Successful local login commits one composed result**
   - create one enabled local account with a valid encoded password;
   - call `login` with the accepted email/password and a non-null device label;
   - assert one device-session row commits with the expected user, label, active initial-generation state, and hash-only refresh storage;
   - assert the returned session ID equals the persisted session and the access-token `sid` input;
   - assert access/refresh token values and both exact expiries are preserved from their issuing services;
   - assert no user/auth-identity row changes.

2. **Invalid credentials short-circuit before issuance**
   - exercise an invalid password for an existing enabled local account;
   - assert parameterless `INVALID_CREDENTIALS`;
   - assert no session row, no JWT encoding, and no application ID generation for session or token instance;
   - assert identity rows remain unchanged and no secret/detail appears in the exception.

3. **Access-token failure rolls back the flushed refresh session**
   - accept valid credentials and configure the test encoder to throw an unchecked JWT encoding failure;
   - assert the same unchecked failure escapes the login service;
   - assert zero device-session rows after the call despite refresh-session `saveAndFlush` having executed;
   - assert no token/result is returned and identity rows remain unchanged.

The test may additionally cover a null device label on a successful call if this keeps the suite concise. All previously accepted identity/security tests must remain green.

### HTTP/security

The PostgreSQL transaction-composition suite is the required security integration coverage for this application-only PR. No MockMvc or filter-chain test is required because no HTTP boundary exists.

## Acceptance criteria

1. Exactly two production files add one immutable application result and one concrete orchestration service.
2. The public method is exactly `LocalLoginResult login(String email, String rawPassword, String deviceLabel)` and has one ordinary write transaction boundary.
3. Null email/password fail as internal caller violations; nullable device label passes through unchanged; no service-level Bean Validation is added.
4. Credential authentication, refresh-session issuance, and access-token issuance are each invoked once and in that order with exact prior outputs/inputs.
5. The orchestration duplicates none of the accepted authentication, session, token, eligibility, hash, or expiry logic and accesses no repository directly.
6. A successful call commits exactly one initial device session and returns the exact session ID, both raw tokens, and both expiries without exposing other state.
7. Invalid credentials create no session and perform no access-token issuance.
8. An unchecked access-token encoding failure after refresh-session flush rolls the device-session insert back and escapes unchanged.
9. No password, raw token, stored hash, signing key, or sensitive failure detail is logged or added to exceptions.
10. No migration, mapping, repository, dependency, configuration, HTTP/API, decoder/filter/principal, refresh lifecycle, event/abuse-control, frontend, or financial change is added.
11. Required focused tests and all prior tests pass without weakening an existing assertion.
12. `./mvnw spotless:check`, focused suite, `./mvnw test`, and `./mvnw verify` pass.
13. `git diff --check` passes and the complete diff contains only PR-012 planning, production, test, and completion-document files.
14. Completion Record, `STATE.md`, and `progress-report.md` are accurate; `CURRENT.md` remains on PR-012 through review.
15. Implementation and review agents perform no Git mutation.

## Documentation completion

Before completion, fill this Completion Record, update `STATE.md` and `progress-report.md`, and keep `CURRENT.md` on PR-012 until supervisor commit.

Do not mark HTTP login/token delivery, bearer authentication, refresh rotation, logout/revocation, persistent keys, events, or abuse controls implemented.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=LocalLoginServiceTest,LocalPasswordAuthenticationServiceTest,RefreshSessionIssuanceServiceTest,AccessTokenIssuanceServiceTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required.

## Completion record

Fill this before marking PR-012 complete.

### Starting commit

- `6aa57b6` (`issue local access tokens`).

### Accepted commit

- `5570f8d` (`orchestrate atomic local login`).

### Implemented

- Added immutable `LocalLoginResult` with required session ID, raw access/refresh tokens, and their exact expiry instants.
- Added `LocalLoginService.login(...)` with null email/password caller checks and one ordinary write transaction that invokes the accepted credential, refresh-session, and access-token services exactly once in order and directly assembles their outputs.
- Added PostgreSQL/Testcontainers transaction-composition coverage using the real component services and only a controllable primary `JwtEncoder` test boundary.
- Proved successful exact result/session commit, hash-only refresh persistence, invalid-credential short-circuit before ID/JWT issuance, unchanged nullable-label semantics, flushed-session rollback with unchanged exception identity, and null credential caller contracts.
- Updated implementation state and progress documentation while keeping `CURRENT.md` on PR-012.

### Deviations from specification

- None.

### New decisions

- None. The orchestration relies on accepted default Spring `REQUIRED` participation and unchecked-exception rollback semantics without adding manual transaction APIs or alternate propagation.

### Tests executed

- `./mvnw spotless:check` — passed; all 56 Java files are clean.
- `./mvnw -Dtest=LocalLoginServiceTest,LocalPasswordAuthenticationServiceTest,RefreshSessionIssuanceServiceTest,AccessTokenIssuanceServiceTest test` — passed, 17 tests.
- `./mvnw test` — passed, 78 tests.
- `./mvnw verify` — passed, 78 tests; executable JAR packaging and the bound Spotless check succeeded.
- `git status --short`, `git diff --check`, and the complete scope diff — passed; only PR-012 planning, production, test, and completion-document files are present.

### Follow-up work

- HTTP login/token delivery, a production decoder/resource server and authenticated principal/filter chain, persistent signing-key management, refresh lookup/rotation/reuse detection, logout/revocation/session management, security events, and abuse controls remain deferred.
