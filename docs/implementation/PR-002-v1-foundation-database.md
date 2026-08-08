# PR-002 — V1 foundation database

Status: **COMPLETE**

## Goal

Create the first authoritative Flyway migration for the rewritten backend: a small, reviewed PostgreSQL foundation containing the application schemas plus the identity/session/security-event/durable-job tables required by later slices.

This PR establishes **database structure only**. It does not implement registration, login, refresh-token behavior, Spring Security, JPA entities, repositories, controllers, or job execution.

The primary review artifact should be one understandable migration and focused PostgreSQL tests proving its constraints.

## Source documents

Read and follow:

- `AGENTS.md`
- `docs/engineering/coding-standards.md`
- `docs/review/backend-master-plan.md` — fresh-database strategy, R1 foundation requirements, migration ownership
- `docs/review/progress-report.md` — current rewrite status

`docs/review/accounting-contract.md` is not required for implementation in this PR because no financial activity, balance, valuation, or calculation behavior is introduced.

## Starting state

PR-001 has been reviewed and accepted. The active backend should already have:

- Java 25;
- Spring Boot 4.1.x baseline;
- PostgreSQL + Flyway + JPA dependencies;
- Hibernate `ddl-auto=validate`;
- SQL auto-init disabled;
- PostgreSQL Testcontainers smoke coverage;
- no active legacy `V2`–`V14` migration chain;
- no replacement application migration yet;
- no replacement JPA entities yet;
- the minimal `ServerApplication`, `Clock`, ID generator, and problem-details infrastructure;
- `src/main/web` preserved unchanged.

Before editing, inspect the actual repository rather than assuming PR-001 filenames exactly match the earlier proposal.

Do not perform Git mutations. The user will review and commit the working-tree changes.

## Scope

1. **Create the authoritative migration**
   - Add `src/main/resources/db/migration/V1__foundation.sql`.
   - Flyway remains the sole DDL owner.
   - Do not add `schema.sql`, Hibernate DDL annotations, runtime schema creation code, or test-only duplicate DDL.
   - Do not use `IF NOT EXISTS` to hide an unexpected pre-existing application object. A fresh/disposable database should fail loudly if the expected baseline cannot be created as designed.

2. **Create the application schemas**

   `V1__foundation.sql` creates exactly these application schemas:
   - `identity`
   - `reference`
   - `ledger`
   - `data`
   - `money`
   - `analysis`
   - `asset`
   - `platform`

   Only `identity` and `platform` receive application tables in this PR. The other schemas are deliberate empty ownership boundaries for later migrations.

   Do not create PostgreSQL extensions, PostgreSQL enum types, sequences, views, triggers, procedures, or functions in this PR.

3. **Create `identity.user_account`**

   Purpose: stable application user identity independent of any one authentication provider.

   Required columns:

   | Column             | PostgreSQL type | Rules                                                         |
   | ------------------ | --------------- | ------------------------------------------------------------- |
   | `id`               | `uuid`          | primary key; application-generated, no DB UUID default        |
   | `email`            | `text`          | not null; trimmed/non-blank                                   |
   | `email_normalized` | `text`          | not null; trimmed/non-blank; lowercase canonical lookup value |
   | `disabled_at`      | `timestamptz`   | nullable                                                      |
   | `created_at`       | `timestamptz`   | not null; default `CURRENT_TIMESTAMP`                         |
   | `updated_at`       | `timestamptz`   | not null; default `CURRENT_TIMESTAMP`                         |

   Required constraints/indexes:
   - named primary key;
   - unique constraint or unique index on `email_normalized`;
   - check that `email` is not blank and has no leading/trailing whitespace;
   - check that `email_normalized` is not blank, has no leading/trailing whitespace, and is lowercase;
   - index supporting enabled-user lookup is optional only if it is genuinely useful; do not add speculative indexes merely because columns exist.

   Important semantic boundary:
   - `disabled_at != null` means the account is disabled;
   - do not introduce a user-status enum/check catalogue in this PR;
   - email normalization logic is implemented in PR-003, but the database protects the canonical normalized representation.

4. **Create `identity.auth_identity`**

   Purpose: bind a user to one authentication provider identity. It must support local auth first and external identities later without making Google/OpenID logic part of this PR.

   Required columns:

   | Column             | PostgreSQL type | Rules                                      |
   | ------------------ | --------------- | ------------------------------------------ |
   | `id`               | `uuid`          | primary key; application-generated         |
   | `user_account_id`  | `uuid`          | not null FK to `identity.user_account(id)` |
   | `provider`         | `text`          | not null; trimmed/non-blank                |
   | `provider_subject` | `text`          | not null; trimmed/non-blank                |
   | `password_hash`    | `text`          | nullable; used by local auth later         |
   | `created_at`       | `timestamptz`   | not null; default `CURRENT_TIMESTAMP`      |
   | `updated_at`       | `timestamptz`   | not null; default `CURRENT_TIMESTAMP`      |

   Required constraints/indexes:
   - FK to `user_account` with delete behavior chosen deliberately; use `ON DELETE CASCADE` so full user deletion cannot strand credentials;
   - unique `(provider, provider_subject)`;
   - unique `(user_account_id, provider)` for the current product model: at most one identity per provider for one user;
   - non-blank provider/subject checks;
   - index on `user_account_id` if not already adequately covered by the chosen unique index.

   Do **not** add a database check enumerating `LOCAL`, `GOOGLE`, or future providers. Provider codes are application values that may expand without a database-type migration.

   Do **not** implement password hashing or provider-specific password-nullability rules yet; PR-003 owns the local authentication workflow and can strengthen semantics through application logic/tests.

5. **Create `identity.device_session`**

   Purpose: persist refresh-session/token rotation history in a form that can later detect token reuse and support device-session revocation.

   Model one refresh-token generation as one row. Rotating a token creates a replacement row in the same `family_id` and revokes/replaces the previous row rather than overwriting token history.

   Required columns:

   | Column                   | PostgreSQL type | Rules                                                            |
   | ------------------------ | --------------- | ---------------------------------------------------------------- |
   | `id`                     | `uuid`          | primary key; application-generated                               |
   | `user_account_id`        | `uuid`          | not null FK to user account                                      |
   | `family_id`              | `uuid`          | not null; stable across rotations for one logical device session |
   | `refresh_token_hash`     | `text`          | not null; only a one-way token hash is persisted                 |
   | `device_label`           | `text`          | nullable                                                         |
   | `created_at`             | `timestamptz`   | not null; default `CURRENT_TIMESTAMP`                            |
   | `last_used_at`           | `timestamptz`   | nullable                                                         |
   | `expires_at`             | `timestamptz`   | not null                                                         |
   | `revoked_at`             | `timestamptz`   | nullable                                                         |
   | `revoke_reason`          | `text`          | nullable                                                         |
   | `replaced_by_session_id` | `uuid`          | nullable self-FK                                                 |

   Required constraints/indexes:
   - FK `user_account_id -> identity.user_account(id)` with `ON DELETE CASCADE`;
   - self-FK for `replaced_by_session_id`; use deliberate delete behavior that does not create a deletion cycle (`ON DELETE SET NULL` is acceptable);
   - unique `refresh_token_hash`;
   - check `expires_at > created_at`;
   - check a row cannot replace itself;
   - index for listing a user's sessions efficiently;
   - index for session-family lookup;
   - **partial unique index allowing at most one non-revoked row per `family_id`** (`WHERE revoked_at IS NULL`). This is a core rotation invariant, not an optimization.

   Do not store raw refresh tokens, JWTs, passwords, IP addresses, user-agent blobs, or encryption keys in this migration.

6. **Create `platform.security_event`**

   Purpose: append security-relevant events later without inventing a large audit framework now.

   Required columns:

   | Column            | PostgreSQL type | Rules                                               |
   | ----------------- | --------------- | --------------------------------------------------- |
   | `id`              | `uuid`          | primary key; application-generated                  |
   | `user_account_id` | `uuid`          | nullable FK for events associated with a known user |
   | `event_type`      | `text`          | not null; trimmed/non-blank                         |
   | `occurred_at`     | `timestamptz`   | not null                                            |
   | `details`         | `jsonb`         | not null; default empty JSON object                 |

   Required constraints/indexes:
   - nullable FK to `identity.user_account(id)` using `ON DELETE CASCADE`; anonymous events remain possible with `user_account_id = null`;
   - check `event_type` is non-blank;
   - check `details` is a JSON object, not an arbitrary scalar/array;
   - index `(user_account_id, occurred_at desc)` for user-scoped history;
   - index `(event_type, occurred_at desc)` for security investigation/querying.

   JSONB is acceptable here because `details` is sparse event metadata, not hidden core financial state.

7. **Create `platform.job`**

   Purpose: minimal durable queue/state table for later imports/rebuilds. This PR creates storage only; no scheduler/worker/claim service is implemented.

   Required columns:

   | Column                  | PostgreSQL type | Rules                                                  |
   | ----------------------- | --------------- | ------------------------------------------------------ |
   | `id`                    | `uuid`          | primary key; application-generated                     |
   | `owner_user_account_id` | `uuid`          | nullable FK for user-scoped work; null for system jobs |
   | `job_type`              | `text`          | not null; trimmed/non-blank                            |
   | `status`                | `text`          | not null; default `READY`                              |
   | `payload`               | `jsonb`         | not null; default empty JSON object                    |
   | `available_at`          | `timestamptz`   | not null; default `CURRENT_TIMESTAMP`                  |
   | `claimed_by`            | `text`          | nullable                                               |
   | `claim_token`           | `uuid`          | nullable                                               |
   | `claimed_at`            | `timestamptz`   | nullable                                               |
   | `heartbeat_at`          | `timestamptz`   | nullable                                               |
   | `attempt_count`         | `integer`       | not null; default `0`                                  |
   | `max_attempts`          | `integer`       | not null; default `5`                                  |
   | `completed_at`          | `timestamptz`   | nullable                                               |
   | `last_error`            | `text`          | nullable                                               |
   | `created_at`            | `timestamptz`   | not null; default `CURRENT_TIMESTAMP`                  |
   | `updated_at`            | `timestamptz`   | not null; default `CURRENT_TIMESTAMP`                  |

   Required status values for this initial durable state machine:
   - `READY`
   - `RUNNING`
   - `SUCCEEDED`
   - `FAILED`
   - `CANCELLED`

   Use a migration-owned `CHECK` constraint rather than a PostgreSQL enum.

   Required constraints/indexes:
   - nullable FK `owner_user_account_id -> identity.user_account(id)` with `ON DELETE CASCADE`;
   - non-blank `job_type`;
   - `payload` must be a JSON object;
   - `attempt_count >= 0`;
   - `max_attempts > 0`;
   - `attempt_count <= max_attempts`;
   - when `status = 'RUNNING'`, `claimed_by`, `claim_token`, and `claimed_at` must all be non-null;
   - terminal states `SUCCEEDED`, `FAILED`, and `CANCELLED` require `completed_at`;
   - partial claim index supporting the future query pattern `status = 'READY'` ordered by `available_at`, then creation/order key;
   - partial heartbeat/recovery index for `status = 'RUNNING'`;
   - optional owner index only if not adequately served by another required index.

   PR-004 will define and test the actual `FOR UPDATE SKIP LOCKED` claim/retry behavior. Do not write worker code now.

8. **Use explicit, reviewable SQL conventions**
   - Name PK, FK, unique, and check constraints consistently rather than accepting generated names.
   - Use lower snake_case identifiers.
   - Use `uuid` for application-owned IDs.
   - Use `timestamptz` for instants.
   - Use `text` for evolving application codes instead of PostgreSQL enums.
   - Do not add `SERIAL`/identity numeric IDs.
   - Do not use DB-generated UUID defaults; the existing `IdGenerator` owns application IDs.
   - Do not add triggers to maintain `updated_at`; application services will own updates when they exist.
   - Do not hide identity/session state in JSONB.
   - Do not create financial/reference tables early merely because their schemas now exist.

9. **Update the existing smoke test for V1**

   The PR-001 smoke test currently expects zero replacement migrations. Change it so the accepted state is:
   - Flyway applies exactly one application migration: version `1` / `V1__foundation.sql`;
   - Spring context starts against a fresh PostgreSQL container;
   - representative legacy application tables are still absent;
   - the five foundation tables exist in their intended schemas;
   - no application/domain tables are created in `public` (the Flyway history table in `public` is acceptable if that is where Flyway manages it).

10. **Add focused migration/constraint tests**

    Add PostgreSQL/Testcontainers tests that prove at minimum:
    - all eight schemas exist after migration;
    - the five foundation tables exist in the correct schemas;
    - duplicate `email_normalized` is rejected;
    - an auth identity cannot reference a missing user;
    - duplicate `(provider, provider_subject)` is rejected;
    - duplicate refresh-token hash is rejected;
    - a session with `expires_at <= created_at` is rejected;
    - two simultaneous non-revoked rows for the same `family_id` are rejected;
    - an invalid job status is rejected;
    - a `RUNNING` job without claim metadata is rejected;
    - invalid attempt counts are rejected;
    - deleting a user cascades to that user's auth identities, device sessions, user-scoped security events, and user-scoped jobs.

    Prefer testing actual constraint behavior over brittle assertions about every index/constraint name in `pg_catalog`. Object-existence assertions should verify the important schema/table/index contract without turning tests into a verbatim duplicate of the SQL migration.

11. **Keep the application layer empty**

    Do not add:
    - JPA entities;
    - Spring Data repositories;
    - services;
    - controllers;
    - security configuration;
    - password encoders;
    - JWT/token code;
    - job scheduler/worker code;
    - domain exceptions/problem codes.

    This PR proves the database contract first. PR-003 will build local identity/authentication behavior on it.

12. **Update implementation documentation after code is complete**
    - Fill this PR's Completion record accurately.
    - Update `docs/review/progress-report.md` with the migration/test results and any accepted deviation.
    - Do not advance `docs/implementation/CURRENT.md` to PR-003. The user reviews and accepts the diff first.

## Explicit non-goals

This PR does **not** implement:

- registration/login/logout/refresh endpoints;
- password hashing behavior;
- Spring Security;
- Google/OIDC/OAuth;
- authorization/current-principal helpers;
- JPA entities or repositories;
- durable-job claiming/retry code;
- `/api/v1` business endpoints;
- reference countries/currencies/markets/instruments (`V2` owns those);
- financial accounts/opening balances;
- ledger activities/postings;
- portfolios/trades;
- imports/reconciliation;
- claims/shared expenses;
- observations/market data;
- demo data;
- frontend changes;
- Open Banking/bank/broker connectivity;
- payment initiation;
- Git branches, commits, merges, rebases, tags, resets, stashes or pushes.

## Database changes

Migration:

```text
src/main/resources/db/migration/V1__foundation.sql
```

Creates:

```text
identity
  user_account
  auth_identity
  device_session

platform
  security_event
  job

reference   # schema only
ledger      # schema only
data        # schema only
money       # schema only
analysis    # schema only
asset       # schema only
```

`public` is not an application-domain schema. It may contain Flyway's own schema-history table according to the existing Flyway configuration.

## Application changes

No production Java application behavior is added.

Expected production-code diff outside the migration should be close to zero. If the agent needs to add production Java to make the migration work, stop and re-evaluate rather than introducing infrastructure hidden from this PR.

Expected test changes:

```text
src/test/java/dev/canverse/stocks/
  ContextSmokeTest.java            # update V1 expectation
  ...FoundationMigrationTest.java  # exact name/layout may vary
```

Do not create placeholder identity/job packages in production Java.

## API contract

No API endpoints are added or changed.

The frontend remains untouched and may continue to be incompatible with the replacement backend until later bounded compatibility/API work.

## Database invariants

The following invariants are part of the accepted foundation contract:

1. Application IDs are UUIDs generated by application code, not database sequences/default UUID functions.
2. Email uniqueness is enforced through the canonical `email_normalized` value.
3. Authentication provider subjects are globally unique within a provider.
4. A user has at most one auth identity for a given provider in the current model.
5. Identity/session rows cannot outlive their deleted user.
6. Raw refresh tokens are never persisted; only hashes are stored.
7. Refresh-token rotation history is append-oriented: replacement rows are linked instead of overwriting the prior token row.
8. At most one non-revoked refresh-token row may exist for one session family at a time.
9. Security events may be anonymous, but user-scoped events are deleted with that user.
10. Durable jobs can be system-scoped or user-scoped.
11. A job cannot claim to be `RUNNING` without claim ownership metadata.
12. Terminal job states have a completion timestamp.
13. All instants use `timestamptz`.
14. Flyway owns this schema; Hibernate does not create/modify it.

## Required tests

### Pure/domain

No new pure/domain tests are required. This PR has no domain behavior.

### PostgreSQL/Testcontainers

Required:

- fresh PostgreSQL container migrates successfully to Flyway version 1;
- Spring context starts after migration;
- all application schemas/tables listed above exist;
- legacy tables remain absent;
- constraint behaviors listed in Scope item 10 are exercised against PostgreSQL;
- user-deletion cascade behavior is exercised;
- Hibernate validate mode remains enabled and does not create/alter tables.

Do not use H2 or mock SQL behavior.

### HTTP/security

None. Spring Security/authentication is not part of this PR.

## Acceptance criteria

PR-002 is ready for human review only when all of the following are true:

1. `V1__foundation.sql` is the only replacement application migration.
2. A fresh PostgreSQL database migrates successfully from empty to Flyway version `1`.
3. all eight application schemas exist.
4. only the five specified foundation tables are introduced.
5. no application-domain table is created in `public`.
6. every PK/FK/unique/check/index required by this specification is owned by SQL and reviewable in the migration.
7. no PostgreSQL enum/extension/trigger/function/view/procedure is introduced.
8. no DB-generated UUID strategy is introduced.
9. no JPA entity/repository/service/controller/security/job-worker production code is added.
10. the existing smoke test now expects and proves one foundation migration rather than zero migrations.
11. PostgreSQL tests prove the important uniqueness/FK/check/partial-unique/cascade behaviors.
12. `src/main/web` has no source changes.
13. `./mvnw spotless:check` passes.
14. `./mvnw test` passes.
15. `./mvnw verify` passes.
16. `git diff --check` passes.
17. this Completion record is filled accurately.
18. the agent performs no Git mutation beyond working-tree edits.

## Verification commands

Run and record:

```bash
./mvnw spotless:check
./mvnw test
./mvnw verify

git status --short
git diff --check
```

Useful non-destructive inspection commands are allowed, for example:

```bash
git diff -- src/main/resources/db/migration/V1__foundation.sql
```

Do not run the migration manually against the user's local `extreme_accounting` database as part of automated verification. Tests use fresh PostgreSQL Testcontainers instances.

If the user later chooses to start the application locally against `extreme_accounting`, normal Flyway startup may apply V1 there; that is not required for PR-002 acceptance.

## Review guide for the user

Review this PR mainly in this order:

1. **`V1__foundation.sql`** — read every table, FK, check, default, and index. This is the primary artifact.
2. **Scope creep** — there should be no auth service/JPA/business implementation hidden in the PR.
3. **Session model** — confirm one token generation per row, hashed token only, family/replacement relationship, one active row per family.
4. **Job model** — confirm it is only a durable state table, not a framework or worker implementation.
5. **Deletion semantics** — deleting a user should not strand credentials/sessions/user-scoped jobs/events.
6. **Tests** — they should prove PostgreSQL behavior rather than duplicating SQL as string assertions.
7. **Frontend** — there should be no source changes under `src/main/web`.

If the migration becomes much larger than this contract or introduces unrelated future tables, reject/split that extra scope.

## Completion record

Fill this before claiming PR-002 complete. Do not change `CURRENT.md` to the next PR.

### Starting commit

- `f139c8d0009eaad1f216cf6dae8efd0c91ec8b6b`

### Implemented

- `src/main/resources/db/migration/V1__foundation.sql`: creates all 8 application schemas and the 5 foundation tables (`identity.user_account`, `identity.auth_identity`, `identity.device_session`, `platform.security_event`, `platform.job`) with all required PKs, FKs, unique constraints, check constraints, and indexes as specified.
- `src/test/java/dev/canverse/stocks/ContextSmokeTest.java`: updated `zeroReplacementApplicationMigrationsApplied` → `oneFoundationMigrationApplied` (asserts exactly one migration at version 1); added `fiveFoundationTablesExistInCorrectSchemas` and `noApplicationDomainTableCreatedInPublicSchema`.
- `src/test/java/dev/canverse/stocks/FoundationMigrationTest.java`: 16 PostgreSQL/Testcontainers tests covering schema/table existence, all required constraint and cascade behaviors.

### Deviations from specification

- None.

### New decisions

- `java.time.OffsetDateTime` (not `java.time.Instant`) is used to pass `timestamptz` values in JDBC tests. The PostgreSQL JDBC driver 42.x does not accept `Instant` via `setObject()` without an explicit SQL type hint; `OffsetDateTime` maps directly to `TIMESTAMPTZ` and is the correct JDBC representation.

### Tests executed

```
./mvnw spotless:apply    # formatting applied
./mvnw spotless:check    # BUILD SUCCESS – 0 files need changes
./mvnw test              # Tests run: 24, Failures: 0, Errors: 0, Skipped: 0 – BUILD SUCCESS
./mvnw verify            # Tests run: 24, Failures: 0, Errors: 0, Skipped: 0; spotless clean – BUILD SUCCESS
git diff --check         # no whitespace errors
```

Test class breakdown:

- `ContextSmokeTest` (6 tests): context starts on Java 25, 1 migration applied at version 1, legacy tables absent, 5 foundation tables present in correct schemas, no domain tables in public schema, infrastructure beans available.
- `FoundationMigrationTest` (16 tests): all 8 schemas present, 5 tables present, duplicate `email_normalized` rejected, whitespace in email rejected, uppercase in `email_normalized` rejected, unknown-user FK rejected, duplicate `(provider, provider_subject)` rejected, duplicate refresh-token hash rejected, `expires_at <= created_at` rejected, two non-revoked rows for same `family_id` rejected, invalid job status rejected, RUNNING job without claim metadata rejected, negative `attempt_count` rejected, zero `max_attempts` rejected, `attempt_count > max_attempts` rejected, user-deletion cascade to auth identities / device sessions / security events / jobs verified.
- `InfrastructureTest` (2 tests): unchanged; still passing.

### Follow-up work

- PR-003 will add the first application behavior on this schema: local identity/authentication mapping and registration/login boundaries. Its exact scope will be written only after PR-002 is reviewed and accepted.
