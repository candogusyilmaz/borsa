# Frontend implementation workflow

This directory contains the active implementation pointer, current-state handoff, and specifications for the frontend application under `web/`.

## Implementation track

Frontend specifications use the `UI-xxx` unit namespace (e.g. `UI-001`, `UI-002`), independent of the backend `PR-xxx` specifications.

Currently active unit: **UI-001 (Frontend Foundation Rebuild)**.

## Active-spec mechanism

- `CURRENT.md` is a stable pointer to exactly one active `UI-xxx` specification, or explicitly records that no UI unit is currently active (currently pointing to UI-001).
- `STATE.md` describes current frontend reality: technology baseline, implemented features, and verified status.

## Context discipline

- Frontend work is scoped to `web/` and this documentation lane.
- Backend API contracts remain authoritative; frontend work does not modify `server/` without explicit authorization.
