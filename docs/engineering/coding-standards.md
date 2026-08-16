# Backend coding standards

Status: authoritative implementation style for the scratch rewrite.

These standards define **how code is written**. Product scope and sequencing live in `docs/review/backend-master-plan.md`; shared financial meaning lives in `docs/review/accounting-contract.md`; the active PR specification defines **what to change now**.

## 1. Technology and dependency policy

- Java 25 is the required language/runtime.
- Spring Boot 4.1.0 is the pinned starting release.
- Use the Spring Boot parent/dependency management for Spring Framework, Spring Data, Hibernate ORM, Hibernate Validator, Jackson and other managed dependencies. With Spring Boot 4.1.0 this currently means Spring Framework 7.0.8, Spring Data JPA 4.1.0 and Hibernate ORM 7.4.1.Final.
- Do not set individual managed-library versions unless a PR documents a concrete compatibility/security defect and its tests.
- Jakarta Persistence 3.2 is the persistence API baseline.
- Use stable/final Java 25 and Jakarta Persistence 3.2 features where they make code simpler or safer.
- Do not enable Java preview/incubator/experimental features by default.
- Do not use Jakarta Persistence 4, Hibernate ORM 8, snapshots, milestones or release candidates in normal feature work.
- New dependencies require a concrete feature need in the current PR. Prefer JDK/Spring/Boot capabilities already on the classpath.
- Before implementing commodity infrastructure such as scheduling, batch execution, retries, rate limiting, protocol/authentication servers, tracing, caching, serialization, file parsing, or workflow orchestration, perform and record a build-versus-buy check. Evaluate, in order, the JDK, the pinned Spring Boot/Spring ecosystem, and focused actively maintained libraries against the concrete requirement. Custom implementation requires a documented semantic gap, operational constraint, or unacceptable dependency cost; an already-created table or partial implementation is not sufficient justification.
- Do not build a generic infrastructure subsystem without a production consumer in the same PR. Select infrastructure with the first concrete workload so required restart, transaction, throughput, deployment, observability, and data-retention semantics drive the choice.
- Build-versus-buy does not outsource product meaning. Accounting signs, opening-state coverage, correction, ownership, source quality, and other repository domain contracts remain application responsibilities even when technical plumbing comes from a library.

## 2. Java style

- Prefer small cohesive classes with one reason to change.
- Prefer records for immutable HTTP contracts, application-owned use-case models, read models and small value objects when identity/mutability are unnecessary.
- Jakarta Persistence 3.2 record embeddables are allowed when a true persisted value object benefits from them; do not force every pair of columns into an embeddable.
- Prefer sealed hierarchies and exhaustive pattern matching only when the domain is genuinely closed and the result is clearer than conventional polymorphism.
- Prefer `switch` expressions/patterns when they improve exhaustiveness and readability.
- `var` is allowed for obvious local types; do not use it when it hides domain meaning.
- Prefer immutable collections at boundaries (`List.copyOf`, `Set.copyOf`, etc.) when callers should not mutate them.
- Import referenced types instead of writing fully-qualified JDK names in method bodies. Use a fully-qualified name only to resolve a genuine, documented type-name collision.
- Prefer `Optional.orElseThrow(...)` when absence is an exceptional outcome. When absence is an expected result, use `map`/`flatMap`/`orElse` as appropriate; never use `Optional.get()` or pair `isEmpty()`/`isPresent()` with `get()`.
- Replace business-limit magic numbers with named constants close to the invariant owner. Reuse those constants in validation, service logic and error details where the same limit is exposed in more than one layer.
- Do not introduce a private record solely as a one-method transient tuple. Inline the values or construct the target object directly unless the record is a meaningful reusable result across operations.
- Use `Instant` for machine timestamps, `LocalDate` for date-only business facts, and explicit `ZoneId`/market calendar rules where local time matters. Inject `Clock`; do not call `Instant.now()`/`LocalDate.now()` deep in domain/application logic.
- Use `BigDecimal` for financial amounts, prices, quantities, rates and percentages according to `accounting-contract.md`. Never use `double`/`float` for financial calculations. Compare financial decimals with `compareTo` semantics where scale-insensitive equality is intended.
- Avoid `null` as a hidden state. Use validation, explicit optional fields, sealed/result types, or documented `Optional` at query boundaries as appropriate. Do not store `Optional` in entities.
- Comments explain **why**, invariants, external quirks or non-obvious trade-offs; do not narrate obvious code.

## 3. Spring style

- Use constructor injection. No field injection.
- Omit `@Autowired` on a single constructor.
- Put transaction boundaries on cohesive application/domain services, not controllers.
- Keep controllers thin: HTTP parsing/validation/auth context → application service → response mapping.
- Authenticated controllers receive the existing typed identity through `@AuthenticationPrincipal`; do not accept raw `Authentication`, extract repeated claims, or resolve the same identity again in each controller.
- Prefer composed HTTP method mappings with a direct path string such as `@PostMapping("register")`; omit `value`/`path` when only one path is mapped.
- Do not add `consumes` or `produces` attributes to ordinary JSON request mappings. Let Spring's message converters and content negotiation infer the media types; add explicit media-type constraints only when a mapping ambiguity or a concrete non-default media-type contract requires them.
- For a fixed HTTP status, prefer `@ResponseStatus` and return the response body directly. Use `ResponseEntity` only when the handler needs a dynamic status, response-specific headers, or other response-level control.
- Use `@ConfigurationProperties` for grouped application configuration rather than scattered `@Value` fields.
- Keep `spring.jpa.open-in-view=false`. Do not rely on lazy loading from controllers/serializers.
- Use framework abstractions only when they simplify a real requirement. Avoid generic base services/controllers/repositories that erase domain intent.

## 4. Package and class structure

Organize by coarse capability. Within each capability use these fixed sub-packages:

```text
dev.canverse.stocks
  identity/
    domain/          ← JPA entities, value objects
    application/     ← transactional services, use cases
    infrastructure/  ← Spring Data repositories, external adapters
    configuration/   ← Spring @Configuration classes
    web/
      request/        ← inbound HTTP/API request records
      response/       ← outbound HTTP/API response records
    web/              ← controllers
  reference/
    domain/
    application/
    infrastructure/
    configuration/
    web/request/
    web/response/
    web/
  ... (same structure for every capability)
```

Do not create these as global top-level layers at the root package level. Omit a sub-package entirely when a capability has no code in that layer yet. Within a crowded capability sub-package, a further feature-group split (e.g. `money/application/spending/`) is acceptable.

HTTP contract placement is directional: request records belong in the owning capability's `web/request` package and response records belong in its `web/response` package, even when used by only one controller. Keep meaningful application models and query results in `application/model`; do not move every Java method input/output into the HTTP contract packages. Do not add a parallel `dto`, `mapper` or generic model package.

Group parameters only when they form a meaningful use-case concept. For example, a reference search owns a record such as `reference/application/model/InstrumentSearchCriteria`; do not wrap every three- or four-argument method in a generic `Command`, `Query`, `Request` or `Parameters` type. Keep security identity and transaction context separate from business criteria.

Avoid `FooService` + `FooServiceImpl`, `FooUseCase`, `FooPort`, `FooAdapter`, mapper interfaces and command-handler classes when one cohesive class is enough. Introduce interfaces for:

- multiple real implementations;
- external provider/side-effect boundaries;
- a material test seam that cannot be achieved cleanly otherwise.

## 5. JPA/Hibernate entity standards

- Flyway SQL owns DDL. Entity annotations describe runtime mapping only.
- Entities are not HTTP contract records and must never be serialized directly to clients.
- Prefer field access and a protected no-arg constructor where Hibernate requires one.
- Do not expose blanket public setters. Mutating methods should express domain intent (`rename`, `archive`, `changePolicy`) and enforce relevant invariants.
- Use `@Getter` on JPA entities and value objects instead of writing public getters manually.
- Use `@NoArgsConstructor(access = AccessLevel.PROTECTED)` on JPA entities; Hibernate requires a no-arg constructor and `PROTECTED` prevents accidental direct instantiation.
- Use `@RequiredArgsConstructor` on Spring components (services, configuration) where constructor injection over `final` fields is appropriate.
- `@Setter` is permitted on non-entity classes where mutable state is explicitly intended.
- Do not use `@Data` on entities; it generates `equals`/`hashCode`/`toString` from all fields which causes correctness and performance problems with JPA proxies.
- Do not use `@Builder` on entities; it hides the entity lifecycle and makes partially-initialized states trivial to create accidentally.
- Do not use `@AllArgsConstructor` on entities; Hibernate does not need it and it implies a public construction path that bypasses invariants.
- Records replace Lombok for immutable API request/response types, commands, and small value objects.
- Explicitly mark associations `LAZY` unless eager loading is a deliberate measured requirement. Do not rely on JPA's eager to-one default.
- Prefer unidirectional relationships. Add bidirectional navigation only when both directions are genuinely needed and tested.
- Avoid large object graphs and cascading by default. Cascade only when lifecycle ownership is real.
- Use `orphanRemoval` only for true owned children.
- Entity-owned child collections are exposed as immutable views (`List.copyOf`, `Set.copyOf` or `Collections.unmodifiable*`). Callers must use domain methods for mutation; do not expose a mutable persistence collection through a getter.
- Use `@Version` for mutable metadata/aggregates where concurrent edits can race. Immutable posted financial facts should not be "edited" through optimistic locking.
- Avoid entity `equals/hashCode` based on mutable fields. Use stable identity semantics deliberately.
- Hibernate-specific stable annotations/features are allowed for concrete requirements, but never use them to recreate schema DDL that belongs in Flyway.

## 6. Queries and persistence

- JPA is the default for aggregate writes and straightforward reads.
- Use Spring `JdbcClient` with explicit SQL for complex/reporting read models where SQL is clearer or more predictable than an ORM query.
- For inclusive `LocalDate` ranges, use `from.datesUntil(to.plusDays(1))` after checking the maximum range; preserve missing-date and empty-range semantics without using stream side effects such as `peek`.
- For complex PostgreSQL reference reads, prefer explicit `array_agg`/`array_remove` aggregation for ordered one-to-many values and row-value tuple comparisons for compound keyset cursors when the SQL and indexes align. Do not introduce Spring Data scrolling APIs merely to replace a clear `JdbcClient` cursor contract.
- Do not maintain parallel JPA/MyBatis/QueryDSL implementations of the same query path. MyBatis and QueryDSL are not part of the initial rewrite.
- Avoid N+1 queries. Fetch exactly what a use case needs using explicit query shape, projections, entity graphs/fetch joins where justified, or `JdbcClient`.
- Repository methods should represent meaningful queries, not become generic persistence utility layers.
- Spring Data derived query names are appropriate when the complete name is immediately understandable. If a derived name contains nested-property underscores or becomes difficult to read, use an explicit `@Query` with a descriptive method name and parameter names inferred from the compiled method signature.
- Do not add `@Param` annotations to repository query methods. Use named query parameters that match the method parameter names; use positional parameters only when a named parameter would be less clear.
- A JPA bulk `@Modifying` query that replaces or removes owned children sets both `flushAutomatically = true` and `clearAutomatically = true` unless a documented transaction-specific reason requires different persistence-context handling.
- Use standard stream collectors such as `groupingBy`, `mapping` and `toMap` for bounded row grouping before creating a response record. Add a custom mutable accumulator only when the grouping cannot be expressed clearly with those collectors.
- Do not perform network/provider calls inside a database transaction unless an explicit workflow requires it and the failure semantics are designed.

## 7. Flyway/PostgreSQL standards

- Every schema change is a reviewed Flyway migration.
- Migrations own PK/FK/unique/check constraints, indexes, defaults, generated expressions, delete behavior, extensions, triggers and comments.
- Use explicit, deterministic constraint/index names where operational diagnosis benefits.
- Use PostgreSQL types deliberately: `uuid`, `numeric`, `timestamptz`, `date`, `jsonb`, etc.; do not hide PostgreSQL DDL inside JPA `columnDefinition`.
- Financial precision/scale follows `accounting-contract.md`; do not invent per-table rounding rules.
- Create indexes from observed/queryable access patterns and required uniqueness, not "index every foreign key/table column" cargo culting.
- A migration is incomplete until an empty PostgreSQL Testcontainers database migrates and Hibernate validation succeeds.

## 8. API standards

- New public endpoints live under `/api/v1`.
- API request/response models are records unless mutability is specifically required.
- Annotate response-record components with `@NotNull` when the API contract guarantees they are present, so future OpenAPI/Swagger generation can express required response fields accurately; omit it for conditionally absent fields.
- External IDs are opaque strings/UUIDs; do not expose database implementation details.
- Financial decimals are canonical decimal strings according to the accounting contract/OpenAPI schema; do not send JavaScript-sensitive floating numbers.
- Controllers are the only Bean Validation entry points. Use `@Valid @RequestBody` for request records and Jakarta constraints on request parameters/path variables where needed. Request records may retain constraint metadata and nested `@Valid` type-use annotations for cascading; a deterministic request-record `validate()` method for non-structural rules is called by the controller after binding.
- Do not put `@Validated` on application services, repositories, domain, infrastructure or configuration classes, and do not put service-method `@Valid` parameters there. Application services receive the already-validated HTTP contract and must not repeat request validation. Keep `Objects.requireNonNull` and runtime checks when they protect security, parsing, configuration, persistence or genuine domain integrity.
- Response records own transformations from their exact read-model shape through named factories such as `Response.from(view)`. Services orchestrate and select data; they do not accumulate private response-mapping boilerplate.
- Use RFC 9457-compatible problem details with stable application problem codes and safe messages.
- Never expose stack traces, SQL errors, raw provider bodies, secrets or internal exception messages to clients.
- Potentially unbounded collections use cursor pagination.
- Cursor payloads use one versioned, canonical application format (currently canonical JSON wrapped in unpadded Base64url for new cursor contracts), with decode-then-re-encode validation. Existing public cursor formats remain compatibility exceptions until an explicit versioned API migration; do not silently change an accepted cursor wire contract.
- Retryable financial commands define idempotency semantics from first release.
- Derived financial responses include relevant as-of/source/coverage/quality/calculation/projection metadata.

## 9. Errors and exceptions

### 9.1 Application error contract

- Expected application failures use the shared `AppException` / `ErrorCode` mechanism. Do not introduce parallel base exceptions, ad-hoc error records, or `ResponseStatusException` for expected domain/application outcomes.
- Cross-cutting codes belong in `CommonErrorCode`. Capability-specific enums belong in that capability's `error` package, for example `identity/error/IdentityErrorCode`.
- Error-code constants describe the condition without repeating their capability: use `IdentityErrorCode.INVALID_CREDENTIALS`, not `IDENTITY_INVALID_CREDENTIALS`.
- Each error code defines:
  - its stable enum/code name;
  - HTTP status metadata used by the boundary handler;
  - a safe developer-facing description;
  - the exact required interpolation-parameter keys;
  - its derived message key, such as `error.identity.invalid_credentials`.
- Client behavior branches on the stable `code`, never an English message. `key` and `params` are display/localization inputs, not alternative machine codes.
- `AppException` validates exact parameter-key equality at construction. Missing and extra keys are programmer errors and fail immediately with `IllegalStateException`.
- Exception parameter maps and nested collection/map values are immutable after construction.
- Preserve an underlying cause when it is useful for diagnosis, but never expose the cause or its message to clients.
- Prefer throwing `new AppException(SomeErrorCode.CONDITION, ...)` directly. Add a capability-specific exception subclass only when it provides concrete behavior beyond naming one error code.

### 9.2 HTTP error boundary

- Map exceptions to HTTP in the single global `GlobalExceptionHandler`; controllers and application services do not build `ProblemDetail` responses.
- Every globally owned error response is RFC 9457 `application/problem+json` and includes the standard `type`, `title`, `status`, and `instance` fields plus stable `code`, `key`, `traceId`, and injected-clock `timestamp` extensions.
- Use the project problem-type base `https://canverse.dev/problems/` followed by the lower-kebab error code.
- Include non-empty, safe params for 4xx application errors only. Omit `params` from every 5xx response, even when the server-side exception carries them.
- Never serialize exception messages, causes, stack traces, Java class names, SQL, constraint names, provider bodies, secrets, or developer descriptions as internal-error details.
- Application services never catch `DataIntegrityViolationException`, `ObjectOptimisticLockingFailureException` or `OptimisticLockException`. They retain only flushes required for ordering, read visibility, optimistic-lock semantics, generated state or aggregate consistency; the global boundary maps optimistic failures to the existing state-conflict contract.
- Constraint-name inspection and known-constraint translation are centralized in the static platform `DatabaseConstraintRegistry`, which owns the small explicit mapping table for the existing identity/reference error codes. It never exposes SQL, constraint names or database messages. Unknown persistence failures return the generic safe 500 response and are logged server-side.
- Do not catch `Exception` merely to log and rethrow. The final global catch-all owns unexpected 500 logging and safe response conversion.
- Expected validation, conflict, and not-found outcomes are not error-level log spam. Unexpected and internal 5xx failures are logged with their server-side exception for diagnosis.

### 9.3 Validation errors

- Bean Validation and MVC controller method validation use `CommonErrorCode.VALIDATION_FAILED` with HTTP 422 and one `params.errors[]` structure.
- Each validation entry contains `field`, validated application `key`, and safe `detail`; it may include safe constraint attributes such as `min`, `max`, or `value` under nested `params`.
- Built-in Jakarta constraints map to stable `error.fields.common.*` keys. Explicit custom templates use validated application keys such as `{error.fields.identity.password_too_short}`.
- Never send Jakarta/Hibernate bundle keys, malformed placeholders, arbitrary validation messages, rejected values, or class names as validation keys. Unknown constraints use the safe `error.fields.common.unmapped_constraint` fallback and are logged as implementation/configuration defects.

### 9.4 Request correlation and tracing

- Use one Spring Boot-managed Micrometer Tracing bridge (currently OpenTelemetry) with W3C propagation. The current span/trace context owns native `traceId`/`spanId` logging and context cleanup; do not manually recreate native MDC management or add exporters/network infrastructure without a concrete requirement.
- `RequestTraceFilter` remains a compatibility boundary: it generates a server-owned UUID through `IdGenerator`, stores it on the request, returns it in `X-Trace-Id`, and supplies the public `ProblemDetail.traceId`. The UUID is intentionally related to, but distinct from, the native W3C trace ID when the formats differ.
- Do not trust an inbound `X-Trace-Id` as authoritative and do not create controller-specific trace-ID mechanisms. Inbound `traceparent` is handled by Micrometer; native and compatibility contexts must be isolated and cleaned after every request.

## 10. Security and ownership

- Authorization belongs in backend queries/services even if the UI hides controls.
- Owner/household scope is part of every relevant detail and aggregate query; never fetch a globally addressed user-owned row and authorize "later" by accident.
- Never log tokens, passwords, signing keys, sensitive imported document content or unnecessary personal financial payloads.
- Secrets come from environment/secret storage; disposable development keys are generated and ignored by Git.
- Authentication/session changes require security integration tests.

## 11. Testing standards

Use the smallest test that proves the behavior:

1. pure unit/domain tests for calculations and invariants;
2. PostgreSQL Testcontainers for migrations, constraints, mappings, SQL, locking, transactions, idempotency and rebuild behavior;
3. HTTP/security integration tests for request contracts, ownership and problem details;
4. provider adapter contract tests with saved/sanitized fixtures and WireMock/equivalent only when actual HTTP behavior is under test;
5. optional real-provider smoke tests outside normal CI.

Additional rules:

- Financial calculations need small hand-worked golden fixtures that can be independently checked.
- The large synthetic household dataset is for integration/demo coverage, not the sole oracle for financial mathematics.
- Core CI tests do not require internet access.
- Test names describe the invariant or behavior, not the implementation method.
- Do not mock JPA repositories merely to claim persistence behavior is tested; use PostgreSQL when database semantics matter.

## 12. Formatting and static quality

- Keep formatting deterministic and automate it in the build once PR-001 chooses/configures the formatter. Do not hand-format around a configured formatter.
- Compiler warnings, static-analysis rules and formatter configuration belong in the build so agents and humans receive the same result.
- Generated sources are not manually edited.
- Do not suppress warnings broadly; suppress the narrow case with a reason.

## 13. PR discipline

- Implement only the active PR specification.
- Do not add future tables/endpoints/abstractions "for later".
- Keep migrations, domain behavior, tests and API changes for one capability together when reviewable; split when the diff becomes cognitively large.
- Any cross-cutting financial-semantic change updates `accounting-contract.md` before code.
- Any architecture/technology-baseline change updates `backend-master-plan.md` and this file before code.
- At PR completion, record deviations and follow-up work instead of silently broadening the original task.

## 14. Adopted cross-cutting implementation rules

- Do not put `@Transactional` on repository methods by default. Repository queries participate in the transaction owned by the application service; a repository annotation requires a documented repository-only use case whose lock/transaction semantics are correct.
- When a validated identifier is needed only as a foreign-key reference and no entity state is required, use `EntityManager.getReference(...)` instead of issuing a `findById` select. Use a normal lookup when existence, ownership or mutable state must be checked; a proxy is not a replacement for authorization or state validation.
- Do not manually initialize a primitive `@Version` field; Hibernate starts it at zero. Retain an explicit client-supplied version precondition when the API promises compare-and-swap semantics, because ORM optimistic locking does not compare a stale client version to the freshly loaded entity. Do not add `@Version` to identity/session entities without a migration and a designed conflict contract.
- Enforce entity-local mutation prerequisites such as source kind or lifecycle state in domain methods as well as owner authorization in application services. An entity cannot infer the caller's identity, but it can reject mutation of an incompatible persisted state.
- Persist application enums with `@Enumerated(EnumType.STRING)` when the database contract is textual and enum names are the stable values. Keep the Flyway column textual unless a separate migration explicitly changes that contract.
- Flush only when it changes a subsequent read/write/constraint outcome. Do not issue a second flush after an already-flushed aggregate merely because an unrelated security/audit event was recorded.
- A request-record `validate()` method contains only invariants annotations cannot express, such as normalization-sensitive limits, cross-field rules or duplicate-item rules. It must not repeat `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`, `@Pattern` or nested-object checks. The controller invokes it after the `@Valid` barrier; application services do not invoke it again.
- Local access-token validation composes Spring Security's issuer, audience and zero-skew timestamp validators with a small application validator for required headers, algorithm/key ID/token type, canonical UUID claims, exact lexical timestamp precision, `iat == nbf`, strict existing timestamp boundaries and maximum lifetime. Raw-token inspection remains when it is required to preserve lexical contracts such as fractional NumericDate rejection.
- Micrometer Tracing owns native W3C trace context and native `traceId`/`spanId` MDC values. The compatibility UUID is the only value placed in the compatibility MDC key by `RequestTraceFilter`, and it is removed in `finally`; tests must prove inbound propagation, header/Problem Detail correlation, request isolation and cleanup.
- The global handler omits exception parameters and internal details from 5xx responses, logs server-side diagnostics with both compatibility and native trace context, maps known registered constraints to existing capability errors, and maps unknown persistence failures safely. Never include secrets, tokens, passwords or raw provider/database payloads in those parameters.
- Application models belong to the owning use case, HTTP records remain in `web/request` and `web/response`, repository projections remain infrastructure-owned, and response records keep their factories. Avoid generic DTO/mapper/data/view packages and do not introduce MapStruct.
- Reusable HTTP cache policy belongs in a small platform helper such as `CacheHeaders.noStore()`. Do not duplicate `Cache-Control`/`Pragma` construction in each controller or add a global interceptor unless the policy is truly universal.
- Repeated lifecycle predicates belong on the domain entity that owns the state. Services and converters call a named domain predicate rather than copy the same revoked/expiry/owner-enabled expression.
- Bounded in-memory keyed state uses per-key atomic map transitions and a narrow lock only around capacity admission/pruning. Preserve fail-closed capacity behavior and version-guarded rollback without serializing every independent key.
- Event fields must come from their semantic source, not from another identifier merely because current data happens to make the values equal.
