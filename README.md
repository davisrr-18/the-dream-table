# Redstone Dreams

Alpha `1.0.1-alpha` for Minecraft **1.21.1**, using Fabric Loader and Fabric API.

The Dream Table picks a classic farm, shows the hologram in the world, and places one block per second after the materials are delivered.

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
4. **R** rotates the farm. The key can be changed in Controls, under Redstone Dreams.
5. Hold the material and right-click the clipboard to deliver it. The line is struck through when the count is met.
6. Return to the table and click Build. The position locks, and the build places one block per second.

Farms in this alpha: sugar cane, bamboo, crops, mob farm, and iron farm. The ground needs to be clear. A block that gets in the way shows up red on the hologram.

Discard returns the materials and removes the hologram.

Under **My Schematics**, the table reads `.nbt`, `.schem`, and `.litematic` files from these instance folders:

- `schematics`
- `config/worldedit/schematics`
- `config/litematica`
- `config/redstone_dreams/structures`
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

The mod jar is `build/libs/redstone_dreams-1.0.1-alpha.jar`. Jars suffixed with `sources` or `dev` are for development only.
