# Dashboard backend requirements

The current dashboard combines `DashboardView`, dashboard `TransactionInfo[]`, `BasicPortfolioView[]` and `PositionInfo[]`. The entries below describe the missing fields or server-side features. The JSON fragments are proposed contracts. Currency amounts and percentages use Java `BigDecimal` and OpenAPI `number`. Percentages use percentage points, so `1.47` means 1.47%.

| UI Alanı | Mevcut Endpoint | Eksik Alan / Özellik | Önerilen DTO Alanı & Tipi | Öncelik |
| --- | --- | --- | --- | --- |
| Dashboard subtitle and Linked portfolios | `GET /api/dashboards/{dashboardId}`, `GET /api/dashboards/{dashboardId}/transactions`, `GET /api/portfolios` | Complete dashboard membership including portfolios with no trades | `DashboardView.linkedPortfolios: List<LinkedPortfolioView>`, with `id: String` and `name: String` | P1 |
| Linked portfolio value, weight, asset count and return | `GET /api/dashboards/{dashboardId}`, `GET /api/positions`, `GET /api/dashboards/{dashboardId}/transactions` | Portfolio rollups in a common currency and quote coverage | `LinkedPortfolioView.assetCount: Integer`, `marketValue: BigDecimal?`, `costBasis: BigDecimal?`, `weightPercentage: BigDecimal?`, `returnPercentage: BigDecimal?`, `realizedPnl: BigDecimal?`, `currencyCode: String`, `missingQuoteCount: Integer` | P1 |
| Top positions and daily impact | `GET /api/positions`, `GET /api/dashboards/{dashboardId}` | Dashboard-scoped ranking, converted valuation, daily impact and quote completeness | `DashboardView.topPositions: List<DashboardPositionView>`, `marketValue: BigDecimal?`, `dailyImpact: BigDecimal?`, `dailyImpactPercentage: BigDecimal?`, `valueCurrencyCode: String`, `quoteUpdatedAt: Instant?` | P1 |
| Realized P&L / All time and Win rate | `GET /api/dashboards/{dashboardId}`, `GET /api/dashboards/{dashboardId}/transactions` | Authoritative all-time realized result and eligible sales counts | `RealizedGains.allTime: BigDecimal?`, `saleCount: Long`, `recordedSaleCount: Long`, `winningSaleCount: Long` | P1 |
| Realized performance / 3M, 6M, All | `GET /api/dashboards/{dashboardId}/transactions` | Date-filtered daily aggregates and explicit day boundaries | `DashboardPerformanceView` with `from: LocalDate?`, `through: LocalDate`, `zone: String`, `currencyCode: String`, `transactionCount: Long`, `cumulativePnl: BigDecimal`, `points: List<PerformancePoint>` | P2 |
| Market status and last quote | `GET /api/positions`, `GET /api/dashboards/{dashboardId}` | Market identifier and dashboard-scoped quote freshness | `DashboardView.marketQuotes: List<MarketQuoteStatus>`, with `exchangeCode: String`, `lastQuotedAt: Instant?`, `status: QuoteStatus` | P2 |
| Top positions subtitle | `GET /api/positions` | Instrument display short name | `PositionInfo.InstrumentView.shortName: String?` and `DashboardPositionView.shortName: String?` | P2 |

## 1. Dashboard membership

Extend `DashboardController.getDashboard` and `getDefaultDashboard` through `DashboardService.getDashboard` and `DashboardView`. The service already reads `QDashboardPortfolio` and excludes archived portfolios. Project IDs and names from that same user-scoped association. Include portfolios with no positions or transactions. The dashboard subtitle can use the returned array length.

```json
{
  "linkedPortfolios": [
    { "id": "123", "name": "Core" },
    { "id": "124", "name": "Long term" }
  ]
}
```

**Adapter removal** — Replace the transaction-derived `ids` and `linked` construction in `getDashboardDetails`. [Membership fallback](../../web/src/features/dashboard/dashboard-placeholders.ts#L117)

## 2. Per-portfolio values and weights

Extend the new `LinkedPortfolioView` in `DashboardView`. Add a grouped query to `StatisticsRepository` and hydrate the aggregates in `DashboardService.getDashboard`. Start from the membership query and left-join open positions and the matching-currency `instrument.instrument_snapshots` row. Use `COUNT(DISTINCT pos.instrument_id)` for assets. Convert each `last * quantity` and each `pos.total` to the dashboard currency using the same FX source and valuation timestamp as the dashboard balance. A genuinely empty portfolio has a value of zero. Mark aggregate material with missing quotes or FX as unavailable or partial and report coverage. Use the same priced set for the portfolio weights and their denominator. For the open-position return use `(marketValue − costBasis) / costBasis × 100` on a matching set, with null for zero basis or incomplete pricing. For the realized P&L alternative, sum converted sale P&L from that portfolio separately.

```json
{
  "linkedPortfolios": [{
    "id": "123",
    "name": "Core",
    "assetCount": 5,
    "marketValue": 320000.00,
    "costBasis": 303893.64,
    "currencyCode": "TRY",
    "weightPercentage": 64.0,
    "returnPercentage": 5.3,
    "realizedPnl": 8400.00,
    "missingQuoteCount": 0
  }]
}
```

**Adapter removal** — Replace `summarizePortfolio` and the same-currency `denominator` / `weighted` calculations in `getDashboardDetails`. The current value uses cost basis when a quote is missing, and the UI marks the estimate. Cross-currency weights currently cover only the dashboard-currency portion. [Portfolio aggregation fallback](../../web/src/features/dashboard/dashboard-placeholders.ts#L89) · [Weight fallback](../../web/src/features/dashboard/dashboard-placeholders.ts#L140)

## 3. Top positions and day change

Add `topPositions` to `DashboardView` via `DashboardController.getDashboard` and `DashboardService.getDashboard`. Reuse the definitions from `PositionController.fetchPositions`, `PositionService.fetchPositions`, `PositionMapper.xml` and `PositionInfo`, or project a dashboard-specific DTO over the same open-position data. Include the canonical position, portfolio and instrument IDs so the existing inspector can load the selected position. Rank by converted current market value across all dashboard portfolios. For mark-to-market impact, use `(last − previous_close) × current_quantity`, converted into the dashboard currency. The percentage is `(last − previous_close) / previous_close × 100`; use null for a missing or nonpositive prior close. The price and previous close come from the same instrument/currency snapshot. This quote impact isolates price movement. The existing dashboard `DailyChange` uses historical quantities and can differ when the user traded during the day.

```json
{
  "topPositions": [{
    "positionId": "901",
    "portfolioId": "123",
    "portfolioName": "Core",
    "instrumentId": "86",
    "symbol": "AKBNK",
    "name": "Akbank T.A.Ş.",
    "shortName": "Akbank",
    "currencyCode": "TRY",
    "valueCurrencyCode": "TRY",
    "marketValue": 58000.00,
    "dailyImpact": -800.00,
    "dailyImpactPercentage": -1.3605,
    "quoteUpdatedAt": "2026-11-20T15:00:00Z"
  }]
}
```

**Adapter removal** — In `describePosition`, replace the native valuation, cost-basis estimate, `dailyAmount` and `dailyPercentage`; then remove the fallback `rankingCurrency` and sort in `getDashboardDetails`. The current zero impact is labeled as an estimate when the feed supplies no daily price change. [Position value fallback](../../web/src/features/dashboard/dashboard-placeholders.ts#L62) · [Daily impact fallback](../../web/src/features/dashboard/dashboard-placeholders.ts#L67) · [Ranking fallback](../../web/src/features/dashboard/dashboard-placeholders.ts#L140)

## 4. All-time realized P&L and win rate

Extend `RealizedGains` under `DashboardView`. Use `StatisticsRepository` from `DashboardService.getDashboard` to aggregate all eligible SELL transactions in dashboard membership. Apply the same conversion and archival rules as `DashboardService.getDashboardTransactions`. Sum known converted P&L, count all sales and sales with converted P&L, and count converted P&L greater than zero as wins. Report `allTime: null` if sales exist but their realized P&L is unavailable. An account with no sales can return a zero total and zero counts. A zero P&L sale belongs in the recorded count. The win rate denominator is the recorded sale count. Keep `currentPeriod` and `previousPeriod` for the existing monthly view.

```json
{
  "realizedGains": {
    "currentPeriod": 0.00,
    "currencyCode": "TRY",
    "allTime": 100388.85,
    "saleCount": 20,
    "recordedSaleCount": 19,
    "winningSaleCount": 13
  }
}
```

**Adapter removal** — Bind the summary to the new fields and remove the fallback total, coverage and win-rate calculations in `getRealizedHistory`. Keep any client trade parsing needed until the chart endpoint is available. [Realized history fallback](../../web/src/features/dashboard/dashboard-placeholders.ts#L15)

## 5. Performance ranges and daily chart

Add `DashboardController.getDashboardPerformance` at `GET /api/dashboards/{dashboardId}/performance?range=3M|6M|ALL`. Implement aggregation in `DashboardService` with `StatisticsRepository` and a new `DashboardPerformanceView` DTO. Resolve the dashboard for the authenticated owner. Group eligible sales by execution date in an explicit reporting time zone. Use calendar month windows, sort ascending by date and label currency at the response level. For each day, return `dailyPnl: BigDecimal`, `cumulativePnl: BigDecimal` and `transactionCount: Long`. Cumulative totals start at the selected range boundary and the returned count describes that same range. The Daily bars switch is local presentation state.

```json
{
  "from": "2026-08-23",
  "through": "2026-11-23",
  "zone": "Europe/Istanbul",
  "currencyCode": "TRY",
  "transactionCount": 3,
  "cumulativePnl": 6300.00,
  "points": [
    { "date": "2026-11-09", "dailyPnl": 7400.00, "cumulativePnl": 7400.00, "transactionCount": 2 },
    { "date": "2026-11-20", "dailyPnl": -1100.00, "cumulativePnl": 6300.00, "transactionCount": 1 }
  ]
}
```

**Adapter removal** — Replace `getPerformanceSeries` with the range query keyed by dashboard ID and range. The current grouping uses the browser calendar and returns observed sales dates only. [Performance range fallback](../../web/src/features/dashboard/dashboard-placeholders.ts#L27)

## 6. Market freshness

Extend `DashboardView` through `DashboardService.getDashboard`. Use exchange metadata and `instrument.instrument_snapshots.updated_at`, populated by `MarketUpdater`, for instruments in the linked portfolios. Return one entry per identifiable exchange. A freshness status such as `FRESH`, `STALE` or `UNAVAILABLE` should reflect quote coverage and the market’s session schedule. Return null timestamps for unavailable quotes. Use quote timestamps for data freshness and separate session metadata for trading status.

```json
{
  "marketQuotes": [{ "exchangeCode": "BIST", "lastQuotedAt": "2026-11-20T15:00:00Z", "status": "FRESH" }]
}
```

**Adapter removal** — Replace `getMarketStatus` and the generic “Market” or “Quotes pending” labels with the response. [Market status fallback](../../web/src/features/dashboard/dashboard-placeholders.ts#L101)

## 7. Short instrument names

Add a nullable `shortName` to `PositionInfo.InstrumentView` and map it through `PositionMapper.xml`, `PositionService.fetchPositions` and `PositionController.fetchPositions`. Include the same value on `DashboardPositionView`. Source it from curated instrument metadata. Preserve the current full name for detail views and use the symbol as a final display fallback.

```json
{ "instrument": { "id": "86", "symbol": "AKBNK", "name": "Akbank T.A.Ş.", "shortName": "Akbank" } }
```

**Adapter removal** — Replace the `company` full-name fallback in `describePosition` with the short name from the API, allowing the full name when the field is null. [Company name fallback](../../web/src/features/dashboard/dashboard-placeholders.ts#L65)
