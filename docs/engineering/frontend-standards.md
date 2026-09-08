# Frontend engineering standards

Status: authoritative frontend engineering guidelines for the `web/` application.

These standards define **how frontend code is structured and written**. Product scope and specifications live under `docs/implementation/web/`; backend API contracts and domain meaning live under `server/` and `docs/review/`.

---

## 1. Monorepo architecture and scope discipline

### Plain monorepo structure
The repository is organized as a plain monorepo containing two physically separate applications:
- `server/` — Spring Boot backend application (Java 25, Gradle/Maven, PostgreSQL).
- `web/` — React frontend application (React 19, TypeScript strict, Vite, Mantine v9).

Each application maintains independent tooling, dependencies, and build pipelines:
- No shared npm workspaces or root `node_modules`.
- No cross-boundary imports between `server/` and `web/`.
- Frontend dependencies and scripts reside exclusively in `web/package.json`.

### Scope discipline
- All frontend implementation work is strictly contained within `web/` and `docs/implementation/web/`.
- Never modify or inspect `server/` files during frontend tasks unless explicitly instructed by the user.
- Backend API contracts are authoritative. Frontend implementations must never invent server endpoints, synthesize unofficial query parameters, or alter expected response payloads.

---

## 2. Source code organization

All frontend source code resides under `web/src/` with five distinct architectural tiers:

```
web/src/
├── api/          # Typed API client, OpenAPI schema, error normalization, auth middleware
├── app/          # App bootstrap, Mantine theme, global providers, root layout
├── features/     # Domain feature modules (encapsulated business capabilities)
├── routes/       # TanStack Router file-based routing tree (thin route definitions)
└── shared/       # Shared UI primitives, design tokens, common hooks, cross-cutting utilities
```

### Tier definitions

1. **`src/api/`**:
   - Authoritative OpenAPI schema definitions (`schema.d.ts`) generated directly from backend docs.
   - Pre-configured `openapi-fetch` client and `openapi-react-query` (`$api`) instance.
   - RFC 7807 ProblemDetail error normalization (`normalizeError`).
   - Token lifecycle and Bearer authentication middleware.

2. **`src/app/`**:
   - Global application providers (MantineProvider, QueryClientProvider, RouterProvider).
   - Mantine theme configuration (`theme.ts`).
   - Global application initialization and silent refresh bootstrap.

3. **`src/features/`**:
   - Cohesive domain slices (e.g. `auth`, `dashboard`, `portfolios`, `transactions`).
   - Detailed internal structure described in [Section 3](#3-feature-module-organization).

4. **`src/routes/`**:
   - File-based route definitions adhering strictly to TanStack Router naming conventions.
   - Thin wrappers responsible solely for routing, search validation, loaders, and auth guards.

5. **`src/shared/`**:
   - Truly reusable, domain-agnostic UI components (buttons, badges, icons, layout primitives).
   - Shared hooks (e.g. `useDisclosure`, `useMediaQuery`).
   - Universal type definitions and utilities (e.g. currency formatting, date helpers).

---

## 3. Feature module organization

Each business domain resides in `src/features/<feature-name>/` with a strict, consistent layout:

```
src/features/<feature-name>/
├── components/   # Feature-specific UI components, dialogs, forms, cards, tables
├── hooks/        # Feature-specific hooks (custom queries/mutations when justified)
├── pages/        # Feature page components assembled into full views
└── utils/        # Feature-specific transformations, calculators, validators
```

### Co-location principles
- **Default to local**: Components, hooks, types, and utility functions should live inside their corresponding feature directory.
- **Do not promote prematurely**: Keep utilities and components local to the feature until multiple unrelated features require them.
- **Export public interfaces**: Features should expose a clean surface (via index or explicit imports) to route files and other features, keeping internal sub-components private where appropriate.

---

## 4. Route files as thin wrappers

Route files in `src/routes/` are strictly **thin routing orchestrators**.

### Responsibilities of route files
- **Path and parameter definitions**: TanStack Router path pattern matching.
- **Search parameter validation**: Type-safe search parameter parsing using `validateSearch`.
- **Authentication & context guards**: Enforcing access control via `beforeLoad` (e.g. checking auth context and redirecting).
- **Route loaders**: Pre-fetching critical data via TanStack Query query options within `loader`.
- **Boundaries**: Declaring `errorComponent`, `pendingComponent`, and `notFoundComponent`.

### Prohibitions in route files
- **No inline UI implementation**: Route components must not declare extensive JSX layouts, inline styling, or complex DOM structures. They must immediately delegate rendering to a feature page component (e.g. `<DashboardPage />`).
- **No business logic or state machines**: All data transformation, mutation logic, and interactive state belong in feature components and hooks.

### Route example

```tsx
import { createFileRoute, redirect } from '@tanstack/react-router';
import { DashboardPage } from '@/features/dashboard/pages/dashboard-page';

export const Route = createFileRoute('/_authenticated/dashboard')({
  beforeLoad: ({ context }) => {
    if (!context.auth.isAuthenticated) {
      throw redirect({
        to: '/login',
        search: { redirect: '/dashboard' }
      });
    }
  },
  component: DashboardRouteComponent
});

function DashboardRouteComponent() {
  return <DashboardPage />;
}
```

---

## 5. Server state and caching

### TanStack Query exclusivity
- **TanStack Query exclusively owns all remote server state.**
- **No client state stores for server data**: Do NOT use Zustand, Redux, Pinia, or custom global stores to hold or mirror server data.
- **No local state mirroring**: Do NOT copy query data into local `useState`, `useReducer`, or React context. Read directly from the query hook return value or use `select` to derive transformed state.
- **Cache invalidation over manual mutation**: On successful mutations, invalidate affected query keys (`queryClient.invalidateQueries`) to let TanStack Query refetch fresh data rather than manually modifying remote state in memory.

### Query keys and typing
- Query keys must be structured, deterministic, and hierarchical.
- Use `openapi-react-query` (`$api.useQuery` or `$api.queryOptions`) directly to guarantee query keys align automatically with OpenAPI endpoints and parameters.

### Justification for custom query/mutation hooks
By default, call `$api.useQuery(...)` or use `$api.queryOptions(...)` directly within feature components or pages. 

Custom query or mutation hooks are justified **only** in the following concrete scenarios:
1. **Complex mutation workflows**: Managing multi-step optimistic updates, rollback logic on error, or coordinated multi-query invalidations.
2. **Reusable domain transformations**: Reshaping raw API data for consumption across multiple components in the same feature via a shared `select` transformation.
3. **Chained/dependent operations**: Coordinating multiple sequential or dependent queries where logic is non-trivial.

Do NOT create boilerplate wrapper hooks that merely forward parameters to `$api.useQuery` without adding behavior.

---

## 6. Form management and field adapters

### TanStack Form standard
- Forms must be implemented using TanStack Form (`@tanstack/react-form`).
- Form state, validation lifecycle, touched/dirty tracking, and submission handling are managed by TanStack Form.

### Mantine field adapters
Integrate TanStack Form with Mantine input components using clean, type-safe adapter patterns:
- Map Mantine's `value` to `field.state.value`.
- Map Mantine's `onChange` to `(val) => field.handleChange(val)`.
- Map Mantine's `onBlur` to `field.handleBlur`.
- Map Mantine's `error` prop to `field.state.meta.isTouched && field.state.meta.errors.length > 0 ? field.state.meta.errors[0] : undefined`.

```tsx
<form.Field
  name="email"
  validators={{
    onChange: ({ value }) => (!value ? 'Email is required' : undefined)
  }}
>
  {(field) => (
    <TextInput
      label="Email"
      value={field.state.value}
      onChange={(e) => field.handleChange(e.target.value)}
      onBlur={field.handleBlur}
      error={field.state.meta.isTouched ? field.state.meta.errors[0] : undefined}
    />
  )}
</form.Field>
```

### Server error integration
When form submission fails with an RFC 7807 `ProblemDetail` containing field-level errors (`fieldErrors`), map those errors directly into the form's field error state so users receive immediate inline feedback on the offending inputs.

---

## 7. API contracts and error normalization

### Authoritative OpenAPI contracts
- The Spring Boot backend OpenAPI specification is the authoritative source of truth.
- The contract is compiled into `src/api/schema.d.ts` using `npm run generate:openapi`.
- Never manually hand-write or override TypeScript interfaces for server requests or responses. Always import from `src/api/schema.d.ts` or leverage inferred types through `openapi-fetch` and `$api`.

### RFC 7807 ProblemDetail normalization
All backend error responses adhere to RFC 7807 Problem Details. The frontend normalizes every API error into a structured `ApiError` via `normalizeError()` in `src/api/client.ts`:

```typescript
export interface ApiFieldError {
  field: string;
  key?: string;
  detail: string;
}

export interface ApiError {
  status: number;
  message: string;
  code?: string;
  key?: string;
  traceId?: string;
  fieldErrors?: ApiFieldError[];
}
```

- Always handle errors using normalized `ApiError` instances.
- For field-level errors (e.g. 400 Validation Failure), extract and render `fieldErrors`.
- For general API errors, display `message` or dispatch notifications via Mantine notifications system.

---

## 8. Styling architecture and design tokens

Styling is built on three complementary layers:

### 1. Mantine theme (`src/app/theme.ts`)
- Centralizes global design tokens: custom color palettes (e.g. `brand`), font stacks (`fontFamily`, `fontFamilyMonospace`), heading scales, default border radiuses, and component `defaultProps`.
- All standard Mantine components (`Button`, `TextInput`, `Card`, `Paper`) must inherit default props from the theme rather than repeating props across call sites.

### 2. Semantic CSS variables (`src/index.css`)
- Colors and elevations must use semantic CSS custom properties rather than hardcoded hex values.
- Semantic tokens automatically adapt between light and dark themes via `[data-mantine-color-scheme='dark']`:
  - `--color-bg-base`, `--color-bg-surface`, `--color-bg-elevated`
  - `--color-border-subtle`, `--color-border-default`
  - `--color-text-primary`, `--color-text-secondary`, `--color-text-muted`
  - `--color-brand-50` through `--color-brand-900`
  - `--shadow-sm`, `--shadow-md`, `--shadow-lg`

### 3. CSS Modules (`*.module.css`)
- Component-specific layout, grid systems, and custom visual styling must be implemented in localized CSS Modules.
- Keeps styling co-located with feature components while preventing global style bleeding.

### 4. Iconography (`@phosphor-icons/react`)
- Always use the `Icon` suffix when importing and referencing Phosphor icons (e.g. `SunIcon`, `MoonIcon`, `HouseIcon`, `ChartLineUpIcon`, `SignOutIcon`).
- The non-suffixed exports (e.g. `Sun`, `Moon`, `House`) are deprecated and must not be used.

In CSS Modules, reference Mantine style mixins, functions, and semantic CSS variables:
  ```css
  .container {
    background-color: var(--color-bg-surface);
    border: 1px solid var(--color-border-subtle);
    border-radius: var(--mantine-radius-md);
    padding: var(--mantine-spacing-md);
  }
  ```
- Avoid arbitrary inline `style={{ ... }}` objects in JSX.

---

## 9. Authentication and session lifecycle

The authentication architecture balances security, persistence, and seamless user experience:

### Token storage strategy
- **Access Token**: Stored in memory (`memoryAccessToken`) with a `sessionStorage` fallback (`stocks_access_token`).
  - Survives browser page reload within the same browser tab.
  - Never stored in persistent `localStorage` to mitigate long-term token exposure.
- **Refresh Token**: Stored exclusively in an `HttpOnly`, `SameSite=Lax`, `Secure` cookie (`stocks_refresh_token`) managed entirely by the backend. The frontend JavaScript code never has direct access to the refresh token cookie.

### Silent session refresh on reload
1. When the application initializes in `src/app/`, it attempts a silent session refresh against `POST /api/v1/auth/refresh`.
2. The browser automatically includes the `HttpOnly` refresh cookie.
3. If successful, the new access token is stored in memory and `sessionStorage`, re-authenticating the user seamlessly without redirecting to `/login`.
4. If the refresh request fails (cookie expired or missing), auth state transitions to unauthenticated and the user is redirected to `/login` when accessing protected routes.

### Bearer authentication middleware
- The `openapi-fetch` client includes an `authMiddleware` that automatically attaches `Authorization: Bearer <access_token>` to outgoing requests.
- Endpoints that do not require authentication (`/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/register`) are explicitly bypassed by the middleware.

### Route protection
- Protected routes (e.g. under `_authenticated/`) enforce authentication in their `beforeLoad` route guard.
- When unauthenticated, the guard throws a TanStack Router `redirect` to `/login`, preserving the intended target URL via search parameters (`?redirect=...`).

---

## 10. Toolchain, linting, and verification commands

### Toolchain baseline
- **TypeScript 5.9+**: Strict type checking with `noImplicitAny`, `strictNullChecks`, and modern module resolution.
  - **Rule**: Do not explicitly specify return types where TypeScript naturally and accurately infers them (e.g. React components, standard async helpers, hook consumers). Keep code concise and let the compiler infer types naturally.
- **Biome 2.5+**: Fast, unified linter, formatter, and import organizer.
- **Vite 8+**: Modern build bundler with React Compiler (`@vitejs/plugin-react` + `oxc-transform-react`).
- **React Compiler**: Automatically optimizes components and hooks at build time.
  - **Rule**: Do **NOT** use `useCallback`, `useMemo`, or `React.memo`.
  - The React Compiler automatically handles fine-grained memoization of functions, objects, and component renders. Write clean, idiomatic standard TypeScript/React code without manual memoization boilerplate.
- **Husky 9+**: Git pre-commit hooks ensuring code hygiene before every commit.

### Canonical verification commands
All frontend commands must be executed from the `web/` directory:

| Command | Action | Behavior |
| :--- | :--- | :--- |
| `npm run typecheck` | `tsc -b` | Strict TypeScript compilation check across all project configs. |
| `npm run check` | `biome check ./src` | Read-only verification of linting, formatting, and import order. |
| `npm run check:fix` | `biome check --write ./src` | Mutating auto-fix for formatting and safe lint corrections. |
| `npm run build` | `tsc -b && vite build` | Complete production build and bundle compilation into `dist/`. |

### Pre-commit hook protocol
The Git pre-commit hook runs automated checks prior to commit:
1. Verifies backend Spotless formatting.
2. Runs frontend `npm run typecheck` (`tsc -b`).
3. Runs `npx biome check ./src` as a dry-run. If issues exist, it executes `npm run check:fix` to auto-format files, stages the fixes, and prompts review before completion.
