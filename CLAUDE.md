# The Dream Table — Claude Code Guide

Minecraft mod (Fabric, NeoForge and Forge, Java 21) built around a table that previews and builds schematics and farms. Published on CurseForge, with one branch per Minecraft line.

Only what is specific to this project is written here. The general workflow comes from the user's global `~/.claude/` (`CLAUDE.md`, the `plan-update` skill, the agents); where this file differs, this file wins.

## Rules and commands

Read the relevant technical rules before changing code; Claude Code does not load them by itself:

- `.cursor/rules/project-workflow.mdc`: planning record, stack, build, tests, CI, `javap` lookup, versions, releases, changelog, test handoff, language.
- `.cursor/rules/architecture.mdc`
- `.cursor/rules/build-safety.mdc`
- `.cursor/rules/code-style.mdc`
- `.cursor/rules/ui-style.mdc`

Precedence: current user request, this file, the technical rules, project documentation. Never silently override a technical invariant; point out the conflict and resolve it within the approved scope. Update the rule when a durable invariant changes.

- After code changes: `./gradlew compileJava`, plus the tests `project-workflow.mdc` requires. Full check: `./gradlew build`.
- For behavior changes, add or identify regression coverage before implementing when feasible.
- Preserve saved-world, networking, permissions and persistence compatibility.
- Inspect the actual code and the pinned Minecraft/loader artifacts instead of guessing APIs.
- Before changing anything, confirm the Git root, branch and worktree.
- Code, tests, architecture, docs and rules may be improved when the change is evidence-based and inside the approved scope.
- Released features: the official CurseForge page and changelogs are the source of truth. Current implementation: the repository.
- In-game text, README, release notes, changelogs and code comments are in English.

## Validation (standing user decisions, 2026-10-28 and 2026-10-29)

The user tests in a Minecraft client only once, on the final release-ready build of an update. Intermediate milestones close on automated verification (`./gradlew build`, tests) and independent review; the user runs the dev dedicated-server smokes locally (CI also runs `server-smoke`). Each milestone adds its manual checks to the update's consolidated in-game checklist in the task record instead of waiting for them. Fixes found in that final pass follow the normal workflow. A build handed over for testing goes to the main checkout's `build/release/<loader>/` folders (`project-workflow.mdc`, "Test handoff"). Report what was not tested in game. Implementation complete is not release ready.

## Git, branches and releases

- Commits and pushes are automatic (standing user authorization, 2026-10-28): after each finished and verified task, phase or milestone, commit all of its changes and push, without waiting to be asked. Never commit failing builds, secrets or unrelated user work.
- Commits land on the branch of their Minecraft line (`mc/1.21.1`, `mc/1.20.1`, `mc/1.21.11`); `main` holds only documentation, tools and the feedback Worker (standing user decision, 2026-11-23). Work in a worktree branch is integrated by fast-forwarding the line's branch and pushing it. If it cannot fast-forward, rebase the unpushed work onto its `origin` branch and stop for direction on conflicts. Never force-push or rewrite published history.
- Repository hygiene (2026-11-23): GitHub and the local clone keep only `main`, the maintained line branches and one worktree per line (`v<minecraft>/`). Delete a task or subagent branch and worktree as soon as its work is integrated and pushed (`git worktree remove`, never `--force` over uncommitted work), at the end of each milestone. Before deleting a branch, confirm it has no commit without an equivalent in a line or a tag (`git log --cherry-pick --right-only`) and tell the user what was deleted. Tags and releases are never deleted. A new Minecraft line adds its branch to this list. No pull requests or feature branches on GitHub unless the user asks.
- Apart from that cleanup, no destructive Git operations (`reset --hard`, `clean`, discarding changes); never discard, overwrite or reformat unrelated user work.
- `CHANGELOG.md` always describes the version in development: replace it when a version starts and update it in the same commit as every player-facing change (it is the text Davi posts on CurseForge).
- `gh release` only after every adjustment of the update is done and the user says they will post it on CurseForge; each line releases on its own, tagged `v<version>-mc<minecraft>` (`project-workflow.mdc`). Until then, keep building and committing.
- Publishing and external artifact copies require an explicit user request. Ask before expanding product scope, deciding open user-facing questions, destructive actions or changes to external systems.
- Do not hide unrelated failures or unexpected runtime errors. No unrelated dependencies or broad cosmetic refactors.

## Delegation (overrides the global defaults)

- Delegation is the default for substantial work, in every session including resumed ones: for medium or higher complexity actively prefer real subagents, and do not do it all in the main agent just for convenience. Trivial tasks are done directly. Do not spawn when the evidence is already there, and keep reports short.
- Every subagent (investigator, implementer, reviewer and any general-purpose helper) uses Haiku 5.5 at high effort (standing user decision, 2026-10-29). Never escalate a subagent to another model or effort; narrow the scope and verify more instead. Do not pass `model` or `effort` on a delegation call, except for helpers without a definition file in `.claude/agents/` (general-purpose, Explore, Plan) or when the running agent or its hook requires them: then pass exactly `haiku` and `high`.
- The main agent orchestrates and stays accountable for architecture, integration, verification and delivery. The reviewer does not run builds or tests (the main agent does). No nested delegation unless it clearly helps.
- Default to three concurrent subagents; up to five only when the work splits into independent units by subsystem with no shared files. Never more than three editing implementers, and only on disjoint files. Basis: `docs/tasks/subagent-scaling-test/README.md`.

## Milestones and task records

- Relevant features and updates follow the `plan-update` skill: three milestones, with explicit approval before the first, before each next one and before declaring the update complete.
- Multi-step, cross-package, high-risk or multi-session work keeps one record under `docs/tasks/` (`TEMPLATE.md`), with decisions, acceptance criteria, affected files, verification, blockers, exact next action, milestone approval state and the delegated agents; no secrets or large logs, and reuse the record instead of creating a duplicate. Checkpoint after each meaningful phase, before pauses and near two-thirds of the context. Read the active record before starting; on resume, check it against Git.
- Each milestone (and large phase) runs in its own chat: after closing one, do not start the next; give a short resume phrase with the recommended model and effort. Inside a milestone, suggest a new chat at a phase boundary when the conversation grew long or changed topic.
- Keep this file concise: technical invariants in `.cursor/rules/`, history in task records; nested `CLAUDE.md` only for a genuinely different subtree.

## Primary session model

- Overrides the global approval rule for effort: Sonnet 5.5 at medium effort by default. High for difficult multi-agent integration, subtle bugs (networking, persistence, handshake) or milestone planning. Opus 5.5 at medium only for first-milestone planning of large updates, architectural decisions or problems Sonnet could not solve. Pick effort by actual complexity and risk; lower it for mechanical or low-risk work.
- Only the user changes it (`/model`, `/effort`): recommend at the start of the task or milestone, only models verified as available, state why, record it in the task record and checkpoint before switching.
- Every final handoff has a short **Agent recommendation**: model and effort that fit, whether a lighter option would have been enough, and whether escalation happened.
