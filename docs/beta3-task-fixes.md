# Beta3 quarry, sleep, lumberaxe and quicksand fixes

Date: 2026-09-11. Target: GTNH-2.9.0-Beta3-Horizonwright,
Prism UUID `44d6c004c8a54826a7b334625f610a16`.

## Evidence and changes

- The saved managed-quarry task repeatedly failed to submit infrastructure
  because its approved material, `minecraft:cobblestone`, was absent from the
  player inventory. Missing material now blocks with an actionable refill
  requirement without spending retries. Supplying the material and resuming
  preserves the quarry checkpoint. Existing inventory preparation handles
  supported bags before this check.
- Scheduled sleep repeatedly failed during the previous excavation/navigation
  packet drain. The sleep runner now waits for readiness before acquiring and
  submitting its action, keeping the sleep task active through the handoff.
- Live sleep previously confirmed after five sleeping ticks, including a logged
  confirmation at world time 14985. It now requires daytime and an awake player.
  The confirmation deadline refreshes while sleeping, so waiting for other
  players does not restart excavation in bed. Bed reach, ray tracing and aiming
  use the vanilla 1.7.10 player interaction origin.
- Excavation repeatedly failed to approach high leaves after clearing their
  connected logs. It now allows a short leaf-decay wait, reconciles targets
  removed during navigation, and retains support placement plus the exact leaf
  block allowlist through canopy approach retries. Isolated remaining leaves
  receive the same canopy support. The tree backend also reconciles captured
  logs removed by a lumberaxe while approaching them.
- The installed Beta3 registry export identifies quicksand as
  `BiomesOPlenty:mud` metadata 1. Metadata 0 is ordinary mud. Baritone movement
  cost now rejects quicksand across support/body cells for steps, diagonals,
  jumps and falls, while allowing escape from the starting quicksand column.
  Moving excavation brakes before quicksand, and ramp observations reject it
  as stable support.

## Validation and deployment

`gradlew.bat spotlessApply build` passed: 817 tests, zero failures, errors or
skipped tests. Formatting, Checkstyle, JVM and Baritone artifact verification,
and production artifact isolation passed. `git diff --check` passed.

Regression tests cover sleep packet-drain handoff without retry consumption,
daytime/awake completion, quarry refill and checkpoint continuation, canopy
approach permissions across retries, and quicksand metadata/travel cases.
These are automated checks; the changed behavior still needs an in-game run.

The exact Prism name, UUID and resolved directory were verified immediately
before deployment, with no matching live instance process. Installed hashes:

- `horizonwright-0.1.0-SNAPSHOT.jar`:
  `4c0e64fccc8799497860eb7f7480151a79bf3b309d477624fb837f4075c856f2`
- `baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c.jar`:
  `c6c25e1afe9a0406dc6b905c2d7c382831abdde05163569021cf5f59f14019b7`

Previous Horizonwright jar backup:
`build/deployment-backups/horizonwright-before-quarry-sleep-tree-quicksand-20260911-163526.jar`.

## Physical checks

1. Resume the quarry with cobblestone available; confirm infrastructure proceeds.
   Removing required supplies should produce a refill block, not retry exhaustion.
2. Let nighttime sleep interrupt an active excavation. Confirm the packet drain
   completes, the registered bed is used, and excavation resumes only after
   morning and waking. Repeat with delayed sleep on a multiplayer server.
3. Resume the failed canopy excavation with a lumberaxe. Confirm removed logs
   are reconciled and surviving high leaves use supported approaches.
4. Request navigation and moving excavation beside BOP quicksand. Confirm they
   avoid its support/body cells; ordinary mud must remain distinct.
