# Cash accounts, funding sources, and balance-control design

Review date: 2026-08-05

Status: proposed backend and product design. This expands FND-01 and FT-31 in [implementable-features.md](implementable-features.md). No production code was changed as part of this design.

## Decision

The feature is plausible and foundational. The recommended rules are:

1. A user can own or share **multiple financial accounts** that hold or owe money.
2. Every **posted monetary action** has one or more explicit account postings.
3. For a manual payment/purchase/trade, the user chooses the funding account or confirms a visible default.
4. Transfers have both a source and destination; they are not income or spending.
5. Negative balances are controlled by an account-specific policy. Imported reality is never discarded simply because it breaches that policy.
6. Bills, plans, expected income, reminders, and scenarios do **not** change account balances until a real activity posts.
7. Balances are rebuildable projections of immutable activities. A mutable `balance` column must not be the sole financial truth.

“Active cash” should be presented as several precise amounts rather than one ambiguous total:

- cash actually held;
- cleared and pending amounts;
- liquid cash available under the account's own rules;
- money reserved for goals;
- upcoming committed payments;
- overdraft or credit capacity, shown separately and never counted as wealth.

## Why the current backend needs this

The present application is position-centric:

- `TradeRequest` supplies instrument, currency, quantity, price, commission, and date, but no brokerage account, cash account, or funding legs.
- `TradeService.buy` and `TradeService.sell` mutate a `Position`; they do not withdraw or deposit cash.
- A `portfolio.Transaction` records BUY/SELL and position totals but no cash settlement account.
- A portfolio is currently a grouping/owner of positions, not a place that holds cash.
- The account package represents application users/authentication, not bank/brokerage financial accounts.

Consequences:

- a user can buy unlimited shares without providing funds;
- sale proceeds do not become cash;
- dividends, deposits, withdrawals, bill payments, and account transfers cannot reconcile;
- cash drag, total wealth, liquidity, and “available after commitments” cannot be calculated;
- a future spending feature would become a disconnected second ledger.

This should be solved through the unified activity ledger proposed in [business-logic-and-analytics-design.md](business-logic-and-analytics-design.md), not by adding `cashBalance` to `Portfolio`.

## Not every record changes cash

This boundary is essential:

| Record/action | Changes a real account now? | Why |
|---|---:|---|
| Posted bank/card/cash transaction | Yes | It is an economic fact. |
| Security buy/sell that executed | Yes, subject to trade/settlement state | Security quantity and cash/proceeds/fees must reconcile. |
| Bill issued or subscription renewal expected | No | It creates/updates an obligation and forecast. Payment is separate. |
| Bill paid | Yes | The selected bank/cash/card account is posted and the bill is settled. |
| Planned purchase or shopping-list item | No | It is an intention. |
| Goal allocation | No physical cash movement by itself | It reserves/labels existing money unless an actual transfer occurs. |
| Expected salary/invoice | No | It affects forecast/receivable, not cleared cash. |
| IOU/private loan issued | Usually yes plus a claim | Cash moves and a receivable/payable changes; an opening claim may have no current cash leg. |
| Pending card authorization | Pending/available only | It affects availability but is not yet a cleared posting. |
| Scenario/Decision Replay | No | It is hypothetical and isolated. |
| Statement balance import | Not automatically | It is reconciliation evidence; unexplained differences require review/adjustment. |

## Financial account model

### Avoid the word “account” collision

Keep application identity (`User`, login/account settings) separate from money containers. Name the domain object `FinancialAccount` in Java and `financial_account` in the database.

### Initial account kinds

| Kind | Examples | Natural balance | Can fund an action? | Default negative behavior |
|---|---|---|---:|---|
| `CASH_CURRENT` | Current/checking bank account | Amount owned | Yes | Warn or configured overdraft |
| `CASH_SAVINGS` | Savings account | Amount owned | Yes, if withdrawal is allowed | Disallow for new manual actions |
| `BROKERAGE` | Broker account holding cash and securities | Cash/securities owned; optional margin owed | Yes for trades | Disallow unless margin terms exist |
| `CASH_WALLET` | Physical cash | Amount owned | Yes | Disallow, except reviewed historical correction |
| `STORED_VALUE` | Gift card, prepaid balance, store credit | Restricted amount owned | Only eligible merchants/actions | Disallow |
| `CREDIT_CARD` | Card liability | Amount owed | Yes for purchases, not as cash | May increase to configured limit |
| `OVERDRAFT_LINE` | Linked overdraft/credit line | Amount owed/available facility | Via linked account rules | Configured limit and terms |
| `LOAN` | Mortgage, personal/vehicle loan | Amount owed | Normally receives principal once; not general cash | Schedule/terms control |
| `RECEIVABLE` | Money owed to the user | Amount owed to user | No until repaid | Cannot be spent as cash |
| `PAYABLE` | Money user owes another person/entity | Amount user owes | No | Liability, not a cash source |
| `MANUAL_ASSET` | Property, vehicle, collectible | Estimated amount owned | No | Not applicable |

FT-16 `claim` records remain the canonical detail for person-to-person receivables/payables. `RECEIVABLE`/`PAYABLE` here describe balance-sheet dimensions or internal projection/control accounts; do not force users to create a fake bank-like account for every friend.

Account behavior should use capabilities/policies rather than a large set of scattered `if accountKind == ...` branches. Useful capabilities include:

- `CAN_HOLD_CASH`
- `CAN_HOLD_SECURITIES`
- `CAN_FUND_PURCHASE`
- `CAN_RECEIVE_TRANSFER`
- `CAN_GO_NEGATIVE`
- `HAS_CREDIT_LIMIT`
- `HAS_RESTRICTIONS`
- `REQUIRES_SETTLEMENT`
- `SUPPORTS_MULTIPLE_CURRENCIES`

### Suggested account fields

`financial_account`

- opaque ID;
- owner user/household scope and visibility;
- institution/manual-provider identity;
- display name and optional masked account reference;
- kind/subtype and capability set;
- tracking mode;
- base/display currency and timezone;
- liquidity class (`IMMEDIATE`, `NOTICE`, `LOCKED`, `ILLIQUID`);
- negative-balance/credit policy ID;
- archived/closed dates;
- optimistic version;
- source and last-reconciled metadata.

Tracking modes:

- `FULL_LEDGER`: cash and all activities are tracked/reconciled.
- `BALANCE_SYNC`: provider/manual balances are tracked but activity history may be incomplete.
- `HOLDINGS_ONLY`: securities/manual holdings are tracked; cash is explicitly untracked.
- `MANUAL_VALUE`: periodic appraisal/balance only.

The UI and analytics must never imply full wealth/cash-flow reconciliation for a holdings-only account.

### Multiple currencies

A brokerage or wallet can hold several currencies. Do not store one numeric account balance plus a base-currency label. Use separate cash pockets/dimensions:

`account_cash_pocket(account_id, currency)`

Each pocket has its own native ledger balance and projections. The account's base currency is a display/reporting preference, not a license to erase native currencies.

Examples:

- Brokerage A: TRY 12,000, USD 500, EUR 0.
- Wallet: GBP 30 and EUR 20.
- The portfolio can report a converted total as of a date, but the native balances remain visible.

An action in a currency the account does not hold requires one of:

- an explicit FX conversion activity;
- a provider-executed FX component with both currency legs, rate, spread, and fee;
- a clearly marked untracked external funding leg in holdings-only mode.

Never perform invisible conversion using a current reference rate.

## Activity and account postings

### Activity remains the business fact

One immutable `Activity` describes the user's intent/economic event. It owns typed components and postings. Examples are `SECURITY_BUY`, `BILL_PAYMENT`, `CARD_PURCHASE`, `TRANSFER`, `PRIVATE_LOAN_ISSUED`, or `REFUND`.

Suggested `account_posting` fields:

- opaque ID and activity ID;
- financial account and optional cash pocket;
- currency and exact decimal amount;
- signed `balanceDelta` using the account's documented natural-balance rule;
- posting role (`FUNDING`, `PROCEEDS`, `PRINCIPAL`, `FEE`, `TAX`, `INTEREST`, `REFUND`, `TRANSFER`, `SETTLEMENT`, and so on);
- state (`PENDING`, `POSTED`, `REVERSED`);
- effective/trade time and settlement/cleared time where applicable;
- source/provider reference and reconciliation state;
- correction/reversal link.

Natural-balance convention for the application/API:

- Asset/cash/receivable: positive delta increases value owned; negative reduces it.
- Liability/payable: positive delta increases amount owed; negative repays/reduces it.

Internally, a standard debit/credit representation is also valid if consistently implemented. Do not expose unexplained accounting signs to users. The API should return semantically named `amountHeld`, `amountOwed`, and `delta` with account type.

Security quantity remains a separate exact posting/component:

`security_posting(account_id, instrument_id, quantityDelta, tradePrice, tradeCurrency, ...)`

Fees, tax, interest, discounts, and income remain separate components. They must not be hidden by changing the unit price or principal.

### Activity groups

Some user commands create several real activities. Link them with an `activity_group` without collapsing them:

- bank-to-brokerage transfer followed by security buy;
- credit-card purchase followed weeks later by bank-to-card payment;
- FX conversion followed by a USD stock purchase;
- shared purchase followed later by a friend's reimbursement.

The group makes the workflow understandable, while the separate activities preserve correct dates, balances, and failure behavior.

### Core invariants

- Posted account balances are the ordered sum of posted, non-reversed postings, including any explicit verified `OPENING_BALANCE` activity.
- Every posting has exactly one account and currency/dimension.
- Every activity type defines required posting/component roles and a reconciliation equation.
- Transfers between owned accounts have equal economic value under the declared native/FX rules and do not create income/spending.
- A posted payment cannot settle more bill/claim principal than the allocated outstanding amount unless an explicit overpayment/credit component exists.
- Corrections/reversals preserve the original record.
- Replaying the same activities produces identical account, cash-pocket, position, claim, and liability projections.

## Action-by-action posting behavior

| Action | Account postings | Other components/projections | Spending/income behavior |
|---|---|---|---|
| External cash deposit | Destination cash `+amount` | External-flow/source metadata | Income only if classified as earned/other income; owner contribution may be external flow without income |
| Cash withdrawal | Source cash `-amount`; optional wallet `+amount` | Transfer group if wallet tracked | Transfer if both owned accounts; otherwise cash withdrawal remains unresolved until use is known |
| Owned-account transfer | Source `-amount`, destination `+amount`, fee separately | Linked transfer/group | Neither income nor spending; FX spread/fee is cost |
| Stock/ETF buy | Brokerage cash `-(gross + fees + tax)` | Security quantity `+q`; cost basis increases | Investment acquisition, not consumption spending |
| Stock/ETF sell | Brokerage cash `+(proceeds - fees - tax)` | Security quantity `-q`; realized disposal | Proceeds are not income; gains/income reported by investment logic |
| Dividend/interest | Receiving cash `+net` | Gross income, withholding, fee | Investment income, with withholding separate |
| Bill issued | None | Bill obligation/forecast | No cash movement yet |
| Bill paid from bank/cash | Funding account `-amount` | Bill allocation/settlement | Expense according to bill/purchase; no second expense if already recognized |
| Bill paid by credit card | Card liability `+amount` | Bill settlement | Expense recognized once; bank cash unchanged |
| Credit-card purchase | Card liability `+amount` | Purchase/category/receipt | Consumption spending recognized once |
| Credit-card payment | Bank cash `-amount`; card liability `-amount` | Transfer/debt settlement | Neither new spending nor income |
| Borrow loan principal | Cash account `+amount`; loan liability `+amount` | Loan terms/schedule | Not income |
| Repay loan | Cash `-(principal + interest + fees)`; liability `-principal` | Interest/fee components | Interest/fees are cost; principal is debt reduction |
| Lend to a person | Cash `-principal`; receivable `+principal` | FT-16 claim | Not consumption spending initially |
| Borrow from a person | Cash `+principal`; payable `+principal` | FT-16 claim | Not income |
| Friend reimburses shared purchase | Cash `+amount`; receivable `-amount` | Shared-expense settlement | Not income; original personal spending was only the user's share |
| Refund to bank/card | Cash asset `+amount` or card liability `-amount` | Link to original purchase/item/bill | Reduces/reverses original spending; excess compensation separate |
| Goal allocation | No physical posting unless money actually transfers | Planning allocation/reservation | No income/spending |
| Balance adjustment | Explicit adjustment posting after review | Reconciliation reason/evidence | Separate unexplained adjustment, never silently categorized |

## Funding-source selection

### Manual user flow

For a manual action that moves money:

1. User enters/selects the economic action and amount.
2. Backend returns eligible funding/destination accounts based on ownership, capability, currency, status, and permissions.
3. UI shows each account's native cleared/pending/projected balance, last reconciliation, restrictions, and post-action preview.
4. User selects one source, or intentionally splits funding across sources.
5. Backend previews all account/security/claim/bill effects, FX/fees, warnings, and post-action balances.
6. User confirms.
7. Backend posts atomically with idempotency and account-version/concurrency checks.

Never silently use “the user's active cash.” A remembered default is acceptable only if it remains visible and editable before confirmation.

Useful preferences:

- default daily-spending account by currency;
- default brokerage cash pocket per brokerage account;
- default bill-payment account per contract;
- preferred account for income/refunds;
- saved split-payment pattern.

Defaults are convenience, not routing logic. A default that is closed, restricted, wrong currency, or lacks capacity must not be applied.

### Imported/synchronized actions

An imported transaction already identifies the source account. The user should classify/match it, not choose a fictional alternative funding account. Moving it to another account is a correction that must preserve source provenance.

### Trades

A trade should identify a `brokerageAccountId` and its settlement cash pocket.

- A buy normally deducts from cash already inside that brokerage.
- If the user funds it from a bank, record a bank-to-brokerage transfer and then the trade. A composite UI action may create both, but the ledger keeps them distinct.
- A sell normally credits brokerage cash. A later withdrawal to a bank is a transfer.
- Do not let a sell deposit directly into an arbitrary bank account unless the broker/provider activity truly did so and supplies that settlement behavior.
- If the broker executes FX automatically, record both native legs, execution rate/spread, and fee.

### Bills and purchases

The funding selector can include:

- bank/cash account;
- credit card (increases card liability);
- gift/stored-value account if eligible;
- multiple accounts for split tender;
- a tracked reimbursement/claim allocation, which does not itself fund cash but explains the recoverable share.

A bill can be recorded before payment. Selecting a funding account for a future scheduled payment creates a plan/mandate reference, not an immediate cash deduction.

### API shape

Prefer task-specific commands that emit common ledger activities rather than exposing one giant untyped endpoint:

- `POST /api/v1/transfers/previews` and `/transfers`
- `POST /api/v1/trades/previews` and `/trades`
- `POST /api/v1/bills/{id}/payment-previews` and `/payments`
- `POST /api/v1/purchases/previews` and `/purchases`
- `POST /api/v1/claims/{id}/settlement-previews` and `/settlements`

Common funding input:

```json
{
  "funding": [
    {
      "accountId": "acc_bank_try",
      "currency": "TRY",
      "amount": "1250.00"
    }
  ],
  "effectiveAt": "2026-08-05T10:30:00Z",
  "clientEventId": "01J..."
}
```

Preview response should include:

- account/cash-pocket balance before and after;
- pending and posted effects;
- FX rate/spread/fees and source;
- negative/limit/restriction warnings;
- bill/claim/security effects;
- calculation/policy version;
- account version or short-lived preview token for concurrency validation.

Money and quantity values remain decimal strings in API responses.

## Balance definitions

Do not use one `balance` label for all of these:

| Balance | Definition | Use |
|---|---|---|
| `ledgerBalance` | Sum of posted non-reversed postings as of time | Authoritative reconstructed account history |
| `clearedBalance` | Posted amount confirmed/reconciled by provider/statement policy | Cash confidence/reconciliation |
| `pendingDelta` | Pending authorizations, unsettled trades, or pending transfers | Near-term awareness; not final history |
| `providerAvailableBalance` | Institution-reported withdrawable/spendable amount | Useful evidence; may include overdraft and provider-specific rules |
| `projectedBalance` | Ledger/cleared balance plus selected planned occurrences through a future date | Cash forecast |
| `reservedAmount` | Virtual allocation for goals/tax/protection | Planning constraint, not physical account balance |
| `availableAfterReservations` | Eligible liquid balance minus protected allocations | Planning view |
| `availableAfterCommitments` | Selected liquid balance plus high-confidence inflows minus bills/required payments/reserves | FT-04 decision support, not a guarantee |
| `creditAvailable` | Limit minus debt/holds under provider rules | Borrowing capacity, shown separately from cash and net worth |

Every balance response should state:

- account and native currency;
- requested/actual as-of time;
- posted/pending inclusion policy;
- source and last reconciliation;
- policy/projection version;
- staleness/warnings;
- reporting-currency conversion separately, if requested.

### Dashboard language

Prefer:

- “Cash held”
- “Liquid cash”
- “Reserved”
- “Due in next 30 days”
- “Available after selected commitments”
- “Overdraft used” / “Credit available”

Avoid calling unused credit “active cash,” “money available,” or an asset.

## Negative-balance policy

### Yes, negative balances must be representable

Reality can be negative because of:

- authorized overdraft;
- bank/card fees;
- pending/clearing order;
- margin borrowing;
- missing opening transaction or import gap;
- backdated activity;
- data error awaiting reconciliation.

Rejecting or deleting an imported negative balance would make the ledger less truthful. Allowing every manual account to go negative without controls would make forecasts meaningless. Use explicit policy.

### Suggested policies

`negative_balance_policy`

- `HARD_FLOOR`: new manual/current commands cannot take an asset cash pocket below zero.
- `SOFT_FLOOR`: allow after a clear warning/confirmation and create a policy-breach insight.
- `AUTHORIZED_LIMIT`: allow down to a configured negative limit, with effective-dated overdraft/margin rate and fees.
- `TRACK_REALITY`: record actual/imported history even when negative, but mark reconciliation/policy warnings. Do not offer it casually as a spending preference.
- Liability accounts use their own credit/principal limit policy instead of an asset negative floor.

Suggested defaults:

| Account | Default for new manual actions | Imported/historical reality |
|---|---|---|
| Physical wallet | Hard floor zero | Accept with unresolved/reconciliation warning |
| Savings | Hard floor zero | Accept and flag |
| Current/checking | Soft floor zero until overdraft terms are configured | Accept and flag/attribute overdraft |
| Brokerage cash | Hard floor zero | Accept as unresolved unless margin policy exists |
| Brokerage margin | Authorized limit | Accept and calculate margin liability/interest when data supports it |
| Gift/store value | Hard floor zero | Accept only as correction/dispute warning |
| Credit card | Liability can increase to configured/known limit | Accept provider truth even if over limit; flag |

### Net-worth treatment

If an asset cash account is -100:

- display the account balance as -100 to match the statement;
- report zero positive cash from that pocket and 100 as an overdraft/liability component for gross-assets/liabilities reporting;
- net worth still changes by -100;
- never add the account's unused overdraft/credit limit to assets.

### Manual versus imported behavior

- Imported/synced posted fact: accept, then surface breach/reconciliation issue.
- Manual historical fact: allow with explicit warning/reason; history must be recordable.
- New manual/current action: enforce hard/soft/authorized policy.
- Planned future action: allow scenario/forecast to cross below zero and highlight the first shortfall date; do not post it.

## Pending, trade-date, and settlement behavior

Times that must remain separate:

- authorized/initiated time;
- economic/trade date;
- posting/cleared time;
- settlement date;
- recorded/imported time.

### Practical first release

For manually entered historical trades where settlement details are unavailable, allow a documented policy such as cash effect on trade date. Label it as a simplification.

### Target behavior

- Trade date changes security position/economic exposure.
- A pending/unsettled cash payable/receivable is created.
- Settlement date moves actual brokerage cash and clears the settlement item.
- Pending card authorization affects pending/available views.
- Cleared card transaction becomes the posted purchase/liability activity without duplication.

Do not fill settlement gaps with today's balance or silently move all events to import time.

## Worked examples

### 1. Fund brokerage and buy a share

Starting balances:

- TRY bank: 10,000
- TRY brokerage cash: 0

User transfers 3,000 to brokerage:

- Bank cash: -3,000
- Brokerage cash: +3,000
- Income/spending: 0

User buys shares for 2,000 with 10 fee:

- Brokerage cash: -2,010
- Security quantity: +purchased quantity
- Fee: 10
- Remaining brokerage cash: 990
- Bank remains 7,000

The product can now distinguish invested cost, fee, and idle brokerage cash.

### 2. Pay an issued bill with a credit card

Bill issued for 500:

- Cash: no change
- Open bill: 500
- Forecast: 500 due

User pays using a credit card:

- Credit-card liability: +500
- Bill outstanding: -500 (settled)
- Consumption expense: 500 once
- Bank cash: no change

Later the user pays the card from bank:

- Bank cash: -500
- Credit-card liability: -500
- New spending: 0

### 3. Shared purchase and friend repayment

User pays a 900 hotel bill on a card, split equally with a friend:

- Card liability: +900
- User travel spending: 450
- Receivable from friend: +450

Friend later pays 450 into user's bank:

- Bank cash: +450
- Receivable: -450
- Income: 0

### 4. Checking account becomes negative

An imported fee takes a checking account from 20 to -30:

- Accept the posted fact.
- Show account statement/ledger balance -30.
- Report 30 overdraft liability for gross balance-sheet presentation.
- Create policy/reconciliation warning if no overdraft terms exist.
- Do not claim the user's available cash is 0 plus an unused credit limit.

### 5. Buy a USD security from TRY cash

Do not deduct a USD cost directly from a TRY balance.

Record either:

1. TRY cash -> USD cash FX conversion with executed/reference rate, spread, and fee; then USD cash -> security buy; or
2. one provider-executed composite group containing explicit TRY debit, USD settlement/security cost, FX terms, and fees.

The result must be reproducible without today's FX rate.

### 6. Split-tender shopping purchase

A 1,200 purchase uses 200 gift card and 1,000 bank card:

- Stored-value account: -200
- Bank/card account: -1,000 or card liability +1,000
- Purchase/receipt: 1,200
- Spending: 1,200 once

Funding legs sum to the purchase total; they do not create two purchases.

## Concurrency, idempotency, and atomicity

Financial-account selection creates new race conditions. Required controls:

- client event ID/idempotency key for every posting command;
- lock or optimistic-version check for all affected accounts/projections;
- acquire multiple account locks in deterministic account-ID order;
- preview includes account versions or a short-lived token;
- if a balance/policy changes before commit, recalculate and return a stable conflict/problem response;
- all postings, components, bill/claim allocations, and outbox/rebuild markers commit atomically;
- retry after transient conflict replays against the latest ledger;
- duplicate mobile/offline retries create one economic event.

Suggested problem codes:

- `INSUFFICIENT_FUNDS`
- `ACCOUNT_LIMIT_EXCEEDED`
- `ACCOUNT_VERSION_CONFLICT`
- `ACCOUNT_CURRENCY_UNSUPPORTED`
- `ACCOUNT_RESTRICTED_FOR_ACTION`
- `FUNDING_ALLOCATION_MISMATCH`
- `SETTLEMENT_AMOUNT_EXCEEDED`
- `RECONCILIATION_REQUIRED`

An insufficient-funds response should include allowed alternatives/warning facts, not leak private household accounts the caller cannot view.

## Reconciliation and adjustments

For every full-ledger cash account, support statement reconciliation:

- statement/provider account and currency;
- opening/closing dates and balances;
- cleared transaction count/amount;
- unmatched/missing/duplicate activities;
- pending exclusions;
- difference and final status;
- source document/provider snapshot.

Do not “fix” a difference by silently replacing the computed balance. If the user accepts an adjustment:

- create an explicit `RECONCILIATION_ADJUSTMENT` activity;
- record date, amount, reason, source, and user;
- classify it as unexplained until resolved;
- allow a later correction that replaces/reverses it.

Account deletion should normally become archive/close when financial history exists. A closed account can no longer fund new actions but remains in history and reports.

## Suggested projections

Keep source facts immutable and build:

- current cash balance by account/currency;
- pending and cleared balance;
- daily/end-of-period account balance;
- account liquidity/reservation/commitment view;
- liability and credit utilization;
- user/household cash aggregation by currency;
- first projected shortfall date;
- funding-option read model.

Projection rows contain last applied ordering key/version so they can be rebuilt after backdated activity or correction.

Do not cache authorization-sensitive household totals without owner/visibility scope in the cache key.

## Migration from current portfolios

Do not invent historical cash for existing users.

1. Add the financial-account/activity/posting schema alongside current positions/transactions.
2. Create a placeholder brokerage financial account for each current portfolio, or let the user attach portfolios to a brokerage account during migration.
3. Mark migrated accounts `HOLDINGS_ONLY` and cash `UNTRACKED` by default.
4. Backfill current BUY/SELL facts as security/activity records with known quantity/price/date/currency.
5. Preserve the cash amount implied by price × quantity only as an untracked/external funding component until reconciled; do not claim a real cash balance.
6. Surface known commission/currency ambiguity from the current data instead of fabricating values.
7. Build new projections in shadow mode and reconcile positions with the current system.
8. Let a user opt into `FULL_LEDGER` by entering/importing an opening statement balance and subsequent cash activities.
9. Once reconciled, require/select actual brokerage cash for new trades in that account.
10. Retire current position-only writes only after parity and backdated/concurrency tests pass.

Portfolio and account remain different:

- **Financial account:** where an institution/wallet actually holds or owes value.
- **Portfolio:** a reporting grouping over one or more accounts/holdings.
- **Dashboard:** a presentation grouping.

A user might have two brokers and a pension in one “Retirement” portfolio, or one broker divided across several goal portfolios without duplicating the underlying account.

## Test strategy

### Golden action fixtures

- cash deposit/withdrawal and owned transfer;
- transfer with fee;
- bank-to-broker funding plus buy;
- buy/sell/dividend with fees/tax and cash settlement;
- bill issue then bank payment;
- bill paid by card then card paid by bank;
- loan borrow/principal/interest payment;
- personal lending and partial repayment;
- shared purchase/reimbursement;
- refund to original card and store credit;
- gift card plus card split tender;
- FX conversion plus foreign-security purchase;
- overdraft fee and negative account;
- pending-to-cleared matching;
- backdated insertion and correction;
- simultaneous actions against the same funding account.

### Invariants

- Replaying activities twice produces identical balances.
- Input insertion order cannot alter economic-order result.
- Owned transfer does not change household net worth, income, or spending except explicit fee/FX effect.
- Card purchase plus card payment records spending once.
- Loan principal receipt is not income; principal repayment is not consumption.
- Personal-loan principal remains cash plus claim/liability neutral to net worth at issuance, subject to valuation confidence.
- Funding legs exactly equal the required cash amount plus/minus declared fees/refunds.
- Reversal restores prior projections.
- Hard-floor/manual commands cannot commit below the floor.
- Imported negative truth is retained and flagged.
- Credit available never enters gross assets/net worth.
- Same idempotency key produces one economic activity.

### Database/integration tests

Use PostgreSQL Testcontainers for:

- account/posting constraints and exact numeric behavior;
- atomic multi-account posting;
- lock ordering and concurrent insufficient-funds behavior;
- idempotency uniqueness;
- backdated projection rebuild;
- owner/household access and aggregate privacy;
- empty-to-latest and upgrade migration.

## Recommended first vertical slice

After the current security/database/accounting repair gate:

1. `FinancialAccount` for one-currency bank cash and brokerage accounts.
2. Full-ledger versus holdings-only tracking mode.
3. Cash deposit/withdrawal and owned-account transfer.
4. Account posting projection with ledger/cleared balance.
5. Trade buy/sell cash legs using selected brokerage cash.
6. Hard-floor and soft-floor negative policy.
7. Funding preview with post-action balance and idempotent commit.
8. Statement reconciliation and explicit adjustment.
9. Cash/positions/net-worth read model with untracked-cash coverage warning.

Defer in the first slice:

- margin and sophisticated trade settlement;
- payment initiation;
- automatic overdraft interest;
- multi-source split tender;
- card statement and BNPL policies;
- shared-account household permissions beyond owner-only;
- provider bank sync.

Those extensions use the same postings once the simple path is proven.

## Product acceptance criteria

The feature is ready to call “cash tracking” only when:

- a user can create multiple cash/brokerage accounts and see native balances;
- every new full-ledger trade identifies where cash came from or proceeds went;
- bank/card/brokerage transfers do not distort income or spending;
- bill/card/loan/personal-debt examples reconcile end to end;
- available, pending, reserved, committed, overdraft, and credit amounts are not mislabeled;
- negative-balance behavior is explicit and tested per account;
- imported reality is preserved even when it violates a manual spending rule;
- backdated/corrected activity rebuilds deterministic balances;
- concurrent/retried requests cannot overspend silently or duplicate postings;
- existing users remain honestly labeled holdings-only until they reconcile cash.

## Bottom line

The user should choose **which real account funds a posted action**, and the system should derive every affected balance from ledger postings. Multiple accounts and negative balances are both necessary.

The guardrail is equally important: not everything deducts money immediately. A bill can be due, a goal can reserve money, an invoice can be expected, and a scenario can model a purchase without changing cash. Only a posted economic activity changes a real account.

This model gives the project a common foundation for investments, spending, bills, subscriptions, credit cards, personal IOUs, refunds, income, and future mobile entry without letting those features disagree about how much money the user actually has.
