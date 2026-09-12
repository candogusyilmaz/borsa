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
   - UI Design Guidelines: [ui-design-guidelines.md](../docs/engineering/ui-design-guidelines.md)
   Future frontend units use the `UI-xxx` specification namespace (e.g. `UI-001`).

## Operating rules

- Do not modify `server/` unless explicitly requested.
- Backend API contracts are authoritative. Do not invent server endpoints, query parameters, or response fields.
- When API behavior is unclear, inspect the relevant server controller/request/response code without modifying it unless authorized.
- Follow [ui-design-guidelines.md](../docs/engineering/ui-design-guidelines.md) for all frontend styling, component creation, and layout architecture:
  - **No tests in frontend**: Never author `.test.ts` or `.test.tsx` files.
  - **Touch targets >= 44px**: All buttons, inputs, pills, chips, and interactive elements must have `min-height: 44px`.
  - **Card elevation tokens**: Use `box-shadow: var(--mantine-shadow-sm)` for resting cards and `var(--mantine-shadow-md)` for hover cards; avoid raw `rgba()` shadows; buttons have no resting shadow.
  - **Light mode canvas**: Keep light mode canvas flat and solid (`var(--mantine-color-body)`); radial gradients are strictly dark-mode only.
  - **Dropdown single-line truncation**: Dropdown options must never wrap to multiple lines (`min-width: 0; white-space: nowrap; text-overflow: ellipsis;`).
  - **Input layout stability**: Anchor inputs directly beneath labels; render descriptions underneath (`inputWrapperOrder: ['label', 'input', 'description', 'error']`).
  - **Mobile layout standards**: Stack action buttons full-width on mobile (< 36em); arrange presets in structured grids; stack modal actions vertically (`column-reverse`).
  - **Approachable copy**: Write plain, clear English; avoid dense accounting/ledger jargon.
  - **State management**: Use `$api.useQuery` and `$api.useMutation` directly; do not manage async loading/error states with ad-hoc `useState`.
  - **Notifications**: Call `notifications.show(...)` directly without wrapping or forwarding.
- The user owns Git lifecycle. Never branch, commit, merge, rebase, push, stage, or reset unless explicitly requested. Keep changes in the working tree for review.

## Frontend verification conventions

Run frontend commands from `web/`:

```powershell
npm run typecheck
npx biome check ./src
npm run build
```
