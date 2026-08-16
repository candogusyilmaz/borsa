# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-021 — Financial-account onboarding and immutable cash ledger](PR-021-financial-account-onboarding-and-cash-ledger.md)

Implementation agents must read the complete specification and implement only that scope.

PR-020 is complete in accepted commit `3f45a8c`: the V2 canonical offline reference catalogue, deterministic seeds, explicit market-calendar coverage, owner-scoped manual-instrument lifecycle, SQL search/cursor behavior, user-authorized cross-cutting standards alignment, and all required review fixes passed the expanded 77-test focused gate, full 266-test suite, Spotless, and Maven `verify`.

PR-021 is the first financial-truth vertical slice. It adds V3 owner-scoped financial-account onboarding, explicit opening-state coverage, immutable cash activities/postings, native balance projections/reads, manual deposits/withdrawals/same-currency owned transfers, policy enforcement, idempotency, reversal/opening correction, locking, HTTP/security boundaries, and required pure/PostgreSQL/integration proof.

Do not add reconciliation/imports, pending settlement, investments/trades, multi-currency/FX, spending/income/bills/debt workflows, households, providers, frontend work, or later ledger features.

Do not implement the retired custom durable-job design. No generic scheduler, batch, workflow, rules, retry, or queue infrastructure is justified by PR-021. Future commodity infrastructure must follow the repository build-versus-buy rule and be selected with its first concrete production consumer.

The user owns Git history. Agents perform no commit, branch, merge, rebase, reset, push, or remote operation unless explicitly requested.

Keep this pointer on PR-021 throughout implementation and review. Do not draft or activate PR-022 during this unit.
