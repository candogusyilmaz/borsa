# Implementation PR workflow

This directory contains the active implementation pointer, the concise current-state handoff, the PR template, and bounded implementation specifications.

## Active-spec mechanism

`CURRENT.md` is a stable pointer to exactly one active PR specification, or explicitly says that no implementation PR is active. It is not a state document or a specification. The active specification owns exact scope, acceptance criteria, non-goals, and completion details.

`STATE.md` describes the repository as it exists now: implemented capabilities, current schema, deferred work, and the latest useful verification state. It is not a changelog or a detailed PR archive. Completed specifications and Git history retain historical implementation details.

## Context loading

Implementation and review agents read `CURRENT.md`, then the active specification, then only the relevant standards, contracts, and feature documents. Planning agents start with `STATE.md`, `CURRENT.md`, this workflow document when needed, and `PR-TEMPLATE.md`, then load only the roadmap and domain sections required for the next bounded unit. Historical PR specifications are not normal bootstrap context.

## Lifecycle

1. The human/planner derives exactly one next specification from current state and the roadmap.
2. `CURRENT.md` points to that specification.
3. The implementation agent completes only its scope and records verification and completion details.
4. The human reviews the complete active-unit change surface, including relevant unstaged, staged, untracked, or already committed changes; fixes remain within the same bounded unit.
5. After acceptance, the user decides the Git and pointer transition before the next specification is written just in time.

Do not pre-specify the full roadmap, automatically activate a later PR, or let implementation silently defer acceptance criteria.

A unit is ready for the user's acceptance or commit decision only after its required verification passes and review has no unresolved `MUST FIX` findings.

## Capability and review boundaries

A PR should be a substantial, coherent capability or lifecycle slice carried through every layer needed for a meaningful testable outcome. Combine tightly coupled database, domain, repository, application, API, security, and test work when splitting them would leave an incomplete or mechanical boundary. Split independent business capabilities, invariants, migration risks, security boundaries, or scopes that no longer fit one careful focused review. Do not pad a PR with unrelated cleanup or speculative infrastructure; sizing is determined by capability and review coherence, not a fixed line or file count.

Use [PR-TEMPLATE.md](PR-TEMPLATE.md) for each new specification. Specifications should reference, rather than duplicate, [coding standards](../engineering/coding-standards.md), [the backend master plan](../review/backend-master-plan.md), [the accounting contract](../review/accounting-contract.md), and relevant feature documents.
