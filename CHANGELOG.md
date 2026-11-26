# The Dream Table 1.6.0

Release date: not released yet (in development)

A feature update for Minecraft 1.21.11, 1.21.1 and 1.20.1.

## Fixes

- Protect My Farms now keeps guarding a farm while you take it down. Before, the farm lost its protection from the moment Take Down started until the last block was gone.
- A farm name made only of invisible characters (such as zero-width or no-break spaces) no longer shows as a blank name in My Farms. The farm shows its usual name instead.
- When a build or a repair finishes, the table now saves that right away. A server crash just after a farm was finished could record it twice and show "repair completed" twice.
- Parked projects that this version can't read are no longer deleted the next time the world saves. They are kept as saved, so a later version can still restore them.

## Known issues

- Bamboo Farm on Forge 1.20.1: a player reported that the Requirements page would not take bamboo into the farm's material slot, even with bamboo in the inventory. We could not reproduce or explain it yet, and it was not reported on the other loaders or Minecraft versions. If it happens to you, press Deliver All and send a note from the feedback form in Settings with what it says.

## Good to know

- Worlds, finished farms, builds in progress and parked projects from 1.5.1 carry over to 1.6.0.
