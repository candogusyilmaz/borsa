# CANVERSE UI Skill Reference

> **For LLM agents and developers generating UI for the CANVERSE platform.**
> Stack: Vite + React 19 + Mantine v9 alpha · TypeScript · Biome · TanStack Router/Query

---

## Table of Contents

1. [Absolute Rules](#1-absolute-rules)
2. [Stack Overview](#2-stack-overview)
3. [Project Structure](#3-project-structure)
4. [Import Order](#4-import-order)
5. [Design Tokens](#5-design-tokens)
6. [CSS Variables Reference](#6-css-variables-reference)
7. [Mantine v9 Theme Configuration](#7-mantine-v9-theme-configuration)
8. [Component Patterns](#8-component-patterns)
9. [Typography](#9-typography)
10. [Color System](#10-color-system)
11. [Spacing & Sizing](#11-spacing--sizing)
12. [Layout & Responsive Design](#12-layout--responsive-design)
13. [Mobile Conventions](#13-mobile-conventions)
14. [Animation Guidelines](#14-animation-guidelines)
15. [Financial Data Formatting](#15-financial-data-formatting)
16. [Forms with Mantine](#16-forms-with-mantine)
17. [Data Fetching Patterns](#17-data-fetching-patterns)
18. [Do's and Don'ts](#18-dos-and-donts)
19. [Quick Reference Tables](#19-quick-reference-tables)

---

## 1. Absolute Rules

These rules are non-negotiable in every file you generate:

1. **Always use Mantine v9 API.** Do not use v7/v8 prop names or removed APIs.
2. **Never hardcode colors.** Use CSS variables from `global.css` or Mantine color tokens (`var(--mantine-color-*)`).
3. **Never use `px` for spacing in component props.** Use Mantine spacing tokens (`"xs"`, `"sm"`, `"md"`, `"lg"`, `"xl"`) or `rem()` for pixel values.
4. **Always use `rem()` from `@mantine/core` when a pixel measurement is needed** (e.g., `fz={rem(22)}`).
5. **Never override `--mantine-*` private/internal variables** — only override the stable public variables listed in [CSS Variables Reference](#6-css-variables-reference).
6. **Theme overrides live in `main.tsx`** inside the `<MantineProvider theme={...}>` prop.
7. **`cssVariablesResolver` belongs inside the `theme` object** passed to `<MantineProvider>`, not as a separate prop (Mantine v9).
8. **Component-level style customization uses `classNames`, `styles`, or `defaultProps`** defined in `theme.components`.
9. **Color scheme detection is via `data-mantine-color-scheme` attribute on `<html>`**, not `[data-theme]` or similar.
10. **Use `light-dark()` CSS function in CSS Modules** for theme-aware values.
11. **All financial numbers must be formatted** with `format.*` helpers from `~/lib/format.ts`.
12. **Never render raw API numbers as strings** — always format with the appropriate `format.*` method.
13. **The `<Currency>` component wraps `<Text>` and must be used for all monetary display.**
14. **Mobile-first responsive design:** default styles apply to mobile; overrides target larger breakpoints.
15. **Use `useMatches` from `@mantine/core` for responsive JS values** (sizes, layout mode).

---

## 2. Stack Overview

| Layer | Technology |
|---|---|
| Build tool | Vite 7 |
| UI framework | React 19 with React Compiler (`babel-plugin-react-compiler`) |
| Component library | Mantine v9 alpha (`^9.0.0-alpha.3`) |
| Routing | TanStack Router v1 (file-based, auto code-splitting) |
| Data fetching | TanStack Query v5 + `openapi-react-query` |
| HTTP client | `openapi-fetch` / Axios |
| State management | Zustand v5 |
| Charts | `@mantine/charts` + Recharts |
| Icons | `@tabler/icons-react` (tree-shaken via alias) |
| Forms | `@mantine/form` |
| Dates | `@mantine/dates` + dayjs |
| Linter/Formatter | Biome v2 |
| CSS preprocessor | PostCSS + `postcss-preset-mantine` + `postcss-simple-vars` |
| Animations | Framer Motion v12 |
| TypeScript | v5.9 strict |

---

## 3. Project Structure

```
src/main/web/
├── src/
│   ├── api/                  # OpenAPI client, queries, mutations
│   │   ├── index.ts          # Query key factory
│   │   ├── openapi.ts        # $api client (openapi-react-query)
│   │   ├── schema.d.ts       # Generated OpenAPI types
│   │   ├── queries/          # TanStack Query query factories
│   │   └── mutations/        # TanStack Query mutation factories
│   ├── components/           # Shared UI components
│   │   ├── Currency.tsx      # <Currency> monetary display component
│   │   ├── CanverseText.tsx  # Brand text component
│   │   ├── Dashboard/        # Dashboard-specific components
│   │   ├── Portfolio/        # Portfolio-specific components
│   │   └── Transaction/      # Trade/transaction form components
│   ├── hooks/                # Custom React hooks
│   ├── lib/
│   │   ├── format.ts         # Number/date formatting utilities
│   │   ├── currency.ts       # Currency symbol helper
│   │   ├── constants.ts      # App-level constants (locale, etc.)
│   │   ├── common.ts         # Misc shared utilities
│   │   ├── alert.tsx         # Mantine notification helpers
│   │   ├── axios.ts          # Axios instance / provider
│   │   └── AuthenticationContext.tsx
│   ├── routes/               # TanStack Router file-based routes
│   │   ├── __root.tsx
│   │   ├── _authenticated/   # Auth-guarded routes
│   │   └── ...
│   ├── styles/
│   │   ├── global.css        # Global CSS variables & layout classes
│   │   └── common.module.css # CSS Module for shared component styles
│   └── main.tsx              # App entry: MantineProvider, theme, providers
├── biome.json                # Linter/formatter config
├── postcss.config.cjs        # PostCSS + Mantine breakpoints
├── vite.config.ts            # Vite config with path alias ~/
└── package.json
```

Key path aliases:
- `~/` → `src/` (configured in `vite.config.ts`)

---

## 4. Import Order

Biome (`organizeImports: "on"`) enforces import ordering automatically. The canonical order is:

```tsx
// 1. Mantine core/component imports
import { Button, Card, Group, Stack, Text, rem } from '@mantine/core';
import { useForm } from '@mantine/form';

// 2. Third-party library imports (alphabetical)
import { IconSearch } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useParams } from '@tanstack/react-router';

// 3. React imports (only when needed — React Compiler handles most cases)
import { useState, useMemo } from 'react';

// 4. Internal alias imports (~/)
import { $api } from '~/api/openapi';
import type { paths } from '~/api/schema';
import { Currency } from '~/components/Currency';
import { format } from '~/lib/format';

// 5. Mantine CSS (in main.tsx only)
import '@mantine/core/styles.css';

// 6. Local CSS / CSS Modules
import styles from './MyComponent.module.css';
```

**Rules:**
- Type-only imports use `import type { ... }`.
- No default React import unless a JSX transform is absent.
- Single quotes for strings (Biome enforces this).
- No trailing commas (Biome enforces this).

---

## 5. Design Tokens

All custom design tokens are CSS custom properties defined in `src/styles/global.css`.

### Theme-Aware Tokens (switch with `data-mantine-color-scheme`)

| Token | Light | Dark | Usage |
|---|---|---|---|
| `--bg-gradient` | Radial blue-white gradient | Dark radial slate/black gradient | Page background |
| `--sidebar-bg` | `rgba(255,255,255,0.6)` | `rgba(0,0,0,0.4)` | Sidebar background |
| `--text-main` | `#0f172a` | `#f1f5f9` | Primary text |
| `--text-muted` | `#64748b` | `#94a3b8` | Secondary/muted text |
| `--card-bg` | `rgba(255,255,255,0.4)` | `rgba(255,255,255,0.05)` | Card/modal backgrounds |
| `--border-color` | `#e2e8f0` | `rgba(255,255,255,0.1)` | All borders |
| `--lighter-border-color` | `#e7eeef` | `rgba(255,255,255,0.05)` | Subtle dividers |
| `--card-shadow` | `0 1px 2px 0 rgba(0,0,0,0.05)` | `none` | Card elevation |
| `--table-row-hover` | `#f1f5f9` | `rgba(255,255,255,0.02)` | Table row hover |
| `--modal-border` | `rgba(255,255,255,0.7)` | `rgba(255,255,255,0.1)` | Modal border |
| `--modal-shadow` | Subtle multi-shadow | `0 25px 50px rgba(0,0,0,0.5)` | Modal shadow |

### Static Tokens (always the same)

| Token | Value | Usage |
|---|---|---|
| `--accent-color` | `#6366f1` | Accent/focus rings, highlights |
| `--btn-primary` | `#4f46e5` | Primary button background |
| `--btn-hover` | `#6366f1` | Primary button hover background |
| `--btn-shadow` | Indigo box-shadow | Primary button elevation |

### Mantine-Bridged Tokens

These are standard Mantine tokens that are re-assigned in `global.css` to align with CANVERSE's palette:

```css
--mantine-color-dimmed: var(--text-muted);
--text-color: var(--text-main);
```

---

## 6. CSS Variables Reference

### Stable Public Mantine CSS Variables

These are safe to use in component styles:

```css
/* Colors */
var(--mantine-color-{name}-{shade})   /* e.g. var(--mantine-color-teal-5) */
var(--mantine-color-white)
var(--mantine-color-black)
var(--mantine-color-dark-{0-9})
var(--mantine-color-gray-{0-9})
var(--mantine-color-dimmed)
var(--mantine-color-error)
var(--mantine-color-placeholder)

/* Spacing */
var(--mantine-spacing-xs)   /* 10px */
var(--mantine-spacing-sm)   /* 12px */
var(--mantine-spacing-md)   /* 16px */
var(--mantine-spacing-lg)   /* 20px */
var(--mantine-spacing-xl)   /* 32px */

/* Font sizes */
var(--mantine-font-size-xs)   /* 12px */
var(--mantine-font-size-sm)   /* 14px */
var(--mantine-font-size-md)   /* 16px */
var(--mantine-font-size-lg)   /* 18px */
var(--mantine-font-size-xl)   /* 20px */

/* Font families */
var(--mantine-font-family)
var(--mantine-font-family-monospace)   /* courier new, monospace */

/* Border radius */
var(--mantine-radius-xs)
var(--mantine-radius-sm)
var(--mantine-radius-md)
var(--mantine-radius-lg)
var(--mantine-radius-xl)

/* Line heights */
var(--mantine-line-height-xs)
var(--mantine-line-height-sm)
var(--mantine-line-height-md)
var(--mantine-line-height-lg)
var(--mantine-line-height-xl)

/* Shadows */
var(--mantine-shadow-xs)
var(--mantine-shadow-sm)
var(--mantine-shadow-md)
var(--mantine-shadow-lg)
var(--mantine-shadow-xl)

/* Breakpoints */
var(--mantine-breakpoint-xs)   /* 36em */
var(--mantine-breakpoint-sm)   /* 48em */
var(--mantine-breakpoint-md)   /* 62em */
var(--mantine-breakpoint-lg)   /* 75em */
var(--mantine-breakpoint-xl)   /* 88em */

/* Z-index */
var(--mantine-z-index-app)
var(--mantine-z-index-modal)
var(--mantine-z-index-overlay)
var(--mantine-z-index-popover)
var(--mantine-z-index-dropdown)
var(--mantine-z-index-tooltip)
```

### ⚠️ Private Variables — Do Not Use

Variables prefixed with `--_mantine-*` are internal and may change between alpha releases. Never reference them in custom CSS.

---

## 7. Mantine v9 Theme Configuration

### Theme Structure (`main.tsx`)

```tsx
import { MantineProvider, type DefaultMantineColor, type MantineColorsTuple } from '@mantine/core';

// Extend color names for TypeScript
type ExtendedCustomColors = 'slate' | 'accent' | DefaultMantineColor;
declare module '@mantine/core' {
  export interface MantineThemeColorsOverride {
    colors: Record<ExtendedCustomColors, MantineColorsTuple>;
  }
}

<MantineProvider
  defaultColorScheme="dark"
  theme={{
    defaultRadius: 'md',
    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, ...',
    primaryColor: 'accent',
    primaryShade: 4,
    colors: {
      accent: ['#ebecff', '#d3d4ff', '#a3a5f8', '#6366f1', '#474aed', /* ... */],
      slate: ['oklch(98.4% ...)', /* 10 shades */ ],
      dark: ['#c1cbd5', '#96a3af', /* ... */]
    },
    components: {
      Input: { classNames: { input: 'input-base' } },
      Card: { classNames: { root: 'card' } },
      Divider: { defaultProps: { color: 'var(--border-color)' } },
      Modal: {
        defaultProps: { radius: 'xl' },
        classNames: { content: 'modal-content' },
        styles: { header: { background: 'transparent' } }
      }
    }
  }}>
  {/* ... */}
</MantineProvider>
```

### `cssVariablesResolver` (Mantine v9)

In Mantine v9, `cssVariablesResolver` is a **property on the `theme` object**, not a separate prop on `<MantineProvider>`:

```tsx
theme={{
  // ... other theme properties
  cssVariablesResolver: (theme) => ({
    variables: {
      '--my-custom-var': theme.colors.accent[4]
    },
    light: {
      '--my-custom-var': theme.colors.accent[6]
    },
    dark: {
      '--my-custom-var': theme.colors.accent[3]
    }
  })
}}
```

> **Do not** pass `cssVariablesResolver` as a direct prop to `<MantineProvider>` — this is the Mantine v8 API.

### Color Shades

Mantine colors are 10-element tuples (indices 0–9). The convention is:
- Index 0: lightest tint
- Index 9: darkest shade
- `primaryShade: 4` means the 5th element (0-indexed) is the "default" primary color

```tsx
// Correct: MantineColorsTuple requires exactly 10 strings
const myColor: MantineColorsTuple = [
  '#f0f0ff', // 0 - lightest
  '#e0e0ff', // 1
  '#c0c0ff', // 2
  '#9090ff', // 3
  '#6366f1', // 4 - primary (primaryShade: 4)
  '#4f46e5', // 5
  '#3730d0', // 6
  '#2820bb', // 7
  '#1a10a0', // 8
  '#100080'  // 9 - darkest
];
```

---

## 8. Component Patterns

### Card

Cards automatically get `card` CSS class (glassmorphism effect) via `theme.components.Card.classNames`.

```tsx
import { Card } from '@mantine/core';

// Standard card — no extra className needed
<Card withBorder>
  {/* content */}
</Card>

// Card with error state
<Card withBorder style={{ borderColor: 'var(--mantine-color-red-5)' }}>
  <ErrorView />
</Card>

// Card with explicit size
<Card shadow="sm" radius="md" p="lg" withBorder>
  {/* content */}
</Card>
```

### Text

```tsx
import { Text, rem } from '@mantine/core';

// Standard usage
<Text size="sm" fw={500} c="dimmed">label</Text>

// Large heading-style text (use rem for non-standard sizes)
<Text fw={700} size={rem(22)}>Section Title</Text>

// Inline colored span
<Text span c="teal" fw={600}>profit</Text>

// Muted text
<Text c="dimmed" size="xs" fw={600}>Secondary info</Text>
```

### Group & Stack

```tsx
import { Group, Stack } from '@mantine/core';

// Horizontal layout with space-between
<Group justify="space-between" align="center">
  <Text>Left</Text>
  <Text>Right</Text>
</Group>

// Vertical layout with small gap
<Stack gap="sm">
  <Text>Item 1</Text>
  <Text>Item 2</Text>
</Stack>

// Gap values: "xs" | "sm" | "md" | "lg" | "xl" | number (rem)
```

### Badge

```tsx
import { Badge } from '@mantine/core';

// Semantic coloring
<Badge variant="light" color="blue">BUY</Badge>
<Badge variant="light" color="green">SELL (profit)</Badge>
<Badge variant="light" color="red">SELL (loss)</Badge>
<Badge size="xs" variant="light" color="blue.2">{count} trades</Badge>
```

### ActionIcon

```tsx
import { ActionIcon } from '@mantine/core';

// With tooltip — always provide aria-label
<ActionIcon
  variant="subtle"
  color="gray.6"
  size="sm"
  aria-label="Description of action"
  onClick={handler}>
  <IconName style={{ width: '85%' }} />
</ActionIcon>
```

### ThemeIcon

```tsx
import { ThemeIcon } from '@mantine/core';

// Transparent (icon only, semantic color)
<ThemeIcon variant="transparent" c="teal">
  <IconTrendingUp3 />
</ThemeIcon>

// Small icon for inline use
<ThemeIcon size="xs" variant="transparent" c="dimmed">
  <IconCalendar style={{ width: '100%', height: '100%' }} />
</ThemeIcon>
```

### Modal

Modals automatically get `modal-content` CSS class and `radius="xl"` via theme defaults.

```tsx
import { Modal } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';

const [opened, { open, close }] = useDisclosure(false);

<Modal opened={opened} onClose={close} title="Modal Title">
  {/* content */}
</Modal>
```

### Input Components

All inputs get `input-base` CSS class via `theme.components.Input.classNames`.

```tsx
import { TextInput, NumberInput, Select, NativeSelect } from '@mantine/core';

// Standard input with label styling
<TextInput
  label="FIELD NAME"
  placeholder="Enter value..."
  styles={{
    label: {
      fontSize: 12,
      fontWeight: 600,
      color: 'var(--text-muted)',
      marginBottom: 4,
      marginLeft: 2
    },
    input: {
      height: 50,
      fontSize: 16,
      fontWeight: 300
    }
  }}
  {...form.getInputProps('fieldName')}
/>
```

### SimpleGrid (Responsive Columns)

```tsx
import { SimpleGrid } from '@mantine/core';

// 1 column on xs, 2 columns on sm+
<SimpleGrid cols={{ xs: 1, sm: 2 }}>
  {/* items */}
</SimpleGrid>
```

### SegmentedControl

```tsx
import { SegmentedControl } from '@mantine/core';

<SegmentedControl
  value={value}
  onChange={setValue}
  data={[
    { value: '', label: 'All' },
    { value: 'BUY', label: 'Buy' },
    { value: 'SELL', label: 'Sell' }
  ]}
/>
```

### Divider

Dividers default to `color: 'var(--border-color)'` via theme.

```tsx
import { Divider } from '@mantine/core';

<Divider />
// or with explicit color:
<Divider color="var(--border-color)" />
```

---

## 9. Typography

### Font Family

Set globally in the theme:

```
Inter, -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif, Apple Color Emoji, Segoe UI Emoji
```

Monospace font is available via `var(--mantine-font-family-monospace)`.

### Font Weight Convention

| Use case | Font weight |
|---|---|
| Section/page title | `fw={700}` |
| Card title | `fw={600}` or `fw={700}` |
| Body text | `fw={400}` (default) |
| Label | `fw={500}` or `fw={600}` |
| Input labels (CANVERSE style) | `fw={600}`, `fontSize: 12` |
| Input values | `fw={300}`, `fontSize: 16` |
| Navigation labels | `fw={700}` |
| Nav section categories | `fw={900}`, uppercase, 10px |
| Logo | `fw={900}` |

### Font Size Convention

```tsx
// Use Mantine size tokens whenever possible
<Text size="xs">12px</Text>
<Text size="sm">14px</Text>
<Text size="md">16px</Text>  {/* default */}
<Text size="lg">18px</Text>
<Text size="xl">20px</Text>

// Use rem() for non-standard sizes
<Text size={rem(22)}>Custom 22px title</Text>
<Text fz={rem(22)}>Same with fz prop</Text>
```

---

## 10. Color System

### CANVERSE Custom Colors

| Color name | Purpose | Notable shades |
|---|---|---|
| `accent` | Primary brand color (indigo) | [4] `#6366f1`, [5] `#4f46e5` |
| `slate` | Neutral gray-blue (oklch) | Full 10-shade oklch range |
| `dark` | Dark mode backgrounds | [7] `#1a1b1d`, [8] `#131415`, [9] `#0d0d0d` |

### Semantic Color Usage

| Semantic | Mantine color | Use case |
|---|---|---|
| Profit/positive | `teal` | Positive financial figures |
| Loss/negative | `red` | Negative financial figures, errors |
| Buy action | `blue` | BUY trade badges/icons |
| Primary/accent | `accent` (custom) | CTAs, primary buttons, highlights |
| Warning | `orange` | Upcoming features, caution states |
| Dimmed/secondary | `dimmed` | Secondary labels, dates, metadata |

### Conditional Color Pattern

```tsx
// Profit/loss coloring — use consistently
<Text c={value >= 0 ? 'teal' : 'red'}>
  {format.toCurrency(value)}
</Text>

// Trade type coloring
<Badge color={trade.type === 'BUY' ? 'blue' : trade.profit >= 0 ? 'green' : 'red'}>
  {trade.type}
</Badge>
```

### Error States

```tsx
// Card with error border
<Card withBorder style={{ borderColor: 'var(--mantine-color-red-5)' }}>
  <ErrorView />
</Card>
```

---

## 11. Spacing & Sizing

### Mantine Spacing Scale

```
xs  → ~10px  (var(--mantine-spacing-xs))
sm  → ~12px  (var(--mantine-spacing-sm))
md  → ~16px  (var(--mantine-spacing-md))
lg  → ~20px  (var(--mantine-spacing-lg))
xl  → ~32px  (var(--mantine-spacing-xl))
```

### Component-Level Spacing Props

```tsx
// Padding
<Card p="lg" px="xl" py="sm">

// Gap between items
<Stack gap="sm">
<Group gap="xs">

// Margin
<Box mt="xl" mb="md">

// Width/Height
<Card miw={275} mih={325}>  // min-width, min-height
```

### `rem()` for Pixel Values

```tsx
import { rem } from '@mantine/core';

// Never write raw px strings in component props
// ✅ Correct
<Text size={rem(22)} />
<Box h={rem(100)} />

// ❌ Wrong
<Text size="22px" />
<Box h="100px" />
```

---

## 12. Layout & Responsive Design

### Root Layout

The app uses a fixed full-viewport layout defined in `global.css`:

```
.layout-wrapper  →  display:flex, row, 100dvh, overflow:hidden
  .sidebar       →  18rem wide, flex column, scrollable, z-index:10
  .main-content  →  flex:1, flex column, scrollable
```

### CSS Modules with Mantine PostCSS

In `.module.css` files, use Mantine's PostCSS helpers:

```css
/* light-dark() for theme-aware values */
.myElement {
  color: light-dark(var(--mantine-color-black), var(--mantine-color-dark-0));
  background-color: light-dark(var(--mantine-color-white), var(--mantine-color-dark-8));
}

/* Responsive with $mantine-breakpoint-* variables */
@media (max-width: $mantine-breakpoint-xs) {
  .myElement {
    display: none;
  }
}

/* Mantine PostCSS hover mixin */
.myLink {
  @mixin hover {
    background-color: light-dark(var(--mantine-color-teal-1), var(--mantine-color-dark-6));
  }
}
```

### Responsive Breakpoints

| Name | Em value | Approx px |
|---|---|---|
| `xs` | 36em | 576px |
| `sm` | 48em | 768px |
| `md` | 62em | 992px |
| `lg` | 75em | 1200px |
| `xl` | 88em | 1408px |

### `useMatches` for Responsive JS Values

```tsx
import { useMatches } from '@mantine/core';

// Returns the value for the current breakpoint
const chartSize = useMatches({ base: 200, sm: 230 });
const TradeCard = useMatches({ base: MobileCardContent, sm: DesktopCardContent });
const justify = useMatches({ base: 'center', xs: 'space-between' });
```

### `SimpleGrid` for Responsive Grids

```tsx
<SimpleGrid cols={{ xs: 1, sm: 2, md: 3 }}>
  {items.map(item => <Card key={item.id}>...</Card>)}
</SimpleGrid>
```

---

## 13. Mobile Conventions

- **Default styles are mobile-first.** Write base styles for small screens, add overrides for larger.
- **Use `useMatches`** instead of `useMediaQuery` for rendering different component variants on mobile vs desktop.
- **Sidebar is hidden on mobile** — the main content must work standalone.
- **The `pro-card` sidebar widget** uses `display: none` on mobile, `display: block` on `lg` (`min-width: 1024px`).
- **Action labels** (nav text) are visible at all breakpoints in the current implementation (`display: block`).
- **Scrollable areas** use `overflow-y: auto` with `scrollbar-width: none` on sidebar, and `ScrollArea` from Mantine for content areas.
- **Touch-friendly tap targets** — minimum 44px hit area for interactive elements on mobile.
- **Compact table layout:** use `MobileCardContent` patterns (Stack/Group with smaller font) for cards on mobile instead of full table rows.
- **`ScrollArea scrollbars="x" type="auto" offsetScrollbars`** for horizontally scrollable chart/grid content.

### Mobile Component Pattern Example

```tsx
// Render different layouts per breakpoint
function TradeRow({ trade }: { trade: Trade }) {
  const CardContent = useMatches({
    base: MobileCardContent,  // xs / sm
    sm: DesktopCardContent    // sm+
  });

  return (
    <Card shadow="sm" radius="md" p="xs" px="lg" withBorder>
      <CardContent trade={trade} />
    </Card>
  );
}
```

---

## 14. Animation Guidelines

The project includes Framer Motion v12 for animations. Use it for:

- **Page transitions** (route-level)
- **List item entrance animations**
- **Modal/drawer open/close polish**

### Simple Framer Motion Patterns

```tsx
import { motion } from 'framer-motion';

// Fade-in card
<motion.div
  initial={{ opacity: 0, y: 8 }}
  animate={{ opacity: 1, y: 0 }}
  transition={{ duration: 0.25, ease: 'easeOut' }}>
  <Card>...</Card>
</motion.div>

// Staggered list
const container = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.05 } }
};
const item = {
  hidden: { opacity: 0, y: 6 },
  visible: { opacity: 1, y: 0 }
};
<motion.ul variants={container} initial="hidden" animate="visible">
  {items.map(i => (
    <motion.li key={i.id} variants={item}>...</motion.li>
  ))}
</motion.ul>
```

### CSS Transitions (for Mantine components)

Use CSS `transition` in `global.css` for hover states:

```css
.card {
  transition: all 0.5s ease;
}

.sidebar-link {
  transition: all 0.3s ease;
}
```

### Animation Rules

- **Never animate layout-affecting properties** (width/height) without `layout` prop in Framer Motion.
- **Keep durations short:** `0.2s–0.3s` for micro-interactions, `0.4s–0.5s` for card/panel transitions.
- **Respect `prefers-reduced-motion`** — wrap animations in a check or use `useReducedMotion` from Framer Motion.
- **Tooltip transitions** (Mantine): use `transitionProps={{ duration: 300 }}`.

---

## 15. Financial Data Formatting

All formatting lives in `~/lib/format.ts`. **Always use these helpers — never `toString()` or template literals for financial values.**

### API Reference

```ts
import { format } from '~/lib/format';

// Currency (TRY, compact notation by default)
format.toCurrency(1234567.89)
// → "₺1,23 Mn" (compact)

format.toCurrency(1234567.89, false)
// → "₺1.234.567,89" (standard)

format.toCurrency(99.5, true, 'USD', 'en-US')
// → "$99.50"

// Percentage (value is already in percent, e.g. 12.34 → "12,34%")
format.toLocalePercentage(12.34)
// → "%12,34" (tr-TR locale)

format.toLocalePercentage(-5.5, 2, 'en-US')
// → "-5.50%"

// Humanized number (compact display)
format.toHumanizedNumber(1500000)
// → "1,5 Mn"

// Date formats
format.toShortDate(new Date())
// → "05 Mar 2026"

format.toShortDateTime(new Date())
// → "05.03.2026 14:43"

format.toFullDateTime(new Date())
// → "05 Mart 2026 14:43"
```

### Locale Defaults

| Method | Default locale | Default currency |
|---|---|---|
| `toCurrency` | `tr-TR` | `TRY` |
| `toLocalePercentage` | `tr-TR` | — |
| `toHumanizedNumber` | `tr-TR` | — |
| `toShortDate` | `tr-TR` | — |
| `toShortDateTime` | `tr-TR` | — |
| `toFullDateTime` | `tr-TR` | — |

> Use `constants.locale()` from `~/lib/constants.ts` when a locale is needed dynamically (e.g., for month names).

### `<Currency>` Component

Use the `<Currency>` component (wraps Mantine `<Text>`) for any monetary value rendered in JSX:

```tsx
import Currency from '~/components/Currency';

// Basic usage
<Currency>{stock.value}</Currency>

// With Text props
<Currency fw={500} fz={rem(22)}>{totalPortfolioValue}</Currency>

// Inline within text
<Text size="xs" c="dimmed">
  @ <Currency span>{trade.price}</Currency>
</Text>
```

### Profit/Loss Display Pattern

```tsx
// Always show sign for P&L
<Text fw={600} c={profit >= 0 ? 'teal' : 'red'}>
  {`${profit > 0 ? '+' : ''}${format.toCurrency(profit)}`}
</Text>

// Combined P&L with percentage
<Text size="xs" c={returnPct >= 0 ? 'teal' : 'red'}>
  {`${format.toCurrency(profit)} (${format.toLocalePercentage(returnPct)})`}
</Text>
```

---

## 16. Forms with Mantine

### `useForm` Pattern

```tsx
import { useForm } from '@mantine/form';

const form = useForm({
  mode: 'controlled',
  initialValues: {
    stockId: undefined as string | undefined,
    price: 0,
    quantity: 1,
    actionDate: new Date()
  },
  validate: {
    stockId: (v) => (v ? null : 'Required'),
    price: (v) => (v > 0 ? null : 'Must be > 0'),
    quantity: (v) => (v > 0 ? null : 'Must be > 0'),
    actionDate: (v) => (v ? null : 'Required')
  }
});

// Spread props directly
<NumberInput {...form.getInputProps('price')} />

// Submit handler
const handleSubmit = form.onSubmit((values) => {
  mutation.mutate({ body: values });
});

<form onSubmit={handleSubmit}>
  {/* ... */}
  <Button type="submit" loading={mutation.isPending} disabled={!form.isValid()}>
    Submit
  </Button>
</form>
```

### Date/Time Inputs

```tsx
import { DateTimePicker } from '@mantine/dates';

<DateTimePicker
  label="DATE"
  {...form.getInputProps('actionDate')}
  styles={{
    label: { fontSize: 12, fontWeight: 600, color: 'var(--text-muted)' },
    input: { height: 50, fontSize: 16, fontWeight: 300 }
  }}
/>
```

### Form Button Group

```tsx
<Group grow mt="lg">
  <Button h={50} variant="default" onClick={close}>
    Cancel
  </Button>
  <Button
    type="submit"
    h={50}
    loading={mutation.isPending}
    disabled={!form.isValid() || mutation.isSuccess}
    color="var(--accent-color)">
    Confirm
  </Button>
</Group>
```

---

## 17. Data Fetching Patterns

### OpenAPI-React-Query (`$api`)

```tsx
import { $api } from '~/api/openapi';

// Query
const { data, status } = $api.useQuery('get', '/api/positions', {
  params: { query: { portfolioId: Number(portfolioId) } }
});

// Mutation
const mutation = $api.useMutation('post', '/api/portfolios/{portfolioId}/trades/buy', {
  onSuccess: () => {
    close?.();
    alerts.success('Trade recorded.');
    client.invalidateQueries();
  },
  onError: (res) => console.error(res)
});

mutation.mutate({
  params: { path: { portfolioId: Number(portfolioId) } },
  body: { stockId: 1, price: 100, quantity: 10, actionDate: new Date().toJSON() }
});
```

### TanStack Query (legacy pattern)

```tsx
import { useQuery } from '@tanstack/react-query';
import { queries } from '~/api';

const { data, status } = useQuery(queries.trades.fetchAll({ portfolioId: 1 }));
```

### Loading/Error/Empty State Pattern

```tsx
if (status === 'pending') {
  return (
    <ContainerCard>
      <LoadingView />
    </ContainerCard>
  );
}

if (status === 'error') {
  return (
    <ContainerCard style={{ borderColor: 'var(--mantine-color-red-5)' }}>
      <ErrorView />
    </ContainerCard>
  );
}

if (data.length === 0) {
  return (
    <ContainerCard>
      <Text c="dimmed" size="xs" fw={600} ta="center">
        No data available
      </Text>
    </ContainerCard>
  );
}

return <Inner data={data} />;
```

---

## 18. Do's and Don'ts

### ✅ Do

- Use `rem()` for any pixel measurement in component props.
- Use `var(--border-color)` and `var(--card-bg)` for borders and backgrounds.
- Use `var(--mantine-color-red-5)` for error-state borders.
- Use `format.toCurrency()` / `<Currency>` for all monetary values.
- Use `useMatches` for responsive component variant switching.
- Extend custom colors in `main.tsx` with proper TypeScript declaration.
- Place `cssVariablesResolver` inside the `theme` object (Mantine v9).
- Use `light-dark()` CSS function in CSS Modules for theme-aware values.
- Use `@mixin hover` in CSS Modules (processed by `postcss-preset-mantine`).
- Use `$mantine-breakpoint-*` variables in CSS Module `@media` queries.
- Use Mantine's `<ScrollArea>` for overflow-scrollable content areas.
- Use `aria-label` on all `<ActionIcon>` elements.
- Keep query staleTime at `30000ms` (the app default) unless there's a specific reason.
- Destructure `type` imports with `import type { ... }` syntax.
- Use single quotes for strings (enforced by Biome).

### ❌ Don't

- ❌ Do not hardcode colors like `#6366f1` in component props — use `var(--accent-color)` or the color name `accent`.
- ❌ Do not use `px` directly in `size`, `h`, `w`, `p`, `m` props — use tokens or `rem()`.
- ❌ Do not use `[data-theme]` for color scheme detection — use `[data-mantine-color-scheme]`.
- ❌ Do not use `--_mantine-*` private CSS variables.
- ❌ Do not pass `cssVariablesResolver` as a direct prop on `<MantineProvider>` (v8 API).
- ❌ Do not use `.toString()` or template literals for financial numbers — use `format.*`.
- ❌ Do not use `var(--mantine-color-body)` expecting it to be your card background — use `var(--card-bg)`.
- ❌ Do not import React explicitly unless needed (React Compiler handles JSX).
- ❌ Do not use `className` with plain string if it can be a CSS Module or Mantine `classNames`.
- ❌ Do not create new font-family values — always extend from the global `fontFamily` in the theme.
- ❌ Do not use `em` units for spacing in component props — use Mantine tokens.
- ❌ Do not use `window.location` for navigation — use TanStack Router `navigate` or `<Link>`.
- ❌ Do not use `useEffect` for data fetching — use TanStack Query.

---

## 19. Quick Reference Tables

### Mantine Component Import Map

| Component | Package | Notes |
|---|---|---|
| `Button`, `Card`, `Text`, `Group`, `Stack`, `Box` | `@mantine/core` | Core layout |
| `TextInput`, `NumberInput`, `Select`, `NativeSelect` | `@mantine/core` | Forms |
| `Modal`, `Drawer` | `@mantine/core` | Overlays |
| `Badge`, `ThemeIcon`, `ActionIcon` | `@mantine/core` | UI elements |
| `SegmentedControl`, `Tabs` | `@mantine/core` | Navigation |
| `ScrollArea`, `SimpleGrid` | `@mantine/core` | Layout |
| `rem`, `useMatches` | `@mantine/core` | Utilities |
| `useForm` | `@mantine/form` | Forms |
| `useDisclosure`, `useMediaQuery` | `@mantine/hooks` | Hooks |
| `DateTimePicker`, `DatePicker` | `@mantine/dates` | Date inputs |
| `BarChart`, `DonutChart`, `LineChart` | `@mantine/charts` | Charts |
| `notifications`, `Notifications` | `@mantine/notifications` | Alerts |

### Status-to-Color Map

| Status | Color token | Variant |
|---|---|---|
| Success / Profit | `teal` | any |
| Error / Loss | `red` | any |
| Info / Buy | `blue` | `light` |
| Warning / Upcoming | `orange` | `light` |
| Primary action | `accent` (custom) | `filled` |
| Secondary / Neutral | `gray` or `dark` | `light`/`default` |
| Dimmed text | `dimmed` | — |

### Global CSS Classes

| Class | Element | Effect |
|---|---|---|
| `.layout-wrapper` | Root div | Full-viewport flex row layout |
| `.sidebar` | Sidebar nav | 18rem, glassmorphism, scrollable |
| `.main-content` | Page body | flex:1, vertical scroll |
| `.card` | Card/section | Glassmorphism backdrop-filter |
| `.modal-content` | Modal body | Glassmorphism, theme-aware bg |
| `.input-base` | All inputs | Styled border, border-radius, transition |
| `.button-primary` | Buttons | Indigo bg, white text, shadow |
| `.sidebar-link` | Nav links | Opacity, hover, active states |
| `.custom-scrollbar` | Scrollable areas | Minimal webkit scrollbar |

### Financial Format Quick Reference

| Value type | Method | Example output |
|---|---|---|
| Currency (compact) | `format.toCurrency(v)` | `₺1,23 Mn` |
| Currency (full) | `format.toCurrency(v, false)` | `₺1.234.567,89` |
| Percentage | `format.toLocalePercentage(v)` | `%12,34` |
| Compact number | `format.toHumanizedNumber(v)` | `1,5 Mn` |
| Short date | `format.toShortDate(d)` | `05 Mar 2026` |
| Short datetime | `format.toShortDateTime(d)` | `05.03.2026 14:43` |
| Full datetime | `format.toFullDateTime(d)` | `05 Mart 2026 14:43` |
| Monetary JSX | `<Currency>{v}</Currency>` | Renders formatted TRY |

---

*Last updated: 2026-03-05. Reflects Mantine `^9.0.0-alpha.3`, React 19, Vite 7, TanStack Router v1.*
