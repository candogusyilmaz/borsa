# PR-021 — Durable platform job execution

Status: **DRAFT — NOT ACTIVE UNTIL PR-020 IS ACCEPTED AND `CURRENT.md` IS ADVANCED**

## Goal

Turn the existing `platform.job` storage into a complete in-process durable execution subsystem: callers can submit an idempotent job, concurrent application instances claim only supported due work without duplicate execution, active claims are heartbeat-fenced, transient failures retry with bounded backoff, abandoned work is recovered, and terminal outcomes remain durable and observable. This PR provides the R1 job lifecycle itself; it does not invent a placeholder business job or expose an HTTP administration API.

## Sizing and boundary rationale

- Comparison baseline: accepted PR-018 (`d1eea9a`) production surface of 381 Java additions across 12 production files.
- Expected production surface: approximately 25–35 production Java/migration/configuration files and 1,900–2,400 gross production lines across migration hardening, typed job contracts, PostgreSQL lifecycle SQL, transactional services, handler registration, the bounded worker runtime, lease heartbeats/recovery, configuration, safe diagnostics, and metrics. Tests and documentation do not count. The implementation must report the actual production surface and must not pad the diff merely to meet the estimate.
- Combined steps: idempotent submission, claim/fencing, handler dispatch, heartbeat, retry/failure, stale recovery, graceful lifecycle, and low-cardinality observability are one operational state machine. Splitting any of them would leave jobs that can be stored but not safely executed, or executed without crash/concurrency semantics.
- Review boundary: this is one infrastructure capability with one table and one lifecycle. Concrete import/rebuild/ingestion handlers, user-visible job APIs, ledger behavior, reference administration, and external providers are independent capabilities and remain excluded.
- If the simplest correct implementation lands below the nominal line target, the completion record must explain the equivalent review surface in terms of PostgreSQL locking, fencing, crash recovery, and multi-instance concurrency. No speculative layer, endpoint, or fake handler may be added for size.

## Source documents

- `docs/review/backend-master-plan.md` — “Synchronous and asynchronous work” and R1 durable-job steps/exit gate.
- `docs/engineering/coding-standards.md` — transaction ownership, SQL/JPA boundaries, injected time/IDs, stable errors, logging, configuration, and PostgreSQL test requirements.
- `docs/implementation/STATE.md` — existing V1 `platform.job` mapping and the next-area handoff after PR-020.
- `docs/review/accounting-contract.md` — idempotency/concurrency principles only; this PR does not post or calculate financial facts.

## Starting state

- PR-020 has been reviewed, accepted, and committed by the user; its commit becomes the starting commit recorded here.
- Flyway has migrated the disposable database through V2.
- `platform.job` already exists from V1 with UUID identity, optional owner FK, JSON object payload, due time, lifecycle status, claim metadata, heartbeat, attempt limits, terminal timestamp, error text, timestamps, and partial claim/recovery indexes.
- `Job` and `JobRepository` provide minimal schema-validation mapping only; no production submission, claim, execution, heartbeat, retry, recovery, scheduler, or job-handler contract exists.
- One Spring Boot process and one PostgreSQL database remain the deployment model. Spring Batch, a broker, an outbox, and a second service are neither present nor required.

## Scope

1. Add a forward-only Flyway V2.1 migration that hardens the existing job state constraints and claim/recovery indexes required by the implemented lifecycle. Do not edit accepted V1 or V2 migrations.
2. Replace stringly application status handling with stable `JobStatus` values and immutable job submission, claim, execution-context, and snapshot records. Keep the database status column textual.
3. Implement idempotent job submission using the caller-supplied job UUID as the logical idempotency key. An exact replay returns the same job; reuse of that UUID with different semantic input returns a stable conflict.
4. Implement one bounded PostgreSQL claim operation using `FOR UPDATE SKIP LOCKED`, due-time ordering `(available_at, created_at, id)`, registered job-type filtering, attempt increment, a fresh claim token, and one injected observation time.
5. Implement claim-token-fenced heartbeat, success, retryable failure, terminal failure, and stale-claim recovery transitions. Every transition is a short independent transaction and checks the current status plus claim token where applicable.
6. Add a minimal handler contract and registry. Registered types are unique and bounded; workers never claim a type they cannot execute. Handlers receive immutable job identity/owner/type/payload/attempt/idempotency context, never a managed `Job` entity.
7. Add a bounded in-process worker that polls only up to available execution capacity, commits claims before dispatch, invokes handlers outside the claim transaction, heartbeats active claims, classifies safe failures, schedules bounded deterministic backoff, and stops claiming during graceful shutdown.
8. Recover stale `RUNNING` jobs in bounded batches using PostgreSQL locking. Recoverable attempts return to `READY`; exhausted attempts become `FAILED`; both fence the abandoned token so a late worker cannot heartbeat or publish a terminal transition.
9. Add safe operational diagnostics: structured logs without payloads/secrets and Micrometer counters/gauges with bounded tags for claim, success, retry, terminal failure, stale recovery, lease loss, and active executions.
10. Add pure and PostgreSQL/Testcontainers coverage proving the complete state machine, exact transaction boundaries, multi-worker non-duplication, fencing, retry/recovery, idempotency, and safe diagnostics. Core behavior must remain network-free.

## Explicit non-goals

- No statement/document import, projection rebuild, observation ingestion, notification delivery, scenario calculation, or other concrete business job.
- No HTTP, WebSocket, Actuator write endpoint, admin UI, job-list screen, or change to the bearer security route map.
- No Spring Batch, message broker, outbox, distributed cache, separate process/service, leader election, or generic workflow engine.
- No arbitrary Java class names, SpEL, scripts, serialized Java objects, URLs, credentials, or executable instructions in job payloads.
- No promise of exactly-once handler side effects. Claim fencing prevents duplicate terminal ownership, while each future handler remains responsible for idempotent domain writes because a crash can occur after its write and before job completion.
- No running-job cancellation contract. The existing `CANCELLED` storage value remains reserved for a later user/operations capability; this PR must not pretend interruption can undo committed handler effects.
- No terminal-row retention/purge policy, job dependency graph, priorities, cron persistence, recurring-job model, progress percentage, result blob, or payload schema registry.
- No ledger/reference/provider/frontend changes and no unrelated identity/session refactor.

## Database changes

Migration:

- `V2_1__platform_job_lifecycle.sql` (Flyway version 2.1, preserving V3 for the R3 ledger migration).

The migration changes only `platform.job`:

- replace `ix_job_claim` with a partial ready-job index that supports deterministic `(available_at, created_at, id)` polling and registered-type filtering without changing semantic order;
- replace `ix_job_recovery` with a partial running-job index ordered by `(heartbeat_at, id)` for bounded stale recovery;
- add named checks that enforce:
  - `job_type` is trimmed and bounded;
  - `claimed_by` and `last_error`, when present, are trimmed and bounded;
  - payload remains a JSON object;
  - `RUNNING` has a non-null heartbeat and a positive attempt count;
  - `READY` has remaining attempt capacity, no completion timestamp, and no claim metadata;
  - `max_attempts` does not exceed the shared application/database maximum;
  - non-running rows have no live claim token;
  - non-terminal rows have no completion timestamp;
  - `SUCCEEDED` has no error and `FAILED` has one safe failure code;
  - `SUCCEEDED`/`FAILED` represent at least one attempted execution.

The migration must be safe for every row shape permitted and exercised by the accepted V1/V2 repository state. If a constraint requires normalization of pre-lifecycle rows, perform a deterministic data correction in the same migration and test it. Do not use functions, triggers, PostgreSQL enums, or extensions.

The existing UUID primary key is the submission idempotency key; do not add a redundant idempotency column or table. The existing owner FK and `ON DELETE CASCADE` behavior remain unchanged.

## Application changes

### Stable contracts

- Map `Job.status` with `@Enumerated(EnumType.STRING)` to a stable `JobStatus` enum: `READY`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`.
- Define one shared initial limit contract: job type 100 characters, worker identity 200 characters, canonical payload 65,536 UTF-8 bytes, failure code 128 characters, at most 100 attempts, recovery batch at most 1,000, and local concurrency at most 64. Reuse named constants wherever an invariant crosses boundaries.
- Use an immutable `JobSubmission` with caller-supplied `jobId`, optional owner ID, registered job type, JSON-object payload, `availableAt`, and `maxAttempts`.
- Use canonical JSON/object comparison for idempotent replay; whitespace or object-key order alone cannot cause a conflict. Arrays, scalars, and null are invalid payload roots.
- Expose an immutable `JobExecutionContext` containing job ID, optional owner ID, job type, payload, current attempt, max attempts, and a lease-check/cancellation signal. It must not expose claim tokens to handlers unless a concrete fencing operation requires it internally.
- Expose a read-only `JobSnapshot` for internal callers/tests. Do not expose payload through logs or metrics.

### Submission

- `DurableJobSubmissionService` owns the transaction and delegates SQL to a purpose-specific repository.
- Submission accepts only a type registered in the current application context and validates all limits before SQL.
- Use `INSERT ... ON CONFLICT (id) DO NOTHING`, followed by a same-transaction read when necessary.
- An exact semantic replay returns the existing job snapshot without resetting status, attempts, timing, errors, or claims.
- A conflicting replay throws `JOB_SUBMISSION_CONFLICT`; unknown types and invalid payloads use stable job-specific error codes. Unknown persistence failures remain generic server failures.
- The optional owner is enforced by the existing FK. There is no client-controlled owner boundary in this PR; future HTTP/application callers must derive it from authenticated identity.

### Claim and lifecycle repository

- Keep lifecycle SQL in one purpose-specific PostgreSQL repository rather than scattering native queries through services or forcing lock-sensitive behavior into generic JPA CRUD.
- A claim is one atomic statement/short transaction using a bounded candidate CTE with `FOR UPDATE SKIP LOCKED`, registered-type filtering, `available_at <= observedAt`, exact ordering `(available_at, created_at, id)`, and `LIMIT 1` (or an equivalently bounded batch with the same proof).
- Claiming changes `READY` to `RUNNING`, increments `attempt_count` exactly once, writes worker ID, fresh claim token, claimed/heartbeat/updated timestamps from one injected clock observation, clears completion, and returns an immutable claimed record.
- Heartbeat updates only `RUNNING` rows matching `(jobId, claimToken)`. A zero-row result means lease ownership is lost; it is not retried as if the caller still owned the job.
- Success and failure transitions use the same fence. A late worker after recovery cannot overwrite the recovered/current state.
- Retry transitions clear all live claim metadata, retain only the safe bounded failure code, set `READY`, and schedule `available_at` using deterministic capped exponential backoff derived from the just-failed attempt.
- Terminal success/failure clears the live claim token, records one completion/update time, and may retain bounded non-token claim audit fields only if the migration constraints and read model define that choice consistently.
- No transition stores an exception message, stack trace, payload, credential, URL, SQL detail, or user-supplied text in `last_error`.

### Handler registry and execution

- Define one small `JobHandler` contract with a stable `jobType()` and `handle(JobExecutionContext)` operation.
- Build an immutable registry from Spring beans and fail startup on duplicate, blank, untrimmed, or over-limit type declarations.
- It is valid for production to have zero handlers after this PR. In that state the worker performs no claim query and the application still starts.
- Workers pass the registry's supported type set into every claim. A mixed-version instance must not claim a job it cannot handle.
- Handlers run outside the claim/transition transaction. A handler owns its own short domain transactions and must use the job ID as an idempotency/fencing input where a crash-retry could repeat a write.
- A declared safe retryable job failure records its stable code and retries while capacity remains. A declared permanent failure terminates immediately. Unexpected exceptions are logged server-side, store only a fixed generic code, and retry until the configured/max-attempt boundary.
- Interrupted or lease-lost execution is best-effort stopped and may not publish success/failure after fencing fails. The system does not claim that Java interruption rolls back side effects.

### Worker, heartbeat, recovery, and shutdown

- Add validated `stocks.jobs.*` configuration for enabled state, worker identity, concurrency, poll interval, heartbeat interval, lease timeout, recovery interval/batch size, and initial/maximum retry backoff.
- All durations/counts are positive and bounded. Lease timeout must leave a documented safety margin above heartbeat interval.
- The worker uses a bounded executor and never claims more jobs than its local free capacity. Poll callbacks do not overlap unboundedly and do not hold a database transaction while waiting for capacity or running a handler.
- Each active execution receives periodic heartbeat attempts from the scheduler. A fenced heartbeat marks the local execution lease-lost and prevents a terminal transition.
- Recovery claims stale rows in bounded deterministic order with `FOR UPDATE SKIP LOCKED`. It rechecks staleness under lock, preventing a concurrent fresh heartbeat from being recovered.
- Stale work with attempts remaining becomes `READY` using the same backoff policy and a fixed safe lease-expired code. Exhausted stale work becomes `FAILED` with a completion timestamp.
- On shutdown, stop polling/recovery first, cancel future heartbeat scheduling, and request bounded executor shutdown. Do not rewrite active rows to success/failure merely because the process is stopping; abandoned leases are recovered by the durable protocol.
- Scheduled entry points contain no business logic; they delegate to testable application/runtime components.

### Observability and data safety

- Emit concise structured logs keyed by job ID, registered type, attempt, and worker ID. Never log payload JSON, owner email, credentials, arbitrary exception messages, or claim tokens.
- Micrometer tags are restricted to registered job type and a fixed outcome/reason vocabulary. Job ID, owner ID, worker ID, exception class/message, and failure code are not metric tags.
- Track at least claimed, succeeded, retried, permanently failed, stale-recovered, lease-lost, and current local active-execution counts.
- Metrics/logging failure must not change durable lifecycle state.

## API contract

None. This PR adds an internal Java submission/handler contract and background runtime only. It must not add or publicize an `/api/v1` route, change authorization rules, or create a servlet session.

Stable internal application error codes:

- `JOB_SUBMISSION_CONFLICT`
- `JOB_TYPE_UNREGISTERED`
- `JOB_PAYLOAD_INVALID`
- `JOB_CONFIGURATION_INVALID` only if startup/property validation cannot use the existing configuration-validation boundary cleanly

Do not expose worker exception details through `AppException` or `ProblemDetail`.

## Business and lifecycle invariants

- A job UUID identifies one immutable submission intent. Exact replay is idempotent; conflicting reuse never mutates the existing row.
- A job is claimable only while `READY`, due, below `max_attempts`, and supported by that worker instance.
- One successful claim increments attempts once and creates one unpredictable claim token. Claim tokens are internal fencing values, not authentication credentials or API data.
- `SKIP LOCKED` permits independent workers to make progress without waiting on a job another worker is claiming.
- Only the current `(jobId, claimToken, RUNNING)` owner may heartbeat or publish an outcome.
- A successful terminal transition is durable once; retry/late completion cannot reopen or overwrite it.
- Retry and recovery never reduce/reset attempt count. A row cannot return to `READY` after attempts are exhausted.
- Wall-clock equality is explicit: `available_at <= now` is due; `heartbeat_at <= staleCutoff` is stale. Tests cover equality boundaries.
- Handler/database work is at-least-once across a crash window. Future domain handlers must be idempotent; this subsystem does not hide that contract.
- Owner deletion retains the accepted `ON DELETE CASCADE` semantics. The worker must tolerate a claimed owner-scoped row disappearing and treat the missing fenced transition as lease loss, not recreate it.
- Time comes from injected `Clock`; job/claim IDs come from the repository ID generator. No deep `Instant.now()` or `UUID.randomUUID()` calls.

## Required tests

### Pure/domain

- `JobStatus` exact string/Jackson behavior and unknown-value rejection where serialized.
- Submission validation: blank/padded/over-limit type, invalid JSON root, payload byte boundary, unavailable/past/future time semantics, and attempt/concurrency/configuration limits.
- Canonical payload semantic equality across key order/whitespace and inequality for changed values.
- Exact replay versus conflicting replay comparison without mutable lifecycle fields participating.
- Capped exponential backoff for every attempt boundary, cap equality, overflow-safe duration math, and injected-clock behavior.
- Handler registry empty/single/multiple/duplicate/invalid type behavior and immutable supported-type exposure.
- Failure classification stores only fixed safe codes; arbitrary exception text and payload content never become durable/log/metric values.
- Worker capacity accounting, lease-loss signal, scheduler overlap prevention, and shutdown ordering with deterministic fakes (not sleeps).

### PostgreSQL/Testcontainers

- Fresh migration through V2.1 and upgrade from target V2 with an allowed pre-lifecycle job row; Hibernate schema validation remains green.
- Exact new check constraints and partial index definitions, including ordered index columns/predicates.
- READY/RUNNING/SUCCEEDED/FAILED row-shape matrix, bounded strings/attempts, completion timestamps, and live-token rules; exercise the payload-size boundary through the real submission service while the database independently enforces an object root.
- New submission, exact replay, conflicting replay, and a coordinated concurrent identical-submission race yield one row and one job ID.
- Owner FK and delete cascade remain intact; system jobs with null owner remain allowed.
- Due-time equality, future exclusion, deterministic ordering, inactive/unsupported type exclusion, and attempt increment on claim.
- Two or more coordinated workers claim a populated queue with each job claimed at most once, no blocking convoy, and progress across different supported types.
- Heartbeat/success/retry/failure updates accept only the current token; wrong, old, recovered, missing, and terminal tokens update zero rows.
- Retry backoff and maximum-attempt terminal behavior persist exact timestamps/codes and preserve monotonic attempts.
- Heartbeat-versus-recovery races in both lock acquisition orders: a fresh heartbeat survives; a truly stale claim is recovered once; a late old worker cannot finish it.
- Concurrent recovery workers use `SKIP LOCKED` and recover each stale row at most once in bounded batches.
- Handler success, retry-then-success, permanent failure, unexpected failure, and crash/stale-recovery are exercised through the real worker with test-only handler beans and committed state checks.
- Verify the handler does not execute inside the claim transaction and that terminal transition failure cannot falsely report durable success.
- Query-count/boundedness assertions prove no unbounded scan or N+1 handler lookup was introduced.

### HTTP/security

- No new HTTP endpoint is required. Existing `ApiBearerSecurityHttpTest` and route/context smoke coverage remain green and confirm no `/api/v1` permit or session-policy change.
- A log-capture integration test proves payload markers, claim tokens, and arbitrary exception messages do not appear in worker logs.
- Metrics tests prove only the bounded allowed tag vocabulary and no job/owner/worker IDs or exception text in tags.

## Acceptance criteria

1. Flyway migrates a fresh database and a V2 database to V2.1; accepted migrations remain byte-for-byte unchanged and Hibernate validates the job mapping.
2. Job constraints reject every invalid lifecycle row described above, while allowed initial, retry, running, terminal, system-owned, and user-owned rows persist.
3. Submitting one valid registered job creates one `READY` row with attempt zero, canonical object payload, caller UUID identity, and injected timestamps.
4. Exact repeated and concurrent submissions return the same job without resetting it; conflicting UUID reuse returns `JOB_SUBMISSION_CONFLICT` and leaves the first row unchanged.
5. A claim uses PostgreSQL `FOR UPDATE SKIP LOCKED`, filters to supported due types, follows exact deterministic ordering, commits before handler execution, increments attempts once, and writes a fresh fenced lease.
6. Coordinated multi-worker PostgreSQL tests prove no job is simultaneously claimed by two workers and locked work does not block progress on other due rows.
7. Heartbeat and every terminal/retry transition require the current claim token. Stale or wrong-token workers cannot mutate the row.
8. Retryable, permanent, unexpected, and exhausted failures produce the exact durable states, bounded safe codes, attempt counts, completion/backoff times, logs, and metrics.
9. Periodic heartbeats protect live work; bounded recovery requeues or fails truly stale work exactly once, including equality and both lock-order races.
10. The worker never exceeds configured local concurrency, does not claim unsupported work, does not hold database transactions around handlers, and shuts down without manufacturing terminal outcomes.
11. Production starts cleanly with zero registered handlers and performs no claim query in that state.
12. No job payload, claim token, credential, arbitrary exception message, or high-cardinality identifier is logged or used as a metric tag.
13. No HTTP route/security/session behavior, financial table/fact, provider/network integration, Spring Batch dependency, broker, frontend, or concrete business job is added.
14. Focused pure and PostgreSQL gates, the complete test suite, Spotless, and Maven `verify` pass with no skipped required tests.
15. The completion record reports actual production sizing and any deviation; `STATE.md` and `progress-report.md` are reconciled without activating a later PR.

## Documentation completion

Before this implementation unit is considered complete:

- fill in this specification's Completion Record;
- update `docs/implementation/STATE.md` with the V2.1 migration, implemented job lifecycle, transaction/fencing decisions, test totals, and real deferred work;
- update `docs/review/progress-report.md` with the R1 durable-job completion and verification totals;
- keep `CURRENT.md` on PR-021 through implementation and review; do not draft or activate PR-022 during implementation/review.

Do not put detailed implementation history into `STATE.md`; keep it as a concise repository handoff.

## Verification commands

```bash
./mvnw "-Dtest=JobValueObjectTest,PlatformJobLifecycleMigrationTest,DurableJobSubmissionServiceTest,JobLifecycleRepositoryTest,DurableJobLifecycleServiceTest,DurableJobWorkerTest,DurableJobConcurrencyTest,ContextSmokeTest,ApiBearerSecurityHttpTest" test
./mvnw test
./mvnw verify
git status --short
git diff --check
```

The focused class names may change to match the final idiomatic package split, but the completion record must list the exact command and retain all required proof categories.

## Completion record

Fill this before marking the PR complete.

### Implemented

- Not implemented; specification only.

### Deviations from specification

- None.

### New decisions

- None.

### Tests executed

- Not executed for PR-021; specification only.

### Follow-up work

- Concrete job producers/handlers remain capability-owned future work and must carry their own idempotent domain-write tests.
- Running-job cancellation, retention/purge, user-visible status APIs, progress/results, recurring schedules, priorities/dependencies, and an outbox remain deferred until a concrete requirement justifies them.
