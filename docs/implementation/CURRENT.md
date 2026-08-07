# Current implementation PR

Status: **ACTIVE**

Specification:

[PR-002 — V1 foundation database](PR-002-v1-foundation-database.md)

Implementation agents must read the complete specification and implement only that scope.

The user owns Git history: make working-tree changes only. Do not commit, create/switch branches, merge, rebase, tag, reset, stash or push unless explicitly requested.

PR-001 has been reviewed and accepted by the user. Treat the current working tree/HEAD as the starting implementation state; do not reimplement PR-001 or resurrect deleted legacy backend code.

Do not advance this pointer after implementation. It remains on PR-002 until the user has reviewed and accepted the diff.
