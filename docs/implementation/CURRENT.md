# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-008 — Local password credential verification](PR-008-local-password-authentication.md)

Implementation agents must read the complete specification and implement only that scope.

PR-001 through PR-007 are complete. PR-007 has been reviewed and accepted; the user owns its commit and Git history.

PR-005 established atomic application-layer local registration. PR-006 established the shared RFC 9457 error boundary and request trace correlation. PR-007 exposed registration through one versioned HTTP endpoint.

PR-008 adds only application-layer verification of a `LOCAL` email/password credential and returns the matched user UUID. Do not add an HTTP login endpoint, tokens, device sessions, Spring Security web configuration, rate limiting, security-event writes, frontend changes, or another domain workflow.

The user owns Git history. Make working-tree changes only.

Do not commit, create or switch branches, merge, rebase, tag, reset, stash, or push unless explicitly requested.

Do not advance this pointer after implementation. It remains on PR-008 until the user has reviewed and accepted the resulting diff.
