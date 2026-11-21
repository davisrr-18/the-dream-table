# The Dream Table 1.5.0

Release date: 2026-11-21

The Dream Table now runs on Minecraft 1.21.11. This update also adds a feedback form, a help page for your own schematics, and fixes for bugs found in a code review of 1.4.0.

## Changes

- **Minecraft 1.21.11** is supported on Fabric, NeoForge and Forge, next to 1.21.1 and 1.20.1.
- **My Schematics** has a How It Works page: where to save files, which formats and size are read, how it works on a server, and why a file is greyed out.
- **Settings** has a feedback form: a bug, an idea or another note, sent with its text, category, mod version, loader and Minecraft version; no name, account or ID of yours is sent. A switch turns it off.

## Fixes

- A build far from you keeps the chunk of its next block loaded from the first block, so it no longer stops when that chunk unloads.
- Aiming a new farm over a finished farm whose blocks are mostly gone no longer removes that farm from My Farms. Starting the build there replaces it, as before.
- Discarding a started build whose farm file is gone now says that its blocks were left in place.
- Above 800 schematics, some schematic buttons acted as material swaps. My Schematics now shows the first 800 and says when there are more.
- Reopening a table on the Farm Info page returns to the farm's page, not to My Farms.

## Good to know

- The network protocol changed (version 5): a 1.5.0 server and its players must all run 1.5.0.
- Worlds, finished farms and builds in progress from 1.4.0 carry over to 1.5.0. On Minecraft 1.21.11, a world from 1.21.1 also carries over: the game upgrades it the first time it opens, so keep a copy of the world first.
