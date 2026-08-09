# Backend rewrite implementation state

Last updated: 2026-08-09

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

Status: **IMPLEMENTED AND INDEPENDENTLY REVIEWED - READY FOR SUPERVISOR COMMIT**

Established:

- immutable `stocks.identity.refresh-session.lifetime` configuration with a positive 30-day default;
- 32-byte `SecureRandom` refresh tokens encoded as unpadded Base64url and deterministically hashed with SHA-256 for storage;
- one initial `DeviceSession` construction path whose session ID is also its token-family ID and whose usage, revocation, and replacement state begins null;
- transactional session issuance that rechecks user eligibility, persists only the token hash, and returns the raw token once with its session ID and expiry;
- pure configuration/token coverage and PostgreSQL/Testcontainers coverage for initial issuance, optional labels, independent device families, disabled/missing users, and raw-token non-persistence;
- no HTTP login, access token, refresh rotation, logout/revocation, principal/filter-chain, security-event, abuse-control, schema, migration, dependency, or frontend behavior.

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
- The versioned local-registration endpoint is intentionally unauthenticated: it delegates once to the PR-005 transactional workflow and returns only the new user ID; login, tokens, sessions, and Spring Security remain deferred.
- HTTP registration validation occurs before the service workflow, while duplicate and malformed-request failures retain the shared PR-006 ProblemDetail and trace-correlation behavior.
- Local credential verification is application-only and read-only: it normalizes the lookup email with `Locale.ROOT`, performs one password match with a constructed dummy-hash fallback when needed, rejects disabled accounts uniformly, and returns only the associated user UUID. Structural validation is deferred to the future HTTP login boundary; the service has no validation annotations.
- Initial refresh-session issuance is application-only: it rechecks user eligibility, uses injected clock/ID generation and a positive configured lifetime, returns one high-entropy raw token, and stores only its SHA-256 Base64url hash in the existing `device_session` table.

## Active implementation unit

PR-010 initial opaque refresh-session issuance is active. It adds only the transactional creation of a first server-side session generation for an already authenticated eligible user, returning the raw token once and persisting only its hash.

See `CURRENT.md` and `PR-010-initial-refresh-session-issuance.md` for the authoritative active scope.

## Next likely implementation areas

These are planning hints for work after PR-010, not active specifications.

Likely sequence:

1. HTTP login and the minimum access-token/session boundary, composing the accepted credential-verification and initial-session workflows in separately reviewable units;
2. refresh sessions / rotation / logout / revocation;
3. security events and authentication abuse protection;
4. durable job claim/retry worker;
5. V2 reference migration.

The exact next behavioral implementation unit must be designed just-in-time from the reconciled committed state.

Do not treat this list as a promise of PR numbering or exact scope.

## Known issues / deferred work

- No HTTP authentication, login, access-token, or security-filter behavior yet; application-layer credential verification and initial refresh-session issuance do not establish a principal or HTTP authentication flow.
- No refresh-token lookup, rotation/reuse detection, logout, revocation, or session listing/deletion behavior yet.
- No Spring Security web/filter-chain configuration yet.
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
