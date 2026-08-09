# Repository agent instructions

This file is the repository-level entry point for coding agents. Agents that support `AGENTS.md` should discover it automatically. The user should not have to repeat "follow the coding standards" or "read the current PR spec" in every prompt.

## Required instruction chain

Before editing backend code or migrations:

1. Read `docs/implementation/CURRENT.md`.
2. If it points to an active PR specification, read that specification completely and implement only its scope.
3. Read `docs/engineering/coding-standards.md`.
4. Read the relevant sections of `docs/review/backend-master-plan.md`.
5. For any financial behavior, read the relevant sections of `docs/review/accounting-contract.md`.
6. Read feature-specific review/design documents only when the active PR references them or the affected behavior requires them.

If `CURRENT.md` says no implementation PR is active, do not invent a broad implementation task. Documentation/planning work is allowed; production implementation should wait for a bounded PR specification.

Instruction precedence for implementation is:

1. explicit user request for the current task;
2. active PR specification;
3. `docs/review/accounting-contract.md` for shared financial semantics;
4. `docs/review/backend-master-plan.md`;
5. `docs/engineering/coding-standards.md`;
6. older review/design documents.

If two authoritative documents genuinely conflict, stop expanding scope and surface the conflict rather than silently choosing a convenient interpretation.

## Git and review workflow

Implementation PR specifications are human-review units; they do not require a separate Git branch.

### Default workflow

Unless the user explicitly activates an automated local-commit batch:

- The user owns Git history.
- Do not create/switch branches, commit, amend, merge, rebase, reset, stash, clean, tag, or push.
- Make working-tree changes only.
- Do not advance `docs/implementation/CURRENT.md` to the next PR automatically.
- Keep the active PR selected until the user has reviewed the diff and explicitly advances it.
- You may inspect Git state/history (`git status`, `git diff`, `git log`, `git show`, `git rev-parse`) when useful, but do not mutate Git state.

### Automated local-commit batch

This mode is active only when the user's current task explicitly requests an autonomous multi-PR/local-commit workflow.

In this mode, responsibilities are separated by agent role:

#### Supervising agent

The supervising planning/review agent may:

- inspect Git state and history;
- stage files belonging to a completed active PR;
- create exactly one local commit for each successfully completed PR;
- after that commit, advance repository implementation state and begin planning exactly one next PR.

The supervising agent must:

- commit only after independent review reports no unresolved `MUST FIX` findings;
- run or verify all required acceptance checks before committing;
- stage only files belonging to the active PR;
- ensure the working tree is clean after the commit before beginning the next PR;
- derive the next PR from the newly committed repository state rather than pre-planning future PRs.

The supervising agent must never:

- push;
- create or update remote branches;
- create GitHub pull requests;
- amend an existing commit;
- rebase;
- reset;
- merge;
- cherry-pick;
- stash or clean away user work;
- modify Git remotes;
- force-update history.

#### Implementation agent

The implementation agent must not mutate Git state.

It may inspect Git state/history when useful, but must not:

- stage;
- commit;
- branch;
- amend;
- merge;
- rebase;
- reset;
- stash;
- clean;
- tag;
- push.

It leaves its implementation in the working tree for independent review.

#### Review agent

The review agent is read-only.

It must inspect the actual complete diff and changed files, but must not:

- modify production code;
- modify tests;
- modify documentation;
- stage or commit files;
- perform any other Git mutation.

#### PR transition rule

In automated local-commit batch mode, `docs/implementation/CURRENT.md` remains on the active PR throughout implementation and review.

It may advance only after:

1. every acceptance criterion has been verified;
2. required tests and verification commands pass;
3. independent review has no unresolved `MUST FIX` findings;
4. the completed PR has been committed locally.

Only then may the supervising agent derive and create exactly one next PR specification.

If the current PR cannot satisfy these conditions, do not advance `CURRENT.md`.

## Current technology baseline

- Java 25. Use stable/final Java 25 features where they improve clarity. Do not enable preview, incubator or experimental JDK features unless an explicit later PR approves one.
- Spring Boot 4.1.0 is the pinned rewrite baseline.
- Spring Framework, Spring Data, Hibernate ORM, Hibernate Validator, Jackson and related platform libraries use Spring Boot-managed versions by default. Spring Boot 4.1.0 currently manages Spring Framework 7.0.8, Spring Data JPA 4.1.0 and Hibernate ORM 7.4.1.Final. Do not override them merely to chase a newer version.
- Jakarta Persistence 3.2 is the stable JPA contract. Stable modern features are allowed and encouraged when they simplify correct code.
- Stable Hibernate-specific features are allowed for a concrete requirement; experimental ORM tracks are not.
- PostgreSQL + Flyway + Testcontainers remain the persistence/migration/integration-test foundation.

## Scope and architecture guardrails

- Work backend-first. Do not change `src/main/web` unless the active PR or user explicitly requests frontend work.
- Keep one Maven project, one Spring Boot process, one PostgreSQL database and one deployable artifact.
- Organize Java by coarse business capability. Direct service calls, JPA references, SQL joins and cross-schema foreign keys are allowed.
- A capability owns writes/invariants for its tables; strict module isolation is not a goal.
- Do not introduce microservices, brokers, separate Maven modules, ports-and-adapters ceremony or generic frameworks without a concrete requirement in the active PR.
- R0–R16 are roadmap increments, not PR sizes. Keep each PR small enough for a human to review comfortably. Do not implement later roadmap items "while here."

## Financial guardrails

- The development database is disposable; the scratch rewrite targets `extreme_accounting`. Never execute `docs/review/db-dump.sql` as the new baseline.
- Flyway is the only schema creator; Hibernate validates mappings and never owns DDL.
- Posted financial facts are immutable; corrections use reversal/supersession.
- Balances, positions, valuations and analytics are rebuildable projections.
- Plans, obligations, forecasts, scenarios and unreviewed extraction previews never move actual money.
- Manual entry and file import are permanent primary workflows. Do not design Open Banking, bank/card/broker sync, payment initiation or speculative connection infrastructure unless the user explicitly changes scope.
- Opening state has an explicit historical-coverage boundary and is not income/spending/performance.
- Scheduled payments may name an intended funding account but never post money until an actual payment is recorded/confirmed.
- Shared-expense claims retain provenance to the originating activity; settlements support partial/multi-claim allocation and are not income or duplicate spending.
- Follow `docs/review/accounting-contract.md` exactly for signs, time ordering, fee/tax treatment, cost basis, FX selection, balances, valuation/performance, projection status, idempotency and concurrency.

## Data/provider guardrails

- Core tests and core feature behavior must work without network access.
- Manual/file/synthetic data comes before optional live providers.
- Demo financial facts go through normal application commands; synthetic observations go through normal ingestion. Never seed derived projections directly.
- Synthetic/manual providers are in-process implementations. Use WireMock/equivalent only to contract-test a real HTTP adapter, not as the normal demo architecture.
- Preserve provenance, source, quality and coverage. Never present synthetic/sample data as real or official.

## Completion discipline

- Follow the active PR's acceptance criteria and verification commands.
- Run the relevant pure, Testcontainers, API/security and migration tests before claiming completion.
- Fill the PR specification completion record: implemented scope, deviations, tests, follow-ups.
- Update `docs/review/progress-report.md` when the PR materially changes implementation status or architectural decisions.
- Preserve unrelated user changes. Never reset/delete unrelated work to make a task easier.
- In automated local-commit batch mode, completion documentation belongs to the same local commit as the implementation it describes.
- A PR is not complete merely because the implementation agent reports success. Completion requires independent review, successful required verification, and the supervising agent's final acceptance.
