# The Dream Table 1.1.0

Release date: 2026-10-29

Bulk material delivery, mirrored farms, farm protection and a Brazilian Portuguese translation, plus fixes across the table, the HUD and schematic import. Worlds, finished farms and builds in progress from 1.0.0 carry over. The client and the server must both run 1.1.0.

## Delivering materials

- New **Deliver All** button on the Materials page: one click moves every item the farm still needs from your inventory and offhand into the table, in exactly the amounts needed. A build that was waiting for a missing block starts again by itself.
- New **Deliver From Nearby** button: takes the items the farm still needs from chests, trapped chests, barrels and placed shulker boxes within 6 blocks of the table.
  - Locked containers, loot chests that were never opened, and containers in places you aren't allowed to break (land claims, spawn protection) are skipped.
  - Shulker boxes stored as items inside a chest are never opened.
  - You can turn the button off for yourself in Settings. Server admins can turn it off for everyone with `deliver_from_nearby_enabled=false`.
- Items with custom data (renamed, enchanted, or shulker boxes with items inside) are no longer taken as materials on any delivery path, so their names, enchantments and contents can't be lost. The table tells you how many needed stacks it skipped.
- Both buttons are disabled in Creative, where builds are free.

## Mirrored farms

- Press **M** while placing a farm to mirror it. Together with **R** (rotate) that gives eight orientations. Both keys can be changed in Controls, and the on-screen hint shows the keys you bound.
- A mirrored farm stays on the same spot: only its layout flips.
- Blocks are flipped correctly, including stair corners, door hinges, double chests, hoppers, pistons, observers and rails. Two vanilla mirroring bugs are worked around: double chests no longer split into two halves, and stair corners no longer end up on the wrong side.
- The mirror is saved with the farm, so the hologram, the build, Repair, Take Down, Show Other Farms and Protect My Farms all follow it. Farms from 1.0.0 are unchanged.

## Protect My Farms

- New **Protect My Farms** option in Settings (off by default). When it is on, other players can't break the structure of your finished farms, and they see "This farm is protected by <your name>".
  - You and server operators can still break it.
  - Crops, sugar cane, cactus, bamboo, kelp, pumpkins, melons and other produce stay harvestable for everyone.
  - Only the farm's own blocks are protected: a block someone placed in a hole of the farm can be broken again. Grass or mycelium that grows over the farm's dirt stays protected.
  - Take Down and Repair keep working for you.
- Server admins can turn the feature off for everyone with `farm_protection_enabled=false`.
- Explosions, pistons, mobs, fluids, fire and other mods that remove blocks without a player breaking them are not blocked.

## Server administration

- New operator commands (permission level 2): `/dreamtable reload` reloads the config and the schematic files without a restart, and `/dreamtable status` prints farm, build, claimed-area, loaded-farm and schematic counts and any unreadable saved records.
- Opening a Dream Table and clicking its buttons no longer waits for schematic files to be read; they are read in the background.
- A Dream Table that holds another player's delivered materials, build or Take Down can no longer be broken by someone else; the breaker is told who is using it. The session's owner and server operators can still break it. Because that player's claim doesn't expire, an admin can turn the protection off with `table_break_guard_enabled=false` (then `/dreamtable reload`) to free the table of a player who never comes back.
- When a farm is taken down, each paid block is refunded to the farm's owner. If the owner is offline, they receive it the next time they log in. They get one chat line about it every few seconds instead of one per batch.
- New option `show_other_farms_enabled` (default on): turn it off to stop sending other players' farm areas, for privacy. "Show Other Farms" then reads "Turned off on this server".
- Fabric servers now check The Dream Table's version when a 1.1.0 client connects. If it doesn't match the server's, the client gets a clear message with both versions instead of a confusing error. Clients without the mod can still join, and a 1.0.0 client can't be told apart from them, so client and server should both run 1.1.0.

## Brazilian Portuguese

- The whole mod is now translated into Brazilian Portuguese (Português do Brasil).

## Table, HUD and My Farms

- Table messages (delivery results, missing materials, farm protection, the mirror toggle and more) now appear in a notice at the top center of the screen for a few seconds, even while the table is open: green for success, red for problems, on a translucent panel with rounded corners. Before, they used the action bar, which is hidden behind the table and easy to miss.
- While your build or repair runs and you are near its table, a green "Building n/total" line stays at the top of the screen and counts up live, with the time left once you have opened the table. It hides while the table is open, when you walk away, and when the build pauses, stops or finishes.
- Farm alerts moved into My Farms: a farm with an alert shows a red ⚠ before its name, and its page lists each alert in red at the bottom ("⚠ Alert: storage full"). The separate Alerts page is gone.
- Long text in the table's header, tabs, cards and buttons now scrolls so it can always be read in full, in every language, instead of being cut with "...". Cards and buttons no longer pop up tooltips over the options below them; the few remaining tooltips (icon-only tabs, item slots) wrap to fit the window.
- The Deliver buttons show what they do, or why they are unavailable, right on the button, and rejected schematic files show their reason on their card.
- The hologram and the clipboard no longer disappear when you walk away and the table's chunk unloads; they come back when you return. They are still cleared when the table is broken.
- The tracked farm HUD now shows right after you join, without opening My Farms first. It no longer carries over to another world or server, and it shows the farm's custom name, its translated dimension, and the direction and distance in your language.
- My Farms lists your farms even when the server sets `max_loaded_farms_per_player=0`. It says that keeping farms loaded is turned off on the server, and Keep Loaded reads "Off On This Server".
- My Farms no longer stays stuck on "Looking for your farms..." after resizing the window.
- Other players' farm outlines disappear as soon as you change dimension.
- Breaking a table no longer runs its material drop on the client as well, which could show ghost items.
- Starting to break a table while a build is running there shows a warning: the delivered materials drop and the build ends, while the blocks already placed stay.
- The "storage full" chat message uses the farm's custom name.
- The "farm changed" message now says that the blocks already placed stay where they are.

## Schematics

- `.litematic` files saved by Litematica are now read correctly. Before, their regions could be read with no size, so the file imported as empty.
- In `.litematic` files with several regions, chest contents, signs and other block data now land on their own region's blocks, and entities are placed relative to their own region.
- Huge or damaged schematic files are refused right away instead of freezing the server or running out of memory. A schematic may be at most 512 blocks on any side.
- Schematics that need more than 54 different materials or place two blocks in the same spot now say so, instead of "Can't be read".
- Dream Table blocks inside an imported schematic are left out with a warning, and click events on imported signs are removed so a sign can't run commands.
- A schematic with an extremely long block name can no longer stop a server's My Schematics list from loading.
- On a dedicated server, editing or replacing a schematic file sends the new version to players instead of keeping the old copy. A farm the server didn't answer for is asked for again after a moment, and farm requests are rate-limited per player.

## More table improvements

- Hovering an item icon on a finished farm shows its name and count; empty requirement slots name the item they need.
- Buttons on My Farms that can't be used now say why: Keep Loaded at the limit or off on the server, Repair while the table is busy or when nothing is missing.
- The custom build speed slider works from the keyboard: Tab to it, Left and Right change it by 1%, Home and End set the minimum and maximum.
- Large schematic previews draw much faster: the hologram is built once and reused instead of being redrawn block by block every frame, and the first preview of a big farm is spread over a few frames.
- New **How It Works** page on each built-in farm: how it works, what it needs to run (loaded chunks, bees, villagers, seeds, space in the chest), what it produces and tips.
- New built-in **Creeper Farm** under Mob Farms: a tall tower that gathers gunpowder (and other mob drops) in a chest.
- New built-in **Gold Farm** under Resource Farms, designed for the Nether: zombified piglins spawn on magma blocks and fall to chests. It can only be placed in the Nether; build it above the bedrock layer in the Nether Wastes biome for maximum efficiency.
- Aiming a farm now shows the farm itself and its whole footprint on the ground (a frame of ribbons and a floor) instead of a small marker, so you can turn and mirror it before placing. The outline is coloured by the server's answer: green when the spot is valid, red when it is refused, amber while it is being checked, and it stays around the spot after you click.
- New **map placement mode** (default key G, rebindable in Controls): with a farm chosen at your Dream Table, a free camera starts at your eyes and a ring on the ground shows how far the table reaches. Fly with WASD, Space and Shift, hold the right mouse button to look around, and scroll to move forward or back. The mouse cursor aims the farm, which takes the same colours as the normal aim, and a click places it. The camera stays within the table's reach; at its edge a notice says so. R turns and M mirrors the farm, H switches the hologram layers, and Esc or G goes back. Your character stays where it is. Pt-BR text included.
- The Mob Farm has a new design: a tall tower where mobs fall to the campfires at the bottom and their drops are collected in a chest. Farms you already built keep their old design.
- The farm page's Main Menu button is now Back and returns to that farm's category (or My Schematics for your own files), and the Back button on farm lists, Materials and Swap pages spans the full width. Single-column pages (farm page, build page, How It Works, build speed) now use the full width of the window for their cards, buttons, text and the speed slider; card grids keep their layout. A leftover empty text field on the farm page is gone.

## Fixes and performance

- Fixed a build getting stuck waiting for materials when two different materials were swapped to the same block.
- Fixed Take Down sometimes skipping a refund after an error.
- When the table clears obstacles, ores, obsidian and ancient debris no longer drop items, so clearing isn't free mining. Every other cleared block still drops as before.
- Deliver From Nearby tells you to wait a moment when pressed too quickly, instead of doing nothing.
- Farm details and alerts no longer get stuck on "Looking..." after resizing the window.
- Keyboard navigation shows a clear focus outline on cards, buttons, filters and tabs, and screen readers announce a disabled button as "Unavailable" once.
- The H key no longer toggles hologram layers when no farm is being previewed, and opening another table with the same farm starts a fresh placement.
- Only the ⚠ sign next to a farm with an alert is red, not the whole name.
- The tracked-farm line moves down instead of overlapping a long notice.
- "Click again to take down" and other confirmation hints are shown as normal notices instead of red errors, and the missing-material hint mentions Deliver All.
- Large farms and imported schematics build, repair and save much faster; farm alerts, My Farms and the swap list cost less on servers and clients with many farms.
- Placement checks and My Farms requests are rate-limited more tightly, and repeated identical requests no longer rescan every farm.
- A server setting with a value other than true or false (for example `yes`) is reported in the log and uses its default. Before, such a value counted as false.

## Removed

- Renaming farms and sharing farms with Viewers and Operators were removed to simplify the interface. Collaborators of existing shared farms are dropped; their owners keep their farms. A repair an Operator had in progress stops, and refunds that were owed to an Operator go to the owner.
- The server settings `farm_sharing_enabled`, `max_collaborators_per_farm` and `max_pending_invites_per_player`, and the Settings switches for Accept Farm Invites and Viewers Can Break, are gone. Old config files still load; the removed keys are ignored.

## Known limitations

- Fabric and NeoForge only; there is no Forge build (planned).
- Claim mods that use hooks other than the block break event (Fabric) or block break and place events (NeoForge) are not seen.
- Protect My Farms only stops players breaking blocks by hand.
- Text is available in English and Brazilian Portuguese.
