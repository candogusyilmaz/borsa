# Legacy HTTP route inventory

Recorded from commit `b2c42e751097c7a19805c0a53b28941fa4deebce` before the PR-001 backend replacement.
This file is evidence only; it is not a compatibility contract for the rewrite.

## Authentication / account (`/api/auth`, `/api/account`, `/api/onboarding`)

| Method | Path                         | Controller             |
| ------ | ---------------------------- | ---------------------- |
| POST   | `/api/auth/register`         | `TokenController`      |
| POST   | `/api/auth/token`            | `TokenController`      |
| POST   | `/api/auth/refresh-token`    | `TokenController`      |
| POST   | `/api/auth/google`           | `TokenController`      |
| GET    | `/api/account/me`            | `AccountController`    |
| POST   | `/api/account/clear-my-data` | `AccountController`    |
| GET    | `/api/onboarding/status`     | `OnboardingController` |
| POST   | `/api/onboarding/complete`   | `OnboardingController` |

## Reference data

| Method | Path               | Controller             |
| ------ | ------------------ | ---------------------- |
| GET    | `/api/currencies`  | `CurrencyController`   |
| GET    | `/api/instruments` | `InstrumentController` |

## Portfolios and dashboards

| Method | Path                                                               | Controller            |
| ------ | ------------------------------------------------------------------ | --------------------- |
| GET    | `/api/portfolios`                                                  | `PortfolioController` |
| POST   | `/api/portfolios`                                                  | `PortfolioController` |
| POST   | `/api/portfolios/{portfolioId}/archive`                            | `PortfolioController` |
| GET    | `/api/portfolios/{portfolioId}/analytics/monthly-revenue-overview` | `AnalyticsController` |
| GET    | `/api/dashboards`                                                  | `DashboardController` |
| GET    | `/api/dashboards/{dashboardId}`                                    | `DashboardController` |
| GET    | `/api/dashboards/default`                                          | `DashboardController` |
| POST   | `/api/dashboards`                                                  | `DashboardController` |
| GET    | `/api/dashboards/{dashboardId}/transactions`                       | `DashboardController` |
| DELETE | `/api/dashboards/{dashboardId}`                                    | `DashboardController` |

## Positions and trades

| Method | Path                                                    | Controller           |
| ------ | ------------------------------------------------------- | -------------------- |
| GET    | `/api/positions`                                        | `PositionController` |
| GET    | `/api/positions/{positionId}/active-trades`             | `PositionController` |
| GET    | `/api/portfolios/{portfolioId}/trades`                  | `TradeController`    |
| POST   | `/api/portfolios/{portfolioId}/trades/buy`              | `TradeController`    |
| POST   | `/api/portfolios/{portfolioId}/trades/sell`             | `TradeController`    |
| POST   | `/api/portfolios/{portfolioId}/trades/bulk`             | `TradeController`    |
| POST   | `/api/portfolios/{portfolioId}/trades/undo/{holdingId}` | `TradeController`    |
| POST   | `/api/portfolios/{portfolioId}/trades/import`           | `TradeController`    |
| GET    | `/api/trades`                                           | `TradeControllerV2`  |

## Notes

- Authentication was JWT-based with RSA key pair (`certs/private.pem`, `certs/public.pem`).
- Google OAuth login was supported via `POST /api/auth/google`.
- All routes required authentication except registration and token acquisition.
- The legacy v2 trades endpoint (`GET /api/trades`) appeared alongside the portfolio-scoped `TradeController`.
- No versioned `/api/v1/` prefix was used; the rewrite will use `/api/v1/` from the first endpoint.
