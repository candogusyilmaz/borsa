# PR-003 — Identity and platform JPA entity mappings

Status: **ACTIVE**

## Goal

Add read-capable JPA entity mappings and minimal Spring Data repositories for the five V1 foundation tables:

- `identity.user_account`
- `identity.auth_identity`
- `identity.device_session`
- `platform.security_event`
- `platform.job`

After this PR:

- Hibernate `ddl-auto: validate` actively validates the five mappings against `V1__foundation.sql`;
- Spring Data JPA can load each mapped table through typed repositories;
- foreign-key associations can be resolved correctly;
- the application has a typed persistence mapping foundation for later authentication and platform work.

This PR deliberately does **not** establish JPA write semantics, entity mutation APIs, authentication behavior, or service-layer rules. Those belong to the first concrete use case that needs them.

## Source documents

Read and follow:

- `AGENTS.md`
- `docs/engineering/coding-standards.md`
  - Java style
  - package structure
  - JPA/Hibernate entity standards
  - Flyway/PostgreSQL ownership rules
- `docs/review/backend-master-plan.md`
  - R1 entity-mapping step
  - JPA annotation policy
  - modular-monolith package layout
- `docs/implementation/STATE.md`
- `docs/implementation/PR-002-v1-foundation-database.md`

`docs/review/accounting-contract.md` is not required for this PR because no financial behavior is introduced.

## Starting state

PR-002 has been reviewed and accepted.

The repository currently has:

- `V1__foundation.sql`;
- eight application schemas: `identity`, `reference`, `ledger`, `data`, `money`, `analysis`, `asset`, and `platform`;
- five V1 foundation tables: `identity.user_account`, `identity.auth_identity`, `identity.device_session`, `platform.security_event`, and `platform.job`;
- Flyway applying V1 successfully against fresh PostgreSQL;
- Hibernate configured with `ddl-auto: validate`;
- no JPA entities;
- no Spring Data repository interfaces;
- no Spring Security dependency;
- no Lombok dependency;
- `src/main/web` unchanged by the rewrite.

Before editing, inspect the actual repository. Do not perform Git mutations.

## Scope

### 1. Map the three identity tables

Create package:

```text
dev.canverse.stocks.identity
```

Create exactly these entities.

#### `UserAccount`

Maps `identity.user_account`.

| Java field        | Column             | Java type | Nullable |
| ----------------- | ------------------ | --------: | -------- |
| `id`              | `id`               |    `UUID` | no       |
| `email`           | `email`            |  `String` | no       |
| `emailNormalized` | `email_normalized` |  `String` | no       |
| `disabledAt`      | `disabled_at`      | `Instant` | yes      |
| `createdAt`       | `created_at`       | `Instant` | no       |
| `updatedAt`       | `updated_at`       | `Instant` | no       |

#### `AuthIdentity`

Maps `identity.auth_identity`.

| Java field        | Column             |     Java type | Nullable |
| ----------------- | ------------------ | ------------: | -------- |
| `id`              | `id`               |        `UUID` | no       |
| `userAccount`     | `user_account_id`  | `UserAccount` | no       |
| `provider`        | `provider`         |      `String` | no       |
| `providerSubject` | `provider_subject` |      `String` | no       |
| `passwordHash`    | `password_hash`    |      `String` | yes      |
| `createdAt`       | `created_at`       |     `Instant` | no       |
| `updatedAt`       | `updated_at`       |     `Instant` | no       |

Mapping:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_account_id")
```

Do not add a reverse collection to `UserAccount`.

#### `DeviceSession`

Maps `identity.device_session`.

| Java field            | Column                   |     Java type | Nullable |
| --------------------- | ------------------------ | ------------: | -------- |
| `id`                  | `id`                     |        `UUID` | no       |
| `userAccount`         | `user_account_id`        | `UserAccount` | no       |
| `familyId`            | `family_id`              |        `UUID` | no       |
| `refreshTokenHash`    | `refresh_token_hash`     |      `String` | no       |
| `deviceLabel`         | `device_label`           |      `String` | yes      |
| `createdAt`           | `created_at`             |     `Instant` | no       |
| `lastUsedAt`          | `last_used_at`           |     `Instant` | yes      |
| `expiresAt`           | `expires_at`             |     `Instant` | no       |
| `revokedAt`           | `revoked_at`             |     `Instant` | yes      |
| `revokeReason`        | `revoke_reason`          |      `String` | yes      |
| `replacedBySessionId` | `replaced_by_session_id` |        `UUID` | yes      |

Mapping:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_account_id")
```

`replacedBySessionId` remains a plain `UUID`.

Do not map it as a self-referencing `@ManyToOne` in this PR. A later session-management implementation unit may introduce different navigation if a real use case requires it.

### 2. Map the two platform tables

Use the existing package:

```text
dev.canverse.stocks.platform
```

Do not create speculative platform sub-packages for these two mappings.

#### `SecurityEvent`

Maps `platform.security_event`.

| Java field    | Column            |     Java type | Nullable |
| ------------- | ----------------- | ------------: | -------- |
| `id`          | `id`              |        `UUID` | no       |
| `userAccount` | `user_account_id` | `UserAccount` | yes      |
| `eventType`   | `event_type`      |      `String` | no       |
| `occurredAt`  | `occurred_at`     |     `Instant` | no       |
| `details`     | `details`         |      `String` | no       |

`userAccount` uses:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_account_id")
```

The PostgreSQL column `details` is `jsonb`. Map it with:

```java
@JdbcTypeCode(SqlTypes.JSON)
```

Keep the Java representation as `String` for this PR.

This PR proves JSON loading only. It does **not** establish the final application-level JSON representation or JPA JSON write semantics. The first implementation unit that writes `SecurityEvent` through JPA must add a PostgreSQL write/read round-trip test.

#### `Job`

Maps `platform.job`.

| Java field         | Column                  |     Java type | Nullable |
| ------------------ | ----------------------- | ------------: | -------- |
| `id`               | `id`                    |        `UUID` | no       |
| `ownerUserAccount` | `owner_user_account_id` | `UserAccount` | yes      |
| `jobType`          | `job_type`              |      `String` | no       |
| `status`           | `status`                |      `String` | no       |
| `payload`          | `payload`               |      `String` | no       |
| `availableAt`      | `available_at`          |     `Instant` | no       |
| `claimedBy`        | `claimed_by`            |      `String` | yes      |
| `claimToken`       | `claim_token`           |        `UUID` | yes      |
| `claimedAt`        | `claimed_at`            |     `Instant` | yes      |
| `heartbeatAt`      | `heartbeat_at`          |     `Instant` | yes      |
| `attemptCount`     | `attempt_count`         |         `int` | no       |
| `maxAttempts`      | `max_attempts`          |         `int` | no       |
| `completedAt`      | `completed_at`          |     `Instant` | yes      |
| `lastError`        | `last_error`            |      `String` | yes      |
| `createdAt`        | `created_at`            |     `Instant` | no       |
| `updatedAt`        | `updated_at`            |     `Instant` | no       |

`ownerUserAccount` uses:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "owner_user_account_id")
```

Keep `status` as `String`. Do not add a Java enum in this PR.

The PostgreSQL column `payload` is `jsonb`. Map it with:

```java
@JdbcTypeCode(SqlTypes.JSON)
```

Keep the Java representation as `String` for this PR.

This PR proves JSON loading only. The later durable-job implementation unit that first writes jobs through JPA must define and test write semantics.

### 3. Entity implementation rules

Apply `docs/engineering/coding-standards.md`.

Every entity must have:

- `@Entity`;
- `@Table(name = "...", schema = "...")`;
- field access;
- `@Id` on the UUID identifier;
- no `@GeneratedValue`;
- a protected no-argument constructor;
- public getters;
- `FetchType.LAZY` on every `@ManyToOne`;
- `optional = false` on mandatory associations;
- explicit `@JoinColumn(name = "...")` on associations;
- `@JdbcTypeCode(SqlTypes.JSON)` on the two `jsonb` mappings.

Scalar column names may rely on the configured Spring/Hibernate snake-case physical naming strategy when it unambiguously maps the Java field to the V1 column.

Do not add annotations merely to duplicate Flyway DDL.

Do **not** add:

- public setters;
- public all-arguments constructors;
- builder APIs;
- factory methods;
- business methods;
- entity validation logic;
- Spring Data auditing;
- `@GeneratedValue`;
- `@Version`;
- `@OneToMany`;
- `@ManyToMany`;
- bidirectional associations;
- Hibernate schema constraints;
- `columnDefinition`;
- `@Table(indexes = ...)`;
- `@Table(uniqueConstraints = ...)`;
- DDL-oriented `@Check`;
- Lombok.

This PR is intentionally read-oriented. Do not design entity mutation APIs just to make future features easier.

### 4. Create exactly five repositories

Create:

```text
dev.canverse.stocks.identity/
  UserAccountRepository
  AuthIdentityRepository
  DeviceSessionRepository

dev.canverse.stocks.platform/
  SecurityEventRepository
  JobRepository
```

Every repository extends exactly:

```java
JpaRepository<EntityType, UUID>
```

Do not add query methods, `@Query`, custom implementations, fragments, shared repository base classes, or explicit `@Repository` annotations.

Repository queries should be introduced by the implementation unit that actually needs them.

### 5. Preserve the existing migration and infrastructure tests

Do not modify:

- `V1__foundation.sql`;
- `FoundationMigrationTest`;
- `InfrastructureTest`.

Avoid modifying `ContextSmokeTest` unless the existing test genuinely fails because of the new mappings and the fix is directly related to this PR.

The existing full-context Testcontainers startup already provides the schema-validation gate:

```text
Flyway migrates PostgreSQL
        ↓
Hibernate boots
        ↓
ddl-auto=validate checks mappings
        ↓
Spring context starts
```

A wrong table, schema, column, association, or JDBC type must fail startup rather than being hidden.

### 6. Add one focused `EntityMappingTest`

Create:

```text
src/test/java/dev/canverse/stocks/EntityMappingTest.java
```

Use:

- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)`;
- Testcontainers PostgreSQL using the existing repository pattern;
- `@Transactional` test methods or class-level transaction;
- `JdbcTemplate` to establish valid database fixtures;
- the five repositories to load the fixtures;
- `EntityManager.clear()` before repository reads so the assertion cannot be satisfied from a first-level persistence context.

Use fixed deterministic timestamps. Do **not** use `OffsetDateTime.now()`.

Example values:

```text
2026-08-08T09:00:00Z
2026-08-08T10:00:00Z
2026-08-08T11:00:00Z
```

For JDBC parameters targeting PostgreSQL `timestamptz`, use `OffsetDateTime` as already established by PR-002 test infrastructure.

The JPA entity fields remain `Instant`.

Required cases:

#### `userAccountCanBeLoaded`

Insert a valid `identity.user_account` and load using `UserAccountRepository.findById(...)`.

Assert at least:

- ID;
- email;
- normalized email;
- `disabledAt == null`;
- persisted timestamps map to the expected `Instant` values.

#### `authIdentityCanBeLoadedWithUserAccountReference`

Insert one user and one auth identity referencing that user.

Load through `AuthIdentityRepository` and assert:

- ID;
- provider;
- provider subject;
- password hash if supplied by the fixture;
- `authIdentity.getUserAccount().getId()` equals the expected user ID.

Do not require bidirectional navigation.

#### `deviceSessionCanBeLoadedWithUserAccountReference`

Insert one user and one valid device session using deterministic `created_at` and `expires_at` values satisfying the V1 constraint.

Load through `DeviceSessionRepository` and assert:

- family ID;
- refresh-token hash;
- created/expires timestamps;
- nullable lifecycle fields remain null where appropriate;
- `deviceSession.getUserAccount().getId()` equals the expected user ID;
- `replacedBySessionId` is loaded as a plain UUID if a fixture value is supplied.

#### `anonymousSecurityEventCanBeLoaded`

Insert a `platform.security_event` with `user_account_id = null` and a small valid JSON document for `details`.

Load through `SecurityEventRepository` and assert:

- event type;
- occurred-at timestamp;
- `userAccount == null`;
- JSON content is present and represents the inserted document.

Exact JSON whitespace or formatting must not be asserted.

#### `jobCanBeLoaded`

Insert a valid READY job using fixed timestamps and valid JSON payload.

Load through `JobRepository` and assert at least:

- job type;
- status equals `READY`;
- attempt count;
- max attempts;
- available-at timestamp;
- payload is present and represents the inserted JSON document;
- optional claim/completion fields are null for the READY fixture.

Exact JSON whitespace or formatting must not be asserted.

### 7. Update implementation documentation

Before claiming the PR complete:

- fill this PR's Completion Record;
- update `docs/implementation/STATE.md` with the implemented mappings and repositories;
- update `docs/review/progress-report.md` if required by `AGENTS.md`;
- keep `docs/implementation/CURRENT.md` pointing to PR-003.

Do not create the next PR specification.

Do not advance `CURRENT.md`.

The user reviews and accepts this implementation first.

## Explicit non-goals

This PR does **not** implement:

- JPA entity creation/write workflows;
- JPA JSON write semantics;
- application/domain constructors;
- entity business methods;
- repository query methods;
- registration;
- login;
- logout;
- refresh-token rotation;
- session revocation behavior;
- Spring Security;
- `SecurityFilterChain`;
- JWT creation or validation;
- password hashing;
- Google/OIDC/OAuth integration;
- principal/current-user helpers;
- authentication throttling;
- security-event publishing;
- durable job claiming/retry/heartbeat behavior;
- schedulers/workers;
- optimistic locking;
- application services;
- HTTP controllers;
- DTOs;
- problem codes;
- reference data;
- financial-domain code;
- a new Flyway migration;
- frontend changes;
- Git mutations.

## Database changes

None.

Do not modify:

```text
src/main/resources/db/migration/V1__foundation.sql
```

Do not add a migration.

## Application changes

Expected production files:

```text
src/main/java/dev/canverse/stocks/
├── identity/
│   ├── UserAccount.java
│   ├── UserAccountRepository.java
│   ├── AuthIdentity.java
│   ├── AuthIdentityRepository.java
│   ├── DeviceSession.java
│   └── DeviceSessionRepository.java
└── platform/
    ├── SecurityEvent.java
    ├── SecurityEventRepository.java
    ├── Job.java
    └── JobRepository.java
```

Expected test file:

```text
src/test/java/dev/canverse/stocks/EntityMappingTest.java
```

No Maven dependency is expected.

No `application.yml` change is expected.

No frontend source change is expected.

## API contract

None.

No HTTP endpoint is added or changed.

## Business invariants

No new business invariant is introduced.

`V1__foundation.sql` remains the database integrity authority.

`FoundationMigrationTest` remains responsible for exhaustive database constraint/cascade behavior.

PR-003 is responsible only for:

- Hibernate schema compatibility;
- scalar column mapping;
- FK association mapping;
- `jsonb` read mapping;
- repository-based loading.

## Required tests

### Pure/domain

None.

### PostgreSQL/Testcontainers

All existing PostgreSQL/infrastructure tests must remain green.

Add `EntityMappingTest` containing the five required mapping/load tests.

The tests must use PostgreSQL, not H2 or another in-memory database.

### HTTP/security

None.

## Acceptance criteria

PR-003 is ready for human review only when:

1. Exactly five new JPA entities exist: three in `dev.canverse.stocks.identity` and two in `dev.canverse.stocks.platform`.
2. Exactly five new Spring Data repositories exist.
3. Every repository extends `JpaRepository<T, UUID>`.
4. No repository query method is added.
5. Every entity maps the correct V1 schema and table.
6. Every mandatory association uses `@ManyToOne(fetch = FetchType.LAZY, optional = false)`.
7. Every nullable association uses lazy `@ManyToOne` without falsely declaring it mandatory.
8. `DeviceSession.replacedBySessionId` remains a plain UUID.
9. `SecurityEvent.details` and `Job.payload` use Hibernate JSON JDBC mapping.
10. No public setter, business method, builder, or public all-arguments constructor is introduced.
11. No Hibernate/JPA DDL annotation duplicates Flyway-owned constraints/indexes/defaults.
12. `V1__foundation.sql` is unchanged.
13. No Maven dependency is added.
14. No service, controller, security, auth, worker, DTO, or domain-exception code is added.
15. `src/main/web` has no source changes.
16. Hibernate `ddl-auto: validate` succeeds against fresh V1 PostgreSQL.
17. The five `EntityMappingTest` cases pass.
18. All pre-existing tests remain green.
19. `./mvnw spotless:check` passes.
20. `./mvnw test` passes.
21. `./mvnw verify` passes.
22. `git diff --check` passes.
23. The Completion Record is accurate.
24. `CURRENT.md` still points to PR-003.

## Verification commands

```bash
./mvnw spotless:check
./mvnw test
./mvnw verify

git status --short
git diff --check
```

## Completion record

### Starting commit

- `dee5b02` — pr-002 (HEAD at implementation start)

### Implemented

- `dev.canverse.stocks.identity.UserAccount` — maps `identity.user_account`
- `dev.canverse.stocks.identity.UserAccountRepository` — extends `JpaRepository<UserAccount, UUID>`
- `dev.canverse.stocks.identity.AuthIdentity` — maps `identity.auth_identity`; `@ManyToOne(fetch = FetchType.LAZY, optional = false)` to `UserAccount`
- `dev.canverse.stocks.identity.AuthIdentityRepository` — extends `JpaRepository<AuthIdentity, UUID>`
- `dev.canverse.stocks.identity.DeviceSession` — maps `identity.device_session`; `@ManyToOne(fetch = FetchType.LAZY, optional = false)` to `UserAccount`; `replacedBySessionId` remains plain `UUID`
- `dev.canverse.stocks.identity.DeviceSessionRepository` — extends `JpaRepository<DeviceSession, UUID>`
- `dev.canverse.stocks.platform.SecurityEvent` — maps `platform.security_event`; nullable `@ManyToOne(fetch = FetchType.LAZY)` to `UserAccount`; `details` mapped with `@JdbcTypeCode(SqlTypes.JSON)`
- `dev.canverse.stocks.platform.SecurityEventRepository` — extends `JpaRepository<SecurityEvent, UUID>`
- `dev.canverse.stocks.platform.Job` — maps `platform.job`; nullable `@ManyToOne(fetch = FetchType.LAZY)` to `UserAccount`; `payload` mapped with `@JdbcTypeCode(SqlTypes.JSON)`; `attemptCount`/`maxAttempts` as primitive `int`
- `dev.canverse.stocks.platform.JobRepository` — extends `JpaRepository<Job, UUID>`
- `src/test/java/dev/canverse/stocks/EntityMappingTest.java` — five repository-loading tests with Testcontainers PostgreSQL and `EntityManager.clear()` between insert and read

All entities use field access, a protected no-arg constructor, and public getters only. No public setters, builders, or business methods were introduced. No DDL annotations duplicating Flyway-owned constraints were added. Scalar column names rely on the configured snake-case physical naming strategy.

### Deviations from specification

- None.

### New decisions

Established during review; effective from the next PR:

1. **Lombok is now a project dependency.** Entities use `@Getter` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)`. Spring components use `@RequiredArgsConstructor`. `@Setter`, `@Data`, `@Builder`, and `@AllArgsConstructor` are restricted per `coding-standards.md` §5.
2. **Each capability module uses fixed sub-packages:** `domain`, `application`, `infrastructure`, `configuration`, `web`. Omit a sub-package when a layer has no code yet. These were not applied retroactively to the PR-003 entities to keep this PR minimal; the next PR touching identity or platform code places files in the correct sub-package.

### Tests executed

```
./mvnw spotless:check   → BUILD SUCCESS (19 files clean)
./mvnw test             → 29 tests, 0 failures, 0 errors
./mvnw verify           → BUILD SUCCESS (spotless:check + all tests)
git diff --check        → PASSED (no trailing whitespace or conflict markers)
```

Test breakdown:

- `ContextSmokeTest`: 6 tests — context start, Flyway version, schema presence, 5 JPA repositories discovered
- `EntityMappingTest`: 5 tests — all five mapping/load cases pass
- `FoundationMigrationTest`: 16 tests — all constraint/cascade tests remain green
- `InfrastructureTest`: 2 tests — pass unchanged

### Follow-up work

- Do not define the next PR here.
- After user review and acceptance, the next implementation unit will be designed just-in-time from the committed repository state.
