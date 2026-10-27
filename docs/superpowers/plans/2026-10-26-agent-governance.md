# Agent Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the approved separation between AI-agent operating guidance in `AGENTS.md` and project-wide technical rules in `.cursor/rules`.

**Architecture:** Add one concise root `AGENTS.md` that governs agent workflow, autonomy, verification, preservation of user changes, and per-demand model/effort reporting. Keep existing `.cursor/rules/*.mdc` as the technical source of truth and make only targeted wording/frontmatter repairs where needed. Validate references, duplication, and formatting without changing product code.

**Tech Stack:** Markdown, Cursor `.mdc` rules, Git read-only inspection.

**Spec:** `docs/superpowers/specs/2026-10-26-agent-governance-design.md`

## Global Constraints

- Talk to Davi in Portuguese; project text and rules remain clear and consistent with existing repository language.
- Do not overwrite or discard unrelated user changes in the dirty worktree.
- Do not commit, push, release, or run destructive Git operations.
- Keep `AGENTS.md` operational and avoid duplicating technical invariants already in `.cursor/rules`.
- Restrict model recommendations to agents/models exposed by the current project environment.
- End the final handoff with the agent/model and reasoning-effort recommendation for this demand.

## Review Focus

- Precedence between user request, nearest `AGENTS.md`, and `.cursor/rules`: validate the hierarchy is explicit and non-contradictory.
- Model routing per demand rather than a permanent default: validate the final handoff requirement and escalation criteria.
- Dirty worktree safety: validate that instructions preserve existing user changes and prohibit destructive cleanup.
- Technical-rule ownership: validate that architecture, build safety, workflow, code style, and UI rules remain in `.cursor/rules`.
- Future nested instructions: validate that nested `AGENTS.md` files are introduced only for scoped agent workflow differences.

### Task 1: Create the root agent operating manual

**Files:**
- Create: `AGENTS.md`

**Interfaces:**
- Consumes: approved governance spec and existing `.cursor/rules/*.mdc`.
- Produces: a concise root instruction file covering discovery, precedence, workflow, autonomy, verification, safety, routing, and mandatory final reporting.

- [x] **Step 1: Write `AGENTS.md`** with explicit sections for scope, instruction precedence, mandatory discovery, workflow, autonomy, validation, worktree safety, language, model/effort routing per demand, and final handoff reporting.
- [x] **Step 2: Check referenced paths and commands** against the repository and existing rules.
- [x] **Step 3: Review for duplication** and remove technical implementation details that belong in `.cursor/rules`.

### Task 2: Audit and minimally align project rules

**Files:**
- Modify: `.cursor/rules/project-workflow.mdc` only if its agent-facing wording conflicts with the new separation.
- Modify: other `.cursor/rules/*.mdc` only if a concrete contradiction or invalid scope is found.

**Interfaces:**
- Consumes: root `AGENTS.md` from Task 1.
- Produces: rules that remain project-wide technical truth, with valid frontmatter and no duplicated agent workflow policy.

- [x] **Step 1: Inspect every rule frontmatter** and confirm `description`, `globs`, and `alwaysApply` match the rule's actual scope.
- [x] **Step 2: Search for agent-only workflow instructions** in rules and move or rewrite only concrete conflicts; do not reformat unrelated content.
- [x] **Step 3: Confirm each rule remains focused** and under the repository's concise-rule standard.

### Task 3: Validate the governance change

**Files:**
- Test: repository documentation and rule inspection commands; no product test file is required.

**Interfaces:**
- Consumes: `AGENTS.md`, `.cursor/rules/*.mdc`, and the approved spec.
- Produces: verified documentation governance with an auditable final report.

- [x] **Step 1: Run `git diff --check`** for all governance files; expected result is no whitespace errors.
- [x] **Step 2: Verify every path referenced by `AGENTS.md`** exists, including the rules and required Gradle commands.
- [x] **Step 3: Search for contradictions** between `AGENTS.md` and `.cursor/rules`; resolve any found contradiction before handoff.
- [x] **Step 4: Report changed files, checks run, limitations, and the agent/model plus effort recommendation for this demand.**
