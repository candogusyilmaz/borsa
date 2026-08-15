# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-018 — Refresh-session rotation, reuse response, and HTTP refresh](PR-018-refresh-session-rotation-and-http-refresh.md)

Implementation agents must read the complete specification and implement only that scope.

PR-017's local-login and explicit response-body/cookie delivery implementation has passed independent review, its focused PostgreSQL suite, the full 105-test suite, Spotless, and Maven `verify`. The user still owns the accepting commit: record that commit hash in PR-018 before production implementation begins, and do not invent it.

PR-018 is one substantial refresh-lifecycle vertical slice: PostgreSQL-backed owner locking, append-oriented token rotation, committed family revocation on replaced-token reuse, successor-bound access-token issuance, one public JSON-only refresh endpoint, exact native/cookie delivery, shared cookie construction, and the required transaction/concurrency/security tests.

Do not add logout/session listing or user-selected revocation, rolling expiry, refresh retry tolerance, cross-site cookie/CORS/general CSRF infrastructure, security events/abuse controls, authorization/owner helpers, persistent signing keys, schema/dependency/frontend changes, jobs, or another domain workflow.

The user-owned `AGENTS.md` modification remains outside production scope and must not be reverted. Agents perform no Git operations.

Do not advance this pointer during implementation or review. It remains on PR-018 until the supervising user accepts the completed unit.
