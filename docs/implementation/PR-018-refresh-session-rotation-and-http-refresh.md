# PR-018 — Refresh-session rotation, reuse response, and HTTP refresh

Status: **COMPLETE IN ACCEPTED COMMIT `d1eea9a`**

## Goal

Complete the first usable refresh lifecycle for local authentication. A client presents exactly one accepted opaque refresh credential, the backend rotates it into a new generation in the same device-session family, issues an access token bound to that new generation, and returns the replacement refresh token through the caller's explicitly selected response-body or hardened same-site cookie channel. Reuse of a previously rotated token revokes the family's remaining active generation before returning the same safe invalid-credential response.

This is one coherent security vertical slice: database locking and session mutation, rotation/reuse transaction semantics, access-token issuance, the public HTTP refresh boundary, explicit web/native delivery, same-site cookie-request protection, and real PostgreSQL/concurrency/security coverage belong together.

## Source documents

- `docs/review/backend-master-plan.md` — R1 items 5 and 9, R1 refresh endpoint, API invariants, and the R1 exit gate
- `docs/review/backend-audit.md` — SEC-003 and SEC-006
- `docs/review/mobile-api-readiness.md` — Authentication for web and mobile
- `docs/engineering/coding-standards.md`
- `docs/implementation/PR-002-v1-foundation-database.md` — accepted append-oriented device-session family schema
- `docs/implementation/PR-010-initial-refresh-session-issuance.md`
- `docs/implementation/PR-011-local-access-token-issuance.md`
- `docs/implementation/PR-013-opaque-refresh-session-authentication.md`
- `docs/implementation/PR-016-http-bearer-authentication-boundary.md`
- `docs/implementation/PR-017-http-local-login-and-token-delivery.md`

`docs/review/accounting-contract.md` is not required because this PR contains no financial behavior.

## Starting state

Starting commit: **`7f55288`**, the user-owned commit accepting PR-017. This is the actual inspected repository commit; it is recorded here before PR-018 production implementation begins.

PR-017 is independently reviewed and its required focused PostgreSQL suite, full 105-test suite, Spotless check, and Maven `verify` gate pass. The accepted starting behavior includes:

- public local registration and login POSTs under one stateless `/api/v1/**` bearer chain;
- explicit `RESPONSE_BODY` and `HTTP_ONLY_COOKIE` refresh-token delivery at login;
- one initial `identity.device_session` row whose ID is also its family ID, with only the refresh-token hash persisted;
- strict access-token issuance/decoding and current database-backed session/user eligibility checks;
- read-only opaque refresh-token authentication for an active generation;
- a fixed host-only `refresh-token` cookie with `Secure`, `HttpOnly`, `SameSite=Strict`, `/api/v1/auth`, exact whole-second `Expires`, and positive whole-second `Max-Age`.

The existing V1 schema was deliberately designed for this work:

- one row represents one refresh-token generation;
- `family_id` is stable across generations;
- `refresh_token_hash` is unique;
- `replaced_by_session_id` links a consumed generation to its successor;
- `ix_device_session_family_id` supports family lookup;
- `uix_device_session_active_family` permits at most one row with `revoked_at IS NULL` per family.

The worktree also contains a user-owned `AGENTS.md` sizing/workflow change. It remains outside production scope and must not be reverted or rewritten by the implementation agent.

## Scope

### 1. Add explicit refresh request and response contracts

Add `LocalRefreshRequest` in `dev.canverse.stocks.identity.input` with exactly:

```java
String refreshToken
RefreshTokenDelivery refreshTokenDelivery
```

`refreshTokenDelivery` is required with `@NotNull` and reuses the accepted two-value enum. `refreshToken` is conditionally present and therefore has no unconditional `@NotBlank` annotation:

- `RESPONSE_BODY` requires one nonblank body `refreshToken` and requires the `refresh-token` cookie to be absent;
- `HTTP_ONLY_COOKIE` requires the body `refreshToken` to be absent and requires exactly one nonblank `refresh-token` cookie.

Missing, blank, duplicated, or cross-channel refresh credentials and a credential supplied through both channels are indistinguishable `401 INVALID_CREDENTIALS` outcomes. Do not expose whether a token was absent, unknown, expired, revoked, reused, or associated with a disabled user.

Add `LocalRefreshResponse` in `dev.canverse.stocks.identity.output` with exactly these components, in this order:

```java
UUID sessionId
String accessToken
Instant accessTokenExpiresAt
Instant refreshTokenExpiresAt
Instant serverTime
String refreshToken
```

Apply the same response-record rules accepted for `LocalLoginResponse`: all common components are `@NotNull` and constructor-enforced, `refreshToken` is nullable only for cookie delivery, and null JSON properties are omitted. Do not replace the login and refresh records with a generic token envelope.

### 2. Add the minimal device-session rotation lifecycle

Extend `DeviceSession` with intention-revealing behavior for exactly this lifecycle. Names may remain idiomatic, but the entity behavior must enforce:

- a replacement generation has a fresh application UUID and refresh-token hash;
- it retains the exact predecessor user, `familyId`, `deviceLabel`, and absolute `expiresAt`;
- its `createdAt` is the accepted rotation observation time;
- its usage, revocation, reason, and replacement fields begin null;
- consuming an active generation sets `lastUsedAt` and `revokedAt` to the same observation time, sets `revokeReason` to the stable application value `ROTATED`, and links `replacedBySessionId` to the successor;
- reuse response may revoke the currently active family generation with reason `REUSE_DETECTED`, without overwriting the historical `ROTATED` reason on already consumed generations;
- an already revoked/replaced generation cannot be rotated again through an entity method.

Do not add public setters, a generic state-machine framework, a bidirectional replacement association, or database DDL annotations.

### 3. Serialize refresh mutations and rotate atomically

Add one cohesive transactional application service, `RefreshSessionRotationService`, returning an empty/success outcome without exposing a rejection reason. A successful result may be one immutable `LocalRefreshResult` application record containing the replacement session ID, raw replacement refresh token, access token, and both exact expiries.

Use this lock and query order so a relatively weak implementation cannot accidentally create a reuse race:

1. reject a null internal argument before collaborator work;
2. hash the exact presented token once through `SecureRefreshTokenGenerator`;
3. perform one read-only projection lookup by unique hash to obtain only the generation ID and owning user ID; do not load a mutable `DeviceSession` entity into the persistence context during this preliminary lookup;
4. if no projection exists, return the uniform rejected outcome without token/ID generation;
5. acquire a PostgreSQL pessimistic write lock on the owning `identity.user_account` row;
6. reload the exact device-session generation after the owner lock and observe the injected `Clock` once;
7. evaluate reuse, revocation, expiry, and disabled-user state from that post-lock state;
8. on normal rotation, generate one replacement token and one replacement session ID, mutate and flush the predecessor so the partial active-family uniqueness slot is released, insert/flush the successor, issue one access token for the successor, and return the exact result;
9. if access-token issuance or any later unchecked operation fails, roll back both the predecessor mutation and successor insert.

The user-row lock is the accepted family-mutation lock for this PR. It deliberately serializes refresh mutations for one user and avoids a speculative advisory-lock or session-family lock framework. Any later logout/session-revocation workflow that mutates device-session families must follow the same owner-lock-first discipline or deliberately replace it in a reviewed specification.

Do not use a pre-lock entity instance after waiting for the owner lock. Do not rely on the partial unique index as the normal concurrency algorithm, and do not weaken or remove that index.

### 4. Preserve a fixed absolute family expiry

Rotation does not extend login lifetime. Every generation in one family inherits the initial generation's exact full-precision `expiresAt`.

Consequences that must remain explicit:

- `refreshTokenExpiresAt` is unchanged by rotation;
- cookie `Max-Age` decreases as the family approaches expiry;
- a token expiring at or before the post-lock observation time is rejected;
- if the remaining interval cannot produce an eligible access token, the accepted access-token service rejects and the entire attempted rotation rolls back;
- no idle-timeout, sliding expiry, absolute-plus-idle dual lifetime, or runtime-property change is introduced.

### 5. Detect reuse and commit family revocation before returning 401

A stored generation with non-null `replacedBySessionId` proves that its opaque token was already consumed. Presenting it again must:

1. acquire the same owner lock;
2. find the family's currently non-revoked generation, if any;
3. revoke that generation at the accepted observation time with `REUSE_DETECTED`;
4. flush and commit the revocation;
5. return the service's uniform rejected outcome;
6. only after the rotation transaction has completed, throw the existing parameterless `AppException(IdentityErrorCode.INVALID_CREDENTIALS)` at the application/HTTP boundary.

Do not throw the client-facing exception from inside the transaction after applying reuse revocation, because normal runtime-exception rollback would silently undo the security response. A minimal empty/result outcome is justified by this transaction requirement; do not introduce a generic result framework or public reason enum.

Presenting a row revoked without a replacement, an expired active token, an unknown token, or a token whose user is disabled returns the same rejected outcome without creating a successor. Reuse detection does not reveal which branch occurred.

After reuse response, every access token bound to a generation in that family must fail the accepted current-session eligibility check, and no active refresh generation may remain.

### 6. Add one public JSON-only refresh endpoint

Add `LocalRefreshController` in `dev.canverse.stocks.identity.web` and expose exactly:

```text
POST /api/v1/auth/refresh
Content-Type: application/json
```

This mapping may declare JSON `consumes` explicitly because rejecting browser form-compatible content types is part of the cookie request's CSRF boundary, not ordinary content-negotiation decoration.

The controller must:

1. validate `LocalRefreshRequest`;
2. select exactly one credential from the body or cookies according to the explicit delivery enum;
3. invoke `RefreshSessionRotationService` exactly once for a valid single-channel credential;
4. convert an empty service outcome to the existing parameterless `INVALID_CREDENTIALS` exception only after the service transaction returns;
5. observe the injected application `Clock` exactly once after successful service return for `serverTime`;
6. return the replacement access token and session metadata with `Cache-Control: no-store` and `Pragma: no-cache`;
7. emit the replacement raw refresh token through exactly the selected channel;
8. create no servlet session and persist no Spring Security context.

Do not authenticate this route with an access token, catch/map `AppException`, call repositories, perform locking, decode JWTs, or log request/cookie/token/result data in the controller.

### 7. Share the exact refresh-cookie construction

Extract PR-017's exact cookie-header construction into one small identity web helper used by both login and refresh. This extraction is now justified by two production consumers.

The helper must retain the accepted contract exactly:

```text
Name: refresh-token
Path: /api/v1/auth
HttpOnly: true
Secure: true
SameSite: Strict
Domain: absent
Expires: backing refresh-session expiry rounded down to whole seconds
Max-Age: positive whole seconds from response serverTime to expiry, rounded down
```

Spring Framework 7.0.8's `ResponseCookie` builder has no explicit `expires(...)` method and derives `Expires` from the process wall clock when `Max-Age` is serialized. Preserve Spring's cookie construction and replace only that generated `Expires` attribute with the injected-clock/session-expiry value; do not hand-build the entire cookie header. Login's previously reviewed serialized cookie must remain byte-for-byte equivalent for the fixed test inputs.

For refresh cookie delivery, the replacement cookie uses the same name/path and therefore overwrites the consumed browser cookie. Response-body delivery writes no `Set-Cookie` header.

### 8. Define the same-site cookie-request/CSRF boundary

The accepted browser mode remains same-origin deployment only for this increment:

- the credential cookie remains host-only, `Secure`, and `SameSite=Strict`;
- the refresh endpoint consumes JSON only;
- no CORS policy is enabled, so a cross-origin JSON request cannot pass a browser preflight;
- browser form-compatible `text/plain`, form-urlencoded, and multipart refresh requests are rejected before rotation;
- the existing stateless bearer chain remains globally CSRF-disabled; do not add a second chain or a partial session-based CSRF subsystem.

This is the complete cookie-request policy for this PR. Do not add `SameSite=None`, a cookie domain, an origin allowlist, forwarded-header/proxy policy, double-submit token, synchronizer-token storage, or frontend code. A later deployment/CORS change must reassess this boundary explicitly.

### 9. Permit only the exact refresh POST

Add only this request to the existing unauthenticated matchers:

```text
POST /api/v1/auth/refresh
```

Registration and login remain public. Every other request matched by `/api/v1/**`, including other methods at the refresh path, remains protected by the accepted bearer chain. Keep exactly one production `SecurityFilterChain`.

Refresh-credential failures originate after the public route reaches the controller and therefore use the existing `401 INVALID_CREDENTIALS` Problem Detail without a bearer `WWW-Authenticate` challenge. Do not add form login, Basic auth, another authentication provider, a custom refresh filter, or a refresh token bearer resolver.

## Explicit non-goals

- No logout endpoint, clear-cookie response, current/all-device revocation command, session listing, session detail, user-selected session revocation, or session deletion.
- No rolling/sliding refresh lifetime, idle timeout, session-lifetime property change, refresh grace window, retry tolerance, or idempotent refresh. A second use of a consumed token is reuse, including an accidental concurrent duplicate.
- No security-event insert, notification, IP/location/user-agent metadata, throttling, progressive delay, lockout, breached-password check, or session-count limit.
- No cross-site cookie support, CORS allowlist, `SameSite=None`, cookie domain, proxy/TLS deployment configuration, general CSRF-token mechanism, or frontend integration.
- No access-token claim/decoder/validator/converter change except exercising issuance and current-session rejection with the replacement session.
- No persistent signing key, JWK endpoint, OIDC/Google flow, role, permission, authority, method security, owner helper, `/api/v1/me`, or other protected product endpoint.
- No registration/password-policy/email-normalization/login behavior change beyond reusing the extracted exact cookie helper.
- No migration, table, column, constraint, index, dependency, runtime-property, financial, reference, job-worker, demo-data, or legacy-route change.
- No generic credential framework, session-state framework, result hierarchy, service interface/implementation pair, repository wrapper, mapper, event bus, or future logout abstraction.
- No Git operation and no reversal of the user-owned `AGENTS.md` change.

## Database changes

Migration(s): none.

Tables/columns/constraints/indexes introduced or changed: none.

The PR writes the existing `identity.device_session` lifecycle fields only:

- predecessor `last_used_at`, `revoked_at`, `revoke_reason`, and `replaced_by_session_id`;
- one successor row with the same owner/family/label/expiry and a new ID/hash;
- on reuse, the remaining active family's `revoked_at` and `revoke_reason`.

Use the existing unique hash constraint, family index, replacement FK, expiry check, and partial active-family unique index unchanged. Add only the repository projection/locking/family queries required by the specified transaction algorithm.

## Application changes

Expected production surface is approximately:

```text
src/main/java/dev/canverse/stocks/identity/
├── application/
│   ├── LocalRefreshResult.java
│   └── RefreshSessionRotationService.java
├── configuration/
│   └── ApiBearerSecurityConfiguration.java          # exact refresh POST permit
├── domain/
│   └── DeviceSession.java                           # replacement/rotation/reuse intent
├── infrastructure/
│   ├── DeviceSessionRepository.java                 # projection/family queries
│   └── UserAccountRepository.java                   # owner pessimistic lock
├── input/
│   └── LocalRefreshRequest.java
├── output/
│   └── LocalRefreshResponse.java
└── web/
    ├── LocalLoginController.java                    # shared cookie-helper use only
    ├── LocalRefreshController.java
    └── RefreshTokenCookieHeader.java                # exact shared construction
```

One small immutable projection for preliminary token owner/session lookup may live with the repository/application query contract. Do not add layers beyond those justified above.

## API contract

### `POST /api/v1/auth/refresh` — response-body delivery

Request:

```json
{
  "refreshToken": "<current opaque refresh token>",
  "refreshTokenDelivery": "RESPONSE_BODY"
}
```

The `refresh-token` request cookie must be absent.

Success:

```text
HTTP 200 OK
Content-Type: application/json
Cache-Control: no-store
Pragma: no-cache
Set-Cookie: absent
```

```json
{
  "sessionId": "<replacement generation UUID>",
  "accessToken": "<new compact RS256 access token>",
  "accessTokenExpiresAt": "<whole-second instant>",
  "refreshTokenExpiresAt": "<unchanged family expiry>",
  "serverTime": "<controller clock instant>",
  "refreshToken": "<replacement opaque refresh token>"
}
```

### `POST /api/v1/auth/refresh` — cookie delivery

Request:

```text
Cookie: refresh-token=<current opaque refresh token>
Content-Type: application/json
```

```json
{
  "refreshToken": null,
  "refreshTokenDelivery": "HTTP_ONLY_COOKIE"
}
```

The `refreshToken` property may be omitted instead of explicitly null.

Success has the same common JSON fields, omits the null `refreshToken` property, and writes exactly one replacement cookie with the shared hardened contract and decreasing `Max-Age`.

### Failures

- missing `refreshTokenDelivery`: existing trace-correlated `422 VALIDATION_FAILED`;
- unknown enum or malformed JSON: existing trace-correlated `400 MALFORMED_REQUEST`;
- unsupported/form-compatible content type: existing trace-correlated `415 UNSUPPORTED_MEDIA_TYPE`;
- missing, blank, duplicate, ambiguous, unknown, expired, revoked, reused, or disabled-user refresh credential: existing parameterless trace-correlated `401 INVALID_CREDENTIALS`;
- unexpected persistence/JWT/runtime failure: accepted safe global 5xx handling, with successful rotation state rolled back.

No failure response contains a raw token, hash, submitted credential, user/session/family ID, reason, SQL/constraint name, stack trace, or internal message. Refresh-credential `401` responses have no bearer challenge.

## Business invariants

- One successful refresh consumes exactly one active generation and creates exactly one active successor in the same family.
- The successor retains owner, family, device label, and absolute expiry; only generation ID, token/hash, creation state, and access-token instance are new.
- Raw refresh and access tokens are returned once and never persisted; PostgreSQL stores only the replacement refresh hash.
- The predecessor is retained as append-oriented rotation history and linked to its successor.
- At most one non-revoked row exists per family after every committed normal rotation.
- Reuse of any replaced generation leaves no active generation in that family and invalidates access tokens backed by that family.
- Duplicate concurrent consumption is not treated as a harmless retry: one use may produce a response, but reuse response must leave the family revoked and no returned refresh/access token usable against current database state.
- Family revocation caused by reuse commits even though the HTTP result is `401`; unrelated runtime/JWT failure rolls the attempted normal rotation back.
- Credential channel choice is explicit and exactly one-channel; neither mode exposes the raw replacement through both JSON and cookies.
- Cookie refresh is same-site/JSON-only and cannot be triggered through a browser simple cross-origin form request under the accepted no-CORS policy.
- Request tracing, Problem Details, bearer authentication, and servlet statelessness remain owned by their accepted shared boundaries.

## Required tests

### Pure/domain

Add focused entity/control-flow tests where they prove behavior more clearly than PostgreSQL:

- replacement construction retains exact owner/family/label/expiry and starts with clean lifecycle fields;
- normal rotation sets exact predecessor usage/revocation/replacement state and rejects a second entity-level rotation;
- reuse revocation does not overwrite already historical rotation state;
- null internal token fails before hash/query/clock/ID/token work;
- an unknown hash returns the uniform rejected outcome before owner lock, clock, token generation, or ID/JWT issuance.

Do not mock repositories to claim transaction, lock, rollback, or concurrency behavior.

### PostgreSQL/Testcontainers

Add `RefreshSessionRotationServiceTest` against migrated PostgreSQL with real repositories, refresh token generator, access-token encoder/decoder, clock, IDs, and Spring transactions. Replace only an actual side-effect seam when a deterministic JWT failure is required.

Cover at least:

1. **Normal rotation writes the exact two-generation history**
   - create an initial session through the accepted issuance/login workflow;
   - rotate its raw token;
   - assert old row timestamps/reason/replacement link and new row owner/family/label/absolute expiry/clean state;
   - assert exactly one active family row, old-token authentication failure, new-token authentication success, hash-only persistence, and a new access token whose `sid` is the successor;
   - assert the predecessor access token is rejected by the current database-backed converter.

2. **Replaced-token reuse revokes the family and still commits the defense**
   - rotate once successfully, then present the consumed predecessor again;
   - assert the service returns the same rejected outcome while the successor commits as revoked with `REUSE_DETECTED`;
   - assert no active family generation, no additional successor, both refresh tokens rejected, and successor-bound access rejected.

3. **Other invalid states are uniform and non-mutating**
   - cover unknown, expired-at-equality, explicitly revoked-without-replacement, and disabled-user generations;
   - assert no new token/session/access-token ID generation and no persisted changes.

4. **JWT failure rolls normal rotation back**
   - fail access-token encoding after predecessor flush and successor flush;
   - assert the exception escapes through safe existing behavior and the database retains the original generation as active with all lifecycle fields unchanged and no successor.

5. **Concurrent duplicate refresh cannot leave a usable family**
   - run two committed transactions presenting the same active token with deterministic coordination;
   - assert one transaction may return a result and the other is uniformly rejected as reuse;
   - assert final PostgreSQL state has no active family generation and every returned refresh/access credential fails current eligibility;
   - prove the test exercises actual PostgreSQL locking rather than synchronized test code or mocked repositories.

### HTTP/security

Add `LocalRefreshHttpTest` using `@SpringBootTest`, default-filter `MockMvc`, migrated PostgreSQL, explicit cleanup, and no test-level transaction. Use the real registration/login/rotation/access/decoder/converter/security/error/trace stack.

Cover at least:

1. response-body refresh rotates once, returns exact common/new-token fields, writes no cookie/session, and persists the exact family history;
2. cookie refresh consumes the login cookie, omits raw JSON refresh token, writes one exact replacement cookie with unchanged absolute expiry and decreased positive max age, and creates no servlet session;
3. missing/blank/wrong-channel/both-channel/duplicate cookie credentials are uniform `401` failures with no mutation or token leakage;
4. reused predecessor refresh returns the safe trace-correlated `401`, commits family revocation, emits no token/cookie, and makes the first refresh result unusable;
5. missing/unknown delivery, malformed JSON, and unsupported form-compatible content types retain exact `422`/`400`/`415` contracts and stop before rotation;
6. cross-origin preflight receives no CORS authorization and cannot mutate a session;
7. only the exact refresh POST is public; other methods at the path and other `/api/v1/**` routes retain bearer protection; registration/login remain public and exactly one chain exists;
8. all success/failure responses retain trace, no-cache/non-leakage, and no-`JSESSIONID` assertions as applicable.

Retain and rerun `LocalLoginHttpTest` to prove the shared cookie extraction did not change login behavior.

## Acceptance criteria

1. The user-owned PR-017 commit hash is recorded before PR-018 production implementation begins.
2. No Flyway migration or schema/index/constraint change is introduced; existing V1 session-family constraints remain unchanged and green.
3. One transactional rotation service uses preliminary projection lookup, owner-row pessimistic locking, post-lock entity reload, and one post-lock clock observation in the specified order.
4. Successful rotation flushes the consumed generation before inserting its successor, preserves the absolute family expiry, and leaves exactly one active family generation.
5. The consumed row records exact last-use/revocation time, `ROTATED`, and successor linkage; the successor retains exact owner/family/label/expiry and stores only the new hash.
6. The new access token is bound to the successor generation and is issued before the rotation transaction commits.
7. Access-token issuance/runtime failure rolls back both old-row mutation and successor insertion.
8. Reuse of a replaced token commits revocation of the remaining active generation with `REUSE_DETECTED` before the uniform `401` is raised.
9. Concurrent duplicate refresh is proven with PostgreSQL locks and cannot leave any returned family credential currently usable.
10. Unknown, expired, manually revoked, disabled-user, missing, blank, duplicate, and ambiguous credentials are externally indistinguishable and create no successor.
11. Exactly one new public `POST /api/v1/auth/refresh` endpoint accepts JSON and invokes rotation once for a valid single-channel credential.
12. Response-body delivery returns the raw replacement refresh token only in JSON and writes no cookie.
13. Cookie delivery accepts the raw credential only from exactly one hardened request cookie, omits it from JSON, and writes one exact replacement cookie using the shared helper.
14. Both success modes return exact session/access/expiry metadata, controller `serverTime`, no-store/no-cache headers, and no servlet session.
15. Login's accepted cookie serialization and delivery behavior remain unchanged after helper extraction.
16. Browser form-compatible refresh requests and cross-origin preflight cannot reach rotation; no CORS or cross-site cookie support is added.
17. Registration, login, and refresh POSTs are the only public requests inside the existing `/api/v1/**` chain; every other matched request remains bearer-authenticated and exactly one production chain exists.
18. No response/log/database field leaks a raw credential, token hash, credential-state reason, or internal detail.
19. No logout/session-management, security-event/abuse-control, authorization/owner-helper, persistent-key, frontend, job, or financial work is quietly included.
20. Focused pure, PostgreSQL transaction/concurrency, real-filter HTTP/security, full-suite, Spotless, and Maven verify gates pass with no skipped/disabled security or container tests.
21. Completion Record, `STATE.md`, and `progress-report.md` accurately distinguish implemented refresh rotation from still-deferred logout/session management and abuse/security-event work.
22. `git diff --check` passes and agents perform no Git mutation.

## Documentation completion

Before this implementation unit is considered complete:

- fill in this specification's Completion Record, including the actual PR-017 starting commit;
- update `docs/implementation/STATE.md` with the rotation/reuse transaction and lock decisions, exact refresh API, absolute family-expiry policy, and remaining deferred work;
- update `docs/review/progress-report.md` with implementation, verification, concurrency evidence, and review result;
- keep `docs/implementation/CURRENT.md` pointing to PR-018 throughout implementation and review.

Do not mark logout/session listing/revocation, cross-site deployment, security events, abuse controls, authorization helpers, persistent keys, jobs, or financial roadmap work implemented.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=DeviceSessionRotationTest,RefreshSessionRotationControlFlowTest,RefreshSessionRotationServiceTest,LocalRefreshHttpTest,LocalLoginHttpTest,ApiBearerSecurityHttpTest,AccessTokenIssuanceServiceTest,RefreshSessionAuthenticationServiceTest,GlobalExceptionHandlerIntegrationTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required. If the implementation uses different focused pure-test class names, update this command and the Completion Record without weakening the required behaviors.

## Completion record

Fill this before marking PR-018 complete.

### Starting commit

- `7f55288` — user-owned PR-017 acceptance commit.

### Accepted commit

- `d1eea9a` — accepted PR-018 implementation and completion documentation.

### Implemented

- Added explicit refresh request/response contracts, append-oriented entity rotation/reuse behavior, preliminary hash projection, owner pessimistic locking, fixed-expiry transactional rotation, committed reuse revocation, successor-bound access-token issuance, JSON-only refresh HTTP delivery, shared hardened cookie construction, and exact public matcher scope.
- Added pure domain/control-flow tests, PostgreSQL tests for normal rotation, invalid-state no-op behavior, rollback, reuse, and two-transaction duplicate consumption, and real-filter HTTP/security tests covering both delivery channels, credential-channel validation, exact cookie properties, non-leakage, content-type/CORS boundaries, reuse response, statelessness, and route scope.

### Deviations from specification

- None. The existing immediate `replaced_by_session_id` foreign key is satisfied with the required JPA sequence: consume and flush the predecessor, persist and flush the active successor, link and flush the predecessor, then issue the access token. No schema or migration change was introduced.

### New decisions

- Keep the V1 immediate FK unchanged and use the existing JPA repositories for all rotation writes; no public state or generic persistence abstraction was added.

### Tests executed

- `./mvnw spotless:check` — passed after formatting.
- `./mvnw -Dtest=DeviceSessionRotationTest,RefreshSessionRotationControlFlowTest,RefreshSessionRotationServiceTest,LocalRefreshHttpTest,LocalLoginHttpTest,ApiBearerSecurityHttpTest,AccessTokenIssuanceServiceTest,RefreshSessionAuthenticationServiceTest,GlobalExceptionHandlerIntegrationTest test` — 45 tests passed, 0 failures/errors/skips.
- `./mvnw test` — 124 tests passed, 0 failures/errors/skips.
- `./mvnw verify` — passed, including the full 124-test suite and Spotless.
- `git status --short` and `git diff --check` — passed; no Git mutation performed.

### Follow-up work

- Logout/session listing or user-selected revocation, security events/abuse controls, authorization/owner helpers, cross-site deployment/CORS/general CSRF, persistent keys, jobs, frontend, schema, and financial work remain deferred.
