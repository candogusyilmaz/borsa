# UI design guidelines

- Design for phone widths first. Keep primary actions reachable, use touch targets of at least 44 pixels where practical, and allow dense tables to scroll within their own region.
- Use the Mantine theme in `web/src/theme/` for color, typography, spacing, surfaces, focus and motion. Use the financial profit and loss tokens for their corresponding data and actions.
- Prefer Mantine primitives for layout and interaction. Use CSS modules for responsive rules, overflow, meaningful custom states and animation.
- Give the primary action a clear hierarchy. Use drawers for contextual details and modal dialogs for focused decisions. Keep labels and status messages close to the controls they describe.
- Check light and dark schemes, keyboard focus, and the layout on a phone and a desktop.
