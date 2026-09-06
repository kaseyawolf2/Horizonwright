# Tree pass: fell, collect, plant

The pass now saves cleared planting frontiers while finishing all captured trees. It then collects live item drops inside the fully loaded named area, waits 60 client ticks with no drops, and only then plants the deferred saplings. Collection does not request block breaking or placement. Inventory shortages or inaccessible drops stop collection with a diagnostic; the saved planting queue remains available for retry.

Pause/rejoin during collection rescans live drops. Pause/rejoin during planting retains the original species, footprint and remaining planting frontier. Older unfinished checkpoints migrate by deferring their current replant frontier before continuing the remaining felling list. Already planted sites from a previous build are not undone.

Last-run error: `Tree target observation failed: captured tree changed before felling`. The old observer rejected any missing captured log. The updated observer tolerates air (including collateral lumber-axe removal), but still rejects unexpected replacement blocks. The live backend checks remaining log identity and reach before damage and confirms the clear postcondition before queueing replanting.

## Physical checks

- With multiple trees and a lumber axe, verify all trees finish before any new sapling is placed. Verify the intervening pickup phase collects the drops, followed by planting all saved sites.
- Pause/resume during collection and again during planting. Neither should restart felling or lose planting sites.
- With insufficient inventory space, verify collection reports remaining drops and does not silently begin planting. Free space and retry.
- Test an empty plot and a 2x2 planting pattern. The collection phase may be brief when no drops exist, but planting must retain the selected pattern.
- Check that already-missing logs no longer fail the next tree, and the stand-back approach still reaches bottom logs.

The live collection phase and new sequence require physical validation. Automated tests cover ordering, checkpoint round-trips and collection suspension; they do not prove item pickup in Minecraft. Drops outside the named area's bounds and drops that appear after the quiet window are not claimed collected. Baritone is unchanged.
