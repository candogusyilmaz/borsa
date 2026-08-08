# Backend rewrite implementation state

Last updated: 2026-08-08

## Current technology

- Java 25
- Spring Boot 4.1.x
- PostgreSQL
- Flyway owns DDL
- Hibernate/JPA uses schema validation
- Testcontainers for PostgreSQL integration tests
- One Maven project / modular monolith

## Git workflow

- Working branch: `rewrite`
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
│   └── infrastructure
│       ├── UserAccountRepository
│       ├── AuthIdentityRepository
│       └── DeviceSessionRepository
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
    └── web
        └── ApiExceptionHandler
```

## Important decisions discovered during implementation

- Flyway SQL is the only DDL authority.
- PostgreSQL integrity constraints remain authoritative even when JPA mappings exist.
- For PostgreSQL Testcontainers JDBC fixtures targeting `timestamptz`, use `OffsetDateTime` parameters rather than raw `Instant` values with `JdbcTemplate`.
- JPA entities may use `Instant` for persisted timestamp fields.
- PR-003 is read/mapping-oriented. It does not establish entity mutation APIs or JPA write semantics.
- JSON fields are initially mapped as opaque JSON strings with Hibernate JSON JDBC typing; final write-side representation is deferred until a real write use case requires it.
- Lombok is an accepted project dependency. Touched JPA entities use `@Getter` and `@NoArgsConstructor(access = AccessLevel.PROTECTED)`; Spring components use `@RequiredArgsConstructor` where appropriate.
- Capability code uses the fixed `domain`, `application`, `infrastructure`, `configuration`, and `web` sub-packages from `coding-standards.md`, omitting unused sub-packages.
- `LocalAccountRegistrationService` is the first identity write workflow: one transaction creates a `user_account` and its `LOCAL` `auth_identity`, and an identity flush failure rolls both rows back.

## Active implementation unit

PR-005 implements the first atomic local-account registration application workflow: email normalization, password hashing, and transactional creation of `user_account` plus its `LOCAL` `auth_identity`. It remains active pending user review.

See `CURRENT.md` and `PR-005-atomic-local-account-registration.md` for the authoritative active scope.

## Next likely implementation areas

These are planning hints for work after PR-005, not active specifications.

Likely sequence:

1. remaining local authentication HTTP/login/token foundation;
2. refresh sessions / rotation / logout / revocation;
3. security events and authentication abuse protection;
4. durable job claim/retry worker;
5. V2 reference migration.

The exact next behavioral implementation unit must be designed just-in-time after PR-005 is reviewed and accepted.

Do not treat this list as a promise of PR numbering or exact scope.

## Known issues / deferred work

- No HTTP authentication, login, token, or security-filter behavior yet; local registration is application-layer only.
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
