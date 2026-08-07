# Implementation PR workflow

R0–R16 in the master plan are roadmap increments. They are intentionally larger than the pull requests used to implement them. Pull requests are optimized for **human reviewability**.

## Active-spec mechanism

`docs/implementation/CURRENT.md` is the single pointer to the PR specification currently being implemented. Root `AGENTS.md` instructs compatible coding agents to read it automatically before code changes. This avoids repeating repository standards and active-scope instructions in every prompt.

At any time:

- `CURRENT.md` points to exactly one active PR specification; or
- it explicitly says no implementation PR is active.

Do not use `CURRENT.md` as the specification itself. It is only a stable pointer.

## When to write PR specifications

Write specs just in time, normally one to three PRs ahead. Do not pre-specify the full R0–R16 roadmap because implementation will reveal constraints and better boundaries.

A PR should normally represent one coherent capability/invariant/infrastructure behavior. If a human reviewer cannot understand the full diff comfortably, split it further.

## Authority

A PR specification must reference rather than duplicate:

- `docs/review/backend-master-plan.md` for architecture/scope/order;
- `docs/review/accounting-contract.md` for cross-cutting financial semantics;
- `docs/engineering/coding-standards.md` for implementation style;
- relevant feature design documents where needed.

If implementation reveals that an authoritative rule must change, update the authoritative document first, then the active PR spec, then code.

## PR lifecycle

1. Human/assistant defines a bounded PR specification from the roadmap.
2. Set `CURRENT.md` to that spec.
3. Coding agent implements only the spec.
4. Agent runs required verification and fills the completion record.
5. Human reviews the diff and behavior.
6. Fix within the same bounded scope as needed.
7. Merge.
8. Update progress report and clear/change `CURRENT.md`.
9. Write the next PR spec using what was learned.

## Review-size rule

There is no hard line-count limit because migrations/tests/domain code differ in density. The test is cognitive:

> Can one reviewer verify the schema changes, invariants, API behavior and tests without mentally loading multiple future capabilities?

If not, split the PR.

Use `PR-TEMPLATE.md` when creating each specification.
