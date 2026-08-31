# Horizonwright

Horizonwright is a greenfield Forge 1.7.10 client mod for GT New Horizons. It
is intended to own long-running task orchestration, safety, logistics,
persistence, and its local dashboard while treating navigation as a private,
replaceable capability.

The repository has completed the **Milestone 1 task-control vertical slice**
and is integrating **Milestone 2 item-preservation safety**. It contains the
independent Forge entry point, dashboard, resumable task controller, per-world
atomic persistence, exact hash-verified Baritone adapter, synchronous input
revocation, and a noninterfering outbound action boundary. The death-safety
kernel is now attached to live health, connection, persistence, respawn, and
packet boundaries. The version-isolated OpenBlocks adapter now decodes only
the owner and empty-state fields that OpenBlocks 1.12.18-GTNH actually syncs
to clients. Its loaded-chunk scanner bounds discovery to the configured death
radius, maps the current account name to the recorded player incarnation, and
conservatively reconstructs likely grave contents by subtracting the observed
respawn inventory from the live pre-death manifest. Those observations now
feed the live search, stabilization, and verification phases;
missing restart evidence enters an explicit manual hold instead of being
fabricated. Exact activation packets are matched solely against immutable
Minecraft-thread evidence. Interaction-disabled recovery navigation now uses
one death-scoped `MOVEMENT`/`LOOK` lease while the ordinary death lockdown
continues blocking every integrated interaction packet; unintegrated mod
traffic remains pass-through. Automatic activation selects a client-verified
empty hotbar slot, authorizes only that exact slot-change and start-sneaking
pair, then sends the one exact permit-bound grave use and releases sneaking.
Restart checkpoints now retain bounded pre-death inventory and stable-grave
evidence, allowing an interrupted recovery to revalidate safely and allowing a
consumed grave activation to resume verification without replay. The physical
recovery test remains incomplete. Milestone 3 shared operations have started
with durable named loadouts and conservative whole-stack unload selection;
arbitrary modded equipment is protected by configured identity rules rather
than vanilla item-class guesses. Destination filters defer nonmatching items,
and adapter-proposed quick moves become executable only after reservation,
exact-snapshot, empty-cursor, source-reduction, and whole-container content
conservation checks. Excavation can checkpoint its exact frontier for unloading
or repair before acquiring an action lease. The adapter-neutral Tinkers repair
gate triggers at the configured durability threshold or when the next work unit
would exhaust the tool, and accepts output only when material was consumed,
stable tool identity is unchanged, and `InfiTool.Damage` decreased.
The live container boundary now fingerprints the exact window, slot layout,
contents, cursor, and item NBT; correlates each prepared click with its outbound
transaction number and matching server response; and refuses to expose a later
click until the exact synchronized after-state is visible. Rejections, epoch
changes, disconnects, and timeouts abort terminally, and an uncertain click is
never resent. Inactive or unrelated packet traffic remains unchanged.
Verified unloading is now also a first-class resumable task. It preserves named
loadout reservations, applies the configured destination filter, checkpoints a
stable digest of the entire predicted click chain before acquiring container
authority, and revalidates the container on the following step. Restoring a
prepared or awaiting-confirmation checkpoint always performs observation-only
reconciliation first, so a crash cannot blindly replay an uncertain quick move.
Pinned-version Tinkers repair is likewise a first-class resumable task. The
runner applies the durability policy, accepts clicks only in semantic station,
reserved-tool, or explicitly approved material slots, and fingerprints both
the click chain and its tool/material evidence before execution. Completion
requires the exact transaction plus synchronized proof that repair material was
consumed, stable tool identity and maximum durability were retained, the tool
returned to its reserved inventory slot, and `InfiTool.Damage` decreased.
Rejected or mismatched confirmations enter an operator reconciliation hold and
are never automatically replayed after restart.
The unload runner is now connected to a live, session-scoped vanilla 1.7.10
chest adapter. A storage endpoint persists its destination filter and refers to
one named location; the adapter verifies that the open chest is the tile at
that exact dimension and coordinate. Its quick-move model reproduces vanilla
chest slot order, hotbar mapping, existing-stack merges, and empty-slot
placement on copies before submitting anything. Unsupported or subclassed
containers are refused, a full destination produces no speculative click, and
each predicted click still passes through the shared server-confirmed
transaction executor. World retirement identity-unbinds the adapter before the
runtime closes, preventing a prior connection from clearing or using a newer
session's service. This live unload path is covered by automated layout,
capacity, cursor, persistence, transaction-owner, and lifecycle tests but has
not yet been installed into the Prism instance or physically exercised.
Pinned Tinkers repair now has the corresponding live prepared-station path.
The adapter follows the actual 1.14.93-GTNH semantics: station slot `1` is the
damaged input, slots `2...` are materials, and slot `0` is the repaired output
preview. A named repair station binds one exact world location to one loadout;
the live adapter requires both the input tool and every consumed material to
match the appropriate loadout roles. It derives exact material decrements from
the preview's `ToRemove` evidence, strips that transient tag from the predicted
final tool, then plans only the two normal clicks which take the output and put
it back into the empty reserved player slot. Completion requires both clicks
to pass the shared server-confirmed boundary and a fresh synchronized snapshot
to prove the repaired stable tool identity is in that reserved slot. The
prepared-station path is automated-test covered but remains physically
unverified and does not yet populate the station inputs on its own.
Excavation service interruptions are now composed by the session runtime rather
than by a task runner or GUI. An excavation specification can persist named
unload and repair bindings. At an exact unloading or repair checkpoint, the
runtime creates one deterministic child task whose parameters authenticate the
parent task, checkpoint revision, and suspension reason. A completed matching
child is the only event that resumes the parent; failed, cancelled, incomplete,
malformed, or colliding work leaves it blocked. The link survives controller
persistence, so reconnect cannot duplicate the service operation, and the
resumed excavation retains the same frontier. This composition is covered by
automated completion, failure, collision, and restart tests but remains
physically unverified pending installation and the prepared-station test.
The dashboard now includes a guided Profile assets page. It captures a tool and
repair material from numbered player-inventory slots and captures a vanilla
chest or Tinkers station from the block currently under the crosshair. Operators
provide short names; Horizonwright derives item registry identities, metadata,
dimension, and coordinates without exposing JSON or NBT. Dependent assets are
validated and atomically merged into the exact active world profile, existing
unrelated assets remain intact, and a stale page cannot write after a profile
or world binding changes. The first chest editor deliberately creates an
accept-all destination: loadout reservations remain protected, while a future
filter editor will narrow which unreserved items belong in each chest.
The same page now opens a guided **Work areas** editor. The operator stands at
two opposite corners and captures the current feet position at each one;
Horizonwright normalizes the inclusive bounds, refuses cross-dimension corners,
and atomically saves the named area to the active world profile. Existing
schema-v1 profiles without the optional area field load with an empty list, so
this foundation for farms and animal pens does not invalidate enrolled worlds.
The first operational-base task contract is also in place: a finite farm pass
is a CHORE-lane task bound to one named plot and a configured minimum seed
reserve. Its strict checkpoint freezes every crop position, adapter family,
block-state fingerprint, required seed identity, maturity/protection evidence,
verified-mutation count, and next observation index. Restored checkpoints must
match the task's plot and revision exactly; changed or cross-task evidence is
rejected instead of replayed. The resumable runner freezes one bounded scan,
advances non-mutating decisions without a gameplay lease, grants only the
movement/look/dig/place or movement/look/use capabilities required by a planned
mutation, and advances only after a changed, verified immature crop is observed.
Pause cancels an unconfirmed action at the same crop. A version-isolated backend
contract now carries all scan, target, seed-reserve, action, and confirmation
authority. The live observation half now resolves only an exact identity-bound
named area, refuses cross-dimension, oversized, or partially unloaded plots,
and recognizes only pinned vanilla wheat, carrots, potatoes, and nether wart.
Its finite scan order, block/meta fingerprint, exact replant item identity, full
inventory digest, current count, and configured reserve cross the backend
boundary without mod objects. Unknown mod crops are never guessed. The live
mutation half approaches under the same task lease, waits for the navigation
packet drain, revalidates the crop and complete seed snapshot, and requires the
exact approved seed in a stable hotbar slot. It breaks one mature crop, selects
that slot only while the action session is active, performs one normal replant
interaction, restores the prior selected slot, and ends its packet-producing
session before waiting for synchronization. Only a changed, same-family,
same-seed, unprotected, visibly immature replacement confirms the action. The
backend identity-binds to one world runtime and is removed on retirement.
Operators can queue one pass from **Profile assets > Work areas** or with
`/hw farm <task-id> <plot-id> [seed-reserve]`. This live path is automated-test
covered at its planner, runner, authority, classifier, and proof boundaries but
remains physically unverified.
Clean-volume excavation is now attached to a live, session-owned backend. Every
observation and action carries the explicit dimension as well as the existing
geometry, frontier, revision, epoch, and block fingerprint. The observer treats
the tested OpenBlocks grave tile as a grave, protects every other tile entity as
infrastructure, refuses blind fluid clearing, and marks unloaded or unbreakable
targets unreachable. Ordinary blocks require one combined movement/look/dig
lease. Horizonwright first uses the movement-only navigation context to approach
the exact target, waits for that packet session to drain, rechecks fingerprint,
classification, reach, and line of sight, then begins one vanilla dig. Only a
fresh observation proving that exact target is air advances the frontier.
Cancellation, timeout, epoch loss, target replacement, or loss of reach stops
the producer without advancing. The backend identity-binds and unbinds with its
world session. Its pure classification, runner authority, and lifecycle pieces
are automated-test covered; the direct dig path remains physically unverified.
Before an ordinary block can acquire that lease, configured excavation services
now inspect exact live inventory evidence. Fewer than two empty main-inventory
slots requests unloading first. The configured reserved tool slot is decoded by
the same pinned Tinkers adapter used by repair; the plan's fifteen-percent
durability threshold or insufficient durability for the predicted work unit
requests repair. Non-breaking targets never create service churn. These reasons
are persisted at the current frontier and flow into the durable child-task
coordinator; no runner or GUI directly performs the service operation.
Operators can now reach that path without editing task JSON. The **Profile
assets** page opens a guided **New excavation** form which centers a validated
clean-volume cylinder at the player's X/Z when it is queued, and optionally
binds the saved loadout, chest, repair station, reserved tool slot, and expected
work damage. The equivalent typed entry point is
`/hw excavate cylinder <id> <radius> <bottom-y> <top-y>`; supplying all five service arguments adds the
named unload and repair bindings. Both paths validate geometry and profile
references before the controller accepts the task.
Unattended operation remains disabled.

## Pinned target

- GT New Horizons: `2.9.0-beta-2`
- Minecraft: `1.7.10`
- Forge: `10.13.4.1614`
- MCP mappings: `stable_12`
- Build JDK: Temurin `25.0.4.1+1`
- Runtime bytecode: Java 8 compatible
- Gradle: `9.3.1`
- GTNH build conventions: `2.0.20`

## Build

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

Shipping and development JARs are written only to `build/libs`. The build has
no dependency on the neighboring Baritone Backport checkout.

The exact
`vendor/baritone/baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c.jar`
input is declared as
`devOnlyNonPublishable`: it is available for compilation and local development
runs but is not copied into Horizonwright's production JAR or published
metadata. It is the separately installed runtime dependency selected by the
packaging ADR. `assemble` and `check` verify the binary, corresponding source,
and license artifacts against hashes pinned in `build.gradle.kts`; `check` also
opens the production JAR and rejects embedded Baritone classes, its service
provider, or class files newer than Java 8.
Verification can also be run directly:

```powershell
.\gradlew.bat verifyBaritoneArtifacts
```

At runtime, Horizonwright validates that exactly one Baritone mod, API class,
and provider exist and that the production JAR's version and SHA-256 match the
recorded build before loading any Baritone API type. The selected clean-named
JAR embeds build version
`v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c-dirty`; Forge 1.7.10 normalizes its
loaded `ModContainer` version to `1.2.19-mc1.7.10`. Its SHA-256 is
`cc24115b0b61c14678e3634e9257e1e155e1eb6ca570accb7d10622f9d4fff0e`.
Missing, duplicate, or changed installations leave navigation unavailable with
a diagnostic.
Horizonwright source can still be rebuilt against an interface-compatible
modified Baritone; enabling changed production bytes requires deliberate review
and an updated compatibility record.

## Verified client smoke test

Milestone 0A was launch-verified on 2026-08-30 in an isolated GT New Horizons
`2.9.0-beta-2` Prism Launcher instance. The production JAR was discovered as
`horizonwright`, reached the main menu, joined a new singleplayer world, opened
the dashboard with `H`, saved the world, and shut down cleanly. No Baritone JAR
was installed for this test.

## Current client surface

- Press `H` in a loaded world to open the bootstrap dashboard.
- Use `/hw panel` to open it from chat.
- Use `/hw status` to print action-broker and navigation state.
- Use `/hw goto <x> <y> <z> [tolerance]` for a lease-gated navigation task.
- Use `/hw excavate cylinder <id> <radius> <bottom-y> <top-y>` to queue a
  clean-volume cylinder centered at your current X/Z without shared services.
  Append `<loadout> <storage> <station> <tool-slot> <work-damage>` to bind all
  guided unload and repair services, or use **Dashboard > Profile assets > New
  excavation** for the nontechnical form.
- Use `/hw farm <task-id> <plot-id> [seed-reserve]` for one finite CHORE-lane
  pass over a saved work area, or use **Dashboard > Profile assets > Work areas
  > Queue one farm pass**. Keep the exact replant seed in the hotbar; the
  default reserve is 2.
  Relative coordinates such as `~256 ~ ~` are accepted; Horizonwright does not
  impose the old 128-block smoke-test radius.
- Use `/hw navcancel` to cancel the current navigation request and release
  inputs.
- Active and queued goals are saved to the enrolled world profile on exit and
  automatically restored when that same world is rejoined.
- Use `/hw stop` to stop Horizonwright automation. It revokes automation leases
  and drains already-queued automation packets, but does not lock direct player
  movement, mining, inventory use, or unrelated mods.
- Use `/hw reset` after cleanup drains to re-arm automation. Stopped tasks still
  require an explicit resume or a newly submitted task.

The manual automation stop is separate from the death/item-preservation
interlock. Only a verified death-safety transition may engage that narrower,
packet-level safety state.

The planned `#hw` command surface will be introduced with the controller in
Milestone 1. `/hw` exists now as a Forge-native bootstrap command so the first
screen can be smoke-tested without introducing a chat or mixin hook early.

The dashboard reports the installation-probe diagnostic and the most recent
navigation progress. Milestone 0 grants only `MOVEMENT` and `LOOK`; attempted
digging, placement, interaction, attacks, held-slot changes, or container
mutations are blocked and fail the route.

## Safety and server policy

Horizonwright is experimental automation software. It may violate a server's
rules even though it is client-only. Confirm the rules of any server before
using it. No unattended feature is release-eligible until the later death and
gravestone regression suite passes.

See [the implementation plan](HORIZONWRIGHT_IMPLEMENTATION_PLAN.md),
[the architecture boundary](docs/adr/0001-greenfield-boundaries.md), and
[the Baritone packaging ADR](docs/adr/0002-baritone-packaging.md),
[the network noninterference ADR](docs/adr/0003-network-noninterference.md), and the
[reuse register](docs/reuse-register.md) for current scope and decisions.
Use [the manual smoke-test checklist](docs/manual-smoke-test.md) for future
client validation.
