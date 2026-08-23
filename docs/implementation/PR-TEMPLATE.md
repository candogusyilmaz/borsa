# PR-XXX - <title>

Status: DRAFT | ACTIVE | COMPLETE

## Goal

One short paragraph describing the observable outcome of this PR.

## Capability and review boundary

- Coherent capability: explain why this is one independently meaningful implementation unit.
- Combined behaviors: identify the tightly coupled database, domain, application, API, security, and test behaviors that belong together and why separating them would leave an incomplete or mechanical boundary.
- Excluded neighbor: name the independent capability deliberately left for later.
- Focused review: explain why one careful human can verify the scope, invariants, and tests as one review unit.
- Judge size by capability and review coherence. Do not use a fixed line-count, file-count, or production-LOC target, and do not add padding to reach one.

## Source documents

- `docs/review/backend-master-plan.md` - R?/relevant sections
- `docs/review/accounting-contract.md` - relevant sections, if financial
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

1. Update `docs/implementation/STATE.md` with current repository reality only: implemented capabilities, migration/schema state, verified decisions, deferred work, and the latest useful verification state.
2. Replace or remove obsolete STATE statements; do not append a history trail.
3. Update authoritative architecture or domain documents only when verified behavior changes their ownership or contract.
4. Move reusable Windows, sandbox, Maven, Docker/Testcontainers, or tool-output lessons to `docs/engineering/codex-command-playbook.md`.
5. Do not copy detailed implementation history or old test totals into `STATE.md`.
6. Leave detailed implementation history in this specification's Completion Record and Git history.
7. Do not preserve temporary hypotheses, debugging notes, or completed checklist noise as durable documentation.

Update `docs/review/progress-report.md` when project-level status or an architectural decision materially changes.

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
