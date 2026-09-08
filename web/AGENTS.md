# Frontend agent instructions

This file is the initial operating contract and context router for the frontend application under `web/`.

## Context routing

1. Scope is strictly `web/`.
2. Read root [AGENTS.md](../AGENTS.md).
3. Frontend implementation track is located under:
   - Pointer: [CURRENT.md](../docs/implementation/web/CURRENT.md)
   - State: [STATE.md](../docs/implementation/web/STATE.md)
   - Workflow: [README.md](../docs/implementation/web/README.md)
   - Standards: [frontend-standards.md](../docs/engineering/frontend-standards.md)
   Future frontend units use the `UI-xxx` specification namespace (e.g. `UI-001`).

## Operating rules

- Do not modify `server/` unless explicitly requested.
- Backend API contracts are authoritative. Do not invent server endpoints, query parameters, or response fields.
- When API behavior is unclear, inspect the relevant server controller/request/response code without modifying it unless authorized.
- Frontend-specific architecture, styling, component standards, and template migration rules will be documented here and in linked frontend docs once the frontend-template unit is activated.
- The user owns Git lifecycle. Never branch, commit, merge, rebase, push, stage, or reset unless explicitly requested. Keep changes in the working tree for review.

## Frontend verification conventions

Run frontend commands from `web/`:

```powershell
npm run typecheck
npx biome check ./src
npm run build
```
