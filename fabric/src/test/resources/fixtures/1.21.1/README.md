# Minecraft 1.21.1 save-format fixtures: expected values

Generated on the Minecraft 1.21.1 (Yarn 1.21.1+build.3, Fabric) code of The Dream Table, by the mod's own write code.
Only the generator's own UUIDs, ids and names are used; no real player data.

Generators (scratch copy, not in the repository):
- `fabric/src/test/java/com/davisr/thedreamtable/farm/FixtureGeneratorTest.java` writes the five world-saved files.
- `fabric/src/test/java/com/davisr/thedreamtable/block/entity/BlockEntityFixtureGeneratorTest.java` writes the two table files.

Run: `./gradlew --console=plain :fabric:test --tests '*FixtureGenerator*'` in the scratch copy. Result: BUILD SUCCESSFUL, 7 tests, 0 failures.

## File format

Every world-saved file is GZIP NBT (`NbtIo.writeCompressed`), with the root shaped as PersistentStateManager saves it:

```
root {
  DataVersion: int 3955        (NbtHelper.putDataVersion, 1.21.1 save version)
  data: compound               (exactly the compound the state's writeNbt returned)
}
```

The two table files are not wrapped. `table_block_entity.dat` is the compound from `DreamTableBlockEntity.createNbt(registries)`.

## Common values

| Name | Value |
|---|---|
| PLAYER_A | `11111111-1111-1111-1111-111111111111` |
| PLAYER_B | `22222222-2222-2222-2222-222222222222` |
| SHARED (legacy session key) | `00000000-0000-0000-0000-000000000000` |
| FARM_A instance id | `50000000-0000-0000-0000-000000000001` |
| FARM_B instance id | `50000000-0000-0000-0000-000000000002` |
| FARM_C instance id | `50000000-0000-0000-0000-000000000003` |
| Farm key 1 | `minecraft:overworld|30000000-0000-0000-0000-000000000001` |
| Farm key 2 | `minecraft:the_nether|30000000-0000-0000-0000-000000000002` |
| TABLE | overworld (100, 64, 100), stored as `BlockPos.asLong()` = `27487791104064` |

`BlockPos.asLong()` in the stored values: X in bits 38-63, Z in bits 12-37, Y in bits 0-11. All stored longs below were checked against that layout.

## farm_areas.dat (3054 bytes)

`data.Areas` is a list of 5 compounds, in this order.

**[0] BUILD (PLAYER_A)**
- `Kind` = `BUILD`, `Player` = PLAYER_A, `Table` = 27487791104064, `Spot` = 0
- `Box` = [120, 64, 120, 126, 70, 124]
- No `Farm`, `Site` or `InstanceId`.

**[1] DISMANTLE (PLAYER_B)**
- `Kind` = `DISMANTLE`, `Player` = PLAYER_B, `Table` = 27487791104064, `Spot` = 109951164416064 (= (400,64,400).asLong())
- `Box` = [140, 64, 140, 146, 70, 144]

**[2] FINISHED sugarcane, PLAYER_A, custom name (FARM_A)**
- `Kind` = `FINISHED`, `Player` = PLAYER_A, `Farm` = `sugarcane`, `InstanceId` = FARM_A
- `CustomName` = `Riverside Cane`
- `FarmSignature` = -2436952895899130566
- `Site`: `Table` = 27487791104064, `Anchor` = 54975582208064 (= (200,64,200)), `Facing` = `east`, `Mirrored` = 1 (true)
- `Spot` = 219902328832259 (= anchor.asLong() * 4 + 3, where east has horizontal index 3)
- `Box` = [197, 64, 193, 204, 70, 207]
- `Swaps`: one entry, `From` = `minecraft:oak_planks`, `To` = `minecraft:spruce_planks`
- `LayoutId` = `saved:sugarcane#de2e3440b192a53a@7fe404bd-9702-49a1-9493-ec34bf81800e` (the UUID part is random per generation; this is the value stored in the file)
- `Layout`: present. Palette 20 entries, 280 steps (`Item`/`State`/`X`/`Y`/`Z` arrays of 280), 13 `BlockData` entries, empty `Entities`.
- `PlacedSteps` = [-5, -1, -1, -1, 16777215]: every step is placed except step index 2 (bit 2 is clear).
- `PaidSteps` = [3]: steps 0 and 1 are paid.
- `Alerts`: `ActiveAlerts` has 2 entries: `BLOCKS_MISSING` (Quantity 4, Time 120) and `STORAGE_FULL` (Quantity 1, Time 130). `NextAlertEvent` = 3.
- `FinishedAt` = 1795173856588 (a `System.currentTimeMillis()` value, fixed in the file; not an assertion target).

**[3] FINISHED bamboo, PLAYER_B, no custom name (FARM_B)**
- `Player` = PLAYER_B, `Farm` = `bamboo`, `InstanceId` = FARM_B
- `FarmSignature` = 5443182533435593035
- `Site`: `Table` = 27487791104064, `Anchor` = 82463373312064 (= (300,64,300)), `Facing` = `south`, `Mirrored` = absent (false)
- `Spot` = 329853493248256 (= anchor.asLong() * 4 + 0, south)
- `Box` = [294, 64, 297, 308, 70, 304]
- `Swaps`: empty list. No `CustomName`, no `Alerts`.
- `LayoutId` = `saved:bamboo#4b8a11acc90a114b@0e64649f-0ef9-452b-b0a0-f4f9ddb73df5`. `Layout` with 288 steps.
- `PlacedSteps` = `PaidSteps` = [-1, -1, -1, -1, 4294967295]: all 288 steps set (`every(template)`).

**[4] FINISHED crop, PLAYER_B, custom name, UNREADABLE (FARM_C)**
- No `Player` key. This record is the one the reader must keep unchanged: `FarmAreas.read` puts it in `unreadable`, and `writeNbt` writes it back as it was (see UnreadableAreaRecordTest).
- `Farm` = `crop`, `InstanceId` = FARM_C, `CustomName` = `Wheat Field`
- `FarmSignature` = 3357068785952451615
- `Site`: `Anchor` = 137438955520064 (= (500,64,500)), `Facing` = `north`, `Table` = 27487791104064
- `Spot` = 549755822080258 (= anchor.asLong() * 4 + 2, north)
- `Box` = [488, 64, 484, 513, 70, 516]
- `LayoutId` = `saved:crop#2e96b446393f6c1f@8d98f13d-5351-40cc-8550-c380ca26207a`. `PlacedSteps` and `PaidSteps` all set.

Expected reader results: `unreadableCount()` = 1; `finishedBy(PLAYER_A)` = [FARM_A]; `finishedBy(PLAYER_B)` = [FARM_B]; FARM_A `customName` = "Riverside Cane"; FARM_B has no custom name. `FarmAreas.read(...).writeNbt(...)` equals `data`.

## parked_projects.dat (288 bytes)

- `Players` (compound) has one key, PLAYER_A, with one project:
  - `SelectedFarm` = `sugarcane`, `MovedBuildDimension` = `minecraft:overworld`, `FarmFacing` = `south`
  - `AnchorX` = 500, `AnchorY` = 64, `AnchorZ` = -40
  - `Aim`: X 500, Y 64, Z -40, `Facing` = `south`, `Dimension` = `minecraft:overworld`, `Mirror` = 0
- `Held` (list) has one entry:
  - `Player` = PLAYER_B, `World` = `minecraft:overworld`, `Table` = 27487791104064 (TABLE)
  - `Project`: `SelectedFarm` = `sugarcane`, `MovedBuildDimension` = `minecraft:overworld`, `FarmFacing` = `east`, `AnchorX` = 700, `AnchorY` = 64, `AnchorZ` = 20

Expected reader results: `count(PLAYER_A)` = 1, `count(PLAYER_B)` = 0 (the PLAYER_B build is held, not parked). `reserves(overworld, ...)` is true where the kept build's footprint is.

## pending_refunds.dat (181 bytes)

- PLAYER_A: `minecraft:oak_log` = 12, `minecraft:dirt` = 1, `minecraft:lapis_lazuli` = 5
- PLAYER_B: `minecraft:sugar_cane` = 6, `minecraft:bamboo` = 3, `fixture_gone:old_gem` = 2 (an unknown item id, kept on record)

## farm_preferences.dat (220 bytes)

- `Protecting` (list): one entry, PLAYER_A.
- `Players` (list), two entries:
  - PLAYER_A: `Favorites` = [`minecraft:overworld|…0001`, `minecraft:the_nether|…0002`]; `Tracked` = `minecraft:overworld|…0001`; `Acknowledgements` = [`Farm` `minecraft:overworld|…0001`, `Cursor` 17]
  - PLAYER_B: `Favorites` = [`minecraft:overworld|…0001`]; no `Tracked`; `Acknowledgements` = [`Farm` `minecraft:the_nether|…0002`, `Cursor` 9]
- `…0001` is 30000000-0000-0000-0000-000000000001 and `…0002` is 30000000-0000-0000-0000-000000000002 (the farm keys above).

Expected reader results: `protectsFarms(PLAYER_A)` = true, `protectsFarms(PLAYER_B)` = false; `acknowledgement(PLAYER_A, key1)` = 17; `acknowledgement(PLAYER_B, key2)` = 9; `tracked(PLAYER_A)` = key1.

## farm_loader.dat (136 bytes)

- `Players`: PLAYER_A = [`minecraft:overworld|30000000-0000-0000-0000-000000000001`, `minecraft:the_nether|30000000-0000-0000-0000-000000000002`]
- `Players`: PLAYER_B = [`minecraft:overworld|30000000-0000-0000-0000-000000000001`]

These picks are written through `toggleKnown` (package-private, no server). The farm keys do not match any finished farm in farm_areas.dat, so the file tests the save format only, not My Farms resolution.

## table_block_entity.dat (529 bytes)

Compound from `DreamTableBlockEntity.createNbt(registries)`, not wrapped, no `id`/`x`/`y`/`z` keys (see the limitation below).

- `Sessions` (list, 1 entry), for PLAYER_A (`Player` = `11111111-...`):
  - `SelectedFarm` = `sugarcane`, `Page` = 0, `Phase` = `idle`, `SwapMaterial` = -1, `BlocksPerSecond` = 1050, `Kept` = 0, `Repair` = 0
  - `Materials` (list, 2 entries, no `Slot` key stored): list entry 0 is `minecraft:lapis_lazuli` count 5, list entry 1 is `minecraft:oak_planks` count 3
  - `Aim`: X 200, Y 64, Z 200, `Facing` = `east`, `Dimension` = `minecraft:overworld`, `Mirror` = 0
  - `FarmFacing` = `south` (default placement), no `PlacedSteps` (empty), no build started
- `Dismantling` (list, 1 entry), a discard job for PLAYER_B (`Player` = `22222222-...`):
  - `Farm` = `bamboo`, `FarmSignature` = 5443182533435593035
  - `Site`: `Table` = 27487791104064, `Anchor` = 82463373312064 (= (300,64,300)), `Facing` = `south`
  - `Host` = 27487791104064, `AreaSpot` = 82463373312064
  - `PlacedSteps` = [7] (steps 0, 1, 2 placed), `PaidSteps` = [1] (step 0 paid), `Next` = 287 (288 steps minus 1), `Removed` = 0, `Standing` = empty
  - `Swaps` empty, no `Layout`, no `Instance`/`Owner` (not a Take Down job)

Expected reader results: `session(PLAYER_A).getSelectedFarm()` = `sugarcane`; the session holds lapis 5 and planks 3; the aim is (200,64,200) east; the dismantling list has 1 job for PLAYER_B at farm `bamboo`; `createNbt` equals the file compound.

## table_block_entity_legacy.dat (291 bytes)

Pre-sessions flat format: `TableSession.write` output placed at the root, with no `Sessions` list. `readSharedState` reads it.

- `SelectedFarm` = `crop`, `Page` = 0, `Phase` = `idle`, `SwapMaterial` = -1, `BlocksPerSecond` = 1050
- `Materials`: list entry 0 is `minecraft:lapis_lazuli` count 2
- No `Aim`, no `Owner` (progress has no owner), no `Sessions`, no `Dismantling`

Expected reader results: the table reads one session under SHARED (`00000000-0000-0000-0000-000000000000`) with `getSelectedFarm()` = `crop`. Writing it back produces a `Sessions` list with that one entry, and reading the upgraded form again gives the same compound.

## farm_signatures.properties

The `FarmTemplate.signature()` of every built-in farm (`FarmOptions.ALL`) as the 1.21.1 build of the mod computes it, one `id=value` line each. `BuiltInSignaturesTest` requires this game to compute the same values: a build or take-down job saved by 1.21.1 holds one, and a different value reads as "the farm changed". Printed by a throwaway test in a scratch copy of `main` at 0042e7d (the 1.21.1 code, not recomputed here); regenerate it the same way only if a built-in farm's design changes on purpose. The `sugarcane`, `bamboo` and `crop` values equal the `FarmSignature` values above. Only `animal` (it has `minecraft:chain`) needed code: the hash maps `iron_chain` back to `chain`.

## Limitations and what the fixtures do not cover

- Parked project: built from the keys that `ParkedProjectsTest` uses, not from `TableSession.park` (it needs a `ServerWorld`). The `Aim` compound is from `AimedSpot.toNbt()`.
- Unreadable record: produced like UnreadableAreaRecordTest does, by removing `Player` from a record the 1.21.1 code wrote. This is the only edit to a written compound.
- Materials are placed through `TableSession.getMaterials().setStack`, not through the delivery path, so `Ledger`/`Deposited`/`Consumed` counts are empty or zero. `TableSession.deliverAll` needs a server player.
- The table block entity type is built by a test stand-in for the loader's `Content` seam, using the real `DreamTableBlock`. Because the type is not registered, `createNbtWithIdentifyingData` (which writes `id`/`x`/`y`/`z`) is not used. The chunk-level save of a real world will carry those keys too.
- The `FarmSignature` values and `LayoutId` hash come from the 1.21.1 built-in farm templates. A 1.21.11 reader should read them from the file and not recompute them from its own templates.
- `FinishedAt` values are timestamps from the generation run, fixed in the files.
