# The Dream Table 1.6.0

Release date: not released yet (in development)

A feature update for Minecraft 1.21.11, 1.21.1 and 1.20.1.

## New

- My Schematics has an Open Folder button next to How It Works, in worlds that run on your computer. It opens `config/the_dream_table/structures` (and makes it if it's missing), so you can drop your own `.nbt`, `.schem` or `.litematic` files there.
- Protect My Farms now also stops explosions (creepers, TNT and the like) and endermen from breaking a protected farm. Produce stays breakable as before, and an explosion the farm's owner caused, like their own TNT, is not stopped. Endermen don't pick up blocks near a protected farm.
- You can queue farms at a table. Aim a farm and press Add to Queue, aim the next the same way, then press Build on the one that should go first: the queued farms start by themselves, in order, as each one finishes. Up to 8 per player per table. Materials are delivered per farm when its turn comes, and a queued spot is checked again then. Build Queue and Clear Queue are on the category page, and the spots of your queued farms are outlined in blue (only for you) while you aim the next one at that table, in map mode too, and a spot that overlaps one turns red. Discard drops only the farm in progress.
- A farm's page in My Farms now shows its production per hour: how many of each item its chests and barrels gain, measured while the farm is loaded. It says "measuring" for the first ten minutes. Items you put in the farm's chests count too, and a full chest hides production. This changes the network protocol, so client and server must both run 1.6.0.

## Fixes

- Protect My Farms now keeps guarding a farm while you take it down. Before, the farm lost its protection from the moment Take Down started until the last block was gone.
- A farm name made only of invisible characters (such as zero-width or no-break spaces) no longer shows as a blank name in My Farms. The farm shows its usual name instead.
- When a build or a repair finishes, the table now saves that right away. A server crash just after a farm was finished could record it twice and show "repair completed" twice.
- Parked projects that this version can't read are no longer deleted the next time the world saves. They are kept as saved, so a later version can still restore them.

## Known issues

- Bamboo Farm on Forge 1.20.1: a player reported that the Requirements page would not take bamboo into the farm's material slot, even with bamboo in the inventory. We could not reproduce or explain it yet, and it was not reported on the other loaders or Minecraft versions. If it happens to you, press Deliver All and send a note from the feedback form in Settings with what it says.

## Good to know

- Worlds, finished farms, builds in progress and parked projects from 1.5.1 carry over to 1.6.0.
