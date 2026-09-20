# Frontend implementation context

This directory contains the current technical and architectural state snapshot for the frontend application under `web/`.

## Workflow

Frontend implementation work is directly managed and prompt-guided rather than tracked through formal `UI-xxx` specification files or a `CURRENT.md` active unit pointer.

Agents working on the frontend should consult:
- [`web/AGENTS.md`](../../../web/AGENTS.md) for the frontend operating contract and working style.
- [`docs/engineering/frontend-standards.md`](../../engineering/frontend-standards.md) for architecture, responsibility boundaries, and frontend principles.
- [`docs/engineering/ui-design-guidelines.md`](../../engineering/ui-design-guidelines.md) for UI, layout, and interaction decisions.
- [`STATE.md`](STATE.md) for the verified factual snapshot of current frontend reality (technologies, routes, implemented capabilities, and verification status).

## Context discipline

- Frontend work is scoped to `web/` and this documentation lane.
- Backend API contracts remain authoritative; frontend work does not modify `server/` without explicit authorization.
