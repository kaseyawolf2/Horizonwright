# Pinned toolchain

| Component | Pin | Purpose |
| --- | --- | --- |
| GTNH pack | `2.9.0-beta-2` | Initial compatibility and disposable smoke-test target |
| Minecraft | `1.7.10` | Game target |
| Forge | `10.13.4.1614` | Mod loader and client API |
| MCP | `stable_12` | Development mappings |
| Build JDK | Temurin `25.0.4.1+1` | Required by GTNH build conventions `2.0.20` |
| Target bytecode | Java 8 | Compatibility with the 1.7.10 ecosystem |
| Gradle | `9.3.1` | Wrapper distribution |
| GTNH conventions | `2.0.20` | RetroFuturaGradle project configuration |
| TConstruct | `1.14.93-GTNH` / `D4B5C6F4...C772E` | Exact Tool Station/Forge repair adapter |
| TGregworks | `1.7.10-GTNH-1.0.33` / `93FFCA6F...5E807` | Eligible GT repair-material stack |
| Mantle | `0.5.4` / `6E5C4B06...6B9EE` | Required TConstruct runtime foundation |

The daemon provisioning URLs name the exact Temurin release rather than the
mutable `latest/25` endpoint. Adoptium does not publish this release for
Windows AArch64, so the repository declares Linux AArch64/x86-64, macOS
AArch64/x86-64, and Windows x86-64 only. The Gradle wrapper also verifies the
official Gradle 9.3.1 binary-distribution SHA-256 before use, and its bootstrap
JAR matches Gradle's published 9.3.1 wrapper checksum. `assemble` and `check`
run `verifyBuildJvm`, which rejects a daemon whose Java version, runtime build,
or vendor differs from the recorded Temurin build. Distribution and wrapper
hashes come from <https://gradle.org/release-checksums/>.

All build inputs must resolve from this repository's declared repositories.
No absolute path or composite build may point at the neighboring Baritone
checkout.

The optional repair capability hashes the loaded Forge source artifacts before
resolving any TConstruct or TGregworks implementation type. Production repair
is unavailable unless exactly one artifact for each row above has the exact
version and complete SHA-256 pinned in
`TinkersRepairCompatibilityInspector`. The adapter itself uses Minecraft types
plus reflective `IModifyable.getBaseTagName` access so TConstruct implementation
classes remain isolated from core, task, and general runtime packages.

Milestone 0A was launch-verified on 2026-08-30 with a clean GTNH
`2.9.0-beta-2` Prism Launcher instance. The reobfuscated production JAR loaded
as mod ID `horizonwright`, reached the main menu, joined a disposable
singleplayer world, opened its dashboard through the `H` key binding, saved,
and exited cleanly. The test instance contained no Baritone JAR.
