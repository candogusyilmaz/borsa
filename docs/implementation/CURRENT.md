# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-015 — Database-backed access-token authentication](PR-015-database-backed-access-token-authentication.md)

Implementation agents must read the complete specification and implement only that scope.

Local commit `4621473` contains the completed PR-014 local access-token decoder. PR-015 adds only current database eligibility and minimal Spring authentication conversion for an already decoded local access JWT.

Do not add an HTTP endpoint or API DTO, security filter chain/bearer extraction, role/permission/owner helper, persistent signing-key infrastructure, refresh rotation or family-reuse response, logout/revocation, security-event/abuse-control behavior, schema change, frontend work, or another domain workflow.

Implementation and review agents must not mutate Git state. The supervising agent alone may stage and create the one local PR commit after independent review and verification.

Do not advance this pointer during implementation or review. It remains on PR-015 until the supervisor commits the completed unit.
