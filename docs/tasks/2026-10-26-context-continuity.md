# Task: Context continuity and GitHub CLI repair

- Status: Complete
- Owner/model: gpt-5.6-sol, medium effort
- Started: 2026-10-26
- Last checkpoint: 2026-10-26 21:36 America/Sao_Paulo

## Objective

Make long demands safely resumable from repository state and make future authenticated GitHub operations independent of the missing `/usr/bin/gh` executable.

## Acceptance criteria

- [x] Long-task criteria, checkpoint boundaries, and resume behavior are explicit.
- [x] A reusable task-record template exists.
- [x] `gh` resolves from the per-user installation in a login shell.
- [x] Git uses working absolute GitHub credential-helper paths for GitHub and Gist.
- [x] Authenticated remote access succeeds without the temporary CLI.

## Scope

- In: agent continuity instructions, project workflow rule, task-record protocol, shell path, and Git credential helper.
- Out: product code, Java build configuration, automatic commits, pushes, and releases.

## Decisions

- Use a lean ledger only for complex or long work so routine demands retain low overhead.
- Checkpoint before model/session switches and near two-thirds context use so a new context can resume without rediscovery.
- Reuse the existing per-user GitHub CLI if it can be repaired; preserve it before any replacement.
- Retain GitHub CLI 2.103.0 because the expired session, not the binary version, caused the failure; authentication and remote access pass after renewal.
- Configure `github.com` and `gist.github.com` independently because Git stores URL-scoped credential helpers in separate blocks.

## Working set

- Changed: `AGENTS.md`, `.cursor/rules/project-workflow.mdc`, `docs/tasks/`, and the context-continuity spec/plan; outside Git, `/home/davisr/.bash_profile` and `/home/davisr/.gitconfig`.
- Relevant: `/home/davisr/.local/bin/gh`, `/home/davisr/.bash_profile`, `/home/davisr/.gitconfig`.

## Verification

- Passed: documentation structure and whitespace checks (`TASK_1_VALIDATION_OK`).
- Passed: login shell resolves `/home/davisr/.local/bin/gh` version 2.103.0.
- Passed: `gh auth status` and `gh api user` identify authenticated account `davisrr-18`.
- Passed: normal `git ls-remote` resolves remote `main` and `v2.1.0-beta` using the permanent helper.
- Passed: final combined requirements check and `git diff --check` (`FULL_VERIFICATION_OK`).
- Passed: both effective GitHub and Gist helpers resolve to `/home/davisr/.local/bin/gh`; no global helper references `/usr/bin/gh` (`BOTH_GITHUB_HELPERS_OK`).
- Passed: `./gradlew compileJava` (`BUILD SUCCESSFUL`; task up-to-date) after the sandbox-only lock-file failure was rerun with Gradle cache access.
- Passed: post-review combined requirements, authentication, remote-access, checklist, and diff gate (`POST_REVIEW_FINAL_GATE_OK`).
- Passed: `./gradlew test` (`BUILD SUCCESSFUL`; compile, resources, test classes, and tests up-to-date) during branch-finish verification.

## Checkpoint

- Completed: protocol and template created and structurally verified; existing CLI retained; login-shell path, Git helper, and GitHub authentication repaired.
- Current state: the independent review's important findings are fixed; compilation and the post-review final gate pass; repository changes remain uncommitted pending an explicit request.
- Next action: use `docs/tasks/TEMPLATE.md` when the next qualifying demand begins.
- Blockers: None.
