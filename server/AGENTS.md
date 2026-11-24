# Server instructions

- Work from `server/` with Java 21 and the Maven wrapper. Use `mvnw.cmd` on Windows.
- Preserve the Spring API contracts and existing Flyway history. Add future schema changes through new versioned migrations.
- Use an initialized PostgreSQL test database for `./mvnw test`.
- The root Dockerfile builds `web/` and bundles `web/dist` as Spring static resources. Run `./mvnw clean package` after the web build to reproduce the packaged SPA locally.
- Follow [docs/engineering/backend-logging-standards.md](../docs/engineering/backend-logging-standards.md) for logging levels, structured events, MDC correlation, and sensitive-data restrictions.
