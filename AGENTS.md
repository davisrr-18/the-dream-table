
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

## 3. Permanent multi-agent engineering policy

Multi-agent delegation is the default strategy for substantial software engineering tasks whenever supported by the active environment.

Apply this policy automatically in every development session, including new conversations and resumed milestones. The user does not need to repeat a global delegation prompt.

### 3.1. Delegation requirements

The primary agent acts as orchestrator and remains responsible for architecture, integration, verification, and final delivery.

Before executing a relevant task:

1. Assess complexity, risks, dependencies, and available agent capabilities.
2. Identify investigation, implementation, and review activities suitable for delegation.
3. Select the smallest capable combination of specialized agents.
4. Invoke the selected subagents through actual delegation tools.
5. Consolidate their evidence and verify the integrated result.

For medium, high, or critical complexity tasks, actively prefer real subagent delegation when meaningful independent work exists.

Do not perform all substantial work in the orchestrator merely for convenience when useful and safe delegation is available.

For significant investigations, prefer delegating a bounded investigation to an `investigator`.

For isolated implementation work, prefer an `implementer` when the task can be safely separated.

For significant behavior changes or regression risks, prefer an independent `reviewer`.

Delegation is adaptive, not unconditional:

- Trivial tasks may be completed directly.
- Do not spawn agents merely to reach a fixed count.
- Do not delegate work whose coordination overhead exceeds its likely benefit.
- Use at most three concurrent subagents, subject to the actual environment configuration.
- Parallelize independent tasks only.
- Execute dependent activities sequentially.
- Avoid overlapping parallel edits and duplicated investigations.
- Do not repeat completed investigations without a concrete verification need.
- Avoid nested delegation (subagents spawning subagents) unless it brings a clear benefit.

### 3.2. Specialized agents

Use the configured agents or their available equivalents:

**Investigator**
- Performs bounded, read-only investigation.
- Identifies relevant code, dependencies, invariants, and risks.
- Returns concise findings with evidence and file references.
- Does not modify files.

**Implementer**
- Performs isolated implementation within approved scope.
- Preserves existing code, compatibility, and user changes.
- Adds or adjusts appropriate regression tests.
- Reports modified files, verification results, and limitations.

**Reviewer**
- Independently reviews changes and affected behavior.
- Prioritizes correctness, regressions, compatibility, and missing tests.
- Reports concrete findings with severity and evidence.
- Does not modify files during independent review.

Each delegated task must define:

- Objective and bounded scope.
- Relevant files or subsystems.
- Read-only or editing responsibility.
- Dependencies and possible conflicts.
- Acceptance criteria and expected verification.
- Required deliverables and stopping condition.

The reviewer should be different from the implementer whenever practical.

Specialized agent behavior is defined per tool:

- Codex: `.codex/agents/*.toml`
- Claude Code: `.claude/agents/*.md`
- Other environments: equivalent supported agent configuration.

### 3.3. Real execution and transparency

Delegation must be real, not simulated.

- Use actual subagent tools when delegation is selected.
- Never claim a subagent was invoked unless it actually ran.
- If delegation is unsupported or fails, report the limitation.
- Do not silently replace a planned independent review with self-review.
- Do not assume an agent is available merely because its configuration file exists.
- Verify subagent outputs before integrating their conclusions.

At each meaningful stage, report briefly:

- Which agents actually ran.
- Their assigned responsibilities.
- Important findings or changes.
- Verification evidence and remaining risks.

The orchestrator is accountable for the final integrated result.

### 3.4. Resource efficiency

Optimize for validated work per unit of available resources, without sacrificing correctness.

- Prefer the least expensive capable model and reasoning effort.
- Preserve configured agent models and effort levels.
- Keep subagent context focused and reports concise.
- Reuse existing discoveries and checkpoints.
- Avoid unnecessary repeated tool calls and redundant reviews.
- Do not rerun tests that already passed unless the relevant code changed or there is a concrete reason.
- Do not bypass essential tests to reduce usage.
- Do not claim token or quota savings without actual measurements.
- Request explicit approval before exceptional model escalation.

The detailed procedure belongs to the applicable project skill. Do not duplicate its full contents here.

## 4. Milestones and approvals

For relevant features and updates, follow `plan-mod-update`.

Use three sequential milestones:

1. Investigation and planning.
2. Incremental implementation and integration.
3. Independent review, verification, and closure.

Present the initial scope and obtain explicit approval before Milestone 1.

Obtain explicit approval before starting each subsequent milestone and before declaring the update complete.

Approval covers only the agreed scope. In-scope corrections and tests do not require repeated approval.

Do not use delegation to bypass milestone approvals or expand the approved scope.

A task or milestone may be declared complete only when:

- The approved scope is implemented.
- Acceptance criteria are verified.
- Relevant tests have known results.
- Required integration and review are done.
- Limitations and pending items are documented.
- Required approvals were respected.

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
- Record actual delegated agents and significant results without copying full transcripts.
- Record milestone approval state explicitly.
- Checkpoint after meaningful phases, at milestone boundaries, before pauses or model changes, and near two-thirds of useful context consumption.
- On resume, read the record and verify Git state and relevant diffs.
- Recover the next pending milestone without relying on previous chat history.
- Reapply this permanent multi-agent policy when resuming.
- Reuse existing records; avoid duplicate plans and ledgers.
- Never store secrets, credentials, personal data, or large logs in task records.

### 6.1. Session per milestone

For performance and cost, each milestone (and each large phase or task inside one) runs in its own chat session.

- At the end of every phase, milestone, or task, record explicitly in the task record what changed in the plan: scope moved in or out, re-ordered steps, narrowed or adjusted exit criteria, and new decisions, each with its reason.
- Then record the exact next action, so a fresh session can resume from the record and Git state alone.
- After closing a milestone, do not start the next one in the same chat. Recommend opening a new chat and give the user a short resume phrase (e.g. "retomar o Marco 4 da v1.0.0").
- Inside a milestone, recommend a new chat at a phase boundary when the conversation has grown long or changed topic several times.

## 7. Model and reasoning selection

Select the least expensive capable model and reasoning effort for each task, based on actual complexity and risk.

Only recommend models verified as available in the current environment.

Operational preferences, when available:

- Primary agent: orchestration and integration, at medium effort.
- Investigation, isolated implementation, and independent review: delegate to the matching subagent at its configured model and effort.
- Escalation to a stronger model or higher effort: exceptional, requires justification and explicit user approval.

Use low effort for mechanical work, medium for ordinary tasks, and high for difficult or risky tasks. Higher efforts require model support and concrete justification.

Do not escalate automatically. Keep a durable checkpoint before switching models.

State model/effort routing at the start when actionable.

Every final handoff must include a concise **Agent recommendation** explaining the available model and effort appropriate to the task, whether a lighter option would have sufficed, and whether escalation occurred. If availability is unknown, state that rather than guessing.

## 8. Documentation and language

Communicate with the user in Portuguese.

Keep in-game text, README, release notes, changelogs, and code comments in English, following `project-workflow.mdc`.

Use the official CurseForge page and official changelogs as the source of truth for released features. Use repository evidence for current implementation details.

Keep this file concise. Put technical invariants in `.cursor/rules/`, update procedures in the skill, specialized behavior in agent configuration files, and execution history in task records.

Create nested `AGENTS.md` files only for genuinely different, durable subtree workflows.