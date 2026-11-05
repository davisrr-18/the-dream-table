# The Dream Table 1.2.0

Release date: TBD

A new farm, water that no longer needs buckets, and a round of fixes for kept projects, Repair and shared tables. Worlds, finished farms and builds in progress from 1.1.1 carry over. The client and the server must both run 1.2.0.

## New farm

- **Animal Farm.** Available under Mobs, with a **How It Works** page like the other built-in farms.

## Water without buckets

- Farms that use water no longer ask you for water buckets. They need an **infinite water source** within 6 blocks of the table: a water source with at least two other water sources next to it on the same level (a 2x2 pool works, or two sources with one gap between them). The requirements list shows one line telling you whether a source is nearby. Nothing is used up, so there is nothing to carry or get back. Water placed by farms never counts, and a single source is not enough.
- If the source disappears mid-build, the build pauses on a clear message and carries on by itself when a source is back.
- Water buckets you already delivered to a build from an earlier version are given back to you.

## Kept projects

- A farm you chose and placed on the ground but did not start building is now kept when the table is broken. Open a table again and the spot is back, as long as it is still in reach and free; otherwise you get a message and keep the farm and your materials.
- A kept build, still waiting for you, now holds its area. Another player's farm (or yours) can no longer be built over its blocks and wipe them without a refund.
- A finished farm whose saved record cannot be read still keeps its area reserved, so nothing gets built on top of it.
- A saved table entry that cannot be read is no longer lost silently, and a corrupt one that belongs to no player is kept instead of dropped.

## Fixes

- A refused **Repair** no longer changes the farm you had chosen or the spot you had aimed.
- On older worlds, a table's old shared project now goes to the player who opens the table, not to whoever sent the first request to the server.
- The Build button no longer starts a build when no spot has been chosen.
- Breaking blocks in a large protected farm no longer slows the server down.
