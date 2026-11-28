# Dashboard backend requirements

The dashboard combines the dashboard view, transactions, portfolios, and positions in its [frontend adapter](../../web/src/features/dashboard/dashboard-placeholders.ts). These gaps still require authoritative backend data.

| Area | Current limitation | Outstanding requirement |
| --- | --- | --- |
| Linked portfolios | Membership is inferred from transactions, so linked portfolios without trades can be omitted. | Return complete account-scoped dashboard membership, including empty portfolios. |
| Portfolio and position values | The adapter estimates values from cost basis when quotes are missing and computes weights and ranking from comparable currencies only. | Return portfolio rollups, weights, returns, and top positions in the dashboard currency. Include quote and conversion coverage with values and daily impact. |
| Realized performance | The adapter sums sales with recorded profit and groups trades using browser-local dates. The win rate covers sales with known profit. | Return authoritative realized totals, counts of eligible and covered sales, and range-aware performance with explicit day boundaries. |
| Market context | A generic market label and position timestamps stand in for exchange status and quote freshness. Positions fall back to the full instrument name or symbol. | Return exchange and quote status for relevant markets and a short instrument name where available. |
