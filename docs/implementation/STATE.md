# Backend rewrite implementation state

Last updated: 2026-08-19

## Current technology

- Java 25
- Spring Boot 4.1.x
- PostgreSQL
- Flyway owns DDL
- Hibernate/JPA uses schema validation
- Testcontainers for PostgreSQL integration tests
- One Maven project / modular monolith

## Current backend standardization checkpoint

Date: 2026-08-16.

The active PR pointer remained PR-021; no financial-account or ledger implementation was part of this prior checkpoint. The current working tree standardizes the accepted identity/reference/platform backend while preserving the `/api/v1/**` routes and existing HTTP contracts:

- Bean Validation is an HTTP-controller concern. Request records retain structural and nested constraint metadata; controllers invoke any non-structural request-record validation; application services no longer use `@Validated`, service-method `@Valid`, or repeated request validation.
- Local JWT decoding composes Spring Security issuer, audience and zero-skew timestamp validators with the application-specific header, canonical-claim, lexical NumericDate, strict-boundary and lifetime checks.
- Micrometer Tracing uses the Boot-managed OpenTelemetry bridge and W3C propagation. Native trace context drives logging and MDC; the server-owned UUID `X-Trace-Id` and Problem Detail `traceId` remain the public compatibility correlation values.
- Persistence error mapping is global: the static platform `DatabaseConstraintRegistry` owns the small explicit identity/reference constraint table, while services let integrity and optimistic-lock exceptions cross the transaction boundary unchanged. Unknown persistence failures remain safe 500 responses.
- Meaningful application criteria use capability-owned records under `application/model`; HTTP records use `web/request` and `web/response`; immutable collections, inclusive `datesUntil` ranges, and existing PostgreSQL aggregation/tuple-cursor SQL conventions remain in force.

Focused and complete suites are green: 262 tests, 0 failures, 0 errors and 0 skipped; Maven `verify` and `spotless:check` pass.

## Current PR-021 implementation checkpoint

Date: 2026-08-19. PR-021 is implemented in the working tree and remains the active pointer through review; no commit or PR transition was performed.

- V3 adds exactly `ledger.financial_account`, `account_cash_pocket`, `activity`, `money_posting`, `idempotency_record`, and `account_balance_projection`. Flyway remains the only DDL owner and Hibernate validates the mappings.
- The owner-scoped ledger supports exact opening assertions, full-ledger cash, holdings-only brokerage cash coverage, liability opening/read semantics, immutable signed activities/postings, deposits, withdrawals, same-currency transfers/previews, policy enforcement, reversals, opening correction, current/historical balance reads, keyset cursors, and typed-principal HTTP routes.
- Idempotency snapshots and all financial facts/projection changes commit atomically. PostgreSQL advisory transaction locks serialize principal-scoped retries; account/projection rows use deterministic sorted pessimistic locking. The global error boundary handles raw Hibernate and Spring data-integrity failures through the static `DatabaseConstraintRegistry`.
- Activity/posting domain factories and matching PostgreSQL checks enforce signed fact shapes; required mutation versions are controller-validated; negative opening reality and resulting current policy breaches are explicit in activity decisions and account/balance responses; activity history uses one batched posting query.
- Review-fix coverage now includes the combined transfer policy decision, liability/authorized-limit warning semantics, exact V3 constraint/index inventory, cross-owner and numeric/JSONB/reversal/idempotency database violations, real workflow rollback for posting/projection/idempotency failures, and account-creation/authorized-limit/currency/future-time HTTP and concurrency proof. The focused gate passed 61 tests; the full suite and `verify` each passed 319 tests, with Spotless and diff checks green.
- Account-creation replay now checks the locked idempotency record before mutable currency activity, with PostgreSQL coverage proving exact replay after currency deactivation; cursor tests assert `AppException`/`VALIDATION_FAILED` and the `cursor` validation key instead of a broad runtime type.
- Deferred work remains unchanged: reconciliation/imports, pending settlement, investments/trades, multi-currency/FX, spending/income/bills/debt, households/providers, frontend, and all asynchronous/job infrastructure.
- The worktree contains uncommitted/untracked PR-021 production, migration, test, and documentation changes for user review. The reconciled production surface is 75 files with 4,736 gross added lines and 73 deleted lines, including the V3 migration and the preceding platform standardization changes; tests and documentation are excluded.

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

### PR-018 - Refresh-session rotation, reuse response, and HTTP refresh

Status: **COMPLETE IN ACCEPTED COMMIT `d1eea9a`**

Starting commit: `7f55288`

Established:

- one owner-lock-first transactional refresh rotation workflow with post-lock reload and one clock observation;
- append-oriented predecessor consumption and successor insertion under the unchanged immediate replacement FK and partial active-family uniqueness constraint;
- fixed absolute family expiry, successor-bound access-token issuance, and full rollback on downstream issuance/runtime failure;
- committed active-family revocation with `REUSE_DETECTED` before the uniform replaced-token `401` response;
- one public JSON-only `POST /api/v1/auth/refresh` using exact single-channel response-body or hardened host-only cookie credential/delivery semantics;
- shared exact refresh-cookie construction for login and refresh;
- real PostgreSQL rollback/reuse/locking/concurrent-duplicate coverage plus real-filter content-type/CORS/route/stateless security coverage.

Spotless, the 45-test focused suite, the full 124-test suite, and Maven `verify` passed with no failures, errors, or skipped tests. The accepting commit is `d1eea9a`.

## Application state inherited from the foundation

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

PR-017 additionally adds request/response records under `identity.web.request` and `identity.web.response`, plus `identity.web.LocalLoginController`. PR-018 adds refresh rotation application/result contracts, explicit refresh request/response records, repository projection and owner-lock queries, `DeviceSession` rotation/reuse behavior, the shared `RefreshTokenCookieHeader`, and `LocalRefreshController`.

The inherited package tree above predates the current naming cleanup. The authoritative current layout is `web/request` and `web/response` for HTTP records, with meaningful application-owned criteria/read models under `application/model`; no new top-level `input`, `output`, `dto` or generic mapper package is introduced.

## Important decisions discovered during implementation

- Flyway SQL is the only DDL authority.
- PostgreSQL integrity constraints remain authoritative even when JPA mappings exist.
- For PostgreSQL Testcontainers JDBC fixtures targeting `timestamptz`, use `OffsetDateTime` parameters rather than raw `Instant` values with `JdbcTemplate`.
- JPA entities may use `Instant` for persisted timestamp fields.
- PR-003 is read/mapping-oriented. It does not establish entity mutation APIs or JPA write semantics.
- JSON fields are initially mapped as opaque JSON strings with Hibernate JSON JDBC typing; final write-side representation is deferred until a real write use case requires it.
- Lombok is an accepted project dependency. Touched JPA entities use `@Getter` and `@NoArgsConstructor(access = AccessLevel.PROTECTED)`; Spring components use `@RequiredArgsConstructor` where appropriate.
- Capability code uses the fixed `domain`, `application`, `infrastructure`, `configuration`, and `web` sub-packages from `coding-standards.md`, with HTTP records in `web/request` and `web/response`, and meaningful use-case models in `application/model`; unused sub-packages are omitted.
- `LocalAccountRegistrationService` is the first identity write workflow: one transaction creates a `user_account` and its `LOCAL` `auth_identity`, and an identity flush failure rolls both rows back.
- Duplicate-email registration failures use `IdentityErrorCode.EMAIL_ALREADY_REGISTERED` directly with the shared `AppException` contract; no identity-specific exception wrapper is retained.
- HTTP errors use one RFC 9457 `ProblemDetail` contract with stable application fields; internal 5xx details, causes, SQL, and exception messages are not serialized.
- Request correlation UUIDs are generated by the application `IdGenerator`, stored on the request, and returned in `X-Trace-Id` and Problem Detail `traceId`; inbound headers are not authoritative. Micrometer owns the native W3C trace/span context and native MDC entries.
- Bean Validation starts at controllers; request records own structural/nested metadata and controller-invoked non-structural checks, while application services do not use service-level `@Validated` or `@Valid`.
- The global error boundary owns database constraint mapping and optimistic-lock translation. Capability mappings are registered as platform records without importing capability enums into the registry; unknown persistence failures remain safe 500 responses.
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

### PR-019 — Authenticated identity and session security lifecycle

Status: **COMPLETE IN ACCEPTED WORKING-TREE COMMIT `0c6657e`**

Specification:

`PR-019-authenticated-identity-and-session-security-lifecycle.md`

Established:

- `AuthenticatedIdentity` and `AuthenticatedIdentityResolver` extract only canonical authenticated `userAccountId` and `sessionId` values from the already validated `Authentication`; no request parameter or `SecurityContextHolder` lookup is used by the resolver.
- Owner-scoped query endpoints and read repository:
  - `GET /api/v1/me` returns only authenticated user ID, email, and registration timestamp.
  - `GET /api/v1/auth/sessions` provides one-statement keyset pagination with `limit` 1-100 and a canonical unpadded Base64url cursor containing timestamp and family ID.
  - `GET /api/v1/auth/sessions/{familyId}` returns one owner-scoped family with status `ACTIVE`, `EXPIRED`, `REVOKED`, or `COMPROMISED`.
- Owner-locked device session revocation and logout:
  - `POST /api/v1/auth/logout` supports `CURRENT_SESSION` and `ALL_SESSIONS`.
  - `DELETE /api/v1/auth/sessions/{familyId}` supports idempotent owned-family termination and clears the refresh cookie only when the current family is terminated.
  - Each mutation locks the owner row before generation lookup, observes one injected clock value, retains all generations and replacement links, mutates terminal rows in deterministic family order, and flushes the session/event mutation in one transaction shared with refresh rotation.
  - `RefreshTokenCookieHeader.clear()` generates the exact host-only expired `Set-Cookie` header.
- Safe audit security event recording:
  - `SecurityEventRecorder` persists immutable, canonical, sanitized JSON objects to `platform.security_event` using exactly the nine PR-019 event types and required detail keys.
  - User-scoped session/reuse events use `REQUIRED` propagation and receive the mutation observation timestamp; invalid-login and throttle events use `REQUIRES_NEW` and contain only server-owned trace/operation details.
  - Event persistence failures roll back shared session mutations; independent failure/throttle event failures fail safely and roll back the process-local throttle transition.
- In-memory process-local authentication abuse protection:
  - `AuthenticationAbuseProtection` uses SHA-256 unpadded Base64url fingerprints, exact `LOGIN`/`REGISTER`/`REFRESH` source domains, per-bucket fixed windows, fail-closed capacity bounding, transition-specific compare-and-set rollback, and deterministic pruning.
  - Defaults are login principal/source `5/25` per 15 minutes with 15-minute blocks, registration `10` per hour with one-hour blocks, refresh `30` per 15 minutes with 15-minute blocks, and a maximum of `10,000` tracked keys; all configured values are positive.
  - Raw source, email, password, token, and forwarded-IP data are not logged or persisted; state is deliberately process-local and resets on restart.
- Full unit, integration, concurrency, and HTTP coverage includes 164 identity-package tests and 211 tests repository-wide, with no failures, errors, or skips in the final clean verification.

### PR-020 — Canonical reference catalog and owner-scoped manual instruments

Status: **COMPLETE IN ACCEPTED COMMIT `3f45a8c`**

Starting commit: `0c6657e`

Established:

- Flyway V2 creates exactly seven `reference` tables: `country`, `currency`, `market`, `market_currency`, `instrument`, `instrument_alias`, and `market_calendar`.
- Deterministic offline seeds cover TR/GB/US countries, TRY/USD/EUR/GBP currencies, XIST and MANUAL markets, and the specified explicit quotation-currency relationships; no rates, prices, observations, providers, or financial facts are seeded.
- Minimal JPA mappings, repositories, canonical country/currency/market/instrument value objects, instrument/valuation/alias/calendar enums, and migration-owned constraints/indexes are implemented with Hibernate schema validation.
- Authenticated read APIs expose stable countries, currencies, markets, and explicit market-calendar rows. Calendar responses distinguish `NONE`, `PARTIAL`, and `COMPLETE`, list missing dates, preserve local session times, enforce a maximum 366-date range, and never infer schedules.
- Owner-derived manual instrument create/detail/update APIs validate active market/currency support, persist `USER_ENTERED` ownership and aliases atomically, preserve immutable identity fields, expose timestamps/version, enforce owner-only mutation/visibility, and translate the named reference errors.
- SQL search combines active global rows with current-owner rows only, supports prefix/market/type/inactive filters and aliases, returns deterministic bounded cursor pages, binds cursors to canonical filter digests, and avoids N+1 alias loading.
- HTTP/security tests prove exact response contracts, no-store/no-cache behavior, cross-owner non-leakage, malformed/duplicate/inactive/version/cursor failures, no servlet sessions, and the unchanged single bearer chain.
- Standards alignment now places non-annotation request invariants in public DTO `validate()` barriers, co-locates response factories on response records, uses immutable collection boundaries and named limits, expresses bounded market grouping with standard collectors, centralizes cache headers and validation-error construction, and scopes trace IDs in MDC.
- Repository query names avoid nested-property underscores through explicit `@Query` methods without `@Param`; owned-alias bulk deletion flushes and clears the persistence context, then the service obtains a managed instrument reference before reinsertion.
- Known unique-collision translation is centralized in the platform error utility. Unknown integrity failures are logged by the global handler and exposed only as the generic safe server error. PR-019's established pipe session cursor remains a compatibility exception while PR-020 uses its specified canonical JSON instrument cursor.
- FK-only owner assignment uses `EntityManager.getReference`; `Instrument` stores application enums with textual `EnumType.STRING`; response construction is co-located on response records; and redundant reference persistence work is removed.
- Alias-only manual-instrument updates use an immediate owner/version compare-and-swap before child replacement; ordinary metadata updates use JPA `@Version`. Both paths reject stale concurrent writers without pessimistic locking or lost updates.
- The supervising user explicitly authorized the mixed identity/session/abuse-protection standards alignment. It remains behavior-compatible with PR-019, is covered by the expanded focused gate, and is excluded from PR-020's reference-only sizing proof.
- Verification: the expanded 77-test PR-020/reference/security gate, all 266 repository tests, Spotless, and Maven `verify` pass with no failures, errors, or skips.

Production surface: 49 new reference/platform production files contain 2,190 nonblank lines. This reference-only count excludes tests, documentation, generated output, formatting churn, and the separately authorized cross-cutting identity/session alignment; it independently exceeds the fixed 1,905-line PR-sizing comparison floor.

## Current database

Migration version: `V3`

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

### reference

- `country`
- `currency`
- `market`
- `market_currency`
- `instrument`
- `instrument_alias`
- `market_calendar`

### ledger

- `financial_account`
- `account_cash_pocket`
- `activity`
- `money_posting`
- `idempotency_record`
- `account_balance_projection`

### currently empty schemas

- `data`
- `money`
- `analysis`
- `asset`

## Active implementation unit

[PR-021 — Financial-account onboarding and immutable cash ledger](PR-021-financial-account-onboarding-and-cash-ledger.md) is implemented in the working tree and remains active through review from starting commit `cf895ac`.

It is the first financial-truth vertical slice: V3 owner-scoped account onboarding, explicit opening-state coverage, immutable cash activities/postings, native balance projection/read behavior, deposits, withdrawals, same-currency owned transfers, reversals/opening correction, policy enforcement, idempotency, locking, and HTTP/security proof. The focused gate passes 61 tests with no failures, errors, or skips; the full suite and `verify` each pass 319 tests, and Spotless passes.

The former custom durable-platform-job draft is retired. PR-021 must not implement a generic scheduler, worker, retry, batch, queue, or workflow abstraction. Select asynchronous infrastructure only with its first concrete workload after a recorded build-versus-buy evaluation.

See `CURRENT.md` and `PR-021-financial-account-onboarding-and-cash-ledger.md`.

## Build-versus-buy audit

The 2026-08-16 repository audit found no reason to outsource financial domain semantics, but identified these infrastructure boundaries:

- `AuthenticationAbuseProtection` is an accepted, process-local fixed-window implementation with repository-specific rollback behavior. Before multi-instance deployment or further limiter expansion, compare it with a maintained limiter such as Bucket4j and retain custom behavior only where the required semantics cannot be composed safely.
- The unused `platform.job` table/entity/repository are speculative storage scaffolding, not authority to build a worker. Decide whether to adopt maintained scheduling/batch infrastructure and migrate or remove the scaffold only when a concrete asynchronous workload establishes the required semantics.
- The legacy frontend duplicates JWT parsing, browser token storage, and refresh scheduling. It must not be extended; replace it with the backend's current hardened refresh-cookie/bearer contract or a maintained standards-based client before frontend reactivation.
- The accepted identity/session implementation delegates password and JWT cryptography to Spring Security/Nimbus and owns product-specific device-session rotation/reuse behavior. Before adding OIDC, recovery, MFA, federation, or authorization-server duties, evaluate Spring Security's authorization-server support or an external identity provider.
- Shared platform fingerprinting and cursor-token transport now remove duplicated SHA-256/Base64 mechanics; ledger cursor payloads use Jackson records with canonical re-encoding, while cursor positions, filter semantics, error codes, and owner-scoped SQL remain capability-owned. The legacy session cursor remains a compatibility exception.

## Known issues / deferred work

- PR-020 passed the expanded 77-test focused gate and the full 266-test suite, including Testcontainers PostgreSQL integration tests, real-filter HTTP/security checks, abuse/concurrency coverage, Spotless, and Maven `verify`, and is accepted in commit `3f45a8c`.
- Keyset pagination in `DeviceSessionReadRepository` casts `s.id` to `text` inside the PostgreSQL `MAX` aggregate function to support UUID types across PostgreSQL versions.
- Maven explicitly registers Lombok on the annotation-processor path so Lombok-generated entity accessors and constructors compile on Java 25.
- Reference catalog identities, deterministic seeds, explicit calendar storage, and owner-scoped manual instruments now exist; global administration, calendar/import workflows, observations, and provider adapters remain deferred.
- PR-021 now provides the first ledger/financial-account model; reconciliation/imports, pending settlement, investments, multi-currency/FX, spending/debt, households, providers, and future financial capabilities remain deferred.
- The frontend still targets legacy APIs.
- Bank/broker connectivity remains explicitly deferred.
- PR-021 establishes the required JPA JSONB idempotency snapshot mapping and tests raw entity round trips against the V3 schema.

## Resume instructions

To continue implementation or review:

1. read `AGENTS.md`;
2. read this file;
3. read `docs/implementation/CURRENT.md`;
4. read the active PR specification;
5. read `docs/review/backend-master-plan.md`;
6. read `docs/review/accounting-contract.md` when financial behavior is involved;
7. inspect the actual repository state and relevant latest diff;
8. perform only the explicitly requested role against the active PR; do not design or activate a later unit while PR-021 remains active.

Do not require historical chat context to continue the rewrite.
