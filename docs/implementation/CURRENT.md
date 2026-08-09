# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-016 — HTTP bearer authentication boundary](PR-016-http-bearer-authentication-boundary.md)

Implementation agents must read the complete specification and implement only that scope.

Local commit `3d86ff2` contains the completed PR-015 database-backed access-token authentication converter. PR-016 installs only the scoped stateless HTTP bearer boundary over the accepted decoder and converter.

Do not add a production endpoint or API DTO, login/refresh transport, role/permission/owner helper, access-denied policy, persistent signing-key infrastructure, refresh rotation or family-reuse response, logout/revocation, security-event/abuse-control behavior, schema change, frontend work, or another domain workflow.

Implementation and review agents must not mutate Git state. The supervising agent alone may stage and create the one local PR commit after independent review and verification.

Do not advance this pointer during implementation or review. It remains on PR-016 until the supervisor commits the completed unit.
