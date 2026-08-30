# Pinned toolchain

| Component | Pin | Purpose |
| --- | --- | --- |
| GTNH pack | `2.9.0-beta-2` | Initial compatibility and disposable smoke-test target |
| Minecraft | `1.7.10` | Game target |
| Forge | `10.13.4.1614` | Mod loader and client API |
| MCP | `stable_12` | Development mappings |
| Build JDK | `25` | Required by GTNH build conventions `2.0.20` |
| Target bytecode | Java 8 | Compatibility with the 1.7.10 ecosystem |
| Gradle | `9.3.1` | Wrapper distribution |
| GTNH conventions | `2.0.20` | RetroFuturaGradle project configuration |

All build inputs must resolve from this repository's declared repositories.
No absolute path or composite build may point at the neighboring Baritone
checkout.

Milestone 0A was launch-verified on 2026-08-30 with a clean GTNH
`2.9.0-beta-2` Prism Launcher instance. The reobfuscated production JAR loaded
as mod ID `horizonwright`, reached the main menu, joined a disposable
singleplayer world, opened its dashboard through the `H` key binding, saved,
and exited cleanly. The test instance contained no Baritone JAR.
