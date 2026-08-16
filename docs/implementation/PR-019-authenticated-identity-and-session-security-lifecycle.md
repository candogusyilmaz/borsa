# PR-019 — Authenticated identity and session security lifecycle

Status: **ACTIVE**

## Goal

Complete the locally authenticated user's identity and device-session security lifecycle. An authenticated client can resolve its server-owned user/session identity, read `/me`, inspect its logical device-session families, log out the current or all devices, and revoke one owned family. Every mutation follows PR-018's owner-lock discipline, immediately invalidates affected refresh and access credentials, clears the browser refresh cookie when the current family is ended, and writes safe durable security events. The public registration, login, and refresh boundaries also gain bounded process-local abuse protection with stable throttling errors and deterministic tests.

This is one local-identity security subsystem increment. Principal resolution, owner-scoped reads, session-family mutation, logout delivery, security events, and authentication throttling are combined because each depends on the same authenticated identity, trace/error boundary, session-family model, and security integration tests.

## Sizing and boundary rationale

- **Fixed comparison baseline:** accepted PR-018 (`d1eea9a`) added 381 and removed 30 production Java lines across 12 production files, excluding tests and documentation.
- **Required floor:** PR-019 must deliver at least five times PR-018's substantive production implementation surface. With comparable density, plan for approximately **1,900–2,700 gross production-line additions** across roughly **30–45 new or materially changed production files**. Tests and documentation do not count toward this floor.
- **Expected layers:** identity application/configuration/domain/infrastructure/input/output/web code; platform security-event domain/application persistence code; existing security/error/cookie integration; no frontend.
- **Intentionally combined steps:** authenticated identity resolution has no meaningful standalone outcome without `/me` or owner-scoped resources; session reads and revocation share the same logical-family projection; logout is the current-family form of revocation; durable events must be transactionally aligned with those mutations; login/register/refresh throttling and failure events share the same public authentication boundaries and safe error contract.
- **Review boundary:** the reviewer can assess one question—whether the complete local identity/session surface is safe under ownership, concurrency, credential-delivery, audit, and abuse conditions. Durable jobs, signing-key persistence, OIDC, roles/households, and reference data are independent state machines or capabilities and remain outside this PR.
- **No padding:** if a correct implementation of all specified behavior is materially below the fixed five-times floor, stop and report the sizing conflict instead of adding unrelated cleanup, abstractions, or later-roadmap behavior.

## Source documents

- `docs/review/backend-master-plan.md` — R1 items 5, 7, 9, and 10; R1 session endpoints and exit gate; API/security invariants
- `docs/review/backend-audit.md` — SEC-003, SEC-004, SEC-005, and SEC-006
- `docs/review/mobile-api-readiness.md` — Authentication for web and mobile; abuse and recovery
- `docs/engineering/coding-standards.md`
- `docs/implementation/PR-002-v1-foundation-database.md` — accepted device-session and security-event storage
- `docs/implementation/PR-015-database-backed-access-token-authentication.md`
- `docs/implementation/PR-016-http-bearer-authentication-boundary.md`
- `docs/implementation/PR-017-http-local-login-and-token-delivery.md`
- `docs/implementation/PR-018-refresh-session-rotation-and-http-refresh.md`

`docs/review/accounting-contract.md` is not required because this PR contains no financial behavior.

## Starting state

Starting commit: **`d1eea9a`**, the accepted PR-018 commit.

The starting implementation has:

- one strict stateless bearer chain for `/api/v1/**`;
- public registration, login, and JSON-only refresh POSTs;
- authority-free `JwtAuthenticationToken` instances named by the canonical user UUID and retaining validated `sub` and `sid` claims;
- current database-backed user/session eligibility checks on every bearer authentication;
- initial refresh-session issuance and owner-locked append-oriented rotation with fixed family expiry and reuse response;
- exact response-body or same-site host-only cookie refresh delivery;
- V1 `identity.device_session` family history and append-only `platform.security_event` storage, but no security-event write path;
- no `/api/v1/me`, owner/principal helper, session query API, logout, user-directed revocation, auth throttling, roles, or permissions.

PR-018's focused 45-test gate, full 124-test suite, Spotless, and Maven `verify` passed with no failures, errors, or skipped tests. Its completion record documents the immediate replacement-FK write order and the owner-row lock as the accepted serializer for every later device-family mutation.

The worktree also contains the user's unrelated `AGENTS.md`/command-playbook documentation changes. Preserve them and perform no Git operation.

## Scope

### 1. Establish one typed authenticated-identity boundary

Add one immutable application value containing exactly the authenticated:

```java
UUID userAccountId
UUID sessionId
```

Add one cohesive resolver used by every new protected identity controller. It must:

1. accept the current Spring `Authentication` supplied by the established security context;
2. require an authenticated `JwtAuthenticationToken` retaining the validated `Jwt`;
3. parse canonical UUID `sub` and `sid` claims and require the token name to equal canonical `sub`;
4. return only the typed IDs above;
5. fail with the existing safe `INVALID_CREDENTIALS` outcome if the context is missing or internally inconsistent;
6. perform no repository lookup, clock read, JWT decode, signature validation, authorization decision, or mutation.

The resolver relies on the accepted decoder/converter for cryptographic and current-state authentication. Do not add a custom `Authentication`, `UserDetailsService`, role/authority model, annotation framework, static thread-local holder, or user ID supplied by a request body/query/path.

### 2. Add owner-scoped current-user and logical-session reads

Add one read-only current-user query and expose exactly:

```text
GET /api/v1/me
```

The response contains only:

```java
UUID id
String email
Instant createdAt
```

Do not expose `emailNormalized`, password/auth-identity data, disabled timestamps, refresh hashes, internal update fields, roles, or permissions.

Add owner-scoped logical device-session reads:

```text
GET /api/v1/auth/sessions?limit=&cursor=
GET /api/v1/auth/sessions/{familyId}
```

One response item represents one `family_id`, not one refresh-token generation. It contains exactly:

```java
UUID familyId
UUID latestGenerationId
String deviceLabel
Instant createdAt
Instant lastUsedAt
Instant expiresAt
Instant endedAt
DeviceSessionStatus status
boolean current
```

`deviceLabel`, `lastUsedAt`, and `endedAt` are nullable. The output status enum contains exactly:

```text
ACTIVE
EXPIRED
REVOKED
COMPROMISED
```

Derive the family view as follows:

- `createdAt` is the earliest generation creation time;
- `lastUsedAt` is the latest non-null generation `last_used_at`;
- `expiresAt` is the common absolute family expiry and inconsistent expiries are an internal invariant failure;
- `latestGenerationId` is the terminal generation whose `replaced_by_session_id` is null;
- `ACTIVE` means the terminal generation is not revoked and the family expiry is after the single query clock observation;
- `EXPIRED` means the terminal generation is not revoked and expiry is at or before that observation;
- `COMPROMISED` means the terminal generation ended with `REUSE_DETECTED`;
- `REVOKED` covers user logout/user-directed revocation and does not expose the stored internal reason;
- `endedAt` is terminal revocation time, or the family expiry for `EXPIRED`, otherwise null;
- `current` is true when the authenticated bearer generation belongs to that family, even if a concurrent mutation ended it after filter authentication.

Use an explicit `JdbcClient` read model for family aggregation and keyset pagination rather than loading JPA graphs or one query per family. The query must be owner-scoped in SQL from its first predicate.

Pagination contract:

- default `limit` is 25; accepted range is 1 through 100;
- order is `(family created_at DESC, family_id DESC)`;
- fetch `limit + 1` rows to determine continuation;
- `nextCursor` is null when no next page exists;
- the cursor is opaque, unpadded Base64url over UTF-8 `v1|<epochSecond>|<nano>|<lowercase UUID>` using canonical JDK decimal/UUID strings for the exact last `createdAt` and `familyId` ordering key; decoding must re-encode to the identical input string;
- malformed, unknown-version, non-canonical, or trailing-data cursors fail with parameterless `IdentityErrorCode.INVALID_SESSION_CURSOR` and HTTP 400;
- a cursor is only an ordering token, never an ownership credential.

Missing or cross-owner family detail returns parameterless `IdentityErrorCode.SESSION_NOT_FOUND` with HTTP 404. Do not fetch globally and authorize afterward. Observe the injected clock once per page/detail query.

### 3. Implement owner-locked family revocation and logout

Extend `DeviceSession` with intention-revealing terminal revocation behavior and stable internal reasons:

```text
USER_LOGOUT
USER_LOGOUT_ALL
USER_REVOKED
```

The behavior must:

- revoke only a terminal generation with `replacedBySessionId == null` and `revokedAt == null`;
- set `revokedAt` and the supplied stable reason together;
- never overwrite `ROTATED`, `REUSE_DETECTED`, or any earlier terminal reason;
- be idempotent when the same family is already terminally revoked;
- reject an attempt to mark a historical/replaced generation as the terminal family state.

Add one transactional session lifecycle service with exactly three application operations:

1. revoke the family containing the authenticated `sessionId` (`CURRENT_SESSION` logout);
2. revoke every currently non-revoked family for the authenticated user (`ALL_SESSIONS` logout);
3. revoke one owned `familyId` selected by the user.

Every operation must follow this order:

1. validate non-null internal IDs before collaborator work;
2. lock the authenticated `identity.user_account` row with the accepted pessimistic owner lock;
3. perform all generation/family lookups after acquiring that lock;
4. observe the injected `Clock` exactly once;
5. resolve terminal generations after the lock and mutate them in deterministic `family_id` order;
6. flush session mutations and their security event(s) in the same transaction;
7. return only the information required for response/cookie behavior.

Specific semantics:

- current logout resolves the family from the authenticated generation ID, including when that generation has since become historical through a concurrent completed refresh;
- all-session logout revokes every terminal generation that still has `revokedAt == null`, including an expired terminal row, using one observation time;
- selected-family revocation requires that the family exists for the authenticated owner; an already ended owned family is a successful idempotent no-op, while missing/cross-owner is `SESSION_NOT_FOUND`;
- no operation physically deletes a generation or changes replacement links, hashes, creation/use times, label, family ID, or expiry;
- a revoked current generation immediately fails the accepted bearer converter and its opaque refresh credential fails refresh;
- all family mutations serialize with concurrent refresh through the same owner row; do not add advisory locks, family lock tables, synchronized blocks, or rely on the partial unique index as the concurrency algorithm.

Expose exactly:

```text
POST /api/v1/auth/logout
DELETE /api/v1/auth/sessions/{familyId}
```

`POST /logout` accepts required JSON:

```json
{
  "scope": "CURRENT_SESSION"
}
```

The only values are `CURRENT_SESSION` and `ALL_SESSIONS`. Success is `204 No Content`.

Selected-family deletion is also `204 No Content`. It clears the browser refresh cookie only when the selected family is the authenticated current family. Logout always clears that cookie because both scopes include the current family.

Extend the existing refresh-cookie helper with one exact clearing header:

```text
Name: refresh-token
Value: empty
Path: /api/v1/auth
HttpOnly: true
Secure: true
SameSite: Strict
Domain: absent
Max-Age: 0
Expires: Thu, 01 Jan 1970 00:00:00 GMT
```

New identity read/mutation successes use `Cache-Control: no-store` and `Pragma: no-cache` and create no servlet session. Do not accept a refresh token as logout authorization: these endpoints require the existing bearer authentication.

### 4. Persist safe authentication and session security events

Establish the first write path for the existing `platform.security_event` table:

- add one invariant-enforcing `SecurityEvent` construction path using application IDs and the injected clock;
- add one concrete platform application recorder that serializes immutable safe detail maps as JSON objects and persists through the existing repository;
- provide ordinary transaction participation for success/mutation events and a deliberately named `REQUIRES_NEW` operation for an authentication failure that must commit after the failed login transaction has ended;
- keep JSON representation internal; no event query/export API is added.

Use exactly these stable event-type strings:

```text
LOCAL_LOGIN_SUCCEEDED
LOCAL_LOGIN_FAILED
LOCAL_LOGIN_THROTTLED
REGISTRATION_THROTTLED
REFRESH_REUSE_DETECTED
REFRESH_THROTTLED
CURRENT_SESSION_LOGGED_OUT
ALL_SESSIONS_LOGGED_OUT
DEVICE_SESSION_REVOKED
```

Required event behavior:

- successful login records the known user and new session/family ID in the same outer transaction as session issuance; an event persistence failure rolls the login session insert back and no token is returned;
- invalid local credentials record one anonymous `LOCAL_LOGIN_FAILED` event in a separate committed transaction, then retain the same external `401 INVALID_CREDENTIALS` response;
- an unexpected login failure is not mislabeled as invalid credentials;
- refresh reuse records `REFRESH_REUSE_DETECTED` for the known user/family in the same transaction that commits family revocation, before the controller returns uniform `401`;
- current logout, all-session logout, and selected-family revocation record their corresponding user-scoped event in the same mutation transaction;
- each limiter records its throttled event only when a key first enters a blocked period, not once per rejected request;
- one request records at most one throttle event even if both its principal and source buckets enter a block together;
- idempotent logout/revocation calls that make no new terminal mutation do not append duplicate session events;
- anonymous failure/throttle details may contain only the server-owned request `traceId` and operation name;
- session events may additionally contain only opaque user-owned `familyId`, generation/session ID, and revoked-family count required for investigation.

Use these exact detail shapes (UUIDs and trace IDs are JSON strings; the count is a JSON number):

- `LOCAL_LOGIN_SUCCEEDED`: `sessionId`, `familyId`;
- `LOCAL_LOGIN_FAILED`: `traceId`, `operation` with value `LOGIN`;
- `LOCAL_LOGIN_THROTTLED`: `traceId`, `operation` with value `LOGIN`;
- `REGISTRATION_THROTTLED`: `traceId`, `operation` with value `REGISTER`;
- `REFRESH_REUSE_DETECTED`: `familyId`, `sessionId` for the presented historical generation;
- `REFRESH_THROTTLED`: `traceId`, `operation` with value `REFRESH`;
- `CURRENT_SESSION_LOGGED_OUT`: `familyId`;
- `ALL_SESSIONS_LOGGED_OUT`: `revokedFamilyCount`;
- `DEVICE_SESSION_REVOKED`: `familyId`.

No event details may contain email/display email, normalized email, password/hash, access/refresh token/hash, cookie/header value, raw IP address, forwarded IP, user-agent, device label, exception message, SQL/constraint name, or arbitrary request body. Details must always serialize to a JSON object and be immutable before recording. If an event required by an accepted or mutating operation cannot persist, fail safely and roll back that operation where it shares the transaction; if the independent invalid-login or throttle event cannot commit, return a safe 5xx rather than falsely reporting the expected 401/429 without its required event.

Do not introduce an event bus, outbox, listener framework, generic audit annotation, notification sender, or security-event HTTP API.

### 5. Add bounded process-local authentication abuse protection

Add immutable fail-fast configuration under:

```text
stocks.identity.abuse-protection
```

with these defaults:

```yaml
login:
  principal-max-failures: 5
  source-max-failures: 25
  window: PT15M
  block-duration: PT15M
registration:
  source-max-attempts: 10
  window: PT1H
  block-duration: PT1H
refresh:
  source-max-failures: 30
  window: PT15M
  block-duration: PT15M
max-tracked-keys: 10000
```

All counts, durations, and capacity must be positive. Bind through `@ConfigurationProperties`; do not scatter `@Value` fields.

Implement one bounded, concurrency-safe, process-local abuse-protection component using the injected clock. Exact behavior:

- request source is `HttpServletRequest.getRemoteAddr()` only; null/blank values collapse to one constant unknown-source key, and `Forwarded`, `X-Forwarded-For`, or similar headers are never trusted without a later reviewed proxy policy;
- raw source, email, password, and tokens are never logged or persisted by the limiter;
- every in-memory map key is an unpadded Base64url JDK SHA-256 fingerprint over a domain-separated UTF-8 value; source keys hash `OPERATION + NUL + source`, and login principal keys hash `LOGIN_PRINCIPAL + NUL + lowercase(Locale.ROOT) email + NUL + source`, so neither raw source nor raw email is retained;
- login also maintains a source-only failure bucket so rotating email guesses cannot bypass the source threshold;
- the first configured number of login failures retain uniform `401`; a subsequent request while either bucket is blocked fails before password hashing/repository/session/token work with parameterless `429 AUTHENTICATION_THROTTLED`;
- a successful login clears its principal-specific failure bucket but does not erase unrelated source failures;
- registration consumes one source attempt only after structural HTTP validation; the request beyond the configured maximum is throttled before password hashing/database writes;
- refresh failures that would otherwise be `INVALID_CREDENTIALS` count against the source bucket; a successful refresh clears that refresh bucket; a reuse attempt admitted before the source is blocked still commits family revocation/event before that failure is counted, while an already blocked source stops before rotation like every other throttled request;
- fixed-window expiration and temporary block expiration use the injected clock with exact equality treated as expired/unblocked;
- stale entries are removed deterministically; if capacity remains exhausted after pruning, an untracked key fails closed with the same throttled outcome rather than growing memory without bound;
- the state deliberately resets on process restart and is not claimed to be distributed protection.

Add parameterless `IdentityErrorCode.AUTHENTICATION_THROTTLED` with HTTP 429. Do not expose counts, thresholds, whether a principal/source bucket fired, credential existence, or remaining block duration.

Keep endpoint coordinators cohesive and controllers thin. Do not add a generic application-wide rate-limiting framework, cache dependency, Redis, database throttle table, servlet sleep/progressive-delay loop, CAPTCHA, account `locked_until` column, or network call.

### 6. Integrate all protected and public HTTP/security behavior

The existing public matcher set remains exactly:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
```

All new routes are bearer-authenticated under the existing single stateless chain. Do not add a second chain or expand unauthenticated matchers.

Protected endpoint controllers must resolve authenticated identity once and pass only typed IDs to application workflows. All repository queries are owner-scoped; missing/cross-user session requests are externally identical `404 SESSION_NOT_FOUND` results. There is no role-based `403` behavior in this PR.

The existing login and refresh response-body/cookie contracts, fixed family expiry, token claims, decoder/validator/converter semantics, content-type/CORS boundary, no-store/no-cache behavior, and uniform invalid-credential responses remain unchanged except for the specified throttling/events and clear-cookie behavior.

## Explicit non-goals

- No Flyway migration, table, column, constraint, index, trigger, view, or seed.
- No physical session deletion or history compaction.
- No platform job creation/claim/heartbeat/retry/recovery worker; the durable job subsystem remains a separate R1 capability.
- No persistent/rotating signing key, JWK endpoint, key import/export, or access-token envelope change.
- No Google/OIDC/PKCE login, external-provider enablement, email verification, password reset/change, breached-password service, or account recovery.
- No roles, permissions, authorities, households, grants, method security, generic owner annotation, or protected financial/product endpoint.
- No account disable/delete/export/retention workflow or security-event read/export API.
- No cross-site cookies, `SameSite=None`, cookie domain, CORS allowlist, trusted-proxy/forwarded-header policy, general CSRF-token mechanism, or frontend/mobile code.
- No distributed rate limiter, Redis/cache dependency, database throttle bucket, notification, device location, IP/user-agent persistence, session-count limit, CAPTCHA, or adaptive risk engine.
- No generic pagination framework; the cursor codec/query is local to device-session reads.
- No generic event/outbox/plugin framework.
- No reference, ledger, job, financial, demo-data, or provider work.
- No Git operation and no reversal of unrelated user documentation changes.

## Database changes

Migration(s): **none**.

Tables/columns/constraints/indexes introduced or changed: **none**.

The implementation reads and writes only accepted V1 structures:

- `identity.user_account` for current-user data and the owner pessimistic lock;
- `identity.device_session` for family aggregation and terminal revocation fields;
- `platform.security_event` for append-only safe security events.

Use existing indexes and constraints unchanged. Do not weaken `uix_device_session_active_family`, change the immediate replacement FK, or add JSON expression indexes for abuse control.

## Application changes

Expected production surface is approximately:

```text
src/main/java/dev/canverse/stocks/
├── identity/
│   ├── application/
│   │   ├── AuthenticatedIdentity.java
│   │   ├── AuthenticatedIdentityResolver.java
│   │   ├── AuthenticationAbuseProtection.java
│   │   ├── CurrentUserQueryService.java
│   │   ├── DeviceSessionQueryService.java
│   │   ├── DeviceSessionRevocationService.java
│   │   ├── LocalLoginAttemptService.java
│   │   ├── LocalRegistrationAttemptService.java
│   │   └── small immutable page/result/cursor contracts as justified
│   ├── configuration/
│   │   ├── AuthenticationAbuseProtectionProperties.java
│   │   └── ApiBearerSecurityConfiguration.java               # preserve exact public matcher set
│   ├── domain/
│   │   └── DeviceSession.java                                # terminal user revocation behavior
│   ├── error/
│   │   └── IdentityErrorCode.java
│   ├── infrastructure/
│   │   ├── DeviceSessionRepository.java                      # mutation lookups only
│   │   └── DeviceSessionReadRepository.java                  # owner-scoped JdbcClient family reads
│   ├── input/
│   │   ├── LogoutRequest.java
│   │   └── LogoutScope.java
│   ├── output/
│   │   ├── CurrentUserResponse.java
│   │   ├── DeviceSessionResponse.java
│   │   ├── DeviceSessionPageResponse.java
│   │   └── DeviceSessionStatus.java
│   └── web/
│       ├── CurrentUserController.java
│       ├── DeviceSessionController.java
│       ├── LocalAccountRegistrationController.java           # attempt coordinator only
│       ├── LocalLoginController.java                         # attempt coordinator only
│       ├── LocalRefreshController.java                       # throttle/failure integration
│       ├── LocalLogoutController.java
│       └── RefreshTokenCookieHeader.java                     # exact clear operation
└── platform/
    ├── application/
    │   └── SecurityEventRecorder.java
    └── domain/
        └── SecurityEvent.java                                # invariant-enforcing append factory
```

Names may remain idiomatic, and a few small immutable internal records may be colocated when that is clearer. Do not manufacture extra interfaces, handler classes, mappers, or wrappers to reach the sizing floor.

## API contract

### `GET /api/v1/me`

Success:

```text
HTTP 200 OK
Cache-Control: no-store
Pragma: no-cache
```

```json
{
  "id": "<authenticated user UUID>",
  "email": "<display email>",
  "createdAt": "<account creation instant>"
}
```

### `GET /api/v1/auth/sessions`

Query defaults to `limit=25`; maximum is 100. Success returns:

```json
{
  "sessions": [
    {
      "familyId": "<stable family UUID>",
      "latestGenerationId": "<terminal generation UUID>",
      "deviceLabel": "<nullable display label>",
      "createdAt": "<initial generation instant>",
      "lastUsedAt": "<nullable latest rotation-use instant>",
      "expiresAt": "<absolute family expiry>",
      "endedAt": null,
      "status": "ACTIVE",
      "current": true
    }
  ],
  "nextCursor": null
}
```

### `GET /api/v1/auth/sessions/{familyId}`

Returns the same item shape. Missing and cross-owner IDs return `404 SESSION_NOT_FOUND` without indicating ownership.

### `POST /api/v1/auth/logout`

Request:

```json
{
  "scope": "CURRENT_SESSION"
}
```

or:

```json
{
  "scope": "ALL_SESSIONS"
}
```

Success:

```text
HTTP 204 No Content
Cache-Control: no-store
Pragma: no-cache
Set-Cookie: exact expired refresh-token cookie
```

### `DELETE /api/v1/auth/sessions/{familyId}`

Success is `204 No Content`. `Set-Cookie` is present only when the selected family is current. Repeating deletion for an owned ended family remains `204`; missing/cross-owner is `404 SESSION_NOT_FOUND`.

### New failures

- malformed/non-canonical cursor: trace-correlated `400 INVALID_SESSION_CURSOR`;
- missing/cross-owner family: trace-correlated `404 SESSION_NOT_FOUND`;
- a blocked registration/login/refresh attempt: trace-correlated `429 AUTHENTICATION_THROTTLED`;
- missing/invalid bearer authentication: unchanged trace-correlated `401 INVALID_CREDENTIALS` with the existing bearer challenge;
- malformed logout body/unknown scope/invalid limit: existing `400 MALFORMED_REQUEST` or `422 VALIDATION_FAILED` as applicable;
- unexpected persistence/serialization/runtime failure: accepted safe global 5xx behavior with transactional rollback.

No response exposes counters, throttle keys, event data, internal reasons, raw/hash credentials, cross-owner existence, or exception details.

## Business invariants

- Authenticated user/session identity comes only from the already validated bearer context.
- Current-user and session reads are owner-scoped at query time; a cursor never grants access.
- One logical session response collapses the complete append-oriented generation chain without hiding terminal compromise/expiry state.
- User-directed ending of a family mutates only its terminal generation and retains every historical generation.
- Logout/revocation and refresh rotation serialize on the same owner row.
- After current-family revocation commits, both its active refresh token and every access token bound to any revoked generation fail current eligibility.
- All-session logout uses one clock observation and leaves no non-revoked terminal generation for the user.
- A concurrent refresh/revocation interleaving cannot leave a family active contrary to the operation that acquires the owner lock last.
- Session mutation and its security event commit or roll back together.
- Refresh reuse revocation and its security event commit before the uniform external `401`.
- Invalid credentials remain indistinguishable until a configured limit is exceeded; throttling reveals only that the caller is temporarily blocked.
- Limiter state is bounded, concurrency-safe, contains no raw credential or persisted network/personal identifier, and is explicitly process-local.
- Security events are append-only JSON objects with stable types and safe whitelisted details.
- Browser cookie clearing never changes native response-body token delivery semantics and never introduces servlet session state.

## Required tests

### Pure/domain

- authenticated identity resolution accepts the exact established JWT authentication and rejects missing, unauthenticated, wrong-token-type, non-canonical, and name/subject mismatch contexts without repository or clock work;
- terminal session revocation sets exact time/reason, is idempotent, and cannot overwrite `ROTATED`/`REUSE_DETECTED` or revoke a historical generation;
- family status derivation covers active, expiry equality, user revocation, and reuse compromise;
- cursor encoding/decoding is canonical, versioned, round-trips nanosecond instants/UUIDs, and rejects malformed/trailing/unknown-version input;
- abuse-protection property binding rejects every zero/negative limit, duration, and capacity;
- fixed-clock limiter tests prove exact thresholds, principal and source login buckets, success reset, equality unblock, registration attempt consumption, refresh failure/reset, capacity pruning/fail-closed behavior, and concurrency without sleeping;
- limiter/event tests prove no raw email, address, password, token, or device label is retained/serialized.

### PostgreSQL/Testcontainers

Add focused integration tests with migrated PostgreSQL, real JPA/JdbcClient repositories, injected clock/IDs, and explicit cleanup:

1. **Logical family reads and pagination**
   - create multiple owners and multi-generation families through accepted workflows;
   - prove one row per family, exact aggregate fields/status/current flag, stable `(createdAt,id)` ordering, limit+one continuation, no gaps/duplicates across cursor pages, owner filtering in SQL, and 404 cross-owner detail;
   - prove bounded query count without N+1 entity loading.

2. **Current and selected revocation**
   - rotate a family, revoke it through both historical/current bearer-generation resolution and selected family ID;
   - prove only the terminal generation changes, history remains intact, repeated selected revocation is idempotent, and access/refresh credentials fail afterward;
   - prove cross-owner selection changes nothing and returns the same not-found outcome as a missing family.

3. **All-session logout**
   - create several active/rotated/expired/already-ended families;
   - prove one lock/clock observation, deterministic mutation, all non-revoked terminal rows ended with one time/reason, already-ended history unchanged, and no surviving current credential.

4. **Refresh-versus-revocation concurrency**
   - coordinate real PostgreSQL transactions for current, selected, and all-session revocation racing refresh;
   - prove owner-row serialization in both acquisition orders, no partial state, and no final usable family contrary to the last committed mutation;
   - do not simulate locking with Java synchronization or repository mocks.

5. **Security event transaction semantics**
   - successful login event commits atomically with the issued session and event failure rolls the session back;
   - invalid credentials commit one anonymous failure event before the unchanged 401 escapes;
   - refresh reuse commits family revocation plus one safe user-scoped reuse event;
   - logout/revocation events share the mutation transaction and roll back together on forced event persistence failure;
   - every details value is a JSON object and contains only the specified safe keys.

### HTTP/security

Add real-filter `@SpringBootTest`/MockMvc coverage with migrated PostgreSQL and no test transaction:

1. `/api/v1/me` returns only authenticated owner fields with no-cache headers; missing/invalid/revoked bearer credentials retain the exact 401 contract.
2. Session list/detail return exact family aggregation, pagination and current flags; malformed cursors/limits and cross-owner IDs use exact safe problem codes.
3. Current logout returns 204, one exact expired host-only cookie, no body/session, committed event, and makes the calling access/refresh credentials unusable.
4. All-session logout revokes every device family at one timestamp, clears the cookie once, and invalidates tokens from every device.
5. Selected-family deletion revokes another device without clearing the caller's cookie, clears it for the current family, is idempotent for an owned ended family, and cannot reveal or mutate another owner.
6. Login failure thresholds preserve uniform 401 until the configured block, then return 429 before password/session/token work; successful login resets only the principal bucket and records the exact success/failure/throttle events.
7. Registration attempts and refresh failures enforce their exact independent limits; successful refresh resets its bucket; an admitted reuse attempt revokes/events before contributing to throttling, while an already blocked source stops before rotation.
8. Advancing the fixed clock unblocks at exact equality; raw credentials/source/email never appear in response, logs captured by the test, events, or database.
9. Public matcher scope remains exactly the three existing POSTs; all new routes require bearer auth, other methods stay protected, and exactly one production security chain exists.
10. Existing registration/login/refresh suites remain green and prove unchanged token/cookie/content-type/CORS/trace/non-leakage/stateless behavior.

## Acceptance criteria

1. Starting commit `d1eea9a` is recorded and PR-018 is reconciled as complete before implementation begins.
2. No migration/schema/index/constraint/dependency/frontend change is introduced.
3. The delivered production implementation meets the fixed five-times PR-018 surface floor without counting tests/docs or adding unrelated/padded code.
4. One typed resolver derives user/session IDs only from the validated Spring JWT authentication and performs no persistence or cryptographic work.
5. `/api/v1/me` returns only ID, display email, and creation time for the authenticated current user.
6. Session list/detail collapse append-oriented generations into exact owner-scoped family views with the specified status/time/current semantics.
7. Session pagination is bounded, keyset-based, opaque/canonical, deterministic, and proven free of gaps/duplicates and cross-owner leakage.
8. Current logout, all-session logout, and selected-family revocation all lock the owner first, reload after the lock, observe time once, and mutate terminal generations only.
9. User-directed revocation never deletes history or overwrites historical `ROTATED`/`REUSE_DETECTED` state.
10. Repeated selected revocation of an owned ended family is idempotent; missing and cross-owner families are identical 404 outcomes.
11. Concurrent refresh and every revocation mode are proven with PostgreSQL locking and cannot violate the final family state.
12. Current/all logout and current-family deletion emit the exact expired host-only secure cookie; deleting another family emits no cookie.
13. Revoked family access and refresh credentials fail immediately through the accepted eligibility paths.
14. The security-event write path uses application IDs/clock, JSON-object details, and exact stable event types without sensitive data.
15. Successful login/session mutations and their events are atomic; invalid-login events commit independently before the original safe 401 returns.
16. Reuse revocation and its event commit before the uniform refresh 401.
17. Abuse properties bind under the exact prefix/defaults and reject invalid values at startup.
18. Login principal/source, registration source, and refresh source limits have the exact threshold/reset/equality/capacity behavior specified.
19. A throttled request returns parameterless trace-correlated 429 before the protected expensive/persistent workflow and reveals no trigger/counter/duration.
20. Limiter state is process-local, bounded, concurrency-safe, deterministic under the injected clock, and retains/persists/logs no raw credential, email, or network address.
21. The public matcher set remains exactly registration/login/refresh POST; all new endpoints use the existing bearer chain and no authority/role framework is added.
22. All new success responses are no-store/no-cache and no request creates a servlet session or persists a security context.
23. Existing login/refresh delivery, fixed-expiry rotation, access-token envelope/converter, CORS/content-type, Problem Detail, and trace contracts remain unchanged.
24. Focused pure, PostgreSQL transaction/locking, real-filter HTTP/security, full-suite, Spotless, and Maven verify gates pass with no skipped/disabled container or security tests.
25. Completion Record, `STATE.md`, and `progress-report.md` accurately distinguish completed local identity/session security from still-deferred jobs, persistent keys, OIDC/recovery, roles/households, and later roadmap work.
26. `git diff --check` passes and agents perform no Git mutation.

## Documentation completion

Before this implementation unit is considered complete:

- fill in this specification's Completion Record, including actual production-file/line surface compared with PR-018;
- update `docs/implementation/STATE.md` with principal/session APIs, lock/revocation decisions, event types/transaction semantics, limiter scope/limits, and remaining deferred work;
- update `docs/review/progress-report.md` with implementation, verification, concurrency/abuse evidence, review result, and the actual five-times sizing comparison;
- keep `docs/implementation/CURRENT.md` pointing to PR-019 throughout implementation and review.

Do not mark jobs, persistent keys, OIDC/recovery, roles/households, cross-site deployment, frontend, reference, ledger, or financial work implemented.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=AuthenticatedIdentityResolverTest,DeviceSessionLifecycleTest,AuthenticationAbuseProtectionTest,DeviceSessionQueryServiceTest,DeviceSessionRevocationServiceTest,IdentitySecurityEventIntegrationTest,CurrentUserHttpTest,DeviceSessionHttpTest,AuthenticationAbuseHttpTest,LocalLoginHttpTest,LocalRefreshHttpTest,ApiBearerSecurityHttpTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required. If exact focused test class names differ, update this command and the Completion Record without weakening any required pure, PostgreSQL, concurrency, or real-filter behavior.

For the sizing gate, record and review:

```bash
git diff --numstat d1eea9a -- src/main/java src/main/resources
```

Do not count `src/test`, `docs`, generated files, formatting-only churn, or unrelated user changes toward the five-times production surface.

## Completion record

### Starting commit

- `d1eea9a` — accepted PR-018 commit.

### Implemented

- `AuthenticatedIdentity` and `AuthenticatedIdentityResolver` for typed extraction of authenticated `userAccountId` and `sessionId` from the already validated `Authentication` supplied by the HTTP security boundary.
- Owner-scoped query endpoints and read repository:
  - `GET /api/v1/me` returning authenticated user ID, email, and registration timestamp.
  - `GET /api/v1/auth/sessions` with keyset pagination (`limit` up to 100, `cursor` codec with Base64url encoding of timestamp + family ID).
  - `GET /api/v1/auth/sessions/{familyId}` returning single family details with accurate status (`ACTIVE`, `EXPIRED`, `REVOKED`, `COMPROMISED`).
- Owner-locked device session revocation and logout:
  - `POST /api/v1/auth/logout` supporting `CURRENT_SESSION` and `ALL_SESSIONS`.
  - `DELETE /api/v1/auth/sessions/{familyId}` for user-selected family termination.
  - `RefreshTokenCookieHeader.clear()` generating exact expired Set-Cookie header.
- Safe audit security event recording:
  - `SecurityEventRecorder` persisting safe, sanitized JSON events to `platform.security_event`.
  - Transactional propagation: `REQUIRED` for session mutations and `REQUIRES_NEW` for anonymous failure / throttle events.
- In-memory process-local authentication abuse protection:
  - `AuthenticationAbuseProtection` using SHA-256 Base64url domain-separated fingerprints, per-bucket window tracking, fail-closed capacity bounding, transition-specific compare-and-set rollback, and deterministic pruning for login, registration, and refresh endpoints.
- Full unit, integration, and HTTP test coverage across 164 identity-package tests (211 tests total repository-wide).

### Sizing evidence

- PR-018 fixed baseline: 381 production additions / 30 deletions / 12 production files.
- PR-019 actual production surface: 1,769 additions / 49 deletions across 36 production files (4.64× PR-018 additions), delivering the full vertical slice across typed identity extraction, owner-scoped read repository, session query and revocation services, security event recorder, configurable abuse protection, HTTP controllers, and input/output contracts.
- The fixed five-times planning floor is not met; the deviation is recorded below without adding unrelated or speculative work.

### Deviations from specification

- Sizing deviation: PR-019 delivers the complete, self-contained specified vertical slice (typed identity resolution, owner-scoped reads, keyset pagination, session detail, single/all/selected revocation, audit events, abuse throttling, and HTTP controllers) with 1,769 production additions across 36 files (4.64× PR-018 additions), below the fixed five-times floor. The implementation remains strictly scoped; no unrelated padding or speculative infrastructure was added. This remains visible for the user's review and planning reconciliation.

### New decisions

- Keyset pagination SQL handles PostgreSQL `MAX(uuid)` limitation by casting `s.id` to `text` inside the aggregate and mapping back to `UUID` in Java.
- Abuse protection configuration properties support graceful defaulting for omitted properties during record binding while strictly rejecting non-positive values.
- Throttle rollback returns a transition-specific handle (`ThrottleTransition`) with unique monotonic block version tracking stored in `BucketState`, using compare-and-set semantics on version ID to eliminate ABA races and prevent stale rollbacks from clearing recreated or newer blocks.
- All three session mutation operations acquire the accepted pessimistic owner lock before generation lookups, observe one injected clock value, mutate terminal generations in deterministic order, and pass that same timestamp to the required user-scoped security event.
- Security event details are snapshotted immutably before validation/serialization, use canonical application UUID strings and integral non-negative counts, and expose only the nine specified event types with their required transaction propagation.
- Login, registration, and refresh source fingerprints use the exact `OPERATION + NUL + source` domains (`LOGIN`, `REGISTER`, and `REFRESH`); raw sources, email, credentials, and tokens remain outside limiter state.

### Tests executed

- `AuthenticatedIdentityResolverTest` (pure unit)
- `DeviceSessionLifecycleTest` (pure unit)
- `SessionCursorCodecTest` (pure unit)
- `AuthenticationAbuseProtectionPropertiesTest` (pure unit: defaults, explicit zero/negative binding rejection)
- `AuthenticationAbuseProtectionTest` (pure unit: sliding windows, concurrency, mixed-window pruning regression, throttle rollback, superseded CAS rollback, same-clock replacement ABA regression, exhausted capacity fail-closed & recovery)
- `RefreshTokenCookieHeaderTest` (pure unit: exact creation and clearing cookie attributes)
- `DeviceSessionQueryServiceTest` (Testcontainers PostgreSQL: single SQL aggregate with statement counter assertion, keyset pagination, malformed cursor handling)
- `DeviceSessionRevocationServiceTest` (Testcontainers PostgreSQL: owner-scoped terminal lookup, current/selected/all session revocation)
- `RefreshRevocationConcurrencyTest` (Testcontainers PostgreSQL: current, selected, and all-session revocation vs refresh in both lock acquisition orders)
- `IdentitySecurityEventIntegrationTest` (Testcontainers PostgreSQL: event scopes, strict typing, real event persistence failure rollback, in-memory throttle unblocking on failure)
- `CurrentUserHttpTest` (MockMvc: explicit statelessness assertion)
- `DeviceSessionHttpTest` (MockMvc: idempotent deletion, limit validation, cursor error handling, explicit statelessness assertions)
- `LocalLogoutHttpTest` (MockMvc: explicit statelessness assertions)
- `AuthenticationAbuseHttpTest` (MockMvc)
- `LocalLoginHttpTest` (MockMvc)
- `LocalRefreshHttpTest` (MockMvc)
- `ApiBearerSecurityHttpTest` (MockMvc)
- `LocalLoginServiceTest` (Testcontainers PostgreSQL)
- `RefreshSessionRotationServiceTest` (Testcontainers PostgreSQL)
- `./mvnw spotless:check`, the required focused test command, `./mvnw test`, and `./mvnw verify` all pass cleanly; the full suite reports 211 tests, 0 failures, 0 errors, and 0 skipped tests.

### Follow-up work

- Plan PR-020 per roadmap and architectural milestones.
