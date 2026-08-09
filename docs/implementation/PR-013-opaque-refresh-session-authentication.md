# PR-013 — Opaque refresh-session authentication

Status: **COMPLETE**

## Goal

Add one read-only application workflow that authenticates a presented opaque refresh token by hashing it, looking up the matching persisted generation, and verifying that the session and its user are currently eligible. A successful call returns only the opaque session UUID; every unusable credential fails uniformly without mutating session history.

This PR establishes refresh-credential authentication only. It does not rotate a token, issue a replacement token or access JWT, expose HTTP, or establish a durable authorization grant.

## Source documents

- `AGENTS.md`
- `docs/implementation/STATE.md`
- `docs/implementation/PR-008-local-password-authentication.md` — accepted uniform credential-failure pattern
- `docs/implementation/PR-010-initial-refresh-session-issuance.md` — accepted opaque token generation, deterministic hash, and session eligibility behavior
- `docs/implementation/PR-012-atomic-local-login-orchestration.md` — accepted initial session creation boundary
- `docs/review/backend-master-plan.md`
  - R1 items 5, 9, and 10: refresh sessions, reuse/revocation tests, and disabled-user rejection
  - persistence, security, testing, and pull-request rules
- `docs/review/backend-audit.md`
  - SEC-003: hash-backed opaque refresh sessions and later rotation/reuse detection
  - SEC-004: disabled-account rejection
- `docs/review/mobile-api-readiness.md`
  - high-entropy opaque refresh credentials stored hashed per device session
- `docs/engineering/coding-standards.md`
  - service transactions, meaningful repository queries, security, and PostgreSQL testing
- Spring Data JPA 4.1 documentation for derived property queries and `Optional` repository returns

`docs/review/accounting-contract.md` is not required because this PR introduces no financial behavior.

## Starting state

Treat local commit `5570f8d` as authoritative.

Already implemented and not to be redesigned here:

- `SecureRefreshTokenGenerator.generate()` creates 32 random bytes, Base64url-encodes the raw value without padding, and persists its deterministic SHA-256 Base64url hash through the issuance workflow;
- `RefreshSessionIssuanceService` creates one active initial `DeviceSession` for an eligible user and returns the raw token once;
- `identity.device_session.refresh_token_hash` is non-null and uniquely constrained;
- `DeviceSession` exposes its ID, stored hash, expiry, revocation state, and lazy user association inside an application transaction;
- the existing parameterless `IdentityErrorCode.INVALID_CREDENTIALS` is the uniform credential failure;
- no refresh-token lookup, rotation, reuse response, refresh HTTP endpoint, or session principal exists.

No migration, lock, or mutation is needed for read-only authentication of one current refresh-token generation.

## Scope

### 1. Reuse the accepted deterministic token hash

Extend the existing concrete component:

```text
dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator
```

with one public method:

```java
String hash(String rawToken)
```

The method must:

1. require a non-null raw token as an internal caller contract;
2. hash the exact UTF-8 bytes with a fresh JDK SHA-256 `MessageDigest` per call;
3. return URL-safe unpadded Base64 exactly matching the hash produced during generation;
4. accept any non-null string without trimming, Base64 parsing, format-specific early rejection, or logging;
5. retain the existing fail-closed behavior if SHA-256 is unexpectedly unavailable.

Refactor `generate()` to delegate its stored representation to this method so generation and presented-token authentication cannot drift. Keep the component concrete; do not add an interface, pepper/key property, alternate algorithm, password encoder, or second hashing class.

### 2. Add one stored-hash lookup

Add exactly one method to `DeviceSessionRepository`:

```java
Optional<DeviceSession> findByRefreshTokenHash(String refreshTokenHash)
```

Use Spring Data's derived property query. The existing unique constraint supplies lookup uniqueness. Do not add an existence query, explicit JPQL/native SQL, entity graph, projection, lock, second repository, or index.

This read-only lookup deliberately does not lock. A later rotation workflow must acquire the required lock and re-check eligibility atomically; this authentication result alone is not permission to rotate.

### 3. Add one refresh-session authentication service

Add one concrete Spring service:

```text
dev.canverse.stocks.identity.application.RefreshSessionAuthenticationService
```

Use constructor injection for exactly:

- `SecureRefreshTokenGenerator`;
- `DeviceSessionRepository`;
- `Clock`.

Expose exactly one public method:

```java
UUID authenticate(String rawRefreshToken)
```

Use a read-only transaction. Do not add `@Validated` or method-parameter Bean Validation; a null token is an internal caller contract violation handled by `Objects.requireNonNull`, while structural HTTP validation remains deferred.

Within the transaction:

1. require the raw token to be non-null before observing time, hashing, or querying;
2. capture `clock.instant()` exactly once;
3. hash the exact presented string exactly once through `SecureRefreshTokenGenerator.hash(...)`;
4. query `DeviceSessionRepository.findByRefreshTokenHash(...)` exactly once;
5. reject with `new AppException(IdentityErrorCode.INVALID_CREDENTIALS)` when the hash is unknown, the session is revoked, the session expires at or before the captured instant, or the associated user is disabled;
6. return only the matched `DeviceSession.id` when every check succeeds.

Do not update `lastUsedAt`, revoke a row/family, create a replacement, issue a token, return an entity/user/family ID, or call another authentication/session/token service. Do not catch persistence or application failures. Do not include the raw token, hash, session/user ID, or eligibility reason in an exception or log.

## Explicit non-goals

- No refresh rotation, replacement row, family revocation, reuse detection/response, pessimistic lock, or concurrent-refresh behavior.
- No access-token issuance, JWT decoder, bearer filter, principal, authorization, or security context.
- No controller, route, request/response API record, cookie, header, or web/mobile token-delivery decision.
- No login orchestration change, logout, explicit revocation, session listing/deletion, or last-used update.
- No new error code, event, audit record, rate limit, progressive delay, lockout, or notification.
- No migration, constraint, index, entity mapping/mutation, dependency, configuration property, frontend, or financial behavior.
- No refactor of token entropy, encoding, lifetime, or initial-session issuance semantics.

## Database changes

Migration(s): none.

Tables/columns/constraints/indexes introduced or changed: none.

The unique `identity.device_session.refresh_token_hash` constraint remains the authority for one stored generation per hash.

## Application changes

Expected production-code surface is limited to:

- `identity/infrastructure/SecureRefreshTokenGenerator.java` — add deterministic presented-token hashing and delegate generation to it;
- `identity/infrastructure/DeviceSessionRepository.java` — add one derived lookup;
- `identity/application/RefreshSessionAuthenticationService.java` — add the read-only workflow.

Do not add a result record: the successful result is one UUID.

## API contract

None. No endpoint, request/response shape, cookie/header, status code, or public token-delivery behavior is added or changed.

## Business invariants

- A presented refresh credential is compared only through the exact deterministic hash stored for its generation; the raw value is never queried or persisted.
- Unknown, revoked, expired, and disabled-user credentials are indistinguishable through the existing parameterless invalid-credentials failure.
- A session expiring exactly at the observed instant is invalid.
- Successful authentication returns only the matching session UUID and changes no persisted state.
- Authentication is a read-only observation, not a durable grant; rotation must lock and re-check the generation and family state.
- Raw tokens, hashes, IDs, and failure reasons never enter logs or exceptions.

## Required tests

### Pure/domain

Extend `SecureRefreshTokenGeneratorTest` to prove:

1. `hash(rawToken)` equals an independently calculated SHA-256 URL-safe unpadded Base64 value;
2. `generate().hash()` equals `hash(generate().rawToken())` for the same generated pair;
3. hashing is deterministic for the same exact input and different for a different input;
4. null fails the internal caller contract;
5. non-Base64 non-null input follows the same hashing path instead of a format-specific rejection.

Keep existing 256-bit entropy, encoding, uniqueness, and raw-versus-hash assertions intact. Do not print token values in assertion descriptions.

### PostgreSQL/Testcontainers

Add one `RefreshSessionAuthenticationServiceTest` using the real Spring context, migrated PostgreSQL, real generator, fixed or controllable `Clock`, and real repositories. Create valid sessions through accepted registration and refresh-session issuance where practical. Use deliberate fixture SQL only to establish revoked, expired, or disabled states. Use explicit cleanup and no test-level transaction.

Cover at least:

1. **Active refresh token authenticates its session**
   - issue a session for an enabled user;
   - authenticate the exact raw token and receive the persisted session UUID;
   - prove user, identity, and session rows—including `last_used_at`—are unchanged.

2. **Unknown, revoked, expired, and disabled-user credentials fail uniformly**
   - exercise all four states with distinct raw credentials;
   - each throws `AppException` with exactly parameterless `INVALID_CREDENTIALS`, the same safe description, and no submitted token/hash/ID/detail;
   - an expiry equal to the observed clock instant is rejected;
   - all persisted rows remain unchanged by each authentication attempt.

3. **Null fails before work**
   - null throws `NullPointerException` with the internal parameter name;
   - no clock observation, hash lookup, or persisted change occurs. A small non-Spring collaborator test is allowed only if required to prove call order without mocking persistence behavior.

All accepted registration, password-authentication, session issuance, local-login, access-token, migration, mapping, error, and HTTP tests must remain green.

### HTTP/security

The PostgreSQL service suite is the required security integration coverage for this application-only authentication PR. No MockMvc/filter-chain test is required because no HTTP or incoming bearer boundary is added.

## Acceptance criteria

1. `SecureRefreshTokenGenerator` adds only `hash(String)`, and `generate()` delegates stored-hash creation to it without changing token entropy/encoding.
2. Presented tokens are hashed exactly once from exact UTF-8 input with SHA-256 and URL-safe unpadded Base64; arbitrary non-null strings use the same path.
3. `DeviceSessionRepository` adds only `Optional<DeviceSession> findByRefreshTokenHash(String refreshTokenHash)` and no lock/query/index.
4. Exactly one new production service exposes `UUID authenticate(String rawRefreshToken)` with a read-only transaction.
5. Null fails before time/hash/query work; non-null input observes time once, hashes once, and performs one stored-hash lookup.
6. Only an unrevoked session expiring strictly after the observed instant for an enabled user succeeds and returns its UUID.
7. Unknown, revoked, expired-at-or-before-now, and disabled-user credentials throw indistinguishable parameterless `INVALID_CREDENTIALS` failures.
8. Authentication mutates no user, identity, or session field and creates no row/token/event/job.
9. No raw token, hash, identifier, or eligibility reason is logged or exposed through exceptions.
10. No migration, mapping, entity mutation, dependency, configuration, API, rotation/reuse response, lock, bearer/principal, logout/revocation, frontend, or financial change is added.
11. Required pure and PostgreSQL tests pass without weakening existing assertions.
12. `./mvnw spotless:check`, focused suite, `./mvnw test`, and `./mvnw verify` pass.
13. `git diff --check` passes and the complete diff contains only PR-013 planning, production, test, and completion-document files.
14. Completion Record, `STATE.md`, and `progress-report.md` are accurate; `CURRENT.md` remains on PR-013 through review.
15. Implementation and review agents perform no Git mutation.

## Documentation completion

Before completion, fill this Completion Record, update `STATE.md` and `progress-report.md`, and keep `CURRENT.md` on PR-013 until supervisor commit.

Do not mark refresh rotation/reuse response, HTTP token delivery, bearer authentication, logout/revocation, persistent keys, events, or abuse controls implemented.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=RefreshSessionAuthenticationServiceTest,SecureRefreshTokenGeneratorTest,RefreshSessionIssuanceServiceTest,LocalLoginServiceTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required.

## Completion record

Fill this before marking PR-013 complete.

### Starting commit

- `5570f8d` (`orchestrate atomic local login`).

### Accepted commit

- `7bb7c40` (`authenticate opaque refresh sessions`).

### Implemented

- Extended `SecureRefreshTokenGenerator` with exact-input deterministic SHA-256 Base64url hashing, and made generation delegate its stored representation to the same method.
- Added the single derived `DeviceSessionRepository.findByRefreshTokenHash(...)` lookup backed by the existing unique stored-hash constraint.
- Added read-only `RefreshSessionAuthenticationService.authenticate(...)` with null-before-work enforcement, one clock observation/hash/lookup, UUID-only success, and uniform rejection of unknown, revoked, expired-at-or-before-now, and disabled-user sessions.
- Added pure generator and collaborator-order coverage plus PostgreSQL/Testcontainers authentication coverage proving exact active-session resolution, uniform safe failures, equality-boundary expiry rejection, and complete identity/session immutability.
- Updated implementation state and progress documentation while keeping `CURRENT.md` on PR-013.

### Deviations from specification

- None.

### New decisions

- None. Refresh-session authentication remains a read-only observation; rotation must later lock and re-check the presented generation and family state atomically.

### Tests executed

- `./mvnw spotless:check` — passed; all 59 Java files are clean.
- `./mvnw "-Dtest=RefreshSessionAuthenticationServiceTest,SecureRefreshTokenGeneratorTest,RefreshSessionIssuanceServiceTest,LocalLoginServiceTest" test` — passed, 14 tests.
- `./mvnw test` — passed, 84 tests.
- `./mvnw verify` — passed, 84 tests; executable JAR packaging and the bound Spotless check succeeded.
- `git status --short`, `git diff --check`, and the complete scope diff — passed; only PR-013 planning, production, test, and completion-document files are present.

### Follow-up work

- Refresh rotation/replacement, family-reuse detection and response, HTTP token delivery, bearer authentication, logout/revocation/session management, persistent signing keys, security events, and abuse controls remain deferred to separately scoped units.
