# PR-028 - Identity authentication boundary consolidation

Status: **COMPLETE**

## Goal

Reduce accidental identity HTTP and attempt-orchestration ceremony while preserving every existing authentication, session, security-event, transaction, cookie, ownership, and API behavior. Group related public authentication endpoints in one controller, group anonymous attempt policy in one non-transactional application service, reuse one successful credential result/response shape, and place logout with device-session HTTP operations.

## Capability and review boundary

- Coherent capability: registration, login, and refresh share one anonymous authentication perimeter, abuse-protection collaborator, trace/source context, and token-delivery contract.
- Combined behaviors: consolidate the three public local-authentication controllers, the two existing attempt wrappers plus refresh attempt policy, the duplicate login/refresh credential models, and the logout controller placement.
- Preserved boundaries: password verification, initial refresh-session issuance, access-token issuance, refresh rotation, device-session queries, device-session revocation, abuse-state mechanics, security-event persistence, repositories, and security adapters remain independently owned.
- Excluded neighbor: no identity domain, repository, configuration, cryptography, schema, authorization, OIDC/MFA, frontend, ledger, reference, or R4 work.

## Source documents

- `docs/engineering/coding-standards.md` - directness, controller, model, service/repository granularity, security, and testing rules.
- `docs/review/backend-master-plan.md` - architecture, file economy, R1 identity invariants, and testing strategy.
- `docs/implementation/STATE.md` - accepted baseline through PR-027.

## Starting state

- PR-027 is accepted and committed in `4e3108d`; no backend implementation PR was active before this unit.
- Identity exposes six REST controllers and eleven `@Service` classes.
- Registration and login use separate attempt wrappers, while refresh performs equivalent abuse/security-event orchestration in `LocalRefreshController`.
- Login and refresh use distinct application results and response records with the same successful credential shape.

## Scope

1. Add one non-transactional `AuthenticationAttemptService` that owns registration, login, and refresh admission/failure/success abuse-protection orchestration and anonymous throttle/failure security events.
2. Preserve `LocalAccountRegistrationService`, `LocalLoginService`, and `RefreshSessionRotationService` as separately proxied transactional collaborators so attempt success state changes occur only after their database transactions return successfully.
3. Move refresh credential-channel selection and refresh failure accounting out of the controller without introducing a command/wrapper model.
4. Replace `LocalLoginResult` and `LocalRefreshResult` with one meaningful successful local-authentication result.
5. Replace `LocalLoginResponse` and `LocalRefreshResponse` with one response record preserving the exact JSON shape and optional refresh-token behavior.
6. Replace the registration, login, and refresh controllers with one `LocalAuthenticationController` that retains only HTTP binding, servlet cookie extraction, trace/source extraction, no-store headers, and cookie/body response delivery.
7. Move the unchanged `/api/v1/auth/logout` handler into `DeviceSessionController`; preserve the query and revocation services as separate read-family and locked lifecycle boundaries.
8. Update existing behavior-focused tests and add focused attempt-service coverage only where existing HTTP/integration coverage does not directly prove the moved control flow.

## Explicit non-goals

- No endpoint path, HTTP method, status, request, JSON field, serialization order, validation, error code, header, cookie, or bearer-chain change.
- No transaction propagation, owner-lock ordering, refresh rotation/reuse, rollback, revocation, or security-event semantic change.
- No merge of device-session query/revocation services, JPA/JdbcClient repositories, token issuance, password authentication, session issuance/rotation, abuse protection, or Spring Security adapters.
- No migration, dependency, configuration, frontend, provider, OIDC, recovery, MFA, role/permission, ledger, reference, or investing work.

## Database changes

None.

## Application changes

- Add `AuthenticationAttemptService`, `LocalAuthenticationResult`, `LocalAuthenticationResponse`, and `LocalAuthenticationController`.
- Delete the superseded operation-specific attempt services, controllers, and duplicate login/refresh result/response records.
- Modify `LocalLoginService`, `RefreshSessionRotationService`, and `DeviceSessionController` only as required to use the consolidated boundaries.
- Keep `AuthenticationAttemptService` non-transactional. It must call the existing transactional core services through injected Spring proxies; do not replace this with self-invocation or programmatic transaction machinery.

## API contract

No observable API change. Preserve:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/sessions`
- `GET/DELETE /api/v1/auth/sessions/{familyId}`
- `GET /api/v1/me`

## Business and security invariants

- Login invalid credentials still record the anonymous failure event and transition throttling exactly as before.
- Registration and refresh blocking thresholds, just-blocked behavior, fail-closed capacity behavior, and throttle rollback on event-persistence failure remain unchanged.
- Login and refresh success reset abuse state only after the corresponding core transaction successfully returns.
- Anonymous events remain `REQUIRES_NEW`; user-scoped success/reuse/revocation events retain their existing transaction participation.
- Refresh credential delivery remains exactly one body or cookie channel. Ambiguous, missing, blank, duplicate, and wrong-channel credentials remain safe failures before rotation.
- Refresh-token reuse still commits family revocation before the public request returns the existing safe credential failure.
- Owner scoping, current-session cookie clearing, statelessness, and no-store behavior remain unchanged.

## Required tests

### Pure/control flow

- Consolidated attempt service short-circuits blocked operations, preserves exact failure accounting, and rolls back a newly created throttle transition when its event cannot be persisted.
- Existing password timing, token generation, JWT, domain session lifecycle, and rotation control-flow tests remain green.

### PostgreSQL/Testcontainers

- Registration/login/rotation rollback, security-event persistence, refresh reuse, session revocation, and refresh-versus-revocation concurrency tests remain green.

### HTTP/security

- Existing registration, login, refresh, logout, session, abuse, bearer, and current-user tests prove unchanged paths, response bodies, headers, cookies, problems, ownership, and statelessness.

## Acceptance criteria

1. Exactly three identity REST controllers remain: local authentication, device sessions, and current user.
2. One non-transactional attempt service owns registration/login/refresh abuse and anonymous-event orchestration.
3. Login and refresh share one application result and one HTTP response without changing their public JSON contract.
4. Core security, transaction, lifecycle, query-family, repository, and adapter boundaries listed above remain separate.
5. Every named HTTP/security/transaction/concurrency behavior remains unchanged and covered.
6. Deleted type names have no production or test references.
7. Focused identity tests, the full suite, Maven `verify`, Spotless, and `git diff --check` pass.

## Documentation completion

Before completion, update `STATE.md`, this completion record, and `progress-report.md` with current reality. Leave `CURRENT.md` pointed at PR-028 until the user accepts it.

## Verification commands

Run from `server/`:

```powershell
.\mvnw.cmd "-Dtest=LocalAccountRegistrationServiceTest,LocalAccountRegistrationHttpTest,LocalPasswordAuthenticationServiceTest,LocalPasswordAuthenticationTimingTest,LocalLoginServiceTest,LocalLoginServiceFamilyIdTest,LocalLoginHttpTest,RefreshSessionIssuanceServiceTest,RefreshSessionRotationControlFlowTest,RefreshSessionRotationServiceTest,LocalRefreshHttpTest,AuthenticationAbuseProtectionTest,AuthenticationAbuseHttpTest,IdentitySecurityEventIntegrationTest,DeviceSessionLifecycleTest,DeviceSessionQueryServiceTest,DeviceSessionRevocationServiceTest,DeviceSessionHttpTest,LocalLogoutHttpTest,RefreshRevocationConcurrencyTest,ApiBearerSecurityHttpTest,CurrentUserHttpTest" test
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spotless:check
```

## Completion record

### Implemented

- Replaced the registration, login, and refresh controllers with one `LocalAuthenticationController` while preserving every route, status, request, response, header, cookie, validation, trace, and credential-channel contract.
- Replaced the registration/login attempt wrappers and controller-owned refresh attempt policy with one deliberately non-transactional `AuthenticationAttemptService`; the existing transactional registration, login, and rotation services remain separately proxied collaborators.
- Replaced the duplicate login/refresh success models with `LocalAuthenticationResult` and `LocalAuthenticationResponse`.
- Moved the unchanged logout HTTP handler into `DeviceSessionController` while retaining separate query and revocation application services.
- Removed all superseded controllers, attempt services, and duplicate result/response records. Identity now has exactly three REST controllers and ten `@Service` classes, with 192 production and 63 test Java files repository-wide.

### Deviations from specification

- None.

### New decisions

- `AuthenticationAttemptService` accepts the validated refresh request plus the servlet-extracted refresh-cookie values directly. A separate refresh-attempt command type would only recreate the mapping ceremony this unit removes.
- Refresh credential selection remains application attempt policy, while raw servlet-cookie extraction and response cookie/header construction remain HTTP concerns in `LocalAuthenticationController`.

### Tests executed

- `.\mvnw.cmd -DskipTests compile` - passed; 192 production sources compiled.
- Focused identity/security gate named above - 108 tests passed with 0 failures, 0 errors, and 0 skips against PostgreSQL 17 Testcontainers.
- `.\mvnw.cmd test` - 371 tests passed with 0 failures, 0 errors, and 0 skips.
- `.\mvnw.cmd verify` - build passed; 371 tests passed and the executable archive was repackaged.
- Spotless check passed across 255 Java files (192 production and 63 test); `git diff --check`, deleted-symbol searches, controller/service counts, and the non-transactional attempt-service audit passed.

### Follow-up work

- None within this unit. PR-028 was accepted and committed in `ac4d7e7`; broader identity capabilities, R4, and the next lifecycle transition remain separately controlled.
