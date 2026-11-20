# The Dream Table 1.5.0

Release date: not yet released

Bug fixes found in a code review of 1.4.0. The rest of this update is still in progress.

## Fixes

- A build far from you keeps the chunk of its next block loaded from the first block, so it no longer stops when that chunk unloads.
- Aiming a new farm over a finished farm whose blocks are mostly gone no longer removes that farm from My Farms. It stays listed until you take it down.
- Discarding a started build whose farm file is gone now says that its blocks were left in place.
- Above 800 schematics, some schematic buttons acted as material swaps. My Schematics now shows the first 800 and says when there are more.
- Reopening a table on the Farm Info page returns to the farm's page, not to My Farms.

## Good to know

- The network protocol changed (version 5): a 1.5.0 server and its players must all run 1.5.0.
