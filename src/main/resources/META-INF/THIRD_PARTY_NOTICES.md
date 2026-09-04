# Third-party notices

Horizonwright's original source is licensed under the MIT License.

## GT New Horizons ExampleMod 1.7.10

- Source: https://github.com/GTNewHorizons/ExampleMod1.7.10
- Reference commit: `33f0cf760939442696eb0350790f815688b686f6`
- License: MIT
- Use: structure and pinned RetroFuturaGradle convention settings were used as
  a build-system reference. The wrapper scripts began from that reference;
  the wrapper JAR is the checksum-verified official Gradle 9.3.1 bootstrap.

## Gradle wrapper

- Project: https://gradle.org/
- Version: 9.3.1
- License: Apache License 2.0
- Use: repository build bootstrap.
- Binary distribution SHA-256:
  `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`
- Wrapper JAR SHA-256:
  `b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13`

## Baritone enhanced Minecraft 1.7.10 build

- Official upstream: https://github.com/cabaletta/baritone
- Build commit identity:
  `fcbbd4882cc7d846a8e613dea4b50203e1fb4ebc`
- License: LGPL-3.0-or-later
- Validated binary SHA-256:
  `c6c25e1afe9a0406dc6b905c2d7c382831abdde05163569021cf5f59f14019b7`
- Complete GPLv3 text SHA-256:
  `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986`
- Complete corresponding source, LGPL/GPL license material, and checksums:
  `vendor/baritone/` in the Horizonwright source distribution.

Baritone is a hash-verified compile input and separately installed runtime. No
`baritone.*` classes are embedded in or distributed by this Horizonwright
artifact. Horizonwright can be rebuilt against an interface-compatible
modified Baritone; byte-different artifacts remain unvalidated and
navigation/unattended operation must fail closed until the compatibility
record is deliberately updated.

## fastutil 8.5.13

- Project: https://github.com/vigna/fastutil
- Versioned license source:
  https://github.com/vigna/fastutil/blob/8.5.13/LICENSE-2.0
- License: Apache License 2.0
- Use: relocated inside the separately installed Baritone binary under
  `baritone.shadow.it.unimi.dsi.fastutil`.
- Complete license text and checksum:
  `vendor/baritone/LICENSE-fastutil-Apache-2.0` in the Horizonwright source
  distribution, SHA-256
  `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.

No fastutil classes are embedded in this Horizonwright artifact.
