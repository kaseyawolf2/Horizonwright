# Tree pickup movement and automatic inventory moves

The reported run reached 27 remaining drop stacks, failed navigation with `Safety firewall blocked container mutation`, then failed its retry path calculation. The collector requested the item block at 249,85,-76. This is not evidence the item was floating; it may have rested on different-height terrain.

Inspection of the installed Baritone InventoryBehavior shows it reads global `allowInventory` on tick, not the navigation calculation context. Horizonwright previously suppressed it only while constructing that context. Navigation now suppresses automatic Baritone inventory moves until terminal cleanup, then restores the prior preference. No firewall permissions or Baritone jar bytes are changed.

Pickup tracks the target's current block position, cancels stale goals after movement/falling, and waits for navigation cleanup before resubmitting. After failed direct navigation or unsuccessful pickup, it tries four cardinal neighboring positions at the drop's current Y. These are candidate goals, not guaranteed walkable cells; Baritone still decides path feasibility. Five unsuccessful approaches report failure and retain the planting checkpoint rather than silently claiming collection.

Physical checks:

- Collect drops on level ground and in a reachable lower area within the named bounds.
- Confirm no `container mutation` failure while watching without manual interaction.
- Confirm moving/falling drops cause a goal update and remaining drops are collected before planting.
- Verify the Baritone inventory preference is restored after completion, stop and failure.

Bounds are unchanged: drops outside the named area's three-dimensional bounds are not included. Candidate position, target movement, player feet Y and on-ground state are debug-logged. The remaining in-game pickup failure requires physical confirmation; inventory-source attribution was not present in the original packet log.
