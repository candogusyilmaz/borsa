# Server instructions

- Work from `server/` with Java 21. Use `./mvnw` on Linux or `./mvnw.cmd` on Windows.
- Apply Java formatting with `./mvnw spotless:apply`. Check it without editing source with `./mvnw spotless:check`.
- Use `./mvnw compile` for a focused compilation check, `./mvnw -DskipTests verify` for packaging verification, and `./mvnw -Dtest=TestClass test` for relevant automated tests.
- `B14__baseline.sql` initializes empty PostgreSQL 15 databases. Preserve B14 and the existing V2–V14 migration contents. Add schema changes through V15 and later versioned migrations.
- Scope portfolio, position, and trade operations to resources owned by the authenticated account.
- Point `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` at a dedicated local PostgreSQL test database for `./mvnw test`. Flyway initializes it if empty. See the [database setup](../README.md#database-setup).
- Run `./mvnw clean package` after building `web/` to package `web/dist` as Spring static resources locally.
- Follow [docs/engineering/backend-logging-standards.md](../docs/engineering/backend-logging-standards.md) for logging levels, structured events, MDC correlation, and sensitive-data restrictions.

## Backend testing

- Prioritize financial and accounting rules, including portfolio cost basis, profit and loss, cash flow, recurring transactions, currency conversion, and monetary precision. Cover meaningful risks in authorization, account data isolation, data integrity, transaction consistency, imports, persistence, and security.
- Use pure unit tests for deterministic business rules. Use focused integration tests when correctness depends on SQL, persistence, transactions, or Spring Security.
- Skip tests for trivial CRUD, accessors, straightforward mappings, and simple DTOs. Avoid mocks that only assert internal calls, tests that duplicate framework guarantees or repeat coverage across layers, and one test class per production class.
- Add testing abstractions or infrastructure only when they solve a demonstrated need.
