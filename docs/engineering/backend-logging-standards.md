# Backend logging standards

Spring Boot writes human-readable logs to stdout locally and ECS JSON in the production profile. Use `LOG_LEVEL` for application diagnostics or `SQL_LOG_LEVEL=DEBUG` for Hibernate SQL locally. Keep SQL parameter logging off in production.

## Events and levels

- Use SLF4J's fluent API with a stable `snake_case` event name and `camelCase` fields. Record elapsed time as `durationMs`. Use allowlisted identifiers, counts, statuses, and enums.
- Use DEBUG for routine diagnostics, INFO for meaningful operations and successful request or job summaries, WARN for client errors and recoverable failures, and ERROR for unexpected server or job failures.
- Log exceptions at the handling boundary. Include a stack trace for unexpected failures when safe. Keep expected validation and client errors free of stack traces. Aggregate repeated failures and provider results.

## Correlation

- `RequestLoggingFilter` echoes `X-Request-Id` and logs one completion event for each relevant request with its route template, status, and duration. It can generate an ID when the incoming one is missing or invalid. Preflight, infrastructure, and static resource requests are excluded from normal completion logs.
- `UserMdcFilter` adds the authenticated account's internal `userId`. Request filters restore their MDC keys after the request. `MdcTaskDecorator` propagates and restores MDC in async work.
- For significant transactional mutations, emit success events with `TransactionCallbacks.afterCommit` so rolled-back changes are absent from success logs.

## Sensitive data

Never log secrets, tokens, auth headers, cookies, query strings, raw request or response bodies, upload names or contents, email addresses, free-form notes, financial amounts, or raw upstream error bodies. Use the provider name, internal IDs, counts, and exception class to diagnose failures.
