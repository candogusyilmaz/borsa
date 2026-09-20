# Frontend Agent Instructions

This file is the operating contract and context router for work under `web/`.

## Context

Before substantial frontend work:

1. Read the root [AGENTS.md](../AGENTS.md).
2. Follow:
   - [frontend-standards.md](../docs/engineering/frontend-standards.md) for frontend engineering decisions.
   - [ui-design-guidelines.md](../docs/engineering/ui-design-guidelines.md) for UI and interaction decisions.

Current frontend structure, capabilities, dependency versions, routes, and implementation details must be discovered from the repository itself rather than from manually maintained snapshot documentation. Do not duplicate durable guidance here. Inspect the current codebase for implementation details and existing patterns.

## Scope

- Frontend implementation changes belong under `web/` unless explicitly requested otherwise.
- Do not modify `server/` unless explicitly requested.
- You may inspect relevant backend code when necessary to understand an API contract or domain behavior.
- Backend/API contracts are authoritative. Do not invent endpoints, parameters, fields, or server behavior.
- Generated files should not be edited manually unless the repository explicitly expects that workflow.

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

For normal frontend implementation work, verify at least:

```powershell
npm run typecheck
npx biome check ./src
npm run build
```

If a command or script changes, follow the repository's current configuration rather than this document.

Report verification failures clearly; do not hide or work around them.
