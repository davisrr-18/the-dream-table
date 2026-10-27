# Agent Governance Design

## Intent

Give AI agents enough context and autonomy to improve The Dream Table reliably while using the least expensive model and reasoning effort that can still achieve the required quality.

The governance must distinguish agent operating instructions from project-wide technical rules. It must remain easy to evolve as the mod grows.

## Goals

- Create one root `AGENTS.md` as the operating manual for AI agents.
- Keep `.cursor/rules/*.mdc` as the canonical technical rules for the whole project.
- Let agents improve code, architecture, tests, documentation, rules, and agent instructions without waiting for approval when the work is in scope.
- Require evidence for structural changes and verification proportional to their risk.
- Route each task to the least costly model and reasoning effort that can execute it at the project's quality bar.
- Allow scoped `AGENTS.md` files to be introduced later when a subtree develops genuinely different operating needs.
- Preserve user work and keep commits, pushes, and releases opt-in.

## Non-goals

- Duplicating technical architecture or coding rules inside `AGENTS.md`.
- Creating nested `AGENTS.md` files before a concrete need exists.
- Freezing the current architecture or preventing agents from improving the rules.
- Automating commits, pushes, releases, or destructive repository operations.
- Treating a more expensive model as automatically better for every task.

## Anti-goals

- A large `AGENTS.md` that consumes context with implementation details already covered by rules.
- Conflicting instructions whose precedence is unclear.
- Cosmetic refactors presented as architectural improvements.
- Lowering verification standards to save tokens or time.
- Using maximum reasoning for routine edits with clear acceptance criteria.

## Instruction hierarchy

For repository-local guidance, agents should apply this order:

1. The user's current request and explicit decisions.
2. The nearest applicable `AGENTS.md`, from repository root toward the file being changed.
3. Applicable `.cursor/rules/*.mdc` rules.
4. Project documentation and established code patterns.

`AGENTS.md` controls how an AI agent works. `.cursor/rules` controls what valid project work must look like. An agent instruction may explain how to discover and verify a rule, but must not silently override a technical invariant. If guidance conflicts, the agent must identify the conflict and improve the governing files as part of the task when doing so is safe and relevant.

## Root `AGENTS.md`

The root file should be concise and operational. It will cover:

- language and communication expectations;
- mandatory discovery of applicable rules and nearby instructions;
- preservation of existing user changes in a dirty worktree;
- a workflow of inspect, define acceptance criteria, implement, verify, and report;
- proportional testing and explicit disclosure of anything not tested in Minecraft;
- autonomy to improve architecture and governance when the benefit is concrete;
- requirements for structural changes: explain the benefit, update canonical rules, add or update tests, and check compatibility with saved worlds and network data;
- prohibition on automatic commit, push, release, destructive Git operations, and unrelated cleanup;
- model and reasoning routing, including a mandatory routing indication in every final handoff;
- criteria for adding a nested `AGENTS.md` later.

## Project rules

The existing focused rules remain canonical:

- `project-workflow.mdc`: toolchain, builds, tests, versions, releases, and language;
- `architecture.mdc`: ownership, package responsibilities, synchronization, persistence, and extension points;
- `build-safety.mdc`: safety and correctness invariants for building, repair, dismantling, loading, permissions, and saved state;
- `code-style.mdc`: Java structure and naming;
- `ui-style.mdc`: menu, clipboard, and hologram behavior.

Rules may be split, renamed, or refined when that improves discoverability or scope. Each rule should keep one concern, precise file globs when scoped, actionable wording, and examples only where they prevent ambiguity. Agents may update rules alongside code whenever a technical invariant changes or a durable lesson is discovered.

## Autonomy and change control

Agents may make any in-scope change that demonstrably improves the project or its development flow. Autonomy is bounded by evidence:

- diagnose before changing behavior;
- preserve compatibility unless the task explicitly permits a break;
- add regression coverage for logic and persistence changes;
- update the relevant rule when an architectural invariant changes;
- avoid unrelated rewrites;
- do not overwrite or discard user changes;
- report meaningful assumptions and unverified game behavior.

An agent does not need approval for an in-scope refactor or rule improvement. It does need new direction when the proposed work materially expands the product scope, introduces a user-facing product decision not implied by the request, or requires destructive/external actions.

## Model and reasoning routing

The routing strategy is quality-constrained efficiency: classify every demand independently, use the lightest capable combination, then escalate when complexity, risk, ambiguity, or failed verification justifies it. No model or effort level is a permanent project default.

Model selection happens after the agent understands the request and inspects enough project context to estimate its difficulty. A single demand may use different combinations for diagnosis, implementation, visual work, and review. Previous routing does not carry over automatically to the next demand.

Recommendations are restricted to agents/models exposed by the current project environment. A model documented externally or available in another product or workspace is out of scope until it appears in this project's visible model list. If availability cannot be confirmed, the agent must say so and recommend only from the confirmed list.

### Recommended baseline

- **Lightest available agent, low:** repository search, classification, mechanical edits, simple documentation, formatting, and narrow changes with exact instructions.
- **Efficient available agent, medium:** well-scoped implementation with clear patterns and fast tests.
- **Visual/computer-use capable available agent, medium:** scoped work that depends on those capabilities.
- **Strong general coding agent, medium:** normal feature work, coordinated changes across packages, test design, and non-trivial refactoring.
- **Strong general coding agent, high or xhigh:** difficult debugging, persistence/network migrations, concurrency or lifecycle problems, large refactors, and reviews involving conflicting evidence.
- **Most capable available agent, medium or higher:** ambiguous architecture, high-impact decisions, or rare highest-risk work after a lighter available option is insufficient.

### Escalation signals

Increase reasoning effort before changing model when the current model is otherwise appropriate. Escalate the model when one or more of these apply:

- requirements remain ambiguous after inspecting project evidence;
- the change crosses several ownership boundaries;
- saved-world compatibility, networking, permissions, or duplication/loss of items is at risk;
- tests fail for reasons not explained by the first diagnosis;
- two plausible designs have materially different long-term consequences;
- review finds a correctness issue that the implementer repeatedly missed.

Do not escalate merely because a task touches many files when the transformation is mechanical and verifiable.

### Verification of routing

Treat routing as an empirical policy. Compare representative tasks using fixed acceptance criteria, including regression tests, review/repair time, latency, and usage. Keep the cheapest configuration that consistently meets the quality bar and revise the matrix as models and the project evolve.

At the beginning of each demand, the agent should recommend the model and reasoning effort when the choice is visible or actionable by Davi. The recommendation must briefly state why that combination fits the current demand and identify any condition that would cause escalation.

At the end of every demand, the final handoff must include a concise **Agent recommendation** stating an agent/model available to the current project and the reasoning effort appropriate for performing that completed task. It must mention whether a lighter available combination would have been insufficient or whether escalation became necessary. This closing indication is mandatory even when no files were changed.

## Nested `AGENTS.md` policy

Start with only the root file. Add a nested file when a subtree has durable agent workflow needs that are not technical rules and would otherwise burden every task. Examples could include a special validation workflow for schematic readers or manual visual checks for rendering code.

A nested file must:

- add only subtree-specific operating guidance;
- avoid repeating the root file or `.cursor/rules`;
- state its scope through its location and concise content;
- be removed or merged when the special workflow no longer exists.

## Verification

Governance changes are documentation changes, so initial verification consists of:

- checking that every referenced path exists;
- checking that responsibilities are not duplicated or contradictory;
- confirming the root instructions are concise enough to load for every task;
- reviewing rule frontmatter and globs;
- running repository tests only if implementation or build configuration is also changed.

Future implementation tasks continue to use `./gradlew compileJava` after every code change and `./gradlew test` for logic changes, following `project-workflow.mdc`.

## Approaches considered

### One comprehensive root `AGENTS.md`

Simple to find, but it would duplicate project rules, grow continuously, and spend context on irrelevant details.

### Lean root plus scoped nested `AGENTS.md` files — selected

Keeps universal agent behavior visible while allowing future subtrees to add operating guidance only when necessary. Technical truth stays in focused project rules. This provides the best balance of clarity, context efficiency, and maintainability.

### Root `AGENTS.md` plus extensive runbooks under `docs/`

Useful for long human-facing procedures, but creates extra discovery hops for ordinary work and is unnecessary at the project's current size. Runbooks can still be added later for genuinely long procedures.

## Decisions left to implementation

- Exact wording and final length of the root `AGENTS.md`.
- Whether any existing rule should be split during the first cleanup; splitting is justified only by scope or discoverability, not line count alone.
- Which representative tasks will form the first routing evaluation set once enough future work has been completed to compare outcomes.
