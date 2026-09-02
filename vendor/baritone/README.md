# Pinned Baritone development artifacts

This directory is Horizonwright's authoritative pin of its selected enhanced
Baritone navigation runtime. The binary is available to Gradle through
`devOnlyNonPublishable` and is installed as a separate mod at runtime. It is
not embedded in a Horizonwright JAR or declared in published metadata.

## Source identity

- Upstream project: <https://github.com/cabaletta/baritone>
- Official Baritone `v1.2.19` base commit:
  `d9cb2d91a06501c5bcba2181509d0df80361f413`
- Minecraft 1.7.10 fork: <https://github.com/kaseyawolf2/baritone>
- Build commit identity: `fcbbd4882cc7d846a8e613dea4b50203e1fb4ebc`
- Selected runtime filename: `baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c.jar`
- Embedded Gradle build version: `v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c-dirty`
- Forge `ModContainer` version exposed after leading-`v` normalization:
  `1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c-dirty`

The user selected this exact clean-named runtime artifact from the neighboring
Baritone build. Its compiled payload includes the newer local fixes and feature
classes, while its embedded Gradle metadata retains the source tree's
`-dirty` suffix. The byte hash, not the filename or base commit alone, is the
authoritative compatibility identity.

## Files

| File | Purpose | SHA-256 |
| --- | --- | --- |
| `baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c.jar` | Exact enhanced binary used on Horizonwright's development classpath and installed separately at runtime; rebuilt with stable sprint ownership at turns | `03d2295de0c5e6bfd39fdcc88a279b8f6461c736c60bd2a59327efc278f38cec` |
| `baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c-dirty-sources.jar` | Corresponding Gradle sources artifact produced immediately before the selected runtime | `5f573cc35f19360e3ec8d2ce7bd732b87a66d6d3ace6781b9d9dfdd400a7b4a8` |
| `COPYING-GPL-3.0` | Complete GPLv3 text incorporated by LGPLv3 | `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986` |
| `LICENSE-LGPL-3.0-or-later` | LGPL v3 license text preserved from the snapshot (canonical LF text hash) | `a5681bf9b05db14d86776930017c647ad9e6e56ff6bbcfdf21e5848288dfaf1b` |
| `LICENSE-Part-2.jpg` | Second upstream license file preserved from the snapshot | `e3ba782078d7a75fa36f57d2fb1df31d03d361f0bc2daef60612dd6098775400` |
| `LICENSE-fastutil-Apache-2.0` | Complete Apache 2.0 text from fastutil 8.5.13's authoritative repository | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |

The sources artifact is retained byte-for-byte from the same enhanced build.

The Baritone binary contains fastutil 8.5.13 classes relocated beneath
`baritone.shadow.it.unimi.dsi.fastutil`. See Horizonwright's third-party
notices for attribution. Its complete license text was captured from
<https://github.com/vigna/fastutil/blob/8.5.13/LICENSE-2.0>. Horizonwright does
not copy those classes into its own production JAR.

## Verification and update policy

Run `./gradlew verifyBaritoneArtifacts` (or `gradlew.bat` on Windows). Both
`assemble` and `check` depend on this verification. The task hashes every
listed binary and source artifact byte-for-byte, hashes text license artifacts
after canonicalizing line endings to LF, and also requires `SHA256SUMS` to
match the hashes pinned in `build.gradle.kts`.

Do not replace any file independently. A candidate update must change the
source identity, corresponding sources artifact, licenses, checksums, build
pin, notices, reuse register, and packaging ADR together. Horizonwright builds
only from the vendored bytes and never resolves the neighboring mutable
Baritone checkout dynamically.

Horizonwright source can be rebuilt against an interface-compatible modified
Baritone. A byte-different binary is intentionally not treated as the
validated reference: its source, licenses, checksums, and compatibility record
must be deliberately reviewed and updated. Until then, the runtime backend
remains unavailable rather than silently accepting unvalidated bytes.
