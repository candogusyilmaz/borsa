# PR-010 — Initial opaque refresh-session issuance

Status: **COMPLETE**

## Goal

Add one transactional application workflow that creates the first server-side refresh session for an already authenticated local user. The workflow returns a newly generated high-entropy opaque refresh token exactly once while persisting only its deterministic one-way hash in the existing `identity.device_session` table.

This PR establishes initial refresh-session issuance only. It does not expose HTTP login, issue an access token, authenticate bearer requests, rotate refresh sessions, or establish a Spring Security principal.

## Source documents

- `AGENTS.md`
- `docs/implementation/STATE.md`
- `docs/implementation/PR-008-local-password-authentication.md` — accepted credential verification and required eligibility recheck by later session workflows
- `docs/implementation/PR-009-automated-batch-state-reconciliation.md` — accepted starting-state reconciliation
- `docs/review/backend-master-plan.md`
  - R1 item 5: local login and refresh-session behavior
  - pull-request execution model and testing strategy
- `docs/review/backend-audit.md`
  - SEC-003: opaque refresh sessions stored hashed server-side
  - SEC-004: disabled-account rejection
- `docs/review/mobile-api-readiness.md`
  - recommended device-aware session model
- `docs/engineering/coding-standards.md`
  - application transactions, configuration properties, JPA writes, security, and PostgreSQL testing
- Spring Boot 4.1 documentation for constructor-bound `@ConfigurationProperties`, `Duration`, `@DefaultValue`, and `@EnableConfigurationProperties`

`docs/review/accounting-contract.md` is not required because this PR introduces no financial behavior.

## Starting state

Treat local commit `82f6a34` as authoritative.

Already implemented and not to be redesigned here:

- local registration creates an enabled `UserAccount` and `LOCAL` `AuthIdentity` atomically;
- local password verification returns only the enabled user's UUID and does not create a durable authorization grant;
- `identity.device_session` already stores one row per refresh-token generation with `id`, `user_account_id`, `family_id`, `refresh_token_hash`, optional `device_label`, creation/expiry/revocation fields, and replacement linkage;
- `uix_device_session_active_family` permits at most one non-revoked row per family;
- `DeviceSession` and `DeviceSessionRepository` currently provide mapping/read behavior only;
- injected `Clock` and `IdGenerator` abstractions already exist;
- no access-token issuer, HTTP login endpoint, refresh endpoint, security filter chain, principal, or session write workflow exists.

No migration or access-token design is a prerequisite for creating the first opaque refresh-session generation.

## Scope

### 1. Add refresh-session lifetime configuration

Add an immutable constructor-bound configuration-properties type:

```text
dev.canverse.stocks.identity.configuration.RefreshSessionProperties
prefix: stocks.identity.refresh-session
property: lifetime
default: 30d
```

Enable this specific properties type from one identity configuration class using `@EnableConfigurationProperties`. Use Spring Boot's `@DefaultValue("30d")` constructor binding so the application does not require a new checked-in environment value.

Reject a null, zero, or negative lifetime during configuration binding/construction. Do not add access-token, cookie, issuer, audience, signing-key, rotation, or abuse-control properties.

### 2. Generate and hash opaque refresh tokens

Add one concrete Spring component:

```text
dev.canverse.stocks.identity.infrastructure.SecureRefreshTokenGenerator
```

It must:

1. keep one `java.security.SecureRandom` instance per component;
2. generate exactly 32 random bytes per token;
3. encode the raw token with the URL-safe Base64 encoder without padding;
4. hash the raw token's UTF-8 bytes with a fresh SHA-256 `MessageDigest` per operation so the singleton component is thread-safe;
5. encode the hash with the same URL-safe unpadded Base64 form;
6. return the raw value and hash as one immutable application result used by the issuance service.

The component must fail construction or the operation if the JDK-mandated SHA-256 algorithm is unexpectedly unavailable; it must never fall back to a weaker algorithm. No new cryptography or token dependency is allowed.

Represent the generated pair with one immutable application record:

```text
dev.canverse.stocks.identity.application.GeneratedRefreshToken
components: rawToken, hash
```

Do not use `PasswordEncoder` for refresh tokens: the token has 256 bits of server-generated entropy and its deterministic hash must support a later indexed lookup. Never log, persist, or place the raw token in an exception.

### 3. Add the initial DeviceSession construction path

Add one intention-revealing static factory to `DeviceSession` for an initial session generation. It must set:

- `id` from the application `IdGenerator`;
- `userAccount` to the eligible persisted account;
- `familyId` equal to the initial session `id`;
- `refreshTokenHash` to the generated SHA-256 representation;
- `deviceLabel` exactly as supplied, including `null`;
- `createdAt` to the injected clock instant;
- `expiresAt` to `createdAt + configured lifetime`;
- `lastUsedAt`, `revokedAt`, `revokeReason`, and `replacedBySessionId` to `null`.

Require all non-null fields and require `expiresAt` to be strictly after `createdAt`. Do not add setters, rotation/revocation methods, mapping annotations, or schema metadata.

### 4. Add one issuance application service

Create one concrete Spring service:

```text
dev.canverse.stocks.identity.application.RefreshSessionIssuanceService
```

Expose exactly one public method:

```java
IssuedRefreshSession issue(UUID userAccountId, String deviceLabel)
```

`deviceLabel` is optional and is persisted unchanged. This application method has no Bean Validation annotations; structural label validation belongs at the later HTTP boundary.

Use constructor injection for:

- `UserAccountRepository`;
- `DeviceSessionRepository`;
- `SecureRefreshTokenGenerator`;
- `RefreshSessionProperties`;
- `Clock`;
- `IdGenerator`.

Within one transaction, the workflow must:

1. require a non-null `userAccountId` as an internal caller contract;
2. load that user once by primary key;
3. reject a missing or disabled user with the existing parameterless `IdentityErrorCode.INVALID_CREDENTIALS` and create no session;
4. after eligibility succeeds, generate one raw token/hash pair and one session UUID;
5. use that session UUID as both `id` and the initial `familyId`;
6. save and flush the new `DeviceSession` so persistence failures occur inside the transaction;
7. return only an immutable `IssuedRefreshSession` application record containing `sessionId`, raw `refreshToken`, and `expiresAt`.

Do not return an entity or the stored hash. Do not call the password-authentication service from this workflow. A later login orchestrator will compose credential verification, session issuance, and access-token issuance without weakening this workflow's own account-eligibility check.

## Explicit non-goals

- `POST /api/v1/auth/login`, request/response DTOs, controllers, cookies, or HTTP headers;
- access JWTs, JOSE/JWK keys, issuer/audience/type claims, bearer parsing, `JwtEncoder`, or `JwtDecoder`;
- Spring Security filter chains, authorization rules, principals, `Authentication`, or `SecurityContext`;
- refresh-token lookup, refresh endpoint, rotation, reuse detection, family revocation, logout, or session listing/deletion;
- `last_used_at`, `revoked_at`, `revoke_reason`, or `replaced_by_session_id` mutation;
- security-event writes, throttling, delay, lockout, CAPTCHA, or notifications;
- device fingerprinting, IP address, geolocation, user agent, platform metadata, or device-registration infrastructure;
- token delivery differences between web and mobile;
- password, registration, or credential-verification changes;
- a generic token framework, provider strategy, authorization-server dependency, or external identity work;
- schema, migration, constraint, index, or JPA mapping changes;
- frontend or financial/reference/ledger work;
- remote Git operations or Git history rewrites.

## Database changes

None.

Do not add or modify a Flyway migration, table, column, constraint, index, or JPA mapping annotation. The existing V1 `identity.device_session` shape and indexes are sufficient for an initial session generation.

## Application changes

Expected production-code surface:

```text
src/main/java/dev/canverse/stocks/identity/
├── application/
│   ├── IssuedRefreshSession.java
│   ├── GeneratedRefreshToken.java
│   └── RefreshSessionIssuanceService.java
├── configuration/
│   ├── RefreshSessionConfiguration.java
│   └── RefreshSessionProperties.java
├── domain/
│   └── DeviceSession.java                    # initial-session factory only
└── infrastructure/
    └── SecureRefreshTokenGenerator.java
```

`DeviceSessionRepository` requires no new query method. Keep the token generator concrete; do not add an interface solely for one implementation.

Expected test surface:

```text
src/test/java/dev/canverse/stocks/identity/
├── RefreshSessionIssuanceServiceTest.java
├── RefreshSessionPropertiesTest.java
└── SecureRefreshTokenGeneratorTest.java
```

Do not create reusable session test infrastructure for this one workflow.

## API contract

None.

This PR adds no route and does not serialize `IssuedRefreshSession`. The raw refresh token is an application result for a later delivery boundary, not yet a public HTTP contract.

## Business invariants

- A refresh-session generation belongs to exactly one eligible user and one token family.
- The first session UUID is also its family UUID; later rotations retain that family UUID while using new session UUIDs.
- A raw refresh token contains 256 bits of `SecureRandom` output and is returned only once.
- Only the deterministic SHA-256 Base64url hash is persisted; the raw token is never stored.
- Initial session creation sets no usage, revocation, replacement, token-delivery, or access-token state.
- Missing and disabled users fail closed through the same parameterless error and create no session.
- Every persisted timestamp comes from the injected clock and configured positive lifetime.

## Required tests

### Pure/configuration

Add focused no-database coverage that proves:

1. Spring Boot binds the absent lifetime to the 30-day default and rejects explicit zero/negative values; `ApplicationContextRunner` or the Boot `Binder` is appropriate for this configuration contract;
2. each generated raw token is unpadded URL-safe Base64 decoding to exactly 32 bytes;
3. the stored representation equals an independently calculated SHA-256 hash of the raw token's UTF-8 bytes, encoded as unpadded Base64url;
4. two generated tokens and hashes differ;
5. the raw token is not equal to its stored hash.

Do not assert token-generation timing or inspect/log token values in failure messages beyond the minimum assertion diagnostics.

### PostgreSQL/Testcontainers

Add focused service integration coverage using the real Spring context, migrated PostgreSQL, real `SecureRefreshTokenGenerator`, fixed `Clock`, deterministic `IdGenerator`, and an overridden test lifetime. Do not use H2 or mock the repositories.

1. **Eligible user creates one initial session**
   - register an enabled local user through the accepted registration service;
   - issue a session with a non-null device label;
   - assert the returned session ID and expiry;
   - assert the persisted user FK, identical initial family ID, unchanged label, exact fixed-clock timestamps, null usage/revocation/replacement fields, and configured expiry;
   - independently hash the returned raw token and assert it equals the persisted hash;
   - prove the raw token itself does not appear in any `device_session` text column.

2. **Optional label remains optional**
   - issue a session with `null` label and prove it persists as `null` without changing token/session invariants.

3. **Multiple device sessions use independent families**
   - issue two initial sessions for one eligible user;
   - prove distinct session IDs, family IDs, raw tokens, and stored hashes;
   - prove both rows remain active because they belong to different families.

4. **Missing and disabled accounts fail closed**
   - missing and fixture-disabled user IDs each throw `AppException` with exactly parameterless `INVALID_CREDENTIALS`;
   - neither case creates a `device_session` row;
   - no raw token, hash, user ID, or device label is exposed by the exception.

Use explicit cleanup and no test-level transaction that could hide the service transaction or flush.

### HTTP/security

The PostgreSQL service suite is the required security integration coverage for this application-only session change. No MockMvc/filter-chain test is required because no HTTP or Spring Security boundary is added.

All accepted registration, authentication, migration, mapping, error-contract, and HTTP tests must remain green.

## Acceptance criteria

1. One positive duration property with a 30-day default configures initial refresh-session lifetime; no unrelated auth properties are added.
2. One concrete generator uses `SecureRandom` to generate exactly 32 bytes and stores only an unpadded Base64url SHA-256 hash.
3. Exactly one DeviceSession initial factory sets its session ID as its family ID and leaves usage/revocation/replacement state null.
4. Exactly one new transactional service exposes `IssuedRefreshSession issue(UUID userAccountId, String deviceLabel)`.
5. Missing and disabled users throw the existing parameterless `INVALID_CREDENTIALS` and create no session.
6. Eligible users receive a session ID, raw token, and expiry while the database receives only the hash and existing session metadata.
7. Token generation occurs only after user eligibility succeeds, exactly once per successful issuance.
8. The configured lifetime and injected clock determine `createdAt` and `expiresAt`; no system-clock call is introduced.
9. No raw refresh token is persisted, logged, placed in an exception, or exposed by an entity/repository.
10. No dependency, migration, constraint, index, mapping annotation, repository query, controller, DTO, global-error, trace, password, registration, frontend, or financial change is made.
11. No access-token, HTTP login, refresh rotation/reuse, logout/revocation, filter-chain/principal, security-event, abuse-control, or external-provider work is added.
12. Required pure and PostgreSQL/security integration tests pass and prior tests remain green.
13. `./mvnw spotless:check`, the focused suite, `./mvnw test`, and `./mvnw verify` pass.
14. `git diff --check` passes and the complete diff contains only PR-010 files.
15. The Completion Record, `STATE.md`, and `progress-report.md` accurately describe implemented and deferred behavior; `CURRENT.md` still points to PR-010 throughout implementation/review.
16. The implementation and review agents perform no Git mutation.

## Documentation completion

Before this implementation unit is considered complete:

- fill in this specification's Completion Record;
- update `docs/implementation/STATE.md` with initial opaque refresh-session issuance and all still-deferred authentication/session behavior;
- update `docs/review/progress-report.md` with the production surface and verified test result;
- keep `docs/implementation/CURRENT.md` pointing to PR-010 until the supervisor commits it.

Do not mark HTTP login, access tokens, principals, refresh rotation, logout, session revocation/listing, security events, or abuse controls as implemented.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=RefreshSessionIssuanceServiceTest,RefreshSessionPropertiesTest,SecureRefreshTokenGeneratorTest,LocalPasswordAuthenticationServiceTest,LocalPasswordAuthenticationTimingTest,LocalAccountRegistrationServiceTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required by its parser.

## Completion record

Fill this before marking PR-010 complete.

### Starting commit

- `82f6a34` (`reconcile automated batch state`).

### Accepted commit

- `c3c9fd6` (`issue initial opaque refresh sessions`).

### Implemented

- Added immutable `RefreshSessionProperties` with the `stocks.identity.refresh-session` prefix, a constructor-bound `@DefaultValue("30d")` lifetime, positive-duration enforcement, and explicit identity configuration enablement.
- Added a concrete `SecureRefreshTokenGenerator` that creates 32 random bytes per token, returns unpadded Base64url raw tokens, and derives unpadded Base64url SHA-256 hashes using a fresh digest per operation.
- Added immutable generated-token and issued-session application records.
- Added the `DeviceSession.initialGeneration(...)` factory with initial family/session identity, injected timestamps, unchanged optional label, and null usage/revocation/replacement state.
- Added transactional `RefreshSessionIssuanceService.issue(...)` with one eligible-user lookup, missing/disabled fail-closed behavior, injected ID/clock/lifetime use, `saveAndFlush`, hash-only persistence, and raw-token-once return.
- Added pure configuration/token tests and four PostgreSQL/Testcontainers issuance cases covering all required success, optional-label, independent-family, eligibility, and raw-token non-persistence behavior.

### Deviations from specification

- None.

### New decisions

- None.

### Tests executed

- `./mvnw.cmd spotless:check` → BUILD SUCCESS.
- `./mvnw.cmd "-Dtest=RefreshSessionIssuanceServiceTest,RefreshSessionPropertiesTest,SecureRefreshTokenGeneratorTest,LocalPasswordAuthenticationServiceTest,LocalPasswordAuthenticationTimingTest,LocalAccountRegistrationServiceTest" test` → 16 tests, 0 failures, 0 errors.
- `./mvnw.cmd test` → 67 tests, 0 failures, 0 errors.
- `./mvnw.cmd verify` → BUILD SUCCESS, including Spotless and all 67 tests.
- `git diff --check` → PASSED.

### Independent review

- Passed with no findings; PR-010 is ready for the supervisor's local commit.

### Follow-up work

- HTTP login and access-token issuance, refresh lookup/rotation/reuse detection, logout/revocation/session listing, Spring Security principals/filter chains, security events, abuse controls, and all later authentication work remain deferred to separately scoped units.
