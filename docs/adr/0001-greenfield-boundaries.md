# ADR 0001: Greenfield ownership boundaries

- Status: Accepted
- Date: 2026-08-30

## Decision

Horizonwright is an independent Forge mod and a single Gradle project during
the early milestones. Package boundaries act as logical modules:

- `core` is pure Java and imports no Minecraft, Forge, Baritone, or optional
  mod types.
- `forge` owns lifecycle and client UI integration.
- a future `navigation.baritone` package will be the only package allowed to
  import Baritone types.
- exact-version optional-mod implementation types remain in `integrations`.

Gameplay authority begins at `ActionBroker`. Navigation requests carry the
same epoch as their movement lease so stale backend work can be rejected.

## Consequences

The first build uses a deterministic fake navigation backend in tests. The
real navigation spike cannot begin until a clean source snapshot, licensing
obligations, and packaging/collision behavior are recorded. This keeps the
dirty neighboring checkout out of both the build graph and product identity.
