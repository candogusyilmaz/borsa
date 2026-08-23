# Codex command playbook

This file stores verified, reusable execution-environment procedures for this Windows sandbox. It does not define product scope, Git ownership, or the planner/implementer/reviewer workflow.

Record lessons in this form:

**Problem / condition**

What repeatable environment or tool condition occurs.

**Resolved procedure**

The command or procedure that has worked.

**Known restriction**

Any permission, network, Docker, output, or safety limitation that still matters.

Do not record one-off compilation or assertion failures, temporary hypotheses, long command histories, or obsolete environment assumptions. Replace a contradicted procedure when direct current evidence establishes a better one.

## Workspace and shell

**Problem / condition:** Repository searches and edits run in the Windows workspace.

**Resolved procedure:** Work from `C:\Users\Vintage\Documents\stocks`; use `rg`/`rg --files` for discovery; keep inspection commands focused; use `apply_patch` for file edits rather than redirection, `cat`, or inline write scripts.

**Known restriction:** Quote paths and comma-separated Maven properties in PowerShell:

```powershell
./mvnw "-Dtest=FocusedTestA,FocusedTestB" test
Get-Content -Raw "docs\implementation\CURRENT.md"
```

## Git safe-directory warning

**Problem / condition:** The sandbox identity differs from the Windows owner of this checkout, so Git can report `detected dubious ownership`.

**Resolved procedure:** For read-only inspection, pass a one-command safe-directory override and inspect unstaged, staged, and untracked state explicitly:

```powershell
git -c safe.directory=C:/Users/Vintage/Documents/stocks status --short --untracked-files=all
git -c safe.directory=C:/Users/Vintage/Documents/stocks diff
git -c safe.directory=C:/Users/Vintage/Documents/stocks diff --cached
```

When the active implementation has already been committed but remains under review, derive its starting commit from the active specification, `STATE.md`, and Git history, then inspect the complete range as well as later working-tree state:

```powershell
git -c safe.directory=C:/Users/Vintage/Documents/stocks diff STARTING_COMMIT..HEAD --stat
git -c safe.directory=C:/Users/Vintage/Documents/stocks diff STARTING_COMMIT..HEAD
```

Replace `STARTING_COMMIT` with the verified baseline; do not guess it from commit messages alone.

**Known restriction:** Do not change global Git configuration merely to silence the warning. Do not stage, commit, reset, clean, switch branches, push, or otherwise mutate Git unless the user explicitly requests it. Plain `git diff` omits staged and untracked files; `git diff --cached` omits unstaged and untracked files; commit-range diffs omit later working-tree changes. Pair the relevant diff forms with `git status --short --untracked-files=all` and inspect untracked files directly.

## Maven and network permissions

**Problem / condition:** The default sandbox can block Maven Central while resolving a parent or dependency.

**Resolved procedure:** First run the normal wrapper command, such as:

```powershell
./mvnw spotless:check
./mvnw test
./mvnw verify
```

If the failure is specifically a dependency-resolution network or permission error, rerun the same command with an escalation request and a narrow `./mvnw` prefix.

**Known restriction:** Separate dependency-resolution blockers from compilation or test failures. Do not alter dependencies or test configuration to work around the environment.

When a failure matches a documented condition, identify the condition, apply its resolved procedure, and stop exploring equivalent Maven, dependency, repository, or build configurations first. Do not repeatedly request elevated permission for equivalent failed approaches. Add or revise a playbook procedure only after direct successful evidence establishes a reusable resolution.

**Problem / condition:** Clean Java 25 compilation relies on the repository's explicit Lombok annotation-processor configuration.

**Resolved procedure:** Keep the Lombok processor configuration in `pom.xml` when running clean compilation, formatting, or verification; validate changes with the normal Maven wrapper commands.

**Known restriction:** Do not replace the configured processor with ad hoc compiler flags or dependency changes to address a one-off compilation failure.

## PostgreSQL timestamp fixtures

**Problem / condition:** A PostgreSQL/Testcontainers JDBC fixture binds a `timestamptz` parameter through `JdbcTemplate`.

**Resolved procedure:** Bind an `OffsetDateTime` parameter in the JDBC fixture. JPA entity timestamp fields may continue to use `Instant`.

**Known restriction:** This is a JDBC fixture-binding rule, not a reason to change the domain or entity timestamp type.

## PostgreSQL and Testcontainers

**Problem / condition:** PostgreSQL integration tests use Testcontainers.

**Resolved procedure:** Run the required Testcontainers tests against the local Docker Desktop engine. Preserve real database tests where schema, constraint, transaction, locking, or query semantics matter.

**Known restriction:** If Docker is unavailable, report the environment blocker after checking the test output. Do not replace containers with mocks, silently skip tests, or change production test configuration.

## Long-running commands

**Problem / condition:** A command may return a running cell identifier instead of completing in the initial call.

**Resolved procedure:** Use the matching wait operation for that cell and collect output in 10-30 second intervals, reporting progress between long operations.

**Known restriction:** Do not wait more than 60 seconds in one interval, and do not call wait after the command has completed.

## Output and inspection

**Problem / condition:** Tool output can be truncated or become noisy when large files and logs are dumped at once.

**Resolved procedure:** Use targeted searches and line ranges:

```powershell
rg -n "pattern" path
$lines = Get-Content path
for ($i = 100; $i -le 160; $i++) { "{0}: {1}" -f $i,$lines[$i-1] }
git -c safe.directory=C:/Users/Vintage/Documents/stocks diff -- path/to/file
git -c safe.directory=C:/Users/Vintage/Documents/stocks diff --cached -- path/to/file
```

**Known restriction:** Treat truncated output as an indication to run a narrow follow-up command, not as evidence that unseen content is absent. Read large specifications in sections and inspect changed production files and tests separately.

## Verification and scope

**Problem / condition:** A successful implementation or review requires evidence across the relevant test layers without broad context loading.

**Resolved procedure:** Follow the active specification's focused tests, full tests, formatter, and `verify` commands when the environment permits. Inspect the complete active-unit change surface, including relevant unstaged, staged, untracked, and already committed changes, before trusting a summary. Use the repository's role-routing documents for scope and documentation maintenance.

**Known restriction:** Documentation-only work does not authorize production-code, test, migration, dependency, configuration, frontend, or Git-history changes. Preserve unrelated user changes.
