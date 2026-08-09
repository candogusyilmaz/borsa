# PR-011 — Local access-token issuance

Status: **ACTIVE**

## Goal

Add one read-only application workflow that issues a short-lived RS256 access JWT for an existing active refresh session. The signed token identifies only the user, session, token instance, issuer, audience, validity window, and explicit access-token type; it is returned once and is never persisted.

This PR establishes local-development access-token issuance only. It does not expose HTTP login, accept bearer tokens, configure request authorization, create a principal, or rotate a refresh session.

## Source documents

- `AGENTS.md`
- `docs/implementation/STATE.md`
- `docs/implementation/PR-010-initial-refresh-session-issuance.md` — accepted active-session and opaque refresh-token behavior
- `docs/review/backend-master-plan.md`
  - R0 disposable local signing-key flow
  - R1 item 5: local login/session behavior
  - dependency, pull-request, security, and testing rules
- `docs/review/backend-audit.md`
  - SEC-003: distinguish access tokens from refresh sessions
  - SEC-004: disabled-account rejection
- `docs/review/mobile-api-readiness.md`
  - short-lived access JWT with explicit type, audience, issuer, session/device ID, and key ID
- `docs/engineering/coding-standards.md`
  - dependency management, configuration properties, injected clocks/IDs, security, and integration testing
- Spring Security 7.0 documentation for `NimbusJwtEncoder.withKeyPair(...)`, its RSA builder, `JwsHeader`, `JwtClaimsSet`, `JwtEncoderParameters`, and test-only `NimbusJwtDecoder.withPublicKey(...)`

`docs/review/accounting-contract.md` is not required because this PR introduces no financial behavior.

## Starting state

Treat local commit `c3c9fd6` as authoritative.

Already implemented and not to be redesigned here:

- local credential verification returns an enabled user's UUID;
- initial refresh-session issuance creates one active `DeviceSession`, rechecks account eligibility, returns the raw refresh token once, and persists only its hash;
- the initial session UUID is also its family UUID;
- `DeviceSession` exposes mapped session/user/timestamp/revocation state within application transactions;
- injected `Clock` and `IdGenerator` abstractions exist;
- the project currently depends on `spring-security-crypto` only and has no JOSE/JWT support;
- no HTTP login endpoint, access-token issuer/decoder, bearer filter, principal, authorization rule, or stable deployment signing key exists.

R1 local authentication is for development. A disposable in-memory RSA key pair is sufficient for this issuance-only unit and avoids committing a private key. Persistent externally managed deployment keys remain required before deployment and are not designed here.

## Scope

### 1. Add only the JOSE dependency needed for issuance

Add the Boot-managed dependency `org.springframework.security:spring-security-oauth2-jose` without a version.

Do not add the resource-server/client/authorization-server starters, Spring Security test, another JWT library, Bouncy Castle, or a general security starter.

### 2. Add access-token configuration properties

Add one immutable constructor-bound configuration-properties record:

```text
dev.canverse.stocks.identity.configuration.AccessTokenProperties
prefix: stocks.identity.access-token
issuer:   https://canverse.dev
audience: canverse-api
lifetime: 15m
keyId:    local-ephemeral
```

Use Spring Boot `@DefaultValue` constructor binding and explicitly enable this properties type from the access-token configuration.

Require a non-null absolute issuer URI, non-blank audience, non-null strictly positive lifetime, and non-blank key ID. Do not add refresh-session, cookie, CORS, role, permission, provider, Google, or abuse-control properties. Do not add checked-in key paths or key-material properties in this local-ephemeral unit.

### 3. Configure one disposable local RSA signer

Add one identity configuration class:

```text
dev.canverse.stocks.identity.configuration.LocalAccessTokenConfiguration
```

It must:

1. generate one 2048-bit RSA `KeyPair` at application-context startup using the JDK `KeyPairGenerator`;
2. retain that one key pair as a singleton bean for the application-context lifetime;
3. expose one `JwtEncoder` built with `NimbusJwtEncoder.withKeyPair(...)`;
4. explicitly configure `SignatureAlgorithm.RS256`;
5. apply `AccessTokenProperties.keyId` through `jwkPostProcessor(...)`;
6. fail startup if the JDK-mandated RSA algorithm is unexpectedly unavailable;
7. never log, serialize, persist, or write key material to disk.

The key pair is deliberately ephemeral: every restart invalidates tokens issued by the prior process. Name the configuration and documentation so this local-development limitation is unmistakable. Do not implement PEM loading, JWK-set publication, rotation, multiple keys, KMS/HSM integration, certificates, or deployment key management.

### 4. Add one access-token result

Add one immutable application record:

```text
dev.canverse.stocks.identity.application.IssuedAccessToken
components: accessToken, expiresAt
```

Both components are required. Do not include the refresh token, stored hash, user email, roles, permissions, or entity references.

### 5. Add one access-token issuance service

Create one concrete Spring service:

```text
dev.canverse.stocks.identity.application.AccessTokenIssuanceService
```

Expose exactly one public method:

```java
IssuedAccessToken issue(UUID sessionId)
```

Use constructor injection for `DeviceSessionRepository`, `JwtEncoder`, `AccessTokenProperties`, `Clock`, and `IdGenerator`.

Use a read-only transaction. The method has no Bean Validation annotations; a null `sessionId` is an internal caller contract violation and fails through `Objects.requireNonNull`.

Within the transaction:

1. capture the injected clock instant exactly once as full-precision `observedAt` and derive `issuedAt = observedAt.truncatedTo(ChronoUnit.SECONDS)` for JWT NumericDate representation;
2. load the session once by primary key;
3. reject with the existing parameterless `IdentityErrorCode.INVALID_CREDENTIALS` when the session is missing, revoked, expired at or before full-precision `observedAt`, or belongs to a disabled user;
4. derive subject and session claims from the loaded persisted graph;
5. compute the full-precision expiry candidate as the earlier of `observedAt + configured access lifetime` and the refresh session's `expiresAt`, then truncate it down to whole seconds as `expiresAt`;
6. reject with parameterless `INVALID_CREDENTIALS` before token-ID generation when the truncated `expiresAt` is not strictly after `issuedAt`, because no positive JWT validity window is representable;
7. generate one application `jti` through `IdGenerator` only after all eligibility/representability checks succeed;
8. build and sign exactly one JWT;
9. return its compact value and the exact whole-second expiry used in the claims.

The JWS header must contain exactly:

```text
alg: RS256
kid: configured keyId
typ: access
```

The JWT claims must contain exactly:

```text
iss: configured issuer string
sub: user UUID string
aud: configured audience as the RFC 7519-permitted singleton string emitted by stock Nimbus 10.9
iat: issuedAt
nbf: issuedAt
exp: computed expiresAt
jti: generated UUID string
sid: session UUID string
```

Do not add email, provider, password, refresh-token data, device label, family ID, roles, permissions, authorities, locale, IP, user agent, or arbitrary claims.

Do not persist the JWT or `jti`, mutate `last_used_at`, or update any account/session row. This service issues a signed credential; it does not authenticate an incoming request. A future bearer boundary must independently validate signature/type/issuer/audience/time claims and recheck disabled/session state.

## Explicit non-goals

- `POST /api/v1/auth/login` or controller/request/response DTO/cookie/header changes;
- bearer parsing, a production `JwtDecoder`, resource-server configuration, `SecurityFilterChain`, request authorization, principal, `Authentication`, or `SecurityContext`;
- refresh lookup, rotation/reuse detection, logout, revocation, or session listing/deletion;
- stable/persistent signing keys, PEM/JKS/JWK loading, JWK endpoints, key rotation, KMS/HSM, or deployment secrets;
- refresh JWTs or another refresh-token representation;
- roles, permissions, scopes, authorities, households, or owner helpers;
- access-token persistence, revocation lists, introspection, or `jti` storage;
- security events, throttling, delay, lockout, CAPTCHA, password recovery, or notifications;
- Google/OIDC/OAuth login;
- schema, migration, constraint, index, JPA mapping, entity-mutation, or repository-query changes;
- registration, credential-verification, or refresh-session issuance changes;
- frontend, reference, ledger, or financial work;
- remote Git operations or Git history rewrites.

## Database changes

None. Do not add or modify a Flyway migration, table, column, constraint, index, JPA mapping, or repository method.

## Application changes

Expected production-code surface:

```text
pom.xml                                                       # one Boot-managed JOSE dependency
src/main/java/dev/canverse/stocks/identity/
├── application/
│   ├── AccessTokenIssuanceService.java
│   └── IssuedAccessToken.java
└── configuration/
    ├── AccessTokenProperties.java
    └── LocalAccessTokenConfiguration.java
```

No existing production Java file should need modification.

Expected test surface:

```text
src/test/java/dev/canverse/stocks/identity/
├── AccessTokenIssuanceServiceTest.java
└── AccessTokenPropertiesTest.java
```

Do not create reusable JWT/security test infrastructure for this one workflow.

## API contract

None. This PR adds no route and no production decoder. `IssuedAccessToken` is an application result for a later login/refresh delivery boundary, not yet a public HTTP response.

## Business invariants

- Only an existing, non-revoked, non-expired session owned by an enabled user can receive an access token.
- The access JWT is distinguishable from a refresh token through `typ=access`; refresh tokens remain opaque database-backed secrets.
- The JWT is signed with RS256 and carries the configured key ID, issuer, and audience.
- Subject and session identity come only from the persisted session graph.
- The access token never outlives its backing refresh-session generation.
- Token IDs and time claims come only from injected abstractions; signed and returned NumericDate values use explicit whole-second precision without outliving full-precision session expiry.
- Issuance is read-only and neither compact JWT nor `jti` is persisted.
- The ephemeral local key never leaves process memory and intentionally invalidates prior tokens on restart.

## Required tests

### Pure/configuration

Add focused no-database coverage proving:

1. absent properties bind to the exact issuer, audience, 15-minute lifetime, and key-ID defaults;
2. relative/null issuers, blank audience, null/zero/negative lifetime, and blank key ID are rejected;
3. local configuration creates one RSA key pair with at least 2048 modulus bits and one encoder using that key ID and RS256;
4. no private-key bytes/string are logged or written by the test.

`ApplicationContextRunner` is appropriate for the property and singleton-bean contract.

### PostgreSQL/Testcontainers

Add focused service integration coverage using the real Spring context, migrated PostgreSQL, accepted registration/session services, real ephemeral RSA encoder, fixed `Clock`, deterministic `IdGenerator`, and short test lifetimes. Do not use H2 or mock repositories/encoder.

Independently verify successful compact tokens with a test-only `NimbusJwtDecoder.withPublicKey(...)` built from the generated key-pair bean. A production `JwtDecoder` bean must not be added.

Keep this test deterministic across calendar time: the test-only decoder may replace its default timestamp validator with a success validator so decoding proves the cryptographic signature independently, while the suite separately asserts every exact `iat`, `nbf`, and `exp` value against the fixed injected clock. Do not use the machine clock as a test oracle.

1. **Active session receives one valid access token**
   - register a user and issue its initial refresh session through accepted services;
   - issue and independently verify the access token signature;
   - inspect the decoded raw payload JSON, not Nimbus's normalized claim map, and assert `aud` is exactly the configured singleton JSON string;
   - assert exact `alg`, `kid`, `typ`, `iss`, `aud`, `sub`, `iat`, `nbf`, `exp`, `jti`, and `sid` values/types;
   - assert returned expiry equals `exp`;
   - prove no user/session field or row count changes.

2. **Each issuance uses a new token instance ID**
   - issue twice for one active session with deterministic successive IDs;
   - prove `jti` claims and compact tokens differ while subject/session claims remain stable;
   - prove issuance remains read-only.

3. **Access expiry cannot exceed refresh-session expiry**
   - make an otherwise active session expire sooner than the configured access lifetime through fixture SQL;
   - use fractional clock/session instants and assert signed and returned expiry equal the session expiry truncated down to whole seconds;
   - leave persisted state unchanged.

4. **Ineligible sessions fail closed before token-ID generation**
   - cover missing, revoked, expired, disabled-user, and near-expiry sessions whose remaining sub-second lifetime cannot produce `exp > iat` after NumericDate normalization;
   - each throws parameterless `INVALID_CREDENTIALS`;
   - no token is returned, no `jti` is consumed, no row changes, and no detail appears in exceptions.

Also prove one valid token rejects verification with an unrelated RSA public key. Use explicit cleanup and no test-level transaction.

### HTTP/security

The independently decoded PostgreSQL service suite is the required security integration coverage for this issuance-only PR. No MockMvc/filter-chain test is required because no incoming HTTP authentication boundary is added.

All accepted registration, credential, refresh-session, migration, mapping, error-contract, and HTTP tests must remain green.

## Acceptance criteria

1. `pom.xml` adds only Boot-managed `spring-security-oauth2-jose` and no version/security starter/JWT alternative.
2. One immutable property group supplies validated exact defaults for issuer, audience, 15-minute lifetime, and local-ephemeral key ID.
3. One local configuration creates a single in-memory 2048-bit RSA key pair and RS256 `JwtEncoder` with configured key ID; no key material is persisted/logged.
4. Exactly one new read-only service exposes `IssuedAccessToken issue(UUID sessionId)`.
5. Missing, revoked, expired, and disabled-user sessions fail with parameterless `INVALID_CREDENTIALS` before `jti` generation and without writes.
6. Successful issuance derives identities from the loaded entity, uses one injected time and one generated `jti`, and returns compact token plus exact expiry.
7. Headers contain exactly `alg=RS256`, configured `kid`, `typ=access`; claims contain only exact `iss`, `sub`, singleton-string `aud`, `iat`, `nbf`, `exp`, `jti`, `sid` values, and tests inspect the raw payload representation.
8. `iat`/`nbf`, signed `exp`, and returned expiry use whole-second NumericDate precision; expiry is the truncated-down earlier of configured full-precision expiry and refresh-session expiry, and a non-positive representable window fails closed before `jti` generation.
9. Issuance does not persist JWT/`jti`, mutate rows, or expose key/token/session detail through logs/exceptions.
10. Successful tokens verify with generated public key and reject an unrelated RSA public key.
11. No migration, mapping, entity, repository, controller, route, decoder/filter/principal, refresh-rotation, stable-key, event, abuse-control, provider, frontend, or financial change is added.
12. Required tests and all prior tests pass.
13. `./mvnw spotless:check`, focused suite, `./mvnw test`, and `./mvnw verify` pass.
14. `git diff --check` passes and complete diff contains only PR-011 files.
15. Completion Record, `STATE.md`, and `progress-report.md` are accurate; `CURRENT.md` remains on PR-011 through review.
16. Implementation and review agents perform no Git mutation.

## Documentation completion

Before completion, fill this Completion Record, update `STATE.md` and `progress-report.md`, and keep `CURRENT.md` on PR-011 until supervisor commit.

Do not mark HTTP login, bearer authentication, principal/filter chain, persistent keys, refresh rotation, logout/revocation, events, or abuse controls implemented.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=AccessTokenIssuanceServiceTest,AccessTokenPropertiesTest,RefreshSessionIssuanceServiceTest,RefreshSessionPropertiesTest,SecureRefreshTokenGeneratorTest,LocalPasswordAuthenticationServiceTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required.

## Completion record

Fill this before marking PR-011 complete.

### Starting commit

- `c3c9fd6` (`issue initial opaque refresh sessions`).

### Implemented

- Added the Boot-managed Spring Security OAuth2 JOSE dependency without a local version override.
- Added fail-fast immutable access-token properties with the specified issuer, audience, lifetime, and key-ID defaults.
- Added one startup-local 2048-bit RSA key pair and one singleton RS256 `JwtEncoder` carrying the configured `kid`.
- Added immutable access-token issuance output and a read-only issuance service that performs one session lookup, observes full-precision time once, emits whole-second NumericDate values, rejects ineligible or non-representable windows before `jti` generation, caps expiry without outliving the backing session, and emits exactly the specified header and claims.
- Added pure configuration/key tests and PostgreSQL/Testcontainers integration tests for raw singleton-string `aud`, exact token content, signature verification, fractional time normalization, fresh token IDs, expiry capping, near-expiry fail-closed behavior, and persistence immutability.
- Updated implementation state and progress documentation while keeping `CURRENT.md` on PR-011.

### Deviations from specification

- None.

### New decisions

- None. The amended specification explicitly accepts Nimbus 10.9's RFC-valid raw singleton-string `aud` representation and defines whole-second NumericDate normalization; neither changes the application-only issuance boundary or establishes production key management or bearer authentication.

### Tests executed

- `./mvnw -Dtest=AccessTokenIssuanceServiceTest,AccessTokenPropertiesTest,RefreshSessionIssuanceServiceTest,RefreshSessionPropertiesTest,SecureRefreshTokenGeneratorTest,LocalPasswordAuthenticationServiceTest test` — passed, 17 tests.
- `./mvnw spotless:check` — passed after reviewer corrections.
- `./mvnw test` — passed after reviewer corrections, 73 tests.
- `./mvnw verify` — passed after reviewer corrections, including 73 tests and packaging.
- `git status --short` — inspected after reviewer corrections; only the active PR-011 planning, implementation, test, and completion-document files are present.
- `git diff --check` — passed after reviewer corrections.

### Follow-up work

- HTTP login and token delivery, a production decoder/resource server and authenticated principal/filter chain, persistent signing-key management, refresh lookup/rotation/reuse detection, logout/revocation/session management, security events, and abuse controls remain deferred.
