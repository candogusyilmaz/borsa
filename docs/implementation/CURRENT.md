# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-020 — Canonical reference catalogue and owner-scoped manual instruments](PR-020-canonical-reference-catalog-and-manual-instruments.md)

Implementation agents must read the complete specification and implement only that scope.

PR-018's owner-locked refresh rotation, committed reuse response, successor access-token issuance, and JSON/cookie refresh delivery were accepted in commit `d1eea9a` after its focused 45-test gate, full 124-test suite, Spotless, Maven `verify`, and completion record passed.

PR-019 is complete in the accepted working-tree commit `0c6657e`: typed authenticated identity, `/me`, owner-scoped logical session reads, current/all/selected-family revocation, exact logout cookie clearing, durable security events, and bounded process-local login/register/refresh abuse protection with transaction/concurrency/security coverage.

PR-020 is the canonical offline reference catalogue and owner-scoped manual-instrument vertical slice. Implement only its seven-table V2 migration, deterministic reference seeds, minimal mappings/value objects, read-only catalogue/calendar APIs, owner-scoped manual instrument lifecycle, deterministic SQL search/cursor behavior, and required tests. Do not add jobs, persistent signing keys, Google/OIDC/recovery, roles/permissions/households, account export/deletion, cross-site cookie/CORS/trusted-proxy/general CSRF infrastructure, observations/providers, ledger/financial behavior, or frontend work.

On 2026-08-16 the supervising user explicitly authorized retaining the already-mixed behavior-preserving identity/session/abuse-protection and coding-standards alignment described in PR-020's completion record. That authorization resolves the review-scope objection but does not authorize new authentication product behavior or any other deferred capability.

The user-owned `AGENTS.md` and command-playbook changes remain outside production scope and must not be reverted. Agents perform no Git operations.

Keep this pointer on PR-020 throughout implementation and review. Do not advance it to another PR during this unit.

PR-021 has been drafted as a planning artifact only. It is not active until the user accepts/commits PR-020 and explicitly advances this pointer.
