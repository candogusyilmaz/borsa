# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-013 — Opaque refresh-session authentication](PR-013-opaque-refresh-session-authentication.md)

Implementation agents must read the complete specification and implement only that scope.

Local commit `5570f8d` contains the completed PR-012 atomic local-login orchestration. PR-013 adds only hash-based authentication of an existing opaque refresh session.

Do not add an HTTP endpoint or API DTO, bearer decoder/filter chain/principal, persistent signing-key infrastructure, refresh rotation or family-reuse response, logout/revocation, security-event/abuse-control behavior, schema change, frontend work, or another domain workflow.

Implementation and review agents must not mutate Git state. The supervising agent alone may stage and create the one local PR commit after independent review and verification.

Do not advance this pointer during implementation or review. It remains on PR-013 until the supervisor commits the completed unit.
