# GTNH 2.9.0 Beta3 migration

The current physical-test target was updated on 2026-09-11 to
`GTNH-2.9.0-Beta3-Horizonwright`, Prism UUID
`44d6c004c8a54826a7b334625f610a16`. See `../AGENTS.md` for its exact
installation path and process-check requirements. The workspace shortcut is
`GTNH-2.9.0-Beta3-Horizonwright.lnk`.

The supplied reference pack is
`D:\Dev\Baritone-Backport\GT_New_Horizons_2.9.0-beta-3_Java_17-26\GT New Horizons 2.9.0-beta-3`.
The new Prism instance already contains the Beta3 pack. Its 240 enabled pack
JARs match the reference pack by SHA-256. The remaining reference JAR,
`defaultserverlist-1.7.4.jar`, is disabled in the instance; that choice is preserved.
Minecraft 1.7.10, Forge 10.13.4.1614, the build toolchain, and the vendored
Baritone artifact remain the same.

## Updated integration evidence

The following checks used the actual local Beta2 and Beta3 artifacts with
`javap -c -p`, normalizing constant-pool references and disassembly spacing.
They establish the adapter contracts below, not an in-game smoke-test result.

| Integration | Beta3 version | Evidence |
| --- | --- | --- |
| TConstruct | 1.14.108-GTNH | Tool Station/Forge containers, SlotTool/SlotToolForge, ToolBuilder, ToolForgeLogic and IModifyable have unchanged normalized disassembly. ToolStationLogic adds a shared empty slot array. Crafting Station retains output slot 0, center input slot 5 and player slots 10-45; its extracted ingredient-consumption method retains the previous repair behavior, now wrapped in batched crafting updates. |
| Pam HarvestCraft | 1.3.14-GTNH | BlockPamCrop, BlockPamFruit and BlockPamFruitingLog have unchanged normalized disassembly. BlockRegistry still exposes both public static boolean right-click flags. |
| CropsNH | 2.0.114 | hasCrop, hasWeed, isCrossCrop, isMature, getGrowthProgress, getSeed and harvest are unchanged. ISeedData.getCrop, ICropCard.getId/getGrowthRequirements and MachineOnlyGrowthRequirement remain present. |
| OpenBlocks | 1.12.21-GTNH | TileEntityGrave has unchanged normalized disassembly, including its client-visible owner and empty-state evidence. |

The exact runtime compatibility pins were updated together with their records:

| Artifact | SHA-256 |
| --- | --- |
| TConstruct-1.14.108-GTNH.jar | `ef13fb8b3fca725fe8a4d357480777c0e7363de2afce0aac1d32c8bb861e02a0` |
| harvestcraft-1.3.14-GTNH.jar | `6f605b9bbf03122bf8700a3332e3c56bfd5d0472e8d4e03c04841064e5ba9a01` |

Both versions were confirmed from their embedded `mcmod.info` metadata.
TGregworks, Mantle and Hunger Overhaul still match their existing exact hashes.
The Pam observation fingerprint now names the Beta3 adapter version.

## Physical validation

Existing Beta2 smoke-test records retain their original version and dates.
Beta3 launch, repair, crop farming, grave recovery and extended-inventory
interaction still require an in-game smoke test. The extended-inventory
version table records the earlier inspection; the newer Beta3 backpack and
AE2 versions have not been physically revalidated by this migration.
## Build and deployment result

`gradlew.bat build` passed on 2026-09-11: 804 tests across 161 suites,
zero failures, errors or skipped tests. Formatting, Checkstyle, pinned JVM,
vendored Baritone checks and production artifact isolation also passed.

Immediately before installation, the exact Beta3 name, UUID and resolved path
were verified, and no live process matched its instance path. The following
separate runtime JARs were installed into its `.minecraft/mods` directory and
verified against their source files by SHA-256:

| Installed artifact | SHA-256 |
| --- | --- |
| horizonwright-0.1.0-SNAPSHOT.jar | `762ee6131635904be805aae3a5fe428cfbc03c6b79212669b339ddee73e06442` |
| baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c.jar | `c6c25e1afe9a0406dc6b905c2d7c382831abdde05163569021cf5f59f14019b7` |

The disabled defaultserverlist artifact also matches the reference pack by hash.
No Beta2 worlds, settings or runtime JARs were changed.
