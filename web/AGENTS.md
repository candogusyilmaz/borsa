# Web instructions

- Work from `web/`. Read the [UI design guidelines](../docs/engineering/ui-design-guidelines.md) for UI changes.
- Keep app composition in `src/app/` and route definitions, guards, and loaders in `src/routes/`. Keep screens and workflows with their features, and generic formatting, feedback, and UI in `src/shared/`.
- Use the existing OpenAPI client and TanStack Query for server state. Keep cache invalidation aligned with the backend contract. The backend owns authoritative financial values and rounding.
- Use the existing Mantine form, notification, and overlay patterns. Validate near the form and show errors by the affected field or action.
- Authenticated locale preferences take precedence over device defaults once loaded.
- Never manually edit `src/routeTree.gen.ts` or `src/api/schema.d.ts`. Use the Vite router plugin for the route tree. For relevant backend contract changes, run `npm run generate:openapi` against Spring running the current backend code, review the generated diff, and verify affected consumers.
- Use `npm run format` to apply formatting and `npm run check` for read-only Biome verification. The `lint` and `check:fix` scripts also write changes.
- Choose `npm run typecheck` for a focused type check or `npm run build` to verify production output. The build already includes type checking.

## Frontend testing

- Default to the checks above and browser verification. Use temporary smoke tests or scripts when more behavioral verification is useful. A small or empty permanent suite is acceptable when these checks adequately verify behavior.
- Add permanent tests only for clear regression value. Candidates include non-trivial financial calculations, financial input parsing, significant data transformations, critical auth or session behavior, and regressions that are hard to verify reliably with simpler checks.
- Skip routine tests for components, hooks, pages, forms, stores, routes, and UI workflows. Avoid assertions about framework behavior, rendering details, straightforward state changes, or trivial event handlers. Skip frontend coverage targets.
- Get explicit approval before adding Vitest, React Testing Library, Playwright, Cypress, or other frontend testing infrastructure.
