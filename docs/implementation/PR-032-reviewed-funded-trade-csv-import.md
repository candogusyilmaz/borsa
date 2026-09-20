# PR-032 - Reviewed funded-trade CSV import and atomic commit

Status: **ACTIVE**

## Goal

An authenticated owner can upload one bounded canonical CSV of historical same-currency funded brokerage buys and sells, inspect deterministic row issues and aggregate cash/position effects, explicitly confirm the exact preview, and atomically commit every row through the existing immutable trade ledger. Exact file retries and economic duplicates cannot create duplicate facts, every committed activity retains file/row provenance, and a failed or stale batch commits nothing.

## Capability and review boundary

- Coherent capability: this is the first usable file-ingestion boundary for investment truth. Upload without commit would be inert metadata; commit without persisted preview, provenance, and duplicate detection would be unsafe. The unit therefore carries one deliberately narrow trade format from bytes through parsing, review, and normal ledger posting.
- Combined behaviors: the Apache Commons CSV parser decision, bounded multipart contract, V8 import provenance tables, `FILE_IMPORTED` activity linkage, row/file fingerprints, current-state preview, atomic/idempotent commit, deterministic locks, existing trade calculations, API/security, and PostgreSQL/concurrency proof are one financial workflow.
- Excluded neighbor: holdings-only opening positions and incomplete cash/cost-basis coverage are the next independent ingestion capability and are not started here. They require a different accounting state boundary and must not be represented as funded trades.
- Focused review: one reviewer can follow a single fixed nine-column format into three small provenance tables, one preview/commit service, the existing funded-trade posting path, and focused parser/migration/transaction/HTTP tests. There is no parser-profile framework, row editor, raw-file store, background runtime, or second financial activity type.
- Execution-self-contained: every accepted column, normalization, fingerprint, issue, formula, schema constraint, response field, lock rule, and lifecycle transition needed for implementation is specified below. Source documents are provenance only.
- This PR is backend-only. Do not inspect or modify `web/`, regenerate a frontend client, or add compatibility endpoints.

## Source documents

- `docs/review/backend-master-plan.md` - R4 items 6-7 and 10, Stage 2 import preview/fingerprints/reconciliation, first-backlog item 17, and vertical-slice definition of done.
- `docs/review/implementable-features.md` - FND-04 import, reconciliation, duplicate, provenance, preview, and confirmation intent.
- `docs/review/accounting-contract.md` - sections 2-4, 7-10, 17-20: decimals, clocks, economic ordering, immutable facts, funded-trade settlement, weighted-average replay, projection consistency, idempotency/concurrency, and source provenance.
- `docs/engineering/coding-standards.md` - dependency review, capability packaging, Flyway/JPA ownership, direct services, errors, ownership, and PostgreSQL testing.
- [Apache Commons CSV 1.14.1 documentation](https://commons.apache.org/proper/commons-csv/) - RFC 4180 parsing, header handling, duplicate/missing-header rejection, quoting, and record numbering.
- [Spring Boot 4.1 multipart documentation](https://docs.spring.io/spring-boot/how-to/spring-mvc.html#howto.spring-mvc.multipart-file-upload) - servlet multipart support and bounded upload configuration.

*These documents establish provenance and authority. The implementer should not need to preload them because the executable contract is inlined here.*

## Starting state

- PR-031 is complete, fully verified, committed in `cb16393`, and user-accepted. V7 portfolio reporting groups and portfolio-filtered investment reads are the accepted repository baseline.
- Same-currency manual buys and sells already post immutable `Activity`, `MoneyPosting`, and `SecurityPosting` facts, apply the account cash policy, rebuild `WEIGHTED_AVERAGE_ECONOMIC_V1` positions synchronously, support backdating/reversal, and protect cash/position versions and economic order.
- Eligible trades are limited to active, owner-scoped, `BROKERAGE`/`FULL_LEDGER` accounts and owner-visible `EQUITY`/`ETF` instruments whose quotation currency equals the account currency. Historical buys may use an inactive otherwise-supported instrument; current-action buys may not.
- `ledger.activity.source_kind` currently permits only `USER_ENTERED`; no import batch/row/issue storage, file parser dependency, multipart import endpoint, or imported-row provenance link exists. The latest migration is V7.
- `GlobalExceptionHandler` already owns safe missing-part, unsupported-request-media-type, and upload-size Problem Details. `LedgerIdempotencyStore`, `LedgerCommandLockRepository`, `CanonicalFingerprint`, the trade settlement/replay domain code, and the existing no-store/authenticated HTTP boundary are available for reuse.
- The planning working tree was clean before this specification and lifecycle-pointer transition were created.

## Scope

1. Add one fixed `FUNDED_TRADE_CSV_V1` parser using Apache Commons CSV `1.14.1`. The JDK and Spring stack do not provide an RFC 4180 parser; a hand-written quoting/header parser is prohibited. Add only `org.apache.commons:commons-csv:1.14.1` and no generic import/batch framework.
2. Add V8 trade-import batch, row, and issue provenance plus an exact one-to-one source link from each imported trade activity to its row. Preserve all existing V7 financial facts and manual-source semantics.
3. Accept a bounded multipart upload for one owned eligible brokerage account. Hash the exact bytes, parse and normalize all rows, persist safe source values and static issues, and make the upload command idempotent. The raw file bytes are discarded after parsing.
4. Provide an owner-scoped read-only preview that combines persisted static issues with a repeatable-read current-state check for duplicates, economic-order conflicts, account eligibility, cash effects, historical policy decisions, and weighted-average position effects. Return a token for the exact committable snapshot.
5. Commit only a completely valid batch. Recheck the preview under deterministic locks, require the exact token, create all immutable cash/security facts with `FILE_IMPORTED` provenance in one transaction, rebuild each affected position once, and transition the batch to `COMMITTED`. No row may be skipped or partially posted.
6. Make imported provenance inspectable from existing trade detail/history responses through nullable source batch/row/external identifiers. Preserve the existing manual trade API, reversal, cash ledger, reconciliation, portfolio filters, projection formulas, and owner isolation.

## Explicit non-goals

- No holdings-only/opening-position import, unknown basis, incomplete cash history, or migration of existing broker holdings.
- No deposit, withdrawal, transfer, fee-only, interest, dividend, withholding, tax, corporate-action, pending-settlement, or statement-balance import.
- No source-stated gross override. V1 always derives settled gross from quantity and unit price using the existing manual-trade rule.
- No FX or multi-currency trade, separate settlement currency, broker-executed FX, exchange rate, or currency conversion.
- No arbitrary broker templates, column mapping UI/API, locale-specific numbers/dates, symbol/alias matching, fuzzy matching, AI extraction, or editable persisted rows. Users correct a source file and upload different bytes.
- No partial commit, duplicate skipping, “best effort” posting, destructive batch reset, batch delete, reject/reopen transition, or mutation after commit.
- No raw-file/document blob persistence, file download, retention/deletion API, malware scanning, local/object storage abstraction, or document vault. Only bounded provenance metadata and source row strings are retained.
- No asynchronous parser, scheduler, worker, queue, Spring Batch, `platform.job` use, progress polling, retry framework, or generic workflow runtime.
- No import list/search endpoint, custom pagination, provider/broker connection, frontend work, or generated frontend contract.

## Database changes

Migration: `V8__reviewed_funded_trade_csv_import.sql`.

Create `ledger.trade_import_batch` with exactly:

- `id uuid` primary key generated by the application;
- `owner_user_account_id uuid NOT NULL`;
- `financial_account_id uuid NOT NULL`;
- `import_format text NOT NULL` and constrained to `FUNDED_TRADE_CSV_V1`;
- `status text NOT NULL` and constrained to `PARSED` or `COMMITTED`;
- `original_file_name text NOT NULL`;
- `media_type text NOT NULL`; store the submitted part media type trimmed and lowercased, or `application/octet-stream` when absent. It is provenance, not a trust decision;
- `byte_size bigint NOT NULL`;
- `content_sha256 text NOT NULL` as lowercase hexadecimal over the exact uploaded bytes, before BOM removal or decoding;
- `parsed_row_count integer NOT NULL`, counting data records with the header excluded;
- `created_at timestamptz NOT NULL` and nullable `committed_at timestamptz`;
- `version bigint NOT NULL DEFAULT 0` for the single commit lifecycle transition.

Batch constraints and indexes:

- `pk_ledger_trade_import_batch` on `id`;
- `fk_ledger_trade_import_batch_owner` to `identity.user_account(id)` with `ON DELETE CASCADE`;
- `fk_ledger_trade_import_batch_account` on `(owner_user_account_id, financial_account_id)` to `ledger.financial_account(owner_user_account_id, id)` with `ON DELETE CASCADE`;
- `uq_ledger_trade_import_batch_owner_id` on `(owner_user_account_id, id)`;
- `uq_ledger_trade_import_batch_content` on `(owner_user_account_id, financial_account_id, import_format, content_sha256)`, so different filenames with identical bytes resolve to the same account-specific batch;
- `ck_ledger_trade_import_batch_file_name`: the stored basename is trimmed and contains 1-255 characters;
- `ck_ledger_trade_import_batch_media_type`: trimmed and 1-120 characters;
- `ck_ledger_trade_import_batch_byte_size`: `byte_size BETWEEN 1 AND 1048576`;
- `ck_ledger_trade_import_batch_sha256`: `content_sha256 ~ '^[0-9a-f]{64}$'`;
- `ck_ledger_trade_import_batch_row_count`: `parsed_row_count BETWEEN 1 AND 500`;
- `ck_ledger_trade_import_batch_status_shape`: `PARSED` requires null `committed_at`; `COMMITTED` requires non-null `committed_at` not earlier than `created_at`;
- `ck_ledger_trade_import_batch_version_non_negative`: `version >= 0`.

Create `ledger.trade_import_row` with exactly:

- `id uuid` primary key generated by the application;
- `owner_user_account_id uuid NOT NULL`;
- `import_batch_id uuid NOT NULL`;
- `source_record_number integer NOT NULL`, one-based among data records; it is the Apache CSV logical record number after the header, not a physical line number, so quoted embedded newlines do not change identity;
- nullable `source_external_id text`;
- `source_values jsonb NOT NULL`, an array retaining the strings in parsed column order after CSV quote handling and surrounding-space normalization; malformed-width rows retain every parsed value;
- `normalization_status text NOT NULL`, constrained to `VALID` or `INVALID`;
- nullable normalized `side text`, `instrument_id uuid`, `currency_code text`, `effective_at timestamptz`, `economic_sequence bigint`, `quantity numeric(38,18)`, `unit_price numeric(38,18)`, `commission_amount numeric(38,18)`, `gross_amount numeric(38,18)`, `cash_delta numeric(38,18)`, and `row_fingerprint text`;
- `created_at timestamptz NOT NULL`.

Row constraints and indexes:

- `pk_ledger_trade_import_row` on `id`;
- `fk_ledger_trade_import_row_owner` to `identity.user_account(id)` with `ON DELETE CASCADE` plus `fk_ledger_trade_import_row_batch` on `(owner_user_account_id, import_batch_id)` to the batch owner/id key with `ON DELETE CASCADE`;
- nullable `fk_ledger_trade_import_row_instrument` to `reference.instrument(id)` and `fk_ledger_trade_import_row_currency` to `reference.currency(code)`, both with `ON DELETE RESTRICT`;
- `uq_ledger_trade_import_row_owner_id` on `(owner_user_account_id, id)` and `uq_ledger_trade_import_row_owner_batch_id` on `(owner_user_account_id, import_batch_id, id)` for owner-safe references;
- `uq_ledger_trade_import_row_record` on `(owner_user_account_id, import_batch_id, source_record_number)`;
- `ix_ledger_trade_import_row_owner_fingerprint` on `(owner_user_account_id, row_fingerprint)` where the fingerprint is non-null, for committed-import duplicate matching through the activity source link;
- `ck_ledger_trade_import_row_record_number`: `source_record_number BETWEEN 1 AND 500`;
- `ck_ledger_trade_import_row_source_values`: `jsonb_typeof(source_values) = 'array'` and its textual representation is at most 65,536 bytes;
- `ck_ledger_trade_import_row_external_id`: null or trimmed with 1-200 characters;
- `ck_ledger_trade_import_row_fingerprint`: null or lowercase 64-character SHA-256 hex;
- `ck_ledger_trade_import_row_normalized_shape`: `INVALID` has every normalized economic column and fingerprint null; `VALID` has all normalized columns/fingerprint non-null, `source_external_id` non-null, `side IN ('BUY','SELL')`, non-negative economic sequence, positive quantity/unit price/gross, non-negative commission, and non-zero cash delta. For a valid buy, `cash_delta < 0`; for a valid sell, `cash_delta > 0`.

Create `ledger.trade_import_issue` with exactly:

- `id uuid` primary key generated by the application;
- `owner_user_account_id uuid NOT NULL`;
- `import_batch_id uuid NOT NULL`;
- `import_row_id uuid NOT NULL`;
- `issue_code text NOT NULL`;
- `field_name text NOT NULL`; use the exact CSV header name or `row` for a record-wide issue;
- `created_at timestamptz NOT NULL`.

Issue constraints:

- `fk_ledger_trade_import_issue_owner` to `identity.user_account(id)` with `ON DELETE CASCADE` and `fk_ledger_trade_import_issue_row` on `(owner_user_account_id, import_batch_id, import_row_id)` to the row owner/batch/id key with `ON DELETE CASCADE`;
- unique `(owner_user_account_id, import_row_id, issue_code, field_name)`;
- trimmed `field_name` length 1-80;
- `issue_code` is one of `RECORD_SHAPE_INVALID`, `EXTERNAL_ID_INVALID`, `SIDE_INVALID`, `INSTRUMENT_ID_INVALID`, `INSTRUMENT_UNSUPPORTED`, `CURRENCY_INVALID`, `CURRENCY_MISMATCH`, `EFFECTIVE_AT_INVALID`, `ECONOMIC_SEQUENCE_INVALID`, `QUANTITY_INVALID`, `UNIT_PRICE_INVALID`, `COMMISSION_INVALID`, `SETTLED_PRECISION_INVALID`, `SELL_PROCEEDS_NOT_POSITIVE`, `DUPLICATE_EXTERNAL_ID`, `DUPLICATE_ROW_IN_FILE`, or `ECONOMIC_ORDER_CONFLICT_IN_FILE`.

Alter `ledger.activity`:

- add nullable `source_import_row_id uuid`;
- replace `ck_ledger_activity_source_kind` so only `USER_ENTERED` and `FILE_IMPORTED` are accepted;
- add `fk_ledger_activity_source_import_row` on `(owner_user_account_id, source_import_row_id)` to `ledger.trade_import_row(owner_user_account_id, id)` as `DEFERRABLE INITIALLY DEFERRED` with `ON DELETE NO ACTION`. This permits the existing owner-cleanup statement to remove both sides while preventing an ordinary import-row deletion that would orphan an activity;
- add `uq_ledger_activity_source_import_row` on `(owner_user_account_id, source_import_row_id)` where non-null;
- add `ck_ledger_activity_source_shape`: `USER_ENTERED` requires null `source_import_row_id`; `FILE_IMPORTED` requires non-null `source_import_row_id`, `activity_type IN ('SECURITY_BUY','SECURITY_SELL')`, and `recording_mode = 'HISTORICAL_FACT'`.

Do not alter the accepted money/security posting signs, trade economic-key uniqueness, position projection identity/formula, reconciliation table, portfolio tables, or idempotency snapshot size. Do not create generic document, mapping-profile, job, provider, opening-holding, valuation, or observation tables.

Migration proof must cover empty-to-V8 and a real V7-to-V8 upgrade containing manual and reversed trades, open/closed positions, cash/reconciliation facts, portfolios/memberships, and user/global instruments. V7 facts must remain semantically unchanged with `USER_ENTERED` plus null `source_import_row_id`; Hibernate validation and owner cleanup must pass.

## Application changes

Keep the capability under `investing`: JPA aggregates/enums in `investing/domain`, Spring Data repositories and the concrete Commons CSV adapter in `investing/infrastructure`, one cohesive `TradeImportService` (with a small transaction collaborator only if required to keep byte parsing outside persistence transactions) in `investing/application`, and `TradeImportController` plus request/response records in the established `investing/web` packages. Do not create a root import layer, generic parser interface, mapper package, service/implementation pair, or parallel trade command stack.

### Parser and upload

- Add Apache Commons CSV `1.14.1` explicitly to `server/pom.xml`. The build-versus-buy decision is final for this PR: JDK/Spring multipart handles transport but not RFC 4180 quoting/header semantics; Commons CSV is the focused maintained parser; do not implement a custom CSV tokenizer or introduce Spring Batch.
- Set `spring.servlet.multipart.max-file-size: 1MB` and `spring.servlet.multipart.max-request-size: 2MB`. Retain the existing global `PAYLOAD_TOO_LARGE` mapping. Read no more than 1,048,576 file bytes in application code even when a nonstandard servlet configuration is used.
- Read/hash/decode/parse the bounded file before opening the persistence transaction. Use a UTF-8 decoder that reports malformed/unmappable input. Permit exactly one leading UTF-8 BOM and remove it only for decoding; hash the original bytes. Accept CRLF or LF records, RFC 4180 double-quoted fields including commas/newlines/escaped quotes, and surrounding spaces outside quoted values. Empty records are not ignored; they become invalid rows. Lenient EOF is disabled.
- The header is case-sensitive and must contain exactly these columns in this order, with no missing, blank, duplicate, or extra name: `external_id,side,instrument_id,currency,effective_at,economic_sequence,quantity,unit_price,commission_amount`.
- Require 1-500 data records. A record with a field count other than nine is persisted as `INVALID` with `RECORD_SHAPE_INVALID`; its available values are retained. A file-wide decoding, CSV syntax, or header failure creates no batch.
- Store only the sanitized basename from the submitted filename: normalize `\` to `/`, take the final segment, trim it, and require 1-255 characters. Do not trust or create a filesystem path from it. Discard raw bytes after parsing/persistence and never log bytes or source row contents.
- `POST /api/v1/imports` has a required `clientRequestId`. Use operation scope `investing.trade_import.upload`; its canonical request hash contains `accountId`, `FUNDED_TRADE_CSV_V1`, and the exact content SHA-256, but not filename/media type. Same key plus same semantic request replays the original small upload response; key reuse with a different account or content uses existing `IDEMPOTENCY_CONFLICT`.
- After the client-key advisory lock, take a second owner-scoped advisory lock keyed by `investing.trade_import.content` and a deterministic UUID derived from the first 16 hash bytes. Under it, return the existing same-owner/account/format/content batch rather than inserting another. First creation returns `201`; a different upload key resolving to existing content returns `200`. The first stored filename/media type remain authoritative.
- Validate the selected account in the persistence transaction using the existing owner-safe account authority. It must be active `BROKERAGE`/`FULL_LEDGER`; use existing `ACCOUNT_NOT_FOUND`, `ACCOUNT_ARCHIVED`, and `ACCOUNT_ACTION_NOT_SUPPORTED` outcomes.

### Exact row normalization

- Trim every parsed field after RFC 4180 quote processing. `external_id` must contain 1-200 characters and be unique within the file. It is source provenance and is deliberately excluded from the economic fingerprint.
- `side` is case-sensitive `BUY` or `SELL`. `instrument_id` is a canonical UUID and must resolve through the existing owner/global visibility rule to an `EQUITY` or `ETF`. Because all imported rows are historical facts, an inactive otherwise-supported instrument is allowed. Missing, cross-owner private, and unsupported instruments all produce `INSTRUMENT_UNSUPPORTED` without existence disclosure.
- `currency` is a case-sensitive reference currency code and must equal both the selected account native currency and the instrument quotation currency. A malformed/unknown code produces `CURRENCY_INVALID`; a known non-matching code produces `CURRENCY_MISMATCH`.
- `effective_at` must be an ISO-8601 UTC instant ending in `Z` and exactly representable to microseconds. Do not silently truncate greater precision or interpret a local date/time/timezone. Whether it is still in the future is evaluated on every preview and commit rather than frozen at upload.
- `economic_sequence` is a base-10 non-negative `long`. For deterministic account-level cash ordering, no two normalized rows may share `(effective_at,economic_sequence)`, even for different instruments. Later occurrences receive `ECONOMIC_ORDER_CONFLICT_IN_FILE`.
- Parse `quantity`, `unit_price`, and `commission_amount` with the existing exact `FinancialAmount` rules. Quantity/unit price are positive; commission is non-negative; all must fit `numeric(38,18)`. Use no locale separators or exponent notation not already accepted by `FinancialAmount`.
- Calculate settlement exactly as manual trade entry: `gross = quantity * unitPrice`, rounded exactly once to the account currency minor unit with `HALF_EVEN`; buy `cashDelta = -(gross + commission)`; sell `cashDelta = gross - commission`. Commission must be representable at the currency minor unit; gross must be positive; sell cash delta must be positive. Persist canonical decimals and never accept a source-stated gross.
- A fully normalized row fingerprint is SHA-256 through the existing explicit `CanonicalFingerprint` framing over these keys in this order: `importFormat`, `accountId`, `instrumentId`, `side`, `currency`, `effectiveAt`, `economicSequence`, `quantity`, `unitPrice`, `commissionAmount`, `grossAmount`. UUIDs/enums use canonical text, time uses the normalized microsecond UTC instant, and decimals use `FinancialAmount.canonical()`.
- For repeated valid `external_id` or row fingerprint values, retain the first row and add `DUPLICATE_EXTERNAL_ID` or `DUPLICATE_ROW_IN_FILE` to every later occurrence. Any static issue makes that row `INVALID`; a batch containing any invalid row remains inspectable but cannot commit.

### Preview and duplicate/reconciliation behavior

- `GET /api/v1/imports/{id}/preview` is a repeatable-read, owner-scoped snapshot. A missing/cross-owner ID is `IMPORT_NOT_FOUND`. It returns persisted static issues, then checks current account/instrument/projection/fact state without changing batch or financial data.
- For each statically valid row, search the selected account's existing non-reversal security postings at the same `(effectiveAt,economicSequence)`. If the existing instrument, side, quantity, unit price, gross, commission, currency, and economic order all equal the row, return dynamic `DUPLICATE_EXISTING_TRADE` with the owned `relatedActivityId`; otherwise return `EXISTING_ECONOMIC_ORDER_CONFLICT`. This account-level conflict check is intentionally stricter than the pre-existing per-instrument database key so imported cash order is unambiguous.
- Any row whose normalized `effectiveAt` is later than the preview snapshot clock receives dynamic `EFFECTIVE_AT_FUTURE`; commit repeats the check using its single locked transaction clock instant.
- Also match a committed imported row by row fingerprint through `Activity.sourceImportRowId`; it is `DUPLICATE_EXISTING_TRADE` even if its original trade was later reversed. Reversal preserves history and does not make the same source row new.
- If no static/duplicate/order issue exists, replay every affected instrument using all existing immutable events plus all candidate rows in `(effectiveAt,economicSequence,sourceRecordNumber)` order. Any intermediate negative position makes the first offending row `INSUFFICIENT_POSITION_QUANTITY`. Apply the existing scale-18 `HALF_EVEN` allocation and full-close rules without alternate import math.
- Starting from the current cash projection, process candidates in the same deterministic order and apply the existing `HISTORICAL_FACT` policy evaluation per row. A negative historical result is allowed and records `HISTORICAL_BREACH_RECORDED`; otherwise it records `ALLOWED`. The exact current cash result is `cashBefore + sum(row.cashDelta)` and must fit `numeric(38,18)`.
- `commitEligible` is true only when the batch is `PARSED`, its account is still eligible, every row is normalized, there are no static or dynamic issues, all calculations fit, and every candidate position replay succeeds. No preview token is returned otherwise.
- For a committable preview, compute `previewToken` with `CanonicalFingerprint`/SHA-256 over: batch ID/version; account ID/version; cash projection ID/version/canonical balance; then, by ascending instrument UUID, instrument ID/version, position projection ID or `null`, position version or `0`, and canonical before/after quantity/basis/realized-P&L; then every row fingerprint and policy decision in source-record order. The token is lowercase 64-character hex and represents exactly what the owner confirms.
- Summary totals use all normalized rows and are exact: buy/sell counts; `buyGross = sum(BUY gross)`; `sellGross = sum(SELL gross)`; `commissionTotal = sum(all commission)`; `cashDeltaTotal = sum(all cashDelta)`. `cashAfter = cashBefore + cashDeltaTotal`. Monetary totals use the one account currency. No mixed-currency sum exists.
- The preview is read-only evidence. It creates no activity/posting, changes no projection/version, and does not reserve a balance or position. A later concurrent change is handled by the commit token/locks.

### Atomic commit

- `POST /api/v1/imports/{id}/commit` uses operation scope `investing.trade_import.commit`. Its idempotency hash contains owned batch ID and `previewToken`. The same client request/key returns the original commit response; key reuse with different input is `IDEMPOTENCY_CONFLICT`. A different key against an already committed batch returns `IMPORT_ALREADY_COMMITTED`.
- One transaction owns the entire state change. Lock in this order: owner/idempotency advisory key; owned batch row; brokerage account; cash projection; every affected position row by ascending instrument UUID; then every visible instrument row by ascending UUID. This extends the existing manual-trade order and prevents batch/batch or batch/manual deadlocks/lost updates. An absent position is protected by the already-held account lock and final projection uniqueness.
- Under the locks, rerun account eligibility, duplicate/order checks, settlement arithmetic, cash policy evaluation, and full position replay. If the batch has any static/dynamic issue, return `IMPORT_NOT_COMMITTABLE`; if it remains committable but the recomputed token differs, return `IMPORT_PREVIEW_STALE`. Both leave the batch `PARSED` and write no financial state.
- Use one injected `Clock` instant as `recordedAt`, posting `createdAt`, batch `committedAt`, and transaction observation time. For each row create a historical `SECURITY_BUY` or `SECURITY_SELL` activity with `sourceKind=FILE_IMPORTED`, `sourceImportRowId=row.id`, `clientEventId=batch.id`, operation scope `investing.trade_import.commit`, and `commandSequence=sourceRecordNumber - 1`. Persist the same gross cash leg, optional non-zero fee leg, and security leg as manual trade entry.
- Process facts in `(effectiveAt,economicSequence,sourceRecordNumber)` order for policy and cash projection watermark behavior. Rebuild each distinct position once from all existing facts plus the complete batch. Apply every row cash delta to the single selected account projection; immutable postings remain the authority.
- Update the batch to `COMMITTED`, set `committedAt`, and increment its optimistic version once. Flush financial facts/projections and lifecycle before saving the idempotent response. Any persistence, calculation, duplicate, owner, lock, or projection failure rolls back every row, posting, projection, batch transition, and idempotency record.
- Do not invoke controllers, loop through public HTTP commands, create one transaction/idempotency record per row, or maintain a parallel trade formula. Extract/reuse the smallest cohesive internal funded-trade settlement/posting/replay behavior needed by both manual commit and import; preserve the manual endpoint's request/response and accepted semantics.

## API contract

All endpoints are authenticated, owner-scoped, use existing no-store headers, and live under `/api/v1`. Raw file bytes and row contents must never be logged.

### Upload

`POST /api/v1/imports` consumes `multipart/form-data` with exactly these required parts:

- `clientRequestId`: UUID;
- `accountId`: UUID;
- `file`: one non-empty file, bounded to 1 MiB.

The endpoint returns `TradeImportUploadResponse`. A new batch returns `201` with `Location: /api/v1/imports/{id}/preview`; an exact same-content batch found under a different request key returns `200` with the same location. The response contains required `id`, `accountId`, `importFormat`, `status`, `duplicateContent`, `createdAt`, and `previewUrl`. `status` is `PARSED` or `COMMITTED`; `duplicateContent` describes whether this call resolved an existing batch.

### Preview

`GET /api/v1/imports/{id}/preview` returns `TradeImportPreviewResponse` with required:

- batch metadata: `id`, `accountId`, `importFormat`, `status`, `originalFileName`, `mediaType`, `byteSize`, `contentSha256`, `rowCount`, `createdAt`, and nullable `committedAt`;
- `commitEligible` and nullable `previewToken`; token is present exactly when a `PARSED` batch is currently committable;
- complete `batchIssues`, ordered by code then field, for current account-level or aggregate-calculation blockers that do not belong to one row;
- `summary` containing required `rowCount`, `normalizedRowCount`, `issueRowCount`, `duplicateRowCount`, `buyCount`, `sellCount`, `currency`, `buyGross`, `sellGross`, `commissionTotal`, and `cashDeltaTotal`, plus nullable `cashBalanceBefore`, `cashBalanceAfter`, `accountVersion`, and `cashBalanceVersion`. Current-state fields are present only for a complete successful simulation;
- complete unpaged `rows`, ordered by `sourceRecordNumber` ascending;
- `positionImpacts`, ordered by instrument UUID ascending and present only for a successful simulation.

Each row contains required `id`, `sourceRecordNumber`, `sourceValues`, and `issues`; nullable normalized `externalId`, `side`, `instrumentId`, `currency`, `effectiveAt`, `economicSequence`, `quantity`, `unitPrice`, `commissionAmount`, `grossAmount`, `cashDelta`, `rowFingerprint`, `policyDecision`, and `committedActivityId`. An issue contains required `code`, `field`, and safe `detail`, plus nullable owned `relatedActivityId`. Issues are ordered by code then field. Dynamic row issue codes are `EFFECTIVE_AT_FUTURE`, `DUPLICATE_EXISTING_TRADE`, `EXISTING_ECONOMIC_ORDER_CONFLICT`, and `INSUFFICIENT_POSITION_QUANTITY`; dynamic `batchIssues` are `ACCOUNT_ARCHIVED`, `ACCOUNT_ACTION_NOT_SUPPORTED`, and `NUMERIC_OVERFLOW`. These are response issue codes, not Problem Detail codes, and supplement the persisted static codes listed in the database section.

Each position impact contains required `instrumentId`, `symbol`, `currency`, `positionVersion`, `quantityBefore`, `quantityAfter`, `remainingBasisBefore`, `remainingBasisAfter`, `realizedEconomicPnlBefore`, and `realizedEconomicPnlAfter`; a missing current projection has version `0` and zero before-values. Every financial decimal is a canonical string.

For a committed batch, preview remains available with stored rows and `committedActivityId` links, `commitEligible=false`, and no token. Do not recompute or imply the old pre-commit cash/position snapshot after later financial changes; nullable current-state summary fields and `positionImpacts` are absent/empty for committed batches.

### Commit

`POST /api/v1/imports/{id}/commit` accepts JSON `TradeImportCommitRequest` with required `clientRequestId` UUID and `previewToken` matching lowercase `[0-9a-f]{64}`. It returns `200 TradeImportCommitResponse` with required `id`, `accountId`, `status=COMMITTED`, `committedRowCount`, `activityIds` in source-record order, `cashBalanceAfter`, `cashBalanceVersion`, `positions` ordered by instrument UUID with required `instrumentId` and `positionVersion`, and `committedAt`.

Extend existing `TradeResponse` and `TradeSummaryResponse` with nullable `sourceImportBatchId`, `sourceImportRowId`, and `sourceExternalId`. All three are non-null exactly when `sourceKind=FILE_IMPORTED` and identify the owned source evidence; all are null for `USER_ENTERED`. Populate them by a left join through `Activity.sourceImportRowId` in the existing trade read stack. Do not add a second history/detail endpoint or expose raw `sourceValues` from general trade reads.

Add stable investing errors, all without interpolation params:

- `IMPORT_NOT_FOUND` - HTTP 404 for a missing or cross-owner batch;
- `IMPORT_FILE_EMPTY` - HTTP 422 for zero bytes or no data records;
- `IMPORT_FILE_ENCODING_INVALID` - HTTP 422 for non-UTF-8 input;
- `IMPORT_FILE_FORMAT_INVALID` - HTTP 422 for malformed RFC 4180, invalid filename, or a non-exact header;
- `IMPORT_ROW_LIMIT_EXCEEDED` - HTTP 422 when more than 500 data records are observed;
- `IMPORT_NOT_COMMITTABLE` - HTTP 409 when static or current dynamic issues block the complete batch;
- `IMPORT_PREVIEW_STALE` - HTTP 409 when a currently committable locked snapshot does not match the submitted token;
- `IMPORT_ALREADY_COMMITTED` - HTTP 409 for a different commit key after completion.

Structural missing parts/UUIDs/token format use the existing common binding/validation contract. Over-size transport uses `PAYLOAD_TOO_LARGE`; wrong outer request media type uses `UNSUPPORTED_MEDIA_TYPE`; idempotency-key misuse uses existing `IDEMPOTENCY_CONFLICT`. Never expose parser exceptions, source row values, SQL/constraint names, or cross-owner existence in Problem Details. Register only constraints that have a safe deterministic application translation; concurrent same-content upload should normally resolve under its content lock, while unknown persistence failures remain safe 500s.

## Business invariants

- A parsed file is preview evidence, not financial truth. Only a successful explicit batch commit creates actual facts.
- One batch belongs to exactly one owner and one actual brokerage account. Every row and issue has the same owner/batch by composite foreign key; every imported activity has exactly one source row and every source row can create at most one activity.
- File identity is the SHA-256 of exact bytes; economic row identity is the canonical normalized trade fingerprint. Filename, media type, upload time, source record position, and external ID do not change economic identity.
- The fixed format represents only historical, settled, same-currency, fully funded trades. It cannot create current-action intent, pending cash, holdings-only state, source-stated gross, FX, tax, or corporate action facts.
- Imported settlement, posting signs, fee treatment, cash policy decisions, weighted-average basis, realized P&L, full-close reset, precision, and backdated replay are identical to manual historical trade behavior.
- Economic replay order is `effectiveAt`, then `economicSequence`; source record number is only a deterministic final tie-break after the file/account-level conflict rules have proved that financial ordering is not ambiguous.
- A batch is all-or-nothing. One invalid, duplicate, conflicting, short-producing, stale, or overflowing row prevents every financial write. Commit never silently drops, deduplicates, clamps, rounds beyond the settlement rule, or reorders an ambiguous event.
- Exact upload retry returns one batch. Exact commit retry with the same key/request returns one result. A changed request under a reused key conflicts. A committed source row cannot create a second activity even after reversal.
- Preview confirmation is optimistic, not a reservation. Commit must revalidate under account/cash/position/instrument locks, and concurrent manual/import commands must yield a serially valid winner plus a stable stale/version/conflict result for the loser.
- Raw file bytes are not retained. Stored filename, exact content hash, source record number/external ID/source strings, normalized values, issues, and activity linkage are the audit/provenance record for this V1 format. Financial facts survive only through the normal owner-cleanup lifecycle and are never deleted by an import lifecycle command.

## Required tests

### Pure/domain

- RFC 4180 CRLF/LF, quoted comma/newline/escaped quote, surrounding-space handling, UTF-8 BOM, strict UTF-8 failure, empty records, malformed quote/EOF, exact/missing/extra/duplicate/reordered headers, record numbering, 1/500/501-row boundaries, and 1 MiB boundary behavior.
- Exact field normalization and every persisted static issue code, including invalid row width, owner-invisible instrument, currency mismatch, microsecond/time/future rules, decimal boundaries, minor-unit commission, zero/negative values, and non-positive sell proceeds.
- File and row fingerprints prove exact-byte sensitivity, semantic decimal/time canonicalization, external-ID exclusion, deterministic field order, and changed economic input sensitivity.
- Hand-worked multi-row buy/partial-sell/full-close/reopen preview proves aggregate cash, commission, quantity, basis, realized P&L, remainder handling, source/economic ordering, historical breach decisions, and no rounding beyond the accepted formulas.

### PostgreSQL/Testcontainers

- Fresh V8 migration, V7-to-V8 preservation, Hibernate mapping validation, future-table absence, and accepted V7 trade/reversal/position/reconciliation/portfolio fixtures unchanged.
- Raw SQL acceptance/rejection for owner alignment, batch content uniqueness, status/timestamp/version shape, file/hash/count bounds, source JSON/status/economic shape, row number uniqueness, issue codes/uniqueness, activity source kind/link/type/recording shape, one activity per source row, and deferred owner cleanup.
- Upload idempotency and same-content different-key convergence, including concurrent uploads; different account or bytes create distinct batches and key reuse with changed content conflicts.
- Static-invalid batches persist complete ordered evidence but no financial fact. Preview is repeatable-read/read-only and changes no batch, cash, position, reconciliation, or projection version.
- Existing manual/import exact duplicate matching, changed-value economic-order conflict, within-file duplicate/external-ID/order detection, and reversed-import re-upload all block without duplicate facts.
- Successful multi-instrument commit creates the exact activities/money/security postings, `FILE_IMPORTED` row links, policy decisions, cash result, one rebuilt projection per affected instrument, batch transition, and source-record-ordered response. Backdated rows produce the accepted as-of/reconciliation-staleness behavior.
- Atomic rollback for one late invalid/short/overflow/constraint failure leaves no row activity, posting, projection change, batch transition, or idempotency record.
- Concurrent preview-to-commit staleness, manual-trade versus batch, batch versus batch on one account, and independent accounts prove deterministic locks, no deadlock/lost update, one winner where applicable, stable conflicts, and no partial facts.
- Generic reasoned reversal of an imported trade preserves batch/row provenance and rebuilds cash/position exactly; the import row remains a duplicate thereafter.

### HTTP/security

- Authenticated multipart new upload (`201`/Location), same-content resolution (`200`), preview, commit, idempotent replay, no-store headers, exact required/nullable response fields, deterministic row/issue/position/activity ordering, and canonical decimal strings.
- Missing/malformed parts, empty/oversize/non-UTF-8/malformed/header-invalid/too-many-row files, token validation, non-committable/stale/already-committed states, and safe RFC 9457 codes without parser/SQL/source leakage.
- Every row issue code has a safe response shape; normalized fields are null for invalid rows; current-state/position fields and token appear only under the specified conditions.
- Unauthenticated rejection and cross-owner account, private instrument, batch preview, and batch commit isolation without existence disclosure.
- Generated OpenAPI documents multipart parts, upload status alternatives, preview/commit schemas, enum values, canonical-string financial fields, required/nullable fields, and no unrelated endpoint changes.
- Existing trade detail/history exposes nullable import batch/row/external provenance exactly for imported activities and retains nulls for all manual activities.
- Existing manual trade/preview/reversal/history/position, cash ledger/reconciliation, portfolio filters, bearer security, global error, and OpenAPI tests remain green.

## Acceptance criteria

1. An owner can upload a 1-500-row canonical funded-trade CSV for one active full-ledger brokerage account and retrieve complete deterministic row evidence without posting financial facts.
2. File/row normalization, exact formulas, static and current issues, duplicate matches, cash totals, and position impacts are objectively reproducible; only a fully valid current snapshot receives a preview token.
3. Confirming that exact token commits every row once in one transaction through immutable cash/security facts with `FILE_IMPORTED` row provenance and accepted weighted-average projections.
4. Any invalid, duplicate, ambiguous-order, short-producing, stale, concurrent, overflowing, or persistence-failing row commits nothing and returns the specified safe outcome.
5. Exact file and commit retries cannot create another batch/economic fact; modified same-key requests conflict; prior imported facts remain duplicates after reversal.
6. PostgreSQL enforces owner/provenance/source/lifecycle shape and preserves the V7 database on upgrade; owner cleanup, Hibernate validation, and fresh migration pass.
7. Manual trade, reversal, reconciliation, position/trade reads, portfolio filters, cash balances, security, and OpenAPI behavior remain compatible apart from the specified nullable provenance fields.
8. No holdings-only opening, generic mapping/editor, raw-file store, async runtime, non-trade import, FX/tax/corporate action, provider, frontend, or speculative schema enters the change surface.
9. The focused parser/domain/migration/service/concurrency/HTTP/OpenAPI gate and the single full Maven exit gate pass with no skipped required tests.

## Documentation completion

Before this implementation unit is considered complete:

1. Update `docs/implementation/STATE.md` with V8 reality only: the fixed CSV format, provenance/source-kind behavior, import lifecycle, duplicate/atomic semantics, dependency baseline, schema tables, deferred holdings-only/import-expansion work, and latest useful verification state.
2. Replace or remove obsolete STATE assertions rather than appending history. Keep detailed implementation/test evidence in this Completion Record and Git history.
3. Update `docs/review/progress-report.md` for the verified R4 reviewed-import checkpoint. Change `accounting-contract.md`, `backend-master-plan.md`, or `coding-standards.md` only if implementation changes an authoritative semantic or baseline beyond this specification.
4. Move only reusable Windows, Maven, multipart, Testcontainers, or tool-output lessons to `docs/engineering/codex-command-playbook.md`.
5. Do not update `docs/implementation/web/STATE.md` or any file under `web/`; this PR is backend-only.

## Verification commands

### Inner-loop verification

Run the smallest focused gate from `server/` while implementing:

```powershell
.\mvnw.cmd "-Dtest=TradeImportParserTest,TradeImportDomainTest,TradeImportMigrationTest,TradeImportServiceTest,TradeImportConcurrencyTest,TradeImportHttpTest,OpenApiHttpTest" test
```

Do not run the complete test suite or Maven `verify` during normal inner-loop development.

### Exit gate

Run once only after implementation and inner-loop verification are complete:

```powershell
.\mvnw.cmd verify
```

Run `.\mvnw.cmd spotless:apply` beforehand if formatting adjustments are needed.

## Completion record

Implementation, deviations, decisions, verification, and follow-up are recorded below. User acceptance remains pending.

### Implemented

- Added V8 import batches, source rows, static issues, import activity source kind, and nullable one-to-one trade activity provenance. Added the fixed Apache Commons CSV `1.14.1` parser, bounded multipart upload, safe row persistence, and exact-byte SHA-256 identity.
- Added owner-scoped idempotent upload, repeatable-read preview with dynamic eligibility, duplicate, order, cash, and weighted-average checks, plus deterministic preview tokens.
- Added atomic all-or-nothing commit with deterministic locks and shared funded-trade posting, one projection rebuild per instrument, and imported source metadata in trade detail/history. Commit retries use a compact import-specific replay snapshot that preserves the exact response within the existing 32,768-byte idempotency limit.
- Data-record parsing accepts surrounding spaces around quoted values while the exact header is parsed strictly. Added PostgreSQL coverage for a 500-row/500-instrument commit and exact replay, late persistence-failure rollback, reversal rebuild/provenance, history provenance, and valid/raw-SQL normalized-row constraints.

### Deviations from specification

- None.

### New decisions

- None; the dependency, format, limits, lifecycle, and posting behavior follow the specification.

### Tests executed

- `.\mvnw.cmd spotless:apply` completed successfully.
- Specified focused gate (`TradeImportParserTest,TradeImportDomainTest,TradeImportMigrationTest,TradeImportServiceTest,TradeImportConcurrencyTest,TradeImportHttpTest,OpenApiHttpTest`): 30 tests passed, 0 failures/errors/skips.
- Final `.\mvnw.cmd verify`: 451 tests passed, 0 failures/errors/skips; Maven reported `BUILD SUCCESS` and Spotless check passed.

### Follow-up work

- PR-032 is implemented and verified pending user acceptance. No next PR was activated. Holdings-only opening positions and the broader deferred R4 capabilities remain outside this specification.
