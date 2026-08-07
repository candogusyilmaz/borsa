# Product direction and differentiation

For the implementation-level feature catalogue, backend/API implications, acceptance criteria, and validation plan, see [implementable-features.md](implementable-features.md).

## Product thesis

The project can grow beyond a stock tracker without becoming an unfocused “everything finance” app if every feature supports one promise:

> Help people understand the consequences of their financial decisions in the currency, purchasing power, risk, and goals that matter to them.

The application should still track portfolios well, but tracking becomes the factual input to a **personal decision intelligence** product.

## Competitive baseline

This is a lightweight, current feature check, not a complete market study. Official product/help pages were reviewed on 2026-08-04.

- [getquin](https://www.getquin.com/portfolio-tracker/) already presents aggregated net worth, broad asset coverage, broker/manual import, costs/taxes/dividends, time-weighted return, allocation, benchmarks, fund look-through, watchlists, and community features.
- [Snowball Analytics](https://snowball-analytics.com/pricing) already lists fees/dividends/taxes in performance, IRR, benchmarking, risk metrics, rebalancing, dividend forecasting, custom assets, cash, broker/report import, long backtests, and combined portfolios.
- [Sharesight](https://help.sharesight.com/us/performance_report/) already emphasizes date-range performance reports; its [portfolio help](https://help.sharesight.com/ca/show_portfolio/) covers growth drivers and benchmark comparisons.
- Open-source [Portfolio Performance](https://help.portfolio-performance.info/en/reference/view/reports/performance/dashboard/) already supports TWR, IRR, contribution/performer views, heatmaps, risk/fee/tax metrics, and reporting-period controls.
- Standalone tools also compare cash with investing. Vanguard has a [holding cash versus investing tool](https://investor.vanguard.com/tools-calculators/potential-growth-tool), and other calculators compare purchases with stocks, gold, currencies, or inflation.

Therefore, these are important parity features but weak primary differentiation:

- total portfolio value and P&L;
- allocation pie charts;
- ordinary benchmark comparison;
- dividend calendar;
- generic “AI insights”;
- a one-input “if you bought Apple/Bitcoin” calculator;
- basic multi-asset manual tracking.

The opportunity is to connect **the user's real history** to **realistic alternatives**, local rules, goals, and explainable outcome decomposition.

## Recommended flagship: Decision Replay

### Core job

Let a user select a real decision or cash-flow sequence and ask:

- What if I had bought a different share or index?
- What if I had converted to USD/EUR instead?
- What if I had used a term deposit with the rates and tax rules available then?
- What if I had held gold, repaid debt, or waited one month?
- What if I had invested the same monthly amounts rather than one lump sum?
- Which choice protected my purchasing power or reached my goal sooner?

### Why it can be distinctive

The defensible version is not a calculator page. It is integrated with:

- actual dated cash flows and holdings;
- historical prices, FX, rates, inflation, dividends, and corporate actions;
- fees, spreads, tax/withholding assumptions, and market calendars;
- personal goals and a personal cost-of-living basket;
- risk/path comparison, not just ending value;
- a decision journal and later outcome review;
- transparent sources and calculation versions.

It can answer “the stock won by 20%” and the more useful “the stock finished higher, but the deposit was ahead for 14 of 18 months, had no drawdown, and better matched the date of your home deposit.”

### First usable slice

Keep the first release narrow:

1. One starting amount/date/currency and one ending date.
2. Compare up to four alternatives: one market instrument, one currency, gold/index, and a simple fixed-rate deposit.
3. Use adjusted total-return data and historical FX.
4. Show ending value, opportunity difference, real value after one inflation series, max drawdown, and a timeline.
5. Show assumptions/data sources beside the result.
6. Save/share the scenario privately.

Then add replay of the user's actual multiple cash flows, actual bank-rate schedules, debt repayment, taxes, and personal baskets.

## Related differentiated features

### 1. “Explain my return”

Reconcile wealth change into:

- asset price;
- FX;
- dividends/interest/rent;
- fees and taxes;
- contribution/withdrawal timing;
- unpriced/data changes.

Users often see a return number but cannot tell whether they picked a good asset, benefited from currency movement, or simply added more money. The decomposition is useful, testable, and harder to fake with a generic dashboard.

### 2. Decision journal with outcome review

When recording an activity, optionally capture:

- reason/thesis;
- expected holding period;
- target and risk limit;
- alternatives considered;
- confidence and information source;
- whether the decision followed a plan.

At 1/3/6/12 months, compare expectation, actual result, and the saved alternatives. Highlight calibration and repeated patterns without pretending to predict markets.

### 3. Personal purchasing-power index

Allow users to define recurring items or goals such as rent, a home deposit, tuition, travel, vehicle cost, or a basic consumption basket. Translate net worth/returns into those units alongside official inflation.

This makes a 40% nominal return meaningful in high-inflation or multi-currency lives.

### 4. Goal and liability matching

Start with the future obligation rather than the asset:

- emergency reserve in months of spending;
- house deposit in the house's currency/index;
- tuition due dates;
- retirement income;
- mortgage/debt schedule.

Show funding probability/range only when assumptions support it. Initially, deterministic historical replays and conservative scenarios are safer than probabilistic forecasts.

### 5. Data confidence as a feature

Show which parts are live, delayed, manually valued, stale, missing, or estimated. Let users filter aggregates by confidence. A financial tool that says “92% valued; two assets are stale” earns more trust than one that silently substitutes cost.

### 6. Privacy-first statement inbox

Turn the current AI importer into a controlled ingestion workflow:

- deterministic parsers first;
- on-device/client redaction where possible;
- explicit consent before external AI processing;
- row-level source provenance and confidence;
- duplicate detection and reconciliation totals;
- review/approve before posting;
- delete original document and derived data independently;
- provider retention disclosure.

Privacy and auditability can differentiate more than an “AI” badge.

### 7. “Time in front” and path comparison

Terminal value hides lived experience. For two alternatives, show:

- percentage of days/months each was ahead;
- worst drawdown and time to recover;
- longest losing period;
- when their paths crossed;
- result at the user's actual goal date.

This helps a user understand whether an alternative's higher return required risk they could realistically tolerate.

### 8. Household and shared decisions

Later, support private household spaces with scoped permissions:

- mine/yours/shared accounts;
- household goals and liabilities;
- view-only accountant/adviser access;
- scenario comments/approvals;
- privacy controls that can hide exact balances while sharing allocation or progress.

Avoid a public social feed as an early differentiator; it adds moderation, privacy, and incentive problems before the accounting foundation is mature.

## Broader assets and “other stuff”

Add assets in an order that reuses the ledger/valuation engine.

### High-value next classes

1. **Cash accounts and term deposits** — essential for the user's comparison idea and complete portfolio reconciliation.
2. **Currencies and FX conversions** — both an asset and a return component.
3. **ETFs/funds/indices** — common benchmark and holdings types; later add look-through exposure.
4. **Gold/precious metals/commodities** — quoted in different currencies/units; good counterfactual candidates.
5. **Dividends, bonds, coupons, and interest** — make total return and income planning real.
6. **Crypto** — existing subtype foundation, but needs wallet/exchange, staking/yield, fees, and 24/7 valuation rules.
7. **Debt/liabilities** — loan prepayment is often the most realistic alternative investment.
8. **Pensions/retirement wrappers** — contribution/tax/lockup rules matter more than a price ticker.
9. **Property and private/manual assets** — low-frequency appraisals, rent, expenses, debt, and confidence ranges.
10. **Vehicles and durable physical assets** — lifecycle costs, usage meters, maintenance/warranty state, financing, depreciation, and disposal; see [real-asset-lifecycle-tco-design.md](real-asset-lifecycle-tco-design.md).
11. **Collectibles/private businesses** — manual valuations with conservative liquidity/fee assumptions and specialist models.

### Do not pretend all assets are stocks

- Deposits accrue according to rate/day-count/term rules.
- Bonds have coupons, maturity, accrued interest, and duration.
- Property has rent, expenses, debt, transaction costs, and appraisal uncertainty.
- Debt produces avoided interest, not market-price return.
- Personal property can depreciate and has a bid/ask/liquidity discount.
- A physical asset's cash paid, economic cost including depreciation, current net value, and forecast ownership cost are different views; never add purchase price, loan principal, and depreciation into one total.

A shared `Instrument` label is fine, but valuation/activity subtypes need real behavior.

## Parity features users will still expect

These are not the brand story, but the product eventually needs them:

- reliable CSV/broker imports, transaction correction, export, and backup;
- cash, dividends, fees, taxes, splits, transfers, and recurring activities;
- watchlists and price/data-staleness alerts;
- performance over arbitrary periods with TWR and XIRR;
- benchmarks in the same currency with dividends aligned;
- allocation/exposure by asset, issuer, currency, country, sector, and account;
- target allocation and contribution-aware rebalancing;
- income calendar and goal progress;
- search, pagination, tags, notes, and saved views;
- full account/data deletion and portable exports;
- accessibility, offline-tolerant mobile entry, and clear error recovery.

## Product principles

### Trust before breadth

Do not ship more asset types until the ledger can express their cash flows and their valuations state uncertainty. A smaller product with correct, auditable results is more valuable than broad coverage with silent approximations.

### Explain, do not merely score

Prefer “62% of your return came from TRY depreciation against USD” over a mysterious “Portfolio health: 74.” If AI is used, it should explain calculations already produced by deterministic services and link every statement to data.

### Local depth, global model

The current BIST/TRY direction can become an advantage: term-deposit tax/renewal behavior, high-inflation purchasing power, and FX/gold comparisons are meaningful. Keep the core policies pluggable so another country can supply its own tax, inflation, rates, and calendars.

Potential authoritative time-series sources should be adapter-based. For example, the ECB publishes reference rates and an [SDMX API](https://data.ecb.europa.eu/help/getting-data-web-services-sdmx-0); TCMB EVDS provides Turkish time-series access (see its [official Python access guide](https://evds2.tcmb.gov.tr/help/videos/User_Guide_to_Access_EVDS_Data_by_Using_Python.pdf)). Source license, revision, and reference-vs-execution-rate limitations must be retained.

### No hindsight deception

Historical comparisons are educational, not recommendations. Show survivorship/data limitations, distinguish price from total return, state that an alternative may not have been accessible to the user, and never use data published after the simulated decision date.

### No guilt mechanics

Opportunity cost can become manipulative. Let users mark a purchase/experience as worth it, compare only when useful, and avoid notifications designed to shame spending.

## Feature priority table

| Capability | User value | Foundation required | Suggested priority |
|---|---:|---|---:|
| Correct fees, ledger replay, corrections | Very high | Database/tests | Now |
| Cash and FX activities | Very high | Ledger | Now/next |
| Historical prices and FX | Very high | Market-data store | Next |
| Explain-my-return decomposition | Very high | Ledger + valuation history | Next |
| Decision Replay MVP | Differentiating | Historical data + scenario core | Next flagship |
| TWR/XIRR and honest benchmarks | Expected/high | Daily NAV + flows | Next |
| Personal inflation/goal basket | Differentiating | Index series + goals | After MVP |
| Deposit and debt alternatives | Differentiating | Policy engines | After MVP |
| Dividends/corporate actions | Expected/critical | Ledger + reference data | Early |
| Mobile offline entry/sync | High | Stable API/idempotency | After API foundation |
| Generic AI commentary | Low without trust | Deterministic analytics | Later |
| Public social/community | Unclear/high risk | Privacy/moderation | Much later or never |

## What not to build yet

- Automated buy/sell recommendations or price predictions.
- Complex portfolio optimization based on unreliable/missing history.
- A marketplace of bank products before rate terms can be modeled/audited.
- Public leaderboards that reward risky behavior.
- Tax filing claims before jurisdictional policies and professional review exist.
- Microservices, streaming infrastructure, or a data warehouse simply because the roadmap is ambitious.

## Success measures

Early metrics should reward trust and insight, not trading frequency:

- percentage of portfolios that fully reconcile;
- percentage of value with fresh/known-quality prices and FX;
- import rows auto-matched versus explicitly reviewed, with zero silent skips;
- calculation discrepancy/error reports;
- scenarios saved/revisited and decision reviews completed;
- users who can identify the main driver of their return after viewing decomposition;
- mobile mutations safely recovered after offline/retry conditions;
- export/delete requests completed correctly.
