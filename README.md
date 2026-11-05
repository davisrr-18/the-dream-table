# The Dream Table

Version `1.2.0` for Minecraft **1.21.1**, on Fabric (with Fabric API) and NeoForge.

The Dream Table picks a classic farm, shows the hologram in the world, and builds it after the materials are delivered. Options sets how fast it builds.

## How to play

The table is in the Redstone creative tab, or craft it with this recipe:

```
LLL
PLP
OOO
```

Lapis lazuli on the top row and in the center, paper on the sides, and oak planks along the bottom.

1. Place the table, pick a category (Plant Farms, Mob Farms or Resource Farms), then a farm. Each farm shows its block count and how long it takes at your build speed. My Schematics shows the same for your own files.
2. A small turning model of the chosen farm floats above the table. Close the menu. A yellow marker follows the crosshair.
3. Right-click the ground to anchor the hologram. Right-click again to move it.
4. **R** rotates the farm and **M** mirrors it (eight orientations together; the mirror is saved with the farm, so building, Repair and Take Down follow it). While it builds, the hologram shows only the layer being built and the one above; **H** switches to the whole farm and back. Both keys can be changed in Controls, under The Dream Table.
5. Hold the material and right-click the clipboard to deliver it. The line is struck through when the count is met. On the Materials page, **Deliver All** moves every item the farm still needs from your inventory and offhand in one click, and **Deliver From Nearby** takes them from chests, trapped chests, barrels and placed shulker boxes within 6 blocks of the table (locked, never-opened loot and protected containers are skipped). Both are off in Creative, where builds are free, and Deliver From Nearby can be turned off in Settings.
6. Return to the table and click Build. The position locks. The build can start before every material is on the clipboard, and it waits at the next missing block instead of skipping it. In Creative mode, the build needs no materials and runs at the server's fastest rate. While it builds, the same button reads **Pause**; press it to stop after the current block, and **Resume** to pick up from the next one.

**Materials**, on the farm screen, swaps a block for another of the same shape: full block for full block, stairs for stairs, slab for slab, glass for glass, bed for bed, and so on. Type in the search box at the top of the list to find a block by name. Pick the swap before delivering that material or starting the build. The hologram, the clipboard, and the build all use the new block. Options, on the farm list, opens Build Speed: Slow, Normal (the default) or Fast, which are 25%, 50% and 100% of the server's fastest rate (64, 128 and 256 blocks per second by default). Each option shows its share and how long the chosen farm would take. Custom has a slider: drag it to any share from 1% to 100%. Blocks are spread evenly over each second. While building or paused, the farm screen shows the time left, like "~4 min". Blocks already in place are skipped instantly and don't count against the pace. When the build waits for a missing block, delivering it on the clipboard continues the build on its own.

The Dream Table holds the essential vanilla farms, one design each: sugar cane, bamboo, cactus, crops, honey, mob farm, creeper farm, iron farm, a Nether gold farm, and an animal farm, with more to come. For a bigger, smaller or different version of a farm, put its schematic in My Schematics. Blocks in the way are broken and dropped as items as the build reaches them (in Creative they are just removed). Blocks that hold data, like chests, furnaces, hoppers, signs, beds, and spawners, are never removed. Neither are unbreakable blocks, redstone parts, pistons, rails, TNT, infested blocks, or blocks you can't get back, like budding amethyst and eggs. The build stops there and the block shows up red on the hologram. Clearing never updates the blocks around it, so nothing outside the farm falls, pops off, or fires.

Every player has their own session at a table: their own menu page, farm, materials, swaps, clipboard, hologram and build. Players can use the same table at once without affecting each other, and two players' farms at one table can't overlap. When a table is broken, by anyone or anything, each player's project is kept for them and comes back in the next Dream Table they open: the farm, the materials they delivered, and a started build, paused on its spot. The build continues when the new table is in the same dimension and within 48 blocks of it. The adaptive menu has persistent Build, Farms, Schematics, and Settings tabs; it keeps the same safe inventory-slot region on compact and wide screens.

Discard returns the materials and removes the hologram. Whatever leaned on the removed blocks, like cane or bamboo that grew, crops you planted, torches or redstone, drops as items, as it would when mined by hand. If the build has started and is not finished, Discard also takes apart the blocks the table placed and gives them back, along with anything stored in its chests and hoppers. It takes them apart a few dozen per tick, so large farms don't lag the server, and you can pick a new farm right away. Items delivered in Creative are never handed back. It asks for a second click first. Blocks you changed, or that were already there, are left alone. A block you turned or replaced is also left alone; opening a door doesn't count as a change. A finished farm is never taken apart.

The build refuses a farm that reaches above or below the world, or past the border. A block is counted as already built only when its direction, half and axis match, so a hopper or stairs facing the wrong way is replaced. If the farm's file changes after the build starts, the build can't be resumed. Discard it and start again; its blocks are left in place.

When you anchor or turn the hologram, the table checks the spot right away: the whole hologram turns red and the reason shows when the farm would leave the world, overlap another farm, or enter protected land. Farms from different tables can't overlap either, and a build never breaks blocks inside another farm's area. A finished farm keeps its area even after you move on to another farm or break the table, so nothing can be built over it. Once less than half of its blocks are left, the area is free again. Builds and take-aparts only run while the player who started them is online. While they run, the table keeps its own chunk and the chunk being built loaded, so you can walk away.

**Show Other Farms**, in Options, outlines other players' farms near you: orange while being built or taken apart, green once finished. It is off by default and remembered on your computer.

If a farm you finished with an early beta has no protection, place the same farm exactly over it: the table says how much of it is already built. Press Build, and it only fills the gaps and protects the farm once done.

The table builds with the permissions of the player who started the build. It won't start a farm that touches spawn protection, land outside the world border, or a claim that player can't build in, and it won't build in Adventure mode. If a spot becomes protected during the build, the build stops there until you press Try Again.

When a build stops, the menu says what happened and where (the block, its coordinates and the cause: protected land, a block the table won't clear, another farm, a missing item, or a block the game refused) and what you can do. A chunk that takes long to load is shown too, while the build waits for it. Pressing Try Again or Continue first checks whether the problem is gone: if it isn't, the build stays stopped and tells you, instead of starting and stopping again. Discard skips blocks the discarding player isn't allowed to break. Claim mods that use Fabric API's block break event, or NeoForge's block break and place events, are respected. Modpacks can protect blocks from other mods by adding them to the block tag `the_dream_table:protected`.

Each player's table data is sent only to that player.

Server owners can change these in `config/the_dream_table.properties`, which is written with the defaults the first time and read when the server starts:

| Key | Default | Meaning |
| --- | --- | --- |
| `max_blocks_per_second` | 256 | Fastest any build may go, 1 to 1024. Caps every build, including Creative ones. |
| `max_loaded_farms_per_player` | 3 | Farms each player may keep loaded, 0 to 16. |
| `load_farms_while_offline` | false | Keep loaded farms loaded after their player logs off. |
| `deliver_from_nearby_enabled` | true | Allow Deliver From Nearby for every player. |
| `farm_protection_enabled` | true | Allow players to turn on Protect My Farms. |

The true or false keys accept only `true` or `false`; anything else is logged and the default is used.

**My Farms**, in Options, lists every farm you finished, in every dimension. Each card shows whether the farm is running and how full its storage is, whether its storage is full or it is not loaded, how many blocks are missing, and how far away it is (or its dimension). Highlighted cards are the farms kept loaded. Type in the search box to find a farm by name, and use the filters above the list to show only farms that need attention (full storage, missing blocks, or a lost design), running, not loaded or full ones, or one dimension, and to sort by newest, name, attention first, fullest storage or nearest. Click one to open it:

- Its page shows the storage in use, the items held, and the farm's block count.
- Each farm keeps the exact design it was built with, so Repair and Take Down keep working after an update changes or removes that farm.
- **Keep Loaded** keeps it loaded while you play, so plants grow, hoppers collect and iron golems spawn even when you are far away or in another dimension, up to the server's `max_loaded_farms_per_player` (0 to 16, default 3). A farm covering more than 64 chunks can't be kept loaded. When all its chests and barrels fill up, you get a chat message.
- The page also shows how many blocks are missing and the items a repair needs for them, and how many blocks you changed.
- **Repair** rebuilds it from the table you are using, in the same dimension: blocks still in place are skipped, so only missing ones are placed and need materials. A spot where you put another block is left as you made it, and mobs or minecarts still in place aren't spawned again. The button shows how many blocks are missing and is off when nothing is.
- **Take Down**, after a second click that names the farm and its coordinates, removes the whole farm a few blocks per tick and gives its blocks and the contents of its chests back, as mining it would. Blocks you changed or added stay.
- If you log off during a build or while a farm is being removed, it waits and tells you when it continues after you return. An unexpected error stops only that operation, with a message, and its details go to the game log.
- Items handed back never get lost: when your inventory is full, the rest drops at your feet and you get a message. Set `load_farms_while_offline=true` to keep them loaded after you log off. Mob farms are the exception: the game only spawns mobs within 128 blocks of a player, loaded or not. Build them above sea level and away from caves; the table reminds you when you place one lower.

**Protect My Farms**, in Settings (off by default), stops other players from breaking the structure of your finished farms by hand; they see who protects it. You and server operators can still break it. Crops, sugar cane, cactus, bamboo, kelp, pumpkins, melons and other produce stay harvestable for everyone, and only the farm's own blocks are protected. Explosions, pistons, mobs, fluids, fire and other mods that remove blocks are not blocked.

A finished farm has a permanent identity, so its saved design and alerts survive Repair and world restarts. Favorite or track a farm from its page: My Farms can show only favorites or sort them first, and tracked farms show a direction and distance HUD. The selected farm page shows its operating state, Keep Loaded state, distance or dimension, storage, condition, and repair needs. Alerts report missing blocks, full storage, unavailable designs, and Repair problems: a farm with an active alert is marked with a warning sign in My Farms and its page ends with a red "Alert" line for each one.

On a server, My Schematics lists the server's files, and players see the hologram, clipboard and miniature of those farms without having the files themselves.

Under **My Schematics**, the table reads `.nbt`, `.schem`, and `.litematic` files of up to 16,384 blocks from these instance folders:

- `schematics`
- `config/worldedit/schematics`
- `config/litematica`
- `config/the_dream_table/structures`
- `schematics` inside the world folder

Each file is checked when the list is read, before anyone builds it. A file that can't be used is still listed, greyed out, with the reason: it can't be read or is damaged, it isn't in one of these formats, it has no blocks, more than 16,384 blocks, more than 64 MB of data, it is taller than any world or longer than 512 blocks on a side, it needs more than 54 different materials, or it places two blocks in the same spot. A farm the table can build may come with warnings, shown on its card and on the farm screen before you press Build: blocks from mods that aren't installed, which are left out, blocks a survival player can't get, like bedrock or spawners, so only Creative can finish it, and Dream Table blocks, which are left out. Click events on signs in a file are removed, so a sign can't run commands. On a server, players see the same reasons and warnings for the server's files. New or changed files show up within a second.

A finished farm keeps everything its blocks held in the schematic, like a hopper's filter items, so a repair puts them back as they were.

## Requirements

- Minecraft 1.21.1
- Java 21
- Fabric: Fabric Loader 0.16 or newer and Fabric API
- NeoForge: NeoForge 21.1 (the 21.1 series, for Minecraft 1.21.1)

Put the jar for your loader in the `mods` folder of the game and, on a server, of the server too: the mod is needed on both sides, and the client and the server must run the same version (1.2.0).

Worlds, finished farms and builds in progress from 1.0.0 carry over to 1.2.0. Farms that were shared in 1.0.0 keep their owners, and their collaborators are dropped. 1.0.0 was the first stable release and followed 2.2.0-beta; the lower number is intentional.

## Known limitations

- Fabric and NeoForge only; there is no Forge build.
- A farm built in Creative gives back no blocks on Take Down, even in Survival. Chest contents still come back.
- Claim mods are respected through Fabric API's block break event on Fabric and through block break and place events on NeoForge. Claim mods that use other hooks are not seen.
- Keep Loaded can't hold a farm that spans more than 64 chunks.
- Text is available in English and Brazilian Portuguese only.
- Protect My Farms only stops players breaking blocks by hand.

## Build

From the project root, with Java 21:

```
./gradlew build
```

The build places one jar per loader in its own folder:

- Fabric (needs Fabric API): `build/release/fabric/the_dream_table-fabric-1.2.0.jar`
- NeoForge: `build/release/neoforge/the_dream_table-neoforge-1.2.0.jar`

The `sources` and `dev-shadow` jars under `fabric/build/libs/` and `neoforge/build/libs/` are for development only.
