# ADR 0002: Baritone backend packaging

- Status: Proposed
- Date: 2026-08-30

## Context

The local 1.7.10 backport demonstrates a viable navigation engine but is a
dirty development checkout. Baritone is LGPL-licensed and its launch hooks,
reflection, mixins, and input ownership make relocation riskier than a normal
library shade.

## Starting hypothesis

Evaluate a clean, exact, internally resolved source snapshot first. Keep all
Baritone types behind Horizonwright's private `NavigationBackend`, preserve
the required license and notices, and fail clearly if a conflicting Baritone
installation is present.

## Required evidence before acceptance

1. Freeze and hash a clean backend source snapshot independently of the local
   reference checkout.
2. Complete the LGPL compatibility and distribution notice review.
3. Compare bundled, separate-JAR, and relocated launch behavior in the pinned
   disposable GTNH instance.
4. Prove submit, progress, completion, cancellation, epoch rejection, input
   release, and duplicate-class diagnostics.

No packaging option is accepted yet, and the current Gradle build resolves no
Baritone dependency.
