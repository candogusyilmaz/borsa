# Repository agent instructions

This file is the repository operating contract and context router. Keep it small.

The repository is a plain monorepo containing two physically separate applications:
- `server/` — Spring Boot backend application
- `web/` — React frontend application

For recurring Windows, sandbox, Maven, Docker/Testcontainers, Git, or tool issues, consult [the command playbook](docs/engineering/codex-command-playbook.md).

## Task scope and routing

Determine the requested task scope before loading detailed context:

1. **Backend tasks**: read and follow [server/AGENTS.md](server/AGENTS.md).
2. **Frontend tasks**: read and follow [web/AGENTS.md](web/AGENTS.md).
3. **Cross-stack tasks**: load both only when explicitly required by the active task.

Agents should not inspect or modify the other application by default.

## Task modes

Respect the requested task mode:

- **Review tasks**: inspect the complete active change surface; review is read-only.
- **Implementation tasks**: complete only the active specification within the requested application scope; keep changes in the working tree.
- **Planning tasks**: define exactly one next bounded specification without implementing it.

Do not spawn, delegate to, or coordinate coding agents, subagents, background jobs, or automatic planner/implementer/reviewer chains unless explicitly requested by the user.

## Git workflow

The user owns Git history. Unless explicitly requested:
- Never branch, commit, merge, rebase, cherry-pick, tag, push, stage, reset, clean, stash, or create pull requests.
- Read-only inspection (`git status`, `git diff`, `git log`, `git show`, `git rev-parse`) is allowed.
- Keep implementation changes in the working tree for user review. Preserve unrelated user changes.

## Context discipline

- The current repository code is the source of truth.
- Load detailed documentation just in time from authoritative documents.
- Do not duplicate authoritative rules across documents.
- Persist only reusable, verified lessons.
