# Frontend UI Design Guidelines & Standards

Status: **Authoritative** UI/UX engineering reference for the `web/` application.

This document formalizes all UI/UX design principles, styling conventions, component patterns, and quality checklists established for the project. Every new UI component, modal, or page must adhere to these guidelines.

---

## 1. Core Philosophy: Mobile-First Ergonomics

We build **mobile-first**. Every screen must be fully functional, aesthetically polished, and effortless to use on a mobile viewport (320px–480px) before scaling up to tablet and desktop.

### 1.1. Accessible Touch Targets (>= 44px)
- **Strict Requirement**: Every interactive element must provide a touch target of at least **44 × 44 pixels** (WCAG 2.5.5 / 2.5.8).
- **Buttons**: All `<Button>` components must have `min-height: 44px` (or `48px` for primary quick actions).
- **Action Icons**: `<ActionIcon>` components must have `min-width: 44px; min-height: 44px`.
- **Inputs & Selects**: Text inputs, selects, and textareas must have `min-height: 44px` for their input box.
- **Pills, Chips & Presets**: Date presets (e.g. "Now", "Today", "Start of Month"), filter chips, and tag buttons must have `min-height: 44px;`.
  - **Prohibition**: Never use `size="compact-xs"` or override CSS with `height: 28px;` or `min-height: 28px` on interactive elements.
- **Table Rows / Ticker Rows**: Clickable list items or cards must have `min-height: 48px;` or `52px;`.

### 1.2. Responsive Grid Progression
Design with fluid, progressive CSS grids:
- **Mobile (< 36em / 576px)**: Single column layout (`grid-template-columns: 1fr`). Full width.
- **Tablet (>= 48em / 768px)**: 2-column grids, horizontal headers with actions aligned to the right.
- **Desktop (>= 64em / 1024px)**: 3-column grids, side-by-side metric tiles and panels.

### 1.3. Mobile Action Buttons & Presets Layout
- **No Awkward Button Wrapping**: On mobile (< 36em / 576px), action button toolbars must stack full-width (`flex-direction: column; width: 100%`) or use a balanced responsive grid (`repeat(3, 1fr)`). Never allow 3 buttons to wrap into an uneven 2+1 layout.
- **Structured Preset Grids**: Toolbar presets (such as date pills) on mobile must use a clean 2-column grid (`repeat(2, 1fr)`) with any reset action spanning full width (`grid-column: 1 / -1`). Never use free-floating inline wraps that scatter buttons across uneven vertical rows.
- **Modal Action Stacking**: On small screens, modal actions must stack vertically (`flex-direction: column-reverse; width: 100%`), keeping the primary confirm action full-width on top.


---

## 2. Design Tokens & Elevation Hierarchy

We use Mantine's semantic CSS variables as our primary design system token layer.

### 2.1. Card Elevation Consistency
- **Resting Cards**: Always use `box-shadow: var(--mantine-shadow-sm)`.
  - Applies to: account cards, portfolio cards, stat cards, filter cards, information cards, section panels.
  - Border: `border: 1px solid var(--app-border-subtle)`.
  - Radius: `border-radius: var(--mantine-radius-md)` (or `var(--mantine-radius-lg)` for hero cards).
  - Background: `var(--mantine-color-default)` or `var(--app-surface-elevated)`.
- **Hover / Interactive Cards**:
  - `box-shadow: var(--mantine-shadow-md)`.
  - `border-color: var(--app-border-strong)`.
  - `transform: translateY(-1px)`.
  - Transition: `box-shadow var(--app-motion-fast) var(--app-ease), transform var(--app-motion-fast) var(--app-ease)`.
- **Floating Surfaces (Modals, Drawers, Menus)**:
  - Modals & Drawers: `box-shadow: var(--mantine-shadow-xl)`.
  - Dropdown Menus: `box-shadow: var(--mantine-shadow-lg)`.
- **Action Buttons**:
  - **Prohibition**: Do **NOT** add resting box-shadows to buttons. Buttons should have flat elevation to maintain contrast against elevated cards.
- **Avoid Arbitrary Shadows**:
  - Never write custom multi-layer raw `rgba(...)` shadows in component CSS modules.
  - Mantine's shadow tokens automatically handle color-scheme adaptation in dark mode.

### 2.2. Surface & Border Vocabulary
| Token | Semantic Purpose |
| :--- | :--- |
| `var(--mantine-color-body)` | Root canvas background |
| `var(--mantine-color-default)` | Primary card surface (white in light, dark navy in dark) |
| `var(--app-surface-secondary)` | Subtle inset surface (stat backgrounds, metadata strips, toggle containers) |
| `var(--app-surface-elevated)` | Floating panels, hero cards, drawers |
| `var(--app-surface-selected)` | Highlighted/selected row background |
| `var(--app-border-subtle)` | Subtle structural divider / resting card border |
| `var(--app-border-strong)` | Focus, hover, or active card border |
| `var(--app-focus-ring)` | Accessible keyboard focus outline halo |

### 2.3. Dropdown Options Single-Line Truncation
- Dropdown options (`Combobox.Option`, `Select.Option`, `Menu.Item`) must never wrap to multiple lines.
- Always apply:
  ```css
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  ```
  with `flex: 1; min-width: 0;` on inner text labels so long values truncate with a clean ellipsis (`...`) instead of wrapping or overflowing.

---


## 3. Shell, Header & Layout Stability

### 3.1. Prevent Layout Shifts (`scrollbar-gutter: stable`)
- Vertical scrollbars appearing/disappearing as pages navigate must never cause layout shift or header jank.
- `scrollbar-gutter: stable` is enforced on `html` in `theme.css`.

### 3.2. Container Alignment
- The header and page content container must maintain identical horizontal padding at every breakpoint:
  - **Mobile (< 48em)**: `padding-inline: var(--mantine-spacing-md);`
  - **Desktop (>= 48em)**: `padding-inline: var(--mantine-spacing-lg);`
  - **Max Width**: `max-width: 80rem; margin-inline: auto;`
- Header padding is carried by `.headerInner`, ensuring exact vertical alignment with page `.container`.

### 3.3. Sticky Glassmorphism Header
- Header is `position: sticky; top: 0; z-index: 40; height: 60px;`.
- Semi-transparent backdrop with blur:
  ```css
  background-color: color-mix(in srgb, var(--mantine-color-body) 82%, transparent);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  ```
- **Active Navigation State**:
  - Style TanStack Router's `[data-status="active"]` attribute:
  ```css
  .navLinks [data-status="active"] {
    background-color: var(--mantine-primary-color-light) !important;
    color: var(--mantine-primary-color-filled) !important;
    font-weight: var(--mantine-font-weight-bold) !important;
    box-shadow: inset 0 -2px 0 0 var(--mantine-primary-color-filled);
  }
  ```

### 3.4. Background Canvas & Atmosphere (Light vs Dark Mode)
- **Light Mode**: Must remain a clean, flat, uniform canvas using `background-color: var(--mantine-color-body);`. Do **not** apply radial gradients in light mode, as they create an optical illusion of gradient backgrounds on resting cards and panels.
- **Dark Mode**: Enriched with a subtle, fixed ambient radial gradient:
  ```css
  :root[data-mantine-color-scheme="dark"] .shell,
  :root[data-mantine-color-scheme="dark"] .page {
    background-image:
      radial-gradient(circle at 15% 15%, rgb(98 91 246 / 0.08) 0%, transparent 40%),
      radial-gradient(circle at 85% 85%, rgb(98 91 246 / 0.05) 0%, transparent 40%);
    background-attachment: fixed;
  }
  ```


---

## 4. Data Display, Typography & Progressive Disclosure

### 4.1. Financial Numerics (`tabular-nums`)
- Always apply `font-variant-numeric: tabular-nums` (or class `.app-numeric`) to financial values, prices, balance amounts, timestamps, and percentages. This prevents jitter and visual instability as numbers change.

### 4.2. Empty, Error, and Loading State Invariants
- **Never display misleading `$0.00` on error or unloaded state**:
  - If a balance or metric query is loading: render `<Skeleton height={...} />`.
  - If a balance query fails or is not established: render `<Text c="dimmed">Unavailable</Text>` or `"Not Established"`. Never default an errored ledger query to zero.
  - If an account has no coverage: provide an explicit alert explaining coverage requirements.

### 4.3. Progressive Disclosure (Active-First UX)
- **Active Data First**: Default views must show **active** records only (e.g. Active Sessions, Active Accounts).
- **Archived / Historical Data**: Keep behind a progressive disclosure toggle (Mantine `Switch` or filter dropdown). Users should not have their primary workflow cluttered with inactive data.
- **List Capping & Pagination**: For operational lists (e.g. sessions), cap initial rendering at 5–10 items and provide a clear "Load More" button.
- **Current Entity Distinction**: Always distinguish the "Current / This Device" item with visual cues:
  - Pulsating green dot indicator (`pulseDot` animation).
  - Subtle brand accent background (`color-mix` with brand light).
  - Explicit badge (e.g. `"This Device"`, `"Current Session"`).

### 4.4. Approachable, Plain-English Copy
- Avoid dense internal accounting or ledger jargon (e.g. "Verified Ledger Anchor", "liability instrument", "sub-millisecond execution engine").
- Write plain, conversational, and direct English that everyday users understand immediately (e.g. "Verified Opening State", "Debt / Loan Account", "Starting Balance & Start Date") while preserving technical correctness.

---

## 5. Forms, Mutations & Concurrency UX

### 5.1. Zero Frontend Tests
- **Strict Rule**: We do **not** author frontend test files (`*.test.ts`, `*.test.tsx`). Verification is performed via TypeScript compilation (`npm run typecheck`), Biome linting (`npx biome check ./src`), and Vite production builds (`npm run build`).

### 5.2. State Discipline: No Ad-Hoc `useState` for Server Operations
- **Strict Rule**: Always use `$api.useMutation` and `$api.useQuery` syntax directly.
- Do **NOT** manage query/mutation loading states or error states with ad-hoc manual `useState` booleans. Let TanStack Query manage `isLoading`, `isPending`, `isError`, and `error`.

### 5.3. Notifications
- **Strict Rule**: Call `notifications.show(...)` directly.
- Do not pass a `notify` function or wrap `notifications.show` in intermediate abstraction parameters.

### 5.4. TanStack Form Standards
- Forms use TanStack Form (`useForm`).
- **Dynamic Keying**: Modals containing forms must incorporate relevant entity versions or query data into their React `key` (e.g. `key={`edit-${account.id}-${account.version}-${balance}`}`) to guarantee form fields re-initialize when fresh server state arrives.
- **Strict Validation**: Always validate currency and amount strings with strict plain decimal regex (`/^-?(?:0|[1-9]\d*)(?:\.\d+)?$/`) matching backend `FinancialAmount` domain invariants. Do not rely on permissive JavaScript `parseFloat()`.

### 5.5. Optimistic Concurrency & 409 Conflict Handling
When updating entities protected by versioning (accounts, balances, policies, opening states):
1. Transmit entity `version` in payload or headers.
2. Catch HTTP 409 (`ACCOUNT_VERSION_CONFLICT`, `BALANCE_VERSION_CONFLICT`, `OPENING_STATE_CONFLICT`).
3. Display an inline conflict alert explaining that another session modified the record.
4. Provide a **"Reload Latest Server State"** button directly inside the dialog to refetch the freshest server revision and update form defaults without losing user context.

### 5.6. Input Layout Stability (`inputWrapperOrder`)
- Inputs must never shift vertical placement when dynamic descriptions appear or change.
- Enforce `inputWrapperOrder: ['label', 'input', 'description', 'error']` across all inputs (`TextInput`, `Select`, `NumberInput`, `PasswordInput`, `Textarea`, `MultiSelect`).
- Input boxes are anchored directly beneath labels, with descriptions rendering underneath the input as stable helper text.

---

## 6. Developer Checklist for New UIs

Before considering any new frontend feature or component complete, verify every item:

- [ ] **Touch Targets**: Are all buttons, inputs, presets, and clickable rows `>= 44px` in height?
- [ ] **Mobile-First Layout**: Does the view look natural on a 360px mobile screen without horizontal overflow or awkward 2+1 button wrapping?
- [ ] **Modal Actions Stacking**: Do modal actions stack vertically (`column-reverse`) on mobile with 44px targets?
- [ ] **Preset Grids**: Are toolbar/filter presets organized in structured grids (e.g. 2x2) with full-width reset buttons?
- [ ] **Card Shadows**: Do cards use `box-shadow: var(--mantine-shadow-sm)` and hover cards use `var(--mantine-shadow-md)`? Are arbitrary `rgba()` shadows avoided?
- [ ] **Light Mode Canvas**: Is the background a solid, flat canvas (`var(--mantine-color-body)`) without radial gradient bleed in light mode?
- [ ] **Dropdown Truncation**: Do dropdown items enforce single-line text with ellipsis (`min-width: 0; white-space: nowrap; text-overflow: ellipsis`)?
- [ ] **Input Stability**: Are inputs anchored directly below labels with descriptions underneath (`inputWrapperOrder: ['label', 'input', 'description', 'error']`)?
- [ ] **Plain English**: Is copy simple, approachable, and free of dense accounting jargon?
- [ ] **Tabular Numerics**: Do all currency numbers, prices, and percentages use `tabular-nums`?
- [ ] **Error Grace**: Does the UI display `"Unavailable"` rather than `"$0.00"` if a query fails?
- [ ] **Active-First**: Does the view show active records by default, hiding archived/revoked items behind progressive disclosure?
- [ ] **Notifications**: Are notifications called directly via `notifications.show(...)`?
- [ ] **Server State**: Is server state managed exclusively by `$api.useQuery` and `$api.useMutation` without ad-hoc `useState`?
- [ ] **Optimistic Locking**: If mutating versioned entities, is HTTP 409 conflict handling with a reload button provided?
- [ ] **Verification**: Do `npm run typecheck`, `npx biome check ./src`, and `npm run build` pass cleanly with 0 errors?
- [ ] **No Tests**: Are there zero `.test.ts` or `.test.tsx` files authored?

