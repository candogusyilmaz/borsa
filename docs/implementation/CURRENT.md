# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-011 — Local access-token issuance](PR-011-local-access-token-issuance.md)

Implementation agents must read the complete specification and implement only that scope.

Local commit `c3c9fd6` contains the completed PR-010 initial opaque refresh-session issuance. PR-011 adds only local-development RS256 access-token issuance for one active eligible session.

Do not add an HTTP login endpoint, bearer decoder/filter chain/principal, persistent signing-key infrastructure, refresh rotation, logout/revocation, security-event/abuse-control behavior, schema change, frontend work, or another domain workflow.

Implementation and review agents must not mutate Git state. The supervising agent alone may stage and create the one local PR commit after independent review and verification.

Do not advance this pointer during implementation or review. It remains on PR-011 until the supervisor commits the completed unit.
