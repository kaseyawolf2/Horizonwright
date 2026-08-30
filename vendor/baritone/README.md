# Pinned Baritone development artifacts

This directory is Horizonwright's authoritative snapshot of its selected
Baritone navigation runtime. The binary is available to Gradle through
`devOnlyNonPublishable` and is installed as a separate mod at runtime. It is
not embedded in a Horizonwright JAR or declared in published metadata.

## Source identity

- Upstream project: <https://github.com/cabaletta/baritone>
- Official Baritone `v1.2.19` base commit:
  `d9cb2d91a06501c5bcba2181509d0df80361f413`
- Minecraft 1.7.10 fork: <https://github.com/kaseyawolf2/baritone>
- Clean snapshot commit: `fcbbd4882cc7d846a8e613dea4b50203e1fb4ebc`
- Local lightweight tag: `v1.2.19-mc1.7.10`
- Relationship to the official base: 47 commits later

At the time of capture, the clean snapshot commit and local tag were not
published by the fork remote. Do not construct a commit URL that implies they
are remotely retrievable. The full source snapshot and checksums in this
directory are the durable in-repository record.

## Files

| File | Purpose | SHA-256 |
| --- | --- | --- |
| `baritone-v1.2.19-mc1.7.10.jar` | Exact binary used on Horizonwright's development classpath and installed separately at runtime | `f644ac987bae86863122853af1e47ae1298c485b4bac1f3c4fab98ce3aad3c1d` |
| `baritone-v1.2.19-mc1.7.10-sources.jar` | IDE/source attachment assembled from the clean snapshot | `806d0e1f1b52ec0a33d2409861ee1503ece493058c0a2c627d237fccfdc89021` |
| `baritone-v1.2.19-mc1.7.10-source-snapshot.zip` | Complete corresponding source/build snapshot | `31f6f0efa564c7b8cd2e79ca76adf216601f7218ff5776df15bfcaf6db1d2659` |
| `COPYING-GPL-3.0` | Complete GPLv3 text incorporated by LGPLv3 | `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986` |
| `LICENSE-LGPL-3.0-or-later` | LGPL v3 license text preserved from the snapshot | `f831e7eed577481687a9bc0b48024e5e40b6f655fcde073ede964b50be5d55d9` |
| `LICENSE-Part-2.jpg` | Second upstream license file preserved from the snapshot | `e3ba782078d7a75fa36f57d2fb1df31d03d361f0bc2daef60612dd6098775400` |
| `LICENSE-fastutil-Apache-2.0` | Complete Apache 2.0 text from fastutil 8.5.13's authoritative repository | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |

The vendored sources JAR has the same entry names and semantic source content
as the clean Gradle sources artifact. It is not byte-identical: it was
normalized/repacked from the source snapshot, retaining snapshot CRLF endings
in 51 text entries. Its authoritative identity is therefore the `806d0e...`
hash above, not the hash of a separately generated Gradle sources JAR.

The Baritone binary contains fastutil 8.5.13 classes relocated beneath
`baritone.shadow.it.unimi.dsi.fastutil`. See Horizonwright's third-party
notices for attribution. Its complete license text was captured from
<https://github.com/vigna/fastutil/blob/8.5.13/LICENSE-2.0>. Horizonwright does
not copy those classes into its own production JAR.

## Verification and update policy

Run `./gradlew verifyBaritoneArtifacts` (or `gradlew.bat` on Windows). Both
`assemble` and `check` depend on this verification. The task hashes every
listed binary, source, and license artifact and also requires `SHA256SUMS` to
match the hashes pinned in `build.gradle.kts`.

Do not replace any file independently. A candidate update must change the
source identity, complete source snapshot, complete GPLv3 text, both upstream
Baritone license files, the fastutil license, checksums, build pin, notices,
reuse register, and packaging ADR together.
Never source the dependency from the neighboring mutable Baritone checkout.

Horizonwright source can be rebuilt against an interface-compatible modified
Baritone. A byte-different binary is intentionally not treated as the
validated reference: its commit, source, licenses, checksums, and compatibility
record must be deliberately reviewed and updated. Until then, the runtime
backend remains unavailable and navigation or unattended operation fails
closed rather than silently accepting unvalidated bytes.
