# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-012 — Atomic local login orchestration](PR-012-atomic-local-login-orchestration.md)

Implementation agents must read the complete specification and implement only that scope.

Local commit `6aa57b6` contains the completed PR-011 local access-token issuance. PR-012 adds only one atomic application workflow that composes the accepted credential, initial refresh-session, and access-token services.

Do not add an HTTP endpoint or API DTO, bearer decoder/filter chain/principal, persistent signing-key infrastructure, refresh rotation, logout/revocation, security-event/abuse-control behavior, schema change, frontend work, or another domain workflow.

Implementation and review agents must not mutate Git state. The supervising agent alone may stage and create the one local PR commit after independent review and verification.

Do not advance this pointer during implementation or review. It remains on PR-012 until the supervisor commits the completed unit.
