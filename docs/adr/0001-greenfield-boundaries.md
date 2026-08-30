# ADR 0001: Greenfield ownership boundaries

- Status: Accepted
- Date: 2026-08-30

## Decision

Horizonwright is an independent Forge mod and a single Gradle project during
the early milestones. Package boundaries act as logical modules:

- `core` is pure Java and imports no Minecraft, Forge, Baritone, or optional
  mod types.
- `forge` owns lifecycle and client UI integration.
- `navigation.baritone` is the only package allowed to
  import Baritone types.
- exact-version optional-mod implementation types remain in `integrations`.

Gameplay authority begins at `ActionBroker`. Navigation requests carry the
same epoch as their movement lease so stale backend work can be rejected.

## Consequences

Deterministic fakes remain the primary contract-test boundary. The real
Baritone adapter was added only after its clean snapshot, licenses, separate-JAR
packaging model, and collision diagnostics were recorded. The dirty neighboring
checkout remains outside both the build graph and product identity.
