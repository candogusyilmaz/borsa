# Backend implementation agent guide

This is a manual implementation role. The planner, implementer, reviewer, Git owner, and PR transitions remain
separate human-controlled steps.

## Just-in-time context loading

Before editing:

1. Read the repository [operating contract](../../AGENTS.md).
2. Read [CURRENT.md](../../docs/implementation/CURRENT.md).
3. Read the active PR specification named there completely.
4. Use that specification's references and changed surfaces to identify the initial relevant sections of the coding
   standards, backend master plan, accounting contract, and feature documents.
5. Inspect the actual affected code, migrations, tests, and configuration before making implementation decisions.
6. Expand documentation context only when affected code or concrete repository evidence requires another authoritative
   rule.

Do not preload completed PR specifications, unrelated roadmap sections, or unrelated feature-design documents. Read a
historical PR only to resolve a concrete prerequisite, compatibility, or regression question that current state and
code cannot answer. Do not reconstruct historical reasoning for its own sake.

If no implementation PR is active, do not invent production scope.

## Implementation contract

- Implement the active specification completely, including every acceptance criterion and required test.
- Do not quietly defer specified behavior, broaden into later roadmap work, add speculative infrastructure, or pad the
  change with unrelated cleanup.
- Work backend-first. Do not edit `src/main/web` unless the user or active specification explicitly includes frontend
  work.
- Keep one Maven project, one Spring Boot process, one PostgreSQL database, and one deployable artifact.
- Flyway owns schema creation; Hibernate/JPA validates mappings and never owns DDL.
- Preserve the repository's financial semantics and read the accounting contract sections required by the active
  capability.

## Verification and handoff

- Run the active specification's focused tests, relevant integration/API/security and migration tests, formatter, full
  suite, and `verify` commands when the environment permits.
- Self-review the complete active-unit change surface against the active specification before claiming completion. This
  includes relevant unstaged, staged, untracked, and already committed changes; derive any committed review range from
  the active specification, current state, and Git history rather than assuming the work exists only in `git diff`.
- Record implemented scope, deviations, verification, and follow-ups in the active PR completion record.
- Update current documentation with current reality only: replace superseded `STATE.md` facts, promote verified durable
  rules to their authoritative documents, and put reusable environment/tool lessons in the command playbook.
- Keep detailed implementation history in the PR completion record and Git, not in `STATE.md`; do not preserve temporary
  hypotheses, debugging notes, or completed checklist noise.
- Preserve unrelated user changes; never discard, overwrite, or reformat them merely to simplify implementation.
- Review is read-only and all Git history operations remain under the user's control. Do not stage, commit, branch,
  reset, stash, push, or otherwise mutate Git.

Consult the [command playbook](../../docs/engineering/codex-command-playbook.md) before retrying a documented Windows,
sandbox, Maven, Docker/Testcontainers, permission, or output failure.
