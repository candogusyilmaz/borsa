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

## 2. Java style

- Prefer small cohesive classes with one reason to change.
- Prefer records for immutable API DTOs, commands, query results and small value objects when identity/mutability are unnecessary.
- Jakarta Persistence 3.2 record embeddables are allowed when a true persisted value object benefits from them; do not force every pair of columns into an embeddable.
- Prefer sealed hierarchies and exhaustive pattern matching only when the domain is genuinely closed and the result is clearer than conventional polymorphism.
- Prefer `switch` expressions/patterns when they improve exhaustiveness and readability.
- `var` is allowed for obvious local types; do not use it when it hides domain meaning.
- Prefer immutable collections at boundaries (`List.copyOf`, `Set.copyOf`, etc.) when callers should not mutate them.
- Use `Instant` for machine timestamps, `LocalDate` for date-only business facts, and explicit `ZoneId`/market calendar rules where local time matters. Inject `Clock`; do not call `Instant.now()`/`LocalDate.now()` deep in domain/application logic.
- Use `BigDecimal` for financial amounts, prices, quantities, rates and percentages according to `accounting-contract.md`. Never use `double`/`float` for financial calculations. Compare financial decimals with `compareTo` semantics where scale-insensitive equality is intended.
- Avoid `null` as a hidden state. Use validation, explicit optional fields, sealed/result types, or documented `Optional` at query boundaries as appropriate. Do not store `Optional` in entities.
- Comments explain **why**, invariants, external quirks or non-obvious trade-offs; do not narrate obvious code.

## 3. Spring style

- Use constructor injection. No field injection.
- Omit `@Autowired` on a single constructor.
- Put transaction boundaries on cohesive application/domain services, not controllers.
- Keep controllers thin: HTTP parsing/validation/auth context → application service → response mapping.
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
    web/             ← controllers, request/response records
  reference/
    domain/
    application/
    infrastructure/
    configuration/
    web/
  ... (same structure for every capability)
```

Do not create these as global top-level layers at the root package level. Omit a sub-package entirely when a capability has no code in that layer yet. Within a crowded capability sub-package, a further feature-group split (e.g. `money/application/spending/`) is acceptable.

Avoid `FooService` + `FooServiceImpl`, `FooUseCase`, `FooPort`, `FooAdapter`, mapper interfaces and command-handler classes when one cohesive class is enough. Introduce interfaces for:

- multiple real implementations;
- external provider/side-effect boundaries;
- a material test seam that cannot be achieved cleanly otherwise.

## 5. JPA/Hibernate entity standards

- Flyway SQL owns DDL. Entity annotations describe runtime mapping only.
- Entities are not API DTOs and must never be serialized directly to clients.
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
- Use `@Version` for mutable metadata/aggregates where concurrent edits can race. Immutable posted financial facts should not be "edited" through optimistic locking.
- Avoid entity `equals/hashCode` based on mutable fields. Use stable identity semantics deliberately.
- Hibernate-specific stable annotations/features are allowed for concrete requirements, but never use them to recreate schema DDL that belongs in Flyway.

## 6. Queries and persistence

- JPA is the default for aggregate writes and straightforward reads.
- Use Spring `JdbcClient` with explicit SQL for complex/reporting read models where SQL is clearer or more predictable than an ORM query.
- Do not maintain parallel JPA/MyBatis/QueryDSL implementations of the same query path. MyBatis and QueryDSL are not part of the initial rewrite.
- Avoid N+1 queries. Fetch exactly what a use case needs using explicit query shape, projections, entity graphs/fetch joins where justified, or `JdbcClient`.
- Repository methods should represent meaningful queries, not become generic persistence utility layers.
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
- External IDs are opaque strings/UUIDs; do not expose database implementation details.
- Financial decimals are canonical decimal strings according to the accounting contract/OpenAPI schema; do not send JavaScript-sensitive floating numbers.
- Use Bean Validation at the HTTP boundary and domain/application validation for invariants that cannot be expressed structurally.
- Use RFC 9457-compatible problem details with stable application problem codes and safe messages.
- Never expose stack traces, SQL errors, raw provider bodies, secrets or internal exception messages to clients.
- Potentially unbounded collections use cursor pagination.
- Retryable financial commands define idempotency semantics from first release.
- Derived financial responses include relevant as-of/source/coverage/quality/calculation/projection metadata.

## 9. Errors and exceptions

- Domain/application exceptions should carry stable semantic error codes, not HTTP concerns.
- Map exceptions to HTTP problem details in one boundary layer.
- Do not catch `Exception` just to log/rethrow or convert everything to HTTP 500.
- Preserve causes for observability while returning safe client details.
- Expected validation/conflict/not-found conditions are not error-level log spam.

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
