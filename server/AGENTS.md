# Backend agent instructions

This file is the operating contract and context router for the Spring Boot application under `server/`.

## Context routing

For backend implementation or review:

1. Read [CURRENT.md](../docs/implementation/CURRENT.md).
2. Read the active PR specification named there completely.
3. Use that specification's references and changed surfaces to identify relevant sections of:
   - [coding standards](../docs/engineering/coding-standards.md)
   - [backend master plan](../docs/review/backend-master-plan.md)
   - [accounting contract](../docs/review/accounting-contract.md)
   - feature-specific design documents
4. Expand documentation context only when affected code or concrete repository evidence requires another authoritative rule.

For backend planning:

1. Read [STATE.md](../docs/implementation/STATE.md), `CURRENT.md`, and [the implementation workflow](../docs/implementation/README.md).
2. Read [PR-TEMPLATE.md](../docs/implementation/PR-TEMPLATE.md).
3. Read only the roadmap, domain, accounting, and feature-design sections needed to define the next bounded unit.

Completed PR specifications and Git history are historical evidence; do not preload them unless current code or a concrete compatibility question requires them. If no implementation PR is active, do not invent production scope.

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

Run Maven commands from `server/`:

```powershell
.\mvnw.cmd spotless:check
.\mvnw.cmd test
.\mvnw.cmd verify
```

## Completion and context maintenance

After successful verification of an implementation unit:
- Update `docs/implementation/STATE.md` with current reality only, removing or replacing superseded statements.
- Keep detailed implementation history in the active PR completion record and Git, not in `STATE.md`.
- Leave `docs/implementation/CURRENT.md` and PR lifecycle transitions under the user's manual control.
