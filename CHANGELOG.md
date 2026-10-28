# The Dream Table 1.0.0

Release date: 2026-10-28

The first stable release of The Dream Table. It follows 2.2.0-beta; the lower number is intentional and marks the start of stable versions. Worlds, finished farms, builds in progress and shared farms from the betas carry over.

## NeoForge support

- The mod now runs on NeoForge 21.1 for Minecraft 1.21.1, alongside Fabric. Each loader has its own jar.
- On NeoForge the mod must be installed on both the client and the server. On Fabric it is also needed on both sides, because it adds a block and a menu.
- Claim and protection mods are respected on both loaders: on Fabric through Fabric API's block break event, on NeoForge through the block break and place events.

## Take Down

- Take Down now removes and gives back only the blocks the farm placed and was paid for. Blocks you added yourself, or that were already there, stay where they are.
- A farm built in Creative gives back no blocks on Take Down, even in Survival, so nothing can be created out of thin air. Chest contents still come back.
- A Take Down in progress now survives a server restart instead of being lost.
- A Take Down that can't break a protected block no longer leaves the farm without an owner; the farm and its name, sharing and alerts stay as they were.
- A farm that was taken down is no longer recorded again after the world reloads.
- Lava and powder snow steps now hand back the empty bucket while building, and Take Down no longer refunds those fluids, so buckets can't be duplicated. Iron farms built with an earlier beta still refund their lava buckets.

## Materials

- Materials you delivered are now remembered item by item. If a design is removed or a schematic changes, Discard gives back exactly what you put in, instead of losing or swapping items.
- Delivered items that no longer fit a changed design come back to you automatically the next time you use the table.
- Shift-click and drag on the material slots only take what the farm still needs; the rest stays in your inventory.
- Leftover materials are no longer destroyed when you pick another farm after finishing one, or when items were left in the slots.

## Builds, Repair and saved worlds

- If an imported schematic is changed or deleted while its farm is being built, the build stops with a clear "farm changed" message instead of building the wrong design or getting stuck.
- A Repair started by a collaborator ends cleanly when their access is revoked or they lose the repair: the blocks already placed stay, the owner can take the farm down again, and unspent materials are returned. If that player is offline, the materials wait in the table and come back when they return.
- A refused Repair no longer leaves the table changed.
- One damaged save entry no longer wipes the other sessions, farms or Take Down jobs around it; unreadable entries are kept untouched so nothing is lost.
- Very large or broken numbers in saved data are clamped to safe values on load.

## Keep Loaded

- Keep Loaded now holds at most 64 chunks per farm; a larger imported farm shows "too large" instead of loading a huge area.
- Unpicking, removing or revoking a kept farm stops its chunks from ticking right away.

## Multiplayer and server performance

- Repeated clicks (Keep Loaded toggles, invitations, placing, depositing, farm list refreshes) are rate-limited per player, so spam can't lag the server; normal use is unaffected.
- Farm list updates sent to a player are combined into one update per half second.
- Invitations are now checked against the farm's roles, not against whichever menu is open.

## Interface

- The table menu now has a fixed size that only changes with the GUI scale. It no longer stretches with the window, and it shrinks to fit short windows.
- The scrollbar track now starts right under the tabs on every page.
- Fixed raw text keys showing instead of names when the game runs in a Turkish locale.
- The mod now has an icon and links to its source and issue tracker in the mod list.

## Known limitations

- Fabric and NeoForge only; there is no Forge build.
- Claim mods that use hooks other than the block break event (Fabric) or block break and place events (NeoForge) are not seen.
- Only English text is included.
