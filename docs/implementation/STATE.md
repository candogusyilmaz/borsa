# Backend rewrite implementation state

Last updated: 2026-08-15

## Current technology

- Java 25
- Spring Boot 4.1.x
- PostgreSQL
- Flyway owns DDL
- Hibernate/JPA uses schema validation
- Testcontainers for PostgreSQL integration tests
- One Maven project / modular monolith

## Git workflow

- Working branch: `rewrite-agent-batch`
- The user owns commits and Git operations.
- Agents leave implementation changes uncommitted unless explicitly instructed otherwise.

## Completed implementation units

### PR-001 — Modern backend foundation

Status: **COMPLETED**

Established:

- Java 25 / Spring Boot 4 foundation
- legacy backend removed
- frontend preserved
- `Clock` abstraction
- `IdGenerator` abstraction
- Problem Details foundation
- Testcontainers smoke test
- Flyway configured for the replacement backend

### PR-002 — V1 foundation database

Status: **COMPLETED**

Specification:

`PR-002-v1-foundation-database.md`

Established:

- eight application schemas: `identity`, `reference`, `ledger`, `data`, `money`, `analysis`, `asset`, `platform`
- five V1 foundation tables: `identity.user_account`, `identity.auth_identity`, `identity.device_session`, `platform.security_event`, `platform.job`
- Flyway-owned primary keys, foreign keys, unique constraints, checks, defaults, and indexes
- PostgreSQL/Testcontainers migration and constraint coverage

### PR-003 — Identity and platform JPA entity mappings

Status: **COMPLETED**

Accepted commit: `97a06b7`

Specification:

`PR-003-identity-jpa-entity-mappings.md`

Established:

- `dev.canverse.stocks.identity.domain.UserAccount` — maps `identity.user_account`
- `dev.canverse.stocks.identity.infrastructure.UserAccountRepository`
- `dev.canverse.stocks.identity.domain.AuthIdentity` — maps `identity.auth_identity`; lazy mandatory FK to `UserAccount`
- `dev.canverse.stocks.identity.infrastructure.AuthIdentityRepository`
- `dev.canverse.stocks.identity.domain.DeviceSession` — maps `identity.device_session`; lazy mandatory FK to `UserAccount`; `replacedBySessionId` as plain `UUID`
- `dev.canverse.stocks.identity.infrastructure.DeviceSessionRepository`
- `dev.canverse.stocks.platform.domain.SecurityEvent` — maps `platform.security_event`; nullable lazy FK to `UserAccount`; `details` as `@JdbcTypeCode(SqlTypes.JSON) String`
- `dev.canverse.stocks.platform.infrastructure.SecurityEventRepository`
- `dev.canverse.stocks.platform.domain.Job` — maps `platform.job`; nullable lazy FK to `UserAccount`; `payload` as `@JdbcTypeCode(SqlTypes.JSON) String`
- `dev.canverse.stocks.platform.infrastructure.JobRepository`
- `EntityMappingTest` — five Testcontainers PostgreSQL mapping/load tests; Hibernate `ddl-auto: validate` passes against V1

### PR-004 — V1 mapping package alignment

Status: **COMPLETED**

Accepted commit: `eed342e`

Specification:

`PR-004-v1-mapping-package-alignment.md`

Established:

- five V1 entities reside in capability `domain` packages;
- five Spring Data repositories reside in capability `infrastructure` packages;
- the entities use the accepted Lombok getter/protected-constructor convention;
- Maven explicitly registers Lombok as an annotation processor for clean Java 25 builds;
- V1 mappings, repository behavior, and database/API behavior remain unchanged.

### PR-005 - Atomic local account registration

Status: **COMPLETED**

Established:

- transactional local account registration with delegating password hashing;
- application-generated IDs and injected-clock timestamps;
- safe duplicate-email exception semantics and atomic rollback of the two identity rows.

### PR-006 - Stable RFC 9457 error handling

Status: **COMPLETED**

Established:

- structured `ErrorCode`, `CommonErrorCode` and strict-parameter `AppException` contracts;
- one global `GlobalExceptionHandler` producing RFC 9457 `ProblemDetail` responses;
- stable `code`, `key`, `traceId`, and injected-clock `timestamp` extension fields;
- server-owned `X-Trace-Id` request correlation through `RequestTraceFilter`;
- safe 5xx non-leakage and stable persistence/framework fallback mappings;
- one `VALIDATION_FAILED` / `params.errors[]` shape with validated application keys;
- Spring Security-specific error integration remains deferred.

### PR-007 — HTTP local account registration

Status: **COMPLETED**

Established:

- versioned `POST /api/v1/auth/register` JSON endpoint;
- HTTP validation matching the accepted local-registration structural rules;
- HTTP 201 responses containing only the opaque user UUID;
- duplicate, validation, and malformed-request responses through the shared PR-006 ProblemDetail contract;
- PostgreSQL-backed MockMvc coverage proving committed two-row registration, no-write failures, trace correlation, and password non-leakage.
- identity input/output records kept outside the controller in `identity.input` and `identity.output`.

### PR-008 - Local password credential verification

Status: **COMPLETED**

Accepted commit: `61fe8a6`

Established:

- parameterless `IdentityErrorCode.INVALID_CREDENTIALS` with HTTP 401 metadata and the derived `error.identity.invalid_credentials` key;
- one `LOCAL` identity lookup using the normalized email and the existing provider-subject uniqueness constraint;
- read-only local credential verification returning only the enabled user's UUID; service-level `@Validated` and parameter validation annotations are intentionally absent, with structural validation deferred to the future HTTP login boundary;
- uniform invalid-credential failures for unknown email, wrong password, null local hash, and disabled account;
- one password match for every structurally valid attempt, including a service-owned dummy hash for missing/null hashes;
- pure fallback and PostgreSQL/Testcontainers coverage proving timing-control flow and unchanged persisted identity state;
- no HTTP login, principal, token, session, security-event, abuse-control, schema, migration, or entity-mapping behavior.

### PR-009 - Automated-batch state reconciliation

Status: **COMPLETED**

Accepted commit: `82f6a34`

Established:

- repository implementation documents reconciled with the accepted PR-008 commit;
- automated local-commit batches use `git commit --no-verify` only after required verification and independent review;
- no production, test, migration, dependency, configuration, frontend, or backend-behavior change.

### PR-010 - Initial opaque refresh-session issuance

Status: **COMPLETED**

Accepted commit: `c3c9fd6`

Established:

- immutable `stocks.identity.refresh-session.lifetime` configuration with a positive 30-day default;
- 32-byte `SecureRandom` refresh tokens encoded as unpadded Base64url and deterministically hashed with SHA-256 for storage;
- one initial `DeviceSession` construction path whose session ID is also its token-family ID and whose usage, revocation, and replacement state begins null;
- transactional session issuance that rechecks user eligibility, persists only the token hash, and returns the raw token once with its session ID and expiry;
- pure configuration/token coverage and PostgreSQL/Testcontainers coverage for initial issuance, optional labels, independent device families, disabled/missing users, and raw-token non-persistence;
- no HTTP login, access token, refresh rotation, logout/revocation, principal/filter-chain, security-event, abuse-control, schema, migration, dependency, or frontend behavior.

### PR-011 - Local access-token issuance

Status: **COMPLETED**

Accepted commit: `6aa57b6`

Established:

- immutable `stocks.identity.access-token` issuer, audience, lifetime, and key-ID configuration with exact local defaults and fail-fast validation;
- one startup-local 2048-bit RSA key pair and one Boot-managed Spring Security JOSE encoder restricted to RS256, with no key persistence or exposure;
- read-only access-token issuance for an existing eligible device session, with exact `iss`, `sub`, singleton-string `aud`, `iat`, `nbf`, `exp`, `jti`, and `sid` claims and expiry capped by the session expiry;
- explicit NumericDate handling that observes the clock once at full precision, truncates signed/returned time claims down to whole seconds, and rejects a non-positive representable window before token-instance ID generation;
- uniform parameterless invalid-credential rejection for missing, revoked, expired, disabled-user, and unrepresentably near-expiry sessions before token-instance ID generation;
- pure configuration/key tests and PostgreSQL/Testcontainers coverage for raw signed-token JSON, signature verification, fractional time normalization, fresh `jti` values, expiry capping, fail-closed eligibility, and unchanged persisted state;
- no HTTP login, production JWT decoder/resource server, bearer filter chain, principal, persistent key, refresh rotation, schema, migration, repository-query, or frontend behavior.

### PR-012 - Atomic local login orchestration

Status: **COMPLETED**

Accepted commit: `5570f8d`

Established:

- one ordinary write transaction that composes accepted local credential verification, initial refresh-session issuance, and access-token issuance in that order without duplicating their logic;
- one immutable application result containing only the issued session identifier, raw access/refresh tokens, and their exact expiries;
- null email/password internal-caller enforcement and unchanged nullable device-label pass-through, with no service-level Bean Validation;
- PostgreSQL/Testcontainers proof that successful login commits one exact composed session/result, invalid credentials short-circuit before ID/JWT issuance, and an unchecked JWT-encoding failure rolls the flushed session back unchanged;
- no HTTP/API, bearer validation, refresh rotation, schema, migration, configuration, dependency, or frontend change.

### PR-013 - Opaque refresh-session authentication

Status: **COMPLETED**

Accepted commit: `7bb7c40`

Established:

- exact-input deterministic SHA-256 Base64url hashing of presented opaque refresh tokens through the accepted concrete generator, with generation delegating to the same hash path;
- one Spring Data derived lookup by unique stored refresh-token hash and one read-only authentication workflow returning only an eligible session UUID;
- one clock observation, hash operation, and lookup per non-null attempt, with null rejected before collaborator work;
- uniform parameterless invalid-credential rejection for unknown, revoked, expired-at-or-before-now, and disabled-user sessions with no writes;
- pure generator/control-flow tests and PostgreSQL/Testcontainers coverage proving exact active-session resolution, safe indistinguishable failures, expiry equality rejection, and unchanged persisted state;
- no HTTP delivery, rotation/reuse response, logout/revocation, schema, dependency, configuration, or frontend change.

### PR-014 - Local access-token decoding

Status: **COMPLETED**

Accepted commit: `4621473`

Established:

- one production decoder using the existing startup-local RSA public key and RS256 only;
- one strict local validator for the accepted access-token header, claim, identifier, and validity-window envelope using the injected clock and no implicit skew;
- focused real-encoder/decoder coverage for valid local tokens, untrusted key/algorithm rejection, exact envelope failures, and safe validation errors;
- no HTTP bearer boundary, principal, user/session eligibility lookup, persistence, dependency, key-management, refresh behavior, or frontend change.

Independent review passed after correcting the stale PR-013 specification status and accepted-commit record; no `MUST FIX` or `SHOULD FIX` findings remain.

### PR-015 - Database-backed access-token authentication

Status: **COMPLETED**

Accepted commit: `3d86ff2`

Implemented scope:

- one direct Boot-managed Spring Security resource-server library module supplies standard JWT authentication token/exception types without installing HTTP security;
- one exact session/user owner-scoped repository lookup and one read-only decoded-JWT converter recheck current session/user eligibility;
- successful conversion returns a minimal authenticated `JwtAuthenticationToken` named by canonical user UUID with no authorities;
- pure collaborator and PostgreSQL/Testcontainers coverage proves claim-validation order, one scoped query, current eligibility, cross-user rejection, safe failures, unchanged persisted state, and no `SecurityFilterChain`;
- Spotless, the 15-test focused suite, the full 95-test suite, and `verify`/package pass with no failures, errors, or skipped tests;
- no security filter chain, bearer extraction, HTTP/API, role/permission/owner helper, mutation, schema, key-management, refresh behavior, or frontend change.

Independent review passed with no `MUST FIX` or `SHOULD FIX` findings.

### PR-016 - HTTP bearer authentication boundary

Status: **COMPLETED**

Accepted commit: `9fcbb69`

Established:

- Boot-managed servlet security and test-scoped MVC test starters support one servlet-only stateless Spring Security filter chain limited to `/api/v1/**`;
- the existing registration POST remains public while every other matched request requires the accepted local JWT decoder and current database-backed converter;
- the highest-precedence trace filter and one authentication entry point map missing or unusable bearer credentials into the existing trace-correlated `INVALID_CREDENTIALS` Problem Detail with the exact `Bearer` challenge;
- real-filter PostgreSQL HTTP/security coverage proves exact chain scope, committed public registration, safe uniform failures, valid authority-free authentication, unchanged persistence, and no session persistence;
- the one superseded PR-015 no-chain assertion now retains its converter-singleton proof alongside exactly one production chain;
- Spotless, the 16-test focused suite, the full 99-test suite, and `verify`/package pass with no failures, errors, or skipped tests;
- no production endpoint, login/refresh transport, authorization/owner helper, refresh mutation, schema, key-management, or frontend change.

Independent correction-cycle-1 whole-diff review passed with no remaining `MUST FIX` or `SHOULD FIX` findings. The documentation correction removed or reworded four stale pre-PR-016 claims so current state consistently records public registration under the installed chain, decoder-to-HTTP wiring, and the still-deferred login/refresh-delivery and authorization work.

### PR-017 - HTTP local login and explicit token delivery

Status: **COMPLETE IN USER-OWNED COMMIT `7f55288`**

Starting commit: `9fcbb69`

Established:

- one public `POST /api/v1/auth/login` endpoint delegates to the accepted atomic local-login workflow;
- every request explicitly selects `RESPONSE_BODY` or `HTTP_ONLY_COOKIE`, with no inferred delivery default;
- response-body success returns the exact access/session metadata and raw refresh token once, while cookie success omits that JSON property and emits one host-only `refresh-token` cookie with the fixed secure, HTTP-only, `SameSite=Strict`, `/api/v1/auth` policy;
- successful responses use `Cache-Control: no-store` and `Pragma: no-cache`, and the existing stateless API chain permits only the exact registration and login POSTs;
- validation, malformed-body, unknown-enum, credential-failure, trace-correlation, token/session binding, hash-only persistence, and no-servlet-session behavior are covered by the new real-filter six-case HTTP suite;
- no migration, schema, entity, repository, application-service, token-service, dependency, runtime-property, frontend, or financial behavior changed.

Spotless, the 29-test focused suite, the full 105-test suite, and `verify` pass with no failures, errors, or skipped tests. Independent correction-cycle review fixed the exact cookie `Expires` value and passed with no remaining `MUST FIX` or `SHOULD FIX` findings. The user-owned accepting commit is `7f55288`.

## Current database

Migration version: `V1`

Schemas:

- `identity`
- `reference`
- `ledger`
- `data`
- `money`
- `analysis`
- `asset`
- `platform`

Tables:

### identity

- `user_account`
- `auth_identity`
- `device_session`

### platform

- `security_event`
- `job`

### currently empty schemas

- `reference`
- `ledger`
- `data`
- `money`
- `analysis`
- `asset`

## Current application state

Implemented production foundation and V1 mappings in the authoritative capability sub-packages:

```text
dev.canverse.stocks
├── ServerApplication
├── identity
│   ├── domain
│   │   ├── UserAccount
│   │   ├── AuthIdentity
│   │   └── DeviceSession
│   ├── error
│   │   └── IdentityErrorCode
│   ├── input
│   │   └── RegistrationRequest
│   ├── infrastructure
│   │   ├── UserAccountRepository
│   │   ├── AuthIdentityRepository
│   │   └── DeviceSessionRepository
│   ├── output
│   │   └── RegistrationResponse
│   └── web
│       └── LocalAccountRegistrationController
└── platform
    ├── domain
    │   ├── SecurityEvent
    │   └── Job
    ├── infrastructure
    │   ├── SecurityEventRepository
    │   └── JobRepository
    ├── config
    │   └── TimeConfiguration
    ├── id
    │   ├── IdGenerator
    │   └── UuidIdGenerator
    ├── error
    │   ├── AppException
    │   ├── CommonErrorCode
    │   ├── ErrorCode
    │   └── GlobalExceptionHandler
    └── web
        └── trace
            └── RequestTraceFilter
```

PR-017 additionally adds `identity.input.LocalLoginRequest`, `identity.input.RefreshTokenDelivery`, `identity.output.LocalLoginResponse`, and `identity.web.LocalLoginController`. PR-018 adds refresh rotation application/result contracts, explicit refresh request/response records, repository projection and owner-lock queries, `DeviceSession` rotation/reuse behavior, the shared `RefreshTokenCookieHeader`, and `LocalRefreshController`.

## Important decisions discovered during implementation

- Flyway SQL is the only DDL authority.
- PostgreSQL integrity constraints remain authoritative even when JPA mappings exist.
- For PostgreSQL Testcontainers JDBC fixtures targeting `timestamptz`, use `OffsetDateTime` parameters rather than raw `Instant` values with `JdbcTemplate`.
- JPA entities may use `Instant` for persisted timestamp fields.
- PR-003 is read/mapping-oriented. It does not establish entity mutation APIs or JPA write semantics.
- JSON fields are initially mapped as opaque JSON strings with Hibernate JSON JDBC typing; final write-side representation is deferred until a real write use case requires it.
- Lombok is an accepted project dependency. Touched JPA entities use `@Getter` and `@NoArgsConstructor(access = AccessLevel.PROTECTED)`; Spring components use `@RequiredArgsConstructor` where appropriate.
- Capability code uses the fixed `domain`, `application`, `infrastructure`, `configuration`, `input`, `output`, and `web` sub-packages from `coding-standards.md`, omitting unused sub-packages. Directional `input`/`output` packages contain HTTP/API request and response records only.
- `LocalAccountRegistrationService` is the first identity write workflow: one transaction creates a `user_account` and its `LOCAL` `auth_identity`, and an identity flush failure rolls both rows back.
- Duplicate-email registration failures use `IdentityErrorCode.EMAIL_ALREADY_REGISTERED` directly with the shared `AppException` contract; no identity-specific exception wrapper is retained.
- HTTP errors use one RFC 9457 `ProblemDetail` contract with stable application fields; internal 5xx details, causes, SQL, and exception messages are not serialized.
- Request correlation IDs are generated by the application `IdGenerator`, stored on the request, and returned in `X-Trace-Id` and `traceId`; inbound headers are not authoritative.
- Bean Validation, method validation, and framework validation failures use `VALIDATION_FAILED` with safe, shape-validated validation keys.
- The versioned local-registration endpoint remains intentionally public under the PR-016 security chain: it delegates once to the PR-005 transactional workflow and returns only the new user ID. PR-017 adds the separate initial-login boundary, and PR-018 adds refresh rotation and lifecycle behavior; logout/session management remains deferred.
- HTTP registration validation occurs before the service workflow, while duplicate and malformed-request failures retain the shared PR-006 ProblemDetail and trace-correlation behavior.
- Local credential verification is application-only and read-only: it normalizes the lookup email with `Locale.ROOT`, performs one password match with a constructed dummy-hash fallback when needed, rejects disabled accounts uniformly, and returns only the associated user UUID. Structural validation is deferred to the future HTTP login boundary; the service has no validation annotations.
- Initial refresh-session issuance is application-only: it rechecks user eligibility, uses injected clock/ID generation and a positive configured lifetime, returns one high-entropy raw token, and stores only its SHA-256 Base64url hash in the existing `device_session` table.
- Local access-token issuance is application-only and read-only: one disposable RSA key pair is generated at startup, RS256 tokens are issued only for an eligible existing session, raw `aud` uses Nimbus's RFC-valid singleton string, NumericDate values are truncated down to whole seconds without exceeding full-precision session expiry, and neither compact tokens nor token-instance IDs are persisted.
- Local login orchestration is application-only: its outer default-`REQUIRED` write transaction composes the three accepted service beans so a downstream unchecked access-token failure rolls back the already-flushed session insert, while non-transactional token/ID generation may still be consumed.
- Opaque refresh-session authentication is application-only and read-only: it hashes the exact presented credential, performs one unique stored-hash lookup, and returns only an unrevoked, unexpired, enabled-user session UUID; the observation neither mutates session state nor grants permission to rotate.
- Local access-token decoding retains its accepted strict envelope semantics: one decoder reuses the startup-local RSA public key under RS256 and one package-local validator requires the exact configured access-token header, issuer, singleton audience, canonical UUID identifiers, raw whole-second NumericDates, and no-skew validity window while returning one safe constant `invalid_token` result for repository-owned envelope failures; PR-016 wires that decoder into the servlet bearer chain.
- HTTP bearer authentication now has one servlet-only stateless `/api/v1/**` chain: the exact registration and login POSTs are public, all other matched requests use the accepted decoder and database-backed authority-free converter, request tracing precedes security, and expected authentication failures share the existing safe `INVALID_CREDENTIALS` Problem Detail.
- Initial local login now has one thin HTTP boundary over the accepted atomic workflow. The caller explicitly selects native response-body or same-site browser cookie delivery; the cookie is host-only, secure, HTTP-only, `SameSite=Strict`, scoped to `/api/v1/auth`, and never includes a domain. PR-018 adds exact single-channel refresh consumption and rotation; logout, CORS, and CSRF policy remain deferred.
- Refresh rotation is one write transaction: it hashes once, performs a non-entity owner/session projection lookup, locks the owning `user_account` pessimistically, reloads the generation after the lock, observes the clock once, and then either returns the uniform rejected outcome or appends one successor with the predecessor access-token state recorded as `ROTATED`.
- The unchanged V1 self-referencing replacement FK is immediate, so the normal rotation transaction consumes and flushes the predecessor, persists and flushes the active successor through JPA, then links and flushes the predecessor before access-token issuance. Any unchecked failure rolls back both row mutations. No migration or constraint change was introduced.
- Reuse of a replaced generation locks the same owner row, revokes the remaining active family generation with `REUSE_DETECTED`, flushes and returns an empty service outcome; the controller raises `INVALID_CREDENTIALS` only after that transaction returns. The fixed absolute family expiry is inherited by every successor.
- `POST /api/v1/auth/refresh` is the only new public route. It accepts JSON only, selects exactly one body/cookie credential, returns no-store/no-cache metadata, uses the shared hardened host-only cookie helper for cookie delivery, and remains inside the single stateless bearer chain with no CORS or general CSRF subsystem.

## Active implementation unit

PR-018 refresh-session rotation, reuse response, and HTTP refresh is the active specification. It is one substantial R1 vertical slice covering owner-row locking, append-oriented generation replacement, committed family revocation when a replaced token is reused, successor-bound access-token issuance, the public JSON-only refresh boundary, explicit response-body/cookie delivery, and transaction/concurrency/security tests.

PR-017's implementation and review are complete in user-owned commit `7f55288`. PR-018 implementation is now present in the working tree and remains the active unit until the supervising user reviews and accepts it. See `CURRENT.md` and `PR-018-refresh-session-rotation-and-http-refresh.md`; do not broaden the active scope.

## Next likely implementation areas

These are planning hints for work after PR-018, not active specifications.

Likely sequence after PR-018:

1. logout/revocation and owner-scoped session management using PR-018's accepted owner-lock discipline;
2. owner/principal helpers and endpoint authorization over the accepted authenticated identity;
3. security events and authentication abuse protection;
4. durable job claim/retry worker or the V2 reference migration.

The exact next behavioral implementation unit must be designed just-in-time from the reconciled committed state.

Do not treat this list as a promise of PR numbering or exact scope.

## Known issues / deferred work

- HTTP bearer authentication is installed for `/api/v1/**`; PR-017 login/delivery and PR-018 refresh rotation/delivery are implemented in the working tree, while endpoint authorization, roles/permissions, owner helpers, logout, explicit revocation, and session listing/deletion remain deferred.
- The rotation implementation preserves the unchanged immediate replacement FK through a transactional JPA sequence that flushes the consumed predecessor before the active successor is inserted, then links the predecessor. This is the main persistence subtlety for review. No migration/schema change was made, and invalid-state, rollback/reuse/concurrency coverage is present.
- Maven explicitly registers Lombok on the annotation-processor path so Lombok-generated entity accessors and constructors compile on Java 25.
- No reference data yet.
- No ledger yet.
- No financial-account model yet.
- The frontend still targets legacy APIs.
- Bank/broker connectivity remains explicitly deferred.
- JPA write semantics for database-defaulted fields are not established yet.
- JPA JSON write semantics are not established yet.

## Resume instructions

To continue planning implementation:

1. read `AGENTS.md`;
2. read this file;
3. read `docs/implementation/CURRENT.md`;
4. read the active PR specification;
5. read `docs/review/backend-master-plan.md`;
6. read `docs/review/accounting-contract.md` when financial behavior is involved;
7. inspect the actual repository state and relevant latest diff;
8. design only the next human-reviewable implementation unit.

Do not require historical chat context to continue the rewrite.
