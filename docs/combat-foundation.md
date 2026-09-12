# Combat foundation

This first combat increment supplies target policy, threat/resource decisions,
and melee request timing, plus a live observation command. It does **not** enable
automated attacks, self-defense during tasks, pursuit, or retreat movement.

## In-game diagnostic

With a bound Horizonwright profile, run `/hw combatscan` (12-block radius) or
`/hw combatscan 24`. Radius must be an integer from 1 through 32. The scan uses
only currently loaded living entities inside a sphere around the player. It
reports eligibility totals, a candidate, resource advice, and the nearest eight
entities with their UUID, classification, distance, and exclusion reason.

Players, friendly teammates, named/tamed mobs, children, and all identities in
the active profile's protected livestock list are excluded. Livestock protection
applies across every saved pen, including animals that have wandered outside it.
Dead entities, occluded entities, and entities beyond the selected radius cannot
be candidates. Protected or occluded known hostiles still count as threats for
resource advice. A candidate inside scan range is not evidence of melee reach.

The initial adapter recognizes exact vanilla zombie, skeleton, creeper,
silverfish, slime, magma cube, witch, blaze, and ghast classes. Pig zombies,
endermen, and spiders are excluded because their aggression needs additional
evidence. Other modded entities and hostile subclasses remain unknown. Exclusion
does not establish that an entity is harmless; the scan is not a complete danger
assessment of GTNH mobs. No chunks are loaded to search for targets.

Default resource advice requires more than 50% health, at least 4 armor points,
at least 6 food points (3 hunger icons), and more than 10% remaining weapon
durability. Only the held vanilla sword is recognized by this diagnostic; empty
hands and unsupported weapons report no usable weapon. Armor points measure
current protection, not remaining armor durability. A swelling creeper within
6 blocks produces retreat advice. This is a conservative proximity rule, not an
explosion damage prediction.

The scan is a fresh, one-shot resource assessment. It does not inspect action
leases, input ownership, dry-run or emergency-stop state, retain a recovery
latch between commands, acquire capabilities, change held slots, send attacks,
or drive navigation. `READY` is resource advice only, not permission to attack.

## Core contracts

- `CombatTargetPolicy` applies exclusions before deterministic nearest-target
  selection. A retained target remains selected only while still eligible.
- `CombatThreatController` has configurable health, armor, food, weapon, and
  ammunition thresholds. At or below 40% health it always prohibits attacks.
  After retreat starts at 50%, health must recover to 70% before readiness
  resumes. Missing observations, death, or movement lockdown return `STOP`.
  Resource restrictions return `RETREAT` when threats exist and `HOLD` otherwise.
  Ammunition applies only to ranged readiness; ranged execution is not implemented.
- `MeleeAttackController` emits bounded attack **requests**, not confirmed hits.
  It requires fresh target evidence (current or previous tick), at most 3 blocks
  of observed distance, visibility, eligibility, readiness, and action authority.
  It spaces requests by at least 10 ticks, including across target switches,
  waits during target hurt frames, and enforces an attack budget. Cancellation,
  epoch changes, lost authority, unavailable readiness, and clock rewind stop
  the controller terminally. A new action epoch must use a new controller.

These pure components contain no Minecraft dependencies or input producers.
The melee distance is a conservative positional bound; the future live adapter
must additionally verify actual eye-to-hitbox reach and the unobstructed ray.

## Validation

Automated tests cover all protection flags, player/neutral/unknown exclusions,
deterministic selection and target loss, visibility/range boundaries, invalid
numeric evidence, resource thresholds, critical health, recovery hysteresis,
input lockdown, explosion advice, ranged ammunition requirements, stale/future
observations, request cadence, target switching, hurt frames, cancellation,
epoch revocation, clock rewind, and request budgets. Classifier tests ensure
unknown zombie subclasses do not inherit eligibility.

Physical validation is pending. In the canonical Beta3 Horizonwright instance:

1. Run the scan near an adult vanilla zombie and a cow; only the zombie should
   be eligible. Repeat with a player, baby zombie, named zombie, tamed animal,
   protected livestock, pig zombie, enderman, and daytime spider.
2. Put a wall between the player and the zombie. It should remain counted as a
   threat while becoming ineligible for targeting.
3. Repeat while changing held sword, armor, food, and health; check the resource
   advice against the displayed thresholds. No equipment or movement should
   change from running the command.
4. Check radius boundaries and malformed values. Leave/rejoin or unbind the
   profile and confirm diagnostics require a current world/profile.

## Next implementation slice

Integrate a finite combat task with the task controller and existing action
broker, then add a live melee adapter that reobserves immediately before each
dispatch and binds exact target identity/held weapon to its attack lease and
packet boundary. Handle interruption and confirmation without replaying an
uncertain attack. Add movement-only retreat through the existing navigation
boundary, protected route constraints, and task resumption. Persist operator
settings and expose dashboard controls. Only then extend weapon handling to
Tinkers melee tools, bows, crossbows, and javelins with their own input state
machines and ammunition evidence.
