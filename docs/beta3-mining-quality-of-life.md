# Beta3 mining and scheduling update

Date: 2026-09-11. Physical-test instance: GTNH-2.9.0-Beta3-Horizonwright,
UUID 44d6c004c8a54826a7b334625f610a16.

## Behavior

- Task and schedule creation is allowed during a manual automation stop.
  The action broker remains stopped and queued tasks cannot execute until it
  is reset. Existing blocked tasks still require explicit resume. Death safety
  retains its existing creation restriction.
- Moving mining can take over from navigation 0.75 blocks beyond the player's
  normal block reach, when there is a clear ray to the target. This starts a
  short guarded walking approach; no digging starts until the existing exact
  reach and target checks pass. Unsafe/stalled walking falls back to navigation.
- Routine excavation unloading announcements use aqua. Missing requirements
  use yellow. Actual safety, configuration, external, retry-exhaustion and
  unsafe-to-continue failures retain red.
- The user-provided screenshot identifies BOP thorns as
  BiomesOPlenty:plants metadata 5. That exact variant joins quicksand
  (BiomesOPlenty:mud metadata 1) in path, walking and support hazard checks.
  Other plants and ordinary mud are not classified as these hazards.
- Excavation no longer applies the automatic planting-supply reserve.
  Linked excavation unload tasks also release those automatic reservations,
  including when resolving the live chest loadout. Food, tools, repair
  materials and explicitly selected named loadouts retain their policies.
- Excavation runs a periodic nearby-drop pass every two minutes with confirmed
  work, and before accepting a cleared layer/final verification. The previous
  16-target / 15-second trigger was removed so concurrent pickup while digging
  has time to operate. The timer starts with the task, not at clock zero.
  Each pass scans loaded drops within 24 blocks horizontally and 12 vertically,
  inside the excavation's horizontal footprint. Only inventory-compatible
  pickups are approached. Navigation is movement-only, with eight-second
  attempts and a thirty-second pass budget. Inaccessible drops can remain for
  later passes rather than exhausting excavation retries. Collection is
  cancelled on pause or loss of authority without advancing mining progress.
- Nightly sleep departs early using a conservative same-dimension travel
  estimate: 0.45 seconds per horizontal block, 1.5 seconds per vertical block,
  plus a five-second handoff allowance, capped at five minutes. This is a
  distance/height estimate, not an exact computed route.
  Only the schedule's opening time shifts; its night occurrence and morning
  cutoff remain unchanged. Early arrival waits at the bed for the normal
  sleep window. Completion still requires daytime and waking.
  The schedule editor displays estimated departure and trip timing.

## Validation

gradlew.bat spotlessApply build passed: 829 tests, zero failures, errors or
skips. Checkstyle, formatting, JVM verification, pinned Baritone checks and
production artifact isolation passed. git diff --check passed.

New/updated regression coverage includes queued creation under an engaged stop,
early sleep action submission, one schedule occurrence across changing lead
times and persistence, exact thorn metadata, the walking reach handoff,
planting-supply unloading, inventory capacity for pickups, pickup completion and
interruption, and informational versus failure colors.

Live Minecraft behavior has not been physically exercised for this update.
Suggested checks: create work while stopped; reset and run a small excavation
with moving mining; observe pickups and sapling unloading; travel near thorns;
let a distant registered bed schedule trigger before nighttime and wait there.

## Deployment

The exact name, UUID and resolved path were verified after the user closed the
test instance. No live process matched its exact instance path before copying.

Installed Horizonwright: horizonwright-0.1.0-SNAPSHOT.jar
SHA-256: 439c5fb70d995f70aee3203a3a450e113dc5542073890627cf04f47596a318dc

Verified installed Baritone:
baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c.jar
SHA-256: c6c25e1afe9a0406dc6b905c2d7c382831abdde05163569021cf5f59f14019b7

Previous Horizonwright jar:
build/deployment-backups/horizonwright-before-mining-pickups-sleep-travel-20260911-210625.jar


## Simultaneous pickup and digging follow-up

On 2026-09-11, Walk while breaking gained live drop-directed movement during
an ordinary excavation dig. Each movement tick rescans nearby grounded drops
inside the excavation footprint, with inventory capacity checked. It chooses
the nearest drop whose destination preserves the current block's breaking reach
and whose next step passes collision, ray, support, liquid and hazard checks.
Movement includes current momentum in those checks. Look remains aimed at the
current digging block; forward/strafe input can move sideways or backward.
Within 0.35 blocks of a drop, walking holds for pickup synchronization while
digging can continue. This is enabled by the existing Walk while breaking option.
Obstruction/tree-log recovery and the pre-reach approach retain their movement
policies. The periodic and layer-end pickup passes remain for farther drops and
detours. This change does not extend block interaction reach.

Validation: spotlessApply build passed 833 tests with zero failures, errors or
skips; formatting, Checkstyle and artifact checks passed. New tests cover nearest
safe selection, out-of-reach/elevated/obstructed drops, arrival hold, and world
movement independent of look yaw. Physical Minecraft validation is still pending.

After the user closed Beta3, its exact display name, UUID and directory were
reverified and no matching process remained. The updated jar was installed:

Horizonwright SHA-256:
f5cb8fd4eb600148f14ffbe8b622598fd4b285cb9bf1c83a9ee231252c77a82b

Baritone filename/hash remain as recorded above and were verified again.
Backup: build/deployment-backups/horizonwright-before-concurrent-pickup-20260911-212305.jar
