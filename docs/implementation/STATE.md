# Backend rewrite implementation state

Last updated: 2026-08-07

## Current technology

- Java 25
- Spring Boot 4.1.x
- PostgreSQL
- Flyway owns DDL
- Hibernate/JPA uses schema validation
- Testcontainers for PostgreSQL integration tests
- One Maven project / modular monolith

## Git workflow

- Working branch: rewrite
- User owns commits and Git operations.
- Agents leave changes uncommitted.

## Completed implementation units

### PR-001 — Modern backend foundation

Status: COMPLETED

Established:

- Java 25 / Spring Boot 4 foundation
- legacy backend removed
- frontend preserved
- Clock abstraction
- IdGenerator abstraction
- ProblemDetail foundation
- Testcontainers smoke test
- Flyway configured with no migrations

### PR-002 — V1 foundation database

Status: ACTIVE

Specification:
PR-002-v1-foundation-database.md

## Current database

Expected migration version:
V1 after PR-002

Schemas:

- identity
- reference
- ledger
- data
- money
- analysis
- asset
- platform

Tables implemented:

- none before PR-002

## Important decisions discovered during implementation

None yet.

## Next likely units

These are planning hints, not active specifications:

1. Identity JPA mappings
2. Registration/local authentication
3. Sessions/token rotation
4. Security events/throttling
5. Durable job execution

Exact scope must be designed just-in-time.

## Known issues / deferred work

- No authentication yet.
- No reference data yet.
- No ledger yet.
- Frontend still targets legacy APIs.
- Bank/broker connectivity explicitly deferred.

## Resume instructions

To continue planning implementation:

1. read this file;
2. read `backend-master-plan.md`;
3. read `accounting-contract.md` when financial behavior is involved;
4. inspect the most recently completed PR specification;
5. inspect current repository state/diff;
6. design only the next reviewable PR.
