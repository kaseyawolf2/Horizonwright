# Third-party notices

Horizonwright's original source is licensed under the MIT License. The
following third-party material is present in the repository or informs the
build.

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
- Minecraft 1.7.10 fork: https://github.com/kaseyawolf2/baritone
- Official `v1.2.19` base commit:
  `d9cb2d91a06501c5bcba2181509d0df80361f413`
- Build commit identity: `fcbbd4882cc7d846a8e613dea4b50203e1fb4ebc`
- License: LGPL-3.0-or-later
- Use: exact, hash-verified `devOnlyNonPublishable` compile/local-development
  input and separately installed runtime for the private navigation adapter.
- Binary SHA-256:
  `f253f077181bcf0f008dcbe020c40e93a9bed0f5581d463c81fbd94bb4235ac6`
- Corresponding sources JAR SHA-256:
  `c1ff3406763fb2b640f72c282933b73121c22c53dc9e5e4a3079f879d50da86f`
- Complete GPLv3 text SHA-256:
  `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986`
- Corresponding source and license record: `vendor/baritone/`

The exact enhanced binary and its corresponding Gradle sources artifact are
vendored by hash. Horizonwright does not embed, publish, or redistribute
Baritone. The complete GPLv3 text incorporated by LGPLv3 is preserved as
`vendor/baritone/COPYING-GPL-3.0`; both upstream license files are also
preserved.

Horizonwright can be rebuilt against an interface-compatible modified
Baritone. Byte-different Baritone artifacts are unvalidated and must leave
navigation and unattended operation fail-closed until the compatibility
record is deliberately reviewed and updated.

## fastutil 8.5.13

- Project: https://github.com/vigna/fastutil
- Versioned license source:
  https://github.com/vigna/fastutil/blob/8.5.13/LICENSE-2.0
- License: Apache License 2.0
- Use: the separately installed Baritone binary contains fastutil classes
  relocated under `baritone.shadow.it.unimi.dsi.fastutil`.
- Complete license text: `vendor/baritone/LICENSE-fastutil-Apache-2.0`
- License-text SHA-256:
  `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`

Horizonwright does not embed fastutil in its production JAR.
