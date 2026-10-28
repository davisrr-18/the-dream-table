# The Dream Table 2.2.0-beta

Release date: 2026-10-27

This beta improves multiplayer farm management, repair safety, and the table interface while preserving existing worlds and farm layouts.

## Interface and accessibility

- Rebuilt the table menu around persistent Build, Farms, Schematics, and Settings tabs.
- Added compact and wide layouts that keep handled inventory slots safe, reachable, and stable while the window changes size.
- Improved keyboard focus, narration, clipping, scrolling, and menu routes for custom controls.

## Shared farms

- Finished farms now have permanent identities that keep their custom name, saved layout, sharing data, alerts, and preferences through Repair and restarts.
- Owners can invite players as Viewers or Operators. Invitations expire, must be accepted, and never make a farm public.
- Viewers can inspect authorized farms; Operators can start Repair and manage the owner's Keep Loaded selection without taking ownership.
- Added favorites, farm tracking, a direction-and-distance HUD, and private Farm alerts.

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
