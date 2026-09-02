# ADR 0002: Baritone backend packaging

- Status: Accepted
- Date: 2026-08-30

## Context

The Minecraft 1.7.10 Baritone fork provides the selected navigation engine,
but the neighboring checkout contains unrelated local work and is not a
Horizonwright build input. Baritone's launch hooks, mixins, service provider,
reflection, and relocated fastutil classes make embedding or relocating it
riskier than loading it as an ordinary Forge coremod.

Horizonwright must retain ownership of task scheduling, action authorization,
input release, commands, and user-facing state. Baritone must remain a private,
replaceable navigation implementation and must never gain authority merely
because its classes are present.

## Decision

Require a separate, exact-version Baritone JAR at runtime. The user-selected
enhanced build is
`baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c.jar`, with production
binary SHA-256
`bcd9d1b8ad15fb7bc5abe7e3dcf9e54018425bc7dd98c8b5d3bf9008c52e3bb3`.
The clean external filename contains compiled local improvements; its embedded
Gradle version deliberately retains the source tree's `-dirty` marker.

The binary is also declared as `devOnlyNonPublishable` so the adapter can
compile and local development runs can load it. It is never embedded, shaded,
relocated, published in Horizonwright metadata, copied from the neighboring
checkout, or placed inside Horizonwright's production JAR.

Horizonwright orders itself optionally after mod ID `baritone`. It can still
launch without Baritone, but its navigation capability remains unavailable.
Before loading any Baritone API type, the installation probe requires:

- exactly one loaded `baritone` mod container;
- Forge manifest version
  `1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c-dirty`;
- exactly one `baritone/api/BaritoneAPI.class` resource;
- exactly one provider resource containing only `baritone.BaritoneProvider`;
- a readable source artifact; and
- the exact production JAR SHA-256 outside a deobfuscated development run.

Missing, duplicate, byte-different, or structurally incompatible installations
fail closed with a dashboard and `/hw status` diagnostic. Deobfuscated or
remapped development artifacts may pass structural validation while being
reported as `referenceBytes=false`; production may not.

## Authority boundary

The real adapter lives only under `navigation.baritone`, owns a private
`IBaritoneProcess`, and accepts one request at a time. A request must carry a
current Horizonwright lease with `MOVEMENT` and `LOOK` and the same action
epoch. Revocation, explicit cancellation, world loss, dimension change,
backend loss, or a safety-firewall denial terminates the request and releases
Baritone and vanilla inputs.

Horizonwright does not write Baritone settings. The outbound packet firewall
therefore rejects any attack, dig, place, use, held-slot, container, or other
restricted packet that Baritone attempts without a matching capability. The
Milestone 0 route is intentionally clear and unobstructed; any attempted world
mutation fails the route rather than broadening its lease.

## Provenance and license

- Official upstream: <https://github.com/cabaletta/baritone>
- Official `v1.2.19` base commit:
  `d9cb2d91a06501c5bcba2181509d0df80361f413`
- Minecraft 1.7.10 fork: <https://github.com/kaseyawolf2/baritone>
- Build commit identity: `fcbbd4882cc7d846a8e613dea4b50203e1fb4ebc`
- Corresponding Gradle sources artifact SHA-256:
  `b1aa8cad9ba4e05199e0e6cb58cc4cdda542d6b186bccf3321fac0a98885439f`

The vendored binary and corresponding Gradle sources artifact are the
authoritative durable compatibility record for this enhanced build.

Baritone is LGPL-3.0-or-later. `vendor/baritone/` preserves the corresponding
source, LGPL text, upstream `LICENSE-Part-2.jpg`, complete GPLv3 text, and
checksums. Baritone relocates fastutil 8.5.13 under
`baritone.shadow.it.unimi.dsi.fastutil`; its complete Apache-2.0 license is
preserved as well.

Horizonwright can be rebuilt and relinked against an interface-compatible
modified Baritone. The exact validated binary is a safety compatibility record,
not a restriction on modification. A changed build requires deliberate review
and an updated commit, source snapshot, licenses, checksums, compatibility
record, and safety tests before production navigation is enabled.

## Verification

`verifyBaritoneArtifacts` hashes the binary, corresponding sources JAR,
licenses, and checksum manifest. `assemble` and `check` depend on it.
Architecture tests keep Baritone imports inside the adapter package and inspect
the production Horizonwright JAR for class collisions.

Deterministic tests cover installation mismatch and duplicate-resource
diagnostics, lease and epoch rejection, cancellation, input release, and packet
classification. A real GTNH launch, clear-route completion, cancellation, and
emergency-stop run remain the physical acceptance gate for this checkpoint.

## Consequences

- Installation requires two independent mod JARs: Horizonwright and the pinned
  Baritone runtime.
- Horizonwright's production artifact contains no `baritone.*` or relocated
  fastutil classes.
- Missing or incompatible Baritone never prevents the rest of Horizonwright
  from loading, but navigation remains unavailable.
- Updating Baritone is an explicit compatibility and safety event rather than
  an ordinary dependency bump.
