# Backend agent instructions

This file is the operating contract and context router for the Spring Boot application under `server/`.

## Context routing

For backend implementation or review:

1. Read [CURRENT.md](../docs/implementation/CURRENT.md).
2. Read the active PR specification named there completely. Treat the active PR specification as the primary execution-self-contained contract: it inlines every exact invariant, formula, changed schema detail, API contract detail, error code, ordering rule, transaction requirement, and acceptance behavior required to implement and verify that PR.
3. Do not preload long-lived design, review, or contract documents (`backend-master-plan.md`, `accounting-contract.md`, feature design documents, or historical PRs).
4. Consult an external authority document only when:
   - the active PR explicitly leaves an unstated invariant there;
   - the PR is ambiguous or contradictory;
   - current source code conflicts with the PR specification;
   - a security, accounting, or domain invariant cannot be resolved locally.
   When consulting external documents, read only the smallest relevant section rather than loading the entire file.

For backend planning:

1. Read [STATE.md](../docs/implementation/STATE.md), `CURRENT.md`, and [the implementation workflow](../docs/implementation/README.md).
2. Read [PR-TEMPLATE.md](../docs/implementation/PR-TEMPLATE.md).
3. Read only the roadmap, domain, accounting, and feature-design sections needed to define the next bounded unit. Ensure the generated specification is execution-self-contained so implementers do not need to preload external documents.

Completed PR specifications and Git history are historical evidence; do not preload them unless current code or a concrete compatibility question requires them. If no implementation PR is active, do not invent production scope.

## Context and tool output discipline

- Search before reading (`rg`, `find_by_name`).
- Read the smallest relevant source region; avoid loading entire large classes when inspecting methods.
- Do not repeatedly dump or re-read unchanged source files after edits.
- Prefer targeted diffs during implementation (`git diff -- path/to/file`) instead of repository-wide diffs.
- Prefer the smallest focused test gate during the inner loop.
- Verbose Maven, test, or build output should be redirected to a file when practical (e.g. `.\mvnw.cmd "-Dtest=..." test *> target/agent-logs/test.log`). On success, inspect only the summary tail (e.g. `Get-Content target/agent-logs/test.log -Tail 20`). On failure, inspect only the relevant failing-test section or bounded tail and expand only if necessary. Never dump large build logs into the conversational context.

## Instruction precedence

1. The explicit user request for the current task.
2. The active backend PR specification.
3. `accounting-contract.md` for shared financial semantics.
4. `backend-master-plan.md` for architecture, roadmap, and sequencing.
5. `coding-standards.md` for implementation style.
6. Relevant feature documents and older review material.

If authoritative documents conflict, report the conflict instead of silently choosing one interpretation.

## Backend constraints

- Work within `server/`. Do not inspect or modify `web/` unless explicitly requested.
- Keep one Maven project, one Spring Boot process, one PostgreSQL database, and one deployable artifact. Organize code by coarse capability; do not introduce microservices, brokers, extra modules, or generic framework ceremony without a concrete requirement.
- Java 25 and Spring Boot 4.1.x baseline.
- Flyway owns schema creation; Hibernate/JPA validates the schema and does not own DDL.
- Core behavior must work without network access. Manual, file, and synthetic data precede optional live providers; preserve provenance.
- For financial behavior, follow [accounting-contract.md](../docs/review/accounting-contract.md).
- Parked MyBatis/read-persistence work remains parked until explicitly activated.
- Consult [the command playbook](../docs/engineering/codex-command-playbook.md) before retrying known environment or tool failures.

## Backend verification conventions

Separate verification into two distinct phases:

### Inner-loop verification
During implementation, run only the smallest focused test gate specified by the active PR:
```powershell
.\mvnw.cmd "-Dtest=FocusedTestA,FocusedTestB" test
```
Do not run the complete test suite or Maven `verify` during normal inner-loop development.

### Exit gate
Run once only after implementation and inner-loop verification are complete:
```powershell
.\mvnw.cmd verify
```
Maven `verify` automatically executes Enforcer, compilation, tests, packaging, and Spotless checks in a single lifecycle execution. Run `.\mvnw.cmd spotless:apply` beforehand if formatting adjustments are needed.

### Exit gate failure handling
If the full exit gate fails:
```text
identify specific failure
-> run only the affected focused test/check
-> fix until focused gate is green
-> rerun the complete exit gate once
```
Do not run `full verify -> tiny fix -> full verify` loops.

## Completion and context maintenance

After successful verification of an implementation unit:
- Update `docs/implementation/STATE.md` with current reality only, removing or replacing superseded statements.
- Keep detailed implementation history in the active PR completion record and Git, not in `STATE.md`.
- Leave `docs/implementation/CURRENT.md` and PR lifecycle transitions under the user's manual control.
