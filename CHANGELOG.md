# The Dream Table 2.2.0-beta

Release date: 2026-10-28

This beta improves multiplayer farm management, repair safety, and the table interface while preserving existing worlds and farm layouts.

## Interface and accessibility

- Rebuilt the table menu around persistent Build, Farms, Schematics, and Settings tabs.
- Added compact and wide layouts that keep handled inventory slots safe, reachable, and stable while the window changes size.
- Fixed responsive HUD placement at every supported interface size: cards, filters, buttons, search fields, and the scrollbar stay inside the table instead of overlapping or leaking outside the panel.
- Wide collections now use their available space as two balanced columns, while focused farm workflows stay centered. Informational text also centers and wraps within its visible area.
- Improved keyboard focus, narration, clipping, scrolling, and menu routes for custom controls.
- Moved Honey Farm from Plants to Resources so its category matches the resources it produces.

## Shared farms

- Finished farms now have permanent identities that keep their custom name, saved layout, sharing data, alerts, and preferences through Repair and restarts.
- Owners can invite players as Viewers or Operators. Invitations expire, must be accepted, and never make a farm public.
- Viewers can inspect authorized farms; Operators can start Repair and manage the owner's Keep Loaded selection without taking ownership.
- Added favorites, farm tracking, a direction-and-distance HUD, and private Farm alerts. My Farms now has a dedicated favorites-only filter and a Favorites First sort option.
- Expanded the selected farm view with the player's role, running or unavailable state, Keep Loaded state, and distance or dimension alongside storage, condition, repair, and collaborator information.

## Alerts and repair safety

- Added persistent alerts for missing blocks, full storage, unavailable designs, and Repair outcomes, with acknowledged history for each authorized player.
- Active problems remain visible to authorized collaborators, while past alert history stays private to events after access was accepted.
- Alert requests are issued once per farm page so rendering cannot invalidate an acknowledgement token.
- Repair now retains the finished farm record during concurrent overlap checks, preserving its identity, name, grants, and alerts.
- Resuming or completing Repair resolves earlier Repair-blocked and Repair-failed alerts while keeping their history.
- Repair now handles partial minecart/passenger pairs safely and rolls back only entities created by a failed attempt.

## Compatibility and safety

- Existing finished farms, legacy Repair progress, page ids, layouts, and Keep Loaded selections migrate with safe defaults.
- Network actions revalidate the authenticated player, open table, distance, stable farm key, role, request token, and bounded input.
- Reduced server limits reject new oversized changes without deleting already accepted saves.

## Verification

- Automated regression coverage includes legacy migration, authorization, concurrent Repair leases, invitation expiry and limits, alert privacy and acknowledgement, and menu layout safety.
- `./gradlew clean build --console=plain` completed successfully for the local release artifact.

## Known limitation

- Manual in-game screenshots are still required at 320x240, 416x240, 420x240, 640x360, and 854x480 because Minecraft cannot be launched in this environment.
