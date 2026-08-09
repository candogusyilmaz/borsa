# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-014 — Local access-token decoding](PR-014-local-access-token-decoding.md)

Implementation agents must read the complete specification and implement only that scope.

Local commit `7bb7c40` contains the completed PR-013 opaque refresh-session authentication. PR-014 adds only production cryptographic and structural decoding of the already accepted local access-token envelope.

Do not add an HTTP endpoint or API DTO, resource-server/filter chain/principal, user/session lookup, persistent signing-key infrastructure, refresh rotation or family-reuse response, logout/revocation, security-event/abuse-control behavior, schema change, frontend work, or another domain workflow.

Implementation and review agents must not mutate Git state. The supervising agent alone may stage and create the one local PR commit after independent review and verification.

Do not advance this pointer during implementation or review. It remains on PR-014 until the supervisor commits the completed unit.
