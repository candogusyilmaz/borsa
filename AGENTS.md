# Repository agent instructions

This file is the always-loaded operating contract and context router for the repository. Keep it small. Load detailed rules just in time from their authoritative documents.

For recurring Windows, sandbox, Maven, Docker/Testcontainers, Git, or output problems, consult [the command playbook](docs/engineering/codex-command-playbook.md) before retrying an equivalent approach.

## Context routing

For implementation or review:

1. Read [CURRENT.md](docs/implementation/CURRENT.md).
2. Read the active PR specification named there completely.
3. Use the active specification's references and affected surfaces to identify the initial relevant sections of [coding standards](docs/engineering/coding-standards.md), [the backend master plan](docs/review/backend-master-plan.md), [the accounting contract](docs/review/accounting-contract.md), and feature-specific design documents.
4. Expand documentation context only when affected code or concrete repository evidence requires another authoritative rule.

For planning:

1. Read [STATE.md](docs/implementation/STATE.md), `CURRENT.md`, and [the implementation workflow](docs/implementation/README.md).
2. Read [PR-TEMPLATE.md](docs/implementation/PR-TEMPLATE.md).
3. Read only the roadmap, domain, accounting, and feature-design sections needed to define the next bounded unit.

`CURRENT.md` is a pointer, `STATE.md` is current repository reality, and an active PR specification is the only implementation scope. Completed PR specifications and Git history are historical evidence; do not preload them or entire large design documents unless current code or a concrete compatibility question requires them. If no implementation PR is active, do not invent production scope.

## Instruction precedence

1. The explicit user request for the current task.
2. The active PR specification.
3. `accounting-contract.md` for shared financial semantics.
4. `backend-master-plan.md` for architecture, roadmap, and sequencing.
5. `coding-standards.md` for implementation style.
6. Relevant feature documents and older review material.

If authoritative documents genuinely conflict, stop expanding scope and report the conflict instead of silently choosing one interpretation.

## Manual role isolation

The user manually controls planning, implementation, review, Git history, and PR transitions.

- Planning defines exactly one next bounded specification and does not implement it.
- Implementation completes only the active specification and does not design or activate later work.
- Review inspects the complete active-unit change surface, including relevant unstaged, staged, untracked, or already committed changes, and is read-only.
- Do not spawn, delegate to, or coordinate coding agents, subagents, background jobs, or automatic planner/implementer/reviewer chains.

## Git workflow

The user owns Git history. Unless explicitly requested, do not create or switch branches, stage, commit, amend, merge, rebase, reset, cherry-pick, stash, clean, tag, push, change remotes, or create pull requests. Read-only inspection such as `git status`, `git diff`, `git log`, `git show`, and `git rev-parse` is allowed. Keep implementation changes in the working tree for review. Preserve unrelated user changes; never discard, overwrite, or reformat them merely to simplify the active task.

## Non-negotiable repository constraints

- Work backend-first; change `src/main/web` only when the user or active specification explicitly includes frontend work.
- Keep one Maven project, one Spring Boot process, one PostgreSQL database, and one deployable artifact. Organize code by coarse capability; do not introduce microservices, brokers, extra modules, or generic framework ceremony without a concrete requirement.
- Flyway owns schema creation. Hibernate/JPA validates the schema and does not own DDL.
- Do not implement later roadmap work, speculative infrastructure, or unrelated cleanup while completing an active unit.
- Core behavior must work without network access. Manual, file, and synthetic data precede optional live providers; preserve provenance and never present sample or synthetic data as official.
- For financial behavior, follow [accounting-contract.md](docs/review/accounting-contract.md). Do not restate its full sign, time, correction, balance, projection, idempotency, or concurrency contract here.

## Context discipline

- Load only task-relevant documentation and inspect the actual repository before making claims.
- Start with targeted `rg`/`rg --files` searches and focused commands; avoid broad dumps of large files or logs when filtered evidence is sufficient.
- Do not read historical PR specifications merely because they exist. Load one only for a concrete prerequisite, compatibility, or historical question that current state and code cannot answer.
- Consult `codex-command-playbook.md` before retrying a known environment or tool failure. Do not repeatedly try equivalent permission or elevated-access approaches.
- Persist only reusable lessons verified through successful execution. Store conclusions and procedures, not temporary hypotheses, one-off bugs, or command trajectories.
- Update authoritative current documents by replacing superseded facts; do not append obsolete narratives, completed checklists, or debugging notes.

## Completion and context maintenance

After successful verification of an implementation unit:

- update `STATE.md` with current reality only and remove or replace superseded statements;
- update architecture/domain documents only when verified behavior changes their authority;
- put reusable Windows, sandbox, Maven, Docker/Testcontainers, or tool-output lessons in the command playbook;
- keep detailed implementation history in the active PR completion record and Git, not in `STATE.md`;
- preserve temporary hypotheses and completed checklist noise only in the working conversation, not durable documentation;
- leave `CURRENT.md` and PR lifecycle transitions under the user's manual control.

An implementation unit is ready for the user's acceptance or commit decision only after required verification passes and review has no unresolved `MUST FIX` findings.

Documentation/context work is not permission to modify production code, tests, migrations, dependencies, application configuration, frontend code, or Git state.
