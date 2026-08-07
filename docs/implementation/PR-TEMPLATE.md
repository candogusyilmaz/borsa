# PR-XXX — <title>

Status: DRAFT | ACTIVE | COMPLETE

## Goal

One short paragraph describing the observable outcome of this PR.

## Source documents

- `docs/review/backend-master-plan.md` — R?/relevant sections
- `docs/review/accounting-contract.md` — relevant sections, if financial
- `docs/engineering/coding-standards.md`
- other feature-specific design documents, if needed

## Starting state

State exactly what must already exist before this PR.

## Scope

1. ...
2. ...

## Explicit non-goals

- ...
- ...

## Database changes

Migration(s):

- ...

Tables/columns/constraints/indexes introduced or changed:

- ...

Do not create unrelated future schema.

## Application changes

Expected packages/classes/workflows and responsibility boundaries. Keep this guidance minimal enough that the implementation can remain idiomatic.

## API contract

Endpoints/requests/responses/problem codes added or changed. Write `None` when the PR has no API surface.

## Business invariants

- ...
- ...

Do not restate cross-cutting rules already owned by `accounting-contract.md`; reference the relevant sections instead.

## Required tests

### Pure/domain

- ...

### PostgreSQL/Testcontainers

- ...

### HTTP/security

- ...

## Acceptance criteria

1. ...
2. ...

## Documentation completion

Before this implementation unit is considered complete:

- fill in this specification's Completion Record;
- update `docs/implementation/STATE.md` with:
  - newly implemented capabilities;
  - database migration/version changes;
  - new architectural/domain decisions;
  - newly discovered deferred work;
- update `progress-report.md` if project-level progress changed.

Do not put detailed implementation history into STATE.md.
Keep it as a concise handoff of the current repository state.

## Verification commands

```bash
./mvnw test
```

Add narrower/full commands required by this PR.

## Completion record

Fill this before marking the PR complete.

### Implemented

- ...

### Deviations from specification

- None / ...

### New decisions

- None / ...

### Tests executed

- ...

### Follow-up work

- ...
