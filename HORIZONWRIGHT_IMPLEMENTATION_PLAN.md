# Horizonwright Greenfield Implementation Plan

## Product Vision

Build Horizonwright as a new, independent Forge 1.7.10 client mod for GT New Horizons. Horizonwright is a persistent autonomous-operations platform that owns scheduling, suspension, safety, logistics, recovery, its GUI, persistence, and low-level action authorization. Baritone supplies navigation through a narrow, replaceable backend; it is not Horizonwright's application framework, public API, or product identity.

The existing Baritone Backport repository remains unchanged as a behavioral and licensing reference. Horizonwright starts with a clean source tree and its own mod lifecycle. Useful algorithms may be reimplemented or selectively ported only after their behavior, provenance, and license obligations are understood. The old process architecture, command-centric ownership model, runtime state, and package layout are not migration requirements.

Primary delivery order:

1. Bootstrap the independent Horizonwright project, identity, pinned toolchain, test harness, and licensing notices.
2. Prove the Baritone navigation backend plus the required input and packet safety hooks in a minimal vertical slice.
3. Build the Horizonwright task kernel, scheduler, action broker, persistence, and minimal dashboard.
4. Complete the death/grave item-preservation system before allowing unattended operation.
5. Deliver the base-work vertical slice and then the operational-base MVP: excavation, logistics, farming, husbandry, sleep, and Tinkers repair.
6. Deliver the Thaumcraft module, with Auto Research as its first and highest-priority slice.
7. Add Piston Boots, elevators, Hang Glider travel, and CropsNH breeding/stat improvement.
8. Add exploration, prospecting, dungeon work, hunting, ranged combat, and recursive crafting-grid automation.

Unattended operation is not considered releasable until the gravestone-loss regression suite passes.

## Product Decisions

- Product identity is `Horizonwright`; Forge mod ID is `horizonwright`, the proposed base package is `io.github.kaseyawolf2.horizonwright`, the configuration/state root is `horizonwright`, and shipping artifacts are named `horizonwright-<version>.jar`.
- Horizonwright owns its lifecycle, controller API, tasks, commands, dashboard, persistence, safety policy, and optional-mod integrations. No source, binary, configuration, or command compatibility with Baritone Backport is promised.
- Baritone is accessed only through Horizonwright's private navigation capability. Milestone 0 decides whether the pinned backend is bundled, relocated, or supplied as an exact-version runtime dependency.
- The dashboard runs locally on the bot client. Horizonwright's typed client commands remain a fallback; a main-account-to-bot network relay is outside the initial release.
- The server remains unmodified. The bot may use any information sent to its client, but all gameplay changes must use normal server-authoritative movement, clicks, containers, and packets.
- Safety precedence is `Safety > manual orders > recurring chores > fallback`.
- Item preservation outranks survival and task progress. Suspected death fails closed even when this causes a false positive or avoidable death.
- Excavation supports both clean-volume and managed-quarry modes. Managed quarry is the default, with a perimeter ramp, lighting, fluid containment, logistics, and repair.
- Recurring intervals use connected time. Reconnect performs at most one catch-up occurrence per rule rather than replaying every missed interval.
- Dungeon interaction is limited to recognized generated structures. Player storage and unknown containers are never eligible.
- GTNH support is version-pinned but modular: an incompatible optional mod disables its capability rather than crashing the automation core.
- Autonomous play may violate a multiplayer server's rules even when it is technically client-only; documentation must require the operator to verify server policy.

## Core Architecture and Public Interfaces

### Project bootstrap and source-reuse policy

- Keep the existing Baritone Backport checkout unchanged as a read-only behavioral reference; record its exact reference commit SHA, but never use that local checkout as a Git parent, submodule, live build input, or artifact destination.
- Initialize a clean Forge 1.7.10 project with independent mod metadata, source layout, assets, configuration, persistence, tests, CI, license, and third-party notices.
- Maintain a reuse register with `REIMPLEMENT`, `PORT_WITH_ATTRIBUTION`, and `LEAVE_BEHIND` decisions for Circle geometry, farming, prospecting, storage, Tinkers support, Piston Boots, and other candidate behavior.
- Port only isolated, licensable behavior protected by characterization fixtures. Do not import old processes, controllers, commands, runtime state, or configuration structures merely for parity.
- Treat old HOME, BED, DROP_OFF, Circle, farm, and prospect behavior as optional migration inputs or golden fixtures rather than Horizonwright APIs.
- Pin the target GTNH build, Forge/mappings, Java runtime, optional-mod versions, dependency repositories, and exact Baritone revision. Any Baritone source used by the build must be vendored or dependency-resolved inside Horizonwright with its commit, provenance, license, and notices recorded.
- Choose and document Horizonwright's own license before importing, porting, or bundling third-party source, and verify compatibility for each reuse decision.

### Component boundaries

```text
Dashboard / #hw commands
          |
IHorizonwrightController
          |
TaskOrchestrator + Scheduler + Policies
          |
Horizonwright capability interfaces
   |-- ActionBroker and death-safety kernel
   |-- Inventory, containers, crafting, combat, and held use
   |-- NavigationBackend -> private Baritone adapter
   `-- Exact-version GTNH mod adapters
```

Use the following logical modules. They may begin as strict packages/source sets and become separate Gradle modules only if the legacy ForgeGradle toolchain supports that cleanly:

- `horizonwright-core`: pure Java task specifications, runners, scheduler, policies, planners, schemas, and immutable snapshots; no Minecraft, Forge, Baritone, or optional-mod imports.
- `horizonwright-forge1710`: Forge entry point, events, client-thread dispatcher, GUI, keybindings, world snapshots, input arbitration, and packet-boundary hooks.
- `horizonwright-navigation-baritone`: the only code allowed to import Baritone types; translates navigation requests, cancellation, progress, and movement capabilities.
- `horizonwright-integrations`: exact-version adapters for GTNH mods, with strict optional-classloading boundaries.
- `test-fixtures`: captured packets, containers, research notes, crop states, fake clocks/worlds, and regression scenarios.

Assemble one user-facing Horizonwright JAR unless the Milestone 0 packaging spike proves that infeasible.

### Baritone navigation backend

Milestone 0 evaluates three packaging models:

1. Vendor or dependency-resolve the exact known-working backport snapshot as an internal Horizonwright module. This gives the best standalone experience but requires notices, provenance, and an explicit collision policy.
2. Require a separate exact-version Baritone JAR. This is technically simpler but makes installation and support less self-contained.
3. Bundle and relocate Baritone classes. This gives namespace isolation but may break legacy launch hooks, reflection, mixins, or access transformers.

The starting hypothesis is option 1: build a recorded source snapshot into the Horizonwright artifact, expose none of it as Horizonwright API, and reject a second incompatible Baritone installation with a clear diagnostic. Relocation is attempted only after the navigation and launch proof succeeds. The old Baritone Backport checkout is never consulted during builds or modified by this work.

### Horizonwright controller and task kernel

- One Horizonwright `TaskOrchestrator` owns task selection, scheduling, suspension, and action authorization.
- `IHorizonwrightController` is Horizonwright's typed API for submitting, editing, pausing, resuming, cancelling, reordering, and inspecting tasks and schedules.
- Expose immutable controller, safety, queue, and capability snapshots for the GUI and external integrations.
- A narrow `NavigationBackend` accepts typed path requests and reports immutable progress/results. Any `IBaritoneProcess` or `PathingControlManager` bridge remains a private backend detail.
- The GUI and Horizonwright commands call `IHorizonwrightController` directly; they never construct Baritone commands, cast to Baritone implementations, or control Minecraft input.
- Feature modules depend on Horizonwright capability interfaces and must not import Baritone types.

Core types:

- Immutable `TaskSpec`, `ScheduleRule`, `LoadoutSpec`, `NamedArea`, `NamedLocation`, and `NamedRoute`.
- Persisted `TaskCheckpoint` for mutable progress.
- A resumable `TaskRunner` state machine that requests capabilities rather than controlling Minecraft or Baritone directly.
- `StepResult` variants for progress, wait, safe suspension, completion, failure, and typed blocking.
- Task states `QUEUED`, `RUNNING`, `SUSPENDING`, `SUSPENDED`, `BLOCKED`, `COMPLETED`, `FAILED`, and `CANCELLED`.
- Typed `BlockedReason` containing cause, location, retry count, missing requirement, and required user action.
- Monotonic task/action epoch attached to every path result, click, packet, async calculation, and container transaction.

### Scheduler

- Lanes are fixed as `SAFETY`, `MANUAL`, `CHORE`, and `FALLBACK`.
- Manual work and chores suspend lower lanes at task-declared safe checkpoints.
- Safety may interrupt immediately and ignores normal safe-cancel rules.
- The excavation job is the normal idle fallback.
- Support connected-time intervals, world-time windows, conditions, relative chore ordering, and idle triggers.
- After reconnect, restore checkpoints and enqueue no more than one catch-up run per missed rule.
- Default retry policy is three attempts with 1-, 5-, and 30-second backoff, followed by `BLOCKED`. Safety failures never retry into unsafe actions.

### Shared action services

An `ActionBroker` grants revocable, epoch-bound leases for movement, look, attack, use, digging, placement, held use, and container interaction. Safety can synchronously revoke every lease. No task, backend, GUI, or integration adapter may write inputs or gameplay packets without a valid lease.

All modules use shared Horizonwright services for:

- Movement and transport.
- Block interaction and protected-block policy.
- Inventory and loadout reservations.
- Transactional containers.
- Crafting and repair.
- Targeting and combat.
- Held-use actions such as eating, scanning, bow charging, node draining, and glider activation.

Container operations must verify the window/container type and slot layout, take before/after snapshots, execute one non-idempotent click at a time, wait for server confirmation, and abort on unexpected state or action-epoch changes.

Minecraft worlds, entities, containers, registries, and optional-mod objects are read only on the client thread. Worker threads receive bounded immutable snapshots and return epoch-tagged plans with cancellation and backpressure.

### Optional GTNH mod integrations

Add optional, versioned adapters for CropsNH, HarvestCraft, GregTech, VisualProspecting, TConstruct/TGregworks, OpenBlocks, Adventure Backpack, Thaumcraft, Thaumcraft Research Tweaks, Salis Arcana, and relevant GTNH Thaumcraft addons.

- Prefer public APIs through compile-only dependencies.
- Keep references to `thaumcraft.common.*` and equivalent implementation classes inside exact-version adapters.
- Guard optional classloading with mod/version/signature checks.
- Build runtime research, aspect, weapon, movement, recipe, and scannable catalogs after all mods have registered content.
- Show unsupported/mismatched capabilities in the dashboard and disable only the affected feature.

### Horizonwright command interface

Provide the namespaced `#hw` command surface, including `#hw panel`, `#hw task`, `#hw schedule`, and task-specific forms such as `#hw excavate cylinder`. Commands submit the same typed controller operations as the dashboard.

Legacy aliases such as `#circle`, `#farm`, and `#prospect` may be added later behind an opt-in migration setting. They are not a parity requirement, do not preserve old process behavior, and never bypass Horizonwright scheduling or safety.

## Persistence

Store Horizonwright schema version 1 JSON under its own configuration/state root, partitioned by server/world profile:

- `profile.json`: named assets, routes, loadouts, task templates, schedules, filters, policies, and GUI preferences.
- `runtime.json`: task queue, active checkpoints, excavation frontier, surveyed ore cells, Thaumcraft scan/node knowledge, last schedule occurrences, and unresolved death/recovery state.

Requirements:

- Include dimension IDs in every location and route node.
- Use a stable world/profile identity beyond server address alone and require user-confirmed reassociation after a server world reset.
- Write through a temporary file, atomically replace, and retain a rolling backup.
- Never silently discard a corrupt or newer schema.
- Persist unresolved death state across disconnects and restarts.
- Offer an explicit, one-time migration wizard/command for legacy HOME, BED, and DROP_OFF waypoints without modifying the originals.
- Do not import legacy in-flight processes or task state into Horizonwright checkpoints.
- Never write to Baritone's settings, waypoints, caches, configuration, or runtime files.
- Log only hashes/fingerprints where full inventory NBT is unnecessary.

## Dashboard

Build a local GTNH-styled `GuiScreen` dashboard with:

- Active-task status, progress, current action, safety state, and blocked reason.
- Queue controls and task-specific editors.
- Schedule editor.
- Named areas, locations, transport routes, loadouts, and item filters.
- Compatibility/version diagnostics.
- Bounded persistent event log.
- Pause, dry-run, and emergency-stop controls.
- Scaled layouts, scrolling, tooltips, keyboard navigation, validation, and destructive-change confirmations.

The Thaumcraft page additionally shows:

- Research queue and dependency closure.
- Current note/solver progress and resource estimate.
- Aspect points, discovered aspects, and configured reserves.
- Warp amount and approval state.
- Scan catalog and aspect-discovery progress.
- Node Atlas with safety/recharge filters.
- Registered Research Tables, scan stations, charging stations, and wand charge profiles.

## Item-Preservation and Death Safety

### Confirmed gravestone race

OpenBlocks can place and populate a grave while the old dead server-player object still exists. An owner interaction can then call `autoEquipAll` on that old object; respawn replaces it and discards the newly restored inventory. A stale mining-start packet is sufficient to trigger the loss.

### State machine

Add a per-connection `DeathSafetyController` with monotonic states:

`ACTIVE -> CRITICAL -> DEATH_LATCHED -> RESPAWN_REQUESTED -> POST_RESPAWN_QUARANTINE -> RECOVERY_READY -> ACTIVE/MANUAL_HOLD`

- At or below 40% health, enter `CRITICAL`: stop every attack, use, dig, placement, and container action. Permit only movement-based retreat.
- Exit `CRITICAL` only after health is above 60% for 20 consecutive ticks.
- Latch actual death at the earliest inbound `S06PacketUpdateHealth <= 0` before packet queueing, with `isDead`, `deathTime`, local death callbacks, and `GuiGameOver` as redundant signals.
- Actual `DEATH_LATCHED` never clears from positive health packets, closing a screen, timeout, disconnect, or reconnect.

### Emergency stop

On the first latch:

- Force-checkpoint the active task without waiting for a normal safe point.
- Cancel the active navigation backend and all pending navigation/action work.
- Revoke every `ActionBroker` lease, clear Horizonwright's global input arbiter and real Minecraft keybindings, and require the navigation backend to clear its private input state.
- Release the eating action and every other held-use owner.
- Invalidate all queued action and container epochs.
- Prevent every task, backend, and input owner from reacquiring input while latched.
- Enforce the greenfield invariant that no navigation backend or feature may enable gameplay input while death is latched or `GuiGameOver` is active; only the Respawn UI remains usable.

### Controller and packet firewall

- Guard all relevant `PlayerControllerMP` entry points for block damage/destruction, right-use, item use, entity attack/interaction, window clicks, enchanting, slot changes, and item dropping.
- Add an actual outbound network-write firewall so a packet queued before the latch is still rejected when written afterward.
- Apply the firewall only to packet semantics Horizonwright has explicitly integrated and regression-tested. Unknown or unintegrated network traffic passes through unchanged and cannot poison an action session.
- While dead, gate understood action traffic so only audited connection maintenance, transaction cleanup, block-break abort/release-use cleanup, and exactly one `C16 PERFORM_RESPAWN` remain available.
- Block understood entity interaction, digging start/finish, block/item use, held-item changes, animation, inventory clicks, creative/enchant/sign traffic, and explicitly integrated custom actions when their required authority is absent.
- Treat Forge/custom/mod payloads as observe-only until an exact-version integration defines their semantics and tests both allowed and denied behavior. Horizonwright must never become a compatibility allowlist for unrelated mods.
- Apply the same gate to Thaumcraft scans, aspect packets, note use, node draining, pedestal operations, and research containers.

### Respawn and grave recovery

- Persist the death epoch, server, dimension, location, old player identity, active task, and pre-death inventory fingerprint.
- After respawn, require a different player object, positive health, no death state, a loaded world, the normal inventory container, and 20 stable ticks.
- Permanently blacklist `OpenBlocks:grave` from generic mining, use, combat, and scavenging.
- Route toward the death location with generic interactions disabled. Registered route actions receive narrowly scoped tokens and may never target the grave.
- Find an owned, nonempty grave within the configured OpenBlocks placement radius.
- Require stable owner, position, and observed contents for 40 consecutive ticks.
- Missing, unloaded, ambiguous, or changing graves enter `MANUAL_HOLD` indefinitely.
- Verified automatic recovery selects an empty hand, sneaks, and performs one scoped activation against the exact grave. Consume the authorization on the first matching packet.
- Resume only after the grave is empty/removed and recovered inventory matches observable pre-death/grave contents.
- Partial recovery, insufficient capacity, or mismatch enters `MANUAL_HOLD`.

This remains client-only. A packet already received by the server before the client learns about death cannot be retracted; the pre-death critical-health lock minimizes but cannot mathematically eliminate that residual race.

## Feature Modules

### Excavation

Implement a new `ExcavationTask`; use legacy Circle geometry only as a golden behavioral fixture:

- Shapes: cylinder, cuboid, and selected prism.
- Configurable top and bottom Y.
- Clean-volume or managed-quarry policy per job.
- Managed defaults: perimeter ramp, approved lighting, fluid containment with approved filler, reachable source clearing, and retained-containment reporting.
- Persist work by chunk/layer/band frontier.
- Snapshot world data on the client thread; calculate immutable target sets off-thread.
- Reject stale results whose task/action revision no longer matches.
- Report completed, remaining, protected, unreachable, fluid-contained, and failed blocks.
- Graves and registered infrastructure are always protected.
- Suspend for unloading or repair and resume the exact frontier.
- Make `#hw excavate cylinder` the primary command; an optional `#circle` alias may submit the same typed task.

### Logistics and loadouts

- Named routes, storage endpoints, restock sources, destination filters, and fallback dumping.
- Reserved tools, food, armor, ammunition, repair materials, scanning tools, research materials, and combat gear.
- Support ordinary containers and portable-storage providers through explicit transactional adapters.
- Never unload reserved gear merely because it is not a vanilla `ItemTool` or `ItemBow`.

### Farming and trees

Implement finite scheduled passes over named plots:

- Vanilla crops and other `IGrowable` blocks.
- Pam crops, hanging fruit, and fruiting logs using correct maturity/right-click behavior.
- Never break Pam fruiting logs merely to harvest fruit.
- Designated tree farms with controlled felling and sapling replanting.
- Mature CropsNH harvesting before breeding support is introduced.
- Configurable seed/sapling reserves and verification after every harvest/replant.

### Husbandry and hunting

- Initial husbandry supports cows, sheep, pigs, and chickens in named pens.
- Feed adults, maintain configured population bounds, cull only excess adults, collect drops, and preserve breeding pairs.
- Never target babies, named animals, tamed animals, players, or protected stock.
- Hunting is a separate whitelist task with a return route, risk budget, and inventory limit.

### Sleep

- Use registered beds through normal server interaction.
- Add an optional Adventure Backpack portable-sleep adapter.
- Trigger from world-time rules and skip/hold when the dimension, danger state, or bed is invalid.

### Tinkers repair

- Use a registered Tool Station or Tool Forge.
- Validate container type and semantic slots.
- Insert the damaged tool and try eligible TConstruct/TGregworks repair materials.
- Accept output only if tool identity is unchanged and `InfiTool.Damage` decreased.
- Return the tool to its reserved slot.
- Trigger before 15% durability or when predicted durability cannot finish the next work unit.

### Thaumcraft automation

Target the installed stack initially:

- Thaumcraft `4.2.3.5`.
- Thaumcraft Research Tweaks `1.4.0`.
- TC4Tweaks `1.5.45`.
- Salis Arcana `1.1.65-GTNH`.
- GTNH TC Wands `1.4.14`.
- Hard research mode (`research_difficulty=1`).

#### Auto Research

Add `ThaumcraftResearchTask` modes:

- Solve the note currently in a registered Research Table.
- Complete a selected research plus its dependency closure.
- Complete a selected category.
- Continue through all currently available safe research until blocked.

Research workflow:

1. Build the runtime research graph from `ResearchCategories` after all GTNH addons have registered entries.
2. Resolve parents, hidden parents, siblings, item/entity/aspect triggers, and research flags.
3. Verify Thaumonomicon, paper, usable scribing tools, ink, aspect pools, inventory capacity, table reach, and warp policy.
4. Create a hard-mode note through the same normal request used by the Thaumonomicon.
5. Insert scribing tools in semantic slot 0 and the note in slot 1.
6. Snapshot the actual `ResearchNoteData` hex grid, aspect pools, bonus aspects, and ink durability.
7. Solve the complete board before sending any mutation.
8. Combine missing compound aspects only when the chosen plan requires them.
9. Place/erase one aspect at a time through normal Thaumcraft packets, waiting for synchronized note, ink, and pool changes after every operation.
10. Retrieve the completed note, move it to the hotbar, invoke normal use-item handling, and verify server-synced research knowledge.
11. Rebuild the available queue because completion may unlock siblings or auto research.

Solver design:

- Treat the actual board as a hex graph and discovered aspects as a second graph whose edges are direct component relationships.
- Search the product graph `(hex cell, aspect)` for compatible connectors among all root components.
- Use several cheapest labeled paths, branch-and-bound over compatible path unions, and an MST lower bound.
- Reject assignments that put different aspects in the same hex.
- Minimize lexicographically: erasures, ink-consuming writes, scarcity-weighted aspect use, and combination cost.
- Preserve existing placed cells unless they make all solutions impossible.
- Validate the final board with a pure local implementation of Thaumcraft's completion predicate before execution.
- Run solving off-thread from an immutable snapshot and compare the world/container/action epoch before applying it.
- If no affordable plan exists, return an acquisition plan or a typed blocked reason rather than making speculative placements.

Research safety rules:

- Never edit research NBT, directly grant knowledge, forge a scan, or call completion methods.
- Only one non-idempotent research transaction may be outstanding.
- A timed-out operation is never blindly resent; close/reopen if necessary and rebuild state.
- Require an empty inventory slot before requesting a note because Thaumcraft may otherwise drop it into the world.
- Default warp policy excludes every research with nonzero warp. Require explicit per-target or per-run approval and show the amount.
- Never automatically reveal or complete lost, hidden, or trigger-gated research before its legitimate trigger occurs.
- Do not request notes for virtual, auto-unlock, empty-tag, or scan-only entries.

Recommended blocked reasons include:

- `MISSING_RESEARCH_PARENT`
- `MISSING_SCAN_TRIGGER`
- `UNDISCOVERED_ASPECT_COMPONENT`
- `MISSING_PAPER`
- `SCRIBE_INK_EXHAUSTED`
- `ASPECT_POINTS_DEFICIT`
- `NO_RESEARCH_TABLE`
- `TABLE_CONTAINER_MISMATCH`
- `UNSOLVABLE_NOTE`
- `WARP_CONFIRMATION_REQUIRED`
- `AMBIGUOUS_TRANSACTION`
- `VERSION_UNSUPPORTED`

Optionally support the later GTNH Research Completer multiblock as another backend. It may use only nodes explicitly registered as sacrificial; it must never consume Node Atlas entries automatically.

#### Node scanning and Node Atlas

Add `AuraNodeSurveyTask`:

- Enumerate loaded tile entities implementing `INode` on the client thread.
- Navigate to a safe line-of-sight position.
- Equip a real Thaumometer and hold normal use for the configured 20 uninterrupted ticks.
- Require the target to remain under the crosshair.
- Verify synchronized scan/aspect knowledge before recording success.
- Persist dimension, coordinates, node ID, type, modifier, base/current aspects, scan status, last observation, safety classification, and recharge history.
- Invalidate or mark stale records when a node moves, is jarred, disappears, or changes identity.
- Normal scans may incidentally update TC Node Tracker, but the bot does not depend on that mod's internal storage.

Default node hazard policy:

- Normal, Pure, Bright, and Pale nodes may be surveyed and considered for approved uses.
- Hungry, Dark, Tainted, Unstable, Fading, unknown addon nodes, and hard-mode hazards are scan-only/avoided unless explicitly enabled.
- Every node is permanently protected from generic excavation and scavenging.

#### General Thaumometer scanning

Add `ThaumometerScanTask` for:

- Unique inventory item variants.
- Registered scan-storage containers.
- Nearby blocks and tile entities.
- Entities and dropped items.
- Supported addon phenomena.

Behavior:

- Use server-synchronized scan knowledge to skip completed fingerprints.
- Scan only targets whose component-aspect prerequisites are currently known.
- After discovering an aspect, retry previously blocked targets until no progress remains.
- Use Salis Arcana's normal inventory/container-scanning semantics through a versioned adapter.
- If the Salis adapter is unavailable, use an opt-in enclosed scan tray: drop one fingerprinted item, scan it normally, recover it, and verify exact return.
- Moving, changing, disappearing, or occluded targets cause clean cancellation/retry.
- Addon phenomena require explicit resolvers because there is no universal enumeration API.

#### Wand recharging

Add `WandRechargeTask` for Thaumcraft casting wands, staves, and scepters.

Charging-source preference:

1. Registered CV-enabled Wand Recharge Pedestal.
2. Registered passive relay/Vis Amulet charging area.
3. Direct drain from a safe Node Atlas entry.
4. Optional EMT/GTNH charging-station adapter.

Requirements:

- Charge profiles specify target primal-vis amounts and node reserves.
- Prefer safe sources that satisfy the missing aspects with the lowest route and depletion cost.
- Direct node drain holds normal wand use, keeps the crosshair fixed, and verifies wand gain/node loss after each five-tick batch.
- Independently enforce a reserve and stop early enough for the maximum Node Tapper drain batch.
- Never sneak while preservation policy is active.
- Do not drain Hungry, Dark, Tainted, Unstable, Fading, unknown, or disallowed nodes.
- Pedestal operations fingerprint full wand NBT, reserve inventory capacity, verify insertion, wait for the charge target, retrieve immediately, and verify the same wand.
- Registered pedestals should be enclosed because activating an occupied pedestal can eject the wand as a world item.
- Every scan/recharge held-use action is owned by the global action gate and releases immediately on critical health or death.

### Mobility

- Add immutable per-agent/per-navigation-session `MovementCapabilitySnapshot` objects created on the client thread and consumed by navigation planning.
- Expose a Horizonwright `MovementEdgeProvider` registry through `NavigationBackend`. The Baritone backend translates providers into its movement graph and preserves provider/edge identity during path reconstruction without leaking Baritone types into core.
- If Baritone engine changes are unavoidable, contain them in the version-pinned navigation backend or an explicitly maintained internal fork with preserved notices; never mix them into feature modules.
- Implement Adventure Backpack Piston Boots first: auto-step, generated high-jump edges, sprint cost, safe-fall trait, and equipment-change invalidation.
- Implement normal OpenBlocks elevators with released jump/sneak pulses, destination verification, timeout/retry, and transport-aware heuristics.
- Add rotating elevators only after building an immutable main-thread tile/link index.
- Implement Hang Glider with an `ActiveTravelController`, not ordinary grid A*: prepare, reach safe launch, deploy, steer, land, verify/fold, and resume ground travel.
- Use gliding only when known launch/landing terrain and conservative estimates make it faster and safe. Never depend on thermal lift.
- Abort to a known safe landing on item loss, blacklisted dimension, unloaded terrain, collision risk, or state timeout.

### CropsNH breeding

- Resolve requested crops by stable crop ID.
- Use CropsNH mutation APIs to plan required parents.
- Let jobs specify independent Growth, Gain, and Resistance minimums; provide a GUI shortcut that applies one value to all three.
- Implement seed acquisition/analysis, soil/environment validation, parent planting, cross sticks, water/fertilizer/weed/sickness maintenance, offspring analysis, best-seed retention, and repeated generations.
- Checkpoint every generation.
- Never discard the best known seed unless a verified equal-or-better duplicate exists.

### Exploration, prospecting, dungeons, and combat

- Persist exploration frontiers, POIs, danger exclusions, return budgets, and home routes.
- Survey GT ore cells using the `3N+1` center grid and a configurable multi-probe bore/lateral pattern.
- Use GregTech APIs to identify natural vein ore, ignore small ores, and interact once to register a discovery with VisualProspecting.
- Persist surveyed cells, coverage/confidence, found material, coordinates, and failures.
- Split combat into target policy, threat/retreat controller, and weapon adapters.
- Support melee, vanilla/Tinkers bows, Tinkers crossbows, and javelins with distinct input state machines.
- Exclude players, friendly entities, named/tamed mobs, babies, and protected husbandry stock.
- Retreat on configurable health, armor, food, ammunition, and tool thresholds.
- Recognize generated dungeons through explicit structure adapters; unknown containers remain untouched.
- Dungeon modes are `Loot Only`, `Secure` with `Light`/`Break` spawner policy, and `Scavenge` with a block whitelist and selected spawner policy.

### Production crafting

- Plan recursively for inventory 2x2 and crafting-table 3x3 recipes.
- Support quantities, recipe alternatives, and OreDictionary ingredients.
- Verify every intermediate and output transaction.
- Support armor and ordinary item crafting.
- Stop at furnace or machine inputs and report the exact missing machine-produced ingredient.
- Reuse the planner for future machine adapters without coupling them to the initial release.

## Delivery Milestones

### Milestone 0: Identity, toolchain, and feasibility

- Establish the Horizonwright Forge project, mod metadata, package namespace, assets, Gradle wrapper, pinned target pack/toolchain, CI build, chosen project license, third-party notices, and release conventions.
- Record the Baritone Backport reference commit SHA and inventory candidate algorithms in the reuse register without making its checkout a build dependency.
- Decide and document whether the exact Baritone backend is bundled, relocated, or separately required; test class-collision behavior.
- Add deterministic fakes for clock, world snapshots, inventory, packets, action execution, and navigation.
- Capture golden fixtures for Circle geometry, crop harvesting, GTNH prospect calculations, storage transactions, and Piston Boots behavior.
- Prove a minimal Horizonwright screen opens in a disposable GTNH instance.
- Implement the minimal non-persistent `ActionBroker`/input-lease path needed by the spike; no navigation proof may bypass it.
- Prove one bounded, lease-gated navigation request can be submitted, observed, cancelled, and completed through `NavigationBackend`.
- Prove the intended input-revocation and outbound-packet interception points work on the pinned Forge/GTNH stack.

Exit criterion: a clean Horizonwright JAR with a selected compatible license and complete notices launches and completes one cancellable, lease-gated navigation vertical slice without any feature module importing Baritone types.

### Milestone 1: Automation kernel

- `IHorizonwrightController`, `TaskOrchestrator`, task runners, queue lanes, scheduling, checkpoints, retries, action epochs, reconnect restore, and immutable snapshots.
- `ActionBroker`, client-thread dispatcher, asynchronous calculation boundary, navigation capability, versioned persistence/backups, named assets, and `#hw` command interface.
- Minimal status/queue GUI, dry-run mode, pause, and emergency stop.
- Implement a simple `GoToTask` as the end-to-end reference task.

Exit criterion: a task can start, suspend, cancel, persist, reload, and complete without the GUI or task code controlling Baritone or Minecraft input directly.

### Milestone 2: Item-preservation safety

- Critical-health policy, death latch, synchronous action/input revocation, packet firewall, respawn quarantine, grave protection, and verified recovery/manual hold.
- Add the minimum safety-owned inventory fingerprint/capacity checks, grave-specific transactional activation, and interaction-disabled recovery navigation required for automatic recovery. Milestone 3 generalizes these into shared services.
- Complete the deterministic death/grave regression harness, including disconnect and restart behavior.

Exit criterion: the complete gravestone-loss suite passes. No unattended feature may ship before this criterion remains green.

### Milestone 3: Shared operations and base vertical slice

- Named locations/routes/areas, loadouts, transactional inventory/container services, unloading, Tinkers repair, and a checkpointed cylinder excavation task.
- Scheduler preemption, fallback resumption, reconnect restoration, and bounded event diagnostics.

Exit criterion: excavation unloads, repairs, survives a synthetic scheduled preemption task and reconnect, then resumes the exact frontier.

### Milestone 4: Operational-base MVP

- Managed-quarry ramps, lighting, fluid containment, ordinary farming/trees, husbandry, and sleep.
- Acceptance scenario: recurring crop and livestock chores preempt the radius-250 excavation; night sleep preempts fallback; unloading and repair occur as needed; excavation resumes after every interruption and reconnect.

Exit criterion: the complete recurring-base scenario runs in a disposable world for the configured observation period without losing reserved items, violating population bounds, or losing the excavation checkpoint.

### Milestone 5: Thaumcraft automation

1. Exact-version adapter, solve-current-note mode, and solver verification fixtures.
2. Full research dependency queue, aspect acquisition, warp policy, and dashboard.
3. General scanning and Node Atlas.
4. Wand recharge sources and scheduled recharge.
5. Optional GTNH Research Completer backend.

Exit criterion: Horizonwright legitimately completes a selected hard-mode research dependency chain, progresses the scan catalog, records a safe node, and recharges the same fingerprinted wand while respecting warp, aspect, node-reserve, transaction, and death-safety policies.

### Milestone 6: Mobility

- Piston Boots, elevators, then Hang Glider active travel.

Exit criterion: the navigation backend selects and verifies each enabled movement provider, aborts safely on equipment/terrain/state changes, and resumes ordinary ground navigation from the verified post-movement position.

### Milestone 7: CropsNH breeding

- Mutation planning and independent G/G/R improvement.

Exit criterion: a checkpointed job breeds a requested crop to independently configured Growth/Gain/Resistance targets and retains the best verified seed across interruption and restart.

### Milestone 8: Exploration stack

- Combat foundation, hunting, exploration, ore prospecting, and generated-dungeon modes.

Exit criterion: a bounded expedition explores new terrain, surveys the configured GT ore cells, interacts only with a recognized dungeon under its selected policy, handles or retreats from threats, and returns home within its risk and inventory budgets.

### Milestone 9: Production automation

- Recursive crafting-grid planner and transactional execution, with later machine adapters using the same production-planning contracts.

Exit criterion: Horizonwright crafts a multi-level item requiring both 2x2 and 3x3 recipes with verified intermediates, and produces an exact typed block reason when a required furnace/machine product is unavailable.

## Test Plan

### Bootstrap and architecture

- Build Horizonwright from a fresh checkout using only documented, pinned inputs.
- Launch the assembled JAR in the pinned disposable GTNH instance and verify mod metadata, configuration, dashboard, and clean shutdown.
- Enforce dependency rules so core, tasks, GUI, and feature modules cannot import Baritone or optional-mod implementation types.
- Run `NavigationBackend` conformance tests for submit, progress, cancellation, stale-result rejection, input release, and backend loss.
- Test bundled/separate Baritone collision behavior and fail with a clear diagnostic rather than duplicate classes or undefined ownership.
- Verify optional integrations can be absent or version-mismatched without causing early classloading failure.

### Core and scheduler

- Priority/preemption, safe suspension/resumption, one-catch-up reconnect behavior, retry/block transitions, action-lease revocation, action-epoch rejection, persistence migration/corruption recovery, profile reassociation, and capability-version failures.

### Death and gravestones

- Simulate held mining, right-use, eating, combat, scanning, wand draining, research placement, and window clicks followed by lethal `S06`, delayed grave placement, stale packets, delayed `GuiGameOver`, rapid respawn, reconnect, dimension change, and artificial latency.
- Assert no dangerous packet reaches the actual write boundary, no key, task, backend, or input owner reactivates, and grave contents remain intact.
- Cover wrong owner, multiple/missing/unloaded graves, partial inventory capacity, changing tile contents, and indefinite manual hold.

### Excavation and base work

- Radius 250, negative coordinates, chunk unloads, stale async results, fluids, protected blocks, managed ramps/lights, logistics interruption, repair, and reconnect.
- Farm maturity/right-click behavior, tree boundaries/replanting, husbandry population rules, and night sleeping.

### Thaumcraft

- Property-test generated and captured complexity 1-4 notes: all roots connected, every link uses a direct component relationship, coordinates are valid, and only discovered/obtainable aspects are placed.
- Compare solver cost with brute force on small boards.
- Test parity detours, duplicate root aspects, addon aspects, holes, partial notes, disconnected extras, necessary erasure, corrupted NBT, and no-solution cases.
- Resource tests assume zero random refunds and verify exact ink, personal pool, table bonus, and aspect-combination cost.
- Test delayed/missing/out-of-order synchronization, manual interference, early completion, sibling auto-unlock, full-inventory note drop prevention, table chunk unload, and reconnect.
- Assert a timed-out placement is never resent without a new snapshot.
- Test scan prerequisite progression, duplicate fingerprints, moving entities, interrupted line of sight, node movement, dangerous nodes, node reserves, and pedestal ejection/recovery.
- Verify no warp-bearing research can enter note creation without approval.
- Verify no direct knowledge/NBT mutation and no Thaumcraft action packet after the death latch.
- Run exact integration against the complete GTNH 2.9.0-beta-2 Thaumcraft stack and representative addon research/aspects.

### Mobility, CropsNH, exploration, and crafting

- Equipment add/remove/break invalidation, elevator color/range/obstruction, glider launch/landing/abort, crop mutation generations and seed retention, prospect grid coverage, weapon state machines, dungeon container classification, and crafting transaction failures.

## Release and Build Requirements

- Feature-flag each milestone and keep unsupported capabilities disabled by default.
- Run manual integration in a disposable copied world with nonvaluable items before enabling unattended mode.
- Do not release unattended mode until the death/gravestone suite passes.
- Build from the Horizonwright repository alone with a pinned Gradle/Forge/mappings/Java/dependency toolchain and run the full unit, property, integration, and smoke suite for every milestone.
- Produce one user-facing `horizonwright-<version>.jar` in `build/libs` unless the Milestone 0 packaging spike documents why multiple artifacts are required.
- A repo-local `dist` directory may contain release candidates and checksums. Optional deployment may target only a configured disposable GTNH test instance's `mods` directory.
- Never place Horizonwright build artifacts in Baritone Backport or treat the old repository as part of the build.
- Add GitHub Actions using the same pinned toolchain and publish tagged artifacts, checksums, supported GTNH versions, third-party notices, and capability compatibility status.
- Use `0.x` versions while persistence formats and unattended-safety guarantees remain unstable.
- Record the final artifact timestamp and cryptographic hash in the release checklist.

## Greenfield Risk Register

- **Baritone packaging and class collision:** bundled unrelocated `baritone.*` classes may conflict with another installation. Milestone 0 must choose one supported ownership model and fail clearly on incompatible duplicates.
- **Third-party licensing:** copied or bundled Baritone and integration code requires provenance records, compatible licensing, attribution, and preserved notices.
- **Safety-hook feasibility:** Forge 1.7.10 input and network interception order must be proven against the pinned GTNH stack before the death design is considered implementable.
- **Residual server race:** the client cannot retract a packet the server received before lethal-health notification; the critical-health lock reduces but cannot eliminate this window.
- **Optional-mod classloading:** one leaked optional-mod type in common code can crash startup before capability checks run.
- **Legacy toolchain decay:** ForgeGradle artifacts or Maven dependencies may disappear; pin and cache legally redistributable inputs and document recovery.
- **GTNH version drift:** addon updates may invalidate containers, packets, NBT, research, movement, and recipe behavior; exact-version adapters fail closed.
- **Scope pressure:** every milestone needs a playable vertical slice and an exit criterion so the project does not become a large framework without usable behavior.
- **Persistence identity:** server address is insufficient after a world reset; profile reassociation must be explicit and auditable.
- **Performance and threading:** radius-250 excavation, scan catalogs, and research solving require bounded snapshots, cancellation, backpressure, and strict client-thread access.
- **Multiplayer policy:** technical client-only operation does not imply permission to automate on a server.

## Technical References

- [OpenBlocks grave interaction](https://github.com/GTNewHorizons/OpenBlocks/blob/1.12.18-GTNH/src/main/java/openblocks/common/block/BlockGrave.java)
- [OpenBlocks death handler](https://github.com/GTNewHorizons/OpenBlocks/blob/1.12.18-GTNH/src/main/java/openblocks/common/PlayerDeathHandler.java)
- [CropsNH crop-stick API](https://github.com/GTNewHorizons/CropsNH/blob/master/src/main/java/com/gtnewhorizon/cropsnh/api/ICropStickTile.java)
- [TConstruct Tool Station](https://github.com/GTNewHorizons/TinkersConstruct/blob/master/src/main/java/tconstruct/tools/logic/ToolStationLogic.java)
- [OpenBlocks elevator implementation](https://github.com/GTNewHorizons/OpenBlocks/blob/1.12.18-GTNH/src/main/java/openblocks/common/ElevatorActionHandler.java)
- [OpenBlocks Hang Glider](https://github.com/GTNewHorizons/OpenBlocks/blob/1.12.18-GTNH/src/main/java/openblocks/common/entity/EntityHangGlider.java)
- [GTNH Thaumcraft configuration](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/blob/master/config/Thaumcraft.cfg)
- [Thaumcraft Research Tweaks 1.4.0](https://github.com/GTNewHorizons/thaumcraft-research-tweaks/releases/tag/1.4.0)
- [Research Tweaks placement adapter](https://github.com/GTNewHorizons/thaumcraft-research-tweaks/blob/455798b/src/main/kotlin/elan/tweaks/thaumcraft/research/frontend/integration/adapters/ResearchNotesAdapter.kt)
- [GTNH runtime Thaumcraft research modifications](https://github.com/GTNewHorizons/NewHorizonsCoreMod/blob/master/src/main/java/com/dreammaster/scripts/ScriptThaumcraft.java)
- [GTNH Research Completer design](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/7347)
