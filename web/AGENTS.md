# Web instructions

- Read the root [AGENTS.md](../AGENTS.md), the [frontend standards](../docs/engineering/frontend-standards.md) and the [UI design guidelines](../docs/engineering/ui-design-guidelines.md) before substantial frontend changes.
- Work from `web/`. Keep routes focused on navigation.
- Generate `src/routeTree.gen.ts` with the Vite router plugin. Generate `src/api/schema.d.ts` with `npm run generate:openapi` against Spring.
- Run `npm run typecheck`, `npm run check` and `npm run build` for frontend verification.

## Frontend testing

- Default to the checks above and browser verification. Use temporary smoke tests or scripts when more behavioral verification is useful. A small or empty permanent suite is acceptable when these checks adequately verify behavior.
- Add permanent tests only for clear regression value. Candidates include non-trivial financial calculations, financial input parsing, significant data transformations, critical auth or session behavior, and regressions that are hard to verify reliably with simpler checks.
- Skip routine tests for components, hooks, pages, forms, stores, routes, and UI workflows. Avoid assertions about framework behavior, rendering details, straightforward state changes, or trivial event handlers. Skip frontend coverage targets.
- Get explicit approval before adding Vitest, React Testing Library, Playwright, Cypress, or other frontend testing infrastructure.
