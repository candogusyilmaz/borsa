# Implementable feature catalogue and differentiation plan

Review date: 2026-08-05

Status: product and engineering proposal. It is not financial, tax, or legal advice, and it does not imply that every feature should be built.

## Executive recommendation

Do not turn the project into a stock tracker with a budget tab. Both categories are crowded, and a long feature checklist will produce a broad but undistinguished product.

Build a **personal financial decision engine** around one promise:

> See what happened to all of your money, understand why, compare the realistic alternatives you had, and decide what your money needs to do next.

The product should connect four questions that other products often handle separately:

1. **What happened?** A reconciled timeline of cash, holdings, income, spending, fees, tax, and debt.
2. **Why did it happen?** Explain wealth change as contributions, spending, market return, FX, income, interest, fees, tax, and unresolved data.
3. **What else could I have done?** Replay the same dated cash flows through a share, index, gold, currency, deposit, debt repayment, or another realistic alternative.
4. **What should this money prepare for?** Show upcoming commitments, resilience, goals, and trade-offs without pretending to predict the market.

The flagship remains **Decision Replay**, but it becomes much more useful when it is fed by an everyday-money ledger. The defensible combination is not merely “what if I invested?” It is:

- the user's real cash-flow dates;
- point-in-time prices, FX, rates, fees, taxes, and inflation;
- investments, deposits, debt, and spending on one accounting timeline;
- path and purchasing-power comparison, not only an ending balance;
- visible assumptions, sources, missing data, and calculation versions;
- a private history of decisions and later outcome reviews.

## What the research says

### Most individual features are already table stakes

This market scan uses official product material available on the review date. Marketing pages show what competitors claim to support; they do not prove quality or customer satisfaction.

| Product/category | Existing capability | Implication for this project |
|---|---|---|
| [getquin](https://www.getquin.com/portfolio-tracker/), [Snowball Analytics](https://snowball-analytics.com/overview), [Sharesight](https://help.sharesight.com/us/performance_report/), and [Portfolio Performance](https://help.portfolio-performance.info/en/reference/view/reports/performance/dashboard/) | Multi-asset aggregation, dividends, fees, benchmark/performance analytics, imports, allocation, IRR/TWR, and risk metrics are already common in serious portfolio tools. | Portfolio totals, allocation charts, and a generic “health score” cannot be the main reason to switch. They are parity work. |
| [Monarch Money](https://www.monarchmoney.com/features/recurring), [Rocket Money](https://www.rocketmoney.com/faq), and [Lunch Money](https://lunchmoney.app/features) | Net worth, bank aggregation, categorization, recurring bills/subscriptions, budgets, goals, and reports are established personal-finance features. Lunch Money explicitly supports multiple currencies and an API. | Adding ordinary budgeting and subscription detection does not create differentiation. The project needs a distinct workflow that connects these facts to decisions. |
| [YNAB](https://www.ynab.com/features) | Intentional allocation, targets, planned irregular expenses, account import, net-worth reporting, and loan payoff planning are mature product concepts. | Do not try to beat a mature budgeting method in its first release. Offer a lower-chore “commitments and resilience” view first. |
| [ProjectionLab](https://projectionlab.com/) | Long-range what-if planning, compare mode, historical tests, tax modeling, Monte Carlo analysis, and life-event scenarios already exist. | A generic future simulator is not unique. Start with historical, auditable decisions and only add uncertain forecasts when assumptions can be defended. |
| [Actual Budget](https://actualbudget.org/) and [Wealthfolio](https://wealthfolio.app/docs/introduction/) | Local-first/private finance software, optional encrypted sync, self-hosting, investments, net worth, spending, and planning are available. | “Privacy-first” is valuable but is not sufficient alone. Privacy must be paired with a result users cannot easily get elsewhere. |
| [Splitwise](https://www.splitwise.com/) and [tricount](https://tricount.com/en-in/) | Groups, equal/unequal/custom splits, balances, settlements, recurring expenses, receipt support, offline entry, and multiple currencies are mature shared-expense concepts. | “Who owes whom” is useful but not unique by itself. The opportunity is to reconcile each claim with actual cash, spending, net worth, and goals. |
| [Expensify](https://use.expensify.com/all-products) | Receipt capture, expense classification, reimbursement, bills, invoices, payments, mileage, and reconciliation form an established business-expense workflow. | Receipt scanning is an ingestion feature, not a product identity. Personal use should connect line items to shopping, returns, warranties, personal inflation, and cash flow. |
| [Honeydue](https://www.honeydue.com/how-it-works) | Couples can aggregate accounts, coordinate bills, discuss transactions, and choose what financial information to share. | Household collaboration needs selective disclosure; merely adding a second login can leak private money. |
| Dedicated subscription and purchase-record apps such as [PayClear](https://www.pay-clear.com/) and [Proofly](https://proofly.cc/) | Trial/renewal reminders, price tracking, spending limits, shared bills, receipt storage, return windows, and warranty deadlines are available as focused tools. | The broader product wins only if a subscription or purchase is connected to the payment, contract, household split, goal impact, refund, and document evidence. |

The opportunity is therefore a **connected workflow**, not an isolated feature invention. A user should not need one app for spending, another for investments, a spreadsheet for deposits and FX, and a calculator for “what if.”

### The product should optimize for financial well-being, not activity

The US Consumer Financial Protection Bureau defines financial well-being around control of day-to-day finances, capacity to absorb a shock, progress toward goals, and freedom of choice. Its model explicitly says that income, net worth, or a credit score alone is insufficient ([CFPB resources](https://www.consumerfinance.gov/consumer-tools/educator-tools/financial-well-being-resources/)). That is a useful product framework even outside the US.

The UK FCA's Financial Lives 2024 survey reported that 24% of UK adults had low financial resilience, including people with low savings, heavily burdened commitments, or recent missed payments. Nine percent could cover living expenses for no more than a week after losing their main income ([FCA key findings](https://www.fca.org.uk/publication/financial-lives/financial-lives-survey-2024-key-findings.pdf)). This supports building cash runway and commitment visibility alongside investing analytics.

For irregular earners, [MoneyHelper](https://www.moneyhelper.org.uk/en/everyday-money/budgeting/how-to-budget-for-an-irregular-income) recommends planning around conservative income, covering regular bills, reserving for tax, preparing for high-cost months, and building an emergency fund. Those jobs are more useful than another monthly pie chart.

### Anecdotal user signals worth validating

Recent community discussions repeatedly describe three practical tensions. These are qualitative signals, not representative research:

- Imported balances and imported transactions can disagree, making reconciliation essential ([example](https://www.reddit.com/r/MonarchMoney/comments/1q1dixt/new_401k_account_oddity/)).
- A budgeting model can classify money moved to investments as spending, separating everyday-money truth from investment truth ([example](https://www.reddit.com/r/ynab/comments/1u2azgj/expenses_vs_transfers/)).
- Couples want a shared view of household obligations without exposing every private transaction ([example](https://www.reddit.com/r/budget/comments/1o4q914/budget_appssoftware_discussion_megathread/)).

These signals justify reconciliation, cross-domain transfers, and selective household sharing as product hypotheses. They do not justify assuming the proposed solution is correct without interviews and prototype tests.

## Who should choose this product

### Initial target user

Start with a user who:

- holds some combination of shares/funds, cash, foreign currency, gold, deposits, or debt;
- lives with inflation or currency risk and thinks in more than one currency;
- wants to understand decisions, not receive day-trading signals;
- has enough financial activity to outgrow a spreadsheet but does not want to maintain a complex budget every day;
- is willing to import statements or enter a small amount manually in exchange for trustworthy analysis.

The current BIST/TRY foundation makes multi-currency and high-inflation households a credible first niche. Keep the model global, but build one localization pack deeply before claiming global coverage.

### Explicit non-targets for the first releases

- active traders seeking order execution, live order books, alerts, or predictions;
- users seeking regulated personalized investment advice;
- users who need completed tax returns rather than informational estimates;
- enterprises needing accounting/ERP controls;
- users who only want a fully automatic bank balance screen and will never review imported data.

Narrowing the initial audience matters. Trying to serve all of these groups would change the legal, data, UX, and operational requirements.

## Why users would choose it

| Choice reason | Proof the product must provide | Why it is stronger than a slogan |
|---|---|---|
| **My investments and real life are connected** | A transfer from salary to brokerage is one transfer, not an expense in one module and a mystery contribution in another. Debt, cash, goals, and investments reconcile to the same net worth. | The product answers whole-money questions without duplicating or contradicting records. |
| **It tells me why, not only how much** | Every period reconciles opening value, external cash flow, spending, market movement, FX, income, fees, tax, and residual. | An explanation is actionable and also exposes accounting errors. |
| **Its comparisons are fair** | Alternatives receive identical dated cash flows and point-in-time data. Results include fees, spreads, tax assumptions, drawdown, liquidity, and purchasing power. | This is materially better than a hindsight chart based on one starting lump sum. |
| **It understands local financial reality** | TRY deposits, historical FX, gold, inflation, withholding/day-count rules, and local calendars are first-class policies with sources. | Global products often offer broad asset coverage without deep local decision logic. This claim must be validated market by market. |
| **It admits what it does not know** | Valuation coverage, stale/missing observations, manual estimates, assumptions, and calculation versions appear beside every result. | Visible uncertainty builds more trust than silent fallbacks or a mysterious score. |
| **It is useful without becoming a second job** | Import-review-reconcile flows, recurring detection, a commitments calendar, and “available after commitments” work without forcing zero-based budgeting. | It serves users who want control but reject constant category maintenance. |
| **Its incentives are aligned with mine** | No sale of financial data, no ads disguised as advice, no pay-to-rank financial products, portable export, and understandable deletion/consent controls. | The business model reinforces trust instead of undermining the analysis. |

### The moat, in order

1. **Correctness and auditability:** a tested ledger, deterministic replay, point-in-time valuation, and reproducible calculations.
2. **Localized policy/data packs:** market calendars, deposits, debt, tax/withholding assumptions, inflation, and licensed data behavior.
3. **The user's decision history:** actual cash flows, saved alternatives, goals, and later reviews become a private dataset that improves personal relevance.
4. **A coherent workflow:** capture -> reconcile -> explain -> compare -> prepare -> review.
5. **Trust and portability:** source visibility, privacy controls, export, and no lock-in tactics.

AI-generated commentary, a chat box, and a large asset catalogue are copyable. They are not the moat. AI may explain deterministic results or help classify an import, but it must never invent financial facts or perform the authoritative calculation.

## Product model

Use four user-facing pillars backed by one ledger:

| Pillar | User question | Core capabilities |
|---|---|---|
| **Truth** | What happened, and can I trust the numbers? | Accounts, activities, cash, holdings, liabilities, imports, reconciliation, net worth, data quality. |
| **Explain** | Why did my position change? | Return decomposition, TWR/XIRR, cash-flow analysis, recurring costs, personal purchasing power. |
| **Compare** | What would a realistic alternative have done? | Decision Replay, deposit/FX/gold/share/debt strategies, purchase and life-decision templates. |
| **Prepare** | What needs attention next? | Money calendar, resilience/runway, goals, contribution planning, irregular-income reserves, reviews. |

The backend should not create four disconnected applications. `Activity`, `Account`, point-in-time `Observation`, `Valuation`, and versioned `CalculationRun` are shared primitives.

### Expanding toward “everything money” without creating chaos

The long-term product can cover almost every household-money job, but it must not represent everything as a transaction. Use distinct domain nouns and connect them:

| Domain noun | Meaning | Example | What it must not be confused with |
|---|---|---|---|
| `Activity` | An economic fact that occurred | Card purchase cleared; salary received; friend repaid £40 | A predicted bill or hypothetical trade |
| `Account` | A place value is held or owed | Bank, wallet, brokerage, credit card, gift card | A category or goal |
| `Claim` / `Obligation` | An amount one party owes another | Alex owes the user half the hotel; user owes a friend | Spending again when the underlying purchase was already recorded |
| `Contract` | Terms that may generate future obligations | Broadband term, rent, insurance, subscription trial | A posted payment |
| `PlannedOccurrence` | An expected dated cash flow | Electricity bill expected next Friday | Cash already gone |
| `Plan` | The user's intended allocation or constraint | £300 groceries target; £2,000 reserve floor | A guarantee or bank sub-account |
| `Purchase` and `PurchaseItem` | What money acquired and its post-purchase lifecycle | Laptop, groceries, warranty, return deadline | The bank transaction alone |
| `Document` | Evidence for another record | Receipt, invoice, contract, statement, payslip | The authoritative structured financial fact until reviewed |
| `Scenario` | A hypothetical or counterfactual calculation | Buy now versus repay debt | Actual history or advice |
| `Project` | Context spanning multiple money records | Trip, wedding, move, renovation | An account that actually holds funds |
| `Counterparty` | A person or organization involved | Friend, employer, merchant, utility provider | Necessarily another registered user |

The most important state boundary is:

1. **Posted facts** affect balances and historical reports.
2. **Open obligations and claims** affect amounts due/receivable and optionally net worth with a confidence policy.
3. **Plans and expected occurrences** affect forecasts but never current balances.
4. **Scenarios** affect nothing outside their saved result.
5. **Documents and extracted data** remain evidence/previews until a user or deterministic reconciliation commits them.

This distinction allows the app to become broad without allowing a scanned bill, recurring prediction, IOU, and bank payment to count the same money four times.

## Required foundations before feature expansion

The defects in [backend-audit.md](backend-audit.md) and Stages 0-3 of [prioritized-roadmap.md](prioritized-roadmap.md) are prerequisites, not optional cleanup. In particular, fees, backdated trades, historical FX, database bootstrap, committed signing keys, concurrency, and near-zero test coverage must be resolved before marketing new analytics as trustworthy.

### FND-01 - Unified account and activity ledger

**Implement**

- `financial_account` for brokerage, cash, deposit, liability, wallet, and manual accounts.
- Multiple accounts and native-currency cash pockets per user/household; portfolios remain reporting groups rather than cash containers.
- Immutable `activity` envelopes with effective/recorded time, source, client/external ID, lifecycle state, provenance, correction/reversal relation, and calculation schema version.
- Typed account/security/claim legs for cash deposit/withdrawal, buy/sell, fee, tax, dividend/interest, transfer, bill/card payment, FX conversion, borrowing, principal repayment, and manual revaluation.
- Account-specific tracking mode, liquidity/capability, negative-balance/overdraft policy, pending/cleared state, and funding preferences.
- Rebuildable cash, position, lot/cost-basis, and liability projections.

The detailed account/posting/funding rules are in [cash-accounts-and-funding-design.md](cash-accounts-and-funding-design.md). A posted action changes selected accounts; a bill merely issued, plan, goal allocation, or scenario does not.

**Backend contract**

- `POST /api/v1/accounts`
- `GET /api/v1/accounts/{id}`
- `POST /api/v1/activities` with an idempotency key
- `GET /api/v1/activities?accountId=&cursor=&from=&to=`
- `POST /api/v1/activities/{id}/corrections`; never destructive “undo”
- `GET /api/v1/accounts/{id}/balances?asOf=`
- task-specific funding previews for trades, bills, purchases, transfers, and claim settlements before idempotent commit

All IDs are opaque strings and all decimal money/quantity values are exact strings at the API boundary, as described in [mobile-api-readiness.md](mobile-api-readiness.md).

**Acceptance criteria**

- Insertion order does not change the result when economic ordering is unchanged.
- A retried write produces one economic activity.
- A reversal plus replay equals a ledger in which the original event had not affected the projection.
- Quantity, cash, fees, tax, and realized basis reconcile for golden fixtures.
- Transfers change account balances but not household income, spending, or investment performance.
- Card purchases/payments, bill obligations/payments, loans, personal IOUs, refunds, and security trades affect the correct selected accounts exactly once.
- Imported negative reality is retained and flagged; manual negative behavior follows the account's explicit hard-floor, soft-floor, or authorized-limit policy.

### FND-02 - Historical observation and valuation platform

**Implement**

- Immutable price, adjusted-total-return, FX, index/rate/inflation, and manual-value observations.
- Provider, series, license/usage, ingestion run, publication vintage, revision, timezone/calendar, and quality metadata.
- A current observation projection for fast screens plus rebuildable daily account/portfolio/household valuation.
- A single valuation service that converts native values to a requested reporting currency as of a declared time.

**Backend contract**

- Internal provider adapters return normalized observations, never provider DTOs.
- `GET /api/v1/valuations?scope=&scopeId=&asOf=&currency=`
- `GET /api/v1/valuations/series?scope=&scopeId=&from=&to=&currency=&interval=`
- Every response includes requested/actual time, providers, priced/unpriced value, stale count, interpolation/fallback rule, and valuation version.

**Acceptance criteria**

- A historical trade and valuation use historical FX, never the latest rate.
- Missing data stays missing/flagged; cost is not silently substituted for market value.
- Backdated activities and revised observations rebuild only the affected range but produce the same result as a complete rebuild.
- Benchmark and portfolio are aligned to the same currency, dates, and income treatment.

### FND-03 - Versioned calculation runtime

**Implement**

- Pure calculation modules for valuation, performance, deposit accrual, debt amortization, inflation deflation, and scenarios.
- `calculation_run` containing input hash, calculation version, policy versions, series/revisions used, warnings, creation time, and result location.
- Cache keys based on scenario/input version plus data revisions and calculation version.
- Golden examples and property tests from [business-logic-and-analytics-design.md](business-logic-and-analytics-design.md).

**Acceptance criteria**

- The same inputs, policies, and data revisions return the same result.
- A source revision marks dependent results stale without deleting the old audit record.
- Results expose a calculation ID that support staff and users can inspect.
- No React or React Native screen independently recalculates authoritative financial values.

### FND-04 - Import, reconciliation, and provenance

**Implement**

- Manual entry and CSV first; deterministic broker/bank parsers before generic AI extraction.
- `import_batch` and `import_row` states: uploaded, parsed, needs review, approved, committed, rejected.
- File hash and row fingerprint duplicate detection.
- Statement opening/closing balance, count, cash, and quantity reconciliation.
- Explicit mapping for account, instrument, currency, timezone, fee, tax, and activity type.
- Source document retention/deletion independent from derived activities.

**Backend contract**

- `POST /api/v1/imports`
- `GET /api/v1/imports/{id}/preview`
- `PATCH /api/v1/imports/{id}/rows/{rowId}`
- `POST /api/v1/imports/{id}/commit` with idempotency
- `POST /api/v1/reconciliations`
- `GET /api/v1/accounts/{id}/reconciliation-status`

**Acceptance criteria**

- Preview totals must reconcile or explicitly state the unresolved difference before commit.
- Re-importing an identical file creates no duplicate economic events.
- AI output is always a preview with confidence/provenance; it cannot post directly.
- A synchronized balance that cannot be explained by synchronized activities appears as a reconciliation issue, not hidden “correct” wealth.

### FND-05 - Localization and policy packs

Policies must be code/data with effective dates and versions, not scattered conditionals.

Suggested interfaces:

- `MarketCalendarPolicy`
- `FxExecutionPolicy`
- `DepositAccrualPolicy`
- `WithholdingPolicy`
- `DebtAmortizationPolicy`
- `InflationSeriesPolicy`
- `CostBasisPolicy`
- `TaxInformationPolicy` (informational until professionally reviewed)

The first pack can focus on Turkey because the current product already supports BIST/TRY. TCMB publishes historical indicative FX, but explicitly notes that those rates are not binding transaction prices ([TCMB](https://www.tcmb.gov.tr/wps/wcm/connect/EN/TCMB%2BEN/Main%2BMenu/Core%2BFunctions/Exchange%2BRate%2BPolicy/Foreign%2BExchange%2Band%2BBanknotes%2BMarkets/Indicative%2BExchange%2BRates)); the UI must distinguish a reference rate from the user's executed rate. TÜİK exposes CPI through its [data portal](https://veriportali.tuik.gov.tr/en/). Borsa İstanbul states that it owns its indices and market data and describes licensing/distribution requirements ([market-data products](https://www.borsaistanbul.com/en/data/data-dissemination/market-data-products), [indices](https://www.borsaistanbul.com/en/indices)). Data licensing is therefore an early feasibility task, not something to solve after launch.

### FND-06 - Trust, security, and consent

- Rotate/externalize the exposed signing key and correct Google audience validation before adding sensitive financial scope.
- Encrypt transport and sensitive stored data; separate production secrets and provider credentials.
- Record security-relevant exports, imports, linked providers, sessions, and deletions.
- Let users see/revoke device sessions and external data connections.
- Provide full export, account deletion, retention controls, and a concise explanation of what is stored.
- Make notification, analytics/telemetry, AI-processing, and data-linking consent separate choices.

If UK open banking is added, it should use an authorized provider/appropriate regulatory route, explicit consent, scoped duration, and a revocation dashboard. Open Banking Limited describes explicit opt-in and user-controlled revocation as core behavior ([FAQ](https://www.openbanking.org.uk/faqs/), [consent management](https://standards.openbanking.org.uk/customer-experience-guidelines/introduction/consent-mgmt/v4-0/)). CSV/manual import is a much safer first integration than prematurely becoming or operating like an account-information provider.

## Implementable user features

Priorities below mean:

- **P0:** required to make present claims safe.
- **P1:** first valuable, trustworthy product.
- **P2:** differentiating product wedge.
- **P3:** expansion/retention after the wedge works.
- **Later:** useful, but only after validation or specialist review.

| ID | Feature | Main product role | Depends on | Differentiation | Relative size |
|---|---|---|---|---:|---:|
| FT-01 | Reconciled Money Timeline and net worth | Truth | FND-01/02/04 | Medium; essential bridge between money domains | XL |
| FT-02 | Investment Truth / Explain my return | Explain | FT-01 + daily valuation | High when fully reconciled and multi-currency | L |
| FT-03 | Money Calendar / recurring commitments | Truth/Prepare | FT-01 + classification | Low alone; high as input to resilience | L |
| FT-04 | Available after commitments / runway | Prepare | FT-03 + liquid balances | High for users who reject full budgeting | L |
| FT-05 | Goal and sinking-fund planning | Prepare | FT-01/03/04 | Medium; higher with real-world units | M |
| FT-06 | Decision Replay | Compare | FND-02/03 + FT-02 | Very high; flagship | XL |
| FT-07 | Deposit, debt, and FX engines | Compare | FT-06 + policy packs | Very high in localized markets | L per policy family |
| FT-08 | Personal purchasing power | Explain/Compare | Indices + FT-01/06 | High if methodology is honest | L |
| FT-09 | Purchase/life-decision templates | Compare/Prepare | FT-04/05/06/07 | High one validated template at a time | L-XL per template |
| FT-10 | Irregular-income/tax reserve | Prepare | FT-03/04 + policies | Medium/high for a focused segment | L |
| FT-11 | Contribution-first rebalancing | Prepare | FT-02 + allocation metadata | Medium; expected by investors | M |
| FT-12 | Decision journal/review | Explain/Learn | FT-06 | High as a long-term personal feedback loop | M |
| FT-13 | Selective household money | Truth/Prepare | All scope/authorization foundations | High if privacy controls work | XL |
| FT-14 | Evidence-backed Money Brief | Explain/Prepare | Stable read models from earlier features | Medium retention feature | L |
| FT-15 | Spending intelligence and monthly close | Truth/Explain | FT-01 + categories/rules | Medium alone; essential for whole-money truth | L |
| FT-16 | Personal IOUs and private loans | Truth/Prepare | Claims + FT-01 | High when connected to net worth and cash flow | L |
| FT-17 | Shared expenses and settlement | Truth/Household | FT-16 + permissions | Medium alone; high as part of one money ledger | L |
| FT-18 | Bills, subscriptions, contracts, and renewals | Truth/Prepare | FT-03 + documents | Medium; high when lifecycle and payment reconcile | L |
| FT-19 | Shopping, receipts, and purchase lifecycle | Truth/Explain | FT-15 + documents | High if item prices, returns, and goals connect | XL |
| FT-20 | Income, payslips, benefits, and reimbursements | Truth/Prepare | FT-01 + documents | Medium; enables accurate household planning | L |
| FT-21 | Invoices and lightweight freelancer money | Truth/Prepare | FT-10/20 + claims | High for a focused self-employed segment | XL |
| FT-22 | Refunds, disputes, warranties, and claims | Truth/Prepare | FT-19 + documents | High practical value; strong retention | L |
| FT-23 | Financial document vault and action queue | Truth/Prepare | Secure storage + all record links | Medium alone; high as workflow glue | L |
| FT-24 | Cash, gift cards, store credit, and rewards | Truth | FT-01 | Medium; closes common net-worth gaps | M |
| FT-25 | Utility usage and tariff intelligence | Explain/Prepare | FT-18/19 + localization | High after one local data/tariff pack works | XL |
| FT-26 | Event and project money | Prepare/Household | FT-05/17/19 | Medium; reusable across life events | L |
| FT-27 | Insurance and protection map | Truth/Prepare | FT-18/22/23 | Medium/high; underserved but sensitive | L |
| FT-28 | Family support, allowances, gifts, and giving | Truth/Prepare | FT-13/16/20 | Medium; valuable household extension | L |
| FT-29 | Cards, BNPL, overdrafts, and installments | Truth/Prepare | FT-01/03/18 | Very high for accurate commitments and debt | XL |
| FT-30 | Tax and government-money calendar | Prepare | FT-10/20/23 + policy packs | High locally; high regulatory maintenance | XL |
| FT-31 | Multi-account cash, funding, and balance control | Truth/Foundation | FND-01 | Essential; enables every posted money feature | XL |
| FT-32 | Real Asset Lifecycle and Total Cost of Ownership | Truth/Explain/Compare | FND-01/02 + FT-19/22/23/27/29/31 | Very high when actual ledger, usage, value, and decisions connect | XL |

### FT-01 - Reconciled Money Timeline and net worth (P1)

**User outcome**

The user sees one dated history across cash, investments, deposits, liabilities, income, expenses, and transfers. They can answer “what did I own, owe, receive, spend, and move on this date?”

**MVP**

- Brokerage, bank/cash, liability, and manual asset accounts.
- Manual/CSV activities, linked transfers, category/tag/note, and statement reconciliation.
- Current and historical gross assets, liabilities, and net worth in a selected reporting currency.
- A clear mode label: `FULL_CASH_TRACKING`, `HOLDINGS_ONLY`, or `MANUAL_BALANCE`.
- Coverage panel listing unreconciled accounts, stale/manual values, and unpriced holdings.

**Backend additions**

- FND-01, FND-02, and FND-04.
- `category`, `merchant`, `activity_category`, and user-defined tags; keep classification metadata separate from immutable financial legs.
- Daily `net_worth_projection` or a generic scoped valuation read model.

**Key business rules**

- Net worth = valued assets - liabilities; unpriced amounts are reported separately.
- A transfer is neither income nor expense. Brokerage contributions are external flows for investment performance but internal transfers for household net worth.
- Manual valuations carry an effective date and remain stale until a new appraisal.
- Unlike currencies are never added without an explicit dated conversion.

**Acceptance criteria**

- The closing ledger balance agrees with a reconciled statement for each tracked account.
- Household net worth is invariant when money moves between owned accounts, apart from explicit fees/FX spread.
- The same date/currency request returns the same versioned value until an input/revision changes.
- A user can trace every aggregate back to accounts and activities.

**Later**

Property, pensions, vehicles, private businesses, collectibles, and delegated read-only adviser access.

### FT-02 - Investment Truth and “Explain my return” (P1)

**User outcome**

Instead of one unexplained percentage, the user learns whether wealth changed because of investment selection, FX, dividends, fees, tax, cash-flow timing, or simply contributing more money.

**MVP**

- Absolute P&L, TWR, XIRR, realized/unrealized P&L, and reporting currency shown with named methodology.
- Opening-to-closing bridge: external flows, price return, FX return, income, fees/tax, and residual/data effect.
- Benchmark total return aligned by date and currency.
- Drawdown/recovery, concentration, fee drag, and data coverage after daily NAV is reliable.

**Backend additions**

- `PerformanceService`, `ReturnDecompositionService`, and calculation result/read-model records.
- `GET /api/v1/performance?scope=&scopeId=&from=&to=&currency=&method=`
- `GET /api/v1/performance/explanation?scope=&scopeId=&from=&to=&currency=`

**Key business rules**

- TWR evaluates the strategy independent of external cash-flow size/timing; XIRR represents the user's money-weighted experience. Do not label either simply “return.”
- Income-inclusive portfolios require total-return benchmarks.
- Price and FX effects use a declared attribution method and must reconcile to wealth change within the currency/relative tolerance policy.
- A non-trivial residual is an integrity alert, not a miscellaneous bucket to hide.

**Acceptance criteria**

- Opening value + external flows + decomposed result = closing value within declared tolerance.
- Hand-worked multi-currency, dividend, fee, and intra-period-flow fixtures pass.
- Every number includes period, currency, flow treatment, as-of time, data coverage, and calculation ID.
- The current hard-coded benchmark claim and cash-flow-contaminated “daily change” no longer exist.

### FT-03 - Money Calendar and recurring commitments (P1/P3)

**User outcome**

The user sees salary, rent, card payments, debt payments, subscriptions, annual insurance, taxes, and goal contributions before they happen, including likely amount ranges.

**MVP**

- User-created recurring income/expense/transfer schedules.
- Detection suggestions based on merchant, interval, account, direction, and amount tolerance; user approval required.
- Calendar/list for observed, expected, due, late/unmatched, and changed occurrences.
- “Subscription review” shows annualized cost, last change, use/keep/cancel note, and next renewal. It does not claim it can cancel services.

**Backend additions**

- `recurring_pattern`, `planned_occurrence`, `pattern_match`, and `merchant_alias`.
- `GET /api/v1/cash-flow/calendar?from=&to=&accountId=`
- `GET /api/v1/recurring/suggestions`
- `POST /api/v1/recurring/{id}/confirm`

**Key business rules**

- A prediction is not a posted activity and never changes a ledger balance.
- Credit-card payments and owned-account transfers must not be double-counted as spending.
- Variable commitments show a range/confidence derived from a declared lookback; a changed price is not automatically a duplicate subscription.
- Annualized cost must state whether taxes, FX, and variable usage charges are included.

**Acceptance criteria**

- No suggested recurrence is activated without confirmation.
- Every expected occurrence matches at most one posted activity unless the user explicitly splits it.
- Missed/changed alerts tolerate weekends, settlement delays, and configured grace periods.
- A calendar total separates committed, estimated, and optional amounts.

### FT-04 - Available after commitments and resilience runway (P2)

**User outcome**

The product answers a safer question than “can I afford this?”: “How much liquid money remains after known near-term commitments, my chosen safety floor, and funded goals?” It also shows how long essential expenses could be covered under an income shock.

**MVP**

- User-selected horizon, included liquid accounts, essential categories, reserve floor, and protected goals.
- Available-after-commitments bridge rather than one magic number.
- One-month and three-month no-income stress tests.
- Runway expressed both in time and a dated cash-balance path.

**Backend additions**

- `commitment_policy`, `resilience_plan`, `stress_scenario`, and daily projected cash balance.
- `GET /api/v1/plans/liquidity?asOf=&horizon=&currency=`
- `POST /api/v1/plans/resilience/calculate`

**Calculation sketch**

`eligible cleared liquid value - committed outflows in horizon - protected goal allocations - user reserve floor + high-confidence income in horizon`

Every term is inspectable. Pending card activity, overdraft/credit, uncertain income, investment assets, and tax reserves have separate inclusion policies. The result is informational, not a promise that spending the amount is safe.

**Acceptance criteria**

- The result links to every included balance, commitment, and policy.
- Transfers between eligible accounts do not change household availability.
- Low-confidence income is excluded by default and displayed as an upside scenario.
- The user can see how one changed bill, income delay, or goal affects the date of minimum cash.

### FT-05 - Goals and sinking funds in real-world units (P2/P3)

**User outcome**

Goals are obligations or choices with a date, currency, and purchasing-power target—not decorative progress bars.

**MVP**

- Target types: fixed amount/date, recurring sinking fund, emergency reserve, and liability payoff.
- Target currency and optional linked inflation/index/basket.
- Account/amount allocations without pretending the app has moved bank money into a real sub-account.
- Required periodic contribution, progress, shortfall, and effect on liquidity.

**Backend additions**

- `goal`, immutable `goal_version`, `goal_allocation`, `goal_schedule`, and `goal_progress_projection`.
- `POST /api/v1/goals`
- `GET /api/v1/goals/{id}/progress?asOf=`
- `POST /api/v1/goals/{id}/calculate-plan`

**Key business rules**

- Goal progress is measured in the goal's currency/unit using point-in-time conversion.
- One real balance cannot be allocated twice without showing over-allocation.
- Deterministic contribution planning comes before probability-of-success forecasts.
- Pausing or reprioritizing a goal is normal behavior and must not trigger shame messages.

**Acceptance criteria**

- Progress plus unallocated value reconciles to the underlying accounts.
- A target/index revision creates a new version and an explained progress change.
- The contribution plan respects recurring commitments and reserve floor.
- A goal can be private, household-visible, or shared based on explicit permissions.

### FT-06 - Decision Replay MVP (P2 flagship)

**User outcome**

The user selects a real amount/date or a saved decision and compares what actually happened with realistic alternatives.

**First vertical slice**

- One starting amount, start/end date, source currency, and reporting currency.
- Up to four alternatives: market instrument/index, hold/convert currency, gold/commodity proxy, and simple fixed-rate deposit.
- Same-day close or next-market-open execution rule, configurable fees/spread, income reinvestment, one official inflation deflator.
- Ending nominal/real value, opportunity difference, timeline, max drawdown, time ahead, and assumptions/source panel.
- Immutable saved scenario version and private share/export.

**Backend additions**

- `scenario`, `scenario_version`, `scenario_cash_flow`, `strategy_definition`, `scenario_run`, and versioned result.
- Pure `ScenarioEngine` consuming resolved series and policies.
- `POST /api/v1/scenarios`
- `POST /api/v1/scenarios/{id}/versions/{version}/runs`
- `GET /api/v1/scenario-runs/{runId}`
- Use an asynchronous job only if calculation exceeds the normal request budget; result retrieval must be resumable for mobile.

**Fairness rules**

- Every alternative receives identical external cash flows at identical economic times.
- No future observation may fill a missing past market day.
- Use adjusted total return or explicit corporate actions/dividends.
- Use point-in-time FX and state whether it is reference, closing, or actual execution.
- Include declared fee, spread, tax/withholding, reinvestment, holiday, and deposit-renewal assumptions.
- Keep historical facts separate from future assumed returns.

**Acceptance criteria**

- Golden scenarios can be recomputed by hand from exported inputs.
- Reordering alternatives does not change any result.
- The run stores all series revisions and policy versions and can be reproduced.
- Missing/licensing-limited inputs prevent or visibly qualify the comparison; they are never silently imputed.
- The output states that historical comparison is educational and not a recommendation.

**Next version**

Replay the user's actual multiple cash flows or a linked decision; add deposit renewal schedules, debt repayment, custom baskets, and portfolio strategies.

### FT-07 - Deposit, debt, and FX strategy engines (P2)

These are reusable Decision Replay strategies and standalone calculators. They are especially important because repaying debt or using a deposit can be a more realistic alternative than buying another share.

**Deposit MVP**

- Principal, start/maturity, nominal rate, fixed/variable behavior, day-count convention, compounding, withholding, fees, renewal rule, and early-break behavior.
- Distinguish a historical observed product quote from a generic/reference rate series.

**Debt MVP**

- Principal, APR/rate schedule, payment frequency, remaining term, interest method, fees/insurance, prepayment amount/date/penalty, and optional rate reset.
- Return revised payoff date, interest avoided, liquidity used, and payment schedule difference.

**FX-hold MVP**

- Source/target currency, historical conversion policy, bid/ask or explicit spread, cash yield if any, reconversion rule, and reference-versus-execution warning.

**Backend additions**

- Versioned `deposit_terms`, `liability_terms`, and amortization/accrual schedules.
- `DepositStrategy`, `DebtPrepaymentStrategy`, and `FxHoldingStrategy` implementing a common scenario interface.
- `POST /api/v1/calculators/deposit`, `/debt-prepayment`, and `/fx-holding` can be thin scenario templates, not separate math implementations.

**Key business rules**

- Avoided debt interest is not a market price return; show its lower uncertainty and reduced liquidity separately.
- Never compare a gross investment return with a net debt/deposit result without labeling the mismatch.
- Effective annual yield, nominal rate, and APR are distinct concepts.
- Rate/tax policies are effective-dated and jurisdiction-specific.

**Acceptance criteria**

- Hand-worked day-count, compounding, withholding, amortization, and prepayment-penalty fixtures pass.
- The revised debt schedule reconciles principal, interest, fees, and ending balance to zero/tolerance.
- The comparison shows liquidity, drawdown/volatility where meaningful, and assumption risk alongside ending value.

### FT-08 - Personal purchasing power (P2/P3)

**User outcome**

The user sees whether wealth is improving against official inflation and against things they actually care about: rent, tuition, a home deposit, travel, or a chosen essentials basket.

**MVP**

- Official CPI deflation.
- User-defined goal index linked to manual prices or an official category series.
- Results in currency and intuitive units such as “months of essential spending” or “percentage of the home deposit.”
- A fixed-weight personal basket with visible coverage and update dates.

**Backend additions**

- `basket`, `basket_version`, `basket_item`, `unit_price_observation`, and official-series link.
- `POST /api/v1/baskets`
- `POST /api/v1/baskets/{id}/observations`
- `GET /api/v1/purchasing-power?scope=&scopeId=&basketId=&from=&to=`

**Critical calculation rule**

Do not infer personal inflation from total category spending alone. Spending can rise because price, quantity, quality, household size, or category mix changed. The MVP should use fixed base-period weights and item/category price indices, with explicit substitution/version rules. If only spend totals exist, label the output “cost mix change,” not inflation.

**Acceptance criteria**

- Basket versions preserve historical weights and sources.
- Missing items show coverage; an official-category fallback is explicit.
- Nominal, official-real, and personal-basket-real results use the same dates/currency.
- Editing future goal assumptions does not rewrite a prior historical scenario result.

### FT-09 - Purchase and life-decision templates (P3)

**User outcome**

The user can evaluate meaningful choices without turning every purchase into guilt: buy now versus wait, cash versus finance, keep a subscription, change salary/currency, rent versus buy, or spend on an experience versus fund a goal.

**First MVP: one purchase decision**

- Purchase amount/date/currency and optional recurring ownership cost.
- Compare buy now, delay, deposit/cash, debt repayment, and one market alternative.
- Show effect on liquidity floor, goal date, and historical/assumed opportunity path.
- Let the user mark the purchase “worth it,” add non-financial value, or opt out of future review.

**Backend design**

- Implement templates as validated constructors over the same versioned scenario/cash-flow engine.
- Each template declares required fields, generated cash flows, supported policies, result measures, and warnings.
- Never create separate rent/buy, car, and purchase math scattered across controllers.

**Later templates**

- Car: cash/loan/lease, depreciation, maintenance, insurance, resale.
- Rent/buy: deposit, mortgage, maintenance, transaction costs, rent increases, property value, mobility horizon.
- Job/pay: after-tax pay assumptions, currency, pension/benefits, commute/care costs, start date, income volatility.
- Education: fees, lost earnings, financing, and non-financial goals.

These are assumption-heavy and may cross into regulated/tax territory. Ship them one at a time after specialist review and user testing, not as a generic “AI financial adviser.”

**Acceptance criteria**

- Users can inspect every generated cash flow and edit assumptions.
- Financial output never assigns a monetary value to user-entered happiness/meaning.
- Forecast and historical inputs are visually and structurally distinct.
- Sensitivity ranges replace false point-estimate certainty where inputs are uncertain.

### FT-10 - Irregular income and tax reserve planner (P3)

**User outcome**

Freelancers, contractors, commission workers, and households with variable income can see a conservative spending base, upcoming tax reserve, and high-cost months.

**MVP**

- Classify income streams and distinguish gross/net/reimbursement/transfer.
- Monthly distribution and seasonality view with user-chosen conservative baseline; suggest but do not force a lower-percentile/lowest-period method.
- Essential commitments, high-cost months, reserve floor, and tax-reserve bucket.
- Stress cases for delayed/missing client payments.

**Backend additions**

- `income_stream`, `income_expectation`, `reserve_rule`, and cash forecast scenarios.
- Tax reserve is initially a user-entered percentage/rule with a disclaimer, not a tax liability claim.
- Later jurisdiction packs can estimate bands only after professional review and effective-dated rules.

**Acceptance criteria**

- One-off transfers/refunds are excluded from earned-income statistics by default.
- The baseline method, lookback, exclusions, and confidence are visible.
- Expected invoices do not become ledger income until posted.
- A delayed-income scenario explains which commitments/reserves become constrained and when.

### FT-11 - Contribution-first rebalancing planner (P3)

**User outcome**

The user learns how new savings could reduce portfolio drift while respecting goals, fees, minimum order sizes, tax sensitivity, and cash needs.

**MVP**

- User-defined target allocation and tolerance bands.
- Current look-through only at direct instrument/asset-class/currency level initially.
- Allocate a new contribution across underweight targets; default to no sales.
- Show before/after allocation, remaining drift, fees, and unresolved/unpriced positions.

**Backend additions**

- Versioned `allocation_policy`, `allocation_target`, and pure `ContributionPlanner`.
- `POST /api/v1/plans/contribution-allocation`

**Key business rules**

- This is a planning calculation, not an order or recommendation.
- Unpriced assets and restricted/tax-wrapped accounts cannot be optimized away silently.
- Currency exposure and asset classification are different dimensions.
- Advanced tax-aware sales wait for lot/jurisdiction support.

**Acceptance criteria**

- Allocations sum exactly to the available contribution after estimated fees/reserve.
- Minimum order, fractional quantity, allowed account/instrument, and tolerance constraints hold.
- Re-running unchanged inputs is deterministic.
- The user must explicitly apply any plan; no automatic trade execution.

### FT-12 - Decision journal and outcome review (P3)

**User outcome**

The product helps users learn from their own process rather than encouraging more trading.

**MVP**

- Optional note attached to a purchase, investment, deposit, debt repayment, or major spending decision.
- Reason, horizon, alternatives, expectation, risk/constraint, confidence, and review dates.
- At review, show expected versus actual result and the saved alternatives using the original scenario version.
- User reflection tags such as plan followed, assumption wrong, circumstances changed, or still worthwhile.

**Backend additions**

- `decision`, immutable `decision_version`, `decision_link`, `review_schedule`, and `decision_review`.
- Notifications reference due reviews but never say “you made a bad trade.”

**Acceptance criteria**

- Later market data cannot change the original assumptions; it creates a new review result.
- Reviews distinguish outcome quality from decision-process quality.
- Private notes are excluded from AI processing and household sharing by default.
- Behavioral summaries require enough observations and use descriptive, non-diagnostic language.

### FT-13 - Selective household money (P3)

**User outcome**

Partners can coordinate shared commitments and goals without automatically exposing every personal balance or purchase.

**MVP**

- Household space, invite, owner/member/viewer roles.
- Account/activity visibility: private, aggregate-only, shared detail.
- Shared commitments, goals, and scenarios.
- “Mine / yours / shared” contribution view with permissions evaluated server-side.

**Backend additions**

- `household`, `household_member`, `resource_grant`, and explicit scope/visibility on read models.
- Every repository query and cached projection must include owner/household authorization scope.
- Audit changes to sharing permissions.

**Acceptance criteria**

- Aggregate-only members cannot infer hidden exact values through subtotals, exports, charts, scenario inputs, notifications, or API errors.
- Removing a member revokes refresh sessions and future access immediately.
- Shared transfers/expenses are not duplicated in household totals.
- Invitations expire and cannot be replayed.

### FT-14 - Evidence-backed Money Brief (P3)

**User outcome**

A weekly/monthly brief highlights what changed and what needs attention without forcing the user to inspect every dashboard.

**MVP**

- Deterministic facts: upcoming shortfall, changed recurring bill, unreconciled account, stale price, fee increase, unusual cash flow, goal drift, and return decomposition.
- Each card links to supporting accounts/activities/calculation.
- User controls frequency, thresholds, channels, quiet hours, and topic opt-outs.
- Optional generative summary may restate selected facts but may not calculate or add unsupported claims.

**Backend additions**

- `insight_fact`, `insight_rule_version`, `brief`, delivery preferences, and notification outbox.
- Facts are generated idempotently from versioned read models.

**Acceptance criteria**

- Every sentence is traceable to structured evidence.
- Changed source data retracts/supersedes an insight rather than silently editing it.
- No urgency, shame, trading prompt, or affiliate product appears as analysis.
- Users can rate usefulness/incorrectness to improve rules without uploading private notes by default.

### FT-15 - Spending intelligence and monthly close (P1/P3)

**User outcome**

The user can answer “where did my money go this month, what was normal, what changed, and what remains after transfers, refunds, shared costs, and one-off items are treated correctly?”

**MVP**

- A transaction inbox for pending, cleared, unmatched, needs-review, and reconciled activity.
- Merchant normalization, user categories/subcategories, tags, notes, transaction splits, and ordered classification rules.
- Three optional planning modes: `TRACK_ONLY`, flexible category targets, and later envelope/zero-based allocation. Do not force one budgeting philosophy.
- Monthly views for income, consumption spending, transfers, debt principal, interest/fees, savings/investing, recoverable/shared amounts, and net cash flow.
- Plan versus actual, rolling 3/6/12-month baselines, fixed/variable and essential/discretionary dimensions, seasonal/annual expenses, and user-marked one-offs.
- A monthly-close checklist: reconcile accounts, resolve duplicates/unclassified transactions, review claims/refunds, explain material differences, then save a versioned snapshot.

**Backend additions**

- `category`, `category_version`, `merchant`, `merchant_alias`, `classification_rule`, `activity_split`, `spending_plan`, `plan_period`, `category_target`, and `monthly_close`.
- Keep category/classification revisions separate from immutable money legs so reclassification does not rewrite the economic event.
- `GET /api/v1/spending/summary?from=&to=&scope=&scopeId=`
- `GET /api/v1/spending/months/{month}`
- `POST /api/v1/spending/rules` and `POST /api/v1/spending/months/{month}/close`

**Critical business rules**

- Card payments, owned-account transfers, investment contributions, money lent to a person, and debt-principal repayment are not consumption spending.
- A refund reverses or reduces the original purchase/category when linked; it is not ordinary income.
- When another person owes part of a purchase, the payer's cash outflow is the full amount, personal spending is only the payer's share, and the remainder is a receivable.
- Pending and cleared versions of the same transaction must match, not count twice.
- Category budgets use the user's reporting currency and point-in-time FX; native amounts remain inspectable.
- “Savings rate” must publish its numerator/denominator and treatment of pension contributions, debt principal, and investment gains.
- Month-over-month comparisons account for partial months, different pay cycles, annual bills, refunds, and user-marked exceptional events.

**Acceptance criteria**

- Every spending total traces to activity splits and reconciles to account cash movement after non-spending flows.
- Reclassifying a transaction changes reports but never the account balance or source provenance.
- A closed month can be reopened with an audit record; later imports identify their effect on the prior close.
- Rule previews show which historical/new records would change before bulk application.
- Reports never sum mixed currencies without dated conversion and coverage metadata.

### FT-16 - Personal IOUs and private loans (P2/P3)

**User outcome**

The user can safely record “I owe someone” or “someone owes me,” including partial repayments and due dates, while their cash flow, spending, and net worth remain correct.

**MVP**

- Counterparty as a private contact label or invited account; phone contacts upload is not required.
- Direction (`RECEIVABLE` or `PAYABLE`), principal, currency, issue/due dates, purpose, installments, evidence, notes, and reminder preference.
- Interest-free IOU first. Add user-entered fixed interest only after jurisdictional/legal review.
- Partial/full settlement, disputed amount, due-date change, forgiveness/write-off, and correction history.
- Face value, amount paid, outstanding value, overdue age, and optional “exclude from spendable/net worth” treatment.
- One-off social debts (“I owe Sam dinner”) and documented private loans share the claim ledger but have different detail requirements.

**Accounting model**

- Lender: cash decreases and a receivable increases. This is not spending unless later forgiven/written off.
- Borrower: cash increases and a payable increases. This is not income.
- Repayment reduces cash and principal on one side and increases cash/reduces receivable on the other.
- Interest, fee, gift/forgiveness, and foreign-exchange effects are separate components.
- A claim without a linked cash movement is allowed for opening balances or shared-expense allocation, but its source/verification status must be explicit.

**Backend additions**

- `counterparty`, `claim`, immutable `claim_version`, `claim_party`, `repayment_schedule`, `claim_allocation`, `claim_event`, `claim_evidence`, and `claim_confirmation`.
- `POST /api/v1/claims`, `POST /api/v1/claims/{id}/events`, and `GET /api/v1/claims/summary`.
- Link repayment activities to exact claims; one payment may allocate across claims only with an explicit user allocation.

**Multi-currency and confidence rules**

- The obligation currency does not change merely because the reporting currency changes.
- Settlement in another currency records the actual paid amount/rate and the agreed obligation reduction; do not rewrite historical principal at today's FX.
- User-owned receivables should appear separately from liquid assets. Allow a confidence/collectability state such as `CONFIRMED`, `UNCONFIRMED`, `DISPUTED`, or `WRITTEN_OFF`; do not invent a default haircut presented as fact.

**Safety boundary**

Keep the first product as a private record and mutually confirmed ledger. Do not match borrowers/lenders, underwrite, recommend interest, hold client money, enforce collection, report to credit agencies, or send coercive reminders. Those changes can become regulated lending, payment, or debt-collection activity. For example, the UK FCA treats consumer lending and debt-related activities as regulated areas requiring the appropriate permissions ([FCA authorization overview](https://www.fca.org.uk/consumers/how-check-firm-individual-authorised), [lender guidance](https://www.fca.org.uk/firms/authorisation/consumer-credit-lenders-hirers)). Review each launch jurisdiction independently.

**Acceptance criteria**

- Principal issued - principal repaid - forgiven/written-off principal = outstanding principal exactly.
- Lending/borrowing and principal repayment do not distort income or consumption-spending reports.
- Invited counterparties can confirm, propose a correction, or dispute without changing the owner's private ledger silently.
- Reminder frequency, quiet hours, and stop controls are respected; the product never presents itself as a debt collector.
- Deleting a private contact does not destroy required financial history; identifying details can be minimized/anonymized according to policy.

### FT-17 - Shared expenses, reimbursements, and settlement (P3)

**User outcome**

Friends, partners, families, housemates, and travel groups can record who paid, who benefited, and who owes whom, then settle without duplicating spending.

**MVP**

- Group/project, participants (registered or private placeholders), payer(s), beneficiaries, date, currency, category, receipt, and notes.
- Split equally, exact amounts, percentages, shares, and item-by-item receipt allocation.
- Recurring household splits and saved default shares.
- Partial reimbursements, direct settlement, debt simplification suggestion, balances by person/group/currency, and export.
- Offline-capable client creation with idempotent synchronization for the future Expo app.

**Backend additions**

- `expense_group`, `group_member`, `shared_expense`, `expense_payer`, `expense_share`, `settlement_suggestion`, and links to FT-16 claims and ledger activities.
- A shared expense is a view over one economic purchase plus claims; it must not create duplicate purchase activities for every participant.
- `POST /api/v1/shared-expenses`, `POST /api/v1/shared-expenses/{id}/revisions`, and `POST /api/v1/groups/{id}/settlements`.

**Calculation rules**

- Deterministic largest-remainder/minor-unit allocation handles indivisible cents; show who receives the rounding remainder.
- Settlement minimization is a suggestion over net claims, never a rewrite of the original who-paid-for-whom history.
- Keep different currencies as separate obligations by default. If participants agree to net/convert, store the settlement date, rate source or actual rate, and consent/version. Splitwise also keeps currencies separate by default, illustrating why conversion must be deliberate ([Splitwise multi-currency behavior](https://kb.splitwise.com/balances-and-expenses/how-can-i-manage-a-friendship-or-group-with-multiple-currencies)).
- Refunds reduce the original shared expense and each participant's claim according to an explicit allocation.

**Acceptance criteria**

- Sum of payer amounts equals purchase total; sum of participant shares equals purchase total in native minor units.
- The payer's personal consumption equals only their final share; receivables cover other shares.
- Editing a confirmed expense creates a version and notifies affected participants; it never silently changes settled history.
- Group visibility does not reveal unrelated personal transactions or balances.
- Removing/simplifying settlement edges cannot change any participant's net balance.

### FT-18 - Bills, subscriptions, contracts, and renewals (P1/P3)

**User outcome**

The user knows not only that a payment repeats, but why, under which contract, when it can change or end, whether the bill was issued/paid, and what action is possible before renewal.

**MVP**

- Distinguish `BILL`, `SUBSCRIPTION`, `MEMBERSHIP`, `LEASE`, `INSURANCE`, `UTILITY`, `TAX`, and generic `CONTRACT`.
- Bill lifecycle: expected, issued, due, scheduled, partially paid, paid, overdue, disputed, waived, and cancelled.
- Contract terms: start/end, minimum term, notice period, trial, promotion, scheduled price changes, auto-renewal, early-exit fee, billing cadence, payer/account, household split, and attachments.
- Match predicted/issued obligations to actual payments and explain amount/date differences.
- Price history, monthly-equivalent and remaining committed cost, trial/renewal/notice alerts, and cancellation/status notes.
- Detect probable recurring charges, duplicate services, or price increases as review suggestions—not automatic facts.

**Backend additions**

- `service_contract`, immutable `contract_version`, `bill`, `bill_line`, `contract_price_phase`, `renewal_event`, `notice_rule`, `bill_payment_allocation`, and document links.
- FT-03 `recurring_pattern` predicts cash timing; FT-18 records contractual/issued obligations. Do not merge them into one ambiguous recurrence table.
- `POST /api/v1/contracts`, `POST /api/v1/bills`, `POST /api/v1/bills/{id}/payments`, and `GET /api/v1/contracts/action-calendar`.

**Key business rules**

- A contract generates planned obligations; only a posted payment changes cash.
- Variable bills retain quantity/usage, unit rate, fixed charge, tax, adjustment, and total when known.
- A cancelled payment mandate does not prove the underlying service contract was cancelled, and contract cancellation does not prove a pending bank mandate stopped.
- Annualized/monthly-equivalent cost states which price phases, FX policy, usage assumptions, tax, and remaining term are included.
- Do not claim savings from cancellation unless the remaining contract/exit costs and replacement need are included.

**Acceptance criteria**

- Each actual payment matches at most one bill allocation unless explicitly split.
- Partial payment, credit note, refund, late fee, and overpayment reconcile to bill balance.
- The app warns before the actionable notice date, not merely on the renewal/charge date.
- A price change preserves prior contract versions and recalculates future commitments without rewriting posted bills.
- Payment/cancellation execution remains out of scope until regulated provider, consent, failure recovery, and liability are designed.

### FT-19 - Shopping, receipts, and purchase lifecycle (P3)

**User outcome**

Shopping becomes more than a merchant total: the user can plan a basket, understand item-level prices, match the receipt to payment, share items, and recover value through returns or warranties.

**MVP**

- Shopping lists with quantity/unit, target budget, preferred store, household/project sharing, priority, and “already owned” state.
- Receipt/photo/PDF/email import into a reviewable preview: merchant, date, currency, subtotal, discount, tax, tip, total, payment method, and line items.
- Match one receipt to one/multiple ledger payments and identify unmatched cash, split tender, gift card, or card activity.
- Line-item quantity, package size, normalized unit, unit price, discounts, category, buyer/beneficiary, and returnability.
- Purchase record with delivery, return deadline, warranty, serial/model, installment plan, receipt/document, and later resale/disposal.
- Personal price book and basket history after enough reviewed item data exists.

**Backend additions**

- `shopping_list`, `shopping_item`, `shopping_trip`, `receipt`, `receipt_line`, `product_identity`, `merchant_product`, `purchase`, `purchase_item`, `purchase_payment`, and `ownership_event`.
- OCR/AI extraction always lands in an import-preview state; source image regions/confidence should be retained for review.
- `POST /api/v1/receipts`, `GET /api/v1/receipts/{id}/preview`, `POST /api/v1/receipts/{id}/commit`, and `GET /api/v1/price-book`.

**Price and spending rules**

- Normalize comparable units (for example per kg/litre/item) only when product quantity/unit is known; do not compare unlike sizes or qualities as equivalent.
- Allocate receipt-level tax/discount/tip using a declared method and preserve the undistributed minor-unit remainder.
- A receipt line can split across categories/people/projects, but the sum must reconcile to the receipt total and matched payments.
- Item-level observed prices can feed FT-08's personal basket. Changing quantities or brands is consumption mix, not automatically inflation.
- Price alerts/store comparison require current retailer data and permission/licensing; a personal historical price book can be built without pretending it represents the whole market.

**Later**

- Pantry/household inventory, replenishment reminders, food-waste cost, barcode/catalog matching, shared meal/event basket, delivery tracking, resale value, and total cost of ownership.
- A “cooling-off” wishlist can show goal/liquidity impact while allowing the user to mark an intentional purchase as worth it.

**Acceptance criteria**

- Receipt subtotal + tax + tip + fees - discounts/credits = total within the declared rounding rule.
- Receipt total equals matched payment allocations or exposes the unresolved difference before commit.
- OCR never creates a posted expense without review or deterministic matching approval.
- Returns/refunds preserve item and original-payment links.
- Household members see only shared lists/items/receipts according to permission.

### FT-20 - Income, payslips, benefits, and reimbursements (P2)

**User outcome**

Income is understood as more than a bank deposit: users see recurring take-home pay, variable components, employer benefits/contributions, deductions, and money they are still owed.

**MVP**

- Income streams for salary, freelance, pension, rent, interest, dividends, benefits, gifts, and other sources.
- Payslip import/review with gross pay, base/variable pay, bonus/overtime, tax, social contributions, pension/retirement contributions, employer match, benefits, deductions, and net pay.
- Expected versus actual payday/amount, multi-currency income, and pay-period/calendar normalization.
- Employer/client expense reimbursement as a receivable linked to the original expense; receipt and submission status.
- Annual/rolling income and volatility reports that exclude transfers, refunds, and borrowed principal.

**Backend additions**

- `income_stream`, `pay_statement`, `pay_component`, `benefit`, `reimbursement_claim`, and document provenance.
- Components map to cash, tax/withholding, pension asset, benefit information, or receivable without forcing every non-cash benefit into net worth.
- `POST /api/v1/pay-statements/imports`, `GET /api/v1/income/summary`, and `GET /api/v1/reimbursements`.

**Acceptance criteria**

- Gross - employee deductions = net cash plus separately identified non-cash/redirected components according to the statement.
- Employer match is not counted as personal cash income but can increase a linked pension asset when supported.
- Reimbursement receipt reduces the receivable and does not count as new earned income.
- Payroll/tax labels are jurisdiction-versioned; the app does not claim to replace an official payslip or tax return.

### FT-21 - Invoices and lightweight freelancer money (P3/Later)

**User outcome**

A self-employed user can separate personal and work money, know what clients owe, reserve for tax, track reimbursable costs, and forecast cash without adopting a full accounting suite.

**MVP**

- Client/counterparty, estimate/quote reference, invoice number, issue/due dates, currency, line items, tax/discount, payment instructions, and attachment/export.
- Draft, issued, viewed/acknowledged, partially paid, paid, overdue, disputed, credited, and written-off states.
- Accounts receivable aging, expected cash timeline, payment allocation, and link to FT-10 tax reserve.
- Work expense/project tagging, receipt capture, mileage with user-entered/local policy, and reimbursable expense claims.
- Personal/business transfer classification and a separate business workspace/reporting scope.

**Backend additions**

- `business_profile`, `client`, `invoice`, immutable `invoice_version`, `invoice_line`, `invoice_payment`, `credit_note`, and links to FT-16 claims/projects/documents.
- `POST /api/v1/invoices`, `POST /api/v1/invoices/{id}/issue`, and `POST /api/v1/invoices/{id}/payments`.

**Boundaries**

- Initially this is cash visibility and receivable tracking, not double-entry business accounting, statutory invoicing in every jurisdiction, payroll, VAT/sales-tax filing, or accounts submission.
- Do not collect payments or hold funds until payment-service obligations, failure/refund handling, fraud, and jurisdictional requirements are addressed.

**Acceptance criteria**

- Invoice total, credits, payments, withholding, FX differences, and outstanding balance reconcile exactly.
- A bank payment can match one or several invoices only through an explicit allocation.
- Overdue reminders are user-controlled, professional, rate-limited, and never framed as automated debt collection.
- Issued documents are immutable versions; corrections use a replacement/credit workflow.

### FT-22 - Refunds, disputes, returns, warranties, and claims (P3)

**User outcome**

The app helps the user recover money already theirs by tracking open refunds, return windows, duplicate/incorrect charges, warranties, insurance claims, deposits, and reimbursements.

**MVP**

- Case types: return/refund, merchant dispute, card dispute/chargeback, warranty, insurance claim, deposit recovery, reimbursement, and billing correction.
- Case amount/currency, original purchase/bill/activity, items, counterparty, opened/deadline dates, expected outcome, status, evidence, messages/notes, and actual recovery.
- Reminders before return/warranty/response deadlines and an “awaiting whom?” queue.
- Expected recoverable amount shown separately from cleared cash and from confirmed receivables.
- Duplicate charge, unexpected amount, missing refund, and repeat-payment anomaly suggestions.

**Backend additions**

- `recovery_case`, immutable `case_version`, `case_item`, `case_deadline`, `case_evidence`, `case_event`, and `recovery_allocation`.
- `POST /api/v1/recovery-cases` and `GET /api/v1/recovery-cases/actionable`.

**Business rules**

- A promised refund does not change cash until posted. It can become a separately labeled expected/confirmed receivable.
- A received refund reduces the original spending/item basis when linked; compensation beyond purchase price is classified separately.
- Detection is “possibly duplicate/unexpected,” never a definitive fraud accusation.
- Legal/consumer-rights information, deadlines, and warranty rules require a sourced jurisdiction pack and update owner.

**Acceptance criteria**

- Recovered allocations never exceed the case amount without an explicit compensation/FX component.
- Closing a case requires an outcome reason; unresolved expected money remains visible.
- Attachments inherit the case/purchase visibility and retention rules.
- The app never files a legal, insurance, or card claim without a separate explicitly authorized integration.

### FT-23 - Financial document vault and Money Action Queue (P2/P3)

**User outcome**

Receipts, statements, contracts, bills, payslips, tax documents, policies, loan agreements, and proof of payment are searchable and connected to the financial records/actions they support.

**MVP**

- Encrypted document metadata/attachments with type, issuer, date/period, currency, owner/scope, retention choice, expiry/deadline, checksum, and source.
- Link one document to multiple records and maintain an immutable provenance chain from extraction preview to committed data.
- Search structured metadata first; optional full-text OCR remains permissioned and clearly disclosed.
- Action queue combining pay/review/cancel/renew/collect/submit/return/reconcile/update tasks from bills, contracts, claims, goals, and documents.
- User-created tasks, due dates, snooze, dependencies, completion evidence, and household assignment.

**Backend additions**

- `document`, `document_version`, `document_blob_ref`, `document_link`, `extraction_run`, `money_action`, `action_dependency`, and audit events.
- Store blobs through an abstraction with malware/type/size checks, encryption, signed short-lived access, backup, and deletion lifecycle.
- `POST /api/v1/documents`, `POST /api/v1/documents/{id}/extractions`, and `GET /api/v1/actions?status=&assignee=`.

**Safety rules**

- Extraction is a reviewable proposal; the original file and extracted fields have separate deletion/retention controls.
- Never send documents to an AI provider without explicit per-source consent and a disclosed retention/data-location policy.
- Household access is document-specific; linking a private document to a shared bill must not expose the file automatically.
- Passwords, full card data, authentication secrets, and unnecessary government identifiers should be rejected/redacted rather than stored casually.

**Acceptance criteria**

- Every extracted field links to source/document and confidence; users can correct it before commit.
- Deleting an allowed source blob does not corrupt posted activity but leaves provenance indicating the source was removed.
- Action completion does not itself post a payment/cancellation unless a separate verified financial event exists.
- Export and deletion cover both metadata and blobs and report failures clearly.

### FT-24 - Physical cash, gift cards, store credit, and rewards (P3)

**User outcome**

Cash-like value that normally falls outside bank feeds no longer disappears from the user's money picture.

**MVP**

- Physical wallet/cash account with manual spend, transfer, count/reconciliation, and currency.
- Gift card/voucher/store-credit account with issuer, balance, expiry, restrictions, reloads, redemptions, and refunds.
- Refundable deposits (rental/security/key/utility) as claims/receivables with expected return and evidence.
- Loyalty/reward balances with units, expiry, redemption restrictions, and optional manually confirmed cash-equivalent value.

**Business rules**

- Purchasing a gift card transfers value from cash to restricted value; it is not consumption until redeemed or expired, subject to user reporting policy.
- Rewards with no dependable redemption value are non-monetary units, not cash/net worth.
- Store-credit refunds remain a restricted asset rather than reversing cash unless cash was actually returned.
- Expiry/fees/write-offs create explicit loss/expense events.

**Backend additions**

- Reuse `financial_account` with valuation/restriction metadata plus `stored_value_terms`, `reward_program`, and expiry events.

**Acceptance criteria**

- Issue/purchase + redemption + expiry/fee + closing balance reconcile in native units.
- Restricted value is separately visible from liquid cash and excluded from runway by default.
- Manual balance adjustments require a reconciliation reason and preserve history.

### FT-25 - Utility usage and tariff intelligence (Later)

**User outcome**

The user can tell whether an energy, water, phone, or internet bill changed because of usage, unit price, fixed charges, taxes, a promotion ending, or a contract change.

**MVP for one local utility domain**

- Contract/tariff, billing period, meter/service identifier (masked), usage quantity/unit, unit-rate phases, standing/fixed charge, tax, credit/adjustment, and total.
- Manual bill/reading import first; provider/smart-meter data only through approved integrations.
- Usage-versus-rate decomposition, seasonal baseline, estimated-versus-actual reading, unusual-usage suggestion, contract end/price-change date, and scenario comparison.
- Household/project allocation and home-move workflow.

**Backend additions**

- `service_point`, `meter_reading`, `usage_observation`, `tariff`, immutable `tariff_version`, `tariff_rate`, and `utility_bill` linked to FT-18.
- Tariff comparison is another scenario strategy using the user's usage curve and effective-dated rates.

**Key business rules**

- Never infer lower usage from a lower bill without separating rate/standing-charge/tax changes.
- Estimated meter readings and missing periods are visibly different from actual observations.
- Savings comparisons use the same usage profile and include standing charges, taxes, term, exit fees, and known price phases.
- Consumer rules are local and time-sensitive. For example, Ofcom requires UK telecom contract/end-of-contract price information in defined circumstances ([Ofcom contract guidance](https://www.ofcom.org.uk/phones-and-broadband/saving-money/in-or-out)), while Ofgem publishes energy cap unit rates and standing charges by region/payment type ([Ofgem data](https://www.ofgem.gov.uk/information-consumers/energy-advice-households/energy-price-cap-unit-rates-and-standing-charges)). These illustrate why policies cannot be global constants.

**Acceptance criteria**

- Usage × applicable tier/time rate + fixed charge + tax + adjustments reconciles to the bill or exposes the difference.
- Comparison results state data period, location/tariff eligibility, and unmodeled terms.
- No supplier recommendation is ranked by affiliate payment.

### FT-26 - Event and project money (P3)

**User outcome**

Trips, weddings, moving home, renovations, education, a new child, care, medical events, and other finite life projects get one financial workspace from estimate through final settlement.

**MVP**

- Project date range, people, reporting currency, budget categories, quotes, commitments, purchases, shared expenses, claims, documents, goal funding, and expected refunds/deposits.
- Estimated, committed, paid, recoverable, and final cost shown separately.
- Multi-currency cash/settlement view and contingency reserve.
- Post-project close comparing estimate, committed cost, final personal/household cost, and unresolved claims.

**Backend additions**

- `money_project`, `project_member`, `project_budget`, and polymorphic links to goals, activities, bills, purchases, shared expenses, claims, and documents.
- A project is a reporting context, not a new source of money or duplicate transaction store.

**Acceptance criteria**

- Project totals are views over canonical records and reconcile to them.
- Shared cost and recoverable amounts are not counted in personal final cost twice.
- Closing a project does not hide open refunds, IOUs, warranties, or installments.
- Permissions can share project detail without granting household-wide account access.

### FT-27 - Insurance and protection map (Later)

**User outcome**

The user knows which important people/assets/liabilities have protection, what it costs, when it renews, the deductible/excess, and where the evidence is when a claim is needed.

**MVP**

- Policy type, provider, policy number (masked), insured people/assets, coverage summary/limits, deductible/excess, premium schedule, start/renewal/end, beneficiary/contact notes, and documents.
- Link premiums to bills/payments and policies to property, vehicle, travel/project, liability, or person.
- Renewal/action reminders and claim workflow through FT-22.
- A user-authored protection checklist; do not calculate a mysterious universal “coverage score.”

**Backend additions**

- `insurance_policy`, immutable `policy_version`, `coverage_item`, `insured_resource`, and links to contracts/documents/recovery cases.

**Boundaries and acceptance criteria**

- The product records and explains user/provider terms; it does not determine legal coverage, recommend a product, broker insurance, or guarantee a claim.
- Premium, tax/fee, refund, and claim payout are separate flows.
- Renewal changes preserve the policy version active for past claims.
- Sensitive health/beneficiary data receives stricter field minimization and visibility than ordinary household spending.

### FT-28 - Family support, allowances, gifts, and giving (Later)

**User outcome**

Money exchanged for care and relationships can be recorded with its real intent: allowance, shared support, remittance, gift, donation, reimbursement, or loan.

**MVP**

- Scheduled allowance/family support/remittance with payer, recipient label, purpose, currency, fees, and expected/posted status.
- Explicit classification as gift, personal support, shared expense, reimbursement, or claim/loan; changing classification shows reporting/tax-information implications without giving tax advice.
- Child/teen virtual buckets for spend/save/give goals using parent-controlled ledger entries; no payment account in the first version.
- Donation recipient, purpose/campaign, restricted/unrestricted note, recurring commitment, and receipt/document.
- Family-care and child-related project/category views without public comparison or judgment.

**Safety rules**

- Minor accounts require age-appropriate consent, guardian control, minimal profiling, and no targeted financial advertising.
- Cross-border remittance quotes/execution, charitable tax relief, and benefit eligibility require regulated/sourced integrations and local review.
- A gift must not silently become a receivable; a loan must not be presented as a gift to improve spending reports.

**Acceptance criteria**

- Transfers to owned household accounts remain transfers; external support/gifts use explicit counterparty and purpose.
- Remittance fee and FX spread are separate from recipient amount.
- Private family notes and minor activity are excluded from household-wide sharing/AI by default.

### FT-29 - Credit cards, BNPL, overdrafts, and installments (P2)

**User outcome**

The user sees what was purchased, what is owed, when it must be paid to avoid fees/interest, and how installments affect future liquidity.

**MVP**

- Credit-card account with purchase/refund/payment/fee/interest, statement period, statement balance, minimum/full payment, due date, limit, and pending authorizations.
- Purchase installments with principal schedule, interest/fee, first/last due date, remaining amount, early settlement terms, and linked purchase.
- BNPL plan with provider, purchase, installments, late fee terms, refund allocation, and autopay account.
- Overdraft/credit-line utilized balance, rate/fee, limit, and repayment priority.
- Upcoming required/full-pay commitments feed FT-03/04; credit-card payment remains a transfer/debt reduction, not spending again.

**Backend additions**

- `credit_account_terms`, `statement`, `statement_line_match`, `payment_requirement`, `installment_plan`, `installment`, and links to purchase/bill activities.
- `GET /api/v1/credit/commitments` and `POST /api/v1/statements/{id}/reconcile`.

**Critical business rules**

- Purchase date, posting date, statement date, due date, and payment date are distinct.
- Interest-free/grace behavior is policy- and account-specific; never assume all new purchases remain interest-free when a balance is carried.
- Refunds must reduce the correct purchase/plan/statement and may not remove a payment due immediately.
- Available credit is not liquid wealth or income.
- Minimum-payment projections disclose that rates/fees and future spending can change the result.

**Acceptance criteria**

- Prior balance + purchases/fees/interest - payments/refunds/credits = closing balance.
- Statement lines reconcile to activities without duplicating already imported transactions.
- Spending reports recognize the underlying purchase once and exclude card payoff.
- Commitments distinguish minimum required, full statement payoff, scheduled installment, and optional extra payment.

### FT-30 - Tax and government-money calendar (Later)

**User outcome**

The user can prepare documents and cash for known filing/payment dates, refunds, benefits, grants, pension limits, and other government-related money without the app pretending to be a tax authority or adviser.

**MVP for one jurisdiction and user type**

- Effective-dated calendar entries for filing, estimated payment, property/vehicle tax, benefit renewal, pension/allowance deadline, document issue, and expected refund.
- User-confirmed applicability, required documents/actions, reserved amount, actual payment/refund, and official source link/version.
- Connect FT-10 reserves, FT-20 payslip withholding, FT-21 invoices, FT-23 documents/actions, and FT-27 policy data only when relevant and consented.

**Backend additions**

- `jurisdiction_event_definition`, `user_applicability`, `tax_period`, `information_estimate`, `government_claim`, and policy-source review metadata.

**Boundaries and acceptance criteria**

- Start as calendar/reserve/document preparation. Exact liability, filing, eligibility, optimization, and submission require professional/jurisdictional validation.
- Every rule has jurisdiction, affected user type, effective dates, official source, last-reviewed date, and named maintenance owner.
- Estimates are labeled, versioned, and never replace official calculations/notices.
- A rule that is stale or not confirmed applicable is suppressed or strongly qualified rather than guessed.

### FT-31 - Multi-account cash, funding source, and balance control (P1 foundation)

Full design: [cash-accounts-and-funding-design.md](cash-accounts-and-funding-design.md).

**User outcome**

The user can hold money in multiple bank, savings, brokerage, wallet, foreign-currency, stored-value, and credit/liability accounts. For every posted trade, purchase, bill payment, transfer, repayment, or refund, the affected account is explicit and balances remain reconcilable.

**Core decision**

- Yes, the user should choose which eligible account funds a manual action or visibly confirm a remembered default.
- A posted monetary action adds/removes value through account postings; balances are derived projections, not arbitrary mutable fields.
- Not every record moves cash: a bill issued, scheduled payment, goal allocation, expected income, shopping-list item, and scenario affect obligations/plans/forecasts only.
- Multiple funding legs support later split tender; transfers always have source and destination.
- Credit/overdraft capacity is displayed separately and never counted as cash or net worth.

**MVP**

- `FinancialAccount` types for current/checking, savings, brokerage, physical cash, credit card, and liability.
- `FULL_LEDGER`, `BALANCE_SYNC`, `HOLDINGS_ONLY`, and `MANUAL_VALUE` tracking modes.
- Native-currency cash pockets; no summing/withdrawing across currencies without explicit FX legs.
- Posted, cleared, pending, provider-available, projected, reserved, and committed balance views with unambiguous labels.
- Cash deposit/withdrawal, owned transfer, fee, selected-account trade settlement, bill payment, card purchase/payment, loan principal/payment, and refund postings.
- Funding preview showing account balance before/after, FX/fees, policy warnings, and affected bill/security/claim before confirmation.
- Statement reconciliation and an explicit adjustment activity instead of silently replacing a computed balance.

**Negative balances**

Negative balances must be representable because overdrafts, margin, fees, import gaps, and real historical data exist. Behavior is per account:

- `HARD_FLOOR`: block a new manual action below zero.
- `SOFT_FLOOR`: allow only after a clear warning/confirmation and surface the breach.
- `AUTHORIZED_LIMIT`: allow to an effective-dated overdraft/margin limit with terms.
- `TRACK_REALITY`: retain imported/historical truth even when it breaches a rule, then create a reconciliation warning.

If an asset cash account is negative, show the statement balance as negative and treat the negative portion as an overdraft liability for gross asset/liability reporting. Unused credit is never an asset.

**Trade behavior**

- A buy deducts gross price + fee + tax from the selected brokerage cash pocket and increases security quantity/cost basis.
- A sell reduces quantity and credits net proceeds to brokerage cash.
- Funding a broker from a bank is a separate linked transfer; withdrawing sale proceeds is another transfer.
- Auto-FX requires explicit source/target currency legs, executed/reference rate, spread, and fees.
- Existing portfolios migrate as `HOLDINGS_ONLY` with untracked cash until users provide/reconcile actual cash; never invent historical cash from trade totals.

**Backend/API additions**

- `financial_account`, `account_cash_pocket`, `account_posting`, `security_posting`, `account_limit_policy`, `funding_preference`, `account_balance_projection`, and `account_reconciliation`.
- Task-specific preview/commit commands for transfers, trades, bill payments, purchases, and claim settlements, all emitting the common immutable ledger.
- Exact decimal strings, client-event idempotency, deterministic multi-account lock ordering, optimistic versions, and stable insufficient-funds/limit/conflict problems.

**Acceptance criteria**

- Replaying activities reproduces every account/currency balance exactly.
- An owned transfer changes neither household net worth nor income/spending except explicit fees/FX.
- A card purchase plus later card payment records spending exactly once.
- Borrowed/lent principal does not become income/spending; linked liabilities/receivables reconcile.
- Full-ledger trades cannot create/destroy cash silently and state their settlement account.
- Imported negative facts are preserved; new manual actions follow the selected account policy.
- Concurrent/retried actions cannot overspend silently or duplicate postings.
- Holdings-only accounts visibly qualify cash/net-worth/performance coverage until reconciled.

### FT-32 - Real Asset Lifecycle and Total Cost of Ownership (P3, vehicle-first)

Full design: [real-asset-lifecycle-tco-design.md](real-asset-lifecycle-tco-design.md).

**User outcome**

The user sees what a high-value physical asset has actually cost to acquire, own, use, maintain, finance, and eventually dispose of. They can view economic cost per kilometre, mile, hour, cycle, unit of output, or month; current value and associated debt; maintenance and warranty status; and a transparent keep/repair/replace comparison.

Vehicles are the first template, but the common engine supports motorcycles/bicycles, appliances, heating/cooling systems, phones/computers, tools/equipment, and solar/battery systems through typed usage and cost policies.

**Critical business rules**

- Keep **cash burden**, **economic TCO**, **net-worth position**, and **forecast cost** separate.
- Economic TCO while held is broadly acquisition/opening value plus capital additions and eligible lifecycle costs, less current/closing value and confirmed recoveries/income.
- Never add both full purchase price and depreciation to the same economic total.
- Loan principal affects cash and liability balances but is not an extra economic cost; interest and fees may be costs.
- Actual, accrued, committed, and forecast values remain separate. Warranty coverage reduces cost only when a claim/recovery is confirmed.
- Historic costs use point-in-time FX; current value uses valuation-date FX. Valuation source, range, date, and confidence flow into result coverage.
- Cost-per-unit reports expose the exact numerator, denominator, period, inclusion profile, readings, and missing-data warnings.
- Cash-date and economic recognition differ: an annual premium can leave cash today but be recognized across its coverage period without creating duplicate transactions.
- A keep-versus-replace scenario starts from today's realizable value and future costs; historic purchase/repair spending is sunk and remains visible only as history.

**MVP**

- A `physical_asset` lifecycle linked one-to-one where applicable with a durable FT-19 `purchase_item` rather than duplicating the purchase.
- Acquisition/funding/loan links, cost allocations over canonical ledger activities, and manual current-value observations.
- Vehicle odometer readings with km/mile normalization, correction, and reviewed reset/replacement events.
- Fuel/charging, maintenance, repair, insurance, tax/registration, and finance-interest classification.
- Actual economic TCO, cash burden, net-worth context, cost/month, and cost/km with a traceable breakdown.
- Service due by date or distance; warranty/insurance expiry and linked documents/actions.
- Sale/trade/disposal workflow and realized lifecycle result.
- Exportable asset timeline, readings, costs, valuations, coverage, and calculation assumptions.

**Backend/API additions**

- `physical_asset`, `asset_identifier`, `asset_interest`, `asset_lifecycle_event`, `asset_relation`, `asset_cost_link`, `usage_meter`, `meter_reading`, `resource_consumption_event`, `asset_valuation`, `depreciation_policy_version`, `maintenance_plan`, `service_event`, `warranty_coverage`, `asset_disposal`, and `asset_tco_profile`.
- A `physicalassets` module that consumes ledger, purchase, contract/protection, document, valuation, FX, and scenario interfaces without creating a shadow transaction ledger.
- `GET /api/v1/physical-assets/{id}/tco?from=&to=&basis=&unit=&currency=&profileId=` returns economic/cash views, cost breakdown, values, denominator/readings, coverage, and calculation-run provenance.
- Preview/commit commands for cost allocations and financial actions; creating an expected service, warranty, or forecast never moves cash.

**Acceptance criteria**

- Acquisition, financing, current value, costs, recoveries, and linked liability reconcile without double counting.
- A card-paid service and later card payment produce one maintenance cost; a refund/claim adjusts it once.
- Missing or inconsistent usage produces no fabricated per-unit result.
- Observed values and forecast depreciation curves are visibly different and versioned.
- Backdated activities, readings, valuations, allocations, corrections, meter resets, multi-currency costs, and disposal rebuild deterministically.
- Every headline result traces to canonical ledger facts, valuation/FX observations, meter readings, allocation and inclusion policies, and a reproducible calculation run.

**Why it can differentiate**

Dedicated vehicle logs already track fuel, expenses, reminders, and cost per distance. This product can connect those jobs to real bank/card accounts, receipts, loans, net worth, insurance claims, warranties, historical FX, Decision Replay, and replacement scenarios, then reuse the same trustworthy engine for non-vehicle assets. The connected evidence chain—not a generic TCO number—is the reason to choose it.

## Additional long-horizon money surfaces

The core primitives above can later support these verticals without creating separate ledgers:

| Surface | Reuse | Potential first outcome |
|---|---|---|
| Home ownership and renting | Projects, contracts, bills, insurance, debt, purchases | True monthly/annual home cost and rent-versus-buy replay |
| Vehicles and mobility | FT-32 physical-asset lifecycle, purchases, installments, insurance, projects | Actual and forecast cost per month/km, service/warranty state, and keep/replace decisions |
| Healthcare and care | Bills, claims, insurance, projects, documents | Out-of-pocket cost, reimbursement, and care commitment calendar |
| Education | Goals, projects, debt, income scenarios | Tuition/funding schedule and study-versus-work scenario |
| Travel | Projects, shared expenses, FX, insurance, claims | Personal final trip cost after splits/refunds in home currency |
| Retirement and pensions | Accounts, income, goals, tax policies, scenarios | Contribution and future-income plan with explicit uncertainty |
| Estate and continuity | Documents, account inventory, insurance, permissions | Private “what exists and who needs to know” continuity pack |
| Donations and community funds | Giving, projects, shared expenses, documents | Transparent contribution/use history and receipts |
| Benefits and entitlements | Government calendar, income, household scope | User-confirmed renewal/action checklist; no automatic eligibility promise |
| Resale and circular ownership | Purchases, ownership events, price observations | Net lifetime cost after resale and repair |
| Financial identity and credit files | Documents, disputes, liabilities | Credit-report review/action workflow through authorized sources |

“Think big” should mean a reusable model and a coherent money history, not that all of these surfaces launch together.

## Asset and money-domain expansion order

Add domains according to whether the ledger and valuation engine can model their real behavior, not according to the length of a marketing list.

| Order | Domain | Required activities/valuation | Main user value | Main risk |
|---:|---|---|---|---|
| 1 | Cash, FX, current/savings accounts | Deposits, withdrawals, transfers, FX legs; cash balance and dated FX | Reconciliation, net worth, comparisons, resilience | Confusing reference FX with execution/spread |
| 2 | Spending, bills, and income | Purchase/income/refund/reimbursement/transfer classification; recurrence and documents | Monthly money truth and inputs for every later plan | Double counting transfers, cards, claims, and refunds |
| 3 | Personal claims/shared expenses | Issue, allocate, confirm, repay, forgive/write off; face/confidence value | Who owes whom without corrupting spending/net worth | Privacy, relationship conflict, regulated collection/lending boundary |
| 4 | Credit cards/installments/BNPL/debt | Purchase, borrow, principal, interest, fee, statement, rate change; amortized balance | Commitment visibility, debt-versus-invest, resilience | Incorrect grace/amortization rules or advice claims |
| 5 | Term deposits | Open, accrue, tax, mature, break, renew; policy-based accrued value | Distinctive local alternative and cash planning | Product-specific rates/terms and tax drift |
| 6 | Stored value, refunds, deposits, rewards | Issue/purchase, redeem, expire, recover; restricted/manual value | Close everyday-money gaps | Treating restricted/uncertain value as liquid cash |
| 7 | Funds/ETFs/indices/gold | Buy/sell/income/corporate actions; total-return series | Common benchmarks and diversified alternatives | Licensing and price-only comparison |
| 8 | Bonds/fixed income | Buy/sell, coupon, accrued interest, maturity; clean/dirty price | Income and liability matching | Considerably richer math/data |
| 9 | Crypto | Transfer, trade, network fee, staking; 24/7 price policy | User demand/multi-asset completeness | Wallet basis, scams, exchange/data coverage |
| 10 | Vehicles/durable physical assets | Purchase/funding, usage, cost, maintenance, recovery, valuation, disposal | Actual cost per km/month/cycle and keep/replace decisions | Manual capture, valuation uncertainty, double counting |
| 11 | Property/manual assets | Purchase/sale, rent, expense, debt, appraisal | Whole net worth and life decisions | False precision, illiquidity, transaction costs |
| 12 | Pensions/tax wrappers | Contribution, employer match, fee, income/withdrawal; wrapper policy | Long-term goals | Jurisdiction and tax complexity |
| 13 | Insurance/protection | Premium, refund, claim payout; contract/coverage state | Protection map and claim readiness | Sensitive data and advice/broker boundary |
| 14 | Collectibles/private business | Purchase, costs/income, appraisal, sale | Broader wealth view and ownership history | Sparse valuations and specialist accounting |

## Suggested backend boundaries

Keep a modular monolith. Suggested packages/modules are:

- `ledger`: accounts, activities, money/asset legs, correction, projection.
- `importing`: files, parsers, previews, fingerprints, reconciliation.
- `reference`: instruments, currencies, calendars, classifications.
- `marketdata`: providers, series, observations, revisions, licenses, quality.
- `valuation`: point-in-time native/reporting-currency values and daily projections.
- `performance`: TWR, XIRR, attribution, risk, benchmark.
- `scenario`: scenario versions, strategy interfaces, runs, comparisons.
- `policies`: localization, deposit, debt, FX, inflation, tax-information policies.
- `spending`: categories, merchants, rules, activity splits, plans, monthly close.
- `cashflow`: recurring patterns, expected occurrences, calendar, and forecasts.
- `claims`: counterparties, IOUs/private loans, shared-expense allocations, reimbursements, and settlements.
- `contracts`: bills, subscriptions, price phases, renewals, and credit/installment terms.
- `purchases`: receipts, line items, shopping lists, ownership, returns, warranties, and price book.
- `physicalassets`: durable-asset identity, lifecycle, usage meters, cost links, service, valuations, and TCO orchestration.
- `income`: income streams, payslips, benefits, invoices, and employer/client reimbursements.
- `documents`: secure attachments, extraction provenance, deadlines, and money actions.
- `planning`: goals, resilience, contributions, purchasing-power baskets.
- `projects`: event/life-project scopes over canonical financial records.
- `protection`: policies, coverage records, and claims links.
- `decisions`: journal, scheduled review, outcome comparison.
- `household`: membership, grants, scoped aggregation.
- `insights`: deterministic facts, briefs, delivery preferences.

Avoid module-to-module table access. Publish application services/read models inside the same process. Use an outbox/job table for rebuilds, imports, and notifications, but do not introduce microservices or a streaming platform for this scale.

### Shared value objects

- `Money(amount, currency)`
- `Quantity(amount, instrument/unit)`
- `EffectiveTime(instant, localDate, timezone/calendar)`
- `ObservationRef(seriesId, date, revision, quality)`
- `CalculationContext(reportingCurrency, asOf, policyVersions, calculationVersion)`
- `DataCoverage(priced, unpriced, stale, estimated, warnings)`
- `Allocation(total, parts, roundingPolicy)` for receipt lines, shared expenses, payments, and claims
- `ObligationState(faceAmount, outstandingAmount, currency, dueAt, confidence, status)`
- `SourceEvidence(sourceType, sourceId, extractionVersion, confidence)`
- `MeasuredQuantity(amount, unit, dimension, conversionVersion)` for distance, time, cycles, energy, and output.
- `ValuationRange(low, base, high, currency, asOf, source, confidence)` for illiquid physical assets.

These types should make illegal operations—such as adding TRY and USD or valuing without an as-of policy—difficult to express.

## Implementation sequence

This sequence starts only after the immediate security/database/accounting containment work in [prioritized-roadmap.md](prioritized-roadmap.md).

| Release slice | User-visible result | Backend work | Exit gate | Relative size |
|---|---|---|---|---:|
| **A. Trusted tracker** | Correct fees, corrections, imports, balances, coverage | Repair current accounting; FND-01 subset; migration/test harness | Golden trade/import/retry/reconciliation tests pass | XL |
| **B. Unified money truth** | Multiple cash/brokerage accounts, selected funding, reconciled timeline, liabilities, historical net worth | Full FND-01, FND-02, FND-04; FT-01, FT-31; claim/obligation primitives | Account/household values reconcile, funding is explicit, and missing cash is visible | XL |
| **C. Everyday money** | Correct monthly spending, income, cards/installments, bills, documents/actions | FT-15, FT-18, FT-20, FT-23, FT-29 | A month closes with cash, spending, debt, refunds, and transfers reconciled | XL |
| **D. Explain** | Honest investment performance and whole-money change bridge | FND-03; daily NAV; FT-02 plus spending/income classification | Decomposition residual and benchmark/cash-flow fixtures pass | L |
| **E. Distinctive comparison** | Single-amount Decision Replay across share/index, FX, gold, deposit | FT-06 plus deposit/FX part of FT-07 | Reproducible no-look-ahead scenarios with source panel | XL |
| **F. Commitments and resilience** | Money calendar, available after commitments, goals, irregular-income reserve | FT-03, FT-04, FT-05, FT-10 | Forecast terms trace to facts/plans and avoid double counting | XL |
| **G. People and shared money** | Personal IOUs, split expenses, reimbursements, selective household view | FT-13, FT-16, FT-17 | Claims reconcile and privacy/permission tests pass | XL |
| **H. Purchase and recovery** | Receipt-level shopping, refunds, warranties, stored value, project money | FT-19, FT-22, FT-24, FT-26 | Receipt/payment/item/claim totals reconcile end to end | XL |
| **I. Physical-asset ownership** | Vehicle value, cost/month and cost/km, service/warranty state, realized disposal result | FT-32 vehicle MVP + FT-27/29 links | Acquisition/cash/debt/value/usage/cost/disposal fixtures reconcile and every ratio is traceable | XL |
| **J. Deep localized comparison** | Real cash-flow replay, debt, personal inflation, utility/purchase templates | FT-07 debt, FT-08, FT-09, FT-25 | Actual and alternatives share dated flows and effective local policies | XL |
| **K. Income/protection automation** | Freelancer receivables, insurance map, family flows, briefs | FT-14, FT-21, FT-27, FT-28 | Evidence, reminder, sensitivity, and regulatory boundaries pass review | XL |
| **L. Advanced planning** | Rebalancing, tax calendar, pensions/bonds/property, uncertain forecasts | FT-11, FT-30, and individually validated domains | Specialist/data/regulatory review per feature | Ongoing |

Relative size is intentionally not a calendar estimate. Team size, production schema recovery, data licenses, and reconciliation quality can change duration dramatically.

### First feature backlog after the current correctness gate

1. Approve ADRs for activity semantics, currency/rounding, economic order, correction, valuation, and calculation versioning.
2. Add `financial_account`, native-currency cash pockets, tracking/negative policies, immutable activity/account/security postings, and idempotency storage.
3. Implement cash deposit/withdrawal, linked transfer, selected brokerage funding, buy/sell cash legs, fee/tax, dividend/interest, correction/reversal.
4. Build deterministic cash/position projections, funding preview/concurrency checks, and shadow-reconcile current positions without inventing historical cash.
5. Add CSV import batch/row preview, fingerprints, reconciliation totals, and commit.
6. Add provider/series/observation/revision schema and one licensed historical price adapter plus historical FX adapter.
7. Build valuation context, coverage metadata, daily account/portfolio NAV, and affected-range rebuild.
8. Ship FT-01/FT-31 current/historical money timeline, multi-account balances, reconciliation, funding-source chooser, and trace-down endpoint.
9. Implement TWR/XIRR/decomposition golden fixtures and FT-02 APIs.
10. Add pure scenario domain, immutable versions, calculation-run provenance, and result cache invalidation.
11. Implement market-instrument, FX-hold, inflation-deflator, and fixed-deposit strategies.
12. Ship FT-06 behind a feature flag with an assumptions/source panel and exportable calculation input.
13. Run user tests before starting the broader everyday-money or planning surface.

### First broader-money backlog after validation

1. Approve an ADR distinguishing posted activities, claims/obligations, contracts/bills, planned occurrences, purchases, documents, and scenarios.
2. Add category/merchant/activity-split and ordered rule models; ship transaction inbox and transfer/refund/card-payment characterization fixtures.
3. Add spending-plan periods and a month-close reconciliation report; ship FT-15 in `TRACK_ONLY` mode before envelope budgeting.
4. Add service-contract, bill, price-phase, bill/payment matching, and action-date models; split FT-03 prediction from FT-18 obligation truth.
5. Add credit-card statement, installment, BNPL, and payment-requirement models; prove purchase/card-payment non-duplication.
6. Add pay-statement and income-component preview; reconcile gross/deductions/net and employer reimbursement claims.
7. Add counterparty/claim/claim-event with interest-free IOUs; prove lender/borrower cash, principal, spending, and net-worth invariants.
8. Add shared-expense payer/share allocation over canonical purchases and deterministic minor-unit rounding.
9. Add secure document/attachment abstraction, extraction preview, malware/type/size controls, and Money Action Queue.
10. Add receipt/line-item/purchase preview and payment matching; do not start product catalogs or retailer price comparison yet.
11. Add recovery cases for refund/return/warranty/reimbursement with deadlines and linked evidence.
12. Run a vehicle-first FT-32 validation/MVP after purchase, recovery, document, valuation, and account-link semantics are stable; do not begin with telematics or automated resale pricing.
13. Only then select another deeper vertical—utility, freelancer, insurance, event/project, or family—for a complete user/data/regulatory validation spike.

## Data and integration strategy

### Build internally

- Ledger, spending/transfer/refund semantics, claim/allocation accounting, reconciliation, valuation orchestration, policy semantics, calculation runtime, Decision Replay, goal/resilience logic, and data-quality presentation.
- These are the trusted core and the primary differentiation.

### Buy or license where appropriate

- Exchange/adjusted-price/corporate-action data.
- Broker/bank connectivity and normalized raw transaction feeds.
- Specialized security master/fund look-through data.
- OCR/document extraction, malware scanning, and encrypted object storage where an audited provider is safer than building the infrastructure.
- Product catalogs, retailer prices, utility usage/tariff data, and consumer-rights content only where licenses and geographic coverage support the promised result.
- Email/push delivery infrastructure.

Buying data does not transfer responsibility for validation, provider outage handling, revisions, user consent, or licensing rights.

### Source hierarchy

1. User's actual execution/statement data.
2. Licensed authoritative market/product data.
3. Official reference series such as TCMB/TÜİK/ECB.
4. User-declared assumptions/manual observations.
5. Estimates, only when clearly labeled and never silently mixed with facts.

Store provider terms, redistribution/display restrictions, attribution, retention, and allowed derived use beside the adapter. A technically accessible endpoint is not automatically licensed for a commercial product.

## Product validation before building everything

Competitor research cannot answer whether the target users will change behavior or pay. Validate these hypotheses in order.

### H1 - Decision Replay is a compelling switching reason

- Interview 12-20 people who hold at least three of investments, FX/gold, deposits, and debt.
- Ask for the last real decision they compared manually; observe their spreadsheet/app process.
- Test a clickable prototype using their own anonymized dates and alternatives.
- Success signal: at least half can name a recurring use, and several volunteer data/import effort to obtain the result.

### H2 - Users want “available after commitments” without full budgeting

- Prototype the commitment bridge and runway beside a traditional category budget.
- Test comprehension: users must explain what is included/excluded and identify one unsafe assumption.
- Success signal: users return to the forecast weekly and do not mistake it for a guaranteed spend allowance.

### H3 - Visible uncertainty increases trust

- Compare a simple total with a total plus stale/unpriced/source coverage.
- Measure comprehension and whether warnings cause useful correction rather than abandonment.
- Success signal: users can identify incomplete data and resolve it without support.

### H4 - Local depth is worth narrower breadth

- Test Turkey-first flows: TRY deposit, USD/EUR, gold, BIST share/index, CPI, and debt.
- Conduct a market-data/license spike before promising history.
- Success signal: users prefer the locally correct comparison over a competitor with more asset logos but generic assumptions.

### H5 - One reconciled monthly view beats a separate budget and tracker

- Give users a month containing card purchases/payments, brokerage contributions, a friend reimbursement, a refund, cash spending, and an annual bill.
- Ask them to explain consumption, net cash flow, amount saved/invested, and what remains due before showing the app's classification.
- Success signal: users understand and trust the app's separation, correct mistakes quickly, and prefer it to a raw category total.

### H6 - Personal debts and shared expenses belong in the same money timeline

- Interview users who regularly lend to family/friends, split household/travel costs, or await employer/client reimbursements.
- Test private placeholder counterparties versus invited mutual confirmation and ask what must remain private.
- Success signal: users record a real claim, understand its impact on cash/spending/net worth, and find neutral reminders socially acceptable.

### H7 - Receipt-level shopping earns its maintenance cost

- Prototype three outcomes separately: fast receipt-to-expense matching, return/warranty recovery, and item-level price history.
- Measure extraction corrections and whether users scan ordinary groceries after the novelty period.
- Success signal: at least one focused outcome drives repeated capture with a tolerable review burden; otherwise keep receipts for high-value purchases only.

### H8 - The Money Action Queue reduces missed value

- Seed upcoming trial cancellation, bill notice date, unclaimed reimbursement, expiring return, unresolved reconciliation, and goal shortfall.
- Test whether users can identify the next action and supporting evidence without notification overload.
- Success signal: tasks are completed and marked useful; reminders are not broadly disabled.

### H9 - Reconciled asset TCO can replace a vehicle log and spreadsheet

- Interview vehicle owners who track fuel/service in an app or spreadsheet and owners who stopped because entry was too burdensome.
- Build the result from three months of their real transactions, mileage, loan terms, and a user-confirmed current-value range; compare it with a fuel-only cost and an annual ownership estimate.
- Test whether they understand cash burden versus economic cost, can find the source of cost/km, and would keep logging readings/service in return for replacement and warranty decisions.
- Success signal: users correct missing links, return after another fuel/service event, and use the keep/repair/replace view; otherwise keep FT-32 focused on high-value purchase/service records rather than daily capture.

### Instrumentation that respects the product

Track feature events and quality metrics, not raw financial amounts unless strictly necessary and consented. Useful events include import completed, reconciliation resolved, explanation opened, assumption inspected, scenario saved, goal changed, and insight marked incorrect. Do not send transaction descriptions, notes, account numbers, or exact balances to general analytics by default.

## Success measures

### Trust and correctness

- percentage of tracked value reconciled;
- percentage valued with fresh/known-quality observations;
- decomposition residual rate and size;
- import duplicate/error/correction rate;
- percentage of monthly cash movement explained as spending, income, transfer, debt, claim, or unresolved;
- card statement, bill/payment, receipt/payment, and claim/settlement reconciliation rates;
- number and age of unresolved data warnings;
- user-reported incorrect calculation rate.

### Activation

- user reaches a reconciled first account;
- user receives a first return explanation or cash-flow truth;
- user closes a first month with transfers/card payments/refunds correctly separated;
- user runs and understands a first Decision Replay;
- user links a first physical asset and understands cash burden versus economic cost per unit;
- time and manual corrections required to reach first trustworthy insight.

### Ongoing value

- repeat scenario/review use rather than trading frequency;
- commitments caught before due date;
- IOUs/reimbursements resolved without duplicate spending and refunds/returns recovered before deadlines;
- receipt/document capture leading to a completed action rather than unused storage;
- physical-asset costs/readings captured, maintenance or warranty actions completed, and TCO coverage improved;
- goals/reserve plans updated after real changes;
- users resolving data-quality issues;
- brief cards marked useful versus noisy/incorrect;
- export/deletion completion and support failure rate.

### Guardrails

- no increase in impulsive trade prompts or notification-driven activity;
- no hidden sponsored ranking or recommendation;
- warnings understood rather than dismissed;
- no permission leaks in household views;
- no generative statement without structured evidence.

## Business model and incentive design

A trustworthy default is subscription-funded software with a useful manual tier. One possible structure to test:

- **Free/manual:** one household/user, manual/CSV entry, core net worth, limited history, and a small number of saved replays.
- **Paid individual:** automated imports where available, full history, advanced comparisons, goals/resilience, exports, and briefs.
- **Paid household:** selective sharing, more members, shared plans, and private/aggregate permissions.

Do not lock raw data export, deletion, reconciliation, security controls, or correction of financial records behind a premium plan. Avoid selling user data, payment-for-placement products, broker kickbacks that affect analysis, or an ad-supported advice feed. If affiliate revenue is ever used, it must be visually and computationally separate from rankings and scenarios.

## Major risks and deliberate exclusions

| Risk | Mitigation |
|---|---|
| Scope becomes “every money app in one” | Preserve the four-question workflow; each new feature must reuse the ledger/scenario core and improve a target job. |
| Convincing but incorrect financial output | Golden/property/integration tests, calculation versions, reconciliation, residual checks, and visible assumptions. |
| Market-data cost or redistribution blocks the flagship | License spike before UI commitment; provider abstraction; do not scrape as a business model. |
| Product crosses into regulated advice | Educational historical comparisons, user-chosen assumptions, no personalized buy/sell ranking, jurisdictional review. |
| Tax/deposit/debt rules become stale | Effective-dated policy packs, sources, owners/review dates, and explicit informational status. |
| Automation hides sync errors | Balance-versus-activity reconciliation and an import inbox; never trust a provider total blindly. |
| Personal-debt reminders damage relationships or cross regulatory boundaries | Neutral opt-in reminders, dispute/stop controls, rate limits, record-only MVP, and jurisdictional review before lending/collection/payment behavior. |
| Receipts/contracts/payslips create a severe privacy breach surface | Minimize fields, encrypt blobs, strict scoped access, short-lived downloads, consented extraction, retention/deletion controls, and security testing. |
| Item-level shopping becomes high-effort clutter | Validate receipt capture around returns/warranties or price insight; progressively request detail and let low-value purchases remain transaction-level. |
| Opportunity-cost features shame users | Opt-in comparisons, non-financial notes, “worth it” state, quiet controls, neutral language. |
| AI hallucinates or leaks sensitive information | Deterministic calculation first, structured evidence, redaction/consent, no direct AI posting, provider retention controls. |
| Household aggregates reveal private details | Server-side resource grants, aggregate privacy tests, minimum disclosure rules, audit/revocation. |
| Forecasts imply certainty | Historical/future separation, sensitivity ranges, scenario labels, and no probability claims before model validation. |

Do not build yet:

- order execution, copy trading, predictions, automated buy/sell recommendations, or performance leaderboards;
- peer-to-peer lending marketplace, credit underwriting/scoring, automated debt collection, or holding/transmitting settlement funds;
- a public social feed;
- tax filing or exact tax optimization claims;
- generic Monte Carlo “success scores” without calibrated assumptions and user comprehension tests;
- bill negotiation/financial-product marketplace operations;
- autonomous subscription cancellation, dispute/claim filing, or retailer/supplier ranking funded by placement fees;
- complex property, pension, bond, and insurance modeling simultaneously;
- microservices, Kafka, or a data warehouse before the modular monolith needs them;
- generic AI chat presented as a financial adviser.

## Product decision rule

Before approving a feature, require affirmative answers to all of these:

1. Which of Truth, Explain, Compare, or Prepare does it improve?
2. What user decision or anxiety becomes easier?
3. Can the existing ledger/valuation/scenario primitives represent it honestly?
4. What data, license, jurisdiction, privacy, and advice risks apply?
5. What is the smallest end-to-end slice with a testable user outcome?
6. How will the user inspect assumptions and trace the result?
7. What metric proves value without rewarding risky financial activity?

If a feature is merely another chart, AI summary, asset badge, or alert and cannot pass this test, it should not displace the accounting and Decision Replay roadmap.

## Bottom line

The strongest product is not “stocks plus budgeting plus bill splitting.” It is an auditable **money operating system and decision engine** that happens to begin with the project's current investment tracker.

The first marketable moment should be:

> “I imported what I actually did. The app reconciled it, explained what changed my wealth, and replayed the same money against the deposit, currency, gold, debt, and investment alternatives I realistically had—using the rates and conditions from those dates.”

If that experience is trustworthy, fast, and locally accurate, users have a concrete reason to choose the product. The broader money features then deepen the same promise instead of turning it into an unrelated collection of tools.

The recurring-use moment should become:

> “This month is reconciled. I know what I consumed, transferred, invested, borrowed, lent, can reclaim, still owe, and still expect. My bills, card payment, subscriptions, IOUs, refunds, goals, and next actions agree with the same cash and net-worth history.”

Decision Replay supplies the distinctive reason to try the product; monthly truth, claims, commitments, purchases, and actions supply the reason to keep using it.
