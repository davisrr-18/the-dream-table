# The Dream Table 2.1.0-beta

Release date: 2026-10-26

This release focuses on reliability, usability, schematic compatibility, and safe multiplayer behavior. It does not add a new farm mechanic; it makes the existing build workflow easier to understand and safer to resume, repair, dismantle, and share.

## Added

- Construction diagnostics with typed `BuildIssue` records for missing materials, protected areas, obstacles, unloaded chunks, placement failures, repeated failures, and unexpected errors.
- Issue details synchronized to the owner's client and shown in the Work page with the affected location, block or item, attempt count, and recovery guidance.
- My Farms filtering by text, status, dimension, and loaded state, with deterministic sorting and distance-aware ordering.
- My Farms storage details for containers, capacity, stored item counts, block count, missing blocks, and changed blocks.
- Repair survey showing what is missing, what the player changed, and which materials Repair will use.
- Explicit Take Down confirmation naming the farm and its coordinates before removal.
- Safe item handover through a shared `Handover` path; overflow drops at the player's feet and is reported once per operation.
- Imported schematic validation for Vanilla Structure NBT, Sponge, and Litematica formats.
- Import diagnostics for unsupported blocks, missing palettes, malformed data, size limits, step limits, height limits, and Creative-only content.
- Server schematic notes and rejected-file information for dedicated-server clients.
- Exact finished-farm layouts, including block-entity NBT, persisted under unique `LayoutId` values.
- Stable-key My Farms actions through an authenticated `FarmCarePayload` protocol.
- Independent area reservations for simultaneous dismantle jobs, with compatibility for legacy saved jobs.
- Regression coverage for diagnostics, filtering, finished-layout persistence, repair surveys, schematic validation, stable farm selection, dismantle reservations, and one-time handover notices.

## Changed

- Farm building now reports and preserves the reason a step stopped instead of exposing only a generic blocked state.
- Repair rebuilds only missing or replaceable farm spots; solid blocks changed by the player are left in place and counted separately.
- Repair and Take Down always use the exact design saved with the finished farm, even when the current farm schematic changed or disappeared.
- My Farms actions no longer address a farm by its mutable list index. The server resolves the submitted stable key again for the authenticated owner and open table.
- Repair previews now use the exact saved layout ID sent in the My Farms list.
- Finished farm storage and condition information is refreshed while the farm page is open.
- Imported structures are reloaded at most once per second for the same world, avoiding repeated filesystem work from menu clicks.
- Network payloads cap list and string sizes at their protocol boundaries.
- UI text and spacing were unified across Work, My Farms, My Schematics, filters, cards, warnings, and confirmation states.
- The project now has explicit agent governance in `AGENTS.md`, scoped technical rules under `.cursor/rules/`, and durable architecture/build-safety guidance.

## Compatibility and safety

- Existing finished-farm saves without `LayoutId` are migrated on load with safe unique IDs.
- Existing dismantle saves without `AreaSpot` continue releasing their legacy reservation key.
- Existing session, material, speed, farm-area, and schematic data keeps its previous NBT keys and defaults.
- Server-side ownership, table distance, open-screen validation, build permissions, and per-step break/place checks remain authoritative.
- No block is removed during Repair when a solid replacement belongs to the player or world.

## Verification

- `./gradlew clean build`
- 73 automated tests passed with zero failures.
- Build, test, jar, remap, source-jar, and access-widener checks completed successfully.

## Known limitation

- If a minecart is destroyed while its passenger remains, Repair currently does not reconstruct only the missing vehicle. This is isolated for a follow-up because fixing it safely requires remounting the existing entity rather than spawning a duplicate.
