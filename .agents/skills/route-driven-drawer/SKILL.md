---
name: route-driven-drawer
description: Implement or convert a URL-addressable workflow into a nested TanStack Router drawer in this repository's React frontend. Use for route-driven drawer work, not transient local dialogs.
---

# Route-driven drawers

Use the current `web/` implementation as the source of truth. Read `web/AGENTS.md` and inspect the nearest parent route, an existing child drawer route, `shared/components/route-drawer`, and `shared/components/responsive-drawer` before editing. Older local modals and drawers are not the route pattern.

Choose a child route when the workflow has a meaningful URL, should survive a direct visit or refresh, and should leave its parent page mounted. Keep a transient interaction as a local dialog when it needs none of those properties.

```text
Parent route
├── persistent page content
└── Outlet
    └── Child workflow route
        └── RouteDrawer
            └── Feature component
```

## Responsibilities

- **Parent route:** Render the persistent page and `<Outlet />`. When the route identifies a server resource, resolve it through the canonical detail query and handle pending, error, and not-found states before rendering the outlet. Ancillary layout/list data must not become the authority for that resource.
- **Child route:** Live under its parent in `web/src/routes/` (for example, `$resourceId/edit.tsx` under `$resourceId.tsx`). Read required server data from the canonical TanStack Query cache/query used by the parent. Reuse the same query key/options; do not derive the selected resource from unrelated list/layout state. Enforce workflow eligibility, choose the feature component and defaults, compose `RouteDrawer` directly, and decide where `onExited` navigates.
- **`RouteDrawer`:** Own generic `opened` state, `close`, and the exit callback. It uses `ResponsiveDrawer` for Mantine presentation. The current implementation mounts closed and opens from a `requestAnimationFrame` in `useEffect`; `onExitTransitionEnd` calls `onExited`. Leave its children mounted while closing.
- **Feature component:** Own form/session state, validation, mutation, query invalidation, and feature-specific feedback. Accept a simple `onClose` callback; do not accept `opened`, transition callbacks, or route-history concerns.

The shared `ResponsiveDrawer` already selects a mobile bottom sheet or desktop right drawer, with the project's sizes, scrolling, and transitions. Reuse it through `RouteDrawer` instead of restyling a second drawer.

## Route composition

In this composition sketch, `detailPath` stands for the resource's existing typed OpenAPI detail path; use its actual route-param name and query shape:

```tsx
function EditRoute() {
  const { resourceId } = Route.useParams();
  const navigate = useNavigate();
  const { data: resource } = $api.useQuery('get', detailPath, {
    params: { path: { resourceId } }
  });

  // The parent holds its Outlet until loading/error/not-found is handled.
  if (!resource) return null;

  return (
    <RouteDrawer
      title="Edit"
      onExited={() => navigate({ to: '/app/resources/$resourceId', params: { resourceId }, replace: true, resetScroll: false })}>
      {({ close }) => <EditForm resource={resource} onClose={close} />}
    </RouteDrawer>
  );
}
```
A verified type-safe `to: '..'` is also acceptable when the route hierarchy makes the parent unambiguous.

Use the actual route param, typed OpenAPI path, feature props, title, and eligibility rule. Verify that `to: '..'` resolves to the intended parent in that route; use an explicit typed parent path if it does not. Opening navigation goes to the child URL with its route params, usually with `resetScroll: false`. Do not store the resource or navigation origin in location state.

Explicit close means `close()` → exit animation with full content mounted → `onExited` → replace child URL with the logical parent → child unmounts and local form state resets. A direct visit follows the same close path. Browser Back keeps normal router behavior; it need not animate like the explicit close action.

## Avoid

- `Route → FeatureRouteWrapper → FeatureOverlayWrapper → RouteDrawer → FeatureForm` when `Route → RouteDrawer → FeatureForm` is sufficient. A little duplication between child routes is fine.
- Context, global stores, registries, overlay managers, or custom history/origin state to decide where Close goes.
- Resource or feature rules, query invalidation, API calls, or route destinations inside `RouteDrawer`.
- Mounting the drawer already open, timer-based animation coordination, or removing children as soon as `opened` becomes false.
- Hand-editing `routeTree.gen.ts` or inventing an API endpoint/query key.

## Check before finishing

1. Open the child from its parent; verify the parent stays mounted and the drawer visibly enters.
2. Close via button, overlay, and Escape; verify content remains intact through exit and navigation lands on the logical parent.
3. Directly load and refresh the child URL; verify resource, parent page, and drawer render without a previously loaded list, and Close still reaches the parent.
4. Check browser Back, eligibility rejection, loading/error/not-found handling, and fresh form state on reopen.
5. Confirm parent and child share the same TanStack Query detail key; do not add a request or change cache policy just for the drawer.
6. From `web/`, run `npm run typecheck`, `npx biome check ./src`, `npm run build`, and relevant existing tests. Follow `web/AGENTS.md` on frontend test files.
