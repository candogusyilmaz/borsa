# Real Asset Lifecycle and Total Cost of Ownership Engine

Review date: 2026-08-05

Status: proposed backend and product design. This expands FT-32 in [implementable-features.md](implementable-features.md). No production code was changed as part of this design.

## Decision

Build this as a first-class physical-asset domain connected to the common money ledger, not as a calculator with a few vehicle fields.

The first complete template should be a privately owned vehicle because distance, fuel/energy, service, insurance, finance, and resale value make the value obvious. The same core can later support motorcycles, bicycles, appliances, heating/cooling systems, electronics, tools, machinery, solar/battery systems, and other durable assets by changing the usage meter, cost taxonomy, maintenance rules, and valuation policy.

The primary outcome is:

> Show what an asset has actually cost to acquire, own, operate, maintain, finance, and dispose of; what it costs per month and meaningful unit of use; what remains under warranty; and whether keeping, repairing, replacing, leasing, or renting it is likely to be the better next decision.

Three numbers must remain visibly separate:

1. **Cash burden:** money that has actually left the user's accounts, including down payment, principal repayments, interest, and running costs.
2. **Economic cost:** value consumed through depreciation plus operating, ownership, maintenance, finance, and disposal costs, net of confirmed recoveries or income.
3. **Forecast cost:** future estimates based on a named, versioned assumption set.

Combining those into one unexplained “true cost” would be misleading. In particular, the system must never add both the full purchase price and depreciation to the same economic total.

## Why this belongs in this product

Vehicle-expense applications already cover useful individual jobs. For example, [Drivvo](https://www.drivvo.com/en-US/personal-use/) advertises fuel, expenses, maintenance, routes, reminders, income, and reports, while [Fuelio](https://play.google.com/store/apps/details?id=com.kajda.fuelio) records fill-ups, operating costs, mileage, fuel efficiency, and cost categories. A clone of those functions is not a compelling product position.

Formal lifecycle-cost models are broader. The US Department of Energy describes purchase, fuel, operation, maintenance, replacement, disposal, and loan-related costs in building lifecycle analysis ([DOE Building Science Education](https://bsesc.energy.gov/training-modules/life-cycle-analysis)). NIST's lifecycle-cost guidance treats initial investment, replacements, operating/maintenance/repair costs, residual value, and the time value of money as distinct inputs ([NIST Handbook 135](https://www.nist.gov/publications/life-cycle-costing-manual-federal-energy-management-program-nist-handbook-135-1995)). The DOE vehicle calculator also separates usage assumptions and fuel/energy costs from maintenance, tyres, insurance, registration, purchase/finance, escalation, and discount assumptions ([calculator methodology](https://afdc.energy.gov/calc/cost_calculator_methodology.html)). AAA's 2025 ownership analysis uses six categories: fuel, maintenance/repair/tyres, insurance, licence/registration/tax, depreciation, and finance charges ([AAA](https://newsroom.aaa.com/2025/09/aaa-new-vehicle-costs-drop-to-11577/)).

The opportunity here is to combine that discipline with facts the broader application already plans to know:

- which financial account funded the purchase or bill;
- whether a payment is principal, interest, a transfer, or consumption;
- the receipt and purchased item;
- insurance contracts, claims, refunds, and warranties;
- historical FX and current/manual valuation evidence;
- debt outstanding and its effect on net worth;
- real usage and maintenance history;
- the user's Decision Replay assumptions and alternative uses of money.

That produces an ongoing, reconciled ownership record instead of a one-time estimate.

## Product scope

### Asset templates

| Template | Primary usage denominator | Important costs and signals | Useful decisions |
|---|---|---|---|
| Car, motorcycle, van | kilometre/mile, month, trip | Fuel/electricity, tyres, service, repairs, insurance, tax, parking, finance, depreciation | Keep, repair, replace, EV versus combustion, own versus lease/rent |
| Bicycle/e-bike | kilometre, ride, month | Purchase, battery, charging, tyres, parts, service, theft insurance | Repair versus replace; cost versus transit/car |
| Appliance | cycle, operating hour, month | Energy/water, consumables, repair, warranty, depreciation | Repair versus replace; efficient model payback |
| Heating/cooling system | operating hour, kWh input/output, heating season | Energy, inspections, parts, service plan, installation, replacement | Maintain, retrofit, or replace |
| Phone/computer | month, active day, work hour | Purchase, financing, protection plan, repair, accessories, resale | Upgrade timing; buy versus finance; warranty value |
| Tool/equipment | operating hour, job, output unit | Consumables, calibration, repair, downtime, rental income | Own versus rent; replacement threshold |
| Solar/battery system | kWh generated/delivered, month | Installation, finance, maintenance, replacements, tariffs, export income | Payback and keep/expand/replace |

Property, boats, collectibles, and income-producing equipment can eventually use the engine, but each needs a validated template. Property in particular needs transaction costs, land/building separation, mortgage semantics, rent/income, tax, improvements, and illiquid valuation; it should not be treated as “a very expensive car.”

### Lifecycle states

A physical asset should have a history rather than one mutable status:

`PLANNED -> ORDERED -> ACQUIRED -> IN_SERVICE -> IDLE/STORED/UNDER_REPAIR -> SOLD/TRADED_IN/DISPOSED/LOST`

- Events have an effective timestamp, recorded timestamp, source, evidence, and correction/supersession relation.
- Temporary states such as stored and under repair do not end ownership.
- Sale, trade-in, disposal, loss, theft, or total loss closes the ownership interval but does not delete history.
- Transfer between household members/scopes changes custody/visibility without inventing a sale.
- An asset may have components or sub-assets, such as a vehicle with a replaceable traction battery or a house with a boiler. Component replacement must not erase the parent history.

## Accounting boundaries

### The four views users need

| View | Question | Includes | Does not silently include |
|---|---|---|---|
| Cash burden to date | “How much of my own cash has this required?” | Owner-funded acquisition cash, principal paid, interest/fees, posted operating and ownership outflows, less financing proceeds applied to acquisition and actual cash recoveries/income | Unpaid bills, outstanding principal not yet paid, current market value, hypothetical future costs |
| Economic TCO to date | “How much value has ownership consumed?” | Depreciation/value loss, eligible ownership/operating/maintenance/finance costs, disposal cost, less confirmed recoveries/income | Loan principal as a second cost; forecast costs |
| Net-worth position | “What is it worth to me now?” | Current asset value less associated outstanding liabilities | Historic spending as another liability |
| Forecast/scenario | “What may it cost from here?” | Future value loss, cash flows, replacements, residual value, escalation/discount assumptions | Sunk costs unless the question explicitly asks for full lifecycle history |

The UI should make the selected basis part of the title, not hide it in an information tooltip.

### Acquisition basis

For economic TCO, define an asset's starting basis as:

```text
initial acquisition basis
  = agreed cash price
  + directly attributable acquisition/installation costs
  - purchase discounts and rebates

adjusted basis at a later date
  = initial acquisition basis
  + subsequent capitalized improvements
```

Directly attributable items may include delivery, registration/title, inspection, installation, and required commissioning. Optional accessories and later improvements need a user-visible capitalization policy. This is an economic basis; tax/book basis is a separate jurisdiction-specific concept.

Financing does not change what was acquired:

- loan principal creates/increases a liability and funds the acquisition;
- down payment and principal repayments affect cash burden;
- principal is not an extra economic cost on top of acquisition basis;
- interest, origination fees, and eligible early-repayment fees are financing costs;
- the outstanding loan remains a liability in net worth even if it is linked to the asset.

Cash burden is financing-aware. If loan proceeds enter the user's bank account and immediately pass to the seller, the loan draw and financed portion of the seller payment net out; the initial personal burden is still the down payment and fees, not both the full seller payment and the loan. Refinancing principal flows likewise net out except for fees, interest, or additional cash actually taken/paid.

```text
cash burden to date
  = owner-funded acquisition cash
  + principal repaid from owned accounts
  + paid finance and lifecycle costs
  - cash recoveries and attributable income received
```

### Economic TCO while the asset is held

For an ownership period with defensible opening and closing values:

```text
economic TCO
  = opening asset value
  + capital additions during the period
  - closing asset value
  + operating costs
  + fixed ownership costs
  + maintenance and repair costs
  + finance costs
  + other eligible lifecycle costs
  - confirmed recoveries and attributable income
```

For the full period from acquisition, `opening asset value` is the initial acquisition basis. The first four terms involving value form the economic depreciation/appreciation component. This formulation prevents a capital improvement that raises value from being counted as fully consumed on the day it is purchased.

For a disposed asset:

```text
realized lifecycle TCO
  = initial acquisition basis
  + capital additions
  + eligible lifecycle costs
  + disposal costs
  - actual sale/trade/salvage proceeds
  - confirmed recoveries and attributable income
```

The gross cost and deductions should also be displayed separately. A user who earns rental/delivery income from an asset should be able to see both “cost to own” and “net cost after income”; netting everything into one small number can hide risk and activity.

### Prospective decisions and sunk costs

“What has this asset cost me?” and “should I keep it?” use different starting points. A keep-versus-replace decision should normally compare costs from today:

```text
prospective keep cost
  = current realizable value sacrificed by keeping
  - forecast residual value at horizon
  + future operating, ownership, repair, finance, and disposal costs
```

Historic purchase price and past repairs are sunk for that decision, although they remain visible in the ownership history. The engine should offer a full-lifecycle view and a from-today decision view; it must not let the user accidentally use sunk cost as a reason to keep an uneconomic asset.

### Actual, accrued, committed, and forecast are different

- A posted fuel purchase or service payment is an actual cash fact.
- A completed repair with an unpaid invoice is an accrued/owed cost and obligation, not cash paid.
- Next year's insurance renewal is committed or expected according to contract state, not actual.
- A predicted battery replacement is a forecast assumption.
- Warranty eligibility is coverage, not a recovery. Only an accepted/paid claim reduces net user cost.
- Downtime inconvenience is not money. Actual replacement rental or lost income can be recorded; an estimated time/opportunity cost must be labeled separately.

These states must never be summed into one “spent” number.

## Cost taxonomy

Every cost link keeps both a stable high-level family and an optional template-specific category.

| Family | Examples | Typical behavior |
|---|---|---|
| `ACQUISITION` | Price, delivery, title/registration, installation, inspection, initial required accessories | Capital basis unless policy says otherwise |
| `FINANCE` | Interest, origination, administration, early-repayment fee | Period economic cost; principal excluded |
| `FIXED_OWNERSHIP` | Insurance premium, road/property tax, registration, storage, connectivity/service contract | Time-based; allocate to coverage period where supported |
| `ENERGY_OR_FUEL` | Petrol/diesel, charging, electricity, gas, water | Usage-related; quantity and unit should be retained |
| `CONSUMABLE` | Fluids, filters, printer material, cleaning supplies | Usage/period cost |
| `MAINTENANCE` | Scheduled service, inspections, calibration, tyres, preventive work | Link service event and meter/date |
| `REPAIR` | Parts/labour after failure or damage | Unscheduled; link fault, downtime, claim/warranty |
| `CAPITAL_IMPROVEMENT` | Capacity/performance upgrade, major component replacement | Adds to basis under a versioned policy |
| `USAGE_FEE` | Tolls, parking, permits, per-use charge | Optional inclusion policy for cost-per-use questions |
| `DISPOSAL` | Selling commission, removal, recycling, termination | Added at disposal or forecast |
| `RECOVERY` | Rebate, insurance payout, warranty reimbursement, refund | Separate negative component only when confirmed |
| `ATTRIBUTABLE_INCOME` | Hire/rental or work income tied to the asset | Shown gross and optionally netted |

The user may want “car cost excluding parking” or “appliance cost excluding household electricity.” Therefore, each report carries an inclusion profile, and saved profiles are versioned. Categories cannot be retrospectively reclassified without either rebuilding results or retaining the category version used by each calculation run.

### Cash date versus economic recognition

Payment timing and cost consumption are often different. A twelve-month insurance premium paid today belongs entirely in today's cash-burden view but may be recognized across its coverage dates in an economic monthly view. The same applies to registration, prepaid service plans, and multi-period storage contracts.

Each asset cost link therefore needs a recognition policy such as:

- `ON_EFFECTIVE_DATE` for fuel, tolls, and one-off repairs;
- `OVER_SERVICE_PERIOD` for insurance or a prepaid contract;
- `BY_USAGE` when a consumable/capital component is meaningfully consumed by a meter;
- `CAPITALIZE` for an acquisition cost or improvement that enters adjusted basis.

The original payment remains untouched in the ledger. Recognition produces a rebuildable analytical allocation, not extra transactions. If the service period is unknown, use the effective date and expose a coverage warning rather than silently inventing twelve months.

### Shared and split costs

One ledger activity can serve several assets or partly serve an asset. Examples include a household insurance package, a garage used for two vehicles, a repair receipt with several items, or one electricity bill serving an EV and the home.

Use explicit allocation records:

- allocated amount or exact ratio;
- allocation method (`DIRECT`, `EQUAL`, `USAGE`, `VALUE`, `MANUAL`, `ESTIMATED`);
- source amount and currency;
- rounding remainder owner;
- effective period;
- confidence and explanation.

Allocations must reconcile to the eligible source amount. Unallocated value remains a household expense; it is not silently assigned to the asset. Changing an allocation produces a correction/new version and rebuilds affected TCO runs.

## Usage and unit model

### Meters

An asset can have several meters, but one is selected as the primary denominator for a report.

| Meter type | Common units | Examples |
|---|---|---|
| `DISTANCE` | km, mile | Vehicles and bicycles |
| `OPERATING_TIME` | hour, minute | Machinery, heating systems, tools |
| `CYCLE_COUNT` | cycle | Washing machine, battery, production equipment |
| `ENERGY_INPUT` | kWh, litre, m³, kg | EV, appliance, boiler |
| `OUTPUT` | kWh delivered, item, job, page | Solar, tool, printer, productive equipment |
| `ELAPSED_TIME` | day, month, year | Any asset, even with no physical meter |

`usage_meter` defines the dimension, canonical unit, rollover/replacement policy, and whether readings should be monotonic. `meter_reading` retains:

- original value and unit plus normalized value;
- effective timestamp/timezone;
- source (`MANUAL`, `PHOTO_REVIEWED`, `SERVICE_RECORD`, `IMPORT`, `DEVICE`);
- confidence and evidence/document link;
- reset, replacement, or rollover relation;
- recorded timestamp and correction relation.

Do not overwrite an old reading when a user corrects it. Do not reject an imported lower reading blindly: it may be a replaced odometer/meter. Require a reviewed reset/replacement event.

### Cost-per-unit semantics

For a period:

```text
cost per usage unit = eligible period economic TCO / validated usage delta
cost per month      = eligible period economic TCO / fractional ownership months
cash paid per unit  = eligible period cash burden / validated usage delta
```

The report must expose numerator, denominator, unit, period, inclusion profile, and coverage. If the denominator is zero, missing, or contradicted by readings, return no ratio plus a structured warning; never divide by an assumed annual mileage without labeling the result as forecast.

Useful breakdowns include:

- fixed versus variable cost;
- cost by family and rolling period;
- fuel/energy price and consumption separately from total energy cost;
- maintenance cost per unit and time since last/next service;
- depreciation per unit;
- actual, estimated, and unallocated proportions;
- personal versus work/shared usage where the user supplies an allocation.

Unit conversions must be exact and versioned. Store original observations; normalize at calculation boundaries. Currency conversion uses each cost's effective-date FX policy, while current value uses its valuation date. A current FX rate must never be applied retrospectively to all historic costs.

## Valuation and depreciation

### Valuation observations

`asset_valuation` should be immutable evidence, not a mutable `currentValue` field. Suggested kinds are:

- `FAIR_MARKET_ESTIMATE`;
- `DEALER_OR_TRADE_OFFER`;
- `QUICK_SALE_ESTIMATE`;
- `INSURED_REPLACEMENT_VALUE`;
- `PROFESSIONAL_APPRAISAL`;
- `ACTUAL_DISPOSAL_VALUE`;
- `BOOK_OR_TAX_VALUE`.

Each observation includes amount/currency, low/base/high range where available, effective date, provider/manual source, methodology, condition/usage facts, confidence, likely disposal cost, publication/revision metadata, and evidence. These values are not interchangeable: insured replacement value is not necessarily realizable cash.

For economic TCO, prefer an observed realizable-market value appropriate to the question. If only an estimate exists, show a TCO range driven by the valuation range. Sparse or stale valuations lower coverage rather than creating false precision.

### Depreciation policies

Support the following as explicitly named methods:

- `OBSERVED_VALUE`: difference between defensible opening and closing values; preferred for actual economic TCO.
- `STRAIGHT_LINE`: forecast allocation over useful life to residual value.
- `DECLINING_BALANCE`: forecast curve with an effective-dated rate.
- `USAGE_BASED`: forecast value consumption by kilometre/hour/cycle.
- `CUSTOM_CURVE`: category/provider/user curve with version and evidence.
- `MANUAL`: user-supplied value path.

A curve is a forecast or interpolation unless backed by an actual observation. It must never be relabeled as a market appraisal. Improvements, material damage, and component replacement may start a new curve version; they do not rewrite old results.

Economic, accounting/book, and tax depreciation are separate concepts. Tax deductions depend on jurisdiction, ownership/use, and rules beyond this generic engine; do not imply that economic depreciation is tax deductible.

## Maintenance, warranty, insurance, and documents

### Maintenance plan and service history

A maintenance item may become due by date, usage threshold, or whichever happens first:

- schedule source: manufacturer, service provider, user, or detected pattern;
- interval from prior service or in-service baseline;
- due date and/or due meter reading;
- tolerance window and severity;
- estimated cost and duration, clearly marked forecast;
- completion service event, actual parts/labour cost, provider, meter reading, and evidence;
- next occurrence generated only after the completion/review policy is satisfied.

Service events distinguish inspection, preventive maintenance, consumable replacement, repair, upgrade, recall work, and damage restoration. Downtime has start/end and reason. Actual substitute transport/equipment cost can link to the event; a hypothetical productivity loss stays an assumption.

### Warranty and protection

A warranty/guarantee record needs:

- provider and contract/document;
- covered asset or component;
- start/end date and usage limit;
- coverage categories, exclusions, deductible/excess, and monetary limit;
- transferability and registration requirement;
- status and next action;
- claim/recovery links.

Insurance remains a reusable protection contract from FT-27, linked to the physical asset rather than duplicated. Premium allocation, claim expense, payout, deductible, and repair cost are separate ledger facts. The asset screen can show coverage gaps and renewal/maintenance deadlines through the Money Action Queue.

The system should not promise that a warranty or insurance claim will be accepted. It records terms/evidence and deadlines, and labels extracted document data as unreviewed until confirmed.

## Suggested backend model

Do not create a second transaction system. Physical-asset records describe what a canonical ledger activity relates to.

### Core records

`physical_asset`

- opaque ID and owner/household scope;
- category/template and display name;
- make/model/year and category-specific profile reference;
- current economic-interest projection (`OWNED`, `CO_OWNED`, `LEASED`, `RENTED`, or `CUSTODIAL`) and owned share where relevant; financing remains a separate linked liability;
- optional unique `purchase_item_id` so FT-19 can promote a durable purchase into an asset without duplication;
- ownership/lifecycle dates and current lifecycle projection;
- reporting/native currency preference and timezone;
- primary usage meter ID;
- privacy classification and optimistic version;
- optional parent asset/component relation.

`asset_identifier`

- identifier kind such as VIN, registration, serial, IMEI, or custom;
- masked display value;
- encrypted/tokenized full value only when necessary;
- issuer/country, validity period, and verification state.

`asset_lifecycle_event`

- type, effective/recorded time, source/evidence;
- acquisition/disposal counterparty and purchase/sale links where applicable;
- correction/supersession reference;
- optional condition, location, and custody change.

`asset_cost_link`

- canonical `activity_id`, `activity_split_id`, `bill_id`, or reviewed receipt-line reference;
- asset ID, cost family/subcategory, allocated amount/currency/ratio and method;
- capitalization decision and policy version;
- recognition policy and effective service/consumption period;
- service, claim, contract, project, and effective-period references;
- provenance, confidence, correction/version metadata.

Only a posted/accrued canonical fact contributes to actual economic TCO according to the requested basis. A document extraction or predicted bill can propose a link but cannot silently commit one.

Additional records:

- `asset_interest` where ownership/control/share changes over time;
- `usage_meter` and immutable `meter_reading`;
- `resource_consumption_event` for fuel, charge, electricity, water, or another measurable input, retaining resource type, quantity/unit, unit price, total, full/partial-fill state where applicable, linked meter readings, and canonical activity/cost link;
- `asset_valuation` and provider/source revision;
- `depreciation_policy_version`;
- `maintenance_plan`, `maintenance_occurrence`, and `service_event`;
- `warranty_coverage` with contract/document links;
- `asset_disposal` with sale/trade/scrap proceeds and costs;
- `asset_relation` for components/replacements/shared systems;
- `asset_tco_profile` for category inclusion and allocation choices;
- common `calculation_run` and dependency manifest rather than a special untraceable cached total.

`depreciation_policy_version` should retain method, effective dates, in-service/basis reference, expected useful life or usage, residual-value amount/rate, curve parameters, source, and whether it is allowed for forecast, interpolation, book/tax information, or actual economic reporting. No policy edit rewrites an earlier calculation run.

### Category-specific extension

Keep stable common fields relational. Add typed extension tables for important templates, for example `vehicle_profile` with propulsion/fuel types and relevant technical fields. A schema-versioned metadata object may hold low-value optional attributes, but critical calculation inputs must not be buried in arbitrary EAV/JSON where constraints and migrations cannot protect them.

### Module boundary

Add a `physicalassets` module to the modular monolith. It owns asset identity, lifecycle, meters, asset-cost classification, service, and TCO orchestration. It consumes application interfaces/read models from:

- `ledger` for posted/accrued facts and account effects;
- `purchases` for receipt and purchase-item lifecycle;
- `contracts` and `protection` for recurring charges and coverage;
- `documents` for evidence;
- `valuation` and `marketdata` for observations/FX;
- `scenario` for future comparisons;
- `projects` for trips, work, renovation, or shared contexts.

It must not directly update balances or copy transaction amounts into mutable totals.

Backdated ledger facts, cost allocations, meter readings, valuations, lifecycle events, FX revisions, or policy changes invalidate affected projections from the earliest dependency date. Precomputed monthly/lifetime summaries are caches with calculation/dependency versions, never an independent truth.

## Ledger examples

### Financed vehicle acquisition

For a TRY 620,000 acquisition funded with TRY 220,000 cash and a TRY 400,000 loan:

- debit/increase physical-asset acquisition basis: TRY 620,000;
- credit/decrease selected cash account: TRY 220,000;
- credit/increase vehicle-loan liability: TRY 400,000.

The activity is one balanced economic event. Later loan payments split principal, interest, and fees. Principal decreases cash and liability; interest/fees are lifecycle costs. Recording the TRY 620,000 purchase again as “spending” would double count the acquisition.

### Service paid by credit card

- the service invoice/receipt links a TRY 8,000 maintenance cost to the asset;
- the posted card purchase increases the card liability and records the expense once;
- the later bank-to-card payment reduces cash and the card liability but is not another maintenance expense;
- if the manufacturer later reimburses TRY 5,000 under warranty, the recovery is linked and net maintenance cost becomes TRY 3,000 while gross repair cost remains visible.

### Energy shared with a home

An electricity activity is canonical household spending. A reviewed allocation may assign measured or estimated kWh/cost to an EV or heat pump. The original bill is not copied. Reports expose whether the allocation came from a sub-meter, charging session, tariff calculation, or manual percentage.

### Trade-in with outstanding finance

The old asset's negotiated trade-in value is disposal proceeds even if the dealer sends part of it directly to the lender. Paying off the old principal reduces a liability; it is not a disposal expense. If negative equity is rolled into the new loan, the new liability must distinguish financing for the new asset from refinancing the old shortfall. The cash difference on the dealer invoice is not enough to derive either asset's basis or the old asset's disposal value.

## Worked vehicle example

Assume after 18 months:

- acquisition basis: TRY 620,000;
- current realizable value: TRY 450,000;
- fuel/energy: TRY 60,000;
- maintenance/repair: TRY 20,000;
- insurance/tax/registration: TRY 30,000;
- finance interest/fees: TRY 25,000;
- confirmed warranty/insurance recovery: TRY 5,000;
- distance used: 25,000 km;
- original loan: TRY 400,000; principal repaid: TRY 120,000; principal outstanding: TRY 280,000.

Then:

```text
economic depreciation = 620,000 - 450,000 = TRY 170,000
economic TCO           = 170,000 + 60,000 + 20,000 + 30,000 + 25,000 - 5,000
                       = TRY 300,000
economic cost/km       = 300,000 / 25,000 = TRY 12.00/km
economic cost/month    = 300,000 / 18 = TRY 16,666.67/month
net-worth contribution = 450,000 asset - 280,000 liability = TRY 170,000
```

The TRY 120,000 principal repaid affects cash burden and reduces the liability, but it is not added to economic TCO. The acquisition basis is also not added on top of TRY 170,000 depreciation. If the TRY 450,000 value is only an estimate, the result must inherit and display its range/confidence.

## API shape

Use versioned mobile-friendly resources and exact decimal strings.

### Commands

- `POST /api/v1/physical-assets`
- `PATCH /api/v1/physical-assets/{assetId}` for metadata only, with optimistic version
- `POST /api/v1/physical-assets/{assetId}/lifecycle-events`
- `POST /api/v1/physical-assets/{assetId}/meter-readings`
- `POST /api/v1/physical-assets/{assetId}/resource-consumptions`
- `POST /api/v1/physical-assets/{assetId}/cost-links`
- `POST /api/v1/physical-assets/{assetId}/valuations`
- `POST /api/v1/physical-assets/{assetId}/maintenance-plans`
- `POST /api/v1/physical-assets/{assetId}/service-events`
- `POST /api/v1/physical-assets/{assetId}/warranties`
- correction/supersession commands for financial/measurement facts rather than destructive delete

Any command that also posts money uses the FND-01 preview/commit flow and a client-event idempotency key. Creating a maintenance plan, expected cost, or valuation estimate does not move cash.

### Queries

- `GET /api/v1/physical-assets?status=&category=&cursor=`
- `GET /api/v1/physical-assets/{assetId}`
- `GET /api/v1/physical-assets/{assetId}/timeline?cursor=&from=&to=`
- `GET /api/v1/physical-assets/{assetId}/tco?from=&to=&basis=ECONOMIC_ACTUAL&unit=KILOMETER&currency=TRY&profileId=`
- `GET /api/v1/physical-assets/{assetId}/maintenance-status?asOf=`
- `GET /api/v1/physical-assets/{assetId}/coverage-status?asOf=`
- `POST /api/v1/physical-assets/{assetId}/tco-previews` for uncommitted allocation or forecast changes
- `POST /api/v1/scenarios` using physical-asset strategies for keep/repair/replace/lease/rent comparisons

### TCO response contract

The response should include:

- asset, ownership interval, requested period, reporting currency, and basis;
- gross cost, recoveries/income, net economic cost, and separate cash burden;
- acquisition basis, opening/closing value, value method/range/date/confidence;
- breakdown by cost family and fixed/variable/capital classification;
- numerator and denominator behind every per-unit result;
- usage readings used, normalized unit, interpolation/reset warnings;
- actual/accrued/committed/forecast amounts as separate fields;
- linked liability balance for context, not included as a cost;
- native and reporting-currency values with FX observation references;
- data coverage: linked, unallocated, stale, manual, estimated, missing;
- calculation run ID, schema/calculation version, inclusion-profile version, policy versions, and source/dependency references.

A concise default screen can use this response, but an “explain this number” drill-down must reach every cost, reading, valuation, conversion, allocation, and assumption.

## Decision and forecast engine

### First scenarios

1. **Keep versus repair versus replace:** compare from today's realizable value, future costs, downtime, residual value, and financing—not historic sunk price.
2. **New versus used vehicle:** same horizon, actual expected usage, depreciation curves, energy, maintenance, insurance, transaction costs, and financing.
3. **EV versus combustion:** common distance/route assumptions, home/public charging mix, efficiency, energy/fuel escalation, maintenance, charger cost, tax, and residual-value ranges.
4. **Own versus lease/rent:** deposits, monthly charges, mileage limits, excess-use/damage risk, maintenance allocation, end value, and liquidity.
5. **Extended warranty:** premium/deductible versus a probability/range of covered repair costs; informational scenario, never a claim that coverage is suitable.
6. **Repair now versus defer:** expected failure/efficiency/downtime ranges with safety-critical work explicitly outside a purely financial recommendation.

Every scenario is immutable/versioned and distinguishes nominal from real amounts. It records horizon, discount rate, inflation/escalation series, tax treatment, usage path, future replacement events, residual-value method, financing terms, and low/base/high or simulation inputs. Results show cash-flow timing, NPV where relevant, break-even point, liquidity requirement, sensitivity, and data coverage.

The engine should not output a single deterministic forecast when residual value, failure, usage, or energy price materially drives the result. Start with transparent low/base/high sensitivity; add probability distributions only when inputs and communication are defensible.

### Connection to Decision Replay

Physical assets add two useful comparison families:

- **Acquisition replay:** what if the actual dated acquisition and running cash flows had instead gone to a deposit, debt repayment, gold, FX, index, or another asset?
- **Service replay:** compare actual use received and resale value, not only financial ending value. A car and an index do not provide the same service, so the result must show mobility/usage delivered and avoid calling the financially higher ending balance an automatic “better decision.”

This second point is important: asset utility is not captured by money alone. The engine can report cost per kilometre/cycle/month and let the user record a qualitative outcome, but it should not pretend to price all convenience, safety, enjoyment, or time saved.

## User experience

### Asset cockpit

The main screen should answer, in order:

1. What is the asset worth, what is owed against it, and how confident is that value?
2. What has it cost economically and in cash, per month and primary usage unit?
3. What drove the cost: depreciation, energy, maintenance, insurance/tax, finance, or other?
4. What service, warranty, insurance, registration, return, or claim action is next?
5. Is cost/efficiency changing, and is the change based on sufficient data?
6. What decision is approaching: repair, refinance, renew, sell, or replace?

The default card should never present “TRY 12/km” without the dates, total kilometres, inclusion profile, and valuation freshness nearby.

### Low-chore capture for a future mobile app

- select the asset after scanning/reviewing a receipt;
- capture a meter photo, retain it as evidence, and require review before posting the number;
- log fuel/charge quantity, price, total, meter, and full/partial fill state;
- complete a maintenance reminder and link the paid account/receipt;
- record a valuation or dealer offer in a few fields;
- work offline with a client-generated event ID and synchronize idempotently;
- suggest classifications/allocations, but keep AI/OCR output in preview until confirmed.

The system earns convenience over time from linked money facts. It should not require the user to re-enter a bank transaction, receipt, bill, and service cost four times.

## Why users could choose this over separate apps

The defensible difference is not a larger checklist. It is the connected evidence chain:

- real bank/card/brokerage cash flows reconcile with the asset instead of living in a separate vehicle log;
- loan principal, interest, cash burden, depreciation, current value, and net worth remain mathematically consistent;
- fuel/energy, receipts, service history, warranties, insurance, bills, claims, refunds, and documents share one lifecycle;
- cost works across kilometres, hours, cycles, energy/output, and time rather than only cars;
- actual history and future forecast cannot be confused;
- keep/repair/replace and alternative-use-of-money scenarios reuse the same versioned calculation engine;
- valuation ranges, missing usage, allocations, FX, and source quality are shown instead of buried;
- data remains portable and is not sold or converted into disguised product-placement advice.

The strongest retention loop is practical: capture a cost once, get an updated true-cost view, receive the next service/warranty action, and later make a better replacement decision from one's own history.

## Implementation slices

### Preconditions

Do not start this domain before:

- the current security/database/accounting containment work;
- FND-01 ledger and FT-31 account/funding semantics;
- FND-02 point-in-time observation/valuation and historical FX behavior;
- common document/evidence abstraction;
- correction, idempotency, calculation-run, and coverage contracts.

FT-19 purchases, FT-18 contracts, FT-22 recoveries/warranties, FT-23 documents/actions, FT-27 protection, and FT-29 debt make the experience richer, but the first asset slice can use manual links while those modules mature.

### Slice 1 — vehicle actual-cost MVP

- create/import one vehicle and acquisition basis;
- link purchase/funding/loan without double counting;
- manual odometer readings with km/mile normalization and correction;
- link fuel/charging, maintenance, repair, insurance, tax/registration, and finance-interest activities;
- manual current-value observation with date/range/source;
- actual economic TCO, cash burden, cost/month, and cost/km with breakdown/coverage;
- maintenance due by date or distance;
- warranty/insurance expiry and document links;
- asset timeline and export.

Exit gate: the worked acquisition, card service/refund, multi-currency, correction, sale, and meter-reset fixtures reconcile from ledger to TCO and net worth.

### Slice 2 — purchase and recovery workflow

- promote reviewed durable `purchase_item` records to physical assets;
- receipt-line allocation and split/shared costs;
- service/repair parts and labour detail;
- warranty/insurance claims and recovered amounts;
- disposal/trade-in workflow;
- household visibility and shared ownership/usage allocation.

### Slice 3 — decision support

- keep/repair/replace prospective scenario;
- new/used and own/lease/rent templates;
- versioned depreciation/residual-value curves and sensitivity;
- Decision Replay link for alternative use of actual cash flows;
- cost trend and replacement threshold with transparent rules.

### Slice 4 — reusable asset templates and integrations

- appliance/electronics, heating/cooling, bicycle, and tool/equipment templates;
- provider valuation, VIN/product/recall, utility/charging, and telematics integrations only after licensing, consent, quality, and deletion behavior are validated;
- category benchmarks only when cohorts are sufficiently comparable and privacy-safe.

Defer predictive maintenance, automatic diagnosis, live resale marketplaces, tax depreciation, autonomous purchase recommendations, and fleet/enterprise work-order management until the trusted consumer core proves demand.

## Test strategy and invariants

### Calculation fixtures

- cash purchase, financed purchase, refinance, and early payoff;
- principal/interest/fee split;
- opening/closing valuations with capital improvement;
- low/base/high valuation and stale observation;
- sale, trade-in, salvage, negative disposal cost, theft/insurance payout;
- fuel/charge, partial fill, tariff allocation, and multi-currency cost;
- annual insurance paid once but recognized over its coverage period;
- service paid by card and later card payment;
- refund, rebate, warranty recovery, deductible, and partial claim;
- cost shared across multiple assets;
- work/personal usage and attributable income;
- backdated cost/reading/valuation and correction;
- odometer replacement/reset/rollover;
- zero, missing, and inconsistent usage denominators;
- asset/component replacement and parent roll-up.
- trade-in with positive/negative equity and a new financed acquisition.

### Non-negotiable invariants

- Purchase basis plus depreciation is never double-counted in economic TCO.
- Loan principal is never an economic cost; interest and fees follow the declared inclusion policy.
- Card payment, transfer, and loan drawdown do not create duplicate expense/income.
- Asset allocations never exceed the eligible source amount and rounding remainders are deterministic.
- Gross cost minus confirmed recoveries/income equals displayed net cost.
- Actual, accrued, committed, and forecast amounts never share an unlabeled total.
- Replaying identical ledger, valuation, usage, FX, and policy inputs produces an identical result.
- Each displayed ratio exposes and reconciles its numerator and denominator.
- A missing/zero/invalid meter delta produces a coverage warning, not infinity or a fabricated default.
- Meter readings are monotonic unless connected by a reviewed reset/replacement event.
- Historic currency conversion uses point-in-time observations and preserves native totals.
- Net-worth reporting includes current asset value and linked liability once; TCO history is not added again.
- Disposal closes future actual usage/cost attribution unless explicitly reopened/corrected.
- A forecast curve never masquerades as an observed market valuation.
- Reversals/corrections rebuild all affected periods and saved runs remain reproducible from their dependency manifest.

### Security and privacy tests

VINs, registrations, serials, receipts, service locations, and usage patterns can identify a person or reveal routines. Test owner/household authorization on every record and aggregate; mask identifiers in lists/logs; encrypt sensitive identifier/document values; strip unnecessary image metadata; audit export/share access; and make provider/telematics consent revocable.

## Risks and controls

| Risk | Control |
|---|---|
| False precision from uncertain resale value | Date/source/range/confidence, sensitivity, stale warnings, manual-versus-provider label |
| Double-counting acquisition, loan, card, bill, or refund | Canonical ledger links and explicit economic/cash/net-worth views with golden fixtures |
| Too much manual entry | Reuse posted transactions/receipts/contracts, quick mobile capture, suggested-but-reviewed links |
| Generic model that fits no asset well | Shared core plus one validated category template at a time |
| Unsafe repair advice | Financial comparison is informational; safety-critical maintenance is never deferred by an automated financial recommendation |
| Tax/insurance/warranty overclaim | Separate economic versus tax concepts; record terms/evidence; no eligibility/coverage guarantee |
| Provider/licensing dependency | Manual baseline, source abstraction, ingestion revisions, export, and explicit coverage degradation |
| Sensitive location/identity data | Minimize collection, mask/encrypt, scoped consent, retention/deletion and audit controls |
| Forecast manipulation | Immutable assumption versions, actual-versus-forecast split, sensitivity, source panel |
| Scope explosion | Vehicle MVP, explicit exit gate, defer specialist verticals/integrations |

## Product acceptance criteria

The feature is ready to call a “Total Cost of Ownership Engine” only when:

- acquisition, financing, current value, cost, recovery, and linked liability reconcile without double counting;
- users can see separate cash burden, economic TCO, net-worth position, and forecast;
- per-kilometre/month results expose their exact period, numerator, denominator, inclusion profile, and coverage;
- maintenance and warranty status derives from effective dates/readings and links to actual service/claim records;
- costs reuse canonical account activities and receipt/bill facts rather than forming a shadow ledger;
- sale/trade/disposal produces a realized lifecycle result;
- historic FX, corrections, allocations, meter resets, stale valuations, and missing data have tested behavior;
- every result is reproducible through a calculation run and traceable to source facts and policies;
- the first vehicle workflow is useful with manual data before any licensed valuation, VIN, or telematics integration;
- export includes the asset register, timeline, readings, costs, valuations, coverage, and calculation assumptions.

## Bottom line

This is a strong expansion because it turns ordinary spending records into a durable decision asset. A vehicle-first MVP gives users a concrete answer—economic cost per kilometre and month—while the underlying model remains useful for any high-value object with acquisition, usage, upkeep, protection, value loss, and disposal.

Its credibility depends on the accounting boundary: purchase price, depreciation, loan principal, cash payments, current value, recoveries, and future estimates are related but are not the same thing. If the backend preserves those distinctions and traces every result to the common ledger, this can become one of the product's clearest reasons to choose it over a separate budget app, portfolio tracker, vehicle log, and spreadsheet.
