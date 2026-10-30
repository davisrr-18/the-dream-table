# Subagent scaling test (5 / 8 / 10 / 12 Haiku investigators)

- Status: Planned (not run yet)
- Started: 2026-10-29
- Goal: decide the right concurrent-investigator limit by cost and finding quality, not by guess.

## Fixed task (identical in all four runs)

Read-only audit of `common/src/main/java` (about 188 files, 21.8k lines). Each investigator receives one shard and answers:

> Find, in your files only: (1) data received from the network (packet payloads, client requests) used before validation or bounds checks; (2) per-tick or per-frame allocations and unbounded collections; (3) saved-data (NBT/codec) reads that could throw or corrupt on old or malformed data. Report at most 8 findings, each as `file:line | category | one-sentence defect | severity (high/med/low)`. Read-only. No fixes. If nothing is found for a category, say "none".

Shards come from `shard.py N`, line-balanced and deterministic. Note: the greedy split mixes packages, so cross-file context is partial in every run; that is the same handicap for all four, but real tasks should be sharded by subsystem.

## Setup per run

1. Open a **fresh chat** per run (Sonnet 5.5, medium), on `main`, clean `git status`. Same state every time.
2. Run `/usage` and note the 5h and weekly percentages (A).
3. Prompt: "Run docs/tasks/subagent-scaling-test: N=<5|8|10|12>. Use `shard.py N`, launch exactly N `investigator` subagents (model haiku, effort high) in parallel, one shard each, then merge, dedupe, and rank the findings. Do not edit any file. Do not verify findings yet."
4. When it finishes, run `/usage` again (B). Cost = B - A. Note wall-clock time.
5. Save the merged report outside the repo (do not commit it), one file per N.

Run order: 5, 8, 10, 12, with the same gap of time between them if possible, so quota resets do not skew the numbers.

## Metrics to record

| Metric | 5 | 8 | 10 | 12 |
|---|---|---|---|---|
| Quota used (B - A, 5h %) | | | | |
| Quota used (weekly %) | | | | |
| Wall-clock time | | | | |
| Raw findings (before dedupe) | | | | |
| Unique findings after merge | | | | |
| Confirmed real (spot-check, see below) | | | | |
| False positives | | | | |
| Duplicates between shards | | | | |
| Files a shard failed to finish | | | | |

## Quality check (shared ground truth)

Union all four merged reports. Take every unique finding rated high or med and verify it once by reading the cited code (one reviewer session, Sonnet medium, read-only). That verified set is the ground truth. For each run compute recall (confirmed found / total confirmed) and precision (confirmed / reported).

## Decision rule

Pick the smallest N whose recall is within about 10 percentage points of the best run and whose quota cost is not clearly higher. If cost rises with N and recall does not, keep 5 (or lower the read-only exception from 8). If 12 wins on both, raise the limit.

## Caveats

- `/usage` percentages are coarse; one run per N is a weak sample. If results are close, repeat the closest two.
- The main-session cost includes the merge step, which grows with N. That is intended: it is part of the real price.
- Do not run other work in these chats. Do not reuse a chat across runs.
