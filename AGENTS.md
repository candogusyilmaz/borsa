# Repository instructions

- Read [server/AGENTS.md](server/AGENTS.md) for Java and build integration, or [web/AGENTS.md](web/AGENTS.md) for frontend work.
- Keep the server and web client aligned when changing API contracts. The root [README](README.md) describes the local and production builds.
- Prefer `rg` for text and file searches. Narrow the directory and glob to match the task. Use language-aware tooling for symbol navigation.
- For library, framework, SDK, CLI and cloud-service documentation, resolve the library in Context7 and query its official documentation by concept.

## Engineering philosophy

This application is under active development, has no active users, and is unlikely to operate at significant production scale. These principles apply to frontend, backend, database design, testing, and infrastructure.

- Favor simple, readable, maintainable code. Weigh realistic likelihood, user and data impact, implementation cost, and ongoing maintenance cost. Accept rare failures when prevention takes disproportionate effort. Preserve essential security, account isolation, and financial data integrity.
- Prefer native APIs and existing framework capabilities. Delete unnecessary code before adding abstractions. Build infrastructure for concrete requirements.
- Require backward compatibility only when explicitly requested. Breaking changes, session invalidation, and fresh logins are acceptable. Avoid compatibility layers, legacy readers, and transitional mechanisms.
- Focus regression tests on realistic usage and important failures.
- In architecture reviews, classify findings as **Must fix**, **Worth fixing**, **Acceptable limitation**, or **Out of scope**. Use those classifications to decide which findings warrant implementation.
