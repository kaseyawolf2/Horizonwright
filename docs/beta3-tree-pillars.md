# Beta3 tree pillars

Standard axes and hatchets can use temporary scaffolding to reach captured logs above player reach. The same material staging and cleanup apply to excavation tree recovery.

- Eligible supplies include ordinary logs (including mod blocks derived from BlockLog), planks, dirt, cobblestone, stone, netherrack, stone bricks, bricks, sandstone, and hardened clay. Falling blocks, tile entities, non-solid blocks, and NBT-tagged stacks are excluded.
- A guarded inventory swap stages a suitable main-inventory stack into the hotbar when necessary, preserving the selected slot and the tree tool's staging slots. Tree inventory preparation retains 16 of each available eligible supply.
- Placement avoids captured trunk columns. Scaffolds occupy air cells, so pre-existing blocks are not replaced.
- Every confirmed support placement is journaled with its exact position, registry identity, and block metadata, under the active profile and dimension.
- Tree completion and excavation confirmation wait for support removal. Cleanup targets the highest recorded blocks first, allowing descent by mining the pillar beneath the player. Cleanup cannot place new blocks or authorize breaking unrelated logs, terrain, or structures.
- Interrupted work retains the journal. Tree/excavation collection also checks pending supports before collecting drops. Unloaded, changed, or unreachable supports prevent a successful cleanup result; cancellation does not falsely mark them removed.
- After tree supports are gone, the bot walks back to its starting ground position before confirming the tree action. The existing drop collection and replanting phases follow.

Validation: run gradlew.bat spotlessApply build. Regression coverage includes material selection, protected hotbar slots, scope restoration, persistent exact block identity, removal-only navigation, packet-drain handoff, and refusal to finish when supports remain. In-game verification is still required for physical jump-place timing and modded collision behavior.

Physical check: give the player an ordinary hatchet/axe and a stack of logs or dirt in the main inventory. Fell a tree taller than normal reach. Verify hotbar staging, climbing to upper logs, removal of every placed pillar block, return to ground, collection, and replanting. Pause mid-climb and resume to check persistent cleanup.

## Missed tower placement fix

The physical run at 21:59 on September 11 showed the player stranded at (196, 76, 335), with drops five to six blocks below. Excavation 7001 confirmed the tree cleared without entering support cleanup; drop navigation and the next excavation approach then failed. The journal was empty. Inspection of the saved chunk confirmed sandstone at (196, 71..75, 335), above grass at Y=70, matching the five increasing feet heights in the tree approach trace.

Placement tracking now reads the actual C08 placement packet's coordinates, face, and stack instead of Minecraft.objectMouseOver. Multiple outstanding placements are retained until observed in the world or expired as unsuccessful. Pending confirmation blocks the cleanup handoff, so delayed placement observations cannot allow pickup or completion to start early. Tree and excavation actions also remove existing journaled supports before resuming work.

The regression suite covers packet faces without a screen crosshair, overlapping placements, delayed observations, rejected attempts, and world changes. A one-time cleanup journal repair for the five verified sandstone supports allows the stranded task to recover on resume; no world blocks or player position are edited.

## Direct support digging

The later physical failure at (182, 69, 362) was correctly journaled sandstone under the player's feet at Y=70. Saved terrain has air at Y=68 and a leaf block at Y=67. The previous cleanup required a path ending inside the removed sandstone, but that cell cannot be a standing position over the air gap.

Cleanup now digs an exactly matched, visible, reachable support directly under its own guarded action session. It selects a hotbar tool, avoids known Tinkers area tools, checks block identity each tick, and retains the journal until the block is observed gone. Support removal below the player requires a collision surface within a three-block drop; collision checks allow full leaf-block surfaces. Cleanup waits for landing and packet drain before processing the next support. Distant supports use an adjacent approach followed by exact digging instead of an occupied-cell navigation goal. Collection leases include held-tool authority so selection and restoration packets are authorized.

The current sandstone entry remains pending for recovery on resume. No world or journal repair is required for this case. Physical confirmation of the new direct digging path remains pending.

## Held-slot synchronization fix

The September 12 trace showed each completed direct pillar dig followed by a blocked held-slot-change packet in QUARANTINED mode. Cleanup restored the client slot, queued C09, and ended authority before Netty evaluated that write. Vanilla had already updated its slot cache, so the next dirt placement could omit a slot update while the server still held the shovel.

Direct support cleanup now keeps authority until the queued restoration writes pass the outbound boundary, then quarantines and drains. Direct digging explicitly synchronizes its selected and restored slots. Scaffold placement sends a fresh C09 from the C08 PRE-dispatch hook, before the placement is enqueued, independently of vanilla's optimistic slot cache. This preserves TCP packet order without claiming a server acknowledgment. Revoked leases still block action packets.

Embedded-channel regression tests reproduce the dropped restoration, verify restoration-before-quarantine and slot-before-placement ordering, and cover revoked epochs and dispatcher failure. Physical retesting is required to verify the observed shovel-path conversion no longer occurs.

## Stable vertical footing before mining

The subsequent trace repeatedly alternated approach-reach cancellation and dig-reapproach on the same leaf. Mining stole navigation as the jumping player briefly entered reach, before the pillar block could be placed.

Excavation and tree harvesting now require grounded, unchanged vertical position before cancelling an approach to take over mining, before starting or resuming a dig, and while damaging an existing block. A jump apex is not accepted even if instantaneous vertical velocity is zero. Landing must settle for a tick; normal grounded gravity bookkeeping (approximately -0.0784 motionY) does not prevent digging when actual Y is unchanged. Horizontal movement remains permitted. Direct support removal uses the same gate and waits for each landing. Excavation waits preserve the queued held-slot restoration boundary from the desync fix.

Regression tests replay jump, apex, fall, first landing, and settled support states, and cover horizontal walking, vertical steps, and invalid motion data. Physical jump-place handoff confirmation remains pending.
