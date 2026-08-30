# Horizonwright

Horizonwright is a greenfield Forge 1.7.10 client mod for GT New Horizons. It
is intended to own long-running task orchestration, safety, logistics,
persistence, and its local dashboard while treating navigation as a private,
replaceable capability.

The repository is currently at the **Milestone 0 safe-navigation checkpoint**.
It contains the independent Forge entry point, dashboard, pure-Java action and
navigation contracts, deterministic fixtures, an exact hash-verified Baritone
snapshot, a real private Baritone adapter, synchronous input revocation, and an
outbound action firewall. The automated checks are implemented; a physical
GTNH navigation smoke test remains before Milestone 0 is accepted. Unattended
operation remains disabled.

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

The exact `vendor/baritone/baritone-v1.2.19-mc1.7.10.jar` input is declared as
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
recorded build before loading any Baritone API type. Missing, duplicate, or
changed installations leave navigation unavailable with a diagnostic.
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
- Use `/hw goto <x> <y> <z> [tolerance]` for one bounded, lease-gated
  navigation request. Relative coordinates such as `~4 ~ ~` are accepted.
- Use `/hw navcancel` to cancel the current navigation request and release
  inputs.
- Use `/hw stop` to latch the bootstrap emergency stop for the current client
  session.

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
[the Baritone packaging ADR](docs/adr/0002-baritone-packaging.md), and the
[reuse register](docs/reuse-register.md) for current scope and decisions.
Use [the manual smoke-test checklist](docs/manual-smoke-test.md) for future
client validation.
