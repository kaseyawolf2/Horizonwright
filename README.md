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
