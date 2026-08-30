# Reuse register

This register prevents accidental migration of the old process architecture.
`REIMPLEMENT` means behavior may be characterized and independently written;
`PORT_WITH_ATTRIBUTION` requires a clean, exact source snapshot plus a license
review; `LEAVE_BEHIND` means the old implementation is intentionally excluded.

## Reference checkout observed on 2026-08-30

- Path: `D:\Dev\Baritone-Backport\baritone-1.7.10`
- Clean HEAD: `fcbbd4882cc7d846a8e613dea4b50203e1fb4ebc`
- Tag: `v1.2.19-mc1.7.10`
- Branch: `1.7.10-forge`
- State: dirty (modified and untracked feature work was present)

The path and its current artifacts are not build inputs. The recorded commit
identifies a point for investigation; it is not yet the selected backend
snapshot.

| Candidate | Decision | Notes |
| --- | --- | --- |
| Old mod lifecycle and `Minecraft.startGame` bootstrap | `LEAVE_BEHIND` | Horizonwright has its own Forge lifecycle. |
| Baritone commands, process ownership, and runtime state | `LEAVE_BEHIND` | Horizonwright's controller and scheduler own work. |
| Baritone pathfinding/navigation engine | Pending packaging ADR | No source or binary included yet. |
| Circle/cylinder geometry | `REIMPLEMENT` | Capture golden fixtures before implementation. |
| Farming and CropsNH behavior | `REIMPLEMENT` | Use exact-version public APIs where possible. |
| GT prospecting grid calculations | `REIMPLEMENT` | Capture coordinate fixtures first. |
| Storage/container transactions | `REIMPLEMENT` | New epoch-bound transactional service required. |
| Tinkers tool classification and repair behavior | `REIMPLEMENT` | Exact-version adapter, no leaked implementation types. |
| Piston Boots movement behavior | `REIMPLEMENT` | Navigation capability snapshots replace global settings. |
