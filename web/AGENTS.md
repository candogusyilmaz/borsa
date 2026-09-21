# Frontend Agent Instructions

This file is the operating contract and context router for work under `web/`.

## Context

Before substantial frontend work:

1. Read the root [AGENTS.md](../AGENTS.md).
2. Follow:
   - [frontend-standards.md](../docs/engineering/frontend-standards.md) for frontend engineering decisions.
   - [ui-design-guidelines.md](../docs/engineering/ui-design-guidelines.md) for UI and interaction decisions.

Current frontend structure, capabilities, dependency versions, routes, and implementation details must be discovered from the repository itself rather than from manually maintained snapshot documentation. Do not duplicate durable guidance here. Inspect the current codebase for implementation details and existing patterns.

Frontend progress is tracked externally; frontend work does not rely on `STATE.md`, `CURRENT.md`, or dedicated specification documents. Treat the active task description and the current repository code as the source of truth.

Generated files (`src/api/schema.d.ts`, `src/routeTree.gen.ts`) are build artifacts, not architectural reference material or coding patterns. Do not inspect them by default when learning codebase conventions, and never edit them manually.

## Scope

- Frontend implementation changes belong under `web/` unless explicitly requested otherwise.
- Do not modify `server/` unless explicitly requested.
- You may inspect relevant backend code when necessary to understand an API contract or domain behavior.
- Backend/API contracts are authoritative. Do not invent endpoints, parameters, fields, or server behavior.
- Generated files (`src/api/schema.d.ts`, `src/routeTree.gen.ts`) should never be edited manually. Regenerate them using the designated scripts when contracts or routes change.

## Working Style

- Inspect the relevant existing implementation before making changes.
- For architectural or structural frontend work, inspect multiple comparable routes/features before deciding the target pattern. Prefer repeated good codebase patterns over isolated examples, and follow [frontend-standards.md](../docs/engineering/frontend-standards.md) for responsibility boundaries.
- Prefer small, focused changes over broad rewrites.
- Reuse good existing patterns, but do not preserve an awkward pattern merely because it already exists.
- Do not introduce abstractions, dependencies, state managers, or infrastructure without a concrete need.
- Keep feature-specific code local unless there is a real cross-feature reason to share it.
- When requirements or existing behavior are ambiguous, investigate before guessing.

## Frontend-Specific Rules

- Do not author new frontend test files unless explicitly requested.
- Use the existing typed API/query infrastructure for server state.
- Do not duplicate remote server state into local or global client state without a concrete reason.
- Follow the current project's established form, error-handling, notification, styling, and routing conventions rather than inventing parallel systems.
- Follow the UI guidelines for mobile-first design and interaction behavior.
- Do not preserve obsolete internal frontend APIs for backward compatibility during active development. Migrate current call sites and remove the old API unless compatibility is required by an external contract or persisted data.

## Git

The user owns the Git lifecycle.

Do not:
- branch
- commit
- merge
- rebase
- push
- stage
- reset

unless explicitly requested.

Leave changes in the working tree for review.

## Verification

Use the current scripts defined by `web/package.json`.

For normal coding-agent verification, prefer:

```powershell
npm run verify:agent
```

It runs typecheck, Biome, tests, and a production build with reduced success noise while retaining actionable failure diagnostics.

Use the individual standard scripts (`npm run typecheck`, `npm run check`, `npm run test`, `npm run build`) when full output or targeted diagnostics are needed.

If a command or script changes, follow the repository's current configuration rather than this document.

Report verification failures clearly; do not hide or work around them.
