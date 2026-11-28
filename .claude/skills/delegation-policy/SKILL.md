---
name: delegation-policy
description: Delegation steps, agent roles, task contract and reporting for this project. Use before delegating any work to a subagent.
---

Moved from `CLAUDE.md` section 3; section numbers below refer to `CLAUDE.md`.

# Multi-agent engineering policy

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
- Default to three concurrent subagents in total, of any type. Use up to five only when the work splits into independent units by subsystem with no shared files. Never run more than three editing `implementer` subagents at once, and only on disjoint files. Subject to the actual environment configuration. Basis: `docs/tasks/subagent-scaling-test/README.md` (measured on read-only investigators: three matched five on confirmed findings at lower cost, and eight or more found fewer with more false positives).
- Split work into independent, well-scoped units (by subsystem, package, or file set) so subagents can run in parallel without overlapping edits.
- Parallelize independent tasks only.
- Execute dependent activities sequentially.
- Avoid overlapping parallel edits and duplicated investigations.
- Do not repeat completed investigations without a concrete verification need.
- Avoid nested delegation (subagents spawning subagents) unless it brings a clear benefit.

### 3.2. Specialized agents

Use the agents configured in `.claude/agents/*.md`:

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

Specialized agent behavior (model, effort, tools) is defined in `.claude/agents/*.md`.

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
- Use the configured agent models and effort levels (Haiku 5.5 at high, section 7); do not override them on a delegation call.
- Keep subagent context focused and reports concise.
- Reuse existing discoveries and checkpoints.
- Avoid unnecessary repeated tool calls and redundant reviews.
- Do not rerun tests that already passed unless the relevant code changed or there is a concrete reason.
- Do not bypass essential tests to reduce usage.
- Do not claim token or quota savings without actual measurements.
- Recommend changes to the primary session's model/effort under the routing rules in section 7, reporting each one.

The detailed procedure belongs to the applicable project skill. Do not duplicate its full contents here.
