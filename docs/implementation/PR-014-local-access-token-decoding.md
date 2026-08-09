# PR-014 — Local access-token decoding

Status: **COMPLETE**

## Goal

Add one production `JwtDecoder` for the already accepted startup-local RSA key and one strict validator for the locally issued access-token envelope. A token is accepted only when its RS256 signature, local headers, issuer, singleton audience, identifier claims, and exact validity window satisfy the PR-011 contract.

This PR establishes cryptographic and structural access-token decoding only. It does not read a user or device session, create an authenticated principal, install a bearer filter chain, or expose an HTTP authentication boundary.

## Source documents

- `AGENTS.md`
- `docs/implementation/STATE.md`
- `docs/implementation/PR-011-local-access-token-issuance.md` — accepted local key, header, claim, and whole-second validity-window contract
- `docs/implementation/PR-013-opaque-refresh-session-authentication.md` — most recently completed implementation unit
- `docs/review/backend-master-plan.md`
  - R0 item 9: disposable local signing-key flow
  - R1 items 5, 7, 9, and 10: authentication/session foundation, later principal helpers, authorization coverage, and disabled-user rejection
  - security, testing, dependency, and pull-request rules
- `docs/review/backend-audit.md`
  - SEC-003: access-token type/session distinction
  - SEC-004: later disabled-user rejection during token conversion
- `docs/review/mobile-api-readiness.md`
  - short-lived access JWT with explicit type, audience, issuer, session ID, and key ID
- `docs/engineering/coding-standards.md`
  - injected clocks, configuration, dependency management, security, and test rules
- Spring Security 7 documentation for `NimbusJwtDecoder.withPublicKey(...)`, one-algorithm trust, custom type validation, and `OAuth2TokenValidator<Jwt>`

`docs/review/accounting-contract.md` is not required because this PR introduces no financial behavior.

## Starting state

Treat local commit `7bb7c40` as authoritative.

Already implemented and not to be redesigned here:

- `AccessTokenProperties` supplies the validated absolute issuer URI, one audience, positive lifetime, and local key ID;
- `LocalAccessTokenConfiguration` creates one startup-local 2048-bit RSA `KeyPair` and one RS256 `JwtEncoder` from it;
- `AccessTokenIssuanceService` emits whole-second tokens with exact `alg=RS256`, configured `kid`, `typ=access`, and required `iss`, `sub`, singleton `aud`, `iat`, `nbf`, `exp`, `jti`, and `sid` values;
- issued `sub`, `jti`, and `sid` values are canonical UUID strings, `iat` equals `nbf`, and the positive validity window never exceeds the configured access-token lifetime;
- the existing JOSE dependency contains the decoder and validator APIs needed by this unit;
- there is no production decoder, bearer filter chain, principal, or request authorization.

The startup-local key intentionally invalidates earlier tokens when the process restarts. Persistent/external key management remains a deployment prerequisite and is not designed here.

## Scope

### 1. Add one production decoder from the existing local key

Extend:

```text
dev.canverse.stocks.identity.configuration.LocalAccessTokenConfiguration
```

with one `JwtDecoder` bean built through `NimbusJwtDecoder.withPublicKey(...)` from the public half of the existing singleton `KeyPair`.

The decoder must:

1. trust only `SignatureAlgorithm.RS256`;
2. call `validateType(false)` so the repository-owned validator, rather than Nimbus's generic JWT-type policy, enforces exact `typ=access`;
3. install exactly one local validator instance configured from the existing `AccessTokenProperties` and injected application `Clock`;
4. reuse the existing key pair and create no second key, encoder, remote JWK lookup, issuer discovery, or network dependency.

Do not add a resource-server starter. `spring-security-oauth2-jose` already supplies this application-only decoder boundary.

### 2. Validate the exact local access-token envelope

Add one package-local final class:

```text
dev.canverse.stocks.identity.configuration.LocalAccessTokenValidator
```

implementing `OAuth2TokenValidator<Jwt>`. Construct it directly from the decoder bean with exactly:

- `AccessTokenProperties`;
- `Clock`.

Do not register it as a separately injectable generic validator bean and do not create a validator framework or interface beyond Spring Security's existing contract.

For every successfully parsed and cryptographically verified JWT, require all of the following:

1. `alg` is exactly `RS256`, `kid` exactly equals the configured key ID, and `typ` is exactly `access`;
2. `iss` exactly equals the configured issuer URI;
3. decoded `aud` is exactly one element equal to the configured audience; missing, wrong, or additional audiences fail;
4. `sub`, `jti`, and string claim `sid` are present as exact canonical UUID strings (`UUID.fromString(value).toString().equals(value)`);
5. `iat`, `nbf`, and `exp` are all present at whole-second precision;
6. `iat` equals `nbf`;
7. the configured `Clock` supplies the time decision: `nbf` is at or before the observed instant and `exp` is strictly after it, so equality at expiry is invalid;
8. `exp` is strictly after `iat`, and the interval from `iat` to `exp` does not exceed the configured lifetime.

Observe the injected clock once per validator invocation. Do not use the machine clock through `Instant.now()` or Spring's default 60-second skew. There is no skew because this process both issues and validates the local development token; a later distributed deployment/key-management PR may introduce an explicit skew policy if required.

Return one `OAuth2TokenValidatorResult.failure(...)` with OAuth error code `invalid_token` and a single constant safe description for every envelope failure. The error must not contain the compact token, a claim/header value, an identifier, an expected value, or a reason-specific detail. Do not log token material or validation values. Normal invalid-token outcomes must not throw from the custom validator.

The decoder's own malformed-token, untrusted-algorithm, and bad-signature failures remain framework failures before this validator is invoked. This PR does not map any decoder exception to HTTP.

Additional signed claims are not authorization inputs in this unit. Do not create roles, permissions, or authority conversion.

## Explicit non-goals

- No `SecurityFilterChain`, OAuth2 resource-server configuration, bearer-token extraction, request authorization, security context, authentication entry point, or access-denied handler.
- No principal/authority converter, user/session database lookup, enabled/revoked-session check, cache, or owner helper. Those require a separate authenticated-principal unit.
- No controller, route, API request/response record, login/refresh delivery, cookie, header policy, CORS, or web/mobile transport decision.
- No refresh rotation/replacement/reuse response, logout/revocation, session management, event, abuse control, or job behavior.
- No persistent key, keystore, PEM/JWK endpoint, rotation, remote discovery, provider/OIDC, Google, or deployment-secret configuration.
- No migration, table, constraint, index, JPA mapping, repository, entity, dependency, frontend, or financial behavior.
- No change to access-token issuance claims, lifetime, NumericDate normalization, or ephemeral-key semantics.

## Database changes

Migration(s): none.

Tables/columns/constraints/indexes introduced or changed: none.

The decoder and validator perform no database access.

## Application changes

Expected production-code surface is limited to:

- `identity/configuration/LocalAccessTokenConfiguration.java` — expose the decoder from the existing public key and install the local validator;
- `identity/configuration/LocalAccessTokenValidator.java` — enforce the accepted local header/claim/time envelope.

No application service, repository, entity, or HTTP class is added.

## API contract

None. No endpoint, response, cookie/header, HTTP status, public error mapping, or request authentication behavior is added or changed.

## Business invariants

- A local access token is decoded only with the public half of the same process-local RSA key and only under RS256.
- Generic or refresh-like JWTs cannot pass as access tokens: the configured key ID, exact access type, issuer, singleton audience, identifiers, and time envelope are required.
- A token is invalid at its exact `exp` instant and before its `nbf` instant; there is no implicit framework clock skew.
- Validation uses only injected configuration and time and performs no persistence or network work.
- Successful cryptographic/structural decoding alone does not authenticate a request or prove that the user/session remains eligible.
- Invalid-token details and token material are neither logged nor included in custom validation errors.

## Required tests

### Pure/configuration

Add one focused `LocalAccessTokenDecoderTest` using `ApplicationContextRunner` or an equivalently small Spring context, the real local configuration, a fixed/controllable injected `Clock`, and the real encoder/decoder.

Cover at least:

1. **Valid issued envelope decodes**
   - the context contains exactly one `KeyPair`, `JwtEncoder`, and `JwtDecoder`;
   - a token signed by the production encoder with the complete accepted envelope decodes through the production decoder;
   - decoded headers/claims retain the exact configured identity and validity values.

2. **Signature and algorithm trust are local and exact**
   - an otherwise valid RS256 token signed by an unrelated RSA key is rejected;
   - an otherwise valid token signed with the local RSA key under RS512 is rejected;
   - neither failure invokes or weakens the envelope validator to manufacture success.

3. **Header and claim envelope fails closed**
   - missing/wrong `typ` and missing/wrong `kid` fail;
   - missing/wrong issuer fails;
   - missing, wrong, and multi-valued audience fail;
   - missing/malformed/non-canonical `sub`, `jti`, and `sid` fail;
   - missing `iat`, `nbf`, or `exp`, unequal `iat`/`nbf`, non-positive or over-lifetime windows fail;
   - a future `nbf` fails, `nbf` equal to the observed instant is valid, and `exp` equal to the observed instant fails;
   - fractional observed clock time is handled against whole-second claims without using the system clock.

4. **Validation failures are safe and stable**
   - every custom envelope failure returns only `invalid_token` with the same constant safe description;
   - no error text contains the compact token, submitted/expected header or claim values, UUIDs, or reason-specific detail.

Update `AccessTokenPropertiesTest` only as necessary to supply the newly required `Clock` and preserve all existing property, key, and encoder assertions. Do not weaken PR-011 tests or add reusable JWT test infrastructure for later bearer/principal work.

### PostgreSQL/Testcontainers

No new database-specific test is required because the production change has no persistence behavior. The focused command includes the accepted PostgreSQL `AccessTokenIssuanceServiceTest` to prove that real issued tokens remain compatible, and the complete test/verify commands must keep every Testcontainers suite green.

### HTTP/security

The decoder configuration test is the required security coverage for this application-only PR. No MockMvc or filter-chain test is required because no incoming HTTP bearer boundary exists.

## Acceptance criteria

1. Exactly one production `JwtDecoder` is built from the existing singleton RSA public key; no key, network lookup, or dependency is added.
2. The decoder trusts only RS256 and delegates type/header/claim checks to exactly one repository-owned local validator.
3. Only exact configured `alg`, `kid`, `typ`, issuer, singleton audience, canonical UUID `sub`/`jti`/`sid`, and complete whole-second time claims pass.
4. Validation observes the injected clock once, applies no implicit skew, accepts `nbf <= now`, requires `exp > now`, and rejects a non-positive or over-configured-lifetime window.
5. All custom envelope failures use one safe constant `invalid_token` result without token/header/claim/identifier detail or logging.
6. Unrelated-key and RS512 tokens are rejected cryptographically; a complete locally signed RS256 token decodes successfully.
7. Decoding performs no database mutation/read, network request, user/session eligibility decision, authentication/principal conversion, or HTTP behavior.
8. No migration, mapping, entity, repository, application service, dependency, resource-server/filter-chain, API, refresh behavior, stable-key infrastructure, frontend, or financial change is added.
9. Required focused and complete tests pass without weakening existing assertions.
10. `./mvnw spotless:check`, focused suite, `./mvnw test`, and `./mvnw verify` pass.
11. `git diff --check` passes and the complete diff contains only PR-014 planning, production, test, and completion-document files.
12. Completion Record, `STATE.md`, and `progress-report.md` are accurate; `CURRENT.md` remains on PR-014 through review.
13. Implementation and review agents perform no Git mutation.

## Documentation completion

Before completion, fill this Completion Record, update `STATE.md` and `progress-report.md`, and keep `CURRENT.md` on PR-014 until supervisor commit.

Do not mark bearer HTTP authentication, authenticated principals/authorities, user/session eligibility conversion, persistent keys, refresh rotation/reuse response, logout/revocation, security events, or abuse controls implemented.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=LocalAccessTokenDecoderTest,AccessTokenPropertiesTest,AccessTokenIssuanceServiceTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required.

## Completion record

Fill this before marking PR-014 complete.

### Starting commit

- `7bb7c40` (`authenticate opaque refresh sessions`).

### Accepted commit

- `4621473` (`decode local access tokens`).

### Implemented

- Added one production `JwtDecoder` built from the public half of the existing singleton startup-local RSA `KeyPair`, restricted it to RS256, disabled Nimbus's generic type policy, and installed exactly one repository-owned validator using the existing access-token properties and application clock.
- Added the package-local final `LocalAccessTokenValidator` for the exact configured `alg`/`kid`/`typ`, issuer, singleton audience, canonical UUID identifiers, raw whole-second NumericDate representation, equal `iat`/`nbf`, no-skew clock boundaries, and positive configured-lifetime-capped validity window.
- Standardized every repository-owned envelope rejection on one constant safe `invalid_token` result without token, header, claim, identifier, expected-value, or reason-specific detail.
- Added focused real-encoder/decoder coverage for the valid production envelope, one-key/encoder/decoder configuration, unrelated signatures, RS512, the complete header/claim/time failure matrix, exact expiry/not-before boundaries, fractional observed time, one clock observation, and safe stable validation errors.
- Updated the existing access-token configuration test only to provide the required `Clock` and assert the single decoder bean.

### Deviations from specification

- None.

### New decisions

- None. Raw NumericDate JSON is inspected only because Spring Security normalizes decoded NumericDate values to `Instant`; this preserves the already specified whole-second envelope rather than introducing a new contract.

### Tests executed

- `./mvnw spotless:check` - passed.
- `./mvnw -Dtest=LocalAccessTokenDecoderTest,AccessTokenPropertiesTest,AccessTokenIssuanceServiceTest test` - passed, 10 tests with PostgreSQL/Testcontainers coverage.
- `./mvnw test` - passed, 88 tests.
- `./mvnw verify` - passed, 88 tests and package verification.
- `git diff --check` - passed.

### Follow-up work

- Authenticated-principal conversion with current user/session eligibility checks, an HTTP bearer boundary/filter chain, stable signing-key infrastructure, refresh rotation/reuse response, logout/revocation, security events, and abuse controls remain separate future units.
