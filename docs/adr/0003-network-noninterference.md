# ADR 0003: Network noninterference boundary

- Status: Accepted
- Date: 2026-08-30

## Decision

Horizonwright's outbound safety gate interferes only with network traffic whose
action semantics have been explicitly integrated and regression-tested.
Unknown or unintegrated packets pass through unchanged in every action-session
mode, including quarantine and safety lockdown. They never increment the
blocked-action counter and cannot terminate a task.

The packet classifier has three practical outcomes:

- understood action traffic is checked against its exact action capability;
- explicitly integrated safety traffic is handled by its dedicated tested gate;
- unknown or unintegrated traffic is observe-only and passes through unchanged.

Adding a mod or custom-payload packet to an action gate requires an exact-version
adapter, documented semantics, and positive, denial, quarantine, and identity-
preservation tests. An allowlist of supposedly harmless third-party channels is
not an acceptable substitute.

## Rationale

GTNH contains hundreds of independent network protocols, and additional mods
may be installed later. Horizonwright cannot safely infer that an unfamiliar
packet is either harmless or actionable. Blocking unfamiliar traffic can break
unrelated mods, create intermittent desynchronization, and hide the real cause
several layers away from the affected feature.

The final outbound boundary still closes the revocation race for understood
Minecraft actions such as movement, digging, placement, use, attack, inventory
mutation, and respawn. Producer-side authority remains primary; the packet gate
is a last-line interlock for those integrated actions, not a general Minecraft
network firewall.

## Consequences

- Emergency and death safety block understood action packets after authority is
  revoked, including packets already queued for write.
- Horizonwright does not claim that unknown third-party traffic is safe; it
  simply does not own or alter that traffic.
- New action integrations must extend the semantic classifier and its tests
  before the boundary may block their packets.
- Diagnostics may identify observed unknown traffic, but observation must not
  delay, release, rewrite, fail, or count it as a denied action.
