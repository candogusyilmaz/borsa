# PR-005 — Atomic local account registration

Status: **COMPLETE**

## Goal

Add one application-layer workflow that creates a local user account and its `LOCAL` authentication identity atomically, using application-generated IDs/timestamps and a one-way, self-describing Spring Security password hash.

This PR establishes the first JPA write semantics for `identity.user_account` and `identity.auth_identity`. It deliberately stops before HTTP registration, login, access tokens, refresh sessions, or a security filter chain.

## Source documents

Read and follow:

- `AGENTS.md`
- `docs/implementation/STATE.md`
- `docs/implementation/PR-004-v1-mapping-package-alignment.md`
- `docs/review/backend-master-plan.md`
  - per-slice implementation loop;
  - pragmatic modular-monolith layout;
  - JPA annotation and Flyway ownership policy;
  - R1 item 5: local email/password registration and password hashing.
- `docs/review/backend-audit.md`
  - SEC-005: do not restore the legacy consumer-email-domain allowlist;
  - SEC-006: errors must remain semantic and safe.
- `docs/review/mobile-api-readiness.md`
  - authentication abuse/recovery guidance, which remains deferred here except for secure password storage.
- `docs/engineering/coding-standards.md`
  - Spring transaction boundaries;
  - capability packages;
  - JPA entity mutation rules;
  - persistence, error, security, and testing standards.
- Spring Security 7 password-storage documentation: use `PasswordEncoderFactories.createDelegatingPasswordEncoder()` so stored hashes carry an `{id}` prefix and can evolve without rewriting existing credentials.

`docs/review/accounting-contract.md` is not required because this PR introduces no financial behavior.

## Starting state

PR-004 has been reviewed, accepted, and committed as `eed342e`.

The repository currently has:

- Flyway V1 with `identity.user_account` and `identity.auth_identity` constraints already authoritative;
- `UserAccount` and `AuthIdentity` mapping-only entities in `identity.domain`;
- their `JpaRepository` interfaces in `identity.infrastructure`;
- no entity factory/mutation methods and no JPA write workflow;
- application-provided `Clock` and `IdGenerator` abstractions;
- `spring-boot-starter-validation`, but no Spring Security dependency;
- no registration service, controller, password encoder, security filter chain, login, or token behavior;
- a clean Java 25 build with Lombok explicitly registered as an annotation processor.

Before editing, inspect the actual repository. Do not perform Git mutations.

## Scope

### 1. Add only the password-storage dependency

Add this production dependency without an explicit version:

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

Use the Spring Boot-managed version.

Do not add `spring-boot-starter-security`, `spring-security-web`, OAuth2, resource-server, JOSE/JWT, or another password-hashing library.

### 2. Configure one password encoder

Create:

```text
dev.canverse.stocks.identity.configuration.PasswordEncodingConfiguration
```

It provides exactly one `PasswordEncoder` bean created by:

```java
PasswordEncoderFactories.createDelegatingPasswordEncoder()
```

Do not hardcode a raw bcrypt-only encoder, hash strength, pepper, or custom algorithm catalogue in this PR. The stored value must use Spring Security's self-describing `{id}encodedPassword` format.

### 3. Add narrow entity creation paths

Add one public static factory to `UserAccount` for local registration. It accepts:

- application-generated user UUID;
- display email;
- normalized email;
- one registration `Instant` used for both `createdAt` and `updatedAt`.

Add one public static factory to `AuthIdentity` for a local identity. It accepts:

- application-generated auth-identity UUID;
- the managed `UserAccount` returned by `UserAccountRepository.save(...)`;
- normalized email as provider subject;
- encoded password hash;
- the same registration `Instant` used for both `createdAt` and `updatedAt`.

The `AuthIdentity` factory sets provider exactly to `LOCAL`. Keep that code inside `AuthIdentity`; do not add a provider enum/catalogue or expose a caller-controlled provider for this workflow.

Both factories:

- reject null required values;
- preserve the protected JPA no-argument constructor;
- do not add setters, builders, public constructors, update methods, or unrelated lifecycle behavior;
- receive only an encoded password, never the raw password.

Do not add JPA DDL annotations or change existing mappings.

### 4. Add the one required repository query

Add exactly this derived query to `UserAccountRepository`:

```java
boolean existsByEmailNormalized(String emailNormalized);
```

It is an early duplicate check for a friendly application result. The V1 unique constraint remains the concurrency authority.

Do not add a query to `AuthIdentityRepository` and do not add custom repository implementations or locking queries.

### 5. Implement one transactional registration service

Create:

```text
dev.canverse.stocks.identity.application.LocalAccountRegistrationService
```

Use one concrete Spring service; do not add an interface, use-case wrapper, command handler, factory class, mapper, port, or adapter.

Expose exactly one public workflow method:

```java
UUID register(String email, String rawPassword)
```

The service uses constructor injection for:

- `UserAccountRepository`;
- `AuthIdentityRepository`;
- `PasswordEncoder`;
- `Clock`;
- `IdGenerator`.

Annotate the class for Spring method validation and put the transaction boundary on `register(...)`.

Method input rules:

- `email` is not blank;
- `email` is a Jakarta Validation email;
- `email` is at most 320 characters;
- `email` has no leading or trailing whitespace;
- there is no email-domain allowlist;
- `rawPassword` is not blank and is 12–128 characters inclusive;
- the password is not trimmed, case-folded, normalized, logged, returned, or placed in an exception message;
- no password composition rule, breached-password lookup, or configurable password-policy framework is introduced.

Use these method-parameter Jakarta Validation constraints with `@Validated`:

```java
@NotBlank @Email @Size(max = 320) @Pattern(regexp = "\\S(?:.*\\S)?") String email
@NotBlank @Size(min = 12, max = 128) String rawPassword
```

Do not manually recreate an email parser. A validation failure must occur before any repository write.

Registration workflow, in order:

1. Calculate `emailNormalized` as `email.toLowerCase(Locale.ROOT)`. Because surrounding whitespace is invalid, do not silently trim it.
2. Encode the password before the first repository access.
3. If `existsByEmailNormalized(emailNormalized)` is true, throw `EmailAlreadyRegisteredException`.
4. Read one `registrationTime` from the injected `Clock`.
5. Obtain two IDs from `IdGenerator`: one for `UserAccount`, then one for `AuthIdentity`.
6. Create and save the user account.
7. Use the managed `UserAccount` instance returned by `UserAccountRepository.save(...)` when creating the auth identity. This matters because assigned UUID entities may be merged by Spring Data JPA.
8. Save and flush the auth identity so database uniqueness failures occur inside the service method.
9. Return the saved user account UUID only.

The user account and auth identity must commit together or roll back together.

### 6. Translate only duplicate-local-email constraint failures

Create:

```text
dev.canverse.stocks.identity.application.EmailAlreadyRegisteredException
```

It is an application exception with:

- stable code `identity.email_already_registered`;
- a safe message that contains neither the submitted email nor the password;
- an optional retained cause for diagnostics.

The service may use the existence query for the normal duplicate path, but it must also translate the race/constraint path.

When a `DataIntegrityViolationException` is raised during the flush, inspect its cause chain for Hibernate's constraint violation and translate only these named V1 constraints:

- `uq_user_account_email_normalized`;
- `uq_auth_identity_provider_subject`.

Do not parse localized exception-message text. Do not convert primary-key collisions, foreign-key failures, null/check failures, or an unknown integrity violation into “email already registered”; rethrow the original exception in those cases.

### 7. Update implementation documentation after verification

Before claiming completion:

- fill this specification's Completion Record;
- update `docs/implementation/STATE.md` with the implemented registration capability and the two-table write semantics;
- update `docs/review/progress-report.md` if implementation status materially changes;
- keep `docs/implementation/CURRENT.md` pointing to PR-005 until the user reviews and accepts the diff.

## Explicit non-goals

- `POST /api/v1/auth/register` or any other HTTP endpoint;
- controllers, web DTOs, API validation responses, or problem-detail mappings;
- `spring-boot-starter-security`, `SecurityFilterChain`, request authorization, CSRF, CORS, form login, or HTTP Basic;
- password authentication/login or `AuthenticationManager`;
- access tokens, JWTs, signing keys, key generation, cookies, or bearer-token parsing;
- refresh tokens, device-session creation, rotation, reuse detection, logout, or revocation;
- current-principal/owner helpers or `/api/v1/me`;
- Google/OIDC/OAuth or any external provider workflow;
- email verification, password reset/change, breached-password checks, rate limiting, progressive delay, lockout, or security-event publishing;
- roles, permissions, households, or authorization;
- a provider enum/catalogue or generic authentication-provider abstraction;
- schema changes, a new Flyway migration, or changes to V1 constraints/indexes;
- JPA write semantics for `DeviceSession`, `SecurityEvent`, or `Job`;
- frontend changes;
- financial/reference/ledger work;
- Git mutations.

## Database changes

None.

Do not modify or add a Flyway migration. In particular, do not modify:

```text
src/main/resources/db/migration/V1__foundation.sql
```

The existing named unique constraints remain authoritative.

## Application changes

Expected production changes:

```text
pom.xml

src/main/java/dev/canverse/stocks/identity/
├── application/
│   ├── LocalAccountRegistrationService.java
│   └── EmailAlreadyRegisteredException.java
├── configuration/
│   └── PasswordEncodingConfiguration.java
├── domain/
│   ├── UserAccount.java                 # registration factory only
│   └── AuthIdentity.java                # LOCAL factory only
└── infrastructure/
    └── UserAccountRepository.java       # existsByEmailNormalized only
```

Expected test addition:

```text
src/test/java/dev/canverse/stocks/identity/LocalAccountRegistrationServiceTest.java
```

`AuthIdentityRepository`, `DeviceSession`, platform code, application configuration, and frontend source should not change.

## API contract

None.

No endpoint or problem-detail mapping is added. The stable application exception code is introduced now so a later HTTP boundary can map it without changing registration semantics.

## Business invariants

1. One successful registration creates exactly one `UserAccount` and one `LOCAL` `AuthIdentity` in one transaction.
2. Display email preserves the validated caller-supplied casing; normalized email and local provider subject use `Locale.ROOT` lowercase.
3. Local provider subject equals the normalized account email.
4. User and auth-identity IDs come from `IdGenerator`; neither uses a database UUID default.
5. Both rows use the same injected-clock instant for `createdAt` and `updatedAt`.
6. Raw passwords never enter an entity, database column, result, log, or exception message.
7. Stored passwords use a one-way Spring Security `{id}`-prefixed hash and can be verified through `PasswordEncoder.matches(...)`.
8. Duplicate normalized email is rejected semantically, while PostgreSQL uniqueness remains the final race/concurrency barrier.
9. A failure to persist the auth identity rolls back the newly inserted user account.
10. Integrity failures unrelated to the two duplicate-local-email constraints are not mislabeled.

## Required tests

### Pure/domain

None required. The meaningful behavior crosses validation, password encoding, JPA, PostgreSQL constraints, and a transaction; do not replace that evidence with repository mocks.

### PostgreSQL/Testcontainers

Add `LocalAccountRegistrationServiceTest` using the existing `@SpringBootTest(webEnvironment = NONE)` and PostgreSQL Testcontainers pattern.

Use a fixed test `Clock`. Do not wrap the test class or methods in a test-managed transaction: the service transaction must own commit/rollback so the rollback case is observable. Database cleanup/setup must use deliberate child-before-parent deletion; tests must not depend on execution order.

Required cases:

1. `registrationCreatesUserAndLocalIdentityAtomically`
   - register a mixed-case valid email and a valid raw password;
   - assert exactly one user and one auth identity exist;
   - assert returned ID equals the persisted user ID and differs from the auth-identity ID;
   - assert display/normalized email, `LOCAL` provider, and provider subject;
   - assert both rows have the fixed `createdAt`/`updatedAt` instant;
   - assert the persisted hash is not the raw password, has an `{id}` prefix, and matches only the correct raw password through the configured `PasswordEncoder`.

2. `duplicateEmailIsRejectedCaseInsensitively`
   - register once, then retry with different email casing;
   - assert `EmailAlreadyRegisteredException` and its stable code;
   - assert the first account/identity remain unchanged and no additional row exists.

3. `authIdentityConstraintFailureRollsBackNewUser`
   - establish a valid fixture whose `LOCAL` provider subject collides with the requested normalized email while its owning user's account email is different, so the early user-email existence check is false;
   - register the colliding email;
   - assert the named auth-identity uniqueness failure becomes `EmailAlreadyRegisteredException`;
   - assert the attempted new `user_account` row was rolled back and the fixture remains intact.

4. `invalidRegistrationInputWritesNothing`
   - cover malformed email, leading/trailing email whitespace, blank/short/overlong password, and an overlong email;
   - assert method validation rejects each case;
   - assert neither identity table gains a row.

All existing PostgreSQL tests remain green. Tests must use PostgreSQL, not H2.

### HTTP/security

No HTTP test is required because this PR has no endpoint or filter chain.

The PostgreSQL service test must nevertheless prove the security-sensitive storage property: only a delegating encoded hash is persisted and the raw password is not.

## Acceptance criteria

PR-005 is ready for human review only when:

1. `spring-security-crypto` is the only new dependency and has no explicit version.
2. Exactly one `PasswordEncoder` bean uses `PasswordEncoderFactories.createDelegatingPasswordEncoder()`.
3. No Spring Security web/filter/authentication configuration is introduced.
4. `UserAccount` and `AuthIdentity` gain only the specified registration factories; their accepted mappings remain unchanged.
5. `UserAccountRepository` adds only `existsByEmailNormalized`; no other repository contract changes.
6. `LocalAccountRegistrationService.register(...)` has method validation and one application-service transaction boundary.
7. Email normalization, password constraints, ID generation, and timestamp behavior match this specification exactly.
8. The raw password cannot reach an entity, persisted value, return value, log, or exception message.
9. A successful registration persists exactly one user and one local identity atomically.
10. Case-insensitive duplicates produce `identity.email_already_registered` without adding rows.
11. Named database duplicate constraints are translated, unknown integrity violations are rethrown, and downstream auth-identity failure rolls back the user row.
12. `V1__foundation.sql` and all migration/constraint tests are unchanged.
13. No controller, endpoint, token, session, security event, external identity, frontend, or later-roadmap behavior is added.
14. The four required `LocalAccountRegistrationServiceTest` cases pass against PostgreSQL.
15. All pre-existing tests remain green.
16. `./mvnw spotless:check`, `./mvnw test`, and `./mvnw verify` pass without command-line compiler overrides.
17. `git diff --check` passes.
18. The Completion Record is accurate and `CURRENT.md` still points to PR-005.

## Documentation completion

Before this implementation unit is considered complete:

- fill in this specification's Completion Record;
- update `docs/implementation/STATE.md` with the implemented capability and newly established write semantics;
- update `docs/review/progress-report.md` if project-level progress changed.

Do not put detailed implementation history into `STATE.md`. Keep it as a concise handoff of the current repository state.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=LocalAccountRegistrationServiceTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

## Completion record

Fill this before marking the PR complete.

### Implemented

- Added the Boot-managed `spring-security-crypto` dependency and one delegating `PasswordEncoder` bean.
- Added null-safe `UserAccount.register(...)` and `AuthIdentity.local(...)` factories without changing accepted JPA mappings.
- Added `UserAccountRepository.existsByEmailNormalized(...)` and the validated, transactional local registration service.
- Added fixed-clock PostgreSQL/Testcontainers coverage for successful registration, case-insensitive duplicates, auth-identity rollback, and invalid input.
- Added safe `identity.email_already_registered` exception handling for the two named local-email uniqueness constraints.

### Deviations from specification

- None.

### New decisions

- None.

### Tests executed

- `./mvnw spotless:check` (PowerShell: `mvnw.cmd spotless:check`) → BUILD SUCCESS.
- `./mvnw -Dtest=LocalAccountRegistrationServiceTest test` (PowerShell equivalent) → 4 tests, 0 failures, 0 errors.
- `./mvnw test` (PowerShell equivalent) → 33 tests, 0 failures, 0 errors.
- `./mvnw verify` (PowerShell equivalent) → BUILD SUCCESS, including Spotless and all 33 tests.
- `git diff --check` → PASSED.

### Follow-up work

- Keep `CURRENT.md` on PR-005 pending user review; HTTP registration, login, tokens, sessions, and broader security behavior remain deferred to later specifications.
