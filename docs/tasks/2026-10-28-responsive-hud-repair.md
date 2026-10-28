# Task: Responsive HUD repair

- Status: Complete; approved for commit, push, and release
- Owner/model: Codex orchestration; Luna High investigator/reviewer as needed
- Started: 2026-10-28
- Last checkpoint: 2026-10-28 closure approved, America/Sao_Paulo

## Objective

Make the Dream Table HUD contain all controls at every supported GUI resolution and scale, without scrollbar collisions or content leaking outside the viewport.

Also make wide-mode composition use its available content area intentionally: single-column workflows are centered, while collection toolbars and cards use both columns.

Center free-standing informational text, make favorites an explicit My Farms filter and sort, and improve farm operation/condition information using already-authorized client data.

## Acceptance criteria

- [ ] Header, tabs, and toolbar stay fixed while only page content scrolls.
- [ ] Cards, buttons, search fields, and filters remain inside their panel and viewport bounds.
- [ ] The scrollbar stays outside all content columns in compact and wide modes.
- [ ] Layout is correct at 320x240, 384x240, 416x240, 420x240, 640x360, and 854x480 logical pixels.
- [ ] Targeted regressions, full tests, and clean build pass.

## Scope

- In: `screen`, `screen/page`, `screen/ui`, and targeted tests for responsive HUD layout.
- Out: gameplay behavior, network protocols, persistence, visual theme redesign, and unrelated local changes.

## Decisions

- Treat the supplied screenshots as evidence of boundary violations, then prove each correction with geometry tests.
- Preserve the existing uncommitted My Farms, scrollbar, and Honey-category corrections; integrate them only when compatible.
- Use Luna High delegation for independent investigation and review, with the main agent owning integration.
- Preserve vertical action flows in Work, Speed, Farm, and Invites; make only independent card collections responsive grids.
- Make `ScreenGeometry`/`Viewport` the sole source of current layout before any page computes content height.
- Treat `Viewport.cardX()` as the first collection column only. Add a separate centered-flow anchor for single-column pages and a content-span anchor for collection toolbars.
- Keep per-farm details on the selected Farm page and use only the existing list snapshot for My Farms cards; do not add one details request per card.

## Working set

- Changed: existing local changes in `FarmOptions`, `LoadedFarmsPage`, `Viewport`, and their tests.
- Relevant: `DreamTableScreen`, `ScreenGeometry`, `Viewport`, `PageContext`, `CategoryPage`, `SchemesPage`, `MaterialsPage`, `SwapPage`, `AlertsPage`, `FarmPage`, and responsive tests.
- Composition focus: `DreamTableScreen`, `Viewport`, `SearchBox`, `PageContext`, `LoadedFarmsPage`, `SchemesPage`, `WorkPage`, `SpeedPage`, `FarmPage`, `InvitesPage`, and focused layout tests.
- Farm-information focus: static notes in collection/flow pages, `LoadedFarmsPage`, `FarmListing`, `LoadedFarmsView`, `FarmPage`, English translations, and targeted tests.

## Verification

- Passed before this task: focused and full test suites for the existing local corrections.
- Passed in Marco 2: focused `ViewportTest`, `FarmListingTest`, `FarmPageLayoutTest`, and `LoadedFarmsPageLayoutTest`; `git diff --check`.
- Passed in Marco 3: independent Luna High review, full `./gradlew test`, `./gradlew clean build`, repeat full suite after interaction-coverage addition, and `git diff --check`.
- Passed in composition Marco 2: focused layout tests for viewport anchors, centered flows, My Farms, and Schematics; integrated `./gradlew test`; `git diff --check`.
- Passed in composition Marco 3: independent Luna High review, `./gradlew clean build`, repeat full `./gradlew test`, interaction-bound integration test across all target resolutions, and `git diff --check`.
- Remaining manual limitation: no Minecraft client session is available here for screenshot capture at each GUI scale.

## Checkpoint

- Completed: Marco 1 approved and completed. Luna High investigator audited the responsive HUD without edits; its report identified the resize-order defect, remaining linear list pages, and unbounded Farm-page item placement.
- Current state: `Viewport` installs new geometry before responsive page height is computed, fixing the 416/420 px resize boundary. The scrollbar remains in a dedicated gutter. Category, Materials, Swap, Alerts, and My Farms share responsive card-grid geometry. Farm-page item and repair rows wrap before crossing a card edge. The Alerts back control was corrected during integration to follow the last occupied grid row.
- Completed: Luna High review found no concrete defect. Its interaction-coverage observation was addressed with a viewport hit-test matrix for all supported resolutions. Full test and clean build are green.
- Completed: a Luna High investigator classified wide layouts. Collections already using the grid should remain unchanged; My Farms needs a full-width collection toolbar, Schemes needs a responsive grid, and Work/Speed/Farm/Invites need a centered single-column anchor. Requirements slots are excluded unless a concrete visual defect is supplied, because their immutable handler coordinates need separate validation.
- Completed: main agent added collection-span and centered-flow anchors, wide collection search bounds, and explicit positioned controls. Luna High implementers updated My Farms/Schematics and Work/Speed/Farm/Invites in disjoint files. My Farms filters are 2x2 in wide; Schematics uses responsive grids; single-column workflows are centered in wide while compact is unchanged.
- Completed: independent Luna High review found no functional defect. Its P2 coverage observation was addressed with `PageContextLayoutTest`, which proves registered bounds, hit-test bounds, and focused activation agree for centered flow controls across all six target resolutions. Clean build and repeat full suite are green.
- Completed: Luna High investigation found that Favorites already exists as an obscure `Show` enum state, while favorite-first ordering is absent. The current snapshots already expose status, storage, missing/changed blocks, role, distance, keep-loaded selection, availability, and quota state; selected-farm details add exact counts and required repair items. Static notes still use independent left-aligned renderers.
- Completed: shared collection/flow note drawing now centers free-standing messages across their responsive visual region. Schematics, Alerts, Swap, Invites, and My Farms use it. My Farms now has an explicit Favorites All/Only filter and Favorites First sort. The selected Farm page summarizes role, operating availability, keep-loaded state, and distance/dimension, in addition to its existing storage, condition, repair, and collaborator details. No payload or persistence changes were needed.
- Completed: Luna High independent review found one P2 gap in Farm's unavailable/loading message: it bypassed the shared centered flow-note renderer. The main agent corrected it, then reran focused Farm/PageContext tests, the full suite, a clean build, and `git diff --check`, all green. The review found no other concrete geometry, hitbox, favorite-flow, compatibility, or translation defect.
- Completed: closure approved for the verified HUD, favorites, and farm-information update. Commit, push, and 2.2.0-beta release were explicitly authorized.
- Blockers: in-game launch is unavailable in this environment.
