# Codex command playbook

This is repository-specific operational guidance for coding-agent sessions on the Windows sandbox. It explains recurring environment failures and the command forms that work reliably. It does not change product scope, Git ownership, or the active PR workflow.

## Workspace and shell

- Work from `C:\Users\Vintage\Documents\stocks`.
- Use `rg`/`rg --files` for searches and file discovery.
- Quote paths and comma-separated Maven properties in PowerShell, for example:

```powershell
./mvnw "-Dtest=FocusedTestA,FocusedTestB" test
Get-Content -Raw "docs\implementation\CURRENT.md"
```

- Keep commands focused. Separate inspection commands when output would otherwise be truncated or noisy.
- Use `apply_patch` for file edits. Do not write files through `cat`, redirection, or inline scripting.

## Git safe-directory warning

The sandbox identity differs from the Windows owner of this checkout. Git may report “detected dubious ownership.” For read-only inspection, use a one-command override:

```powershell
git -c safe.directory=C:/Users/Vintage/Documents/stocks status --short
git -c safe.directory=C:/Users/Vintage/Documents/stocks diff --stat
```

Do not modify global Git configuration merely to silence this warning. Do not stage, commit, reset, clean, switch branches, or perform other Git mutations unless the user explicitly asks.

`git diff` does not include untracked files. Always pair it with `git status --short`, then inspect untracked files directly or with `rg --files`. `git diff --check` also cannot validate untracked files; run formatter/tests and inspect new files separately.

## Maven and network permissions

The default sandbox may block Maven Central even when dependencies are otherwise healthy. First try the normal wrapper command. If Maven fails while resolving a parent or dependency with a network/permission error, rerun the same command with an escalation request:

- sandbox permission: `require_escalated`;
- a short user-facing justification explaining that Maven Central is needed;
- a narrow reusable prefix such as `./mvnw`.

Typical verification commands are:

```powershell
./mvnw spotless:check
./mvnw test
./mvnw verify
```

Do not treat a dependency-resolution failure as a code failure. Record it separately from compilation or test results.

## Testcontainers and Docker

PostgreSQL integration tests use Testcontainers and require the local Docker Desktop engine. Do not replace them with mocks when database semantics matter. If Docker is unavailable, report the environment blocker after checking the Docker connection through the test output; do not alter production test configuration or silently skip containers.

## Long-running commands

When the command tool returns a running cell ID, use the matching wait operation to collect its output. Do not call wait after a command has already completed. Prefer waits of 10–30 seconds and report progress between long operations; do not block for more than 60 seconds in one wait.

## Output and inspection

Tool output can be truncated. Prefer:

```powershell
rg -n "pattern" path
$lines = Get-Content path
for ($i = 100; $i -le 160; $i++) { "$i`: $($lines[$i-1])" }
git -c safe.directory=C:/Users/Vintage/Documents/stocks diff -- path/to/file
```

Read large specifications in sections and inspect production files and tests separately. A successful command with truncated output is still usable, but its summary should come from a narrow follow-up command rather than assumptions.

## Review and implementation checklist

For a review, inspect the active specification, actual tracked diff, untracked files, and changed-file behavior before trusting a completion summary. Run the specification’s focused tests, full tests, formatter, and `verify` when the environment permits. Preserve unrelated user changes.

For implementation, read `AGENTS.md`, `docs/implementation/CURRENT.md`, the active specification, and the required standards before editing. Keep changes in the working tree for the user. Documentation-only requests do not authorize production-code or Git-history changes.
