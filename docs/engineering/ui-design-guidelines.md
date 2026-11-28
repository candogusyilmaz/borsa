# UI design guidelines

## Foundations

- Use the Warm Paper and Obsidian semantic variables in `web/src/theme/`. Warm Paper uses a `#F3F2EE` canvas, `#FAF9F6` surfaces and `#EAE8E1` raised or hover surfaces. Obsidian uses a `#0D0D0F` canvas, `#131316` surfaces and `#1B1B20` raised or hover surfaces.
- Use the neutral primary fill for main actions and neutral surfaces for selection and allocation, and the accent for focus. Use `--app-finance-profit` and `--app-finance-loss` for signed financial values. Include the sign, a label or another visible cue alongside color.
- Use Inter at 400–600 for product UI, JetBrains Mono for ticker monograms, and tabular numerals for currency, quantities and percentages. Establish hierarchy through type, whitespace and one-pixel dividers. Reserve raised surfaces for navigation and overlays.

## Layout and interaction

- Use the shared `74rem` member container and safe-area-aware horizontal insets for the top bar and main content on every page. Mobile list dividers can extend to the viewport edges while row content follows the shared insets. Use the mobile header for page context and account actions. Keep Dashboard, Portfolios, Positions and Trades in the safe-area-aware bottom navigation.
- Build display layouts with semantic HTML and CSS Modules. Use Mantine for interactive menus, overlays, form controls, tooltips, segmented controls and notifications.
- Use `useIsMobile` at the 48em boundary when markup changes. Share data and interaction state across desktop and mobile views. Use CSS for spacing, wrapping and other layout changes.
- Use `ResponsiveOverlay` for centered desktop dialogs, right-side inspectors and mobile bottom sheets. Preserve keyboard focus, include an accessible title and keep the mobile body clear of the home indicator.
- Keep desktop actions compact. Use 44px targets for primary controls on coarse pointers, `100dvh` for viewport sizing and `inputMode="decimal"` for price and quantity fields.
- Use a native desktop positions table and two-column mobile rows. Use a single trade list for global, portfolio and position history. Use tabs for the Portfolio sections on mobile and a vertical flow on desktop.

## Financial data and verification

- Render financial data from the existing OpenAPI types and TanStack Query hooks. Keep calculations scoped to the applicable currency and available data. Only expose undo for trades the portfolio history marks as eligible. Dashboard fallbacks belong in its adapter. Identify estimated values and partial currency coverage in the UI, and record their replacement contracts in [Dashboard backend requirements](dashboard-backend-requirements.md).
- Verify light and dark appearance, keyboard operation, overlay focus, mobile safe areas and horizontal fit. Follow the verification commands in [web/AGENTS.md](../../web/AGENTS.md).
