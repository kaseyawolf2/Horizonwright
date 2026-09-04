# Horizonwright Agent Instructions

## PrismLauncher test instance

The canonical Horizonwright physical-test instance is:

- Prism display name: `GTNH-2.9.0-Beta2-Horizonwright`
- Prism instance UUID: `199d415b36ca44079210867153c00f3a`
- Instance directory: `D:\Games\Standalone\Minecraft\PrismLauncher-Windows-MinGW-w64-Portable-11.0.2\instances\GTNH-2.9.0-Beta2-Horizonwright`
- Game directory: `D:\Games\Standalone\Minecraft\PrismLauncher-Windows-MinGW-w64-Portable-11.0.2\instances\GTNH-2.9.0-Beta2-Horizonwright\.minecraft`
- Mods directory: `D:\Games\Standalone\Minecraft\PrismLauncher-Windows-MinGW-w64-Portable-11.0.2\instances\GTNH-2.9.0-Beta2-Horizonwright\.minecraft\mods`

Before installing or replacing Horizonwright or Baritone jars, verify the target using the exact Prism display name, instance UUID, and directory above. Do not deploy to another similarly named GTNH instance or another modpack instance.

The user often leaves a second, unrelated Minecraft instance running in the background. A generic `java`, `javaw`, Minecraft, LWJGL, or PrismLauncher process therefore does not mean the Horizonwright test instance is running. Conversely, closing one Minecraft window does not prove that the Horizonwright instance is closed.

Before replacing a jar:

1. Check running process command lines for the exact Horizonwright instance/game-directory path. Report only matching process IDs and names; do not print complete Minecraft command lines because they can contain account credentials or access tokens.
2. Treat the Horizonwright instance as running only when its exact path is associated with a live process or Prism reports that exact instance as running.
3. If only a different Minecraft instance is running, leave it alone and proceed with the Horizonwright deployment.
4. If the exact Horizonwright instance is running, do not replace its jars. Wait for the user to close that instance, then verify the exact path again.
5. Install only into the canonical mods directory above, and verify the installed Horizonwright and Baritone filenames and hashes afterward.

