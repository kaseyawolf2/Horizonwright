# Inventory caching, mining controls and statistics

The excavation setup page has an **Order** button, **Spiral width (1–64 blocks)** field and **Walk while mining** toggle. New GUI tasks default to width 8 and walking on. Existing tasks retain their saved behavior. Open **Tasks → Fallback tasks → Edit settings** to change excavation/quarry order, width, walking, storage and repair bindings. Pause active work first, then **Save & resume**. Walking/service changes retain progress; changing the effective traversal rescans the fixed volume and resets statistics, skipping existing air. Pending inventory recovery and linked service tasks must finish before editing. Fallback work keeps its existing scheduler priority and yields to higher-priority tasks.

## Orders

- **Square spiral**: clockwise rectangular rings inward, clipped by the work-area shape. This retains `spiral-v1` compatibility.
- **Circle spiral**: clockwise concentric Euclidean shells inward, clipped by the work-area shape. Rectangular areas include their corners too. It stores one bounded horizontal lookup reused by every layer, not a list of the entire excavation volume.
- **Chunk by chunk**: the original chunk/band order.
- **Rows along X / Rows along Z**: alternating-direction rows, avoiding a return to the same edge after each row. Minecraft's horizontal axes are X and Z; Y still descends one layer at a time.

Traversal identity remains part of the geometry key. All orders retain exact-target confirmation, interruption checkpoints, cleared-layer verification and the final full-volume verification. The incremental background audit checks up to 64 previously processed positions per confirmed block instead of 4,096; complete verification remains unchanged.

## Walking during digging

New GUI tasks default to walking enabled; toggle it off for comparison. When the current block is within reach, a scoped player movement input can walk toward the next target while the current block is being damaged. Every movement tick requires an active lease, the same player/world, a closed GUI, ordinary grounded movement, a clear swept body volume, solid support across the swept footprint or a verified one-block descent, and a reach margin for the current target. The support check treats the mining target as already removed and requires dry, solid support below it before allowing a step. It does not jump, sprint, or swim.

Cancellation disables the movement input immediately and restores the preceding input object on client-thread cleanup. Global keyboard bindings are not set by this walking driver. Baritone still handles longer approaches and obstacles. Movement ends for block confirmation and inventory/service handoffs: this is not an uninterrupted multi-target Baritone mining process. Terrain and required confirmation boundaries can still produce pauses. No measured speedup is claimed before physical comparison.

## Stats

During active excavation, the HUD shows the layer Y/timer, task ETA, confirmed target blocks broken, average blocks/minute, running time, completed-layer count, last/best layer times, and processed volume. Lines wrap at the current screen width. F1 and the debug screen hide the extra HUD. Stats also appear in task details and `/hw task <id>`; those remain available after completion.

Counters are checkpointed with progress and at approximately one-second intervals while the excavation runner is stepping. Pause and reconnect exclude offline/suspended time. Time between steps, including ordinary inventory preparation while the task remains active, contributes to running time. The metric counts confirmed target breaks for which the live backend started digging, not pre-existing air, skipped blocks, or unobserved collateral multi-block effects. A newly upgraded older task starts a new measurement baseline without inventing historical timing.

ETA uses measured processed-volume throughput after a ten-second warmup. It assumes the remaining volume has comparable density and travel cost; large empty layers, obstacles, repairs and future chores can change it substantially. It is not a promise of completion time. Completed layer timings include their verification pass.

## Bag cache

`/hw inventory` shows current item-backed bag contents or the last observed contents of server-backed bags, with counts and observation age. Cache entries copy stacks, keep metadata/NBT, are bounded to 128 entries, and are cleared with the connection/session or after an uncertain transfer. Persistent bag IDs distinguish carriers; bags without IDs use their carrier fingerprint and inventory location.

Current complete bag item data is used to skip opening bags with no needed retrieval or eligible surplus to accept. This consults counted working reservations and the actual adapter's filters. First-open/unknown contents are still inspected normally. Last-seen server-only bag contents and AE2 network contents cannot prove that supplies or space are available, and are never used to authorize a click or to skip checking a needed server-only bag. All actual transfers still use the live synchronized container and server-confirmed click sequence.

## Freecam and tree tools

The optional adapter uses the installed Freecam 1.0.9 controller's `isActive` and `isPlayerControlled` state. Camera-only movement bindings no longer preempt automation. Player-control mode, attack/use/drop/hotbar input, and normal movement after leaving freecam retain manual takeover. It does not infer camera state from the F4 key alone.

Tool classification now includes the exact TConstruct Hatchet and LumberAxe implementations even without Forge tool-class registration. Inventory preparation, spare-tool decisions and tree-tool selection use compatible classifications. Broken tools remain ineligible. The September 9 recorded tree failure was a sapling-reserve shortage; the message now includes the available count, reserve, and planting-site count.

## Physical checks

Use the canonical `GTNH-2.9.0-Beta3-Horizonwright` instance and a disposable area.

1. Run `/hw inventory` with a filled Forestry bag, an Adventure Backpack tool slot, and a ModdedNetwork bag. Open/change each manually and inspect again. Known item data must refresh; server-only data must remain labeled last observed until reopened by automation. Complete a bag-to-player-to-chest unload with multiple batches and confirm counts/reservations remain correct.
2. Queue fresh rectangular and circular areas with each ordering. Include negative coordinates and a thin rectangle. Verify complete coverage, top-to-bottom layers, and identical next targets after pause/rejoin. Resume an older task and verify its original order remains intact.
3. Compare identical stone/dirt areas with walking ON and OFF and the same tools/order. Watch actual player movement and damage progress simultaneously. Check ledges, a support block under the player, shallow water, blocked headroom, and approaching the reach limit. The player must stop or use the normal approach when walking is unsuitable.
4. Pause/stop during walking and during tool staging. Revoke control with normal WASD. Confirm movement and digging end and that manual control is restored. Rejoin during a layer; timings and counts must not include offline time or reset to zero.
5. Toggle F4, move the camera with WASD/jump/sneak and verify the task continues. Exit freecam and verify ordinary WASD preempts. Test Freecam's player-control mode separately, and try freecam during both Baritone travel and the new walking-digging phase.
6. Check the HUD at the usual GUI scale and `/hw task <id>` after completion. Mining a mostly-air layer must not report that air as blocks broken. Compare layer duration, last/best values and rate against a stopwatch. Test an existing checkpoint with no stats: ETA must warm up from newly measured progress.
7. Put an unbroken hatchet in main inventory and then in a supported bag, provide enough matching saplings above reserve for the planned sites, and run a tree pass with a full hotbar. Repeat with a lumber axe. Broken tools must not be selected, and displaced stacks must remain safe. Remove saplings and confirm the diagnostic names the actual shortage.

For timing comparisons, use `/hw debug off` consistently on both runs. Development tracing is enabled by default and the last inspected client log exceeded 1 GB, so logging settings are a material comparison variable.

Automated geometry, cache isolation/visit decisions, tool classification, input policy, timing persistence and existing integration tests pass in development. Physical behavior and speed comparisons remain unverified until these checks are recorded.

## Fallback editor and spiral-width regression checks

- Open Tasks, Schedules and Fallback tasks: excavation and quarry records should appear only in Fallback tasks. Higher-priority task scheduling must still preempt fallback work.
- Pause an active excavation, enable walking and save. Confirm its processed count persists. Change spiral width to 3 or 8 and save: confirm a new scan starts, air is skipped and the new width persists after reconnect.
- Compare width 1 and 8 on a broad area: wider spirals sweep multiple adjacent rings within each band. Test both circle and square order, including a thin rectangle.
- Walk while mining across a cleared layer with a solid floor one block lower. Check that drops deeper than one block, liquids, obstacles, out-of-reach blocks and manual preemption still stop movement. Instant breaks and confirmation boundaries may still produce pauses.
- Verify the statistics panel above the lower-right block tooltip at normal and small GUI scales, with chat and the top-right minimap visible.

## In-game HUD positioning

Open `/hw hud` or the dashboard's **HUD position** button. Drag the blue preview panel; arrow keys nudge one GUI pixel and Shift+arrow nudges ten. Save persists a normalized position in the instance's `config/horizonwright-hud.properties`. Cancel/Escape discard edits; Reset position previews the default until saved. The editor uses live statistics when available, otherwise sample text, with the same absolute-coordinate renderer used in play.

Verify placement against GTNH information, minimap and block-tooltip HUDs. Save, reopen the editor, reconnect, restart and change resolution/GUI scale: the panel should retain its relative placement and remain on screen. Verify Cancel does not overwrite the saved position.

Walking mode now starts an already reachable target without an extra idle tick, notices locally completed breaks immediately, and plans the next target in the same tick as a clean cleared-volume audit. Packet drains and target confirmation remain; these changes reduce avoidable pauses but do not implement continuous movement across every action boundary.

## Overall/layer HUD and full-stack transfers

The HUD now displays Overall Total and Current Layer separately, each with active elapsed time, ETA, confirmed target breaks, remaining positions, average blocks/min and a trailing 30-active-second break rate. Last layer time remains at the bottom. Remaining positions can include air; both ETAs use processed-position throughput, while break rates count actual confirmed target breaks. The layer ETA measures progress within the current layer. Rates warm up using the active time available; pauses/offline time are excluded. Rolling samples persist across reconnects. Existing pre-upgrade layers show their measured break total immediately with a + suffix because earlier breaks were not recorded. Their blocks/min uses the matching measurement-time baseline. Previously accumulated hidden counts are retained, and the baseline survives reconnects. The next layer starts a complete count and removes the + marker.

Check the full HUD in `/hw hud`, then run through a layer boundary and pause/reconnect. Overall counts should persist; current-layer counts/rates should reset at a layer change; last layer time should update. After 30 active seconds without breaks, both applicable rolling rates should reach zero.

Bag and AE2 deposits and chest unloading prioritize full source stacks. Explicit full-stack transfers prefer a destination with capacity for the whole stack over splitting into an existing partial stack. Partial matching destinations remain usable when no whole-stack destination exists. Vanilla chest shift-click still follows vanilla's compatible merging internally. Bag transfers recheck both source and destination against the observed plan before pickup and reject incompatible item/metadata/NBT identities.

Test dirt stacks of 9 and 64 against a bag with dirt 9, sand 9, and an empty slot: dirt 64 should go to the empty slot first, dirt 9 should merge with dirt, and sand should remain untouched. Also test a full bag with only partial compatible capacity, filtered bags, 16-stack items, and reserved supplies. The reported dirt-to-sand visual attempt has not been reproduced in game; planner and live preflight checks have regression coverage.

## Mining standoff and look stability

Walking while mining stops before the predicted step, including horizontal momentum, enters a one-block horizontal radius around the active block or next target. At that boundary it cancels horizontal momentum so step-up boots cannot coast onto the target. Existing reach, collision, liquid, floor and action-ownership checks remain. Exact digging aim uses the nearest equivalent yaw and preserves yaw within 0.05 horizontal blocks of a vertical aim point. Physically test with step-up boots, diagonals, and targets across the yaw wrap boundary; ensure digging continues while stationary and manual preemption still works.

The HUD and positioning preview label the estimate **Time remaining** in both sections.

## Chest extra-slot fix and revised time estimates

A physical session stopped after opening the chest with `Named loadout is incomplete: {inventory-36=1}`. Its automatic reservation referenced an Extra Utilities axe beyond the normal 0..35 player slots exposed by the chest. Automatic inventory inspection now derives reservations only from those transferable slots, while explicit repair-material requirements remain. Retest with the extra-slot axe equipped and cargo in the normal inventory. Retry the blocked unload service task; do not delete excavation progress.

Time remaining now uses remaining positions divided by confirmed block-break throughput: the overall section uses the full active-run average, and the layer section uses its trailing 30-active-second average. At startup the window uses the active duration available. Zero breaks produces `waiting for breaks`, completed work produces zero, and an exhausted scan awaiting verification shows `verifying`. This supersedes the earlier processed-position-throughput estimates in this document. Remaining positions may include air, and independent rates can still produce a layer estimate greater than the overall estimate.

## Capacity-limited chest unloading

The next physical retry reached chest planning but failed with `vanilla chest has no capacity for approved player slot 11`. The predictor previously discarded every planned transfer if any later stack did not fit. It now skips non-fitting stacks, preserves the sequential snapshots of fitting transfers, and submits a nonempty approved subset through the same server-confirmed transaction checks. The runner reobserves after the batch. Cargo left with no compatible capacity blocks with a clear free-space instruction rather than retrying a planning exception or declaring completion.

Test a nearly full chest with one empty slot and multiple cargo stacks: the first full stack must transfer. Test no empty slots but partial matching capacity: later compatible cargo must still transfer when an earlier different item cannot fit. After the capacity block, free space and Retry now; already transferred items must not be replayed or lost.
# Unload preparation and manual-close regression

## Background mouse isolation

While a Horizonwright runtime is attached to a joined world, inventory GUI mouse
release bypasses vanilla MouseHelper's desktop-cursor recentering. Grabs and mouse
look deltas are suppressed while the window is unfocused. The client also temporarily
disables pause-on-lost-focus and restores the prior setting when detached. Explicit
pause/menu actions remain available. Physical-input interruption handlers ignore
events while the Minecraft window is unfocused.

Physical check: start a bag/chest transfer, switch to another application's window
on the other monitor, and continue using the mouse there through repeated bag and
chest open/close cycles. The pointer must stay in the other app, transfers must keep
running, and no manual-input interruption should occur. Return to Minecraft and
click into the world to resume ordinary mouse capture; verify normal manual aiming,
manual task interruption, and explicit chest-close blocking still work. Unit tests
cover no-warp release, background open/close cycles, and foreground mouse-delta use;
the multi-monitor behavior requires a game test.

## Forestry bag shift-click transfers

Forestry backpack loading and unloading now prefer one verified shift-click when the
planner approves the entire source stack, the full stack fits, and native routing
does not change protected player/carrier slots. The predictor follows Forestry
4.11.31 `SlotUtil` and vanilla's repeated transfer behavior. Loading tries an occupied
eligible bag slot then an eligible slot on each iteration; unloading merges hotbar
then main inventory, then fills empty hotbar and main slots. Predictions only mutate
copied fingerprints. The existing executor still requires server confirmation and
the exact resulting inventory before any next transfer.

Exact pickup/place remains the fallback for reserved partial quantities, limited
space, non-stackable or wildcard items, unknown layouts, and Adventure/Eydamos bags
whose special-slot shift routing is not modeled. No alternate click is automatically
sent after a rejected or uncertain shift-click.

Physical checks: stow full and partial cargo stacks in Forestry backpacks, unload
them to player inventory, then offload to a chest. Verify supplies and carrier slots
remain protected, incompatible items never merge, and full bags or partially reserved
stacks use the exact-click fallback. Compare the bag-transfer pace with the prior
build; closing/interruption must still stop pending transfers.

The live unload handle now starts its CONTAINER action session before submitting to
the packet executor, waits for the preceding storage-open session to drain, and ends
its session on confirmation, rejection, failure, or cancellation. The prior adapter
held a broker lease but never registered it with the packet guard, so the executor
rejected submission with "container transaction does not own the active action epoch".
Regression coverage uses the real action guard, live executor, and packet correlation
bridge to check click dispatch, server confirmation plus synchronized inventory,
session cleanup, cancellation while waiting, and failure before the first click.

The live unload predictor now keeps click IDs stable across the checkpoint revision
increment that saves a prepared transaction. Previously this increment alone changed
the fingerprint, causing an unchanged chest to be replanned indefinitely without clicks.
Exact item, count, metadata, NBT, window, and action-epoch validation remains enabled.

Closing storage after the task has observed it open blocks the unload and releases its
actions. It must not reopen until explicit Retry. Observation errors, including a held
cursor stack, also block for user correction instead of automatically retrying.

Physical checks: retry unloading with an empty cursor and available chest space; verify
items actually transfer. Close the chest during preparation and during transfer; verify
it stays closed and the task reports blocked. Explicitly retry and verify reconciliation
before further transfers. Repeat with a stack on the cursor, return it to a slot, then
retry. Unit coverage checks stable live prediction fingerprints, closure before and
during execution, and held-cursor recovery; actual GTNH behavior still needs a game test.
