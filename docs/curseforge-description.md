# The Dream Table

The Dream Table is a block that builds vanilla farms for you. Choose a farm, place its hologram where you want it, deliver the required materials, and let the table build it block by block.

## How It Works

1. Craft the table using lapis lazuli, paper, and oak planks. You can also find it in the Redstone tab in Creative mode.
2. Choose a farm from the menu. A small preview appears above the table.
3. Aim at your desired location and right-click to place the hologram. The farm and its whole footprint are shown on the ground: green when the spot is valid, red when it is refused (and why), amber while the server checks it.
   - **R** rotates the farm and **M** mirrors it, for eight orientations.
   - **H** switches between the full farm and the current build layer.
   - **J** hides the farm's blocks, keeping only its outline, then only the aim marker, then shows everything again.
   - **G** opens the map placement mode (see below).
   - **P** stops placing, so right-click works normally again (open a chest, use an item) while your farm stays chosen, with its spot and the materials you delivered. Press it again to keep placing.
4. Deliver the required materials. The clipboard next to the table lists every block needed. **Deliver All** moves everything the farm still needs from your inventory in one click, and **Deliver From Nearby** also takes it from chests, barrels and placed shulker boxes within 6 blocks.
5. Press Build to start construction. The table places blocks one by one and waits if materials are missing, automatically resuming when you deliver them.

All keys can be changed in Controls.

## Map Placement Mode

Press G with a farm chosen to lift off into a free camera and place the farm from above. The edge of the table's reach is marked with corner brackets on the ground, the crosshair aims the farm, and a left click places it. Fly with WASD, Space and Shift, hold Ctrl to go faster, scroll to move forward or back, and turn or mirror the farm with R and M. The camera stays inside the table's reach, cannot pass through solid ground, and your character stays where it is.

## Available Farms
- Sugar Cane Farm
- Bamboo Farm
- Cactus Farm
- Crop Farm
- Honey Farm
- Mob Farm
- Creeper Farm (gunpowder)
- Gold Farm (Nether only)
- Iron Farm

Each built-in farm has a **How It Works** page: how it works, what it needs to run, what it produces, and tips. More farms will be added over time.

## Your Own Schematics
Import your own structures using .nbt, .schem, or .litematic files of up to 512 blocks on any side and 16,384 blocks in total.
Place your files in the appropriate schematics folders, and they will appear under My Schematics. They can be built just like the included farms.
Files that cannot be used are listed with an explanation. Warnings about missing mod blocks or Creative-only blocks appear before construction begins.
On servers, players can access the server's schematics without needing the files on their own computers.

## Building Options
- **Build Speed:** Choose Slow, Normal, Fast, or a custom speed (the slider also works from the keyboard).
- **Material Swapping:** Replace blocks with alternatives of the same shape, such as spruce planks instead of oak planks.
- **Pause and Resume:** Stop and continue construction at any time.
- **Discard Unfinished Builds:** Take apart an unfinished build and recover the materials you delivered.
- **Build Error Messages:** If construction stops, the menu explains which block caused the problem and why.
- **Live Progress:** A line at the top of the screen counts up your build while you are nearby.
- **Creative Mode:** Build farms without supplying materials.

## My Farms
My Farms lists all your completed farms and lets you manage them from one place.
- View farm locations and sort or filter your list.
- Mark farms as favorites.
- Track farms using a HUD that displays their direction and distance.
- Keep farms loaded while you play, keeping their chunks active within the supported limits of up to 64 chunks per farm.
- Repair farms by replacing missing or damaged blocks. Repairs and take-downs keep a farm's rotation and mirror.
- Take farms down and recover the blocks placed by the table.
- Receive alerts about full storage, missing blocks, and completed or failed operations. A farm with an alert is marked with a red warning sign.
- Turn on **Protect My Farms** so other players cannot break the structure of your finished farms, while crops and other produce stay harvestable.

## Safety
- The build clears everything inside the farm's area layer by layer, without drops: plants, water, lava and unused blocks. Chests and other containers, spawners, redstone parts, pistons, rails, blocks containing data, other farms, and blocks you are not allowed to break are never removed.
- Farms cannot be built on top of one another, and cannot cover a Dream Table.
- Spawn protection and supported land-claim systems are respected.
- A Dream Table holding another player's materials or build cannot be broken by someone else.
- Items with custom data (renamed, enchanted, or shulker boxes with contents) are never taken as materials.
- If your inventory is full, returned items are dropped at your feet.
- Builds, repairs, and take-down operations persist across server restarts.

## Multiplayer
Each player has their own session at the same table, including their selected farm, materials, and build progress. Players can use the same table without interfering with one another.
Repeated actions are rate-limited to help prevent spam from affecting server performance.

## For Server Owners
The config/the_dream_table.properties file contains Keep Loaded settings, per-player limits, and switches for Deliver From Nearby, Protect My Farms, Show Other Farms and the table break guard.
Operators (permission level 2) get `/dreamtable reload` to reload the config and schematics without a restart, and `/dreamtable status` for diagnostics.
The mod also provides a block tag for blocks that should never be cleared during construction.

## Languages
English and Brazilian Portuguese.

## Inspiration and Credits
The Dream Table was inspired by a Minecraft Bedrock idea for automated farm construction.
The Dream Table is an independently developed Minecraft Java Edition implementation, with its own features and ongoing development.
Credit and thanks to the original creator for the inspiration.
Original project:
https://www.curseforge.com/minestone-br

## Requirements
Minecraft 1.21.1 with either of the following:
- Fabric: Fabric Loader 0.16 or newer and Fabric API.
- NeoForge: NeoForge 21.1.

Install the mod on both the client and the server, and make sure both run the same version.

Bug reports and suggestions are welcome!
