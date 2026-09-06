# Tree pass: fell, collect, plant

The pass finishes all captured trees, collects live item drops inside the fully loaded named area, waits 60 client ticks with no drops, and then rebuilds the planting grid from the configured species, pattern and spacing. The grid is anchored to the area's minimum X/Z, not old trunk locations. Occupied grid cells are skipped. Collection does not request block breaking or placement. Inventory shortages or inaccessible drops stop collection with a diagnostic. There is no artificial post-chop settling delay; normal action cleanup and live log-clear verification remain.

Pause/rejoin during collection rescans live drops. Once saved, the planting grid is retained across pause/rejoin. An already-in-progress planting phase retains its existing saved queue rather than changing coordinates halfway through. Older tasks with no selected planting species block before felling; create a configured pass from Tree Farm setup. Already planted sites from a previous build are not undone.

Tool choice estimates remaining cuts and climbing effort rather than comparing one log's break speed. The installed Tinkers lumber axe's `detectTree` method identifies whole-tree operation; otherwise the estimate uses captured logs in its centered 3x3x3 cube. Single-log jobs receive no multi-block bonus. Estimates and candidate slots are debug-logged and need runtime confirmation.

Last-run error: `Tree target observation failed: captured tree changed before felling`. The old observer rejected any missing captured log. The updated observer tolerates air (including collateral lumber-axe removal), but still rejects unexpected replacement blocks. The live backend checks remaining log identity and reach before damage and confirms the clear postcondition before queueing replanting.

## Physical checks

- With multiple trees and a lumber axe, verify all trees finish before any new sapling is placed. Verify the intervening pickup phase collects the drops, followed by planting all saved sites.
- Pause/resume during collection and again during planting. Neither should restart felling or lose planting sites.
- With insufficient inventory space, verify collection reports remaining drops and does not silently begin planting. Free space and retry.
- Test an empty plot and a 2x2 planting pattern. The collection phase may be brief when no drops exist, but planting must retain the selected pattern.
- Check that already-missing logs no longer fail the next tree, and the stand-back approach still reaches bottom logs.

The live collection phase and new sequence require physical validation. Automated tests cover ordering, checkpoint round-trips and collection suspension; they do not prove item pickup in Minecraft. Drops outside the named area's bounds and drops that appear after the quiet window are not claimed collected. Baritone is unchanged.
