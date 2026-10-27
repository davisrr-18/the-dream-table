# Context Continuity Design

## Intent

Keep long or complex demands reliable across context compaction, model changes, and new sessions without making routine work pay a large documentation cost.

## Goals

- Make the repository, rather than chat history, the durable source of task state.
- Record only the decisions and evidence needed to resume safely.
- Checkpoint before a phase, model, or session changes and before context becomes scarce.
- Preserve the existing distinction: `AGENTS.md` governs AI behavior, while `.cursor/rules` states the project-wide workflow requirement.
- Repair local GitHub CLI routing so future Git operations do not depend on a temporary binary.

## Non-goals

- Logging every command or copying conversation transcripts into the repository.
- Creating task records for simple answers or one-step mechanical edits.
- Selecting one permanent model for the project.
- Automatically committing, pushing, or publishing work.

## Selected approach

Use a lean repository task ledger under `docs/tasks/`. Multi-step, cross-package, high-risk, or likely multi-session work starts from one template. The agent updates the same file at meaningful checkpoints and marks it complete with verification evidence. On resume, an agent reads the task record together with repository instructions and verifies the current Git state before continuing.

Model changes happen at recorded phase boundaries. The least expensive capable model remains preferred; a stronger model is introduced only for a phase whose ambiguity or risk justifies it.

For GitHub CLI, reuse the existing per-user installation when valid. Preserve any existing executable before replacement, keep `~/.local/bin` on the shell path, and configure Git's GitHub credential helper with an absolute path so non-interactive shells remain reliable.

## Alternatives considered

### Keep all continuity in chat summaries

Lowest repository overhead, but summaries are not reviewable project artifacts and can lose exact decisions, commands, and remaining work.

### Create a detailed log for every demand

Maximizes traceability but spends context and maintenance effort on routine work. Rejected because it conflicts with the project's cost-efficiency goal.

### Lean task ledger with explicit checkpoints — selected

Keeps long work resumable while leaving small tasks lightweight. It also supports changing models between phases without repeatedly rediscovering the repository.

## Verification

- Validate rule frontmatter and every referenced path.
- Validate the task template contains objective, acceptance criteria, decisions, file state, verification, checkpoint, and next action.
- Confirm Git has a working GitHub credential helper independent of `/usr/bin/gh`.
- Confirm an ordinary non-interactive shell can resolve `gh` after shell configuration is loaded.
- Do not run the Java build because no product code or build configuration changes.

