# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-005 — Atomic local account registration](PR-005-atomic-local-account-registration.md)

Implementation agents must read the complete specification and implement only that scope.

The user owns Git history. Make working-tree changes only.

Do not commit, create or switch branches, merge, rebase, tag, reset, stash, or push unless explicitly requested.

PR-001, PR-002, PR-003, and PR-004 have been reviewed and accepted by the user.

Treat the current repository state as the starting point. Do not reimplement earlier work or resurrect deleted legacy backend code.

Do not advance this pointer after implementation.

It remains on PR-005 until the user has reviewed and accepted the resulting diff.
