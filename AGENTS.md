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

## Manual agent workflow

The user manually controls planning, implementation, review, Git history, and PR transitions.

Do not spawn, invoke, delegate to, or coordinate other coding agents or subagents. Do not create background tasks or autonomous multi-agent workflows. Do not automatically chain planning, implementation, review, or the next PR.

Each invocation performs only the role explicitly requested by the user's current prompt:

- planning: inspect the repository state, create exactly one next PR specification, and update implementation planning documents as requested;
- implementation: implement only the currently active PR in the working tree;
- review: inspect the completed implementation and report findings without modifying files;
- other work: follow the user's explicit request without assuming one of the roles above.

Repository-defined agent files may be read when the user explicitly asks for them, but they must never be invoked or delegated to automatically.

## Git workflow

The user owns Git history.

Unless the user explicitly requests a specific Git operation:

- do not create or switch branches;
- do not stage files;
- do not commit or amend;
- do not merge, rebase, reset, cherry-pick, stash, clean, or tag;
- do not push;
- do not modify remotes;
- do not create GitHub pull requests;
- do not force-update history.

Git inspection is allowed when useful, including:

- `git status`
- `git diff`
- `git log`
- `git show`
- `git rev-parse`

Implementation work must remain in the working tree for the user's review.

Review work is read-only. A review invocation must not modify production code, tests, documentation, or Git state unless the user explicitly changes that role.

## PR lifecycle

Implementation PR specifications are human-review units; they do not require a separate Git branch.

`docs/implementation/CURRENT.md` identifies the active implementation unit.

During implementation:

- keep `CURRENT.md` pointing at the active PR;
- do not advance to another PR;
- do not create future PR specifications;
- complete only the active PR.

During review:

- review the actual complete diff and changed files;
- do not advance `CURRENT.md`;
- do not create the next PR;
- classify unresolved issues clearly.

When the user later invokes the planning prompt, the planning invocation may derive exactly one next PR from the repository's then-current state and update `CURRENT.md` accordingly.

The user decides when reviewed work is committed and when the repository is ready to move to the next PR.

## PR sizing

Future PRs should be substantial, coherent capability slices rather than the repository's earlier micro-PRs. Human reviewability still matters, but the default planning bias is to combine tightly coupled work until the PR reaches a meaningful end-to-end capability boundary.

These sizing rules apply when planning the next PR. They never authorize an implementation agent to broaden the already-active specification; finish the active PR exactly as written.

Use the following sizing rules:

- Target roughly **six to ten times the implementation surface of the earlier micro-PR style**. This is a directional heuristic, not a line-count quota.
- Start from the largest coherent capability boundary that one careful reviewer can still understand in a focused review session. Split only after identifying a concrete independent capability, invariant, migration risk, or review-complexity boundary.
- A normal PR should contain several substantive behaviors or invariants and usually cross multiple application layers. Three to six tightly coupled implementation steps is a useful default range.
- Prefer a complete user-visible vertical slice or meaningful subsystem increment over a single technical step or boundary wrapper.
- One PR may intentionally include the database/migration change, JPA/domain mapping, repository behavior, application service/orchestration, HTTP boundary, and the required tests when they all implement one coherent capability.
- Prefer combining adjacent steps that have no useful independent product or invariant boundary. Existing prerequisites are a reason to include more of the remaining capability, not a reason to create a thin exposure-only PR.
- Do not create a PR whose main purpose is merely adding one DTO, one repository method, one service wrapper, one controller endpoint, one mapper, one configuration class, or tests around already-complete behavior. A boundary-only PR is acceptable only when that boundary itself carries substantial independently reviewable security, compatibility, or operational risk, and the specification must explain that exception.
- Do not split a capability merely because it crosses application layers.
- Split work when it contains independent business capabilities, independent invariants, a risky migration that deserves isolated review, or a diff large enough that a careful human review becomes difficult.
- Do not make a PR larger by adding unrelated cleanup, speculative abstractions, future infrastructure, or later roadmap work.
- A good PR should leave the repository at a meaningful, demonstrable, testable capability boundary rather than an arbitrary code-layer boundary.
- R0–R16 are roadmap increments, not PR sizes. A single roadmap increment may require multiple PRs, and a single PR may cover several tightly coupled steps within that increment when doing so produces a clearer review unit.

Examples:

- Prefer registration persistence + validation + application orchestration + HTTP endpoint + integration tests in one PR when they form one registration capability.
- Prefer refresh-session locking + rotation + reuse detection + family revocation + HTTP refresh delivery + persistence/security tests in one PR when they form one refresh lifecycle capability.
- Prefer a coherent reference-data increment containing migration/seeds + mappings + repository/query behavior + API exposure + migration/HTTP tests rather than separate schema, mapping, and endpoint PRs.
- Avoid separate PRs for entity mapping, repository creation, service creation, controller exposure, response mapping, and HTTP tests when each PR is only a mechanical prerequisite for the next.

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
- Favor the simplest implementation that correctly completes the active capability.
- Do not implement later roadmap items "while here."

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
- Preserve unrelated user changes. Never reset or delete unrelated work to make a task easier.
- Implementation completion does not authorize a commit or transition to the next PR.
- A PR is ready for the user's commit decision only when its acceptance criteria are satisfied, required verification passes, and the manually invoked review has no unresolved `MUST FIX` findings.
