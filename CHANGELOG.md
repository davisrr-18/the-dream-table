# The Dream Table 1.1.0

Release date: 2026-10-29

Bulk material delivery, mirrored farms, farm protection and a Brazilian Portuguese translation, plus fixes across the table, the HUD and schematic import. Worlds, finished farms, builds in progress and shared farms from 1.0.0 carry over. The client and the server must both run 1.1.0.

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
  - You, the farm's Operators and server operators can still break it. Viewers can't, unless you also turn on **Viewers Can Break**.
  - Crops, sugar cane, cactus, bamboo, kelp, pumpkins, melons and other produce stay harvestable for everyone.
  - Only the farm's own blocks are protected: a block someone placed in a hole of the farm can be broken again. Grass or mycelium that grows over the farm's dirt stays protected.
  - Take Down and Repair keep working for you and your Operators.
- Server admins can turn the feature off for everyone with `farm_protection_enabled=false`.
- Explosions, pistons, mobs, fluids, fire and other mods that remove blocks without a player breaking them are not blocked.

## Brazilian Portuguese

- The whole mod is now translated into Brazilian Portuguese (Português do Brasil).

## Table, HUD and My Farms

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
- Huge or damaged schematic files are refused right away instead of freezing the server or running out of memory. Files whose bounding box holds more than 33 million cells are refused, even when most of it is air.
- On a dedicated server, editing or replacing a schematic file sends the new version to players instead of keeping the old copy. A farm the server didn't answer for is asked for again after a moment, and farm requests are rate-limited per player.

## Known limitations

- Fabric and NeoForge only; there is no Forge build (planned).
- Claim mods that use hooks other than the block break event (Fabric) or block break and place events (NeoForge) are not seen.
- Protect My Farms only stops players breaking blocks by hand.
- Text is available in English and Brazilian Portuguese.
