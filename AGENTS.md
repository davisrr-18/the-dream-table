# The Dream Table — Agent Guide

This file defines permanent AI-agent working rules. Technical invariants belong to `.cursor/rules/*.mdc`; detailed update planning belongs to `.agents/skills/plan-mod-update/SKILL.md`.

## 1. Before starting

- Understand the user's request, existing decisions, scope, and acceptance criteria.
- Read the nearest applicable `AGENTS.md` and relevant `.cursor/rules/*.mdc` files.
- Check `git status --short` and preserve all existing changes.
- Consult active records in `docs/tasks/` before repeating investigations.
- Inspect actual code and pinned Minecraft/Fabric artifacts instead of guessing APIs.
- Confirm the active Git root, branch, and worktree before making changes.

Repository guidance follows this order:

1. Current user request and explicit decisions.
2. Nearest applicable `AGENTS.md`.
3. Applicable `.cursor/rules/*.mdc` technical rules.
4. Project documentation and established patterns.

Never silently override a technical invariant. Identify conflicts and resolve them within the approved scope.

## 2. Engineering workflow

- Diagnose relevant behavior before editing.
- Choose the smallest coherent, maintainable change.
- For behavior changes, identify or create regression coverage before implementation when feasible.
- Preserve saved-world, networking, permissions, and persistence compatibility.
- Update the applicable technical rule when a durable architectural invariant changes.
- Run `./gradlew compileJava` after code changes and the tests required by `project-workflow.mdc`.
- Report changed files, test results, limitations, and anything not tested inside Minecraft.

The canonical technical rules are:

- `.cursor/rules/project-workflow.mdc`
- `.cursor/rules/architecture.mdc`
- `.cursor/rules/build-safety.mdc`
- `.cursor/rules/code-style.mdc`
- `.cursor/rules/ui-style.mdc`

Do not duplicate their implementation details here.

## 3. Adaptive delegation

Use complexity-adaptive delegated engineering.

The primary agent owns orchestration, integration, verification, and delivery. Delegate independent work when it improves efficiency or quality.

- Prefer direct execution for trivial tasks.
- Use `investigator` for bounded exploration.
- Use `implementer` for isolated, approved changes.
- Use `reviewer` for independent correctness and regression review.
- Use at most three concurrent subagents, subject to the actual configuration.
- Do not require a fixed number of agents.
- Avoid duplicated investigations, unnecessary reports, and overlapping parallel edits.
- Require clear scope, deliverables, evidence, and stopping conditions for delegation.
- Keep the primary agent accountable for integration and final verification.

Specialized agent behavior is defined in `.codex/agents/*.toml`.

## 4. Milestones and approvals

For relevant features and updates, follow `plan-mod-update`.

Use three sequential milestones:

1. Investigation and planning.
2. Incremental implementation.
3. Verification and closure.

Present the initial scope and obtain explicit approval before Milestone 1.

Obtain explicit approval before starting each subsequent milestone and before declaring the update complete.

Approval covers only the agreed scope. In-scope corrections and tests do not require repeated approval.

Trivial changes and read-only questions may use a simplified workflow.

The skill owns the detailed process, task classification, delegation plan, acceptance criteria, and quality gates.

## 5. Autonomy and repository safety

Agents may improve code, tests, architecture, documentation, and rules when changes are evidence-based and within approved scope.

- Do not discard, overwrite, reset, or reformat unrelated user work.
- Do not perform destructive Git operations.
- Do not hide unrelated failures or unexpected runtime errors.
- Do not introduce unrelated dependencies or broad cosmetic refactors.
- Explain structural changes and provide regression coverage where applicable.
- Request direction for product-scope expansion, unresolved user-facing decisions, destructive actions, or external-system changes.
- Commits, pushes, releases, publishing, and external artifact copies require an explicit user request.

Do not confuse implementation completion with release readiness.

## 6. Context continuity

For multi-step, cross-package, high-risk, or likely multi-session work, maintain one record under `docs/tasks/` using `docs/tasks/TEMPLATE.md`.

- Record decisions, acceptance criteria, affected files, verification, blockers, and exact next action.
- Record significant delegation results without copying full transcripts.
- Checkpoint after meaningful phases, at milestone boundaries, before pauses or model changes, and near two-thirds of useful context consumption.
- On resume, read the record and verify Git state and relevant diffs.
- Reuse existing records; avoid duplicate plans and ledgers.
- Never store secrets, credentials, personal data, or large logs in task records.

## 7. Model and reasoning selection

Select the least expensive capable model and reasoning effort for each task, based on actual complexity and risk.

Only recommend models verified as available in the current environment.

Operational preferences, when available:

- Terra Medium: primary orchestration and integration.
- Luna: investigation, isolated implementation, and independent review.
- Sol: exceptional escalation requiring justification and explicit user approval.

Use low effort for mechanical work, medium for ordinary tasks, and high for difficult or risky tasks. Higher efforts require model support and concrete justification.

Do not escalate automatically. Keep a durable checkpoint before switching models.

State model/effort routing at the start when actionable.

Every final handoff must include a concise **Agent recommendation** explaining the available model and effort appropriate to the task, whether a lighter option would have sufficed, and whether escalation occurred. If availability is unknown, state that rather than guessing.

## 8. Documentation and language

Communicate with the user in Portuguese.

Keep in-game text, README, release notes, changelogs, and code comments in English, following `project-workflow.mdc`.

Use the official CurseForge page and official changelogs as the source of truth for released features. Use repository evidence for current implementation details.

Keep this file concise. Put technical invariants in `.cursor/rules/`, update procedures in the skill, specialized behavior in agent TOML files, and execution history in task records.

Create nested `AGENTS.md` files only for genuinely different, durable subtree workflows.
