# Context Continuity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a low-overhead durable task ledger and repair the local GitHub CLI path used by future development sessions.

**Architecture:** Put AI checkpoint behavior in `AGENTS.md`, the universal long-task requirement in the project workflow rule, and the human-readable protocol/template in `docs/tasks/`. Reuse the per-user GitHub CLI installation, preserving the existing binary if replacement is required, and point Git at an absolute helper path.

**Tech Stack:** Markdown, Cursor `.mdc` rules, Bash startup configuration, Git and GitHub CLI.

**Spec:** `docs/superpowers/specs/2026-10-26-context-continuity-design.md`

## Global Constraints

- Keep small and read-only demands free of mandatory task-ledger overhead.
- Do not copy transcripts, secrets, tokens, or large command outputs into task records.
- Keep model selection per demand and per phase; do not establish a permanent default.
- Preserve existing shell configuration and the existing GitHub CLI executable before any replacement.
- Do not commit, push, or publish without a separate explicit request.

## Review Focus

- A resumed task must expose its exact current state and next action without relying on chat history.
- A simple one-step change must not require a task record.
- A model/session switch must happen only after a durable checkpoint.
- Git credentials must never be copied into the repository or command output.
- Git must no longer invoke the missing `/usr/bin/gh` helper.

### Task 1: Add the task-ledger protocol

**Files:**
- Modify: `AGENTS.md`
- Modify: `.cursor/rules/project-workflow.mdc`
- Create: `docs/tasks/README.md`
- Create: `docs/tasks/TEMPLATE.md`
- Create: `docs/tasks/2026-10-26-context-continuity.md`

**Interfaces:**
- Consumes: repository governance and model-routing policy.
- Produces: one durable record format and explicit checkpoint/resume behavior.

- [x] **Step 1:** Add context continuity and model/session boundary instructions to `AGENTS.md`.
- [x] **Step 2:** Add the universal multi-step task-record requirement to `project-workflow.mdc`.
- [x] **Step 3:** Add the protocol, template, and first active task record under `docs/tasks/`.
- [x] **Step 4:** Validate references, frontmatter, concise scope, and whitespace.

### Task 2: Repair the per-user GitHub CLI route

**Files:**
- Preserve if replaced: `/home/davisr/.local/bin/gh`
- Modify: `/home/davisr/.bash_profile`
- Modify: `/home/davisr/.gitconfig`

**Interfaces:**
- Consumes: the existing authenticated GitHub account and verified portable CLI when required.
- Produces: `gh` on login-shell `PATH` and an absolute, working Git credential helper.

- [x] **Step 1:** Compare the existing CLI with the verified portable CLI and retain the existing file unless replacement is necessary.
- [x] **Step 2:** Add `~/.local/bin` once to login-shell `PATH` without disturbing interactive aliases.
- [x] **Step 3:** Replace the broken `/usr/bin/gh` Git credential helper with `/home/davisr/.local/bin/gh auth git-credential`.
- [x] **Step 4:** Authenticate only if the repaired CLI still reports an invalid session.
- [x] **Step 5:** Verify `gh`, authentication, Git helper configuration, and authenticated remote access.

### Task 3: Close the durable checkpoint

**Files:**
- Modify: `docs/tasks/2026-10-26-context-continuity.md`
- Modify: this plan

**Interfaces:**
- Consumes: verification evidence from Tasks 1 and 2.
- Produces: a completed, resumable example with exact evidence and no remaining hidden work.

- [x] **Step 1:** Record changed files, decisions, verification results, limitations, and next action in the task record.
- [x] **Step 2:** Mark completed plan steps and run `git diff --check` plus `git status --short`.
- [x] **Step 3:** Report the completed workflow and the next model/effort recommendation.
