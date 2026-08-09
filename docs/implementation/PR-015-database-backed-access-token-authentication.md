# PR-015 — Database-backed access-token authentication

Status: **ACTIVE**

## Goal

Convert an already decoded local access JWT into Spring Security's standard authenticated JWT token only after one owner-scoped database query proves that the signed user/session pair still identifies an active session for an enabled user. Every unusable or cross-user token fails through one safe invalid-bearer outcome.

This PR establishes current database eligibility and a minimal authenticated identity only. It does not install bearer-token extraction, a security filter chain, HTTP authorization, roles/permissions, or an endpoint.

## Source documents

- `AGENTS.md`
- `docs/implementation/STATE.md`
- `docs/implementation/PR-011-local-access-token-issuance.md` — accepted signed `sub`/`sid` and session-bound issuance contract
- `docs/implementation/PR-014-local-access-token-decoding.md` — accepted production cryptographic/structural decoder contract
- `docs/implementation/PR-013-opaque-refresh-session-authentication.md` — accepted current session/user eligibility and equality-boundary pattern
- `docs/review/backend-master-plan.md`
  - R1 items 5, 7, 9, and 10: session authentication, principal helpers, cross-user/revoked-session coverage, and disabled-user rejection
  - dependency, security, testing, persistence, and pull-request rules
- `docs/review/backend-audit.md`
  - SEC-004: reject disabled users during JWT conversion
- `docs/review/mobile-api-readiness.md`
  - reject disabled users during access-token authentication and retain device-session identity
- `docs/engineering/coding-standards.md`
  - owner-scoped queries, transaction boundaries, safe errors, dependencies, and PostgreSQL security testing
- Spring Security 7 documentation for custom `Converter<Jwt, AbstractAuthenticationToken>`, `JwtAuthenticationToken`, and the OAuth2 resource-server module
- Spring Data JPA 4.1 documentation for explicit nested-property derived queries

`docs/review/accounting-contract.md` is not required because this PR introduces no financial behavior.

## Starting state

Treat local commit `4621473` as authoritative.

Already implemented and not to be redesigned here:

- one production `JwtDecoder` verifies the startup-local RSA signature under RS256 and requires exact access-token headers, configured issuer/singleton audience, canonical UUID `sub`/`jti`/`sid`, and the accepted no-skew time envelope;
- `AccessTokenIssuanceService` derives `sub` and `sid` only from the persisted `DeviceSession` graph and never persists the JWT;
- `DeviceSession` exposes its ID, expiry, revocation state, and lazy mandatory `UserAccount` association inside application transactions;
- `UserAccount` exposes its ID and `disabledAt` state;
- `DeviceSessionRepository` already supports ordinary ID loading and stored-refresh-hash lookup;
- there is no Spring Security resource-server module, `JwtAuthenticationToken`, converter, bearer filter, security chain, authenticated endpoint, role, or authority model.

PR-014 structural validation means a normal decoder-to-converter call supplies canonical UUID claims. This PR still fails closed when the converter is invoked directly with missing or malformed identity claims.

## Scope

### 1. Add only the Spring Security authentication-token module

Add the Boot-managed dependency:

```text
org.springframework.security:spring-security-oauth2-resource-server
```

without a version.

Use this library only for Spring Security's standard resource-server authentication token, converter contract support, and invalid-bearer exception types.

Do not add `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, Spring Security test, an authorization server/client dependency, another JWT library, or a local version override. Merely adding the library must not create a `SecurityFilterChain` or change HTTP behavior.

### 2. Add one owner-scoped session lookup

Add exactly one method to `DeviceSessionRepository`:

```java
Optional<DeviceSession> findByIdAndUserAccount_Id(UUID sessionId, UUID userAccountId)
```

Use Spring Data's derived nested-property query. Both signed identities must participate in the SQL lookup so a session is never loaded globally and authorized against `sub` afterward.

Do not add an explicit JPQL/native query, entity graph, lock, projection, existence query, index, second repository, or generic ownership helper. The existing primary key and user foreign key are sufficient for this one-row lookup.

### 3. Add one database-backed JWT authentication converter

Add one concrete Spring component:

```text
dev.canverse.stocks.identity.application.LocalAccessTokenAuthenticationConverter
```

implementing:

```java
Converter<Jwt, AbstractAuthenticationToken>
```

Use constructor injection for exactly:

- `DeviceSessionRepository`;
- `Clock`.

Expose only the required method:

```java
AbstractAuthenticationToken convert(Jwt jwt)
```

Apply a read-only transaction to the public conversion method. Do not add a second service/interface or a separate principal/authority converter.

Within conversion:

1. require non-null `jwt` before observing time or querying;
2. read `sub` and string claim `sid`, require each to be an exact canonical UUID string, and parse them to `UUID` values before observing time or querying;
3. for a structurally valid attempt, capture `clock.instant()` exactly once;
4. call `findByIdAndUserAccount_Id(sessionId, userAccountId)` exactly once;
5. reject when no exact pair exists, the session is revoked, its expiry is at or before the captured instant, or its associated user is disabled;
6. on success return exactly:

```java
new JwtAuthenticationToken(jwt, List.of(), userAccountId.toString())
```

The result therefore:

- is authenticated through Spring Security's standard JWT token type;
- has the canonical user UUID as `Authentication.getName()`;
- retains the validated `Jwt`, including `sid`, as its token/principal data;
- has no granted authorities in this PR.

Missing/malformed identity claims and every database eligibility failure must throw `InvalidBearerTokenException` with one constant safe message. Its OAuth error code must be `invalid_token`. Do not use `AppException` for this Spring Security authentication-conversion boundary, add a new application error code, catch persistence failures, or include the token, claim values, IDs, state, expected values, or reason-specific detail in the message/logs.

Do not update `lastUsedAt`, revoke a session, refresh/issue a token, or write any row/event/job. Successful conversion is a read-only current-eligibility observation; later authorization must still use owner-scoped application queries.

## Explicit non-goals

- No `SecurityFilterChain`, `HttpSecurity`, bearer-token filter/resolver, OAuth2 resource-server auto-configuration, request authorization, CSRF/CORS policy, authentication entry point, access-denied handler, or Spring Security HTTP error mapping.
- No controller, endpoint, API DTO, login/refresh/logout delivery, cookie/header policy, or web/mobile transport decision.
- No custom `Authentication` implementation, custom principal record, user-details service, authentication manager/provider, role, permission, scope, granted authority, owner annotation, or method security.
- No access-token decoding/header/claim/time-policy change and no token issuance change.
- No refresh rotation/replacement/reuse response, logout/revocation/session-management mutation, security event, abuse control, or job behavior.
- No persistent key/JWK/keystore/rotation/provider/OIDC/Google behavior.
- No migration, constraint, index, entity mapping/mutation, frontend, or financial behavior.

## Database changes

Migration(s): none.

Tables/columns/constraints/indexes introduced or changed: none.

The existing `identity.device_session` primary key and mandatory user foreign key support the owner-scoped lookup. No schema change is justified.

## Application changes

Expected production-code surface is limited to:

- `pom.xml` — add only the Boot-managed Spring Security OAuth2 resource-server library module;
- `identity/infrastructure/DeviceSessionRepository.java` — add one exact session/user derived lookup;
- `identity/application/LocalAccessTokenAuthenticationConverter.java` — add the read-only current-eligibility converter.

No existing service, entity, configuration, decoder, issuer, controller, or HTTP class is changed.

## API contract

None. No route, request/response, cookie/header, HTTP status, authorization rule, or public problem response is added or changed.

## Business invariants

- Cryptographic/structural decoding is necessary but not sufficient: a currently active exact signed session/user pair is also required.
- Session ownership is enforced in the repository query through both `sid` and `sub`; a signed cross-user pairing cannot authenticate.
- A revoked session, a session expiring exactly at the observed instant, or a disabled user cannot produce an authenticated token.
- Successful authentication exposes only the canonical user UUID as the principal name and no authorities.
- Conversion observes current eligibility without mutating session/user state or granting authorization to a domain row.
- Every expected token-eligibility failure is one safe `invalid_token` bearer failure with no sensitive detail.

## Required tests

### Pure/domain

Add one focused collaborator/control-flow test for `LocalAccessTokenAuthenticationConverter` using a recording clock and mocked repository only to prove non-persistence call order/count:

1. null JWT fails the internal caller contract before clock/query work;
2. missing, malformed, and non-canonical `sub` or `sid` throw the same safe `InvalidBearerTokenException` before clock/query work;
3. a structurally valid attempt observes the clock once and performs exactly one owner-scoped repository call with the parsed session/user UUIDs;
4. no alternative repository lookup or collaborator call occurs.

Do not use the collaborator test to claim database eligibility or transaction behavior; the PostgreSQL suite owns those assertions.

### PostgreSQL/Testcontainers

Add one `LocalAccessTokenAuthenticationConverterTest` using the real Spring context, migrated PostgreSQL, production encoder/decoder/converter, fixed or controllable `Clock`, accepted registration/session/token services, and real repositories. Use explicit cleanup and no test-level transaction.

Cover at least:

1. **A valid current session becomes the minimal authenticated JWT identity**
   - register an enabled user, issue its session and access token through accepted services, and decode through the production decoder;
   - convert through the production converter;
   - assert the result is a `JwtAuthenticationToken`, is authenticated, retains the exact decoded `Jwt`, has canonical user UUID `getName()`, and has no authorities;
   - assert its retained `sid` is the issued session UUID;
   - prove all user, auth-identity, and session columns/row counts remain unchanged.

2. **Current session/user state fails closed uniformly**
   - issue otherwise valid tokens before deliberately establishing revoked, expiry-equal-to-clock, and disabled-user states through fixture SQL;
   - include a validly signed token naming a missing session and a validly signed cross-user `sub`/`sid` pair;
   - each conversion throws `InvalidBearerTokenException` with OAuth code exactly `invalid_token`, the same safe message, and no raw token/claim/UUID/state/reason detail;
   - all persisted rows remain unchanged by conversion attempts.

3. **The owner-scoped repository method has exact semantics**
   - the real issued session resolves only for its own user ID;
   - the same session ID with another existing user ID returns empty;
   - no persistence mutation occurs.

4. **The dependency alone adds no HTTP security boundary**
   - the real application context contains the converter and still contains no `SecurityFilterChain` bean.

Keep all accepted decoder, issuer, refresh-session, login, registration, migration, mapping, error, and HTTP tests green.

### HTTP/security

The production-decoder-to-converter PostgreSQL suite is the required security integration coverage for this application-only unit. No MockMvc/bearer/filter-chain test is required because no HTTP security boundary is installed.

## Acceptance criteria

1. `pom.xml` adds only Boot-managed `spring-security-oauth2-resource-server` and no starter/version/test/alternative security dependency.
2. `DeviceSessionRepository` adds exactly `findByIdAndUserAccount_Id(UUID, UUID)` and no lock/query/index/helper.
3. Exactly one new converter component implements `Converter<Jwt, AbstractAuthenticationToken>` with a read-only transaction and only repository/clock dependencies.
4. Null and malformed/non-canonical identity claims fail before time/query work; a structurally valid attempt observes time once and performs one exact session/user lookup.
5. Only the exact signed session/user pair succeeds when the session is unrevoked, expires strictly after the observed instant, and its user is enabled.
6. Missing, cross-user, revoked, expired-at-or-before-now, and disabled-user states throw the same safe `InvalidBearerTokenException` with OAuth code `invalid_token`.
7. Success returns a standard authenticated `JwtAuthenticationToken` retaining the decoded JWT, using the canonical user UUID as its name, and containing no authorities.
8. Conversion performs no writes, event/job creation, token issuance/refresh, authorization decision, network request, or logging of token/claim/ID/state details.
9. Adding the library and converter creates no `SecurityFilterChain` and changes no HTTP behavior.
10. No migration, mapping/entity, decoder/issuer, stable-key, refresh mutation, HTTP/API, role/permission, frontend, or financial change is added.
11. Required pure and PostgreSQL tests pass without weakening existing assertions.
12. `./mvnw spotless:check`, focused suite, `./mvnw test`, and `./mvnw verify` pass.
13. `git diff --check` passes and the complete diff contains only PR-015 planning, dependency, repository, converter, test, and completion-document files.
14. Completion Record, `STATE.md`, and `progress-report.md` are accurate; `CURRENT.md` remains on PR-015 through review.
15. Implementation and review agents perform no Git mutation.

## Documentation completion

Before completion, fill this Completion Record, update `STATE.md` and `progress-report.md`, and keep `CURRENT.md` on PR-015 until supervisor commit.

Do not mark HTTP bearer authentication, endpoint authorization, owner helpers, roles/permissions, persistent keys, refresh rotation/reuse response, logout/revocation, security events, or abuse controls implemented.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=LocalAccessTokenAuthenticationConverterTest,LocalAccessTokenAuthenticationConverterControlFlowTest,LocalAccessTokenDecoderTest,AccessTokenIssuanceServiceTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required.

## Completion record

Fill this before marking PR-015 complete.

### Starting commit

- `4621473` (`decode local access tokens`).

### Implemented

- Added only Boot-managed `spring-security-oauth2-resource-server` as a direct library dependency, without a starter, version override, or HTTP security configuration.
- Added the exact `DeviceSessionRepository.findByIdAndUserAccount_Id(...)` owner-scoped derived lookup.
- Added the read-only `LocalAccessTokenAuthenticationConverter`, which validates canonical `sub`/`sid` UUID claims before observing the clock, performs one owner-scoped session lookup, rejects missing, revoked, expired-at-or-before-now, and disabled-user sessions through one safe `invalid_token` outcome, and returns a standard authority-free `JwtAuthenticationToken` named by the user UUID.
- Added pure collaborator/control-flow coverage and real PostgreSQL decoder-to-converter coverage for accepted authentication, current eligibility failures, exact owner scoping, unchanged persistence, bean registration, and the absence of a `SecurityFilterChain`.

### Deviations from specification

- None.

### New decisions

- None. This PR adds only the standard resource-server authentication token/exception types and the specified decoded-JWT conversion boundary; HTTP bearer processing and authorization remain deferred.

### Tests executed

- `./mvnw spotless:check` — passed; 64 Java files were already clean.
- `./mvnw -Dtest=LocalAccessTokenAuthenticationConverterTest,LocalAccessTokenAuthenticationConverterControlFlowTest,LocalAccessTokenDecoderTest,AccessTokenIssuanceServiceTest test` — passed; 15 tests, 0 failures, 0 errors, 0 skipped.
- `./mvnw test` — passed; 95 tests, 0 failures, 0 errors, 0 skipped.
- `./mvnw verify` — passed; 95 tests, 0 failures, 0 errors, 0 skipped, and the application JAR was packaged.
- `git status --short` — inspected; only PR-015 planning, dependency, repository, converter, test, and completion-document files are present.
- `git diff --check` plus no-index checks for all four untracked PR-015 files — passed.

### Follow-up work

- Independent review passed with no `MUST FIX` or `SHOULD FIX` findings; supervisor acceptance/local commit remains pending.
- Bearer-token extraction, `SecurityFilterChain`, HTTP authentication/error delivery, authorization, roles/permissions, owner helpers, persistent key management, refresh rotation/reuse handling, logout/revocation, security events, jobs, and abuse controls remain separate future units.
