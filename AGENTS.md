# The Dream Table — Agent Guide

This file governs how AI agents work in this repository. It does not replace the project rules in `.cursor/rules/`: those rules define the technical truth that every implementation must follow.

## Before changing anything

- Read the user's current request first.
- Inspect the repository and the nearest applicable `AGENTS.md` files.
- Read every applicable `.cursor/rules/*.mdc` file before changing code in its scope.
- Check `git status --short` and preserve existing user changes. Do not reset, checkout, clean, overwrite, or reformat unrelated work.
- Identify acceptance criteria and the smallest files that need to change.
- For Minecraft or Fabric APIs, inspect the pinned local artifacts or existing project usage instead of guessing method names.

## Instruction precedence

Apply guidance in this order:

1. The user's current request and explicit decisions.
2. The nearest applicable `AGENTS.md`, from the repository root toward the target file.
3. Applicable `.cursor/rules/*.mdc` rules.
4. Project documentation and established code patterns.

`AGENTS.md` describes agent behavior. `.cursor/rules` describes valid project behavior. Never silently override a technical invariant. If instructions conflict, explain the conflict and improve the governing documentation when that is safe and in scope.

## Working loop

1. Diagnose and map the relevant code before editing.
2. State the acceptance criteria and the chosen model/effort when that choice is actionable.
3. For behavior changes, write or identify a regression test before implementation. Follow the repository's test and build rules.
4. Make the smallest coherent change. Refactor only when it improves correctness, ownership, maintainability, or the development flow.
5. Update the applicable rule or documentation when a durable architectural invariant changes.
6. Verify with the narrowest useful check, then run the broader required checks from `project-workflow.mdc`.
7. Report changed files, verification evidence, limitations (including anything not tested in Minecraft), and the routing recommendation below.

## Autonomy and boundaries

Agents may improve code, architecture, tests, documentation, rules, and agent instructions when the improvement is in scope and supported by evidence. Structural changes must explain their benefit, preserve saved-world and network compatibility where applicable, and include regression coverage when logic or persistence changes.

Ask for direction only when work would materially expand product scope, introduce an unresolved user-facing product decision, require destructive action, or affect external systems. Commits, pushes, releases, publishing, and copying artifacts to external instances require an explicit request.

Do not hide unrelated failures, silently discard user work, or make broad cosmetic rewrites. Unexpected runtime errors should be diagnosed rather than papered over.

## Rules and scoped agent guides

The canonical technical rules are:

- `.cursor/rules/project-workflow.mdc` — toolchain, tests, versions, releases, and language;
- `.cursor/rules/architecture.mdc` — package ownership, state, synchronization, persistence, and extension points;
- `.cursor/rules/build-safety.mdc` — build, repair, dismantle, permissions, loading, and saved-state invariants;
- `.cursor/rules/code-style.mdc` — Java structure, naming, and comments;
- `.cursor/rules/ui-style.mdc` — menu, clipboard, and hologram behavior.

Keep rules focused, actionable, and scoped by precise globs when appropriate. Agents may change them when a technical invariant or durable lesson changes. Do not duplicate their implementation details here.

Start with this root file only. Add a nested `AGENTS.md` only when a subtree has durable agent workflow needs that differ from the root and cannot be expressed as a project-wide technical rule. A nested file must be concise, additive, and free of duplicated root/rule content.

## Context continuity

For multi-step, cross-package, high-risk, or likely multi-session work, create `docs/tasks/YYYY-MM-DD-<slug>.md` from `docs/tasks/TEMPLATE.md` before implementation. Simple answers, read-only investigations, and one-step mechanical edits do not need a task record.

Keep the record concise and factual: objective, acceptance criteria, scope, decisions, affected files, verification evidence, current checkpoint, and the exact next action. Never paste chat transcripts, secrets, tokens, or large command output.

Update the checkpoint after each meaningful phase, before pausing, before changing model or session, and when roughly two-thirds of the useful context has been consumed. When changing models, do so only after a durable checkpoint; a checkpoint does not itself require a model change. If the current model becomes unavailable or demonstrably incapable, record the state at the earliest safe opportunity.

To resume, read the active task record, applicable instructions and rules, then verify `git status`, recent commits, and relevant diffs before acting. Trust repository evidence over remembered conversation state. Mark the record complete only after final verification and leave it as the durable handoff.

## Model and reasoning routing

Choose the agent/model and reasoning effort independently for every demand after inspecting enough context to estimate its difficulty. There is no permanent project-wide default. A single demand may use different combinations for diagnosis, implementation, visual work, and review.

Only consider agents/models exposed by the current project environment. Do not recommend a model merely because it exists in external documentation, another product, or a different workspace. If availability cannot be confirmed, state that limitation and recommend only from the visible project list.

Within that available list, use the least expensive capable option and escalate when risk, ambiguity, cross-package impact, failed verification, or conflicting designs justify it. Match effort to the task: low for mechanical and exact work, medium for normal implementation, high for difficult debugging or high-risk changes, and xhigh/max only when the available model supports it and evidence shows the extra reasoning is warranted.

At the beginning, state the routing recommendation when it is actionable. At the end of every demand, the final handoff must include an **Agent recommendation** naming an agent/model available to this project and the reasoning effort appropriate for that completed task, why it fit, whether a lighter available option would have been insufficient, and whether escalation occurred. This is required even when no files changed.

## Language

Communicate with Davi in Portuguese. Keep in-game text, README content, release notes, changelogs, and code comments in English, following `project-workflow.mdc`.
