# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-007 — HTTP local account registration](PR-007-http-local-account-registration.md)

Implementation agents must read the complete specification and implement only that scope.

PR-001 through PR-006 are complete. PR-006 has been reviewed and is ready for the user-owned commit.

PR-005 established atomic application-layer local registration. PR-006 established the shared RFC 9457 error boundary and request trace correlation.

PR-007 exposes only the existing local-registration workflow through `POST /api/v1/auth/register`. Do not add login, tokens, sessions, Spring Security, rate limiting, frontend changes, or another domain workflow.

The user owns Git history. Make working-tree changes only.

Do not commit, create or switch branches, merge, rebase, tag, reset, stash, or push unless explicitly requested.

Do not advance this pointer after implementation. It remains on PR-007 until the user has reviewed and accepted the resulting diff.
