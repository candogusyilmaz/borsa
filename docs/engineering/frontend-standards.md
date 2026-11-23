# Frontend standards

- Keep bootstrap, providers, router initialization and app-wide composition under `web/src/app/`.
- Keep route definitions, navigation parameters, guards and loaders under `routes/`. Let feature pages own substantial screen composition.
- Put feature-specific components, hooks, workflows and presentation next to their owning feature. Expose cross-feature capabilities through small public imports. Keep generic formatting, feedback and reusable UI under `shared/`.
- Use the OpenAPI client in `api/` and TanStack Query for server state. Keep cache keys and invalidation consistent with the backend contracts. The backend owns authoritative financial values and rounding.
- Keep validation near the form. Show errors next to the relevant field or action. Use the existing Mantine form, notification and overlay patterns.
