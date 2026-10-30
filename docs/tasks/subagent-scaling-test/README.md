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

## Results (2026-10-29)

Status: Complete. Six runs (N=3, 5, 5 repeat, 8, 10, 12), Haiku high investigators, Sonnet medium orchestrator. Two independent read-only verification passes (Sonnet medium reviewer) checked 43 claims against the code: 10 CONFIRMED, 20 PARTIAL, 13 FALSE. Quota is from `/usage`; tokens are the sum reported for the run.

| N | Tokens | 5h quota | Verified claims reported | Confirmed | Partial | False | Recall (of 10 confirmed) | Precision (confirmed + partial) |
|---|---|---|---|---|---|---|---|---|
| 3 | 548k | 4% | 18 | 8 | 9 | 1 | 8/10 | 94% |
| 5 | 790k | 7% | 16 | 8 | 8 | 0 | 8/10 | 100% |
| 5 (repeat) | 734k | 5% | 25 | 8 | 11 | 6 | 8/10 | 76% |
| 8 | 920k | 4% | 16 | 4 | 9 | 3 | 4/10 | 81% |
| 10 | 873k | 3% | 23 | 4 | 14 | 5 | 4/10 | 78% |
| 12 | 1.07M | 3% | 20 | 4 | 9 | 7 | 4/10 | 65% |

Findings:
- Tokens grow with N (548k to 1.07M). The 5h percentage does not track N (3% to 7%, and two N=5 runs gave 7% and 5%), so it is too noisy to use.
- N=3 and N=5 found the same share of confirmed defects (8/10). N>=8 found 4/10 and produced more false positives, mostly from shards missing context outside their files.
- N=3 cost about 25% fewer tokens than N=5 for the same recall.

Caveats: one run per N (two for N=5); the claim sample was chosen from the reports (cross-run, contradictory, and run-unique findings), so recall is relative to this sample, not to all real defects; every agent hit the 8-finding cap, which limits N=3 to 24 findings.

Verification passes counted one claim per issue per run; the line-balanced shards mix packages.
