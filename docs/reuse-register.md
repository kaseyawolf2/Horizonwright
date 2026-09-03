# Reuse register

This register prevents accidental migration of the old process architecture.
Reference checkouts may provide evidence, but Horizonwright's controller,
safety model, task architecture, persistence, and build remain independently
owned.

## Decision labels

- `REIMPLEMENT`: characterize behavior and write an independent implementation.
- `PORT_WITH_ATTRIBUTION`: reuse only from an exact clean source snapshot with
  its license, corresponding source, notices, and provenance.
- `SEPARATE_RUNTIME`: compile against the recorded artifact but require it as a
  separately installed mod; do not embed it in Horizonwright.
- `LEAVE_BEHIND`: intentionally exclude the old implementation.

## Reference checkout observed on 2026-08-30

- Path: `D:\Dev\Baritone-Backport\baritone-1.7.10`
- Clean snapshot commit: `fcbbd4882cc7d846a8e613dea4b50203e1fb4ebc`
- Tag: `v1.2.19-mc1.7.10`
- Branch: `1.7.10-forge`
- Observed working-tree state: dirty, with unrelated modified and untracked
  feature work present

The path and its mutable artifacts are not build inputs. A detached clean
checkout was rebuilt to establish the recorded binary, and the complete source
snapshot in `vendor/baritone/` is now the authoritative record.

## Baritone snapshot

| Field | Recorded value |
| --- | --- |
| Official upstream | `https://github.com/cabaletta/baritone` |
| Official `v1.2.19` base | `d9cb2d91a06501c5bcba2181509d0df80361f413` |
| Minecraft 1.7.10 fork | `https://github.com/kaseyawolf2/baritone` |
| Clean snapshot | `fcbbd4882cc7d846a8e613dea4b50203e1fb4ebc` |
| Embedded build version | `v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c-dirty` |
| Binary SHA-256 | `9eeadebbabb253aae53af90d46e280c23b217f4df29d5b693eec814d7379ede1` |
| Sources JAR SHA-256 | `e07fe0fbeaa81286035578c65f0cb5ccdb565283f1643db2230e92f6fe968455` |
| License | LGPL-3.0-or-later; complete LGPL/GPL material and fastutil's Apache-2.0 license are in `vendor/baritone/` |

The snapshot commit and tag were local-only when captured. Do not invent a
remote commit URL for them.

## Reuse decisions

| Candidate | Decision | Boundary |
| --- | --- | --- |
| Old mod lifecycle and `Minecraft.startGame` bootstrap | `LEAVE_BEHIND` | Horizonwright has its own Forge lifecycle. |
| Baritone API and navigation engine | `SEPARATE_RUNTIME`, `PORT_WITH_ATTRIBUTION` | Exact binary is a hash-verified `devOnlyNonPublishable` compile input and separately installed runtime; no Baritone class ships inside Horizonwright. |
| Baritone launch hooks and mixins | `SEPARATE_RUNTIME` | They remain owned by the separate Baritone mod and never bootstrap Horizonwright. |
| Baritone commands, process ownership, and runtime state | `LEAVE_BEHIND` | Horizonwright's controller, scheduler, action broker, and safety state own work; only a private adapter process is registered. |
| User-selected enhanced Baritone feature patches | `SEPARATE_RUNTIME` | Exact reviewed binary/source artifacts are vendored by hash; the mutable neighboring checkout is never a dynamic build input. |
| Circle/cylinder geometry | `REIMPLEMENT` | Pure geometry with golden fixtures; no copied implementation. |
| Farming and CropsNH behavior | `REIMPLEMENT` | Use exact-version public APIs and independently recorded ordinary-crop fixtures. |
| GT prospecting grid calculations | `REIMPLEMENT` | Use coordinate fixtures and an independent implementation. |
| Storage/container transactions | `REIMPLEMENT` | New epoch-bound transactional service. |
| Tinkers classification and repair behavior | `REIMPLEMENT` | Exact TConstruct `1.14.93-GTNH`, TGregworks `1.0.33`, and Mantle `0.5.4` source hashes gate a reflection-isolated adapter; no implementation types enter core or task packages. |
| Piston Boots movement behavior | `REIMPLEMENT` | Capability snapshots replace mutable global settings. |

## Compatibility rule

Horizonwright may be rebuilt and relinked with an interface-compatible modified
Baritone. Different bytes remain unvalidated in production, so the backend,
navigation, and unattended operation fail closed until a maintainer deliberately
updates the commit, corresponding source, license and checksum records and
reruns collision, adapter, input-release, packet-firewall, and GTNH smoke tests.
