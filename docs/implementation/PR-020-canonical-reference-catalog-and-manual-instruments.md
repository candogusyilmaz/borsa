# PR-020 — Canonical reference catalogue and owner-scoped manual instruments

Status: **IMPLEMENTATION COMPLETE — awaiting user commit decision**

## Goal

Deliver the first non-authentication product capability: a canonical offline reference catalogue for countries, currencies, markets, market currencies, instruments, aliases, and explicit market-calendar coverage. An authenticated user can browse stable seeded currencies/markets, search the global-plus-personal instrument catalogue, and create or maintain owner-scoped manual instruments without any network provider. The result becomes the authoritative currency/instrument identity foundation required by later financial-account, ledger, investing, observation, and valuation slices.

This PR intentionally moves feature development beyond authentication. It uses only the typed authenticated user identity expected from PR-019 for owner scoping; durable jobs, roles/admin permissions, OIDC, and remaining auth hardening are not prerequisites.

## Sizing and boundary rationale

- **Fixed comparison baseline:** accepted PR-018 (`d1eea9a`) added 381 and removed 30 production Java lines across 12 production files, excluding tests and documentation.
- **Required floor:** target at least five times PR-018's substantive production implementation surface. With comparable density, PR-020 is expected to add approximately **2,000–3,200 production lines** across roughly **35–50 new or materially changed production files**, plus one substantial Flyway migration. Tests and documentation do not count toward the floor.
- **Intentionally combined steps:** schema and deterministic seeds are unusable without mappings; mappings are mechanical without catalogue queries; search is not demonstrable without manual instrument creation; owner-scoped commands require the same visibility rules as reads; calendar rows must expose explicit coverage rather than imply schedules from time zones. These layers form one reference-catalogue capability.
- **Review boundary:** one reviewer can focus on reference identity, ownership/visibility, normalization, deterministic seeding, search/pagination, and no-network truthfulness. Financial accounts/ledger, observations/prices/FX, provider ingestion, global administration, and auth expansion remain independent later capabilities.
- **No padding:** if the complete specified implementation is materially below the fixed floor, stop and report the sizing conflict rather than adding unrelated frameworks, provider stubs, financial behavior, or speculative metadata.

## Source documents

- `docs/review/backend-master-plan.md` — R2 canonical reference model and deterministic seeds; target PostgreSQL organization; seed and provider-independent policies
- `docs/review/accounting-contract.md` — sections 2, 3, 11, 12, 19, and 21 for exact identifiers, currency/FX separation, provenance, and API boundaries
- `docs/review/business-logic-and-analytics-design.md` — Reference and market-data model; instrument identity and valuation-method guidance
- `docs/review/backend-audit.md` — DB-005, DB-006, and legacy latest-rate/snapshot defects
- `docs/review/mobile-api-readiness.md` — cursor pagination and stable API identifiers
- `docs/engineering/coding-standards.md`
- `docs/implementation/PR-002-v1-foundation-database.md` — accepted schemas and Flyway ownership
- `docs/implementation/PR-019-authenticated-identity-and-session-security-lifecycle.md` — typed authenticated identity used only for owner scoping

## Starting state

This specification is activated after PR-019 acceptance. The accepted PR-019 starting commit is recorded in the Completion Record below.

Activation prerequisites:

1. PR-019 implementation and review are complete with no unresolved `MUST FIX` findings.
2. The user has accepted PR-019 in a commit and that exact commit is recorded below and in `STATE.md`.
3. The typed authenticated identity boundary is available for protected controllers to obtain the current `userAccountId` without accepting one from the client.
4. `CURRENT.md` has been explicitly moved from PR-019 to this specification by the supervising transition action.
5. The implementation agent re-inspects the accepted PR-019 production names and updates only stale class-name references in this draft before changing production code.

Expected accepted starting behavior:

- V1 remains the latest migration and the `reference` schema is empty;
- `/api/v1/**` remains bearer-authenticated except the exact registration/login/refresh POSTs;
- an authenticated controller can resolve the current user UUID through PR-019's accepted typed boundary;
- no role, permission, administrator, household, global-reference mutation, reference entity, provider, observation, price, or FX-rate behavior exists;
- core tests require no network.

Accepted starting commit: **`0c6657e`**, the accepted PR-019 working-tree commit.

## Scope

### User-authorized cross-cutting standards alignment

On 2026-08-16 the supervising user explicitly authorized retaining the standards-alignment work already mixed into this review unit. That authority includes behavior-preserving identity/session refactors, per-key atomic abuse-protection concurrency, correct refresh-family event provenance, response factories, repository-query cleanup, shared cache/error helpers, and MDC trace correlation. These changes are part of PR-020's reviewed diff and must not be removed merely because the reference capability is the primary product slice.

This exception does not authorize a new authentication endpoint, credential flow, throttle limit, session wire contract, schema change, role/permission model, or public route. Existing PR-019 behavior remains contract-compatible and is covered by the full identity/security suite. The reference capability alone still has to satisfy the fixed sizing floor so cross-cutting cleanup cannot be used as padding.

### 1. Add the coherent V2 reference schema

Create exactly one migration:

```text
V2__reference_catalog.sql
```

It creates these tables in the existing `reference` schema:

```text
reference.country
reference.currency
reference.market
reference.market_currency
reference.instrument
reference.instrument_alias
reference.market_calendar
```

Flyway owns every key, constraint, index, default, delete action, and comment. Application code generates UUIDs for user-created rows. Do not create PostgreSQL enums, extensions, functions, triggers, provider tables, observation tables, price/FX columns, or latest-snapshot tables.

#### `reference.country`

Columns:

```text
code          text primary key
name          text not null
active        boolean not null default true
created_at    timestamptz not null
```

Rules:

- `code` is the uppercase ISO-3166 alpha-2 identity and matches `[A-Z]{2}`;
- `name` is trimmed and nonblank;
- this PR seeds only `TR`, `GB`, and `US`;
- no numeric surrogate ID, mutable localization table, flag/icon URL, or geopolitical hierarchy.

#### `reference.currency`

Columns:

```text
code          text primary key
name          text not null
symbol        text not null
minor_unit    smallint not null
active        boolean not null default true
created_at    timestamptz not null
```

Rules:

- `code` is the uppercase three-letter identity and matches `[A-Z]{3}`;
- `name` is trimmed/nonblank; `symbol` is nonblank and is display metadata only;
- `minor_unit` is between 0 and 18 and never authorizes calculation/storage rounding by itself;
- seed exactly `TRY`, `USD`, `EUR`, and `GBP`, each with minor unit 2;
- do not store an exchange rate, latest rate timestamp, reporting conversion, balance, numeric surrogate ID, or provider metadata.

#### `reference.market`

Columns:

```text
id                 uuid primary key
code               text not null
code_normalized    text not null
name               text not null
market_type        text not null
country_code       text null
time_zone          text not null
active             boolean not null default true
source_kind        text not null
created_at         timestamptz not null
updated_at         timestamptz not null
```

Rules:

- market IDs are stable hard-coded UUIDs in the migration; later code never relies on insertion order or numeric IDs;
- `code_normalized` is trimmed uppercase `Locale.ROOT`, nonblank, and unique;
- `name`, `market_type`, `time_zone`, and `source_kind` are trimmed/nonblank;
- nullable `country_code` references `reference.country(code)` with `ON DELETE RESTRICT`;
- `time_zone` stores an IANA zone ID and application mapping validates it with `ZoneId.of`;
- seed exactly:
  - `XIST` / `Borsa Istanbul` / type `EXCHANGE` / country `TR` / zone `Europe/Istanbul`;
  - `MANUAL` / `Manual or unlisted market` / type `MANUAL` / no country / zone `UTC`;
- both seeds use source kind `REFERENCE_SEED`;
- no official trading hours or holiday claim is seeded.

The migration must use these stable UUIDs so later fixtures can reference codes or UUIDs deterministically without generated numeric IDs:

```text
XIST:   10000000-0000-0000-0000-000000000001
MANUAL: 10000000-0000-0000-0000-000000000002
```

#### `reference.market_currency`

Columns:

```text
market_id       uuid not null
currency_code   text not null
primary_quote   boolean not null default false
```

Rules:

- primary key `(market_id, currency_code)`;
- FKs to market and currency use `ON DELETE CASCADE` and `ON DELETE RESTRICT` respectively;
- one partial unique index permits at most one `primary_quote = true` row per market;
- seed `XIST/TRY` as primary;
- seed `MANUAL/TRY` as primary and `MANUAL/USD`, `MANUAL/EUR`, and `MANUAL/GBP` as non-primary;
- no rate, price, spread, conversion direction, or mutable latest value.

#### `reference.instrument`

Columns:

```text
id                       uuid primary key
owner_user_account_id    uuid null
market_id                uuid not null
symbol                   text not null
symbol_normalized        text not null
name                     text not null
name_normalized          text not null
instrument_type          text not null
quotation_currency_code  text not null
valuation_method         text not null
active                   boolean not null default true
source_kind              text not null
version                  bigint not null default 0
created_at               timestamptz not null
updated_at               timestamptz not null
```

Rules:

- nullable owner FK references `identity.user_account(id)` with `ON DELETE CASCADE`;
- market FK uses `ON DELETE RESTRICT`;
- quotation currency must be supported by the selected market through a composite FK to `(market_id, currency_code)` in `market_currency`;
- `symbol` is trimmed, 1–32 characters, and matches `[A-Za-z0-9][A-Za-z0-9._:/+-]*`; `symbol_normalized` is its application-computed uppercase `Locale.ROOT` form;
- `name` is trimmed and 1–160 characters; `name_normalized` is its application-computed uppercase `Locale.ROOT` form;
- the migration checks display and normalized columns for canonical trimming/nonblank bounds, and checks ASCII symbol normalization exactly; it must not pretend PostgreSQL collation-aware `upper(...)` proves Java `Locale.ROOT` normalization for arbitrary Unicode names;
- `instrument_type`, `valuation_method`, and `source_kind` are nonblank application codes; do not constrain their values with a PostgreSQL enum/check list;
- ownership/source consistency is database-enforced with implications rather than a closed source enum: non-null owner requires `USER_ENTERED`, `USER_ENTERED` requires non-null owner, and `REFERENCE_SEED` requires null owner; other future nonblank global source kinds remain schema-compatible but no other source kind is accepted by a write workflow in this PR;
- partial unique indexes enforce `(market_id, symbol_normalized)` for global rows and `(owner_user_account_id, market_id, symbol_normalized)` for owner rows;
- query indexes support owner/global visibility, active filtering, normalized symbol/name prefix search, market filtering, type filtering, and cursor order;
- no price, current snapshot, cost basis, quantity, position, issuer fundamentals, provider payload, or subtype table.

This PR seeds no instrument rows. Manual instruments are created only through the application command below.

#### `reference.instrument_alias`

Columns:

```text
id                uuid primary key
instrument_id     uuid not null
alias_type        text not null
alias_value       text not null
alias_normalized  text not null
created_at        timestamptz not null
```

Rules:

- instrument FK uses `ON DELETE CASCADE`;
- type/value/normalized value are trimmed/nonblank; alias values are 1–128 characters and normalization is uppercase `Locale.ROOT`;
- unique `(instrument_id, alias_type, alias_normalized)`;
- indexes support exact and prefix normalized alias lookup;
- alias values are identity/search metadata, not provider observations;
- cross-instrument aliases may collide and therefore search can return multiple visible candidates rather than silently choosing one.

#### `reference.market_calendar`

Columns:

```text
market_id       uuid not null
calendar_date   date not null
session_status  text not null
opens_at        time null
closes_at       time null
source_kind     text not null
created_at      timestamptz not null
```

Rules:

- primary key `(market_id, calendar_date)`;
- market FK uses `ON DELETE CASCADE`;
- status is migration-checked to `OPEN` or `CLOSED` because the two-state row contract is fixed for this slice;
- `OPEN` requires both local opening/closing times and `closes_at > opens_at`;
- `CLOSED` requires both times null;
- source kind is trimmed/nonblank;
- dates and local times are interpreted only with the parent market's IANA zone;
- no recurring weekday assumption, inferred weekend, generated holiday, future market-hours promise, or seed row.

One row is one explicit known market date. A missing row means unknown coverage, not automatically open or closed.

### 2. Add minimal reference entities and value objects

Map all seven tables with Hibernate validation and the repository conventions in `coding-standards.md`:

- `Country` and `Currency` use their stable codes as IDs;
- `Market` and `Instrument` are UUID entities;
- `MarketCurrency` uses one explicit composite key/embeddable only where it makes the mapping clearer;
- `InstrumentAlias` is a separate entity and is not exposed as a mutable public collection from `Instrument`;
- `MarketCalendar` uses an explicit composite key for market/date;
- every association is lazy unless a tested query deliberately fetches it;
- `Instrument` uses `@Version` for owner-managed metadata updates;
- entities contain mapping and genuine invariant methods only, with no DDL annotations, public setters, API serialization, or provider behavior.

Add small immutable value objects for:

```text
CountryCode
CurrencyCode
MarketCode
InstrumentSymbol
```

They enforce exact structural normalization and equality but do not perform repository lookups. `CurrencyCode` does not contain a mutable rate or perform conversion. Do not introduce a generic unit/measurement framework or financial decimal serializer before a real amount API needs it.

`CountryCode` and `CurrencyCode` accept only already-canonical uppercase forms matching `[A-Z]{2}` and `[A-Z]{3}`. `MarketCode` accepts only already-canonical uppercase forms matching `[A-Z0-9][A-Z0-9._-]{0,31}`. `InstrumentSymbol` trims the accepted display symbol, retains its casing, and exposes the separately computed `Locale.ROOT` uppercase normalized value. Do not silently repair wrong-case stable country/currency/market identities at the HTTP boundary.

Application values accepted for owner-created instruments are:

```text
instrumentType:
  EQUITY, ETF, FUND, INDEX, BOND, CRYPTO, COMMODITY, CURRENCY,
  CASH_EQUIVALENT, OTHER

valuationMethod:
  MARKET_OBSERVATION, MANUAL_VALUE, NOT_VALUED

aliasType:
  TICKER, ISIN, PROVIDER, USER
```

These are application enums serialized as the exact strings above. They remain text in PostgreSQL so later reviewed types can be added without a database enum migration.

### 3. Expose read-only seeded countries, currencies, markets, and honest calendar coverage

Add authenticated read services and endpoints:

```text
GET /api/v1/reference/countries
GET /api/v1/reference/currencies
GET /api/v1/reference/markets
GET /api/v1/reference/markets/{marketId}/calendar?from=&to=
```

Countries/currencies/markets are bounded read-only seeded lists in this PR and do not need pagination. Ordering is stable by code ascending.

Each of these three endpoints returns a top-level JSON array of the exact response object below; do not add an envelope, paging metadata, or internal normalized fields.

Country response:

```java
String code
String name
boolean active
```

Currency response:

```java
String code
String name
String symbol
int minorUnit
boolean active
```

Market response:

```java
UUID id
String code
String name
String marketType
String countryCode
String timeZone
List<String> quotationCurrencies
String primaryQuotationCurrency
boolean active
String sourceKind
```

`countryCode` and `primaryQuotationCurrency` are nullable only where the database permits them. Currency lists are immutable and sorted by code.

Calendar query rules:

- `from` and `to` are required inclusive `LocalDate` values;
- `from <= to` and the range is at most 366 days;
- the response returns explicit stored rows ordered by date plus every missing date in the requested range;
- `coverageStatus` is `NONE` when no requested date has a row, `COMPLETE` when every date has a row, and `PARTIAL` otherwise;
- open/close values are returned as local times with the market `timeZone`; do not convert them to an `Instant` without a requested date/zone calculation;
- a missing/cross-invalid market ID uses parameterless `ReferenceErrorCode.MARKET_NOT_FOUND` with HTTP 404;
- an empty BIST calendar therefore honestly returns `NONE`, not a fabricated weekday schedule.

Calendar response contains exactly:

```java
UUID marketId
String marketCode
String timeZone
LocalDate from
LocalDate to
CalendarCoverageStatus coverageStatus
List<MarketCalendarSessionResponse> sessions
List<LocalDate> missingDates
```

Each session contains exactly `LocalDate date`, `MarketSessionStatus sessionStatus`, nullable `LocalTime opensAt`, nullable `LocalTime closesAt`, and `String sourceKind`. `CalendarCoverageStatus` serializes only `NONE`, `PARTIAL`, or `COMPLETE`; `MarketSessionStatus` serializes only `OPEN` or `CLOSED`. Sessions and missing dates are each ascending by date. OPEN session times are non-null, CLOSED session times are null.

The read endpoints require the existing bearer chain. Do not make them public, add a new filter chain, or add roles.

### 4. Add owner-scoped manual instrument creation and maintenance

Expose exactly:

```text
POST /api/v1/reference/instruments
PUT /api/v1/reference/instruments/{instrumentId}
GET /api/v1/reference/instruments/{instrumentId}
```

Creation request contains exactly:

```java
UUID marketId
String symbol
String name
InstrumentType instrumentType
String quotationCurrency
ValuationMethod valuationMethod
List<InstrumentAliasInput> aliases
```

Each alias input contains exactly `AliasType type` and `String value`. Alias input is optional as an empty list, never null, contains at most 32 entries, and must be unique by `(type, normalized value)`. Symbol, name, and alias bounds are the exact bounds defined above.

Creation behavior:

1. resolve the owner only from PR-019's authenticated identity;
2. structurally validate and normalize symbol/name/aliases;
3. require an active market and active quotation currency linked through `market_currency`;
4. generate instrument/alias UUIDs and observe the injected clock once;
5. persist one `USER_ENTERED` owner instrument and its aliases atomically;
6. flush to translate only the named owner/market/symbol and per-instrument alias uniqueness constraints;
7. return HTTP 201 with the complete detail response.

An unknown market is `MARKET_NOT_FOUND`; an unknown currency code is `CURRENCY_NOT_FOUND`; an inactive market or currency is `INACTIVE_REFERENCE`; and an active currency not linked to the selected market is `UNSUPPORTED_MARKET_CURRENCY`. These checks occur before inserting the instrument or aliases.

Update request contains exactly:

```java
long version
String name
ValuationMethod valuationMethod
boolean active
List<InstrumentAliasInput> aliases
```

Update behavior:

- only the owning user can load/update the row; global or cross-owner IDs are indistinguishable `404 INSTRUMENT_NOT_FOUND`;
- primary market, symbol, type, quotation currency, source kind, and owner are immutable after creation;
- name, valuation method, active state, and the exact alias set are mutable;
- aliases are replaced atomically only after the complete normalized set validates;
- optimistic version mismatch returns parameterless `ReferenceErrorCode.INSTRUMENT_VERSION_CONFLICT` with HTTP 409;
- an update observes the injected clock once and updates `updatedAt` with the version;
- no physical delete endpoint is added.

The detail response contains exactly:

```java
UUID id
UUID ownerId
UUID marketId
String marketCode
String symbol
String name
InstrumentType instrumentType
String quotationCurrency
ValuationMethod valuationMethod
boolean active
String sourceKind
long version
Instant createdAt
Instant updatedAt
List<InstrumentAliasResponse> aliases
```

`ownerId` is nullable for future global seed rows. Alias responses contain `UUID id`, `AliasType type`, and `String value`, ordered by `(type, normalized value, id)`.

Detail visibility is an active global row or any row owned by the authenticated user, including the owner's inactive row so it can be reactivated. Unknown IDs, another owner's rows, and inactive global rows are indistinguishable `404 INSTRUMENT_NOT_FOUND` results.

Do not implement shared/global market or instrument administration. Until roles/admin policy is reviewed, authenticated users may mutate only their own manual instruments. The seeded `MANUAL` market supplies an honest location for unlisted/custom instruments, so global market mutation is not required for the next ledger slice.

### 5. Add owner/global instrument search with deterministic cursor pagination

Expose:

```text
GET /api/v1/reference/instruments?query=&marketId=&type=&includeInactive=&limit=&cursor=
```

Visibility and filtering:

- query only global rows (`owner_user_account_id IS NULL`) plus the authenticated user's rows in SQL; never fetch cross-owner rows and filter later;
- owner rows are never visible to another user;
- `includeInactive` defaults to false and may expose only the requesting user's inactive rows plus active global rows; a future admin API owns global inactive behavior;
- `marketId` and `type` are optional exact filters;
- `query` is optional; when present it is trimmed, 1–64 characters, normalized uppercase, and matches normalized symbol, name, or alias by prefix;
- no fuzzy match, unaccent extension, trigram extension, provider request, or full-text search framework.

Stable order is:

```text
symbol_normalized ASC,
market.code_normalized ASC,
instrument.id ASC
```

Pagination:

- default limit 25, accepted range 1–100;
- fetch limit + 1;
- cursor is opaque unpadded Base64url over the following canonical UTF-8 JSON with this exact field order and no insignificant whitespace: `{"v":1,"f":"<filterDigest>","s":"<symbolNormalized>","m":"<marketCodeNormalized>","i":"<lowercase UUID>"}`;
- decode then re-encode to the identical input; reject malformed, noncanonical, unknown-version, wrong-field-count, invalid-UUID, or trailing data;
- `ReferenceErrorCode.INVALID_INSTRUMENT_CURSOR` is parameterless HTTP 400;
- cursor carries order only and never changes owner/global visibility or filters;
- `filterDigest` is lowercase hexadecimal SHA-256 over the canonical UTF-8 filter string `<queryNormalized-or-empty>\n<lowercase-market-UUID-or-empty>\n<type-name-or-empty>\n<true-or-false>`;
- applying a cursor with a different normalized filter set is rejected; recompute and constant-time compare the digest rather than trusting cursor or client filter state.

Use one explicit `JdbcClient` read model for search/detail query shapes where it avoids JPA entity graphs and alias N+1 queries. The search response returns immutable summaries:

```java
UUID id
String symbol
String name
InstrumentType instrumentType
UUID marketId
String marketCode
String quotationCurrency
ValuationMethod valuationMethod
boolean active
String sourceKind
boolean ownerManaged
List<InstrumentAliasResponse> aliases
```

Alias lists are bounded by the 32-alias invariant and sorted deterministically. Search must use a bounded number of SQL statements independent of result count.

### 6. Preserve offline truth and shared API/error behavior

Add parameterless `ReferenceErrorCode` values with these statuses:

```text
MARKET_NOT_FOUND                404
CURRENCY_NOT_FOUND              404
INSTRUMENT_NOT_FOUND            404
DUPLICATE_INSTRUMENT            409
DUPLICATE_INSTRUMENT_ALIAS      409
INSTRUMENT_VERSION_CONFLICT     409
INACTIVE_REFERENCE              422
UNSUPPORTED_MARKET_CURRENCY     422
INVALID_INSTRUMENT_CURSOR       400
```

All errors use the existing `AppException`/global RFC 9457 boundary. Do not expose owner existence, normalized internal values, constraint names, SQL, stack traces, or exception messages.

Every endpoint:

- remains under the one accepted bearer security chain;
- resolves the owner once where owner scope is needed;
- returns `Cache-Control: no-store` and `Pragma: no-cache` because owner-managed catalogue data may be present;
- creates no servlet session;
- performs no network access;
- never labels manual/user-entered rows as provider, official, live, priced, or synthetic data.

## Explicit non-goals

- No new authentication/session product capability beyond the explicitly authorized behavior-preserving standards alignment above; no durable job worker, key management, OIDC, password recovery, role, permission, authority, or admin framework.
- No global/shared market or instrument mutation API; no country/currency/market mutation endpoint.
- No official market-hours/holiday seed, inferred weekday calendar, calendar import, or calendar mutation API.
- No price, FX, rate, CPI, corporate-action, fundamental, latest snapshot, observation, dataset, source-selection, provider, HTTP adapter, cache, or licence workflow.
- No financial account, cash pocket, ledger activity/posting, balance, portfolio, position, trade, import, valuation, performance, scenario, or financial decimal API.
- No instrument subtype tables, polymorphic entity hierarchy, generic metadata JSON, security master, MIC/ISIN provider database, or hardcoded live instrument seed.
- No fuzzy/full-text/trigram search dependency or PostgreSQL extension.
- No public/anonymous reference endpoints and no second `SecurityFilterChain`.
- No frontend change.
- No Git operation and no reversal of user or in-progress PR-019 changes.

## Database changes

Migration:

```text
src/main/resources/db/migration/V2__reference_catalog.sql
```

Created tables:

- `reference.country`
- `reference.currency`
- `reference.market`
- `reference.market_currency`
- `reference.instrument`
- `reference.instrument_alias`
- `reference.market_calendar`

Seeded rows:

- countries `TR`, `GB`, `US`;
- currencies `TRY`, `USD`, `EUR`, `GBP`;
- markets `XIST`, `MANUAL` with exact stable UUIDs;
- market-currency rows specified above;
- no instrument or calendar rows.

The migration must be empty-to-latest safe and preserve V1 unchanged. Hibernate remains `ddl-auto: validate`.

## Application changes

Expected production surface is approximately:

```text
src/main/java/dev/canverse/stocks/reference/
├── application/
│   ├── ReferenceCatalogQueryService.java
│   ├── ManualInstrumentService.java
│   ├── InstrumentSearchService.java
│   ├── InstrumentSearchCursor.java
│   └── small immutable query/result records
├── domain/
│   ├── Country.java
│   ├── Currency.java
│   ├── Market.java
│   ├── MarketCurrency.java
│   ├── Instrument.java
│   ├── InstrumentAlias.java
│   ├── MarketCalendar.java
│   ├── CountryCode.java
│   ├── CurrencyCode.java
│   ├── MarketCode.java
│   ├── InstrumentSymbol.java
│   └── application-code enums
├── error/
│   └── ReferenceErrorCode.java
├── infrastructure/
│   ├── mapping repositories needed for writes/validation
│   └── ReferenceCatalogReadRepository.java       # JdbcClient reads/search
├── input/
│   ├── ManualInstrumentCreateRequest.java
│   ├── ManualInstrumentUpdateRequest.java
│   └── InstrumentAliasInput.java
├── output/
│   ├── CountryResponse.java
│   ├── CurrencyResponse.java
│   ├── MarketResponse.java
│   ├── MarketCalendarResponse.java
│   ├── InstrumentResponse.java
│   ├── InstrumentSummaryResponse.java
│   ├── InstrumentAliasResponse.java
│   └── InstrumentPageResponse.java
└── web/
    ├── ReferenceCatalogController.java
    └── ManualInstrumentController.java
```

Names may remain idiomatic. Omit empty packages and colocate tiny internal records where clearer. Do not add service interfaces, implementation pairs, generic mappers, base repositories/controllers, event buses, or provider ports.

## API contract

### Seeded catalogue reads

```text
GET /api/v1/reference/countries
GET /api/v1/reference/currencies
GET /api/v1/reference/markets
```

Return the exact bounded response fields and stable code ordering defined in Scope. No response contains exchange rates, prices, or claims of current market coverage.

### Market calendar coverage

```text
GET /api/v1/reference/markets/{marketId}/calendar?from=2026-08-01&to=2026-08-31
```

Example with no stored official data:

```json
{
  "marketId": "10000000-0000-0000-0000-000000000001",
  "marketCode": "XIST",
  "timeZone": "Europe/Istanbul",
  "from": "2026-08-01",
  "to": "2026-08-31",
  "coverageStatus": "NONE",
  "sessions": [],
  "missingDates": ["2026-08-01", "..."]
}
```

The actual array contains each missing date, not the illustrative ellipsis.

### Create manual instrument

```text
POST /api/v1/reference/instruments
```

```json
{
  "marketId": "10000000-0000-0000-0000-000000000002",
  "symbol": "MY-FUND",
  "name": "My manually valued fund",
  "instrumentType": "FUND",
  "quotationCurrency": "GBP",
  "valuationMethod": "MANUAL_VALUE",
  "aliases": [
    {"type": "USER", "value": "Pension fund"}
  ]
}
```

Success is HTTP 201 with the exact detail response. The server owns `id`, owner, source, timestamps, active default, and version 0.

### Update manual instrument

```text
PUT /api/v1/reference/instruments/{instrumentId}
```

The request supplies exact current `version` and complete replacement metadata/alias set. Success is HTTP 200 with incremented version. Missing/cross-owner/global IDs are the same 404.

### Search instruments

```text
GET /api/v1/reference/instruments?query=MY&marketId=&type=FUND&includeInactive=false&limit=25&cursor=
```

Success:

```json
{
  "instruments": [],
  "nextCursor": null
}
```

Only active global rows and the authenticated user's visible rows can appear under the exact rules above.

## Business invariants

- Stable country/currency codes are identities; markets/instruments use UUIDs and never generated numeric seed IDs.
- Currency reference data contains no mutable exchange rate or historical conversion behavior.
- Instrument identity is scoped by market and owner where manual, never by ticker alone.
- A manual instrument's quotation currency must be active and supported by its selected active market.
- Global seed rows and owner-entered rows remain distinguishable in database, application, and API output.
- Owner-entered instruments are visible/mutable only to their owner; global rows are read-only in this PR.
- Normalized symbol/name/alias values are deterministic under `Locale.ROOT`; display values retain accepted user casing.
- Alias collisions across instruments return candidates and never silently select one identity.
- Market time zone is identity metadata; calendar rows are explicit local-date evidence, and absence remains missing coverage.
- Search order and cursors are deterministic, filter-bound, owner-safe, and independent of insertion order.
- Manual data works without networking and is never presented as provider/live/official pricing.
- No reference command creates a financial fact, balance, position, observation, valuation, or scenario.

## Required tests

### Pure/domain

- country/currency/market/symbol normalization accepts exact valid forms and rejects blank, whitespace-padded, wrong-case/length, and locale-sensitive edge cases;
- application enums serialize/parse only exact accepted codes and unknown values retain the shared malformed-request contract;
- `ZoneId` validation accepts `UTC`/`Europe/Istanbul` and rejects invalid IDs without relying on machine default zone;
- instrument construction requires all immutable identity fields, starts active/version 0, uses `USER_ENTERED`, and never accepts an owner from the request;
- instrument update changes only name/valuation/active/aliases, increments through JPA versioning, and leaves immutable identity unchanged;
- alias normalization/set validation covers duplicates, bounds, deterministic order, and immutability;
- calendar coverage derives exact `NONE`, `PARTIAL`, and `COMPLETE` results including date-range equality and maximum bound;
- cursor round-trip/re-encoding covers filters, symbols, UUIDs, noncanonical/trailing/unknown-version input, and filter mismatch.

### PostgreSQL/Testcontainers

1. **Empty V1-to-V2 migration and seeds**
   - all seven tables, exact columns, named constraints, indexes, FK/delete actions, comments, and seed rows exist;
   - exact stable market UUIDs and code-based relationships are proven;
   - no instrument/calendar seed, rate/snapshot column, enum, extension, trigger, function, or numeric reference ID exists;
   - Hibernate validates every mapping.

2. **Database integrity**
   - invalid country/currency formats, minor units, blank values, unknown market/currency/user FKs, unsupported market currency, source/owner mismatch, duplicate global/owner symbol, duplicate alias, invalid calendar status/time shape, and second primary market currency are rejected;
   - deleting a user cascades only that user's manual instruments/aliases;
   - stable currency/market deletes are restricted where referenced.

3. **Manual instrument transaction behavior**
   - create persists one exact owner row plus normalized aliases using application IDs/clock;
   - inactive/unknown market/currency and unsupported quotation currency write nothing;
   - named duplicate constraints map to exact safe conflicts while unknown integrity failures remain generic;
   - complete alias replacement is atomic and a forced failure preserves the previous instrument/version/alias set;
   - optimistic concurrent updates produce one commit and one stable conflict with no lost update.

4. **Owner/global reads and search**
   - two users plus global fixtures prove SQL-level visibility and cross-owner detail/update non-leakage;
   - symbol/name/alias prefix matching, market/type/active filters, stable order, limit+one cursor pages, filter binding, and no gaps/duplicates are exact;
   - alias loading uses bounded query count independent of result count;
   - inactive owner rows appear only when requested and cross-owner rows never appear.

5. **Calendar honesty**
   - empty, partial, and complete explicit date sets return exact missing dates and local times in parent market zone;
   - no weekday/weekend inference occurs;
   - invalid/reversed/overlong ranges stop before repository work.

### HTTP/security

Use `@SpringBootTest`, default-filter MockMvc, migrated PostgreSQL, accepted real bearer authentication, explicit cleanup, and no test transaction.

Cover at least:

1. exact country/currency/market seed responses, stable ordering, no-cache headers, and absence of rates/prices;
2. calendar `NONE`/partial/complete contracts, validation, missing market, and no fabricated schedule;
3. authenticated manual instrument create/detail/update with exact owner/source/version/timestamp/alias behavior;
4. another user's bearer token receives the same 404 for detail/update as an unknown UUID and cannot observe the row in search;
5. search query/filter/cursor pagination and alias results use exact stable JSON and no leakage;
6. duplicate/inactive/unsupported/version/cursor/validation/malformed failures retain trace-correlated RFC 9457 shapes and write no partial state;
7. missing/invalid/revoked bearer credentials receive the accepted 401; no new route is public and exactly one chain exists;
8. no endpoint creates a servlet session, calls a network collaborator, returns a provider/live/synthetic claim, or exposes JPA entities/internal normalized columns.

## Acceptance criteria

1. PR-020 is activated only after PR-019 acceptance; `CURRENT.md` points to PR-020 throughout implementation and review.
2. The exact accepted PR-019 commit (`0c6657e`) is recorded and stale typed-identity names are reconciled without broadening scope.
3. One `V2__reference_catalog.sql` creates exactly the seven specified tables and deterministic seeds; V1 is unchanged.
4. Country/currency stable codes, market UUIDs, FKs, checks, partial uniqueness, indexes, source/owner rules, and calendar time-shape constraints are migration-owned and green.
5. No mutable exchange rate, snapshot, price, quantity, balance, provider payload, PostgreSQL enum, extension, function, or trigger is introduced.
6. Hibernate validates minimal mappings with no entity-owned DDL/index/constraint definitions.
7. Country/currency/market/symbol value objects and application enums enforce exact deterministic normalization without a generic unit framework.
8. Seeded reads return exact bounded offline reference facts and no current-price/rate/calendar claim.
9. Calendar output distinguishes NONE/PARTIAL/COMPLETE from explicit rows and never infers a missing date.
10. Manual instrument creation derives owner from authenticated identity, validates active market/currency support, writes owner/source/aliases atomically, and returns exact 201 detail.
11. Manual updates are owner-only, optimistic, atomic, and cannot change immutable instrument identity or global rows.
12. Missing/global/cross-owner mutation targets are indistinguishable safe 404 outcomes.
13. Search combines only global plus current-owner rows in SQL, supports exact prefix filters, uses deterministic bounded query count, and has no N+1 alias loading.
14. Cursor pagination is canonical, filter-bound, stable, bounded, and proven without gaps/duplicates or ownership leakage.
15. Named duplicate/inactive/unsupported-currency/version/cursor failures map to exact stable reference error codes; unknown persistence/runtime failures remain safe.
16. Core behavior and tests require no internet/provider, and manual data is never labeled provider/live/official/synthetic.
17. Existing auth/login/refresh/session external behavior remains contract-compatible; the explicitly authorized internal standards alignment is reviewed in this unit, and no role/admin/job/auth capability is quietly added.
18. Delivered substantive production surface meets the fixed five-times PR-018 floor without tests/docs, formatting churn, unrelated work, or padding.
19. Focused pure, migration/mapping, PostgreSQL ownership/search/concurrency, real-filter HTTP/security, full-suite, Spotless, and Maven verify gates pass with no skipped/disabled container/security tests.
20. Completion Record, `STATE.md`, and `progress-report.md` accurately record V2/reference capabilities and defer global administration, calendars/imports, observations/providers, ledger, jobs, and further auth.
21. `git diff --check` passes and agents perform no Git mutation.

## Documentation completion

Before this implementation unit is considered complete:

- retain the accepted starting commit `0c6657e` in this specification's Completion Record and `STATE.md`;
- fill in this specification's Completion Record, including actual production-file/line surface compared with PR-018;
- update `docs/implementation/STATE.md` with migration V2, tables/seeds, visible APIs, ownership/search/cursor decisions, and deferred reference/provider work;
- update `docs/review/progress-report.md` with implementation, verification, review, no-network, and sizing evidence;
- keep `CURRENT.md` pointing to PR-020 throughout implementation/review and do not advance it during this unit.

## Verification commands

```bash
./mvnw spotless:check
./mvnw -Dtest=ReferenceValueObjectTest,ReferenceCatalogMigrationTest,ReferenceEntityMappingTest,ManualInstrumentServiceTest,ReferenceCatalogQueryTest,ReferenceCatalogHttpTest,ManualInstrumentHttpTest,ApiBearerSecurityHttpTest test
./mvnw test
./mvnw verify

git status --short
git diff --check
```

PowerShell must quote the comma-separated `-Dtest` argument if required. If exact focused class names differ, update the command and Completion Record without weakening migration, mapping, ownership, pagination, no-network, or real-filter proof.

For the sizing gate, compare the eventual implementation with the fixed baseline:

```bash
git diff --numstat d1eea9a -- src/main/java src/main/resources
```

When PR-019 has an accepted commit, use a two-step accounting in the Completion Record so PR-019 code is not incorrectly counted as PR-020 production surface. Tests, docs, generated files, formatting-only churn, and unrelated changes never count.

## Completion record

Fill this before marking PR-020 complete.

### Starting commit

- `0c6657e` — accepted PR-019 working-tree commit.

### Implemented

- `V2__reference_catalog.sql` creates the seven reference tables, deterministic TR/GB/US and TRY/USD/EUR/GBP seeds, XIST/MANUAL markets, explicit market-currency support, integrity constraints, indexes, and no provider/observation/financial structures.
- Country, currency, market, instrument, calendar, alias, and valuation value objects/enums plus minimal Hibernate mappings and repositories are implemented with Flyway-owned DDL and schema validation.
- Authenticated offline country, currency, market, and explicit calendar reads return stable bounded responses with `NONE`/`PARTIAL`/`COMPLETE` coverage, missing dates, local session times, range validation, and no schedule inference.
- Owner-derived manual instrument create/detail/update supports active reference validation, atomic aliases, immutable identity fields, `USER_ENTERED` provenance, timestamps, JPA optimistic versioning, owner-only visibility/mutation, and the named reference error contract.
- Owner/global SQL search supports query, market, type, inactive, alias-prefix, deterministic `(symbol_normalized, market.code_normalized, id)` keyset pagination, canonical filter-bound cursors, bounded two-query alias loading, SQL visibility filtering, and wildcard-safe literal prefixes.
- Required pure, PostgreSQL migration/mapping/service/query, concurrency, real-filter HTTP, cursor/ownership, malformed-input, bearer-security, and authorized abuse-concurrency coverage was added.
- The requested standards alignment adds DTO `validate()` barriers for non-annotation request invariants, named manual-instrument limits, response-record factories, immutable collection boundaries, collector-based market grouping, explicit repository queries without `@Param`, and bulk-alias flush/clear semantics.
- Known unique collisions use the shared platform constraint translator; unknown integrity failures reach the generic logged server-error boundary without database details in the response.

### Sizing evidence

- The requested standards-alignment pass adds three focused production helpers/constants (70 lines by current working-tree count) and refactors existing paths; it does not broaden the PR beyond the standards findings listed above.

- Fixed PR-018 baseline: 381 production additions / 30 deletions / 12 production files.
- Accepted PR-019 starting commit used for the actual PR-020 diff: `0c6657e`.
- The standards-alignment pass includes shared validation/cache/constraint helpers, MDC correlation, domain predicates, enum mappings, response factories, and the authorized identity/session internal refactors.
- The reference-only production surface contains 49 new production files with 2,190 nonblank lines: 45 reference Java files, the V2 migration, and three platform helpers. This count excludes tests, documentation, generated output, formatting churn, and authorized cross-cutting refactors so the sizing proof cannot rely on cleanup.
- Five-times floor satisfied by the reference capability alone: 2,190 nonblank production lines exceed the 1,905-addition comparison floor (5 × 381), independently of the authorized cross-cutting refactors.

### Deviations from specification

- The requested standards-alignment pass intentionally retains PR-019's established v1 session-cursor pipe format as a compatibility exception; the new PR-020 instrument cursor remains the exact canonical JSON format specified here. A future explicit cursor-wire migration can standardize the legacy session endpoint without silently invalidating clients.
- Explicit test fixtures commit setup rows before HTTP calls so each real request observes the same PostgreSQL state as production, and the SQL prefix predicate escapes `%`, `_`, and `\\` so those accepted query characters remain literal prefixes.
- Identity/session `@Version` columns remain deferred because they require a V1 migration and a defined conflict contract outside this PR's reference slice. The manual-instrument client version precondition is intentionally retained because it is the API's compare-and-swap contract, not a duplicate of Hibernate's row-version check.
- Existing PR-019 session cursors retain their pipe format as a public compatibility exception; only the new PR-020 instrument cursor uses the canonical JSON format. No entity-owned child collection exists in the active mappings, so no artificial collection mapping was added.
- The supervising user explicitly authorized the identity/session/standards refactor already mixed into PR-020. It is retained and reviewed as cross-cutting internal alignment, but excluded from the reference-only sizing proof and does not authorize new authentication behavior.

### New decisions

- The read model uses `JdbcClient` with one bounded instrument query plus one bounded alias query per page/detail, while JPA remains the minimal write/mapping model.
- Cursor payloads are canonical JSON encoded as unpadded Base64url and include a SHA-256 filter digest; decoded cursors are rejected when their payload or bound filters are not exact.
- Manual updates replace aliases inside the same optimistic transaction; ordinary metadata changes use JPA `@Version`, while alias-only updates with no dirty scalar use an immediate version-column compare-and-swap before child replacement. Both paths preserve one-winner concurrency and stable conflicts without a pessimistic lock.
- Explicit calendar coverage is computed only from stored rows; `LocalDate` iteration stops at the requested end date to avoid boundary overflow without inferring missing sessions.
- New request validation is deterministic and dependency-free at the DTO boundary; application services trust the validated fields and retain only domain-constructor invariant checks.
- Database constraint cause-chain inspection is centralized in `DatabaseConstraintTranslator`; capability services provide only known constraint mappings, while unknown integrity failures use the generic logged 500 path.
- Trace IDs are scoped in SLF4J MDC and rendered by the application logging correlation pattern; repeated no-store headers use the shared platform helper, and response factories own response construction.

### Tests executed

- `./mvnw "-Dtest=ReferenceValueObjectTest,ReferenceCatalogMigrationTest,ReferenceEntityMappingTest,ManualInstrumentServiceTest,ReferenceCatalogQueryTest,ReferenceCatalogHttpTest,ManualInstrumentHttpTest,ApiBearerSecurityHttpTest,AuthenticationAbuseProtectionTest,RefreshSessionRotationServiceTest,RefreshSessionRotationControlFlowTest" test` — 77 tests passed, 0 failures, 0 errors, 0 skipped.
- `./mvnw test` — 266 tests passed, 0 failures, 0 errors, 0 skipped in the current working tree, including the user-authorized identity/session/abuse-protection alignment.
- `./mvnw verify` — passed after applying the configured formatter, including the full 266-test suite and Spotless, with 0 failures, 0 errors, and 0 skipped.
- `git status --short` and `git diff --check` — passed; no commit, branch, history, or remote operation performed, and all changes remain unstaged for review.


### Follow-up work

- Global reference administration, calendar/import workflows, observations and provider adapters, live prices/rates, ledger/financial behavior, durable jobs, persistent signing keys, further authentication, authorization/roles, cross-site deployment infrastructure, and frontend work remain explicitly deferred.
