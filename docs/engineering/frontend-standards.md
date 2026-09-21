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

### Feature-Local by Default

Prefer feature-local code by default.

A component, hook, utility, or type should remain inside its feature until
there is a real cross-feature reason to move it into shared code.

Do not promote code to `shared` merely because it could theoretically be reused.

Do not enforce folder structures more deeply than necessary. Let the size and
complexity of a feature determine its internal organization. Simple features
should remain simple.

### Responsibility Boundaries Within Features

As features grow in complexity, organizing code by distinct responsibility keeps
ownership clear. Complex features may separate the following responsibilities
when needed, without requiring every feature to adopt an identical deep folder tree:

- **Pages**: A page represents the composition of a real screen or navigation
  destination. Pages compose feature sections and components, own screen-level
  loading, error, and empty presentation, coordinate screen-specific
  interactions, and open contextual workflows or overlays. A page wrapper is
  not required merely because a route contains a small amount of JSX.
- **Components**: Components represent contained UI pieces or reusable feature UI.
  Do not place code under `components/` merely because it happens to render React.
  A route-aware workspace, screen composition, or large multi-step workflow
  should not automatically be classified as a generic component.
- **Workflows**: Multi-step or mutation-heavy user flows (such as flows with
  edit/configure -> preview -> commit/success steps, coordinated mutations,
  concurrency and version handling, policy confirmations, or workflow-specific
  error handling) may be grouped as workflows when complexity justifies the
  distinction. Do not create a `workflows/` directory in every feature by
  default; use it only when the feature actually contains such flows.
- **Domain / Semantic Logic**: Feature-specific business predicates and semantic
  decisions (e.g., whether an account kind supports a capability, whether an
  activity has a particular economic direction, or what domain states are
  allowed together) should have clear ownership rather than being hidden in
  generic formatter or utility modules. A dedicated `domain/` directory is not
  prescribed for every feature; require clear ownership instead.
- **Presentation**: User-facing mappings such as labels, descriptions, badge and
  status presentation, and enum display should remain close to the feature or
  concept that owns them. Do not mix presentation mappings with unrelated
  generic formatting or business rules simply because all of them are helper
  functions. A dedicated `presentation/` folder is not required where one is
  unnecessary.

### Avoid Catch-All `utils` and `components`

`utils/` and `components/` are not banned; use them when the responsibility
genuinely fits. However:

- Do not use `utils/` as the default destination for any non-component code.
- When a helper clearly represents formatting, validation, presentation, domain
  rules, cache behavior, or workflow logic, prefer ownership that communicates
  that specific responsibility.
- Do not split trivial code into many folders merely to satisfy taxonomy. Simple
  structure is valued over folder purity.

### Cross-Feature Dependencies

Do not prohibit all feature-to-feature imports. A feature may intentionally use
another feature's real public capability (for example, an account screen
launching or composing an exported trading workflow when that interaction is
genuinely part of the product).

However:

- Do not reach into another feature's internal implementation paths for convenience.
- Do not make one arbitrary feature the owner of generic formatting, validation,
  or other cross-feature primitives.
- When a primitive is genuinely domain-agnostic and has multiple unrelated
  consumers, move it to an appropriate shared owner.
- Expose intentional cross-feature capabilities through a small feature public API
  where useful.
- Do not broaden feature barrel files to export every internal helper.

### Shared Ownership

Code belongs in `shared` only when it is genuinely cross-feature or
application-generic.

Good shared candidates:
- generic formatting primitives
- generic validation primitives
- reusable UI primitives
- cross-cutting hooks and application infrastructure

Bad reasons to move code to `shared`:
- "it might be reusable someday"
- avoiding a relative import path
- hiding unclear ownership
- making a feature directory appear smaller

Domain-specific rules must remain with their owning domain, even when written as
pure functions.

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

When the same meaningful invalidation group is duplicated across several
workflows, a semantic cache or invalidation helper may be justified. Do not
create a generic cache abstraction preemptively.

Use the project's typed API client directly when that is sufficient.

Do not create wrapper hooks that merely rename or forward an existing query or
mutation.

Create a custom hook when it encapsulates meaningful reusable behavior.

When opening workflows or overlays, prefer passing identifiers and lightweight
inputs rather than large server objects. Let the destination read canonical
server state itself when appropriate.

---

## 5. Authoritative Financial Values

The backend remains authoritative for financial calculations, rounding, and
financial state.

Frontend code should not invent or reconstruct authoritative financial values
when the backend already owns them.

Avoid treating JavaScript floating-point arithmetic as authoritative financial
business logic.

Small UI calculations and comparisons (such as computing display unit costs from
backend totals or determining PnL signs for visual indicators) may exist where
appropriate, but exact financial semantics must follow the backend contract.

---

## 6. Preserve Type Safety

Prefer inference over repeated explicit types.

Use generated API types rather than duplicating request or response models.

Do not use `any` to bypass a type design problem at public boundaries.

Internal type erasure may occasionally be necessary in generic infrastructure,
but the public API should remain type-safe.

Do not add explicit return types when TypeScript already infers the intended
type clearly.

---

## 7. Keep React State Local

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

## 8. Forms

Use the application's established form solution consistently.

Keep validation close to the form or domain rule it represents.

Prefer typed field values and explicit validation over permissive coercion.

Server validation errors should be surfaced as close as possible to the field
or action that caused them.

Do not build generic form abstractions unless they remove meaningful repeated
behavior.

---

## 9. Errors and Mutations

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

## 10. UI Implementation

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

## 11. Localization Readiness

Localization is planned for the future, but translation libraries are not yet
installed in the codebase. Frontend code should follow stable readiness
principles:

- Generic helpers should return semantic values or raw data rather than
  embedding feature-specific user-facing sentences.
- Repeated user-facing enum and status presentation should have a single, clear
  owner rather than being duplicated across components.
- Do not invent translation keys or a pseudo-i18n abstraction before the actual
  i18n library is introduced.
- Locale-sensitive generic formatters should be designed so locale can be
  supplied or adapted later without requiring a full architectural rewrite.
- Backend enum or error codes should not be blindly converted into user-facing
  English through generic underscore or casing transformations when explicit
  presentation is required.
- Localization concerns belong to presentation, not business or domain rules.
- Do not mandate a new directory solely for future internationalization.

---

## 12. Abstraction Rule

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

## 13. Follow Existing Architecture, Not Existing Mistakes

Architectural changes should be based on multiple representative implementations
rather than a single file:

- The current repository code is the implementation source of truth.
- Inspect neighboring and comparable features before deciding on or introducing
  a pattern.
- Repeated good patterns across multiple features provide stronger evidence
  than one isolated implementation.
- Existing code may contain transitional, inconsistent, or awkward structures;
  do not codify or preserve a mistake solely for consistency.
- When changing an established architectural pattern or cleaning up transitional
  structures, keep the change deliberate, incremental, and scoped.

---

## 14. No Unnecessary Internal Backward Compatibility

During active development, do not preserve obsolete internal frontend APIs solely for backward compatibility. When an internal API is intentionally changed, migrate current call sites and remove the old form instead of adding aliases, overloads, shims, deprecated wrappers, or compatibility branches.

Backward compatibility may still matter when dealing with:
- externally consumed or public APIs
- backend contracts that cannot be changed by the frontend
- persisted user data
- browser or platform compatibility
- migrations where old and new formats genuinely coexist

Do not interpret this rule as permission to silently break external contracts. The target is unnecessary internal frontend compatibility baggage.

---

## 15. Verification

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
