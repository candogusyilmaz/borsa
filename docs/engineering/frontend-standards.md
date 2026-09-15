# Frontend Engineering Guidelines

Status: Stable frontend engineering principles.

These guidelines describe how frontend code should be structured and reasoned about.

They intentionally avoid documenting dependency versions, exact file names,
API endpoints, authentication internals, build-tool details, and other
implementation facts that can be discovered from the repository.

When this document and the current codebase disagree about an implementation
detail, inspect the current code and follow the established architecture unless
the task explicitly requires changing it.

---

## 1. Keep Changes Scoped

Frontend work should remain within the frontend application unless the task
explicitly requires changes elsewhere.

Do not modify backend contracts or invent API behavior to make frontend work
easier.

Treat generated API contracts and the actual backend contract as authoritative.

Do not inspect or modify unrelated parts of the repository without a concrete
reason.

---

## 2. Organize by Responsibility

Keep application concerns separated:

- app-level bootstrap and providers
- routing
- API/data access
- domain features
- genuinely shared code

Prefer feature-local code by default.

A component, hook, utility, or type should remain inside its feature until
there is a real cross-feature reason to move it into shared code.

Do not promote code to `shared` merely because it could theoretically be reused.

Do not enforce folder structures more deeply than necessary.
Let the size and complexity of a feature determine its internal organization.

---

## 3. Keep Routes About Navigation

Route files should primarily deal with navigation concerns such as:

- route definitions
- path/search parameters
- route guards
- loaders when useful
- page composition

Keep substantial feature UI and business behavior inside feature code.

Small composition directly in a route file is fine.

Do not create an extra page/component wrapper merely to satisfy a rule that
route files must contain no JSX.

Routes represent real navigation destinations.
Do not use routing solely as a mechanism for displaying transient overlays.

---

## 4. Server State Belongs to the Query Layer

Remote data should be owned by the application's query/data-access layer.

Do not mirror query results into global stores or local React state without a
specific reason.

Prefer reading canonical server state directly from queries.

After mutations, keep cache synchronization simple and correct using the
existing query infrastructure.

Prefer invalidation/refetching over manually reproducing server-side state
changes unless an optimistic update provides meaningful UX value.

Use the project's typed API client directly when that is sufficient.

Do not create wrapper hooks that merely rename or forward an existing query or
mutation.

Create a custom hook when it encapsulates meaningful reusable behavior.

When opening workflows or overlays, prefer passing identifiers and lightweight
inputs rather than large server objects. Let the destination read canonical
server state itself when appropriate.

---

## 5. Preserve Type Safety

Prefer inference over repeated explicit types.

Use generated API types rather than duplicating request or response models.

Do not use `any` to bypass a type design problem at public boundaries.

Internal type erasure may occasionally be necessary in generic infrastructure,
but the public API should remain type-safe.

Do not add explicit return types when TypeScript already infers the intended
type clearly.

---

## 6. Keep React State Local

Use local React state for local UI state.

Use context or application-level state only when the state genuinely crosses
component or feature boundaries.

Do not introduce a global state library for problems that React state/context
or the query layer already solves.

Avoid copying derived values into state.
Derive them during render where practical.

Avoid premature memoization and performance abstractions.
Add them only when required by the current tooling, semantics, or measured
performance.

---

## 7. Forms

Use the application's established form solution consistently.

Keep validation close to the form or domain rule it represents.

Prefer typed field values and explicit validation over permissive coercion.

Server validation errors should be surfaced as close as possible to the field
or action that caused them.

Do not build generic form abstractions unless they remove meaningful repeated
behavior.

---

## 8. Errors and Mutations

Use the application's existing API error normalization rather than interpreting
raw transport errors independently in each feature.

Handle errors at the most useful level:

- field errors near fields
- workflow errors inside the workflow
- global notifications for appropriate cross-cutting feedback

Do not duplicate loading/error state with manual booleans when the underlying
library already owns that state.

Preserve concurrency/version semantics when the API requires them, but follow
the current domain implementation rather than hardcoding specific error codes
into this general guideline.

---

## 9. UI Implementation

Follow the dedicated UI design guidelines for visual and interaction decisions.

For implementation:

- prefer existing good application components
- prefer framework/library primitives for common behavior
- use CSS when it makes complex styling clearer
- avoid unnecessary wrappers and abstractions
- keep feature-specific presentation close to the feature

Do not rebuild capabilities already provided cleanly by the project's UI
library.

Do not force every visual declaration into CSS or every layout decision into
component props. Choose whichever keeps the code clearest.

---

## 10. Abstraction Rule

Start concrete.

Extract only after an abstraction has a clear responsibility.

Good abstractions remove:
- repeated behavior
- lifecycle complexity
- type complexity
- meaningful implementation duplication

Bad abstractions merely rename an existing component, prop, query, or function.

Prefer boring code over speculative framework-building.

---

## 11. Follow Existing Architecture, Not Existing Mistakes

Inspect nearby implementations before adding a new pattern.

Reuse them when they are simple and appropriate.

Existing code is evidence of project conventions, not proof that the pattern is
correct.

Do not propagate an obviously awkward or obsolete design solely for
consistency.

If changing an established architectural pattern, keep the change deliberate
and scoped.

---

## 12. Verification

Before finishing frontend work:

- run the frontend project's existing type-check command
- run its existing lint/format checks
- run the production build when appropriate
- run relevant existing tests when they exist

Read the current package scripts instead of assuming command names from this
document.

Do not add unrelated tests, tooling, dependencies, or repository changes unless
the task requires them.

---

## Working Principle

When multiple implementations are valid, prefer the one with:

1. less custom infrastructure
2. fewer layers
3. stronger type inference
4. clearer ownership
5. less duplicated state
6. easier future replacement

The goal is straightforward frontend code that follows the current application
architecture without turning current implementation details into permanent
rules.
