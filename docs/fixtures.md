# Deterministic fixtures and clean-room characterization

These fixtures support the Milestone 0 feasibility work without making the
neighboring Baritone Backport checkout a source, binary, or build dependency.
Every model in this document was written from Horizonwright's implementation
plan and independently stated observable behavior. No Baritone or legacy
implementation source was copied, translated, or consulted while writing the
fixture code or expected-data tables.

All fixture implementations live under `src/test`; none are packaged in the
shipping Horizonwright JAR. The tab-separated golden tables live under
`src/test/resources/fixtures/characterization` so changes are reviewable as
data instead of being hidden in test logic.

## Deterministic infrastructure

- `FakeClock` tracks wall ticks and connected ticks independently. This is the
  clock model required for connected-time schedules and reconnect tests.
- `FakeWorldSnapshot` defensively copies immutable block positions and states,
  and carries a dimension plus positive world revision.
- `FakeInventory` uses immutable item identities and snapshots while allowing
  a test to apply or restore explicit slot moves.
- `FakePacketBoundary` queues outbound packets and evaluates its gate only at
  flush time. It can therefore reproduce the important case where a packet is
  queued before a safety latch and reaches the write boundary afterward.
- `FakeActionExecutor` queues capability-scoped actions and evaluates the
  associated `ActionLease` at execution time, recording stale-lease
  cancellation deterministically.
- `FakeStorageTransaction` permits one prepared move and commits only when the
  window, action number, action epoch, and complete server inventory snapshot
  match the expected post-click state. A timeout or mismatch is terminal and
  requires rebuilding from a new snapshot.

## Golden characterization tables

### Circle and cylinder geometry

`circle-cylinder-radius-2.tsv` declares the integer columns satisfying
`dx^2 + dz^2 <= radius^2`. Cylinder Y bounds are inclusive, and work order is
deterministic: X ascending, then Z ascending, then Y from top to bottom. This
is a clean-room Horizonwright geometry contract, not copied legacy geometry.

### Ordinary crop harvesting

`ordinary-crop-harvest.tsv` records 1.7.10 metadata maturity boundaries for
wheat, carrots, potatoes, nether wart, and cocoa. Mature ordinary crops use a
`BREAK_AND_REPLANT` outcome; immature crops wait. A generic `IGrowable`-style
signal is treated as mature only when it explicitly reports that it cannot
grow further. Missing adapter evidence fails closed with `HOLD_FOR_ADAPTER`.
Pam and CropsNH behavior are deliberately not inferred by this ordinary-crop
fixture and will require exact-version, black-box observations of their own.

### GT prospect cell centers

`gt-prospect-3n-plus-1.tsv` declares center chunk coordinates as `3N + 1` on
both axes, including negative cells. The corresponding block coordinate is
the center of that chunk, `chunk * 16 + 8`. This table characterizes grid
arithmetic only; natural-vein identification and VisualProspecting interaction
remain integration-adapter responsibilities.

### Storage confirmation

`storage-confirmation.tsv` covers exact confirmation and rejection for a
changed window, action number, epoch, or slot state. The fixture does not
assume optimistic clicks are safe and never retries a terminal transaction.

### Piston Boots capabilities

`piston-boots-capabilities.tsv` states the minimum capability policy from the
implementation plan: equipped, non-broken boots expose auto-step and safe-fall;
a high-jump edge additionally needs two blocks of head clearance and a safe
landing; sprinting adds one abstract cost unit. Equipment revision changes,
removal, or breakage invalidate the captured capability snapshot.

The abstract sprint-cost unit intentionally avoids claiming an unverified
GTNH numeric movement cost. Before a real Adventure Backpack adapter is
accepted, these Piston Boots cases must be replayed as black-box observations
against the pinned disposable GTNH instance. Any observed difference changes
the reviewed table and provenance note, never the expected data silently.

## Provenance and update policy

- Authored: 2026-08-30.
- Primary source: `HORIZONWRIGHT_IMPLEMENTATION_PLAN.md`, especially the
  Milestone 0 fixture list and feature-level behavioral requirements.
- Legacy source reuse classification: `LEAVE_BEHIND` for implementations;
  clean-room expected behavior only.
- Third-party source or binary content: none.

A golden row may change only with a reviewable reason and evidence. For
mod-specific behavior, record the exact GTNH/mod versions, test world, input,
and observed output here or in a linked capture manifest. Never regenerate a
golden table from the implementation under test.
