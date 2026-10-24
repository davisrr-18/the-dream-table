# The Dream Table

Beta `2.0.0-beta` for Minecraft **1.21.1**, using Fabric Loader and Fabric API.

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
4. **R** rotates the farm. While it builds, the hologram shows only the layer being built and the one above; **H** switches to the whole farm and back. Both keys can be changed in Controls, under The Dream Table.
5. Hold the material and right-click the clipboard to deliver it. The line is struck through when the count is met.
6. Return to the table and click Build. The position locks. The build can start before every material is on the clipboard, and it waits at the next missing block instead of skipping it. In Creative mode, the build needs no materials and runs at the server's fastest rate. While it builds, the same button reads **Pause**; press it to stop after the current block, and **Resume** to pick up from the next one.

**Materials**, on the farm screen, swaps a block for another of the same shape: full block for full block, stairs for stairs, slab for slab, glass for glass, bed for bed, and so on. Type in the search box at the top of the list to find a block by name. Pick the swap before delivering that material or starting the build. The hologram, the clipboard, and the build all use the new block. Options, on the farm list, opens Build Speed: Slow, Normal (the default) or Fast, which are 25%, 50% and 100% of the server's fastest rate (64, 128 and 256 blocks per second by default). Each option shows its share and how long the chosen farm would take. Custom has a slider: drag it to any share from 1% to 100%. Blocks are spread evenly over each second. While building or paused, the farm screen shows the time left, like "~4 min". Blocks already in place are skipped instantly and don't count against the pace. When the build waits for a missing block, delivering it on the clipboard continues the build on its own.

The Dream Table holds the essential vanilla farms, one design each: sugar cane, bamboo, crops, mob farm, and iron farm, with more to come. For a bigger, smaller or different version of a farm, put its schematic in My Schematics. Blocks in the way are broken and dropped as items as the build reaches them (in Creative they are just removed). Blocks that hold data, like chests, furnaces, hoppers, signs, beds, and spawners, are never removed. Neither are unbreakable blocks, redstone parts, pistons, rails, TNT, infested blocks, or blocks you can't get back, like budding amethyst and eggs. The build stops there and the block shows up red on the hologram. Clearing never updates the blocks around it, so nothing outside the farm falls, pops off, or fires.

Every player has their own session at a table: their own menu page, farm, materials, swaps, clipboard, hologram and build. Players can use the same table at once without affecting each other, and two players' farms at one table can't overlap.

Discard returns the materials and removes the hologram. Whatever leaned on the removed blocks, like cane or bamboo that grew, crops you planted, torches or redstone, drops as items, as it would when mined by hand. If the build has started and is not finished, Discard also takes apart the blocks the table placed and gives them back, along with anything stored in its chests and hoppers. It takes them apart a few dozen per tick, so large farms don't lag the server, and you can pick a new farm right away. Items delivered in Creative are never handed back. It asks for a second click first. Blocks you changed, or that were already there, are left alone. A block you turned or replaced is also left alone; opening a door doesn't count as a change. A finished farm is never taken apart.

The build refuses a farm that reaches above or below the world, or past the border. A block is counted as already built only when its direction, half and axis match, so a hopper or stairs facing the wrong way is replaced. If the farm's file changes after the build starts, the build can't be resumed. Discard it and start again; its blocks are left in place.

When you anchor or turn the hologram, the table checks the spot right away: the whole hologram turns red and the reason shows when the farm would leave the world, overlap another farm, or enter protected land. Farms from different tables can't overlap either, and a build never breaks blocks inside another farm's area. A finished farm keeps its area even after you move on to another farm or break the table, so nothing can be built over it. Once less than half of its blocks are left, the area is free again. Builds and take-aparts only run while the player who started them is online. While they run, the table keeps its own chunk and the chunk being built loaded, so you can walk away.

**Show Other Farms**, in Options, outlines other players' farms near you: orange while being built or taken apart, green once finished. It is off by default and remembered on your computer.

If a farm you finished before 1.1.4 has no protection, place the same farm exactly over it: the table says how much of it is already built. Press Build, and it only fills the gaps and protects the farm once done.

The table builds with the permissions of the player who started the build. It won't start a farm that touches spawn protection, land outside the world border, or a claim that player can't build in, and it won't build in Adventure mode. If a spot becomes protected during the build, the build stops there until you press Try Again. Discard skips blocks the discarding player isn't allowed to break. Claim mods that use Fabric's block break event are respected. Modpacks can protect blocks from other mods by adding them to the block tag `the_dream_table:protected`.

Each player's table data is sent only to that player.

Server owners can set `max_blocks_per_second` (1 to 1024, default 256) in `config/the_dream_table.properties`. It caps every build, including Creative ones.

**My Farms**, in Options, lists every farm you finished, in every dimension, with its size, coordinates and whether it is running, not loaded, or has full storage. Click one to open it:

- Its page shows the storage in use, the items held, and the farm's block count.
- Each farm keeps the exact design it was built with, so Repair and Take Down keep working after an update changes or removes that farm.
- **Keep Loaded** keeps it loaded while you play, so plants grow, hoppers collect and iron golems spawn even when you are far away or in another dimension, up to the server's `max_loaded_farms_per_player` (0 to 16, default 3). When all its chests and barrels fill up, you get a chat message.
- **Repair** rebuilds it from the table you are using, in the same dimension: blocks still in place are skipped, so only missing ones are placed and need materials. The button shows how many blocks are missing and is off when nothing is.
- **Take Down**, after a second click, removes the whole farm a few blocks per tick and gives its blocks and the contents of its chests back, as mining it would. Set `load_farms_while_offline=true` to keep them loaded after you log off. Mob farms are the exception: the game only spawns mobs within 128 blocks of a player, loaded or not. Build them above sea level and away from caves; the table reminds you when you place one lower.

On a server, My Schematics lists the server's files, and players see the hologram, clipboard and miniature of those farms without having the files themselves.

Under **My Schematics**, the table reads `.nbt`, `.schem`, and `.litematic` files of up to 16,384 blocks from these instance folders:

- `schematics`
- `config/worldedit/schematics`
- `config/litematica`
- `config/the_dream_table/structures`
- `schematics` inside the world folder

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16 or newer
- Fabric API
- Java 21

## Build

From the project root, with Java 21:

```
./gradlew build
```

The mod jar is `build/libs/the_dream_table-2.0.0-beta.jar`. Jars suffixed with `sources` or `dev` are for development only.
