# Server instructions

- Work from `server/` with Java 21 and the Maven wrapper. Use `mvnw.cmd` on Windows.
- Preserve existing Flyway history. Add future schema changes through new versioned migrations.
- Use an initialized PostgreSQL test database when running the full `./mvnw test` suite.
- The root Dockerfile builds `web/` and bundles `web/dist` as Spring static resources. Run `./mvnw clean package` after the web build to reproduce the packaged SPA locally.
- Follow [docs/engineering/backend-logging-standards.md](../docs/engineering/backend-logging-standards.md) for logging levels, structured events, MDC correlation, and sensitive-data restrictions.

## Backend testing

- Prioritize financial and accounting rules, including portfolio cost basis, profit and loss, cash flow, recurring transactions, currency conversion, and monetary precision. Cover meaningful risks in authorization, account data isolation, data integrity, transaction consistency, imports, persistence, and security.
- Use pure unit tests for deterministic business rules. Use focused integration tests when correctness depends on SQL, persistence, transactions, or Spring Security.
- Skip tests for trivial CRUD, accessors, straightforward mappings, and simple DTOs. Avoid mocks that only assert internal calls, tests that duplicate framework guarantees or repeat coverage across layers, and one test class per production class.
- Add testing abstractions or infrastructure only when they solve a demonstrated need.
