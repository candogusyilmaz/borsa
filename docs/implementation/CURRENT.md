# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-019 — Authenticated identity and session security lifecycle](PR-019-authenticated-identity-and-session-security-lifecycle.md)

Implementation agents must read the complete specification and implement only that scope.

PR-018's owner-locked refresh rotation, committed reuse response, successor access-token issuance, and JSON/cookie refresh delivery were accepted in commit `d1eea9a` after its focused 45-test gate, full 124-test suite, Spotless, Maven `verify`, and completion record passed.

PR-019 is one substantial local-identity security subsystem increment: typed authenticated identity, `/me`, owner-scoped logical session reads, current/all/selected-family revocation, exact logout cookie clearing, durable security events, and bounded process-local login/register/refresh abuse protection with transaction/concurrency/security coverage.

Do not add jobs, persistent signing keys, Google/OIDC/recovery, roles/permissions/households, account export/deletion, cross-site cookie/CORS/trusted-proxy/general CSRF infrastructure, migrations/dependencies/frontend changes, reference data, or financial behavior.

The user-owned `AGENTS.md` and command-playbook changes remain outside production scope and must not be reverted. Agents perform no Git operations.

Do not advance this pointer during implementation or review. It remains on PR-019 until the supervising user accepts the completed unit.
