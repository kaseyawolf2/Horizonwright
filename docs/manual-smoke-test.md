# Manual Prism smoke test

Human-driven testing is the default because GTNH startup and GUI control are
slow. Automated GUI control is reserved for infrequent release checkpoints.

Development snapshot tracing is enabled by default. Before reproducing a problem, run
`/hw debug status` and leave it on. After the test, close the Horizonwright instance normally so
the complete `[HWTRACE]` sequence is flushed to `.minecraft/logs/fml-client-latest.log`. Baritone's
`chatDebug` setting is separate and may be enabled at the same time.

## Milestone 0 navigation checkpoint

1. Build and install the production Horizonwright JAR plus the exact separate
   `vendor/baritone/baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c.jar`
   into the isolated instance.
   Confirm there is exactly one Horizonwright JAR and one Baritone JAR in
   `.minecraft/mods`.
2. Launch through `GTNH-2.9.0-Beta2-Horizonwright.lnk` and maximize Minecraft.
3. At the main menu, open **Mods** and confirm both `Horizonwright` and Baritone
   are present with the expected versions.
4. Back up and then open the disposable `Horizonwright Smoke Tes` world, or
   create a new disposable world. Never use a valued world or multiplayer
   server.
5. Run `/hw profile status`. On the first launch for this disposable world it
   must report explicit enrollment is needed; run `/hw profile enroll`, then
   confirm `/hw profile status` reports `READY`. Rejoining the same world must
   select the same profile without asking for enrollment again.
6. Press `H` and confirm the dashboard reports:
   - action epoch `1` and zero leases while idle;
   - Baritone enhanced build `fcbbd4882c` ready;
   - safety ready; and
   - unattended operation disabled.
   Open the **Baritone** tab, search for `allowSprint`, select it, and confirm
   its type, current value, default, Apply, Reset, and Toggle controls are
   visible. Toggle it and reset it, then reopen the tab and confirm the reset
   value persisted.
7. Stand on clear, level ground with at least five unobstructed blocks ahead.
   Note the selected hotbar slot, close the dashboard, and run `/hw status`.
8. Run `/hw goto ~4 ~ ~ 1`. Confirm the player walks to the clear target and
   status reaches `COMPLETED`. Confirm no block was dug or placed, no entity was
   attacked or used, and the selected hotbar slot did not change. Keep Waila's
   overlay enabled and confirm it continues updating during the route.
9. In a disposable open area, submit a target more than 128 blocks away (for
   example `/hw goto ~160 ~ ~ 2`) and confirm it is accepted and begins moving.
   While it is moving, press `E`; confirm the inventory opens normally. Close
   it, cancel the long route if necessary, and confirm direct control remains
   available.
10. Face another clear stretch and run `/hw goto ~12 ~ ~ 1`, then promptly run
   `/hw navcancel`. Confirm status reaches `CANCELLED`, motion stops, and no key
   remains stuck.
11. Start a longer clear route, save and leave the world while it is active,
    then rejoin the same enrolled world. Confirm the same goal is restored and
    resumes from the player's current position rather than disappearing or
    creating a duplicate.
12. Start another clear route and run `/hw stop`. Confirm automation stops, the
    dashboard reports `AUTOMATION STOPPED`, and a new `/hw goto` is refused.
    Then verify ordinary player movement, manual block breaking, the `E`
    inventory, and unrelated mod UI/network behavior still work. Run `/hw reset`
    after cleanup drains, run `/hw resume` without copying a task ID, and confirm
    the suspended route resumes. The dashboard's task-control button must also
    read `Resume task` when that route is the sole resumable task. Do not use an
    actual death to test this manual-stop checkpoint.
13. Create two resumable routes. Run `/hw resume` without an ID and confirm both
    chat choices are clickable and show hover details; click one and confirm
    only that exact task resumes. Repeat with two resumable routes from the
    dashboard and confirm **Choose task** opens a selection popup instead of
    guessing.
14. Save and quit to title, then exit Minecraft cleanly.
15. Report completion, cancellation, automation-stop/reset, hotbar, long-route,
    inventory-menu, and world-mutation
    results. Check the log for Horizonwright packet denials: no Forge, custom,
    unclassified, or non-packet traffic may be denied merely because it is
    unknown. If anything fails, also provide
    `.minecraft/logs/fml-client-latest.log` from the instance.

The Milestone 0 lease intentionally permits only movement and look. Use a clear
route: if Baritone attempts to dig, place, interact, attack, change slots, or
mutate a container, Horizonwright blocks the packet and fails the navigation
request.

Unknown or unintegrated network traffic is outside Horizonwright's ownership
and must pass through unchanged in every state, including death safety. Only
packet semantics with an explicit tested integration may be gated.

## Pending live vanilla-chest unload checkpoint

Use the dashboard's **Profile assets** page to create the named loadout and
targeted storage endpoint without hand-editing JSON. The first guided editor
creates an accept-all endpoint, so use only a disposable vanilla chest and test
selective destination rules later when the filter editor is present. Verify
reserved stacks remain in the player inventory and unreserved items follow
vanilla shift-click merge order,
and every click receives an accepted server confirmation plus the exact
synchronized after-state. Also verify a full chest, held cursor stack, wrong
chest coordinate, modded/subclassed container, disconnect, rejection, and
timeout all stop without replaying a click. This checkpoint is not yet a
recorded physical result.

## Pending live Tinkers repair checkpoint

Use the dashboard's **Profile assets** page to capture the tool and repair
material slots, then target and save the named repair station without
hand-editing JSON. Use the pinned Tool Station or Tool Forge, a disposable
damaged tool, and cheap approved repair material. For the current
prepared-station slice, place
the damaged tool in semantic input slot `1` and material in slots `2...`, leave
the configured reserved player slot and cursor empty, and confirm slot `0`
shows a lower-damage output preview. Verify Horizonwright takes that exact
output, consumes only the preview-declared approved material amounts, and
returns the same stable tool identity to the reserved slot. Wrong coordinates,
unapproved tool/material identities, altered preview NBT, occupied reserved
slot, rejected transaction, timeout, disconnect, and restart must all stop
without replay. This checkpoint is not yet a recorded physical result, and
automatic station population is still outstanding.

## Pending excavation service-composition checkpoint

After installing a build containing the guided editor, configure one disposable
excavation with both a named unload endpoint and a named repair station. Cause an unload
threshold at a recognizable frontier and confirm exactly one unload child is
shown, the parent remains blocked until the child is verified complete, and the
parent then resumes the identical frontier. Repeat for repair. Disconnect once
while a child is queued and once after its final transaction but before the
parent resumes; each reconnect must show the same child rather than create a
duplicate. A failed or cancelled child must leave the parent blocked for
operator inspection. This checkpoint is automated-test covered but has not yet
been physically recorded.

For the trigger portion, leave two empty main-inventory slots and a healthy tool
in the configured reserved slot; excavation should continue. Fill one more slot
before an ordinary target and confirm an unload child appears before any dig.
After unloading, damage the same Tinkers tool below fifteen percent remaining
durability (or below the configured predicted-work requirement) and confirm a
repair child appears at the unchanged frontier. Air, protected blocks, fluids,
and unreachable targets must not repeatedly create service children.

## Pending live clean-volume excavation checkpoint

Install only after the automated build remains green. In a disposable area,
open **Dashboard > Profile assets > New excavation**. Confirm the saved loadout,
storage, and repair station names are prefilled, the displayed center follows
the player's current dimension and X/Z, and invalid radius/Y/service fields
leave the page open with a clear `Nothing queued` message. Queue a uniquely
named radius-1, one-layer cylinder and confirm it appears on the dashboard.
Repeat once with services off. As a typed-path cross-check, use `/hw excavate
cylinder smoke-command 1 ~-1 ~-1`; relative Y coordinates must resolve from the
player and a duplicate name or invalid profile reference must be refused before
any action begins. Then start with a one-block-radius, one-layer clean-volume cylinder containing only
ordinary cheap blocks. Confirm Horizonwright approaches without breaking route
blocks, digs exactly the current frontier target, and advances only after that
block is visibly air. Pause, direct player input, automation stop, disconnect,
and a block replacement during approach must stop without advancing the target.
Then place a vanilla chest, another tile entity, an OpenBlocks grave, bedrock,
flowing fluid, and source fluid at separate test targets: the grave and tile
entities must be protected, bedrock and unloaded positions must be unreachable,
and fluid must never receive a blind dig action. This checkpoint has not yet
been physically recorded.

## Pending live managed-quarry checkpoint

Use a disposable radius-2-or-larger site and open **Dashboard > Profile assets
> Managed quarry**. Confirm the dedicated form has no overlapping controls and
that invalid material names, radius 0–1, invalid Y bounds, or an invalid light
interval leave the page open with a clear diagnostic. Queue the task with
`minecraft:cobblestone` for ramp/filler and `minecraft:torch` for lighting.
Cross-check the typed path with `/hw excavate managed managed-smoke 2 ~-3 ~
minecraft:cobblestone minecraft:torch minecraft:cobblestone 2`.

Place the approved stacks in main inventory rather than the hotbar and fill the
hotbar. Verify the selected hotbar item returns to the same slot after every
placement and unused infrastructure material returns to its original inventory
slot. The quarry must retain one adjacent descending stair step per layer,
clear headroom around that staircase, place scheduled lights only after their
future position is clear, and never excavate a confirmed ramp/light block.
Remove one retained step and confirm it is rebuilt without advancing the volume
frontier first. Introduce a shallow water source and flowing water inside the
next layer; each exact fluid target must be replaced only with the approved
filler and increment `fluidContained` only after the server-visible replacement.
Pause or disconnect during approach, inventory staging, and placement; no
unconfirmed infrastructure or volume target may advance. This checkpoint is
automated-test covered but has not yet been physically recorded.

## Pending named work-area checkpoint

Open **Dashboard > Profile assets > Work areas** in the bound disposable world.
Stand at one corner of a small plot and capture corner 1, then stand at the
opposite corner and capture corner 2. Save it as `smoke-plot`, close the world,
rejoin, and confirm the page reports the saved-area count. Saving `smoke-plot`
again with wider corners must replace the old bounds rather than create a
duplicate. A missing corner or corners captured in different dimensions must
leave the profile unchanged with a clear diagnostic. This checkpoint is
automated-test covered but has not yet been physically recorded.

## Pending finite farm-pass checkpoint

After installing the automated-test-green build, create a small named plot
containing one immature crop and one mature vanilla crop. Put the exact replant
seed in the hotbar with total verified seed inventory above the configured
reserve, then use **Work areas > Queue one farm pass**. The finite pass must skip the immature crop
without acquiring an action lease, harvest and replant the mature crop, and
advance it only after the replacement is visibly immature. Pause during the
mutation and reconnect after one confirmed target; Horizonwright must reobserve
the same unadvanced target after pause and retain the exact next index after
reconnect. Manually alter a frozen target before its turn and confirm the pass
stops without touching it. These runner behaviors are automated-test covered;
the live adapter is present but not yet physically verified. Repeat the basic
pass with `/hw farm smoke-farm smoke-plot 2`; missing plots, seeds outside the
hotbar, insufficient reserve, oversized plots, unloaded plot chunks, and
unknown mod crops must stop without a harvest.

Exercise ordinary crops in two groups. With the pinned Hunger Overhaul artifact
and `enableRightClickHarvesting=true`, mature wheat, carrots, and potatoes must
receive one normal right click, reset to metadata 0, and leave the planted block
in place. Mature nether wart and cocoa must instead require the exact wart or
cocoa-bean material above reserve, break normally, replant once, and confirm the
new immature block. With Hunger Overhaul missing, byte-changed, version-changed,
or its setting disabled, the three `BlockCrops` must also use the explicit
break-and-replant path rather than issuing an ineffective right click.

For one disposable third-party crop that implements `IGrowable`, confirm its
seed item implements `IPlantable`, resolves back to the same planted block, and
is accepted by the current soil. A young plant must be observed but skipped; a
mature plant whose API reports it can no longer grow must break, replant with
the exact seed metadata, and confirm a newly growing replacement. Repeat with a
sapling, melon or pumpkin stem, grass, mushroom, tall grass, and double plant in
the same area: every one must remain untouched. A Pam, CropsNH, or Natura block
whose dedicated adapter is unavailable must also remain untouched instead of
falling through to generic destructive harvesting.

Then schedule the same plot every two connected minutes from the guided page or
with `/hw farmschedule smoke-farm-recurring smoke-plot 2 2`. Keep a long
excavation active and confirm the farm CHORE suspends it at a safe frontier,
runs once, and lets the exact excavation frontier resume. Disconnect for longer
than two minutes: offline time must not create a burst of runs after rejoin, and
at most one occurrence for this schedule may be queued or active at once.

For CropsNH, include one mature crop-sticks target and begin once from beside
the plot and once from more than 30 seconds of travel away. Ordinary travel may
sprint, but the player must stop sprinting before entering the protected crop
radius, walk throughout harvesting, and avoid jumping down onto crop sticks.
The long approach must not retry at 30 seconds. If a CropsNH spade is selected,
the server must receive the held-slot change without an action-guard block; the
crop must reset to immature and the pass must continue normally.

For Pam HarvestCraft 1.3.11-GTNH, separately test one mature ground crop, one
mature hanging fruit, and one mature fruiting log while the pack's right-click
harvest settings are enabled. Each must receive one normal right click and
return to its exact immature metadata. The fruiting log must remain in place and
must never receive a dig action. Disable Pam's right-click crop or fruit setting
in a disposable instance and confirm the corresponding class is ignored rather
than guessed or broken. An unknown subclass or similarly named addon block must
also remain untouched.

## Pending ordinary-tree farm checkpoint

Create a small saved 3D work area containing the entire trunk and branches of
one ordinary vanilla oak, spruce, birch, jungle, or acacia tree. Leave at least
three matching saplings anywhere in the player inventory and select **Tree
farm > Queue one tree pass**, or run `/hw trees smoke-trees smoke-woodlot 2`.
Confirm Baritone owns the approach, may break vanilla leaves or pillar when
needed, and that Horizonwright fells the frozen logs from bottom to top. With a
lumber axe, logs removed by the multi-block effect must be skipped safely. The
task must prove every captured log is air before planting exactly one matching
sapling at the original root, return a staged inventory sapling/displaced item,
and complete only after the replacement is visible.

Pause once while approaching or digging, and once after the clear-root frontier
but before replant confirmation. Resume and rejoin must not repeat a confirmed
fell or skip the pending replant. Remove the matching saplings before a fresh
pass and confirm it blocks before digging. Draw a boundary through a trunk or
try a 2x2 dark-oak tree; both must be skipped without mutation. Finally schedule
`/hw treeschedule smoke-trees-recurring smoke-woodlot 5 2`, edit it in the
scheduled-jobs page, and verify deleting the saved area cancels the unfinished
tree occurrence and schedule. This path is automated-build green but has not
yet been physically recorded.

## Pending non-destructive husbandry checkpoint

Create a small loaded named pen containing exactly two unnamed adult vanilla
animals of one supported species. Put their exact vanilla breeding item anywhere
in the player inventory. Configure the selected species with a minimum above the
current adult count, then queue one husbandry pass. Confirm Baritone approaches
the exact observed adult, the correct feed is staged and returned safely, one
normal entity interaction occurs, and the action advances only after a fresh
complete scan shows that exact adult in breeding state. The next plan may feed
the second adult, but must never target a baby, named animal, player, unsupported
mod animal, or already-engaged animal.

Drop an ordinary item inside the stable-population pen and queue another pass.
Confirm Horizonwright walks through that exact item position and advances only
after the same item-entity identity disappears from a fresh complete scan. Test
with a full inventory: an uncollected drop must remain unconfirmed rather than
being reported as collected. Pause during each approach and confirm no action is
recorded before its postcondition. Finally configure a maximum below the current
eligible adult count. The pass must block with the explicit culling-authorization
diagnostic and must not acquire attack authority or damage any animal.

## Pending registered-bed sleep checkpoint

After installing the automated-test-green build, look directly at a vanilla bed
and use **Profile assets > Save targeted vanilla bed** with the name
`smoke-bed`. Move outside click reach, keep a FALLBACK excavation active, and
choose **Every night** (or run `/hw sleepschedule smoke-sleep smoke-bed`). At the
vanilla night window, the sleep CHORE must suspend excavation at its safe
frontier, navigate to the registered location, interact only with that exact
bed, confirm sleeping or daytime, and let the exact excavation frontier resume.

Repeat with a hostile mob inside the vanilla bed danger radius, with the bed
removed, and from a dimension that does not permit normal sleep. Each case must
stop before bed interaction with a clear blocked reason. Pause during the
approach and reconnect before interaction; resume must perform a fresh bed,
dimension, danger, and reach observation. Unknown mod sleep providers remain
unsupported and must not be invoked. This path is automated-test covered but
has not yet been physically recorded.

## Recorded physical result

On 2026-08-30, the operator completed the long-route, inventory-during-walk,
automation stop/reset, direct movement/mining/inventory, unrelated-traffic, and
ID-free resume checks above in the isolated GTNH 2.9.0-beta-2 instance. The
operator also created two resumable routes and confirmed that ambiguous
`/hw resume` listed both choices and that an exact task ID resumed only the
selected route. All checks passed with production JAR SHA-256
`CF9C16E234048D818C0957BA3284394FA6C62C81CA6D24ED6AC8DDB5415B41D7`.
