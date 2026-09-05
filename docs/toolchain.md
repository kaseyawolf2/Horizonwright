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
| Pam HarvestCraft | `1.3.11-GTNH` / `DA05759C...8B911D` | Exact non-destructive crop/fruit metadata and right-click adapter |
| Hunger Overhaul | `1.0.0.jenkins104` / `800E55C3...92641F9` | Exact non-destructive `BlockCrops` right-click behavior |

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

The optional Pam adapter recognizes only the three class names and metadata
contracts recorded in `reuse-register.md`. It resolves the two public
right-click configuration flags through the block's own class loader and has no
compile-time HarvestCraft dependency. Its Forge-side probe requires exactly one
loaded `harvestcraft` container with version `1.3.11-GTNH` and the complete
recorded SHA-256 before production Pam actions are exposed. A missing,
duplicate, version-different, byte-different, or unreadable artifact disables
only Pam automation; vanilla and CropsNH farming remain available. The artifact
remains a separately installed mod and is not copied into Horizonwright.

The ordinary-crop adapter similarly treats Hunger Overhaul right-click behavior
as a pinned integration rather than a vanilla assumption. Its Forge-side probe
requires exactly one loaded `HungerOverhaul` container with version
`1.0.0.jenkins104` and SHA-256
`800E55C375575941E7EB6CE1F5C13BE518453016171AF1BA77A7B607C92641F9`, then
reads the exact public `enableRightClickHarvesting` flag. Only `BlockCrops`
instances receive that non-destructive action. Missing or changed bytes, a
disabled flag, nether wart, and cocoa use Horizonwright's separately verified
break-and-replant path instead of guessing that a right click will harvest.

Milestone 0A was launch-verified on 2026-08-30 with a clean GTNH
`2.9.0-beta-2` Prism Launcher instance. The reobfuscated production JAR loaded
as mod ID `horizonwright`, reached the main menu, joined a disposable
singleplayer world, opened its dashboard through the `H` key binding, saved,
and exited cleanly. The test instance contained no Baritone JAR.
