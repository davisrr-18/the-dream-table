# The Dream Table 1.5.1

Release date: 2026-11-23

A bug-fix update for Minecraft 1.21.11, 1.21.1 and 1.20.1.

## Fixes

- **Minecraft 1.21.1:** the feedback box in Settings wrapped its text at one character per line. It now wraps by words at the width of the box, and keeps your text and focus when the window is resized.
- A repair whose saved details are damaged can no longer go on without its checks: it ends as lost, and the materials you delivered come back to you.
- A parked project whose saved world is damaged is no longer resumed in the world you are in. It is kept exactly as saved.
- The "this step failed again, look at the spot" message now still appears after you leave the table and come back.

## Known issues

- Bamboo Farm on Forge 1.20.1: a player reported that the Requirements page would not take bamboo into the farm's material slot, even with bamboo in the inventory. We could not reproduce or explain it yet, and it was not reported on the other loaders or Minecraft versions. If it happens to you, press Deliver All and send a note from the feedback form in Settings with what it says.

## Good to know

- The network protocol did not change (version 5), so 1.5.0 and 1.5.1 use the same one. Run the same version on a server and its players anyway.
- Worlds, finished farms and builds in progress from 1.5.0 carry over to 1.5.1.
