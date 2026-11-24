# Backend Logging Standards

This document defines the logging conventions and observability foundation for `server/` (Spring Boot 3.5 on Java 21).

## 1. Formats by Environment

Logging relies on Spring Boot's native Logback support without custom Logback XML or extra JSON encoder dependencies. All application logs write exclusively to standard output (`stdout`); do not configure file appenders inside the container.

### Development (`application.yml`)

Human-readable console output configured via `logging.pattern.console`:

```text
23:05:08.102 INFO  [a83f2b91] HTTP - GET /api/portfolios 200 42ms event=http_request_completed method=GET route=/api/portfolios status=200 durationMs=42
23:05:12.452 INFO  [b72a1c82] TradeService - Trade created event=trade_created tradeType=BUY portfolioId=12 instrumentId=45 currencyCode=TRY
23:05:15.206 WARN  MarketQuoteFetcher - Quote provider failed; trying next provider event=market_provider_failed market=BIST provider=TradingView exception=ResourceAccessException durationMs=5012
23:05:17.315 ERROR [c91d2a13] ExceptionHandlerAdvice - Unhandled server exception event=unhandled_exception category=application exception=IllegalStateException
```

- Timestamp with milliseconds (`HH:mm:ss.SSS`), restrained ANSI colors (`spring.output.ansi.enabled=detect` default).
- Shortened class/logger name (`%logger{0}`).
- Optional `[requestId]` prefix rendered only when `requestId` is present in MDC.
- Structured SLF4J key-value pairs (`%kvp{NONE}`) rendered after the message.
- Full exception stack trace (`%wEx`) when a cause is logged.
- `spring.jpa.show-sql` is `false`. To inspect SQL locally, set `SQL_LOG_LEVEL=DEBUG` (targets `org.hibernate.SQL`).

### Production (`application-prod.yml`)

Spring Boot native Elastic Common Schema (`logging.structured.format.console=ecs`) JSON written one event per line to `stdout`:

- `@timestamp`, `log.level`, `log.logger`, `process.pid`, `process.thread.name`, `ecs.version`
- `service.name` (`APP_SERVICE_NAME`, defaults to `spring.application.name`)
- `service.environment` (`APP_ENVIRONMENT`, defaults to `prod`)
- `service.version` (`APP_VERSION`, populated with the Git commit SHA by CI/CD or Docker build/runtime env, defaults to `unknown`)
- `message`
- MDC fields (`requestId`, `userId`, and future `traceId`/`spanId`) serialized as top-level JSON properties
- SLF4J key-value fields (`event`, `method`, `route`, `status`, `durationMs`, etc.) serialized as top-level JSON properties
- `error.type`, `error.message`, `error.stack_trace` (bounded via `logging.structured.json.stacktrace.max-length` and `max-throwable-depth`)
- SQL and Hibernate statistics logging are disabled (`WARN`) in production.

## 2. Log Level Conventions

| Level | Usage |
| --- | --- |
| `TRACE` | High-volume low-level diagnostics (e.g., excluded infrastructure HTTP probes). |
| `DEBUG` | Development troubleshooting (e.g., expected 4xx `ApiException`/validation details, provider fetch success details). |
| `INFO` | Meaningful lifecycle, business, scheduled job completion, and 2xx/3xx HTTP completion events. |
| `WARN` | Recoverable anomalies, 4xx HTTP responses, authentication/authorization failures, business rule violations, provider fallbacks, partial job runs. |
| `ERROR` | Unexpected server failures, database failures, unhandled 5xx responses, failed scheduled jobs, upstream provider outages requiring investigation. |

Avoid:

- Method entry/exit logs or per-repository call logs.
- Logging inside loops per item (aggregate counts instead).
- Logging ordinary validation or 404 client errors at `ERROR` or with stack traces.
- Logging the same exception at multiple layers (either handle and log once, or rethrow).
- Enabling `spring.jpa.show-sql` or production SQL parameter logging.

## 3. Structured Event Naming & Standard Fields

Use SLF4J's fluent API (`log.atInfo()`, `log.atWarn()`, `log.atError()`) with `snake_case` `event` names and `camelCase` contextual keys.

### Standard Contextual Fields

| Field | Source | Description |
| --- | --- | --- |
| `requestId` | MDC (`RequestLoggingFilter`) | Request correlation ID (8-char hex or validated `X-Request-Id`). |
| `userId` | MDC (`UserMdcFilter`) | Internal numeric `User.id` from Spring Security principal (never email or headers). |
| `event` | KVP | Stable `snake_case` event identifier for filtering and metrics. |
| `method`, `route`, `status`, `durationMs` | KVP (`RequestLoggingFilter`) | HTTP completion metadata using Spring MVC normalized route templates and monotonic `System.nanoTime()`. |
| `job`, `provider`, `market` | KVP | Scheduled job name, external provider identifier, and market code. |
| `processedCount`, `succeededCount`, `failedCount`, `outcome` | KVP | Aggregated batch/job counters and outcome (`success`, `partial`, `skipped`, `failed`). |
| `portfolioId`, `dashboardId`, `positionId`, `instrumentId`, `tradeType`, `currencyCode` | KVP | Non-sensitive internal resource identifiers and enums. |

### Key Application Events

- HTTP: `http_request_completed`
- Auth & account: `authentication_succeeded`, `authentication_failed`, `authorization_failed`, `user_registered`, `onboarding_completed`
- Portfolio & trades: `portfolio_created`, `portfolio_archived`, `trade_created`, `trade_deleted`, `positions_cleared`, `trade_import_completed`, `trade_import_failed`
- Market data & jobs: `market_data_update_completed`, `market_data_update_failed`, `market_provider_fallback`, `market_provider_failed`, `scheduled_job_completed`, `scheduled_job_failed`
- Exceptions & callbacks: `validation_failed`, `business_rule_violation`, `unhandled_exception`, `transaction_callback_failed`

## 4. Request Correlation & MDC Lifecycle

- `RequestLoggingFilter` runs before `springSecurityFilterChain` (`Ordered.HIGHEST_PRECEDENCE + 10`) on `DispatcherType.REQUEST` only.
  - Accepts an incoming `X-Request-Id` header only if it matches `^[A-Za-z0-9_-]{8,64}$`; otherwise generates an 8-character lowercase hex ID.
  - Echoes the effective `X-Request-Id` response header (also exposed in CORS).
  - Emits exactly one `http_request_completed` log per relevant request using `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE` (falling back to a sanitized URI with numeric/UUID segments replaced by `{id}`).
  - Excludes CORS preflight, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`, and static/SPA resource requests from default completion logs.
- `UserMdcFilter` runs inside `SecurityFilterChain` right after `AnonymousAuthenticationFilter`.
  - Extracts `User.id` only from an authenticated `User` principal in `SecurityContextHolder`.
  - Populates `MDC["userId"]` for controller/service logs and records the trusted ID for `RequestLoggingFilter` after Spring Security clears `SecurityContextHolder`.
- Both filters restore only the keys they set (`requestId`, `userId`) in `finally` blocks, preventing leakage across pooled threads and preserving unrelated MDC keys (such as future `traceId`/`spanId`).
- `MdcTaskDecorator` propagates MDC context into `@Async` tasks and restores worker thread MDC in `finally`.

## 5. Sensitive-Data Restrictions

Never log:

- Passwords, password hashes, access tokens, refresh tokens, OAuth codes, API keys, or DB credentials
- `Authorization` headers, cookies, raw request/response bodies, or query strings
- Raw uploaded PDF/CSV file contents or filenames
- Financial values: balances, portfolio valuations, trade prices, quantities, commissions, or realized P&L amounts
- Personal information such as user emails or free-form user notes/names
- Uncontrolled external provider error response bodies or arbitrary user-supplied strings

Always prefer an explicit allowlist of internal IDs, counts, enums, status codes, exception class names, and durations.

## 6. Exception & Transaction Logging Conventions

- Handle and log exceptions once at the appropriate boundary (`ExceptionHandlerAdvice` for HTTP/MVC requests, job entry points for scheduled tasks, or when translating an upstream failure into a domain exception).
- Expected 4xx errors (`MethodArgumentNotValidException`, `NotFoundException`, `BadRequestException`) do not log stack traces; `IllegalArgumentException`/`IllegalStateException` log at `WARN` without stack traces.
- Unexpected 5xx errors (`RuntimeException`, `DataAccessException`, `NullPointerException`, `IndexOutOfBoundsException`) log at `ERROR` with stack traces.
- For `@Transactional` database mutations that merit operational domain events (high-value lifecycle, financial, or destructive changes — not routine UI CRUD already covered by `http_request_completed`), use `TransactionCallbacks.afterCommit(Runnable)` (`dev.canverse.stocks.service.common.TransactionCallbacks`, imported statically for readability).
  - Inside an actual transaction with active synchronization (`isActualTransactionActive() && isSynchronizationActive()`), the callback runs in `TransactionSynchronization.afterCompletion(int)` only when `status == STATUS_COMMITTED`, skipping rollbacks and unknown outcomes.
  - Outside an actual transaction (including active synchronization without a real transaction), the callback executes immediately.
  - Runtime failures inside logging callbacks are caught and logged at `WARN` (`transaction_callback_failed`) so a successfully committed business operation never appears to fail.
- Operational logs are for observability and troubleshooting, not durable financial audit storage.

## 7. Examples

```java
import static dev.canverse.stocks.service.common.TimingUtils.elapsedMs;
import static dev.canverse.stocks.service.common.TransactionCallbacks.afterCommit;

// Correct: structured, allowlisted fields, executed only after transaction commit
afterCommit(() -> log.atInfo()
        .addKeyValue("event", "trade_created")
        .addKeyValue("tradeType", "BUY")
        .addKeyValue("portfolioId", portfolioId)
        .addKeyValue("instrumentId", instrumentId)
        .addKeyValue("currencyCode", currencyCode)
        .log("Trade created"));

// Correct: scheduled job summary using monotonic timing
long startNanos = System.nanoTime();
// ...
log.atInfo()
        .addKeyValue("event", "scheduled_job_completed")
        .addKeyValue("job", "currency_rate_sync")
        .addKeyValue("provider", "forex_rate_api")
        .addKeyValue("processedCount", currencies.size())
        .addKeyValue("succeededCount", updatedCount)
        .addKeyValue("failedCount", missingCount)
        .addKeyValue("durationMs", elapsedMs(startNanos))
        .addKeyValue("outcome", "success")
        .log("Currency rate sync completed");

// Avoid: sensitive values, unstructured interpolation, or log-and-rethrow duplication
// log.info("User {} bought {} shares at {}", user.getEmail(), req.quantity(), req.price());
```

## 8. Docker & Dokploy Log Rotation

Because logs go to container `stdout` and run on a small VPS, configure bounded Docker log rotation on the host or service to prevent disk exhaustion.

Recommended `/etc/docker/daemon.json` defaults on the VPS:

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "5",
    "compress": "true"
  }
}
```

Or per-service Docker/Compose/Dokploy logging options (`max-size: 10m`, `max-file: 5`, `compress: true`), capping local container log storage at roughly 50 MB uncompressed per container.

## 9. Future Grafana Cloud & OpenTelemetry Readiness

- **Grafana Alloy & Loki**: Container `stdout` emits single-line ECS JSON that Alloy's Docker discovery scrapes without multi-line stitching. Keep low-cardinality fields (`service.name`, `service.environment`, `log.level`) as Loki stream labels and query high-cardinality fields (`requestId`, `userId`, `event`, `route`, `status`, `durationMs`) via LogQL `| json` or structured metadata.
- **Deploy Tracking**: Set `APP_VERSION` to the Git commit SHA in Dokploy/CI so `service.version` correlates log spikes with deployments.
- **Micrometer Tracing / OpenTelemetry**: MDC management only touches `requestId` and `userId` and preserves all other MDC keys across servlet requests and `@Async` tasks, so adding `micrometer-tracing-bridge-otel` later will seamlessly emit `traceId` and `spanId` into ECS logs without modifying existing logging code.
