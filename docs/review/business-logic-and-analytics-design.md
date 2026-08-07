# Target business logic and analytics design

## Goal

The application should be able to answer three questions separately and correctly:

1. **What happened?** Reconstruct holdings, cash, income, fees, taxes, and liabilities from immutable facts.
2. **Why did my wealth change?** Separate market return, FX, income, fees/taxes, and external cash flow.
3. **What would have happened under another decision?** Replay the same opportunity and cash-flow timeline through alternative strategies without look-ahead bias.

The current position-centric model can answer a simplified version of the first question for in-order stock trades. The target below is an incremental “ledger plus projections” design, not a recommendation to rewrite the application as distributed event sourcing.

## Non-negotiable accounting rules

Write these as an architecture decision record before implementation:

- Every activity has an **effective time** (economic time), a **recorded time**, a source timezone/market date where relevant, and a deterministic ordering key.
- Original facts are immutable. Corrections create a replacement/reversal relationship and rebuild projections; they do not silently rewrite history.
- Money always includes a currency. Quantity always includes an instrument/unit. Never add unlike currencies without an explicit point-in-time conversion.
- Store raw fee, tax, accrued interest, and withheld amounts separately from price and quantity.
- Use one declared rounding policy per operation; do not round intermediate analytics to display precision.
- Current positions, lots, daily NAV, and statistics are rebuildable projections, not the only record of truth.
- A valuation is always “as of” a timestamp and states its data coverage/quality.
- Actual results, historical counterfactuals, and future assumptions are different data products and must be labeled differently.

## Ledger model

### Account

The detailed multi-account cash, funding-source, account-posting, and negative-balance behavior is specified in [cash-accounts-and-funding-design.md](cash-accounts-and-funding-design.md).

The vehicle-first model for physical-asset identity, acquisition/finance links, usage meters, service/warranty history, valuation, depreciation, disposal, and cost per unit is specified in [real-asset-lifecycle-tco-design.md](real-asset-lifecycle-tco-design.md).

An account is where value is held or owed. Suggested kinds:

- Brokerage and pension account.
- Bank cash/current account.
- Term deposit/savings account.
- Crypto wallet/exchange.
- Physical asset custody/manual account.
- Property/collectible/manual valuation account.
- Credit card, mortgage, or other liability account.

Useful fields include owner/household, portfolio grouping, institution, base currency, tax wrapper, timezone, archived status, and data source. A portfolio becomes a reporting grouping over accounts/activities rather than the only owner of a position.

### Activity

Use one activity envelope with typed details instead of forcing every financial event into BUY/SELL:

- `CASH_DEPOSIT`, `CASH_WITHDRAWAL`.
- `BUY`, `SELL`.
- `DIVIDEND`, `INTEREST`, `RENT`, `COUPON`, `OTHER_INCOME`.
- `FEE`, `TAX`, `WITHHOLDING_TAX`.
- `FX_CONVERSION`.
- `TRANSFER_IN`, `TRANSFER_OUT` linked as one group.
- `STOCK_SPLIT`, `REVERSE_SPLIT`, `SPINOFF`, `MERGER`, `RIGHTS_ISSUE`, `RETURN_OF_CAPITAL`.
- `ASSET_REVALUATION` for manual/non-market assets.
- `BORROW`, `REPAY_PRINCIPAL`, `PAY_INTEREST` for liabilities.

Core envelope fields:

- opaque ID and user/account ownership;
- `effectiveAt`, `recordedAt`, timezone/market date;
- type and lifecycle state (`POSTED`, `PENDING`, `REVERSED`);
- source (`MANUAL`, `CSV`, `PDF_AI_PREVIEW`, `BROKER_SYNC`, `SYSTEM`);
- external/client ID and import fingerprint for idempotency;
- correction/reversal/group IDs;
- notes/tags and immutable source provenance;
- schema/calculation version.

For a trade, store instrument, side, quantity, unit price, trade currency, gross amount, each fee/tax component, settlement date, and cash account. Prefer explicit monetary components over a single ambiguous commission number.

### Cash legs and reconciliation

A practical middle ground is to derive double-entry-like legs for each posted activity:

- Buy: security quantity increases; cash decreases by gross price + fees + taxes.
- Sell: security quantity decreases; cash increases by proceeds - fees - taxes.
- Dividend: cash increases; income and withholding are separately visible.
- FX conversion: one cash currency decreases and another increases, with spread/fee captured.
- Transfer: one account decreases and another increases; it is not portfolio performance.

The invariant is that every posted activity reconciles its value movement. Users can still operate a simplified “holdings only” mode, but the system should explicitly mark cash as untracked rather than imply full net worth.

## Position and cost-basis projection

### Deterministic replay

For every affected `(account, instrument)`:

1. Load posted activities ordered by `effectiveAt`, then source sequence/client ID, then immutable ID.
2. Apply splits/corporate actions on their effective dates.
3. Validate that quantity never becomes negative unless short selling is explicitly supported.
4. Build current quantity, lots, remaining basis, and realized disposals.
5. Persist a versioned projection in the same transaction or via a reliable outbox/job.

On a backdated insert or correction, replay from the earliest affected point. A full replay is initially simpler and safer; checkpoints can optimize large accounts later.

### Cost-basis policies

Keep at least two views:

- **Economic performance basis:** a consistent policy for understanding investment outcome.
- **Tax basis:** jurisdiction/account-specific rules used only for tax reporting.

Candidate policies are weighted average, FIFO, LIFO, and specific identification. The policy and version must appear on realized-gain output. A disposal allocation should preserve which acquisition lots and fee amounts it consumed.

### Projection concurrency

- Add a database unique key for position identity.
- Add optimistic versioning or acquire a row/advisory lock for replay.
- Accept a client event ID/idempotency key.
- If two writes race, one retries the replay against the new ledger; neither silently overwrites state.

## Reference and market-data model

### Instruments

Use a common instrument identity plus subtype metadata. Useful classes:

- Equity, ETF, mutual fund, index.
- Bond/fixed income.
- Currency and cash.
- Crypto/token.
- Commodity/metal.
- Deposit product/rate strategy.
- Pension/annuity.
- Real estate, vehicle, art/collectible, private asset.
- Liability/debt.
- User-defined index or consumption basket.

Not every class needs live prices. Each instrument should declare its valuation method: market observation, accrued formula, amortization schedule, linked index, user appraisal, or outstanding-principal schedule.

Do not model the same ticker as globally unique. Identity may require market/exchange, ISIN or provider IDs, share class, quotation currency, and effective symbol history.

### Immutable observations

Replace “latest snapshot only” as the primary store with immutable series such as:

`price_observation`

- instrument, quotation currency;
- observation time/trading date;
- open/high/low/close/adjusted close as available;
- provider and provider symbol;
- adjustment status, revision/version;
- ingestion run and quality flags.

`fx_observation`

- base currency, quote currency;
- observation time/date;
- rate type (reference, market close, user execution);
- provider, revision, quality.

`index_observation`

- inflation, policy/deposit rates, total-return indices, or user baskets;
- frequency and period;
- publication/vintage date to avoid look-ahead in historical simulations.

Keep a current/latest projection for fast screens, but build it from observations.

### Corporate actions and total return

Historical comparison must use split-adjusted quantities and dividends. Either ingest adjusted total-return series with a documented methodology or ingest corporate actions and calculate the series. Never compare an actual portfolio including dividends with a price-only benchmark without saying so.

### Data quality contract

Every valuation/analysis response should carry:

- requested and actual as-of timestamp;
- provider/source;
- stale threshold and oldest observation;
- priced/unpriced amounts and position count;
- fallback/interpolation rule;
- revision/calculation version.

This turns data quality into a visible product feature rather than an invisible liability.

## Valuation engine

The valuation service should be the only place that turns quantities and cash into reporting-currency values.

Inputs:

- scope: user, household, portfolio, account, or selected assets;
- `asOf` timestamp;
- reporting currency;
- price/FX source policy;
- inclusion policy for pending/unpriced/manual assets.

Outputs:

- position-level native and converted value;
- cash and liability value;
- gross assets, liabilities, and net worth;
- total/remaining cost basis under a named policy;
- unrealized P&L;
- data-quality summary.

Persist daily/end-of-period NAV projections for charts, but make them rebuildable from ledger and market data. Recalculate affected dates after a backdated activity, price correction, corporate action, or FX revision.

## Performance analytics

### Do not use one “return” for every question

- **Absolute P&L:** useful reconciliation in currency units.
- **Simple return:** useful for one holding without intermediate flows.
- **Money-weighted return / XIRR:** answers how the user's timed cash flows performed.
- **Time-weighted return (TWR/TTWROR):** removes the effect of contributions/withdrawals to evaluate the investment strategy.
- **Real return:** deflates nominal wealth by an inflation index or personal basket.
- **Benchmark-relative return:** compares date-aligned total-return series in one reporting currency.

Expose the method in names and metadata. A dashboard label such as “+12%” is incomplete without period, flow treatment, currency, and methodology.

### Return decomposition

For a selected period, reconcile opening wealth to closing wealth:

- external contributions/withdrawals;
- asset price return;
- FX return;
- dividends/interest/rent;
- realized/unrealized movement;
- fees and taxes;
- residual/data effect.

The residual should be near zero. A nontrivial residual is a monitoring alert, not a hidden UI rounding difference.

This decomposition is both useful to users and an excellent accounting integrity test.

### Risk and behavior metrics

Once daily valuations are reliable:

- volatility and downside deviation;
- maximum drawdown and recovery time;
- Sharpe/Sortino with an explicit risk-free series;
- concentration by asset, issuer, currency, country, sector, and look-through fund holdings;
- turnover, fee drag, tax drag, cash drag;
- win/loss statistics that are lot-aware and not substitutes for total return;
- timing effect: actual cash-flow dates versus a systematic schedule;
- behavior cohorts such as panic selling, averaging down, or performance chasing, phrased descriptively rather than as financial advice.

## Counterfactual “Decision Replay” engine

### User question

“What would have happened if, on the dates I used money for X, I had instead put it into Y?”

This is more general than comparing one lump sum. A replay uses the user's actual sequence of contributions, purchases, sales, and withdrawals.

### Scenario input

Suggested contract:

- scenario name and owner;
- evaluation start/end and reporting currency;
- one or more source cash flows `(effectiveAt, amount, currency)`;
- actual strategy or linked real account/portfolio;
- alternative strategies;
- execution rule (same-day close, next market open, periodic rate, custom price);
- dividend/income reinvestment rule;
- fee, spread, withholding/tax policy;
- FX conversion rule;
- missing-data/holiday rule;
- inflation/personal-basket deflator;
- assumptions and data vintages.

### Alternative strategy types

1. **Market instrument:** stock, ETF, index, fund, crypto, gold/commodity.
2. **Hold currency:** convert each flow at the historical rate and hold cash.
3. **Deposit/interest:** fixed or variable rate, term, day-count convention, compounding, withholding, renewal, early-break rule.
4. **Debt repayment:** saved interest and changed amortization schedule, including prepayment fees.
5. **Asset basket:** an allocation with optional rebalancing.
6. **Inflation/purchasing power:** CPI or a user-created basket.
7. **Manual real asset:** known purchase/appraisal/sale observations with explicit low-frequency quality.

### Fair comparison rules

- Use the same external cash-flow dates and amounts for every alternative.
- Convert at point-in-time rates, never today's FX.
- Use information and product rates that were actually available at the time; retain publication vintage where possible.
- Include dividends, corporate actions, fees, spreads, and taxes according to declared policy.
- Use total-return benchmarks when comparing with a portfolio that receives income.
- Do not fill missing market days with a future price. Apply a declared prior-close/next-open rule.
- Separate historical fact from assumed future return.

### Scenario output

Return more than terminal value:

- ending nominal and real value;
- opportunity-cost difference versus actual;
- CAGR, XIRR and TWR where meaningful;
- volatility, maximum drawdown, worst period, recovery time;
- fees, taxes, income, and FX contribution;
- percentage of days/months each alternative was ahead;
- timeline with important crossover events;
- data coverage, assumptions, and warnings;
- a reproducible calculation/version identifier.

### Example without hidden assumptions

A user records a TRY 100,000 decision on 2024-01-15 and compares:

- the share they bought;
- rolling TRY term deposits;
- converting to USD and holding it;
- gold;
- repaying part of a loan.

The comparison cannot be computed from a single current price. It needs the share's adjusted total return, historical TRY/USD observations, the chosen deposit-rate series or actual bank quote, deposit tax/renewal rules, gold quotation/FX convention, and the loan amortization schedule. The UI should let the user inspect those assumptions rather than present one magical number.

### Calculation implementation

Keep scenario definitions immutable/versioned. Execute them with a pure calculation core that receives already-resolved time series and policies. This makes golden tests straightforward and prevents database/provider behavior from changing math silently.

Cache results by `(scenario version, data-series revisions, calculation version)`. When a source revises history, mark prior results stale and recalculate without losing the old audit record.

## Personalized purchasing power

Headline CPI is useful but may not represent a user's life. A differentiated capability is a private “my basket”:

- rent/mortgage, utilities, food, transport, education, healthcare, subscriptions, and user-defined items;
- quantity/unit and observed price over time;
- official category index fallback when personal observations are missing;
- nominal wealth translated into months/units of the user's goal or basket.

Examples:

- “Your portfolio rose 35% in TRY, 12% after official CPI, and 4% against your home-deposit goal.”
- “Holding USD beat the deposit in nominal TRY but had a larger drawdown and did not beat your rent basket over this interval.”

Avoid shame-based “your coffee cost a fortune” messaging. The feature should support intentional choices, including deciding that an experience was worth its opportunity cost.

## Calculation test strategy

### Golden fixtures

Create small hand-worked portfolios checked in as fixtures:

- one buy/one sell with fees;
- multiple buys and partial sell under weighted average and FIFO;
- full close and reopen;
- same-timestamp activities with deterministic source ordering;
- backdated insert and correction;
- split plus dividend plus sale;
- two currencies with historical FX;
- contribution during a day to distinguish NAV change and TWR;
- missing/stale price;
- negative return, previous-period loss, and zero denominator;
- deposit with different compounding/day-count/withholding policies;
- debt prepayment scenario.

Assert ledger balances, lots, NAV, realized/unrealized P&L, cash, TWR, XIRR, and decomposition residual.

### Property/invariant tests

- Replaying the same ledger twice produces the same projection.
- Insertion order does not change a projection when economic ordering keys are unchanged.
- Reversing an activity and replaying equals a ledger without that activity.
- Gross assets minus liabilities equals net worth.
- Opening value + flows + decomposed P&L equals closing value within decimal tolerance.
- Converting A→B→A at reciprocal rates returns the starting amount within the declared rounding policy.
- Idempotent retries produce one activity.

### Database tests

Use PostgreSQL Testcontainers, not an in-memory substitute, because the application depends on schemas, JSONB, named enums, generated columns, partial indexes, PL/pgSQL, and numeric behavior.

## Incremental migration from the current model

1. Freeze and characterize current trade behavior with tests.
2. Fix key/security and database bootstrap independently.
3. Introduce ledger activity tables alongside existing transactions.
4. Backfill each current transaction as an activity, flagging commission/currency ambiguities.
5. Build a deterministic position projection and compare it with current positions in shadow mode.
6. Reconcile differences per user/position; never silently overwrite unexplained differences.
7. Switch writes to ledger + projection and make old transaction APIs adapters.
8. Add cash legs and new activity types.
9. Add immutable price/FX observations and daily valuation projections.
10. Build performance and Decision Replay on the same valuation core.

This sequence lets the existing UI continue while correctness infrastructure is introduced behind it.
