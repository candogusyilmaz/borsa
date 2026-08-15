# PR-016 — HTTP bearer authentication boundary

Status: **COMPLETE**

## Goal

Install one stateless Spring Security bearer boundary for `/api/v1/**` that keeps local registration public, authenticates every other matched request through the accepted local JWT decoder and database-backed converter, and returns the existing trace-correlated RFC 9457 invalid-credential response for missing or unusable bearer credentials.

## Source documents

- `docs/review/backend-master-plan.md` — API invariants and R1 authentication/session scope
- `docs/review/backend-audit.md` — SEC-003, SEC-004, and SEC-006
- `docs/review/mobile-api-readiness.md` — Authentication for web and mobile
- `docs/engineering/coding-standards.md`
- `docs/implementation/PR-006-stable-error-handling.md`
- `docs/implementation/PR-007-http-local-account-registration.md`
- `docs/implementation/PR-014-local-access-token-decoding.md`
- `docs/implementation/PR-015-database-backed-access-token-authentication.md`

## Starting state

Local registration is available at `POST /api/v1/auth/register`. PR-014 supplies the strict production `JwtDecoder`, and PR-015 supplies an authority-free database-backed `Converter<Jwt, AbstractAuthenticationToken>` that rechecks the exact current session/user pair. The application has no `SecurityFilterChain`; the existing request-trace filter and global exception handler already own trace correlation and RFC 9457 application errors.

Starting commit: `3d86ff2` (`authenticate local access tokens`).

## Scope

1. Add only the Boot-managed servlet security starter required to configure and register the HTTP security filter chain, plus the Boot 4 MVC test starter required for full-context `MockMvc` filter registration. Keep the accepted direct resource-server dependency and do not add version overrides or Spring Security test helpers.
2. Order the existing `RequestTraceFilter` at `Ordered.HIGHEST_PRECEDENCE` so its server-owned trace ID and response header exist before Spring Security can reject a request.
3. Add one identity security configuration defining exactly one `SecurityFilterChain` limited by `securityMatcher("/api/v1/**")`:
   - use `SessionCreationPolicy.STATELESS`;
   - disable CSRF only within this matched stateless API chain;
   - permit exactly `POST /api/v1/auth/register` without authentication;
   - require authentication for every other request matched by the chain;
   - configure JWT resource-server authentication with the existing production `JwtDecoder` and `LocalAccessTokenAuthenticationConverter`;
   - use the same custom authentication entry point for resource-server failures and general unauthenticated access.
4. Add one identity web authentication entry point that sets `WWW-Authenticate` to exactly `Bearer`, then delegates a fresh parameterless `AppException(IdentityErrorCode.INVALID_CREDENTIALS)` to Spring MVC's named `handlerExceptionResolver`. Do not serialize a second error shape or pass through the original security exception, token, claim, or reason. If the resolver returns `null`, throw a `ServletException` with a constant non-sensitive message rather than attempting an ad-hoc response.
5. Add one full-context PostgreSQL-backed HTTP/security integration suite using Boot 4 `@AutoConfigureMockMvc` with its default `addFilters = true`. The suite may import a test-only probe controller; no production endpoint is added.

## Explicit non-goals

- No production login, refresh, logout, session-list/revoke, `/me`, probe, or other endpoint and no request/response DTO.
- No decision about browser refresh cookies versus native refresh-token response delivery.
- No refresh rotation, replacement, family-reuse response, logout/revocation mutation, `lastUsedAt` update, security event, throttling, progressive delay, or account lock behavior.
- No role, permission, scope, granted authority, owner helper, method security, access-denied handler, or `403` application contract.
- No CORS policy, custom bearer-token resolver, query-parameter token, form login, HTTP Basic, generated user, remember-me, servlet session authentication, or saved-request behavior.
- No decoder, validator, issuer, converter, repository, entity, schema, migration, signing-key, runtime token-property, frontend, or financial change.
- No default catch-all security chain outside `/api/v1/**`; non-API routes retain their current behavior.

## Database changes

Migration(s): none.

Tables/columns/constraints/indexes introduced or changed: none.

HTTP authentication performs the existing PR-015 read-only owner-scoped session/user query only.

## Application changes

Expected production-code surface is limited to:

- `pom.xml` — add Boot-managed `spring-boot-starter-security` and test-scoped `spring-boot-starter-webmvc-test` only;
- `platform/web/trace/RequestTraceFilter.java` — add the explicit highest-precedence order;
- `identity/configuration/ApiBearerSecurityConfiguration.java` — define the one scoped stateless chain;
- `identity/web/LocalBearerAuthenticationEntryPoint.java` — bridge authentication failures into the accepted global application-error boundary.

Do not change `GlobalExceptionHandler`, an error enum, the accepted decoder/converter, a controller, persistence code, or runtime configuration.

## API contract

No endpoint or DTO is added.

Security behavior changes for the existing `/api/v1/**` namespace:

- `POST /api/v1/auth/register` remains public and preserves its accepted PR-007 success/error contract.
- Every other `/api/v1/**` request requires a valid current local access bearer token.
- Missing, malformed, cryptographically invalid, structurally invalid, expired, revoked-session, cross-user, missing-session, and disabled-user bearer attempts return the same response:
  - HTTP `401`;
  - `Content-Type: application/problem+json`;
  - `WWW-Authenticate: Bearer`;
  - the existing server-owned `X-Trace-Id` header;
  - problem type `https://canverse.dev/problems/invalid-credentials`;
  - title `Unauthorized`, status `401`, exact request-path instance, code `INVALID_CREDENTIALS`, key `error.identity.invalid_credentials`, matching body `traceId`, and injected-clock `timestamp`;
  - no `params` and no token, claim, identifier, decoder/converter reason, exception name, or internal detail.
- Requests outside `/api/v1/**` are not matched by this chain and retain their existing behavior.

## Business invariants

- A request is authenticated only when bearer decoding and the current database-backed exact session/user eligibility check both succeed.
- The chain is stateless: successful authentication is not saved in an HTTP session, and a later request without a bearer token is unauthenticated.
- The only public API exception is the exact existing registration POST route; future auth routes are not pre-permitted.
- Request correlation is established before every security outcome, including failures that occur before MVC controller dispatch.
- All expected authentication failures are indistinguishable at the public application-error boundary and disclose no bearer material or rejection reason.
- This PR establishes authentication only. The authority-free identity does not grant access to any future user-owned domain row without a later owner-scoped authorization rule.

## Required tests

### Pure/domain

No new pure test is required. PR-014 and PR-015 retain focused decoder and converter coverage; this unit is an HTTP/filter integration boundary.

### PostgreSQL/Testcontainers

The required HTTP/security suite uses the real migrated PostgreSQL database, production registration/login/token services, decoder, converter, repositories, and security chain. Do not mock token decoding or current session/user eligibility.

### HTTP/security

Add one `ApiBearerSecurityHttpTest` with `@SpringBootTest`, Boot 4 `@AutoConfigureMockMvc` using its default filter registration, Testcontainers PostgreSQL, explicit cleanup, and no test-level transaction. Import only test-owned probe endpoints needed to observe authentication.

Cover at least:

1. **The chain has the exact scope and public exception**
   - the context has exactly one `SecurityFilterChain`;
   - unauthenticated `POST /api/v1/auth/register` still returns `201` and commits its user/identity rows;
   - an unauthenticated test-only endpoint outside `/api/v1/**` remains reachable;
   - an unauthenticated test-only endpoint inside `/api/v1/**` is rejected before its controller is invoked.

2. **Expected bearer failures share one safe traced problem**
   - cover no `Authorization` header, a malformed/tampered token that cannot decode, and at least one validly signed token whose backing session is revoked or whose user is disabled;
   - assert the exact `401` headers and problem fields above, including body/header trace equality and the exact request-path instance;
   - assert the response contains none of the presented token, signed UUID claims, security exception type/message, state-specific reason, or database detail;
   - assert the probe is not invoked and identity/session rows remain unchanged by authentication attempts.

3. **A valid current bearer identity reaches MVC without authorities**
   - obtain the token through the accepted production registration/login workflow and send `Authorization: Bearer <token>`;
   - the test-only API probe observes a `JwtAuthenticationToken` whose name is the canonical user UUID, whose `sid` is the issued session UUID, and whose authorities are empty;
   - the successful response creates no `JSESSIONID` and does not change user, identity, or session rows.

4. **Authentication is not persisted between requests**
   - after a successful bearer request, a second request to the same API probe without the header returns the same `401` problem and does not invoke the probe;
   - no servlet session or saved security context makes the second request authenticated.

Keep the existing registration HTTP suite and all accepted migration, error, issuance, decoder, converter, and service tests green. Do not weaken the existing registration test's focused manual-filter setup merely to accommodate the new chain; the new full-filter suite owns proof that registration is public under production security.

PR-016 intentionally supersedes the one PR-015 full-context assertion that no `SecurityFilterChain` exists. Update only `LocalAccessTokenAuthenticationConverterTest.resourceServerLibraryAndConverterAddNoHttpSecurityBoundary` (renaming it as appropriate) so it proves the converter remains a singleton alongside exactly one production chain. Do not delete or weaken its converter-bean assertion or any converter behavior/state assertion. This planning clarification reconciles the accepted historical test with the new boundary; it does not broaden production scope.

## Acceptance criteria

1. The POM adds only Boot-managed `spring-boot-starter-security` and test-scoped `spring-boot-starter-webmvc-test`; it adds no versions, security-test library, alternative framework, or unrelated dependency.
2. `RequestTraceFilter` has explicit `Ordered.HIGHEST_PRECEDENCE`, and rejected security requests prove trace header/body correlation.
3. Exactly one production `SecurityFilterChain` is limited to `/api/v1/**`, is stateless, disables CSRF within that chain, permits only the exact registration POST, and authenticates every other matched request.
4. The chain explicitly uses the accepted production decoder and database-backed converter; it does not add another authentication manager/provider, user-details path, or identity lookup.
5. One custom entry point delegates a new parameterless `INVALID_CREDENTIALS` `AppException` through the named MVC exception resolver, sets exactly `WWW-Authenticate: Bearer`, and has only a constant safe unresolved-resolver failure.
6. Missing, malformed/invalid, and database-ineligible bearer attempts expose the same exact trace-correlated RFC 9457 `401` application contract without credential, claim, identifier, state, or reason leakage.
7. A valid current bearer reaches MVC as the accepted authority-free `JwtAuthenticationToken` with canonical user UUID name and exact `sid`.
8. Authentication is stateless: no `JSESSIONID` or persisted security context authenticates a later headerless request.
9. The exact registration POST remains public and committed; other API routes are not pre-permitted; routes outside `/api/v1/**` remain unmatched by the chain.
10. Authentication performs no persistence mutation, token issuance/refresh, security-event/job creation, or network call.
11. No production endpoint/DTO, access-denied/authorization policy, CORS/transport policy, refresh/logout behavior, migration/mapping, key policy, frontend, or financial change is added.
12. The new real-filter PostgreSQL HTTP/security suite and all existing tests pass without weakened assertions; the one superseded PR-015 no-chain assertion is reconciled exactly as specified above.
13. `./mvnw spotless:check`, the focused suite, `./mvnw test`, and `./mvnw verify` pass.
14. `git diff --check` passes and the complete diff contains only PR-016 planning, the two exact dependencies, trace-filter ordering, security configuration/entry point, the scoped HTTP/security test, the one specified PR-015 converter-test assertion reconciliation, and completion-document files.
15. Completion Record, `STATE.md`, and `progress-report.md` are accurate; `CURRENT.md` remains on PR-016 through implementation and review.
16. Implementation and review agents perform no Git mutation.

## Documentation completion

Before completion, fill this Completion Record, update `STATE.md` and `progress-report.md`, and keep `CURRENT.md` on PR-016 until the supervisor commit.

Do not mark login/refresh token delivery, refresh rotation/reuse response, logout/revocation, roles/permissions/owner helpers, security events, jobs, abuse controls, persistent keys, or any financial roadmap increment implemented.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=ApiBearerSecurityHttpTest,LocalAccountRegistrationHttpTest,LocalAccessTokenAuthenticationConverterTest,LocalAccessTokenDecoderTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required.

## Completion record

Fill this before marking PR-016 complete.

### Starting commit

- `3d86ff2` (`authenticate local access tokens`).

### Accepted commit

- `9fcbb69` (`install stateless bearer authentication`).

### Implemented

- Added the Boot-managed servlet security starter and test-scoped Boot 4 MVC test starter while retaining the accepted direct resource-server library.
- Ordered `RequestTraceFilter` at highest precedence and added one servlet-only stateless `/api/v1/**` security chain that keeps only the exact registration POST public and reuses the accepted decoder and database-backed converter.
- Added one bearer authentication entry point that sets the exact `Bearer` challenge and delegates a fresh parameterless `INVALID_CREDENTIALS` application exception to the named MVC exception resolver with a constant safe unresolved-resolver failure.
- Added real-filter PostgreSQL `MockMvc` coverage for chain scope, committed public registration, uniform safe traced failures, current authority-free JWT authentication, unchanged persistence, and headerless follow-up rejection.
- Reconciled the one superseded PR-015 no-chain assertion so it retains the converter singleton proof alongside exactly one production chain.

### Deviations from specification

- None.

### New decisions

- The HTTP security configuration is conditional on a servlet web application, so the production boundary is present in servlet contexts while established non-web application tests remain free of MVC-only collaborators.
- The active specification corrected its PR-006 source-document filename and explicitly documented the narrowly superseded PR-015 no-chain assertion before review; neither correction broadened production scope.
- Independent review correction cycle 1 reconciled four stale `STATE.md` assertions so the repository state consistently records public registration under the installed servlet bearer chain, the decoder's HTTP wiring, and the remaining deferred login/refresh-delivery and authorization work.

### Tests executed

- `./mvnw spotless:check` passed for all 67 Java files.
- `./mvnw -Dtest=ApiBearerSecurityHttpTest,LocalAccountRegistrationHttpTest,LocalAccessTokenAuthenticationConverterTest,LocalAccessTokenDecoderTest test` passed: 16 tests, 0 failures, 0 errors, 0 skipped.
- `./mvnw test` passed: 99 tests, 0 failures, 0 errors, 0 skipped across 22 Surefire reports.
- `./mvnw verify` passed and packaged the application: 99 tests, 0 failures, 0 errors, 0 skipped.
- `git status --short`, tracked and untracked whitespace checks, the dependency audit, and the complete-diff scope audit passed with only the documented PR-016 planning, production, test, and completion files present.

### Independent review

- Correction-cycle-1 whole-diff review passed with no remaining `MUST FIX` or `SHOULD FIX` findings.
- The sole review correction was documentation-only: four stale pre-PR-016 assertions in `STATE.md` were removed or reworded to acknowledge the installed HTTP bearer boundary while preserving the accurately deferred work.

### Follow-up work

- PR-016 was accepted in local commit `9fcbb69`; `CURRENT.md` advanced only in the subsequent planning invocation.
- Login/refresh token delivery, refresh rotation and reuse response, logout/revocation, roles/permissions/owner helpers, security events, jobs, abuse controls, persistent signing keys, and financial roadmap work remain separate future units.
