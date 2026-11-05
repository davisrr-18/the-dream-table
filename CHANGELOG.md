# The Dream Table 1.2.0

Release date: TBD

A new farm, water that no longer needs buckets, and a round of fixes for kept projects, Repair and shared tables. Worlds, finished farms and builds in progress from 1.1.1 carry over. The client and the server must both run 1.2.0.

## New farm

- **Animal Farm.** Available under Mobs, with a **How It Works** page like the other built-in farms.

## Water without buckets

- Farms that use water no longer ask you for water buckets. They need an **infinite water source** within 6 blocks of the table: a water source with at least two other water sources touching it on its sides (a 2x2 pool works, or three sources in a row). Two sources with a gap between them, or a single source, are not enough. The requirements list shows one line telling you whether a source is nearby. Nothing is used up, so there is nothing to carry or get back. Water placed by farms does not count.
- If the source disappears while the build is running, the build stops at the next water block with a clear message and carries on by itself when a source is back.
- A build you had already started in 1.1.1 on a farm with water keeps its progress, but the materials delivered after the first water block are handed back to you and need to be delivered again.

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
