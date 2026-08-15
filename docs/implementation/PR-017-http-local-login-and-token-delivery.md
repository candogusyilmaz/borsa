# PR-017 — HTTP local login and explicit token delivery

Status: **COMPLETE — AWAITING USER COMMIT**

## Goal

Expose the accepted atomic local-login workflow through one public `POST /api/v1/auth/login` endpoint. A successful request returns the short-lived access token and exact session metadata, while the caller must explicitly choose whether the raw refresh token is delivered once in the JSON response for a native client or once in a hardened HTTP-only cookie for a same-site web client.

This PR is the HTTP boundary for initial local login only. It does not consume, rotate, revoke, or otherwise mutate an existing refresh session.

## Source documents

- `docs/review/backend-master-plan.md` — API invariants and R1 local login/session scope
- `docs/review/backend-audit.md` — SEC-003, SEC-004, and SEC-006
- `docs/review/mobile-api-readiness.md` — Authentication for web and mobile
- `docs/engineering/coding-standards.md`
- `docs/implementation/PR-006-stable-error-handling.md`
- `docs/implementation/PR-007-http-local-account-registration.md`
- `docs/implementation/PR-012-atomic-local-login-orchestration.md`
- `docs/implementation/PR-016-http-bearer-authentication-boundary.md`
- Spring Security 7 servlet authorization documentation for exact public request matchers within a scoped stateless filter chain
- Spring Framework 7 MVC/HTTP cookie documentation for `ResponseEntity`, `Set-Cookie`, and `ResponseCookie`

`docs/review/accounting-contract.md` is not required because this PR introduces no financial behavior.

## Starting state

Starting commit: `9fcbb69` (`install stateless bearer authentication`).

Already implemented and not to be redesigned here:

- `LocalLoginService.login(email, rawPassword, deviceLabel)` atomically verifies the accepted local credentials, creates and flushes one initial opaque refresh session, issues its session-bound access token, and returns `LocalLoginResult` containing the exact session ID, raw tokens, and expiries;
- only the refresh-token hash is persisted, and a downstream unchecked access-token failure rolls the session insert back;
- the accepted access token is a strict RS256 bearer token and the accepted refresh token is a 256-bit opaque value;
- `GlobalExceptionHandler` and `RequestTraceFilter` own the stable trace-correlated RFC 9457 error contract;
- one stateless servlet `SecurityFilterChain` matches `/api/v1/**`, permits only the exact registration POST, authenticates every other matched request through the accepted decoder/converter, and creates no servlet session;
- `POST /api/v1/auth/register` is the only current production auth endpoint.

No migration, repository query, token-service change, or additional authentication mechanism is a prerequisite for exposing initial login.

The worktree has a pre-existing user modification to `AGENTS.md`. It is outside this PR and must remain untouched.

## Scope

### 1. Add one explicit refresh-token delivery choice

Add an HTTP-boundary enum in `dev.canverse.stocks.identity.input` with exactly these JSON values:

```text
RESPONSE_BODY
HTTP_ONLY_COOKIE
```

The client must send one value on every login request. Do not infer delivery from `User-Agent`, `Origin`, cookies, CORS state, an absent header, or any server-side client registry. Do not add a default value.

`RESPONSE_BODY` is the native/installed-client contract: the raw refresh token appears once in the success JSON and no refresh cookie is written.

`HTTP_ONLY_COOKIE` is the same-site browser contract: the raw refresh token appears once in `Set-Cookie` and is omitted from the success JSON.

### 2. Add the local-login request record

Add `LocalLoginRequest` in `dev.canverse.stocks.identity.input` with exactly:

```java
String email
String password
String deviceLabel
RefreshTokenDelivery refreshTokenDelivery
```

Apply HTTP-boundary validation as follows:

```java
@NotBlank @Email @Size(max = 320) @Pattern(regexp = "\\S(?:.*\\S)?") String email
@NotBlank @Size(min = 12, max = 128) String password
@Size(max = 128) @Pattern(regexp = "\\S(?:.*\\S)?") String deviceLabel
@NotNull RefreshTokenDelivery refreshTokenDelivery
```

`deviceLabel` remains optional; when present it must be nonblank, have no leading or trailing whitespace, and be at most 128 characters. The controller must pass all three service inputs unchanged. It must not trim or normalize credentials or the device label.

An absent delivery value is a normal `422 VALIDATION_FAILED` outcome. An unknown enum string or malformed JSON is the existing safe `400 MALFORMED_REQUEST` outcome.

### 3. Add the conditional login response record

Add `LocalLoginResponse` in `dev.canverse.stocks.identity.output` with exactly these components, in this order:

```java
UUID sessionId
String accessToken
Instant accessTokenExpiresAt
Instant refreshTokenExpiresAt
Instant serverTime
String refreshToken
```

Annotate every always-present response component with `@NotNull`. `refreshToken` is conditionally absent and must not have `@NotNull`.

The record must omit null JSON properties and enforce non-null common components in its compact constructor. `refreshToken` is nullable only because cookie delivery omits it from JSON.

Do not add a generic token envelope, user profile, email, role, authority, password/hash, stored refresh-token hash, signing-key information, or decoded JWT claims.

### 4. Add one thin local-login controller

Add `LocalLoginController` in `dev.canverse.stocks.identity.web` and expose exactly:

```text
POST /api/v1/auth/login
```

The controller must:

1. accept and validate one JSON `LocalLoginRequest`;
2. invoke `LocalLoginService.login(request.email(), request.password(), request.deviceLabel())` exactly once;
3. observe the injected application `Clock` exactly once after successful service return and use that instant as `serverTime`;
4. map the exact application result into `LocalLoginResponse` without decoding, transforming, recomputing, or persisting a token;
5. return HTTP `200 OK` with JSON;
6. return `Cache-Control: no-store` and `Pragma: no-cache` on every successful login response;
7. return `ResponseEntity` because cookie delivery requires response-specific headers;
8. let all validation, application, persistence, and unexpected exceptions reach the accepted global error boundary unchanged.

Do not catch `AppException`, build `ProblemDetail`, call a repository, duplicate credential/session/token logic, or log the request/result.

### 5. Define the browser refresh cookie exactly

For `HTTP_ONLY_COOKIE`, add exactly one `Set-Cookie` header containing the raw refresh token with:

```text
Name: refresh-token
Path: /api/v1/auth
HttpOnly: true
Secure: true
SameSite: Strict
Domain: absent (host-only)
Expires: the refresh-session expiry rounded down to whole seconds
Max-Age: the positive whole seconds from serverTime to refresh-session expiry, rounded down
```

The cookie expiry must never outlive the full-precision persisted refresh-session expiry. Use Spring's HTTP cookie support; do not hand-concatenate a cookie string.

For `HTTP_ONLY_COOKIE`, omit the `refreshToken` JSON property entirely. For `RESPONSE_BODY`, include the raw refresh token in JSON and write no `Set-Cookie` header. Neither mode may emit `JSESSIONID` or persist a Spring Security context.

This fixed cookie is deliberately host-only, secure, and `SameSite=Strict`. Do not make it cross-site, relax `SameSite`, add a domain, or add environment/CORS/CSRF configuration in this PR. A later cookie-consuming refresh/logout unit must define its own CSRF and deployment-origin behavior before changing these attributes.

### 6. Permit only the exact login POST in the existing API chain

Modify the one accepted `ApiBearerSecurityConfiguration` authorization rules so exactly these unauthenticated requests are permitted:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
```

Every other request matched by `/api/v1/**` remains authenticated. Do not add another `SecurityFilterChain`, authentication provider/manager, form login, HTTP Basic, generated user, custom bearer resolver, CORS rule, or catch-all chain.

The login controller calls the application workflow directly; it is not a Spring Security username/password filter or form-login processing URL.

## Explicit non-goals

- No `POST /api/v1/auth/refresh`, refresh-token consumption, rotation, replacement, reuse detection, family revocation, or `lastUsedAt` mutation.
- No logout, clear-cookie endpoint, current/all-device revocation, session listing, or session deletion.
- No auto-detection or implicit default for refresh-token delivery and no additional login route for web, native, mobile, or legacy clients.
- No cross-site cookie support, `SameSite=None`, cookie domain, CORS policy, CSRF-token mechanism, origin allowlist, proxy/TLS configuration, or frontend integration.
- No change to access-token claims, decoder, validator, database-backed converter, bearer entry point, authority-free principal, or protected-route behavior beyond permitting the exact login POST.
- No persistent/rotating signing key, JWK endpoint, OIDC/Google login, role, permission, scope, owner helper, method security, or access-denied policy.
- No password-policy, email-normalization, credential-timing, refresh-token-generation, hashing, session-lifetime, or access-token-lifetime change.
- No registration auto-login, email verification, password reset/change, authentication event, security-event write, throttling, progressive delay, lockout, breached-password check, or session-count limit.
- No new error code, exception hierarchy, generic response wrapper, controller/service interface, mapper, or token-delivery abstraction.
- No dependency, runtime-property, migration, table, constraint, index, entity, repository, financial, reference, job, frontend, or legacy `/api/auth/**` compatibility change.
- No Git operation and no modification to the pre-existing user change in `AGENTS.md`.

## Database changes

Migration(s): none.

Tables/columns/constraints/indexes introduced or changed: none.

A successful login writes exactly the already-defined initial `identity.device_session` row through `LocalLoginService`. The raw refresh token is never persisted; all user/auth-identity state remains unchanged.

## Application changes

Expected production-code surface is limited to:

```text
src/main/java/dev/canverse/stocks/identity/
├── configuration/
│   └── ApiBearerSecurityConfiguration.java       # permit exact login POST
├── input/
│   ├── LocalLoginRequest.java
│   └── RefreshTokenDelivery.java
├── output/
│   └── LocalLoginResponse.java
└── web/
    └── LocalLoginController.java
```

The controller owns only HTTP validation, explicit delivery selection, safe response headers/cookie construction, and mapping of the accepted application result. `LocalLoginService` remains the transaction/orchestration owner and must not change.

Expected focused test surface:

```text
src/test/java/dev/canverse/stocks/identity/
├── LocalLoginHttpTest.java
└── ApiBearerSecurityHttpTest.java                # stale test name/assertion wording only if needed
```

Do not add a cookie factory, login facade, mapper, custom authentication filter, or alternate result type for one controller.

## API contract

### `POST /api/v1/auth/login`

The endpoint is public under the existing stateless API chain and requires JSON.

Request:

```json
{
  "email": "Alice.Example@example.com",
  "password": "correct horse battery staple",
  "deviceLabel": "Alice's iPhone",
  "refreshTokenDelivery": "RESPONSE_BODY"
}
```

`deviceLabel` may be `null`. `refreshTokenDelivery` is required.

Successful `RESPONSE_BODY` response:

```text
HTTP 200 OK
Content-Type: application/json
Cache-Control: no-store
Pragma: no-cache
Set-Cookie: absent
```

```json
{
  "sessionId": "30000000-0000-4000-8000-000000000003",
  "accessToken": "<compact RS256 access token>",
  "accessTokenExpiresAt": "2026-08-15T12:15:00Z",
  "refreshTokenExpiresAt": "2026-09-14T12:00:00Z",
  "serverTime": "2026-08-15T12:00:00Z",
  "refreshToken": "<opaque refresh token>"
}
```

Successful `HTTP_ONLY_COOKIE` response has the same common JSON fields and:

```text
refreshToken JSON property: absent
Set-Cookie: refresh-token=<opaque refresh token>; Path=/api/v1/auth; Max-Age=<positive>; Expires=<not after session expiry>; Secure; HttpOnly; SameSite=Strict
```

Invalid structurally valid credentials use the existing application contract:

```text
HTTP 401 Unauthorized
Content-Type: application/problem+json
code: INVALID_CREDENTIALS
key: error.identity.invalid_credentials
Set-Cookie: absent
```

Do not add a `WWW-Authenticate` challenge to credential-login failures; that header remains the bearer entry point's contract for protected-resource authentication failures.

Invalid request fields produce the existing trace-correlated `422 VALIDATION_FAILED` shape. Malformed JSON and unknown delivery enum values produce the existing trace-correlated `400 MALFORMED_REQUEST` shape. Failure responses contain no token/cookie, password, submitted email, credential-state reason, SQL, constraint, exception, or internal detail.

No compatibility route under `/api/auth` is added.

## Business invariants

- A `200` response means the accepted atomic login workflow committed exactly one initial refresh-session row and issued an access token bound to that exact session.
- Every request explicitly chooses one refresh-token delivery channel; the raw refresh token is emitted once through that channel and never through both.
- In response-body mode, no refresh cookie is written. In cookie mode, the raw refresh token is absent from JSON and the hardened host-only cookie never outlives the backing session.
- PostgreSQL stores only the deterministic refresh-token hash. Neither raw token, password, nor access token is persisted.
- HTTP validation runs before the application workflow. Invalid structure creates no session and performs no credential/token workflow.
- Structurally valid unknown-email, wrong-password, null-hash, and disabled-account attempts retain the accepted indistinguishable `INVALID_CREDENTIALS` behavior and create no session.
- The exact login POST is public, but login creates no servlet authentication/session and grants no authority to the request that performed it. Later API calls must present the returned access bearer token.
- Successful responses are explicitly non-cacheable and expose only the accepted token/session metadata.
- Request tracing and safe error handling remain owned by the existing global boundary.

## Required tests

### Pure/domain

No new pure/domain test is required. PR-008, PR-010, PR-011, and PR-012 retain the credential, token, session, and transaction behavior coverage. The new behavior is an HTTP/security delivery boundary.

### PostgreSQL/Testcontainers

Add one `LocalLoginHttpTest` using `@SpringBootTest`, Boot 4 `@AutoConfigureMockMvc` with default filters, migrated PostgreSQL, explicit cleanup, and no test-level transaction.

Use the real registration/login services, password encoder, refresh-token generator, JWT encoder/decoder, repositories, security chain, global exception handler, and request trace filter. Use the existing fixed-clock/deterministic-ID test pattern. Do not mock a repository, `LocalLoginService`, decoder, or filter chain.

Cover at least:

1. **Response-body delivery commits one exact session**
   - establish an enabled local account through the accepted registration workflow;
   - call the login POST without a bearer token using `RESPONSE_BODY` and a non-null device label;
   - assert exact `200`, JSON, trace, no-store/no-cache, no `Set-Cookie`, and no `JSESSIONID`;
   - assert every response field, fixed `serverTime`, and raw refresh-token presence;
   - decode/validate the access token through the accepted production decoder and prove its `sub`/`sid` bind to the persisted user/session;
   - prove exactly one active initial session committed with the unchanged label, exact expiries, null usage/revocation/replacement state, and only the refresh-token hash persisted;
   - prove the returned raw refresh token authenticates to that session through the accepted refresh-session authentication service;
   - prove user/auth-identity state is unchanged and neither password nor password hash appears in the response.

2. **HTTP-only-cookie delivery uses only the cookie channel**
   - call the same public endpoint with `HTTP_ONLY_COOKIE` and a null device label;
   - assert the common successful response fields;
   - assert the `refreshToken` JSON property is absent;
   - parse the single `Set-Cookie` header and assert exact name, path, host-only status, `Secure`, `HttpOnly`, `SameSite=Strict`, positive whole-second `Max-Age`, and an expiry no later than the persisted full-precision session expiry;
   - prove the cookie value authenticates to the exact committed session and does not appear in any database text column;
   - assert no `JSESSIONID` and no persisted Spring Security context.

3. **Credential failures are uniform and deliver no token**
   - cover a structurally valid unknown email and wrong password;
   - assert the exact existing `401 INVALID_CREDENTIALS` Problem Detail, trace header/body equality, and request-path instance;
   - assert no `Set-Cookie`, access token, refresh token, submitted password/email, credential-state reason, or internal detail;
   - assert no session row and unchanged identity rows.

4. **Request validation and parsing fail before login**
   - cover the accepted email/password structural boundaries;
   - retain the successful null-label case and cover blank, whitespace-padded, and over-128-character labels as validation failures;
   - cover missing delivery as `422 VALIDATION_FAILED`;
   - cover an unknown delivery string and malformed JSON as `400 MALFORMED_REQUEST`;
   - assert safe trace-correlated problems, no cookie/token, no session row, and no login-owned ID/JWT issuance after the request trace ID.

5. **The public-route change remains exact**
   - prove unauthenticated login reaches the controller under the real filter chain;
   - keep the existing proof that registration remains public, other `/api/v1/**` requests require bearer authentication, outside routes remain unmatched, and exactly one production chain exists;
   - rename only stale PR-016 test wording that claims registration is the sole public route; do not weaken any scope, statelessness, error, converter, or persistence assertion.

All accepted service, issuance, decoder, converter, registration, error, migration, and mapping tests remain green.

### HTTP/security

The real-filter `LocalLoginHttpTest` and retained `ApiBearerSecurityHttpTest` assertions are the required HTTP/security coverage. Do not use Spring Security test authentication helpers or disable filters.

## Acceptance criteria

1. Exactly one new controller exposes only `POST /api/v1/auth/login`, with one request record, one response record, and one two-value delivery enum in the directional identity DTO packages.
2. Email/password validation exactly matches registration; optional device labels are unchanged when valid and are bounded/nonblank when present; delivery choice is mandatory.
3. The controller invokes the accepted `LocalLoginService` once, observes the injected clock once after success, and owns no authentication, persistence, hashing, JWT, or transaction logic.
4. The existing stateless API chain permits exactly the registration and login POSTs; every other matched route retains accepted bearer authentication, and exactly one production chain exists.
5. Both success modes return exact common session/access-token metadata, `serverTime`, `Cache-Control: no-store`, and `Pragma: no-cache` without a servlet session.
6. `RESPONSE_BODY` includes the raw refresh token in JSON and writes no cookie.
7. `HTTP_ONLY_COOKIE` omits the raw refresh-token JSON property and writes exactly one host-only `refresh-token` cookie with the specified path, security attributes, positive max age, and expiry that cannot outlive the session.
8. The controller branch and real HTTP tests prove that the selected delivery mode emits the raw refresh token through exactly one channel.
9. Successful login commits exactly one accepted initial device session, binds the access token to it, persists only the refresh-token hash, and leaves user/auth-identity state unchanged.
10. Structurally valid credential failures retain the existing indistinguishable trace-correlated `401 INVALID_CREDENTIALS` contract and create no session or token/cookie response.
11. Validation, unknown-enum, and malformed-body failures retain the accepted `422`/`400` contracts and stop before login workflow work.
12. No response leaks a password/hash, stored refresh hash, internal error, or credential-state reason; cookie mode exposes the raw refresh token only in its deliberate `Set-Cookie` channel.
13. No existing application service, token configuration, decoder/converter, entity, repository, error code, trace contract, migration, schema, dependency, runtime property, or frontend code changes.
14. No refresh consumption/rotation/reuse response, logout/revocation/session management, cross-site cookie/CORS/CSRF policy, authorization helper, persistent key, security event, abuse control, or unrelated R1 work is added.
15. The focused real-filter PostgreSQL suite and all existing tests pass without disabled filters or weakened assertions.
16. `./mvnw spotless:check`, the focused suite, `./mvnw test`, and `./mvnw verify` pass.
17. `git diff --check` passes; the implementation-attributable diff is limited to this specification, `CURRENT.md`, the objective PR-016 specification/state reconciliation, the five expected production files, the focused login HTTP suite, the narrow stale-test wording reconciliation, and normal completion documents. The pre-existing user-owned `AGENTS.md` modification remains untouched and outside scope.
18. Completion Record, `STATE.md`, and `progress-report.md` are accurate; `CURRENT.md` remains on PR-017 through implementation and review.
19. Implementation and review agents perform no Git mutation.

## Documentation completion

Before this implementation unit is considered complete:

- fill in this specification's Completion Record;
- update `docs/implementation/STATE.md` with the implemented login endpoint, explicit delivery modes, exact public routes, and still-deferred refresh lifecycle/cookie-consumption behavior;
- update `docs/review/progress-report.md` with the implementation, verification, and review result;
- keep `docs/implementation/CURRENT.md` pointing to PR-017 until the supervising user accepts and commits the completed unit.

Do not mark refresh rotation/reuse response, logout/revocation, session management, cross-site cookie deployment, CORS/CSRF policy, owner helpers, persistent keys, security events, abuse controls, jobs, or financial roadmap work implemented.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=LocalLoginHttpTest,ApiBearerSecurityHttpTest,LocalLoginServiceTest,LocalAccountRegistrationHttpTest,GlobalExceptionHandlerIntegrationTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required.

## Completion record

Fill this before marking PR-017 complete.

### Starting commit

- `9fcbb69` (`install stateless bearer authentication`).

### Implemented

- Added `RefreshTokenDelivery`, `LocalLoginRequest`, `LocalLoginResponse`, and `LocalLoginController` for the single public `POST /api/v1/auth/login` boundary.
- Added explicit response-body and hardened HTTP-only-cookie delivery, including non-cacheable success headers, exact common session metadata, and omission of the raw refresh token from cookie-mode JSON.
- Permitted only the exact login POST alongside the accepted registration POST in the existing stateless `/api/v1/**` security chain.
- Added six real-filter PostgreSQL HTTP tests covering both delivery channels, session/token binding, credential failures, structural boundaries, parsing/validation failures, exact public-route scope, and statelessness.
- Updated only the stale PR-016 test wording that referred to registration as the sole public route.

### Deviations from specification

- No implementation-scope deviations.
- At the supervising user's explicit conditional request, planning created PR-018 and advanced `CURRENT.md` after independent review passed but before the user-owned PR-017 commit. PR-018 therefore records that commit as an unresolved starting-state prerequisite rather than inventing a hash.

### New decisions

- None.

### Tests executed

- `./mvnw.cmd spotless:check` — passed.
- `./mvnw.cmd "-Dtest=LocalLoginHttpTest,ApiBearerSecurityHttpTest,LocalLoginServiceTest,LocalAccountRegistrationHttpTest,GlobalExceptionHandlerIntegrationTest" test` — passed 29 tests with 0 failures and 0 errors.
- `./mvnw.cmd test` — passed 105 tests with 0 failures, 0 errors, and 0 skipped tests.
- `./mvnw.cmd verify` — passed 105 tests with 0 failures, 0 errors, and 0 skipped tests.
- `git status --short` and `git diff --check` were run after the final documentation update; the expected PR-017 files are present and the diff check passes.

### Independent review

- Correction cycle 1 fixed the refresh cookie's exact persisted-session `Expires` value and reconciled premature completion wording.
- The corrected whole diff has no remaining `MUST FIX` or `SHOULD FIX` findings.
- The reviewer independently reran the focused 29-test PostgreSQL suite, the full 105-test suite, Spotless, and Maven `verify`; all passed with no failures, errors, or skipped tests.
- PR-017 is ready for the user's commit decision. No Git operation was performed by an agent.

### Follow-up work

- Refresh consumption/rotation/reuse response, logout/revocation/session management, cross-site cookie deployment, CORS/CSRF policy, owner helpers, security events, abuse controls, jobs, and financial roadmap work remain deferred.
