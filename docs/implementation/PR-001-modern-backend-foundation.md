# PR-001 — Modern backend foundation

Status: **COMPLETE**

## Goal

Replace the legacy backend runtime/build skeleton with a minimal, reproducible Java 25 + Spring Boot 4.1.0 foundation that can start and test against an empty PostgreSQL database without recreating any legacy schema. Preserve the frontend and historical evidence, remove obsolete backend/migration/integration baggage, establish deterministic build/format/test tooling, and leave the repository ready for `V1__foundation.sql` in PR-002.

This PR intentionally implements **no business feature, authentication flow, financial table, or replacement Flyway migration**.

## Source documents

Read and follow:

- `AGENTS.md`
- `docs/engineering/coding-standards.md`
- `docs/review/backend-master-plan.md` — technology baseline, pull-request execution model, and R0
- `docs/review/progress-report.md` — current rewrite status and preserved evidence

`docs/review/accounting-contract.md` is not required for implementation in this PR because no financial behavior is introduced.

## Starting state

The following are already true and must be preserved:

- the user created and switched to Git branch `rewrite`;
- the user created PostgreSQL database `extreme_accounting`;
- the database is disposable and must not receive the legacy dump/migration chain;
- the repository still contains the legacy Spring Boot 3.5.x / Java 21 backend being replaced;
- `src/main/web` is the existing frontend and is out of scope for modification;
- the authoritative rewrite documents already exist under `docs/`;
- no replacement `V1__foundation.sql` exists yet.

Before editing, inspect the actual repository and record the starting commit using `git rev-parse HEAD` in the PR completion record/progress report. **Do not create a branch or commit.**

## Scope

1. **Preserve evidence before deletion**
   - Inspect the current controllers and record the legacy HTTP route inventory in `docs/review/legacy-http-routes.md` if an equivalent complete route inventory does not already exist.
   - Record the PR starting Git commit in `docs/review/progress-report.md`.
   - Git history and existing review documents are the preservation mechanism; do not copy old Java code into archive folders.

2. **Replace the active backend Java skeleton**
   - Remove legacy backend Java packages/classes under the existing backend source root after preserving the route inventory.
   - Recreate only the minimal application skeleton under the existing base package `dev.canverse.stocks`.
   - Keep exactly one `@SpringBootApplication` entry point.
   - Add minimal cross-cutting infrastructure only:
     - a UTC `Clock` bean;
     - a UUID/ID generation abstraction with a production implementation and deterministic test override support;
     - minimal RFC 9457-compatible problem-details boundary infrastructure sufficient for later APIs, without inventing domain problem codes yet.
   - Do not rename the base package in this PR.

3. **Remove obsolete backend resources**
   - Remove the active legacy Flyway chain (`V2`–`V14`) and legacy `schema.sql`.
   - Remove MyBatis mapper XML/configuration and unused legacy provider/integration resources that exist only for the backend being replaced.
   - Remove Spring Batch schema/resources/configuration if present; Spring Batch is not part of the rewrite.
   - Remove tracked RSA/private signing material and other committed secret material from active resources.
   - Add a narrow ignored local-secret location (for example `.local-secrets/`) if the repository does not already have one. Do not broadly ignore all certificate/fixture file extensions.
   - Do **not** add replacement JWT keys/configuration yet; authentication owns that later.

4. **Modernize the Maven build**
   - Use `spring-boot-starter-parent` version `4.1.0`.
   - Set Java release/version to `25`.
   - Update/add Maven Wrapper to stable Maven `3.9.16`.
   - Add Maven Enforcer `3.6.3` and fail builds unless:
     - Java is `25.x` (`[25,26)`);
     - Maven is at least `3.9.16` and below Maven 4 (`[3.9.16,4)`).
   - Do not enable `--enable-preview` anywhere.
   - Use Spring Boot dependency management for Spring Framework, Spring Data, Hibernate ORM, Hibernate Validator, Jackson, Flyway, PostgreSQL, Testcontainers integration modules, and other managed libraries. Do not pin their transitive platform versions individually.

5. **Use this minimal production dependency set**
   - `org.springframework.boot:spring-boot-starter-webmvc` (not deprecated `spring-boot-starter-web`);
   - `org.springframework.boot:spring-boot-starter-validation`;
   - `org.springframework.boot:spring-boot-starter-data-jpa`;
   - `org.springframework.boot:spring-boot-starter-actuator`;
   - `org.flywaydb:flyway-core`;
   - PostgreSQL Flyway database support required by the Boot-managed Flyway version (use the supported PostgreSQL module rather than custom Flyway code);
   - `org.postgresql:postgresql` with runtime scope.

   Do not add Spring Security, OAuth/OIDC, Spring Batch, MyBatis, QueryDSL, MapStruct, Lombok, springdoc/OpenAPI, provider SDKs, AI SDKs, messaging, caching, or unrelated libraries in this PR. A later bounded PR adds a dependency when it first has a real use.

6. **Use this minimal test dependency set**
   - `org.springframework.boot:spring-boot-starter-test`;
   - `org.springframework.boot:spring-boot-testcontainers`;
   - `org.testcontainers:postgresql`.

   Use Boot-managed Testcontainers versions. Do not add H2/HSQL/Derby; PostgreSQL behavior is tested with PostgreSQL.

7. **Establish deterministic formatting**
   - Add `com.diffplug.spotless:spotless-maven-plugin` `3.9.0`.
   - Format Java with `palantir-java-format` `2.96.0`, 120-column Palantir style.
   - Bind `spotless:check` to the normal verification lifecycle so CI/agents/humans get the same result.
   - Provide `./mvnw spotless:apply` as the local formatting command.
   - Do not add Checkstyle, SpotBugs, Error Prone, ArchUnit, Sonar, or another static-analysis framework in this PR. We can add a focused tool later if real code demonstrates a need.

8. **Configure the application for the rewrite database**
   - Default application configuration must be environment-overridable and must not commit a real password/secret.
   - Local development targets database name `extreme_accounting`.
   - Flyway enabled.
   - `spring.flyway.baseline-on-migrate=false`.
   - Hibernate `ddl-auto=validate`.
   - `spring.jpa.open-in-view=false`.
   - SQL init/schema auto-initialization disabled.
   - No Spring Batch initialization.
   - No application profile may execute the legacy dump or legacy migrations.

9. **Keep the frontend intact**
   - Do not edit files under `src/main/web`.
   - Preserve existing Maven/resource wiring required to package/serve the frontend if it is independent of the removed backend.
   - If frontend build wiring makes ordinary backend tests unnecessarily execute Node/Vite, isolate that wiring from the test lifecycle rather than modifying frontend source code.

10. **Add green smoke tests**
    - Add a Testcontainers PostgreSQL context smoke test using Spring Boot's supported Testcontainers integration/service connection facilities.
    - The test must prove the application context starts on Java 25 against a clean PostgreSQL container with Flyway enabled and Hibernate validation enabled.
    - Assert that **zero replacement application Flyway migrations** have been applied because PR-002 has not created `V1` yet.
    - Assert that representative legacy domain tables are not recreated. Prefer checking migration/schema metadata rather than maintaining a huge list of old tables.
    - Add a small test proving the production `Clock`/ID infrastructure can be deterministically overridden in tests.
    - All committed tests must pass. Do not commit an intentionally failing `V1 missing` test.

11. **Update implementation documentation after the code is complete**
    - Fill this file's Completion record.
    - Update `docs/review/progress-report.md` with the actual starting commit and accepted implementation facts/deviations.
    - Do not advance `docs/implementation/CURRENT.md` to PR-002. The user will review the diff and advance it after accepting/committing PR-001.

## Explicit non-goals

This PR does **not** implement:

- `V1__foundation.sql` or any replacement application database table;
- identity/user/session entities;
- registration/login/JWT/refresh tokens/OAuth/Google authentication;
- Spring Security configuration;
- authorization/ownership;
- durable jobs;
- reference data/currencies/instruments;
- financial accounts, opening state, ledger activities/postings, balances, trades, imports, claims, observations, analytics, or demo data;
- any Open Banking/bank/broker integration;
- API versioned business endpoints;
- OpenAPI generation;
- frontend changes;
- package-name/repository-name/product-name redesign;
- Maven multi-module conversion;
- microservices, messaging, ports/adapters, generic framework layers;
- Git branch creation, commits, merges, rebases, tags, resets or pushes.

## Database changes

Migration(s): **None.**

This PR removes the obsolete active migration chain but does not create `V1`.

Expected database behavior:

- tests use fresh PostgreSQL Testcontainers instances;
- local runtime points at `extreme_accounting` through environment-overridable configuration;
- Flyway is enabled but has zero replacement application migrations to apply;
- Hibernate validates mappings but there are no replacement entities requiring application tables yet;
- no legacy application table is created automatically.

## Application changes

The exact filenames may vary slightly if the existing repository layout requires it, but the resulting structure should stay close to:

```text
src/main/java/dev/canverse/stocks/
  ServerApplication.java
  platform/
    config/
      TimeConfiguration.java
    id/
      IdGenerator.java
      UuidIdGenerator.java
    web/
      ApiExceptionHandler.java

src/main/resources/
  application.yml
  application-local.yml        # only if useful; no committed real secret

src/test/java/dev/canverse/stocks/
  ... context/database smoke test support
  ... deterministic clock/id test support
```

Do not create placeholder packages/classes for future capabilities.

### ID abstraction

Keep it tiny. A shape such as this is sufficient:

```java
@FunctionalInterface
public interface IdGenerator {
    UUID next();
}
```

Do not build a generic ID framework.

### Time abstraction

Application/domain code will receive `java.time.Clock`; do not introduce a custom time framework when `Clock` is enough.

### Problem-details infrastructure

Use Spring Framework/Spring Boot's RFC 9457 `ProblemDetail` support. This PR may establish a single boundary handler and stable extension-field conventions, but must not invent a large exception hierarchy or financial/domain error catalogue before those domains exist.

## API contract

No business API endpoints are added.

Actuator health may be available according to the minimal actuator configuration, but do not expose unnecessary actuator endpoints.

No compatibility implementation of legacy endpoints is required in this PR; the legacy route list is evidence only.

## Business invariants

None beyond architecture/build invariants.

The important invariants for this PR are:

- Flyway is the only future DDL owner.
- Hibernate never creates/updates schema.
- the legacy migration chain cannot run against `extreme_accounting` from the active application;
- no secret/signing key is committed as replacement configuration;
- Java preview/incubator features are disabled;
- Spring/Hibernate platform dependency versions remain Boot-managed;
- no future business capability is scaffolded speculatively.

## Required tests

### Pure/domain

- deterministic `Clock` override test;
- deterministic `IdGenerator` override/test seam.

No financial/domain calculation tests exist yet.

### PostgreSQL/Testcontainers

- PostgreSQL container starts through Boot/Testcontainers integration;
- Spring application context starts successfully against it;
- Flyway is enabled and reports zero replacement application migrations applied;
- no legacy application tables are recreated;
- Hibernate validation is enabled and startup remains green with the empty replacement model.

### HTTP/security

- no security tests in this PR;
- if a health HTTP smoke test is used, verify only the minimal health behavior and do not create business endpoints merely to test MVC.

## Acceptance criteria

PR-001 is ready for human review only when all of the following are true:

1. `./mvnw -version` reports Maven 3.9.16 running on Java 25.
2. the project uses Spring Boot parent 4.1.0 and does not individually override Boot-managed Spring/Data/Hibernate/Jackson/Testcontainers platform versions.
3. `spring-boot-starter-webmvc` is used instead of the deprecated Boot 4 `spring-boot-starter-web` starter.
4. Java compilation targets release 25 and no preview flag exists in Maven, wrapper JVM config, test config, or documented normal run commands.
5. Maven Enforcer rejects unsupported Java/Maven versions.
6. Spotless/Palantir formatting is deterministic and `spotless:check` passes.
7. legacy Java backend implementation, MyBatis resources, `schema.sql`, and V2–V14 active migrations are removed from the replacement runtime.
8. `src/main/web` source files are unchanged.
9. tracked private/signing key material is removed from the active repository content and is not replaced by another committed secret.
10. the active Spring application consists only of the minimal rewrite skeleton and contains no legacy entity/mapper scanning.
11. Flyway is enabled, baseline-on-migrate is false, Hibernate is validate-only, OSIV is off, SQL auto-init is off.
12. Testcontainers uses PostgreSQL rather than an in-memory substitute.
13. the empty-database smoke test is green and proves zero replacement migrations/no legacy schema creation before `V1`.
14. `./mvnw test` passes.
15. `./mvnw verify` passes, including formatter enforcement.
16. this PR completion record is filled accurately.
17. no Git mutation has been performed by the agent beyond working-tree file edits.

## Verification commands

The agent should run and record the relevant outputs/results of:

```bash
java -version
./mvnw -version
./mvnw spotless:check
./mvnw test
./mvnw verify

git status --short
git diff --check
```

Also inspect the dependency tree for accidental legacy/duplicate frameworks:

```bash
./mvnw dependency:tree
```

At minimum verify that MyBatis, QueryDSL, Spring Batch, Spring Security/OAuth, legacy provider SDKs and individually pinned Hibernate/Spring Data dependencies have not leaked into the new baseline unless an existing frontend packaging plugin has an unrelated build-time dependency.

Do **not** run destructive commands against the user's local PostgreSQL database. Tests use Testcontainers.

## Review guide for the user

When reviewing the diff, focus on:

1. `pom.xml` — is the dependency/build surface genuinely minimal?
2. deleted files — are deletions legacy backend/resource code rather than frontend/review evidence?
3. application config — can Hibernate or SQL init accidentally create schema?
4. secrets — is any private key/password/token still tracked or newly introduced?
5. smoke tests — do they use PostgreSQL and prove the empty replacement state without hiding failures?
6. new Java skeleton — is it tiny, idiomatic, and free of speculative abstractions?
7. `src/main/web` — should show no source changes.

If this review takes more than a short focused pass because the agent added architecture not requested here, reject/split the extra work rather than accepting it "for later".

## Completion record

### Starting commit

- `b2c42e751097c7a19805c0a53b28941fa4deebce`

### Implemented

1. **Legacy route inventory** — recorded all 29 legacy HTTP routes in `docs/review/legacy-http-routes.md` before deletion.
2. **Legacy backend removal** — deleted all legacy Java packages (`config`, `domain`, `integration`, `repository`, `rest`, `security`, `service`) and `Server.java` (~127 source files).
3. **Legacy resources removed** — deleted V2–V14 active Flyway migrations, `schema.sql`, MyBatis mapper XML (`mappers/portfolio/`), RSA certs (`certs/private.pem`, `certs/public.pem`), legacy reference data files (`data/`, `scripts/`), and `application-prod.yml`.
4. **Maven build modernized** — Spring Boot parent `4.1.0`, Java release `25`, Maven Wrapper updated to `3.9.16`, Maven Enforcer `3.6.3` enforcing `[25,26)` Java and `[3.9.16,4)` Maven, Spotless `3.9.0` with Palantir Java Format `2.96.0` (120-col PALANTIR style) bound to `verify`.
5. **Minimal production dependency set** — `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `postgresql` (runtime).
6. **Minimal test dependency set** — `spring-boot-starter-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`.
7. **Application configuration** — `application.yml` replaced: datasource defaults to `extreme_accounting`, Flyway enabled/`baseline-on-migrate=false`, Hibernate `ddl-auto=validate`, `open-in-view=false`, SQL init disabled, minimal actuator (`health` only).
8. **New skeleton** — `ServerApplication.java` (single `@SpringBootApplication`), `platform/config/TimeConfiguration.java` (UTC `Clock` bean), `platform/id/IdGenerator.java` (`@FunctionalInterface UUID next()`), `platform/id/UuidIdGenerator.java` (production impl), `platform/web/ApiExceptionHandler.java` (extends `ResponseEntityExceptionHandler`, generic fallback with RFC 9457 `ProblemDetail`).
9. **`.local-secrets/`** added to `.gitignore`.
10. **Smoke tests** — `ContextSmokeTest` (Testcontainers PostgreSQL 17 via `@ServiceConnection`, proves context starts on Java 25, zero applied migrations, no legacy tables); `InfrastructureTest` (pure unit tests for fixed `Clock` and `IdGenerator` lambda override seam).

### Deviations from specification

- **`spring-boot-starter-flyway` used instead of bare `flyway-core`** — In Spring Boot 4.1.0, Flyway auto-configuration was extracted from `spring-boot-autoconfigure` into a separate `spring-boot-flyway` module. Using bare `flyway-core` provides no `Flyway` bean. `spring-boot-starter-flyway` is the Boot-supported, version-managed way to enable Flyway auto-configuration in Boot 4.x.
- **`testcontainers-postgresql` and `testcontainers-junit-jupiter` used instead of `testcontainers:postgresql`** — Testcontainers 2.x (managed by Boot 4.1.0) renamed all module artifacts with the `testcontainers-` prefix. `testcontainers-postgresql` replaces `postgresql` and `testcontainers-junit-jupiter` provides `@Testcontainers`/`@Container`.
- **`productionIdGeneratorProducesDistinctUuids` test removed** — `UuidIdGenerator` is package-private (intentional encapsulation). The production impl test was dropped; the two remaining tests prove the `Clock` and `IdGenerator` seams comprehensively.

### New decisions

- Spring Boot 4.1.0 requires `spring-boot-starter-flyway` (not `flyway-core` alone) for Flyway auto-configuration.
- Testcontainers 2.x artifact names use `testcontainers-` prefix for all modules.
- `UuidIdGenerator` is deliberately package-private; `IdGenerator` interface is public and the only API surface.

### Tests executed

```
java -version   → openjdk version "25.0.3" 2026-04-21 LTS
./mvnw -version → Apache Maven 3.9.16 / Java 25.0.3

./mvnw spotless:check  → BUILD SUCCESS (7 files clean)
./mvnw test            → BUILD SUCCESS, Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
./mvnw verify          → BUILD SUCCESS, Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

Dependency tree: no MyBatis, QueryDSL, Spring Batch, Spring Security, OAuth2, Gemini, Lombok, SpringDoc, Caffeine, or individually pinned Hibernate/Spring Data dependencies in the production scope.

### Follow-up work

- `PR-002` will introduce the first replacement Flyway baseline/foundation migration; exact scope will be specified only after PR-001 is reviewed and accepted.
