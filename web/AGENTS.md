# Web instructions

- Read the root [AGENTS.md](../AGENTS.md), the [frontend standards](../docs/engineering/frontend-standards.md) and the [UI design guidelines](../docs/engineering/ui-design-guidelines.md) before substantial frontend changes.
- Work from `web/`. Keep routes focused on navigation and retain existing server contracts.
- Generate `src/routeTree.gen.ts` with the Vite router plugin. Generate `src/api/schema.d.ts` with `npm run generate:openapi` against Spring.
- Run `npm run typecheck`, `npm run check` and `npm run build` for frontend verification.
