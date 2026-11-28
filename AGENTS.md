# Repository instructions

- Read [server/AGENTS.md](server/AGENTS.md) for Java and build integration, or [web/AGENTS.md](web/AGENTS.md) for frontend work.
- Keep the server and web client aligned when changing API contracts. The root [README](README.md) describes the local and production builds.
- Prefer `rg` for text and file searches. Narrow the directory and glob to match the task. Use language-aware tooling for symbol navigation.
- For library, framework, SDK, CLI and cloud-service documentation, resolve the library in Context7 and query its official documentation by concept.
- Run formatting and verification explicitly. Review the diff before staging, select only task-related changes, and preserve unrelated staged work. Never stage the entire working tree automatically.

## Engineering philosophy

This application is under active development, has no active users, and is unlikely to operate at significant production scale. These principles apply to frontend, backend, database design, testing, and infrastructure.

- Favor simple, readable, maintainable code. Weigh realistic likelihood, user and data impact, implementation cost, and ongoing maintenance cost. Accept rare failures when prevention takes disproportionate effort. Preserve essential security, account isolation, and financial data integrity.
- Prefer native APIs and existing framework capabilities. Delete unnecessary code before adding abstractions. Build infrastructure for concrete requirements.
- Require backward compatibility only when explicitly requested. Breaking changes, session invalidation, and fresh logins are acceptable. Avoid compatibility layers, legacy readers, and transitional mechanisms.
- In architecture reviews, classify findings as **Must fix**, **Worth fixing**, **Acceptable limitation**, or **Out of scope**. Use those classifications to decide which findings warrant implementation.

## Testing

- Verify every meaningful change using existing tools. Build checks, temporary scripts, smoke tests, API requests, browser interactions, and manual checks are all valid ways to verify a change.
- Add a permanent test only when its regression value and the realistic risk justify its execution and maintenance costs.
- Test observable behavior and business rules. Prefer parameterized tests when different inputs exercise the same behavior. Skip arbitrary coverage targets and exhaustive edge-case permutations.
- Avoid brittle and low-value tests, and remove obsolete or redundant ones. Keep meaningful assertions intact when fixing failures.
