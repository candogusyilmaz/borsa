# UI Design Guidelines

Status: Authoritative UI guidance.

The application is designed mobile-first.
Optimize primarily for phone-sized screens and make layouts scale cleanly to tablet.
Desktop-specific optimization is not currently a priority.

Keep implementations simple. Prefer existing good patterns and framework primitives over custom abstractions.

---

## 1. Mobile First

Design for roughly 320–768px first.

The UI must:
- avoid horizontal overflow
- remain comfortable on small screens
- use clear visual hierarchy
- keep important actions easy to reach
- generally provide at least 44px touch targets

Do not add complexity solely to improve large desktop layouts.

---

## 2. Keep the UI Calm

Prefer:
- whitespace
- typography
- spacing
- subtle surfaces
- progressive disclosure

over:
- excessive cards
- borders around everything
- many badges
- many equally prominent buttons
- showing every available field at once

Show the information and actions users are most likely to need first.

Secondary information should look secondary.

---

## 3. Use Mantine Naturally

Use Mantine components and layout primitives when they express the UI clearly.

Prefer things like:

```tsx
<Stack gap="md">
  <Group justify="space-between">
    ...
  </Group>
</Stack>
````

instead of creating CSS whose only purpose is `display: flex`, `gap`, or basic alignment.

Use Mantine props freely for simple styling:

```tsx
<Text size="sm" c="dimmed" fw={500} />
<Button fullWidth />
<Box mt="md" />
```

Do not avoid Mantine props just to move everything into CSS.

But do not create unreadable prop-heavy JSX either.

Use CSS when it is clearer.

---

## 4. CSS and Inline Styles

Use CSS modules for real styling needs such as:

* responsive layout
* complex positioning
* animations and transitions
* pseudo-elements
* hover/focus states
* reusable visual treatments
* overflow and scrolling behavior

Small one-off styles may stay inline.

Do not create a CSS class for two trivial declarations.

Likewise, do not force a complicated CSS problem into Mantine props.

Rule of thumb:

> Do not write CSS merely to avoid Mantine props, and do not create Mantine prop soup merely to avoid CSS.

---

## 5. Reuse Good Existing Patterns

Inspect nearby existing UI before creating something new.

Reuse an existing pattern when it is:

* simple
* visually consistent
* appropriate for the current problem

Existing code is not automatically correct.

If an existing pattern is awkward, overly complex, or outdated, improve it instead of copying it.

Consistency with a bad pattern is not a goal.

---

## 6. Progressive Disclosure

Do not expose every detail immediately.

Prefer:

* concise summaries
* expandable details
* menus
* drawers
* secondary sections
* subtle "Show details" actions

Ask:

> What does the user need to see or do immediately?

Prioritize that.

Rare actions and technical metadata should not compete visually with primary information.

---

## 7. Actions and Overlays

Keep one obvious primary action when possible.

Do not display many actions with equal emphasis.

Use:

* pages for real navigation/browsing
* drawers/sheets for contextual workflows and details
* modals for small focused decisions or confirmations
* menus/popovers for lightweight actions

Avoid stacking large overlays unless there is a strong reason.

Prefer one surface whose content changes over multiple drawers/modals opening on top of each other.

---

## 8. Implementation Simplicity

Prefer the simplest readable implementation.

Default decision order:

1. existing good application pattern
2. Mantine component
3. Mantine props
4. small inline style
5. CSS module
6. new reusable abstraction

Do not introduce reusable components merely because two pieces of JSX look similar.

Create abstractions when they remove meaningful repeated behavior or complexity.

---

## Final Check

Before finishing UI work, ask:

* Does it feel natural on a phone?
* Does it scale cleanly to tablet?
* Is the important information obvious?
* Are secondary details visually secondary?
* Are interactions easy to tap?
* Is there unnecessary visual decoration?
* Could Mantine simplify custom code?
* Would CSS be clearer than excessive Mantine props?
* Did we reuse a good existing pattern?
* Is the implementation simpler than the problem requires?

The goal is polished UI with boring, understandable code.

