# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-010 — Initial opaque refresh-session issuance](PR-010-initial-refresh-session-issuance.md)

Implementation agents must read the complete specification and implement only that scope.

Local commit `82f6a34` contains the completed PR-009 repository-state reconciliation. PR-010 adds only application-layer issuance of the first opaque refresh-session generation for an already authenticated eligible user.

Do not add an HTTP login endpoint, access JWT, refresh rotation, logout/revocation, Spring Security filter chain or principal, security-event/abuse-control behavior, schema change, frontend work, or another domain workflow.

Implementation and review agents must not mutate Git state. The supervising agent alone may stage and create the one local PR commit after independent review and verification.

Do not advance this pointer during implementation or review. It remains on PR-010 until the supervisor commits the completed unit.
