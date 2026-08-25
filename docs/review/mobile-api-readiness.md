# Expo / React Native API readiness

## Summary

Do not create a separate mobile backend. Stabilize the Spring API as a versioned, client-neutral contract and let web and Expo share generated types/domain conventions where practical.

The main work before mobile is not React Native UI. It is making financial mutations retry-safe, auth device-aware, decimals exact, lists bounded, errors stable, and offline synchronization possible. Mobile releases remain installed after the backend changes, so compatibility matters more than it does for the deploy-in-lockstep web client.

## Contract changes to make before the first mobile release

### 1. Version the API

- Introduce `/api/v1/...` for the supported external contract.
- Keep controller classes or modules versioned by contract, not names such as `TradeControllerV2` behind an unversioned URL.
- Define additive-compatible changes, deprecation headers, removal windows, and the oldest supported app version.
- Return an explicit upgrade-required response only when there is a real safety/compatibility reason.

### 2. Make IDs opaque strings

Use UUID/ULID or string-serialized database IDs consistently in paths and bodies. Do not alternate between `Long`, JSON number, and DTO string. This prevents JavaScript safe-integer issues and allows internal schema changes.

### 3. Serialize decimals exactly

Return money, price, quantity, rates, FX, and percentages as decimal strings:

```json
{
  "quantity": "12.50000000",
  "unitPrice": { "amount": "147.35", "currency": "TRY" },
  "fee": { "amount": "4.99", "currency": "TRY" }
}
```

Use a shared decimal library/conversion layer in web and mobile. Format only at the display edge. Do not let each screen recompute accounting totals with JavaScript `number`.

### 4. Standardize time semantics

- Use ISO-8601 instants for actual timestamps.
- Preserve market/local date and timezone where an event is legally/economically date-based.
- Distinguish `tradeAt`, `settlementDate`, `recordedAt`, and valuation `asOf`.
- Return server time in session/sync responses to help diagnose clock skew.

### 5. Add stable problem responses

Use RFC 9457-style problem details with a product error code and correlation ID:

```json
{
  "type": "https://api.example/problems/insufficient-quantity",
  "title": "Insufficient quantity",
  "status": 409,
  "code": "LEDGER_INSUFFICIENT_QUANTITY_AT_DATE",
  "detail": "The sale would make the position negative on 2025-02-14.",
  "correlationId": "...",
  "fieldErrors": []
}
```

Clients should branch on `code`, never parse English messages. Do not expose Java exception names/messages.

### 6. Paginate ordinary collections only when needed

Return naturally small bounded collections without pagination. When activities, positions, instruments/search results, alerts, or another ordinary collection needs pagination, use the standard Spring `Pageable` contract:

- page and size with a safe maximum;
- an explicitly allowed sort and stable ordering;
- server filters for portfolio/account, instrument, activity type, date, tag, and search text;
- prefer `Slice` when the client needs only bounded results and `hasNext`;
- use `Page` only when totals or total pages have demonstrated product value.

Do not create a custom pagination abstraction around Spring pagination. Custom cursor/keyset pagination is opt-in only for a documented product or measured performance requirement; a changing feed is not by itself a reason to prebuild cursor infrastructure.

### 7. Make financial writes idempotent

Every create/import/correction request should include a client-generated event ID or `Idempotency-Key`. Scope it to user + operation, store a request fingerprint and result, and reject reuse with a different payload.

This protects against:

- the app retrying after a timeout;
- reconnect after offline use;
- the user double-tapping;
- OS background/resume behavior;
- import job redelivery.

### 8. Add optimistic concurrency where users edit resources

Return a version/ETag for portfolios, scenarios, preferences, and mutable metadata. Require `If-Match` or a version in updates so one device does not silently overwrite another.

Financial corrections should append a correction activity and replay, not PATCH materialized position totals.

## Authentication for web and mobile

### Current mismatch

The web flow uses an HTTP-only cross-site refresh cookie and a local-storage access token. Expo/mobile cookie behavior, secure storage, deep linking, and multiple devices require a first-class session model rather than emulating a browser cookie jar.

### Recommended session model

- Short-lived access JWT with explicit `typ=access`, audience, issuer, session/device ID, and key ID.
- High-entropy opaque refresh token, or a strictly typed refresh token, stored hashed as a server-side session.
- One refresh session per device installation/login, with display name, created/last-used time, approximate location/security metadata, and revocation state.
- Refresh rotation on every use with reuse detection; revoke the token family if an old token reappears.
- Endpoints to list/revoke sessions and “log out this device/all devices.”
- Reject disabled/locked/deleted users during access-token authentication, not only password login.

Web can receive the refresh token in a `Secure`, `HttpOnly`, appropriate `SameSite` cookie. Mobile can receive it in an authenticated response and keep it in Expo SecureStore/OS keychain. Keep the access token in memory where possible; persist only what is required for background behavior.

### Google login

Use an authorization-code + PKCE/OIDC flow suitable for installed apps (for example through Expo AuthSession and platform credentials). The backend must validate issuer, signature, expiration, nonce where used, verified email, and the exact platform client ID/audience. Web, iOS, and Android may have different allowed client IDs.

Do not accept any Google-signed ID token merely because the email claim exists.

### Abuse and recovery

- Rate-limit login, refresh, registration, import, and expensive analysis endpoints.
- Record auth/security events and notify on suspicious refresh reuse.
- Add verified-email/password-reset flows before mobile distribution.
- Define behavior for lost devices and account deletion.

## Offline-first entry and synchronization

Users will want to record a transaction with weak/no connectivity. A minimal sync protocol is more reliable than caching arbitrary REST responses.

### Client outbox

The mobile app stores pending commands locally:

- client event ID;
- command type/payload/schema version;
- device-created time;
- send attempt/status and last problem.

On reconnect it posts them idempotently. The server returns the authoritative activity plus whether projections are current or rebuilding.

### Server change feed

Offer a user-scoped incremental feed with a feature-local synchronization continuation token:

`GET /api/v1/sync/changes?continuationToken=...`

It should include changed/deleted entities with an opaque continuation token/order, entity version, and enough type information to update local caches. This token belongs to the synchronization protocol and does not establish cursor pagination for ordinary collection endpoints. Use tombstones for deletion/archival. Authorize every feed row by the current user/household scope.

Do not expose database transaction IDs or assume `updatedAt` alone is a safe continuation position.

### Conflict behavior

- Creating an immutable activity with a new client ID is naturally mergeable.
- Duplicate client IDs return the prior result.
- Metadata edits use versions and return `409` with current server state on conflict.
- A backdated activity may be accepted but should return that affected projections are recalculating.
- Offline sell validation can only be provisional; the server remains authoritative and returns a precise date-specific error.

## Suggested API resource shape

Prefer business terms over UI terms:

- `/sessions`, `/devices`.
- `/accounts`, `/portfolios` (reporting groups).
- `/activities` and `/activity-imports`.
- `/positions` as read-only projections.
- `/valuations` and `/performance`.
- `/instruments/search`, `/prices`, `/fx` where client access is appropriate.
- `/scenarios` and `/scenarios/{id}/runs`.
- `/goals`, `/personal-baskets` later.
- `/sync/changes`.

Example create activity:

```http
POST /api/v1/activities
Idempotency-Key: 01J...
```

```json
{
  "type": "BUY",
  "accountId": "01J...",
  "effectiveAt": "2026-08-04T10:32:00+03:00",
  "trade": {
    "instrumentId": "01J...",
    "quantity": "10",
    "unitPrice": { "amount": "123.45", "currency": "TRY" },
    "fees": [
      { "kind": "BROKER_COMMISSION", "money": { "amount": "4.90", "currency": "TRY" } }
    ]
  },
  "notes": "...",
  "tags": ["long-term"]
}
```

Return `201` for a new activity and the same logical result for a safe idempotent replay.

## Read models for mobile

Avoid making the mobile home screen call many granular endpoints. Provide compact read models that still expose methodology:

`GET /api/v1/home?reportingCurrency=TRY&asOf=...`

- net worth/portfolio value and change decomposition;
- data quality/staleness;
- a few allocation/goal highlights;
- recent activities;
- alerts;
- server calculation version and update time.

Use conditional requests/ETags and sensible cache controls. Do not combine unrelated write operations in a mobile-specific backend; this is a read projection only.

## Imports and documents on mobile

- Accept standard multipart file objects without generated-type casts.
- Create an asynchronous import resource for larger documents: upload → processing → preview → approve/post.
- Return progress and per-row issues; support app background/resume.
- Enforce content sniffing, size/page/row limits, malware scanning where applicable, and short retention.
- Never post AI-parsed rows directly to the ledger without explicit approval.
- Provide photo/document-picker guidance but keep broker credentials out of the app unless using a sanctioned OAuth/open-banking provider.

## Push notifications

When notifications are added, store installations/device tokens separately from auth sessions because tokens rotate independently. Support per-category preferences and quiet hours/timezone.

Good notification candidates:

- import completed and needs review;
- market/FX data for a portfolio is stale;
- dividend/interest/coupon received or expected;
- goal drift or allocation threshold, with user-defined rules;
- security/session alert.

Avoid high-frequency price nudges that encourage compulsive trading unless explicitly requested.

## OpenAPI and client generation

The current web schema is generated manually from a running server. Before a second client:

- generate/publish OpenAPI deterministically in CI;
- lint it and compare against the last released contract for breaking changes;
- version the generated TypeScript package shared by web/mobile;
- add representative request/response examples and error schemas;
- give every operation a stable `operationId`;
- avoid generating directly from JPA entities;
- test multipart and decimal-string mappings.

The server remains authoritative. Generated types should reduce drift, not make current implementation details permanent.

## Mobile-readiness exit criteria

Before public mobile work goes beyond a prototype:

- [ ] API v1 and compatibility policy exist.
- [ ] All external IDs are opaque strings.
- [ ] Financial decimals are strings with shared parsing/formatting.
- [ ] Activity writes are idempotent and concurrency-tested.
- [ ] Device refresh sessions rotate and can be revoked.
- [ ] Google login validates platform-specific audience/issuer.
- [ ] Core list pagination follows the bounded-collection/`Pageable`/`Slice`/`Page` hierarchy and remains filterable.
- [ ] Stable problem codes and correlation IDs are documented.
- [ ] A sync continuation-token/change-feed and client-outbox behavior are tested.
- [ ] Valuation responses expose as-of time and data quality.
- [ ] OpenAPI compatibility checks run in CI.
- [ ] Account export/deletion and privacy behavior work from mobile.
- [ ] An offline/retry test proves that one user action creates one ledger activity.

