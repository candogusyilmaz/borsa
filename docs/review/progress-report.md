# Backend transformation progress report

Report date: 2026-08-16

Scope: Spring Boot backend, PostgreSQL dump, database migration strategy, modular-monolith design, offline/fake data approach, and implementation readiness. React was not reviewed or changed in this update.

## At a glance

| Area                                               | Status                                                       | Evidence/result                                                                                                                                                                                                                                         |
| -------------------------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Existing backend/dump discovery                    | Complete                                                     | Current Java/resources, migrations, configuration and supplied PostgreSQL schema dump inventoried                                                                                                                                                       |
| Backend correctness/security audit                 | Complete as design review                                    | [backend-audit.md](backend-audit.md)                                                                                                                                                                                                                    |
| Business/analytics target design                   | Complete as design review                                    | [business-logic-and-analytics-design.md](business-logic-and-analytics-design.md)                                                                                                                                                                        |
| Feature catalogue                                  | Complete as plan                                             | 32 features in [implementable-features.md](implementable-features.md)                                                                                                                                                                                   |
| Cash/account/funding design                        | Complete as plan                                             | [cash-accounts-and-funding-design.md](cash-accounts-and-funding-design.md)                                                                                                                                                                              |
| Physical-asset/TCO design                          | Complete as plan                                             | [real-asset-lifecycle-tco-design.md](real-asset-lifecycle-tco-design.md)                                                                                                                                                                                |
| Consolidated backend scratch-rewrite plan          | Complete and authoritative                                   | [backend-master-plan.md](backend-master-plan.md), including exact R0–R16 implementation increments and supersession rules                                                                                                                               |
| Accounting contract                                | Initial implementation contract complete                     | [accounting-contract.md](accounting-contract.md): shared posting/time/fee/cost-basis/FX/balance/valuation/performance/projection semantics                                                                                                              |
| New target database                                | Created by user, not yet initialized/verified by this review | PostgreSQL database name `extreme_accounting`                                                                                                                                                                                                           |
| PR-001 — Modern backend foundation                 | **Complete**                                                 | Starting commit `b2c42e751097c7a19805c0a53b28941fa4deebce`; legacy backend deleted; Spring Boot 4.1.0 / Java 25 skeleton green; 5 tests passing                                                                                                         |
| Fresh database baseline (`V1__foundation.sql`)     | **Complete**                                                 | Accepted in PR-002 (`dee5b02`); 8 application schemas and 5 foundation tables with required constraints/indexes; fresh PostgreSQL migrates cleanly to V1                                                                                                |
| PR-003 — Identity and platform JPA entity mappings | **Complete**                                                 | Accepted in `97a06b7`; 5 JPA entities + 5 repositories; Hibernate `ddl-auto: validate` and all 29 tests pass                                                                                                                                            |
| PR-004 — V1 mapping package alignment              | **Complete**                                                 | Accepted in `eed342e`; 5 entities moved to `domain`, 5 repositories moved to `infrastructure`, Lombok conventions applied, and clean Java 25 annotation processing configured                                                                           |
| PR-005 — Atomic local account registration         | **Complete**                                                 | Transactional local registration, delegating password hashing, duplicate-constraint translation, rollback semantics, and 4 PostgreSQL service tests pass                                                                                                |
| PR-006 — Stable RFC 9457 error handling            | **Complete**                                                 | Structured `AppException`/`ErrorCode` contract, direct identity error-code use without a redundant exception wrapper, one global ProblemDetail handler, trace correlation, safe framework/persistence fallbacks, and 18 focused unit/MockMvc tests pass |
| PR-007 — HTTP local account registration           | **Complete**                                                 | Versioned `POST /api/v1/auth/register`, 201 JSON user-ID response, directional identity input/output DTOs, shared validation/duplicate/malformed error contract, and 4 PostgreSQL-backed HTTP tests pass                                                |
| PR-008 — Local password credential verification    | **Complete**                                                 | Accepted in `61fe8a6`; read-only local credential verification, uniform `INVALID_CREDENTIALS`, dummy-hash fallback, disabled/null-hash rejection, and five focused tests; no HTTP login, token, session, or security-filter work                          |
| PR-009 — Automated-batch state reconciliation      | **Complete**                                                 | Accepted in `82f6a34`; implementation state and automated local-commit rules reconciled with Git; documentation-only with no backend behavior change                                                                                                 |
| PR-010 — Initial opaque refresh-session issuance   | **Complete**                                                 | Accepted in `c3c9fd6`; positive 30-day lifetime configuration, 256-bit opaque token generation, SHA-256-only persistence, initial device-session factory, and transactional eligible-user issuance; no HTTP/JWT/rotation behavior                       |
| PR-011 — Local access-token issuance               | **Complete**                                                 | Accepted in `6aa57b6`; Boot-managed Spring Security JOSE, one startup-local 2048-bit RSA signer, immutable access-token properties, and read-only exact RS256 token issuance for eligible sessions                                                      |
| PR-012 — Atomic local login orchestration          | **Complete**                                                 | Accepted in `5570f8d`; one immutable result and one transactional application service compose credential verification, initial refresh-session issuance, and access-token issuance atomically                                                         |
| PR-013 — Opaque refresh-session authentication    | **Complete**                                                  | Accepted in `7bb7c40`; exact presented-token hashing, one unique stored-hash lookup, and read-only eligible-session authentication returning only the session UUID; no rotation, HTTP/API, or token-delivery surface                                  |
| PR-014 — Local access-token decoding               | **Complete**                                                  | Accepted in `4621473`; one production decoder over the existing local RSA public key plus strict RS256/header/claim/time-envelope validation; no bearer filter, principal, HTTP, persistence, or key-management surface                         |
| PR-015 — Database-backed access-token authentication | **Complete**                                                   | Accepted in `3d86ff2`; one owner-scoped session/user lookup and one read-only decoded-JWT converter produce a minimal authority-free Spring JWT authentication only for a currently eligible exact pair                                                    |
| PR-016 — HTTP bearer authentication boundary          | **Complete in local commit `9fcbb69`**                         | One servlet-only stateless `/api/v1/**` chain keeps registration public, reuses the accepted decoder/converter, and maps expected bearer failures into the existing trace-correlated RFC 9457 contract                                                   |
| PR-017 — HTTP local login and explicit token delivery | **Complete in user-owned commit `7f55288`**                  | One public login POST exposes the accepted atomic workflow through explicit response-body or hardened same-site cookie delivery; correction review and all required gates pass |
| PR-018 — Refresh rotation/reuse and HTTP refresh       | **Complete in accepted commit `d1eea9a`**                       | Owner-locked append-oriented rotation, committed family reuse response, successor access issuance, native/cookie HTTP delivery, and PostgreSQL/concurrency/security proof passed the focused 45-test gate, full 124-test suite, Spotless, and Maven `verify` |
| PR-019 — Authenticated identity/session security       | **Complete in accepted working-tree commit `0c6657e`**                   | Typed authenticated identity, `/me`, owner-scoped session reads, current/all/selected revocation, logout cookie clearing, durable security events, bounded process-local authentication abuse protection; focused gate, full 211-test suite, Spotless and Maven `verify` passed |
| PR-020 — Canonical reference catalog/manual instruments | **Implementation complete; awaiting user commit decision** | V2 seven-table reference catalog, deterministic seeds, explicit calendar coverage, owner-scoped manual instrument lifecycle, SQL search/cursor pagination, optimistic alias-replacement concurrency, and real-filter ownership/security proof are implemented; the mixed identity/session standards alignment is explicitly user-authorized and covered |
| Automated backend coverage                         | 266 tests green in the current working tree | The suite covers response/cookie delivery, exact session/token binding, credential failures, validation/parsing, rotation invalid states, rollback, reuse, PostgreSQL locking, abuse-control concurrency, route scope, reference migration/mapping, explicit calendar coverage, owner isolation, bounded cursor search, and statelessness; the current execution has 0 failures, 0 errors, and 0 skips |

Overall status: **PR-020 is implementation- and review-ready for the user's commit decision; no unresolved `MUST FIX` or `SHOULD FIX` finding remains.**

## Active implementation specification — PR-020

- Date: 2026-08-16.
- Starting commit: `0c6657e`, the accepted PR-019 working-tree commit.
- Implemented database capability: Flyway V2 creates exactly `reference.country`, `currency`, `market`, `market_currency`, `instrument`, `instrument_alias`, and `market_calendar`, with deterministic TR/GB/US, TRY/USD/EUR/GBP, XIST, MANUAL, and explicit market-currency seeds. V1 remains unchanged and no rates, prices, observations, providers, financial facts, PostgreSQL enums, extensions, functions, or triggers were introduced.
- Implemented application capability: canonical reference value objects/enums, minimal Hibernate mappings, authenticated offline country/currency/market reads, explicit calendar `NONE`/`PARTIAL`/`COMPLETE` responses with missing dates and local times, owner-derived manual create/detail/update, atomic alias replacement, optimistic version conflict handling, owner/global SQL visibility, alias-prefix search, stable bounded cursor pages, and the exact named reference error contract.
- Security/no-network evidence: all new controllers use the existing single bearer chain; cross-owner/missing targets return safe 404s, no new route is public, no servlet session is created, and tests use Testcontainers PostgreSQL plus in-process fixtures without provider/network collaborators. Manual facts are labeled `USER_ENTERED`/`REFERENCE_SEED` rather than live, official, or synthetic data.
- Standards-alignment follow-up: DTO `validate()` barriers own dependency-free cross-field invariants; response records own read-model factories; repository queries use descriptive explicit JPQL without `@Param`; owned-child bulk deletion flushes/clears before a managed-reference reinsert; and bounded SQL row grouping uses standard collectors.
- Error-boundary follow-up: known unique constraints are translated through the shared platform utility, while unknown database failures are logged server-side and return the generic safe 500 response. The PR-019 pipe cursor remains a documented compatibility exception; new PR-020 cursors retain the exact canonical JSON contract.
- Additional standards alignment: MDC trace correlation is scoped and cleaned by `RequestTraceFilter`; cache headers are centralized; DTO validation no longer duplicates annotation constraints; FK-only owner assignment uses `EntityManager.getReference`; enum fields use textual `EnumType.STRING`; and response mapping is co-located on response records.
- Required proof: the expanded reference/security/concurrency gate covers 77 tests with no failures, errors, or skips; the full current-working-tree suite and Maven `verify` pass 266 tests, with Spotless also passing. The supervising user explicitly authorized retention of the mixed identity/session/abuse-protection standards alignment.
- Review status: review fixes added the omitted value-object, enum-wire, database-constraint, validation, inactive-reference, zero-query range, exact-boundary, malformed-enum, alias-cap/sorting/immutability, abuse-capacity, and optimistic-concurrency proof. Alias-only replacement now uses an immediate version compare-and-swap, optional control flow no longer uses `orElse(null)`, and over-specific standards text was generalized. No commit, branch, history, or remote operation was performed; all changes remain unstaged.
- Non-goals: global reference administration, calendar/import workflows, observations/providers/live rates/prices, ledger/financial behavior, durable jobs, persistent signing keys, further authentication/authorization, cross-site deployment infrastructure, and frontend work remain deferred.
- Next planning artifact: `PR-021-durable-platform-job-execution.md` is drafted around the existing V1 job table, V2.1 lifecycle hardening, idempotent submission, `SKIP LOCKED` claim/fencing, heartbeat/retry/recovery, bounded execution, and safe observability. It is not active while `CURRENT.md` remains on PR-020.

## Latest implementation checkpoint — PR-019

- Date: 2026-08-15.
- Starting commit: `d1eea9a`, the accepted PR-018 commit.
- Implemented capability: typed authenticated identity (`AuthenticatedIdentity`, `AuthenticatedIdentityResolver`), `GET /api/v1/me`, owner-scoped logical family list/detail (`GET /api/v1/auth/sessions`, `GET /api/v1/auth/sessions/{familyId}`) with keyset pagination and URL-safe Base64 cursor codec, current/all/selected family revocation (`POST /api/v1/auth/logout`, `DELETE /api/v1/auth/sessions/{familyId}`) using pessimistic owner lock, and exact browser refresh-cookie clearing (`RefreshTokenCookieHeader.clear()`).
- Implemented security boundary: append-only safe authentication/session security events on `platform.security_event` via `SecurityEventRecorder` with `Propagation.REQUIRES_NEW` for anonymous failure/throttle events and standard `REQUIRED` propagation for session mutations, plus bounded process-local registration/login/refresh throttling (`AuthenticationAbuseProtection`) with per-bucket window tracking, throttle rollback on event failure, and parameterless trace-correlated 429 responses.
- Required proof: owner-scoped JdbcClient pagination with bounded query count assertion, real PostgreSQL refresh/revocation concurrency and lock serialization, real event persistence failure rollback and throttle in-memory unblocking, deterministic fixed-clock/capacity abuse behavior, real-filter endpoint/ownership/cookie/statelessness tests, full suite (211 tests), Spotless, and Maven `verify`.
- Concurrency/abuse evidence: PostgreSQL tests cover current/selected/all revocation against refresh in both lock acquisition orders; fixed-clock tests cover equality expiry, capacity fail-closed behavior, mixed windows, and compare-and-set rollback; event integration tests cover exact scopes, persistence rollback, and throttle unblocking.
- Review result: implementation self-audit found and corrected extreme/blank cursor handling, exact source-key domains, one-clock mutation-event timestamping, strict immutable event details, and stable user-revocation reasons. Independent user review remains pending.
- Sizing: fixed PR-018 comparison baseline of 381 production additions/30 deletions/12 files; PR-019 actual production surface is 1,769 additions/49 deletions across 36 production files (4.64× PR-018 additions), below the fixed five-times floor. The complete specified slice was kept scoped without padding or speculative infrastructure.
- Non-goals: jobs, persistent keys, OIDC/recovery, roles/permissions/households, account deletion/export, cross-site deployment/trusted-proxy/general CSRF infrastructure, schema/dependency/frontend/reference/financial work.

## Latest implementation checkpoint — PR-018

- Date: 2026-08-15.
- Starting commit: `7f55288` (PR-017 acceptance).
- Production change: one append-oriented `DeviceSession` rotation/reuse lifecycle, one transactional owner-lock-first rotation service, one hash-only projection lookup, one shared exact refresh-cookie helper, one JSON-only public refresh controller, and the exact refresh POST permit in the existing stateless bearer chain.
- Security/transaction behavior: normal rotation consumes one generation and creates one successor with unchanged absolute expiry and successor-bound access token; replaced-token reuse commits active-family revocation before the controller returns the uniform `401`; JWT/runtime failure rolls back the predecessor and successor; duplicate concurrent use is serialized by the PostgreSQL owner-row lock.
- Persistence note: the unchanged immediate replacement FK is satisfied by consuming and flushing the predecessor, persisting and flushing the active successor through JPA, then linking and flushing the predecessor. The final committed row is clean and no migration/schema/index/constraint changed.
- HTTP behavior: explicit response-body or exactly-one-cookie credential channels, JSON-only content type, no-store/no-cache success, exact shared hardened cookie serialization, trace-correlated safe failures, no bearer challenge for refresh credential failures, and no servlet session/context persistence.
- Coverage: `DeviceSessionRotationTest`, `RefreshSessionRotationControlFlowTest`, `RefreshSessionRotationServiceTest`, and `LocalRefreshHttpTest` cover pure invariants, ordered short-circuits, real PostgreSQL writes/rollback/reuse/concurrency, both HTTP modes, non-leakage, route scope, content-type/CORS boundaries, and statelessness. Existing login/bearer/refresh-authentication suites remain green.
- Verification: the exact focused command passed with 45 tests, the full `test` suite passed with 124 tests, `verify` passed with Spotless, and `git diff --check` passed; no tests were skipped.
- Follow-up: logout/session management, authorization/owner helpers, security events/abuse controls, cross-site deployment, persistent keys, jobs, frontend, and financial workflows remain outside PR-018.

## Latest implementation checkpoint — PR-017

- Date: 2026-08-15.
- Starting commit: `9fcbb69` (`install stateless bearer authentication`).
- Production change: one explicit `RefreshTokenDelivery` enum, one validated `LocalLoginRequest`, one nullable-cookie-aware `LocalLoginResponse`, one thin `LocalLoginController`, and two exact public POST matchers in the existing stateless API chain.
- HTTP behavior: response-body mode emits the raw refresh token only in JSON; cookie mode emits it only in one Spring `ResponseCookie` header with `/api/v1/auth`, `Secure`, `HttpOnly`, `SameSite=Strict`, host-only scope, positive whole-second `Max-Age`, and an expiry bounded by the session lifetime. Both modes return exact session/access metadata and explicit no-store/no-cache headers.
- Coverage: six real-filter PostgreSQL `MockMvc` cases cover committed response-body and cookie sessions, production decoder/session authentication, hash-only persistence, unchanged identity state, uniform credential failures, all requested structural/parsing boundaries, exact route scope, trace correlation, and no servlet session.
- Verification: `spotless:check`, the focused 29-test suite, the full 105-test suite, and `verify` pass with 0 failures, 0 errors, and 0 skipped tests. The cookie test asserts the exact refresh-session expiry truncated to whole seconds.
- Review status: correction cycle 1 fixed the exact refresh-cookie `Expires` value and completion wording. Independent whole-diff review passed with no remaining `MUST FIX` or `SHOULD FIX` findings; the reviewer reran the focused 29-test PostgreSQL suite, full 105-test suite, Spotless, and `verify` successfully. The user-owned acceptance commit is `7f55288`.
- Non-goals: no refresh endpoint/consumption/rotation/reuse handling, logout/revocation, cross-site cookie/CORS/CSRF policy, authorization, roles/permissions, persistent keys, security events, abuse controls, schema/dependency/frontend work, or financial behavior.

## Latest implementation checkpoint — PR-016

- Date: 2026-08-09.
- Starting commit: `3d86ff2` (`authenticate local access tokens`).
- Production change: Boot-managed servlet security and test-scoped MVC test starters, highest-precedence request tracing, one servlet-only stateless filter chain limited to `/api/v1/**`, and one entry point delegating safe authentication failures into the accepted global Problem Detail boundary.
- Security behavior: `POST /api/v1/auth/register` remains public; every other matched route requires the accepted strict decoder plus database-backed current-session converter; outside routes remain unmatched; authentication is never stored in an HTTP session.
- Coverage: the 4-case real-filter PostgreSQL `MockMvc` suite proves exact chain scope, committed public registration, uniform trace-correlated `401` responses, valid authority-free JWT identity, unchanged persistence, and headerless follow-up rejection. Spotless, the exact 16-test focused gate, the full 99-test suite, and `verify`/package pass with 0 failures, 0 errors, and 0 skipped tests.
- Planning/test reconciliation: the active specification corrected the actual PR-006 filename and explicitly superseded only PR-015's historical no-chain assertion, retaining proof of one converter alongside exactly one production chain.
- Review status: correction-cycle-1 whole-diff review passed with no remaining `MUST FIX` or `SHOULD FIX` findings. The documentation-only correction reconciled four stale `STATE.md` claims about public registration, decoder HTTP wiring, HTTP bearer authentication, and Spring Security filter-chain configuration. PR-016 was accepted in local commit `9fcbb69` before PR-017 began.
- Non-goals: no production endpoint/DTO, login/refresh transport decision, roles/permissions/owner helper, refresh/logout mutation, schema/key-policy/frontend work, or financial behavior.

## Latest implementation checkpoint — PR-015

- Date: 2026-08-09.
- Production change: one direct Boot-managed `spring-security-oauth2-resource-server` library dependency, one exact owner-scoped `DeviceSessionRepository` derived lookup, and one read-only decoded-JWT converter; no starter, migration, entity/mapping, decoder/issuer/configuration, HTTP/API, filter chain, authorization, refresh, key-management, or frontend change.
- Security behavior: conversion requires canonical UUID `sub` and `sid` before observing the injected clock, then performs one session/user-scoped lookup. Only an unrevoked session expiring strictly after that observation for an enabled exact signed user produces Spring Security's standard `JwtAuthenticationToken`, named by the user UUID and carrying no authorities. Expected claim and current-eligibility failures use one constant safe `invalid_token` result and perform no writes.
- Coverage: three pure control-flow cases prove null/invalid-claim short-circuiting and ordered single clock/query work; four real PostgreSQL decoder-to-converter cases prove accepted authentication, missing/cross-user/revoked/expired/disabled rejection, exact derived-query owner scoping, unchanged user/session rows, bean registration, and the absence of a `SecurityFilterChain`.
- Verification: `spotless:check`, the 15-test focused suite, the full 95-test suite, and `verify`/package all pass; final Surefire totals are 95 tests, 0 failures, 0 errors, and 0 skipped.
- Independent review: passed with no `MUST FIX` or `SHOULD FIX` findings.
- Review/acceptance: accepted in local commit `3d86ff2` (`authenticate local access tokens`).
- Database/schema result: unchanged V1 schema and mappings; authentication performs a read-only owner-scoped lookup only.
- Follow-up: HTTP bearer extraction and security filter configuration, authentication/error delivery, authorization and owner helpers, roles/permissions, persistent keys, refresh rotation/reuse response, logout/revocation, security events, jobs, and abuse controls remain separate future units.

## Latest implementation checkpoint — PR-014

- Date: 2026-08-09.
- Production change: one `JwtDecoder` bean reusing the existing startup-local RSA public key and one package-local final validator; no dependency, migration, mapping, repository, service, HTTP/API, filter-chain, principal, refresh, key-management, or frontend change.
- Security behavior: decoding trusts only RS256 and requires the exact configured key ID, `access` type, issuer, singleton audience, canonical UUID `sub`/`jti`/`sid`, raw whole-second `iat`/`nbf`/`exp`, equal issue/not-before time, and a positive configured-lifetime-capped window. The injected clock is observed once with no skew; `nbf` equality is valid and `exp` equality is invalid. Repository-owned envelope failures return one constant safe `invalid_token` result.
- Coverage: four focused decoder/configuration cases use the real encoder and decoder to prove one-key/encoder/decoder construction, valid-envelope compatibility, unrelated-key and RS512 rejection before the validator, complete header/claim/time fail-closed behavior, fractional observed time, exact boundaries, one clock observation, and safe stable custom errors. The focused gate also preserves the accepted property and PostgreSQL issuance coverage.
- Verification: `spotless:check`, the 10-test focused suite, the full 88-test suite, `verify`/package, and `git diff --check` all pass; final Surefire totals are 88 tests, 0 failures, 0 errors, and 0 skipped.
- Independent review: passed after correction cycle 1 with no remaining `MUST FIX` or `SHOULD FIX` findings. The corrected finding was documentation-only: PR-013's specification still said `ACTIVE` and omitted accepted commit `7bb7c40`; both records now agree with `CURRENT.md`, `STATE.md`, and this report.
- Accepted commit: `4621473` (`decode local access tokens`).
- Database/schema result: unchanged V1 schema and mappings; decoding performs no persistence or network work.
- Follow-up: current user/session eligibility conversion and authenticated principals, HTTP bearer/token delivery, persistent keys, refresh rotation/reuse response, logout/revocation, security events, and abuse controls remain separate future units.

## Latest implementation checkpoint — PR-013

- Date: 2026-08-09.
- Production change: one public exact-input hash method in the existing concrete refresh-token generator, one derived unique-hash repository lookup, and one read-only authentication service; no migration, entity/mapping, dependency, configuration, API, HTTP, or frontend change.
- Security behavior: generation and presented-token authentication share one SHA-256 URL-safe unpadded Base64 path. Non-null attempts observe time, hash, and query once; only an unrevoked session expiring strictly after the observation for an enabled user returns its UUID. Unknown, revoked, expired-at-or-before-now, and disabled-user credentials fail through the same parameterless `INVALID_CREDENTIALS` without mutation or sensitive detail.
- Coverage: pure tests prove exact/deterministic arbitrary-string hashing, null-before-work behavior, and one ordered collaborator call; two PostgreSQL/Testcontainers cases prove exact active-session resolution, hash-only storage, equality-boundary expiry rejection, safe uniform failures, and unchanged user, identity, and complete session rows including `last_used_at`.
- Verification: `spotless:check`, the 14-test focused suite, the full 84-test suite, `verify`, and `git diff --check` all pass.
- Independent review: passed with no `MUST FIX` or `SHOULD FIX` findings.
- Accepted commit: `7bb7c40` (`authenticate opaque refresh sessions`).
- Database/schema result: unchanged V1 schema and mappings; the existing unique `identity.device_session.refresh_token_hash` constraint supplies lookup uniqueness.
- Follow-up: refresh rotation/replacement with locking and family-reuse response, HTTP token delivery, bearer authentication, logout/revocation/session management, persistent keys, security events, and abuse controls remain separate future units.

## Latest implementation checkpoint — PR-012

- Date: 2026-08-09.
- Production change: one immutable `LocalLoginResult` and one `LocalLoginService` with an ordinary write transaction; no existing production Java file, dependency, configuration, migration, mapping, repository, HTTP/API, or frontend change.
- Transaction behavior: credential verification, initial refresh-session issuance, and access-token issuance run once in order through their accepted Spring beans and participate in the outer default-`REQUIRED` transaction. Successful output preserves both issuing-service results exactly; invalid credentials short-circuit; unchecked JWT encoding failure escapes unchanged and rolls back the flushed session.
- Coverage: five PostgreSQL/Testcontainers cases prove exact committed composition and hash-only refresh persistence, invalid-credential short-circuit before ID/JWT issuance, unchanged nullable device-label pass-through, flushed-session visibility followed by rollback on JWT failure, identity-row immutability, and null credential caller contracts.
- Verification: `spotless:check`, the 17-test focused suite, the full 78-test suite, `verify`, and `git diff --check` all pass.
- Independent review: passed with no `MUST FIX` or `SHOULD FIX` findings.
- Database/schema result: unchanged V1 schema and mappings; successful orchestration writes only the accepted initial `identity.device_session` row.
- Follow-up: HTTP login/token delivery, production bearer validation and principals/filter chains, persistent signing keys, refresh lookup/rotation/reuse detection, logout/revocation/listing, security events, and abuse controls remain separate future units.

## Latest implementation checkpoint — PR-011

- Date: 2026-08-09.
- Production change: Boot-managed `spring-security-oauth2-jose`, one immutable access-token configuration record, one disposable startup-local 2048-bit RSA signer/encoder configuration, one immutable issuance result, and one read-only session-bound issuance service; no endpoint, decoder/resource server, migration, mapping, repository query, runtime configuration, or frontend change.
- Security behavior: exact RS256 `access` tokens carry only `iss`, `sub`, singleton-string `aud`, `iat`, `nbf`, `exp`, `jti`, and `sid`; one full-precision clock observation is normalized down to whole-second NumericDate values; expiry is capped without outliving the full-precision backing session; missing, revoked, expired, disabled-user, and unrepresentably near-expiry sessions fail uniformly before token-instance ID generation; compact tokens and token IDs are not persisted.
- Coverage: two pure configuration/key cases plus four PostgreSQL/Testcontainers service cases cover defaults and invalid properties, singleton RSA signer construction, raw JSON header/claim representation, cryptographic verification, unrelated-key rejection, fractional-time normalization, fresh token IDs, session-expiry capping, near-expiry rejection before `jti`, and unchanged persisted state.
- Verification after reviewer corrections: `spotless:check`, the amended 17-test focused suite, the full 73-test suite, `verify`, and `git diff --check` all pass.
- Independent review: passed after the correction cycle with no remaining `MUST FIX` or `SHOULD FIX` findings.
- Database/schema result: unchanged V1 schema and mappings; no Flyway migration, constraint, index, entity annotation, repository method, or query was added or modified.
- Follow-up: HTTP login/token delivery, production bearer validation and principals/filter chains, persistent signing keys, refresh lookup/rotation/reuse detection, logout/revocation/listing, security events, and abuse controls remain separate future units.

## Latest implementation checkpoint — PR-010

- Date: 2026-08-09.
- Production change: one positive/defaulted refresh-session lifetime property, one secure opaque-token generator, two immutable application results, one initial `DeviceSession` factory, and one transactional issuance service; no endpoint, JWT, migration, mapping, repository-query, dependency, or frontend change.
- Security behavior: eligible-user issuance generates 32 random bytes, returns the unpadded Base64url raw token once, stores only the unpadded Base64url SHA-256 hash, and initializes an independent active family whose ID equals its session ID. Missing and disabled users fail identically before token/session creation.
- Coverage: two configuration-binding cases, one pure token/hash case, and four PostgreSQL/Testcontainers service cases cover the 30-day default, invalid lifetimes, token entropy/encoding/hash behavior, initial persistence, optional labels, independent families, eligibility rejection, and raw-token non-persistence.
- Verification: `spotless:check`, the 16-test focused suite, the full 67-test suite, `verify`, and `git diff --check` all pass.
- Database/schema result: unchanged V1 schema and mappings; no Flyway migration, constraint, index, annotation, or repository query was added or modified.
- Follow-up: HTTP login/access-token delivery, refresh lookup/rotation/reuse detection, logout/revocation/listing, principals/filter chains, security events, and abuse controls remain separate future units.

## Latest implementation checkpoint — PR-008

- Date: 2026-08-09.
- Production change: one `INVALID_CREDENTIALS` identity error, one derived local-identity lookup, and one read-only application service returning only the enabled user's UUID; no endpoint, DTO, migration, entity, or web-security change.
- Credential behavior: `LOCAL` lookup uses `Locale.ROOT` lowercase normalization; each attempt calls `PasswordEncoder.matches(...)` exactly once; missing/null hashes use one service-owned dummy hash; unknown, wrong, unusable, and disabled credentials fail identically. The service has no validation annotations, per explicit user direction.
- Coverage: one pure fallback test plus four PostgreSQL/Testcontainers service tests cover construction-time dummy encoding, success/casing, indistinguishable failures, disabled accounts, null hashes, and persisted-state immutability.
- Verification: `spotless:check`, the 13-test focused suite, the full 60-test suite, `verify`, and `git diff --check` all pass.
- Database/schema result: unchanged V1 schema; no Flyway migration, constraint, index, or entity mapping was added or modified.
- Accepted commit: `61fe8a6` (`pr-008`).
- Follow-up: `CURRENT.md` points to the documentation-only PR-009 reconciliation; structural validation belongs at the future HTTP login boundary, while HTTP login and durable authentication/session behavior remain separate future units.

## Latest implementation checkpoint — PR-007

- Date: 2026-08-09.
- Production change: one `POST /api/v1/auth/register` controller with request/response records under the identity module's `input` and `output` packages; no migration, entity, repository, service, error-contract, trace-contract, or frontend changes.
- HTTP coverage: success/commit, case-insensitive duplicate, validation-before-write, and malformed-body cases against PostgreSQL Testcontainers and the real Spring web context.
- Verification: Spotless, the 18-test focused suite, the full 55-test suite, and `verify` all pass; `git diff --check` passes.
- Database/schema result: unchanged V1 schema; no Flyway migration added or modified.
- Follow-up: user-owned commit. `CURRENT.md` now points to the separately bounded PR-008 credential-verification specification; its implementation has not started.

## Inputs analyzed

### Repository database dump

- Repository path: [db-dump.sql](db-dump.sql)
- SHA-256: `FABDA56B6737FA710A37436FFA731BDFB8852EB5275BDF5000C981C1FD2AE8BD`
- Dumped from/by PostgreSQL 15.1.
- Approximately 47.1 KB.
- 28 table declarations: `account` 5, `instrument` 6, `portfolio` 8, `public` 9.
- No `COPY` or `INSERT INTO` statements: the supplied file contains schema, not application/reference/user data.
- The dump has clean/drop statements and is useful for inventory, but it is not accepted as the future baseline. The new clean rewrite targets the separately created `extreme_accounting` database.

### Repository backend

Inventory at report time:

- 127 Java source files under `src/main/java`;
- 24 JPA entity classes;
- 12 REST controllers;
- 13 Spring service classes;
- approximately 15 Spring Data repository/MyBatis mapper types;
- one Java test, `ServerTests.contextLoads`;
- 13 Flyway scripts, versions `V2` through `V14`;
- one Spring Boot/Maven module;
- legacy backend currently declares Java 21 in Maven;
- legacy backend currently uses Spring Boot 3.5.3, PostgreSQL, JPA/Hibernate, MyBatis, Flyway and Spring Security.

These are observations about the code being replaced, not the rewrite target.

Current coarse code areas are `config`, `domain`, `integration`, `repository`, `rest`, `security`, and `service`, with nested account/instrument/portfolio concerns.

## Confirmed strengths

- A single Spring Boot deployable is already the right physical architecture.
- PostgreSQL and Flyway are appropriate for the target accounting/data model.
- `BigDecimal` is used for trades, positions, rates, and prices.
- JPA entities have owner relationships and several repositories already filter by principal/user.
- Controllers are generally thinner than services.
- Instrument/market subtype concepts can evolve into the target reference model.
- Spring's JDBC stack is available for concise complex read models; the scratch plan removes the extra MyBatis/QueryDSL layer unless later evidence justifies it.
- OpenAPI generation and the existing React API-client approach can support a later stable `/api/v1` contract.

## Confirmed critical gaps

### Database/bootstrap

- There is no `V1`; `V2` begins by altering tables that are absent from the repository migration history.
- Flyway is disabled in default development configuration and enabled only in the production profile.
- `baseline-on-migrate: true` can hide an unowned schema instead of proving reconstruction.
- The dump includes six legacy Spring Batch tables even though no corresponding backend batch job was found.
- Spring Batch tables/config exist despite no corresponding backend batch-job implementation being part of the reviewed code.
- `schema.sql` contains only a semicolon and should not participate in initialization.
- Stable seed data depends on legacy state and hardcoded IDs, including BIST market ID `3`.

### Financial correctness

- Commission reaches requests but `Transaction.buy/sell` stores `BigDecimal.ZERO`; `Position.buy/sell` excludes it from totals.
- A buy/sell mutates the current position immediately; there is no canonical cash account or funding source.
- Backdated transactions do not rebuild in economic order.
- Destructive `undo` removes the latest list entries instead of creating a correction/reversal fact.
- `BigDecimal.equals` is used in zero/full-close paths where scale-sensitive equality can be wrong.
- There is no idempotency key or concurrency-safe unique projection identity.
- Position, transaction, history, and snapshot records duplicate/derive overlapping state without a reliable rebuild contract.
- Card, bank, debt, bill, spending, income, claim, and transfer semantics do not yet exist.

### Valuation/data

- One mutable `currencies.exchange_rate` and `convert_currency` function cannot support historical FX.
- `instrument_snapshots` stores latest state only and no provider/revision/quality/licence history.
- Current data integrations depend on external Forex, BIST/Sabah, and Gemini paths and have no common manual/synthetic normalized source.
- Market, snapshot, and position currency columns lack complete foreign-key/reference enforcement.
- Missing/stale/synthetic/manual coverage is not a first-class API result.

### Schema/entity drift

- The dump makes `positions.instrument_id` nullable while JPA declares the association mandatory.
- Position total precision differs between dump and entity.
- Timestamps mix `timestamp without time zone` and `timestamptz` even though Java uses `Instant`.
- Daily snapshot quantity is integer while positions support decimal quantity.
- Generated daily totals round to scale 2 and percentage expressions can yield null despite `NOT NULL` declarations.
- Duplicate indexes exist for portfolio/user and position/portfolio.
- Transaction notes/metadata state differs from the apparent migration intention.
- A PostgreSQL `tag_type` remains despite its table being removed.

### Security/operations

- RSA private/public key material is tracked in the repository even though `.gitignore` now lists it.
- Google token verification does not visibly enforce the application's exact client audience in the reviewed flow.
- Default local database password and provider placeholder keys remain in configuration; acceptable only for disposable local development, not a deployment contract.
- Docker packaging skips tests.
- The MyBatis `type-handlers-package` points to `dev.canverse.finances.infrastructure.mybatis`, while code uses `dev.canverse.stocks...`.
- README claims about a live demo/deployment conflict with the user's current statement that the project is not deployed; this should be corrected during Stage 0.

## Decisions recorded in this update

| Decision                                                                                                                                       | State                                                 |
| ---------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| Development database can be dropped/recreated                                                                                                  | Accepted from user request                            |
| New rewrite database is `extreme_accounting`                                                                                                   | Accepted from user update                             |
| Use one deployable modular monolith                                                                                                            | Accepted                                              |
| Direct cross-module references/coupling are allowed                                                                                            | Accepted                                              |
| Avoid excessive files/layers/interfaces                                                                                                        | Accepted                                              |
| Rewrite is a clean cut, not an in-place legacy-data migration                                                                                  | Accepted from latest request                          |
| Flyway owns all DDL; JPA contains mapping annotations only                                                                                     | Accepted from latest request                          |
| Indexes, keys/FKs, unique/check constraints, defaults/generated SQL and cascades stay out of entities                                          | Accepted from latest request                          |
| Use JPA plus `JdbcClient`; omit MyBatis/QueryDSL unless later justified                                                                        | Recommended for the minimal scratch rewrite           |
| Replace legacy migration chain with clean baseline                                                                                             | Recommended and placed first in implementation plan   |
| Use immutable activities plus rebuildable projections                                                                                          | Recommended and already detailed in prior design docs |
| Implement every feature with manual/import/synthetic data before relying on providers                                                          | Accepted and planned                                  |
| Keep synthetic data clearly isolated/labeled                                                                                                   | Required safety/trust rule                            |
| Manual entry and file import are permanent primary financial-data workflows                                                                    | Accepted from latest product decision                 |
| Bank/Open Banking, card/broker synchronization and payment initiation are outside the near-term/R0–R16 roadmap                                 | Accepted from latest product decision                 |
| Onboarding supports explicit opening state and historical-coverage boundaries                                                                  | Accepted from latest product decision                 |
| Planned obligations may name intended funding accounts but actual payments are manually recorded/confirmed ledger facts                        | Accepted from latest product decision                 |
| Shared-expense claims retain originating-payment provenance and support partial/multi-claim settlement without income/spending double-counting | Accepted from latest product decision                 |
| Backend only for implementation planning                                                                                                       | Accepted                                              |
| Do not create microservices or separate Maven modules                                                                                          | Fixed plan decision                                   |

## Decisions confirmed before implementation

The 2026-08-07 document harmonization establishes these implementation rules:

- Rewrite technology baseline: Java 25 + Spring Boot 4.1.0. Spring Framework/Spring Data/Hibernate/Jackson versions remain Boot-managed by default (Spring Framework 7.0.8, Spring Data JPA 4.1.0 and Hibernate ORM 7.4.1.Final in Boot 4.1.0); Jakarta Persistence 3.2 is the stable persistence contract.
- Stable Java 25 and Jakarta Persistence 3.2 features are encouraged when useful; Java preview/incubator features, Jakarta Persistence 4, Hibernate ORM 8 and snapshot/milestone/RC framework dependencies are excluded from normal feature PRs.
- [../engineering/coding-standards.md](../engineering/coding-standards.md) is the repository-wide coding standard. Root [AGENTS.md](../../AGENTS.md) is the agent entry point and directs coding agents to those standards plus the active PR spec automatically.
- R0–R16 remain roadmap increments, not PR sizes. Implementation is delivered through small human-reviewable specifications under `docs/implementation/`, with the current spec selected by `docs/implementation/CURRENT.md`.

- [backend-master-plan.md](backend-master-plan.md) is authoritative for implementation order; older in-place repair/backfill/shadow-mode instructions are historical analysis only.
- The rewrite is a clean replacement. Legacy application rows/IDs are not migration requirements because the application is undeployed and the target database is disposable.
- Cross-cutting financial semantics live in [accounting-contract.md](accounting-contract.md); a code change that changes those semantics updates the contract and small golden fixtures first.
- Demo financial data is loaded through normal application command services; synthetic observations use the normal observation-ingestion path. Demo code never directly seeds derived projections.
- Synthetic/manual providers are in-process implementations. Standalone mock HTTP servers are not part of the demo architecture.
- WireMock/equivalent is reserved for tests of real HTTP provider adapters; business/calculation tests remain no-network.
- The full household demo is an end-to-end/integration fixture. Small hand-worked fixtures remain the authoritative proof of calculation mathematics.
- Observation source selection is an explicit policy covering source priority, staleness, no-look-ahead and missing/fallback behavior.
- Rebuildable projections use `CURRENT`, `STALE`, `REBUILDING`, and `FAILED` semantics and surface consistency metadata.
- `/api/v1`, decimal strings, stable problem codes, owner authorization, idempotency and source/coverage metadata are invariants from the first relevant endpoint, not features postponed to R16.
- Manual account setup and file import are permanent primary workflows; the product must remain fully usable without live financial-account connections.
- Onboarding supports explicit opening balances/state and reports incomplete historical coverage rather than fabricating prior activity/performance.
- Planned card/bill payments may reference an intended funding account, but the app does not initiate payment; the user records/confirms the actual settlement and the ledger links it to the obligation.
- Claims/shared expenses retain provenance to the original payment/split and support partial, multi-payment and multi-claim settlement; repayments are not income and do not duplicate spending.
- Open Banking, bank/card synchronization, broker synchronization and payment initiation are explicitly deferred beyond R0–R16; no speculative connector framework is built.

## Implementation stage status

| Stage | Result                                                            | Status      |
| ----: | ----------------------------------------------------------------- | ----------- |
|     0 | Risk containment and contract lock                                | Not started |
|     1 | Fresh Flyway baseline and Testcontainers harness                  | Not started |
|     2 | Financial accounts, immutable ledger and current trade cutover    | Not started |
|     3 | Observation platform and deterministic synthetic dataset          | Not started |
|     4 | Reconciled net worth and honest investment analytics              | Not started |
|     5 | Decision Replay and localized comparison policies                 | Not started |
|     6 | Everyday spending/income/bills/cards/documents                    | Not started |
|     7 | Commitments, resilience, goals, irregular income and briefs       | Not started |
|     8 | IOUs, shared expenses and selective household money               | Not started |
|     9 | Purchases/recovery/utilities/projects/protection/freelancer flows | Not started |
|    10 | Versioned tax/government calendar policy engine                   | Not started |
|    11 | Physical-asset lifecycle and TCO                                  | Not started |
|    12 | Stable mobile API and operational hardening                       | Not started |

### Scratch-rewrite increment status

| Increment | Implementation result                                        | Status                                                                                                           |
| --------: | ------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------- |
|        R0 | Preserve evidence and replace backend skeleton               | Not started                                                                                                      |
|        R1 | Foundation, identity, auth, sessions and jobs                | In progress — PR-018 refresh rotation/reuse/HTTP is accepted in `d1eea9a`; PR-019 local identity/session security is active; durable jobs and later persistent-key/OIDC/recovery work remain deferred |
|        R2 | Canonical references and deterministic seeds                 | In progress - PR-020 implementation complete in the working tree; awaiting user review and acceptance             |
|        R3 | Accounts/ledger/funding/balances — FT-31                     | Not started                                                                                                      |
|        R4 | Investing parity, funded trades and imports                  | Not started                                                                                                      |
|        R5 | Observation platform and synthetic universe                  | Not started                                                                                                      |
|        R6 | Timeline/net worth/investment truth — FT-01/02/11            | Not started                                                                                                      |
|        R7 | Decision Replay and comparison — FT-06/07/08/09/12           | Not started                                                                                                      |
|        R8 | Spending/recurrence/bills — FT-15/03/18                      | Not started                                                                                                      |
|        R9 | Income/cards/debt/documents — FT-20/29/23                    | Not started                                                                                                      |
|       R10 | Resilience/goals/irregular income/brief — FT-04/05/10/14     | Not started                                                                                                      |
|       R11 | Household/people/claims/shared money — FT-13/16/17/28        | Not started                                                                                                      |
|       R12 | Purchases/recovery/restricted value — FT-19/22/24            | Not started                                                                                                      |
|       R13 | Utilities/projects/protection/freelancer — FT-25/26/27/21    | Not started                                                                                                      |
|       R14 | Tax/government policy calendar — FT-30                       | Not started                                                                                                      |
|       R15 | Physical-asset lifecycle/TCO — FT-32                         | Not started                                                                                                      |
|       R16 | Stable API, export/delete, operations and optional providers | Not started                                                                                                      |

## Documentation progress preserved

The review folder now contains:

- [README.md](README.md) — index and executive assessment;
- [backend-master-plan.md](backend-master-plan.md) — authoritative implementation sequence and supersession rules;
- [accounting-contract.md](accounting-contract.md) — authoritative shared financial semantics and golden-fixture contract;
- [progress-report.md](progress-report.md) — this status/checkpoint;
- [backend-audit.md](backend-audit.md) — code/schema/security findings;
- [business-logic-and-analytics-design.md](business-logic-and-analytics-design.md) — ledger/valuation/calculation target;
- [implementable-features.md](implementable-features.md) — FT-01 through FT-32;
- [cash-accounts-and-funding-design.md](cash-accounts-and-funding-design.md) — account/funding/negative-balance details;
- [real-asset-lifecycle-tco-design.md](real-asset-lifecycle-tco-design.md) — physical-asset/TCO details;
- [mobile-api-readiness.md](mobile-api-readiness.md) — future Expo-safe contract;
- [prioritized-roadmap.md](prioritized-roadmap.md) — original correctness-first roadmap retained as historical analysis; implementation steps that conflict with the scratch rewrite are superseded;
- [product-direction.md](product-direction.md) — product differentiation.
- [../engineering/coding-standards.md](../engineering/coding-standards.md) — Java 25/Spring Boot 4/JPA/Hibernate/Spring/database/API/test coding standards used by implementation agents.
- [../implementation/README.md](../implementation/README.md) — small-PR execution model, active-spec mechanism and PR specification rules.

These documents are complementary. The master plan is the order of execution; `accounting-contract.md` governs shared financial semantics; the feature/TCO/cash documents contain deeper acceptance behavior.

Root [AGENTS.md](../../AGENTS.md) is the automatic agent entry point. It tells compatible coding agents to load the repository coding standard and `docs/implementation/CURRENT.md` before implementation, so normal prompts do not need to repeat those instructions.

## Immediate next-session checklist

Start here and do not begin broader feature work first:

1. Review/finalize [accounting-contract.md](accounting-contract.md), including opening-state/coverage and planned-payment semantics, before writing financial tables.
2. Create the rewrite branch/tag and record its commit.
3. Confirm connectivity to the user-created empty `extreme_accounting` database without applying the legacy dump.
4. Execute R0 from the master plan: dependency/backend skeleton reset while leaving `src/main/web` unchanged.
5. Remove/rotate tracked RSA signing material and make local keys environment/generated.
6. Pin Spring Boot 4.1.0, enforce Java 25, keep Boot-managed dependency versions, disable preview features, and add Testcontainers PostgreSQL.
7. Write an empty-database migration/context integration test that currently fails because replacement `V1` does not exist.
8. Implement `V1__foundation.sql`, with every key/FK/index/check/default defined in SQL and only persistence mapping annotations in JPA.
9. Implement `V2__reference.sql` and stable reference seeds.
10. Begin R3 with `FinancialAccount`, cash pockets, immutable activity/postings and small hand-worked deposit/transfer/idempotency fixtures.

The first meaningful checkpoint is not “all tables created.” It is:

> A clean checkout can start on an empty PostgreSQL database, then post and replay a deposit, transfer, broker funding and fee-aware trade exactly once.

## Risks to monitor

| Risk                                                   | Current level                    | Next control                                                                                      |
| ------------------------------------------------------ | -------------------------------- | ------------------------------------------------------------------------------------------------- |
| Building features on an unreconstructable schema       | Critical                         | Fresh V1 + empty migration test                                                                   |
| Incorrect financial results becoming harder to migrate | Critical                         | Ledger cutover before new money features                                                          |
| Tracked signing key reuse                              | Critical if ever shared/deployed | Rotate/remove immediately                                                                         |
| Fake data mistaken for real data                       | High for future demo             | Dataset isolation, synthetic provenance and command-path-only demo loader from Stage 3            |
| Scope expansion across 32 features                     | High                             | Enforce stage exit gates and one vertical slice at a time                                         |
| Too many architectural files/interfaces                | Medium                           | Follow minimal code pattern; `accounting-contract.md` is the only new cross-cutting contract file |
| No live data/provider access                           | Expected, not a blocker          | Manual/CSV/synthetic implementations first                                                        |
| Jurisdiction rules becoming stale or misleading        | High                             | Version/source/review/sample labels; suppress stale policies                                      |
| Almost no automated tests                              | Critical                         | Testcontainers and golden fixtures before refactor                                                |
| Untracked review documents being lost                  | High                             | Review and commit `docs/review/` with the implementation branch                                   |

## Verification performed for this checkpoint

- Read the complete repository schema dump and extracted schemas, tables, types, function, constraints, indexes and foreign keys.
- Confirmed the repository dump contains no data statements and calculated its SHA-256.
- Read all active Flyway migrations `V2`–`V14` and relevant application/Flyway/Batch configuration.
- Compared dump columns with key current JPA entities for position, transaction, snapshots, instruments and market currencies.
- Counted the current backend/test/controller/service/repository inventory.
- Confirmed tracked certificate files, the current root `AGENTS.md`, and the current untracked documentation worktree state.
- Reused the earlier full backend review instead of re-reading the React application, consistent with backend-only scope.

No database was connected to, dropped, restored or initialized; the existence of `extreme_accounting` is accepted from the user's update. No migration was replaced, no dependencies were installed, and no Java/application behavior was changed during this checkpoint. Runtime tests were not rerun because this task requested a plan/progress report; the previous build verification remains documented in [README.md](README.md).

## Update protocol for future sessions

At the end of every implementation session, update this file with:

1. date and stage;
2. files/migrations changed;
3. tests run and exact result;
4. newly completed acceptance criteria;
5. discovered schema/business changes;
6. next three concrete tasks;
7. whether the worktree contains uncommitted/untracked progress.

Do not mark a stage complete until its exit gate in [backend-master-plan.md](backend-master-plan.md) is satisfied.
