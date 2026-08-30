# Horizonwright

Horizonwright is a greenfield Forge 1.7.10 client mod for GT New Horizons. It
is intended to own long-running task orchestration, safety, logistics,
persistence, and its local dashboard while treating navigation as a private,
replaceable capability.

The repository is currently at **Milestone 0A: buildable identity bootstrap**.
It contains the independent Forge entry point, a minimal dashboard, the first
pure-Java action and navigation contracts, deterministic unit tests, and
provenance records. It does **not** yet bundle Baritone or permit unattended
operation.

## Pinned target

- GT New Horizons: `2.9.0-beta-2`
- Minecraft: `1.7.10`
- Forge: `10.13.4.1614`
- MCP mappings: `stable_12`
- Build JDK: `25`
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

## Verified client smoke test

Milestone 0A was launch-verified on 2026-08-30 in an isolated GT New Horizons
`2.9.0-beta-2` Prism Launcher instance. The production JAR was discovered as
`horizonwright`, reached the main menu, joined a new singleplayer world, opened
the dashboard with `H`, saved the world, and shut down cleanly. No Baritone JAR
was installed for this test.

## Current client surface

- Press `H` in a loaded world to open the bootstrap dashboard.
- Use `/hw panel` to open it from chat.
- Use `/hw status` to print the action-broker state.
- Use `/hw stop` to latch the bootstrap emergency stop for the current client
  session.

The planned `#hw` command surface will be introduced with the controller in
Milestone 1. `/hw` exists now as a Forge-native bootstrap command so the first
screen can be smoke-tested without introducing a chat or mixin hook early.

## Safety and server policy

Horizonwright is experimental automation software. It may violate a server's
rules even though it is client-only. Confirm the rules of any server before
using it. No unattended feature is release-eligible until the later death and
gravestone regression suite passes.

See [the implementation plan](HORIZONWRIGHT_IMPLEMENTATION_PLAN.md),
[the architecture boundary](docs/adr/0001-greenfield-boundaries.md), and
[the reuse register](docs/reuse-register.md) for current scope and decisions.
Use [the manual smoke-test checklist](docs/manual-smoke-test.md) for future
client validation.
