# The Dream Table

Beta `1.1.4-beta` for Minecraft **1.21.1**, using Fabric Loader and Fabric API.

The Dream Table picks a classic farm, shows the hologram in the world, and builds it after the materials are delivered. Options sets the pace at 1, 2, 4, or 8 blocks per second.

## How to play

The table is in the Redstone creative tab, or craft it with this recipe:

```
LLL
PLP
OOO
```

Lapis lazuli on the top row and in the center, paper on the sides, and oak planks along the bottom.

1. Place the table and choose a farm.
2. Close the menu. A yellow marker follows the crosshair.
3. Right-click the ground to anchor the hologram. Right-click again to move it.
4. **R** rotates the farm. The key can be changed in Controls, under The Dream Table.
5. Hold the material and right-click the clipboard to deliver it. The line is struck through when the count is met.
6. Return to the table and click Build. The position locks. The build can start before every material is on the clipboard, and it waits at the next missing block instead of skipping it. In Creative mode, the build needs no materials. While it builds, the same button reads **Pause**; press it to stop after the current block, and **Resume** to pick up from the next one.

**Materials**, on the farm screen, swaps a block for another of the same shape: full block for full block, stairs for stairs, slab for slab, glass for glass, bed for bed, and so on. Type in the search box at the top of the list to find a block by name. Pick the swap before delivering that material or starting the build. The hologram, the clipboard, and the build all use the new block. Options, on the farm list, sets the build time: "finish in about 10, 5, or 1 minutes" (5 by default). The speed comes from the farm's size, up to 256 blocks per second, so small farms may finish sooner. Blocks are spread evenly over each second. While building or paused, the farm screen shows the time left, like "~4 min". Blocks already in place are skipped instantly and don't count against the pace. When the build waits for a missing block, delivering it on the clipboard continues the build on its own.

Farms in this beta: sugar cane, bamboo, crops, mob farm, and iron farm. Blocks in the way are broken and dropped as items as the build reaches them (in Creative they are just removed). Blocks that hold data, like chests, furnaces, hoppers, signs, beds, and spawners, are never removed. Neither are unbreakable blocks, redstone parts, pistons, rails, TNT, infested blocks, or blocks you can't get back, like budding amethyst and eggs. The build stops there and the block shows up red on the hologram. Clearing never updates the blocks around it, so nothing outside the farm falls, pops off, or fires.

Every player has their own session at a table: their own menu page, farm, materials, swaps, clipboard, hologram and build. Players can use the same table at once without affecting each other, and two players' farms at one table can't overlap.

Discard returns the materials and removes the hologram. If the build has started and is not finished, Discard also takes apart the blocks the table placed and gives them back, along with anything stored in its chests and hoppers. It takes them apart a few dozen per tick, so large farms don't lag the server, and you can pick a new farm right away. Items delivered in Creative are never handed back. It asks for a second click first. Blocks you changed, or that were already there, are left alone. A block you turned or replaced is also left alone; opening a door doesn't count as a change. A finished farm is never taken apart.

The build refuses a farm that reaches above or below the world, or past the border. A block is counted as already built only when its direction, half and axis match, so a hopper or stairs facing the wrong way is replaced. If the farm's file changes after the build starts, the build can't be resumed. Discard it and start again; its blocks are left in place.

When you anchor or turn the hologram, the table checks the spot right away: the whole hologram turns red and the reason shows when the farm would leave the world, overlap another farm, or enter protected land. Farms from different tables can't overlap either, and a build never breaks blocks inside another farm's area. A finished farm keeps its area even after you move on to another farm or break the table, so nothing can be built over it. Once less than half of its blocks are left, the area is free again. Builds and take-aparts only run while the player who started them is online.

The table builds with the permissions of the player who started the build. It won't start a farm that touches spawn protection, land outside the world border, or a claim that player can't build in, and it won't build in Adventure mode. If a spot becomes protected during the build, the build stops there until you press Try Again. Discard skips blocks the discarding player isn't allowed to break. Claim mods that use Fabric's block break event are respected.

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

The mod jar is `build/libs/the_dream_table-1.1.4-beta.jar`. Jars suffixed with `sources` or `dev` are for development only.
