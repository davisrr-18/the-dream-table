#!/usr/bin/env python3
"""Split common/src/main/java into N line-balanced shards (greedy, largest file first).

Usage: python3 shard.py N   -> prints one block per shard: file count, LOC, and the file list.
Same input always gives the same shards, so every run of the test sees identical work.
"""
import pathlib, sys

n = int(sys.argv[1])
root = pathlib.Path(__file__).resolve().parents[3] / "common/src/main/java"
files = sorted(root.rglob("*.java"))
sizes = {f: sum(1 for _ in f.open(encoding="utf-8")) for f in files}
shards = [[0, []] for _ in range(n)]
for f in sorted(files, key=lambda f: (-sizes[f], str(f))):
    s = min(shards, key=lambda s: s[0])
    s[0] += sizes[f]
    s[1].append(f)
for i, (loc, fs) in enumerate(shards, 1):
    print(f"### Shard {i}/{n}: {len(fs)} files, {loc} lines")
    for f in sorted(fs):
        print(f.relative_to(root.parents[3]))
    print()
