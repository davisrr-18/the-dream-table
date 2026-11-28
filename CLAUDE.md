# The Dream Table — Claude Code Guide

This file defines permanent working rules for Claude Code, the only AI agent tool used in this project. Technical invariants belong to `.cursor/rules/*.mdc`; detailed update planning belongs to the `plan-mod-update` skill in `.claude/skills/plan-mod-update/SKILL.md`.

## 1. Before starting

- Understand the user's request, existing decisions, scope, and acceptance criteria.
- Read the nearest applicable `CLAUDE.md` and relevant `.cursor/rules/*.mdc` files.
- Check `git status --short` and preserve all existing changes.
- Consult active records in `docs/tasks/` before repeating investigations.
- Inspect actual code and pinned Minecraft/Fabric artifacts instead of guessing APIs.
- Confirm the active Git root, branch, and worktree before making changes.

Repository guidance follows this order:

1. Current user request and explicit decisions.
2. Nearest applicable `CLAUDE.md`.
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

Multi-agent delegation is the default strategy for substantial software engineering tasks whenever supported by the active environment. Apply it in every session, including new and resumed ones, without a new prompt from the user.

**Before delegating, load the `delegation-policy` skill** (`.claude/skills/delegation-policy/SKILL.md`): delegation steps, agent roles, task contract, reporting and resource efficiency. These rules always apply:

- The primary agent is the orchestrator and stays accountable for architecture, integration, verification and delivery.
- Delegation is adaptive: trivial tasks may be done directly; for medium or higher complexity with independent work, prefer real subagents (`investigator`, `implementer`, `reviewer` in `.claude/agents/*.md`).
- Default to three concurrent subagents in total, of any type. Use up to five only when the work splits into independent units by subsystem with no shared files. Never run more than three editing `implementer` subagents at once, and only on disjoint files. Subject to the actual environment configuration. Basis: `docs/tasks/subagent-scaling-test/README.md` (measured on read-only investigators: three matched five on confirmed findings at lower cost, and eight or more found fewer with more false positives).
- Parallelize only independent work without overlapping files. The reviewer differs from the implementer whenever practical.
- Delegation is real: never claim a subagent ran unless it did, report failures, never replace a planned independent review with self-review, and verify subagent output.
- Never set a `model` or `effort` override on a delegation call (section 7).

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

In-game testing (standing user decision, 2026-10-28): the user tests in a Minecraft client only once, on the final release-ready build of an update. Intermediate milestones close on automated verification (`./gradlew build`, tests) and independent review; the user runs the dev dedicated-server smokes (standing user decision, 2026-10-29). Whenever a build is handed over for testing, its jars go in the main checkout's `build/release/<loader>/` folders (`project-workflow.mdc`, "Test handoff"). Each milestone adds its manual checks to the update's consolidated in-game checklist in the task record instead of waiting for them. Fixes found in that final pass follow the normal workflow.

The skill owns the detailed process, task classification, delegation plan, acceptance criteria, and quality gates.

## 5. Autonomy and repository safety

Agents may improve code, tests, architecture, documentation, and rules when changes are evidence-based and within approved scope.

- Do not discard, overwrite, reset, or reformat unrelated user work.
- Do not perform destructive Git operations.
- Do not hide unrelated failures or unexpected runtime errors.
- Do not introduce unrelated dependencies or broad cosmetic refactors.
- Explain structural changes and provide regression coverage where applicable.
- Request direction for product-scope expansion, unresolved user-facing decisions, destructive actions, or external-system changes.
- Releases, publishing, and external artifact copies require an explicit user request.
- Changelog (standing user decision, 2026-10-29): `CHANGELOG.md` always describes the version in development. As soon as a new version starts, replace it with that version's changelog, and update it in the same commit as every player-facing change, following `project-workflow.mdc`. It is the text Davi posts on CurseForge.
- GitHub release (standing user decision, 2026-10-28; per line since 2026-11-23): run `gh release` only after every adjustment of the update is done and the user says they will post it on CurseForge. Each Minecraft line releases on its own, tagged `v<version>-mc<minecraft>`, as `project-workflow.mdc` describes. Until then, keep building and committing.
- Commits and pushes are automatic (standing user authorization, 2026-10-28): at the end of every finished task, phase, or milestone, after its verification passes, commit all of its changes and push them. Never commit failing builds, secrets, or unrelated user work.
- Commit messages never carry a `Co-Authored-By` or any other Claude attribution line (standing user decision, 2026-11-05); this overrides the default attribution reminder.
- Commits and pushes land on the branch of the Minecraft line they belong to (`mc/1.21.1`, `mc/1.20.1`, `mc/1.21.11`); `main` holds only documentation, tools and the feedback Worker (standing user decision, 2026-11-23, replacing the 2026-10-28 rule that everything lands on `main`). Work may happen in a worktree branch, but integrate it by fast-forwarding the line's branch and pushing it; never force-push or rewrite history. If it cannot fast-forward, rebase the unpushed work onto its `origin` branch and stop for direction on conflicts.
- Repository hygiene (standing user decision, 2026-11-23): GitHub and the local clone keep only `main`, the maintained line branches (`mc/<minecraft>`) and one worktree per line (`v<minecraft>/`). A branch or worktree made for a task or a subagent is deleted as soon as its work is integrated into its line and pushed; remove subagent worktrees with `git worktree remove` (never `--force` over uncommitted work) at the end of each milestone. Before deleting a branch, confirm it has no commit without an equivalent in a line or a tag (`git log --cherry-pick --right-only`), and tell the user what was deleted. Tags and releases are never deleted. A new Minecraft line adds its branch to this list. Do not open pull requests or leave feature branches on GitHub unless the user asks.

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

- Primary agent: orchestration and integration on Sonnet 5.5 at medium effort by default (Claude Code main session); raise to high for difficult multi-agent integration, subtle bugs (networking, persistence, handshake), or milestone planning.
- Escalate the primary session to Opus 5.5 at medium effort only for Milestone 1 planning of large updates, architectural decisions, or problems Sonnet could not resolve.
- Subagents (investigator, implementer, reviewer, and any general-purpose helper) use only Haiku 5.5 at high effort (standing user decision, 2026-10-29). Never escalate a subagent to Sonnet, Opus or another model, and never set a `model` or `effort` override on a delegation call; compensate with narrower scopes, explicit acceptance criteria and more careful verification by the orchestrator. Higher models and efforts apply only to the primary session, which the user changes with `/model` and `/effort`.
- Investigation, isolated implementation, and independent review: delegate to the matching subagent at its configured model and effort.
- Adaptive routing (standing user authorization, 2026-10-28, narrowed 2026-10-29): subagents stay fixed at Haiku 5.5 high; the primary session's model and effort change only through the user.

Use low effort for mechanical work, medium for ordinary tasks, and high for difficult or risky tasks. Higher efforts require model support and concrete justification.

Adaptive routing rules:

- Subagents: never set `model`/`effort` on the delegation call; the `.claude/agents/*.md` defaults (Haiku 5.5, high) always apply.
- Primary session: its model and effort can only be changed by the user (`/model`, `/effort`). Recommend the change at the start of the task or milestone, and include the recommended model/effort in every resume phrase for a new chat.
- Lower the level for mechanical or low-risk work; raise it only for concrete complexity or risk, never by default.
- State each change and its reason in the stage report and record it in the task record.
- Keep a durable checkpoint before switching models.

State model/effort routing at the start when actionable.

Every final handoff must include a concise **Agent recommendation** explaining the available model and effort appropriate to the task, whether a lighter option would have sufficed, and whether escalation occurred. If availability is unknown, state that rather than guessing.

## 8. Documentation and language

Communicate with the user in Portuguese.

Keep in-game text, README, release notes, changelogs, and code comments in English, following `project-workflow.mdc`.

Use the official CurseForge page and official changelogs as the source of truth for released features. Use repository evidence for current implementation details.

Keep this file concise. Put technical invariants in `.cursor/rules/`, update procedures in the skill, specialized behavior in agent configuration files, and execution history in task records.

Create nested `CLAUDE.md` files only for genuinely different, durable subtree workflows.