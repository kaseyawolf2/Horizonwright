# Operational-base MVP acceptance evidence

This ledger tracks the authoritative evidence for the Milestone 4 exit criteria
in `HORIZONWRIGHT_IMPLEMENTATION_PLAN.md`. A green automated build is necessary
but cannot replace the disposable-world observation run required by the plan.

## Acceptance scenario components

| Requirement | Current evidence | Status |
| --- | --- | --- |
| Radius-250 excavation | Geometry, bounded scan, checkpoint, cache refresh, missed-layer recovery, navigation, breaking, unloading, repair, and reconnect tests; substantial physical excavation testing | Partially proven; complete observation run pending |
| Recurring crop chore preempts excavation | Real scheduler/task-spec/checkpoint synthetic scenario plus physical one-pass and scheduled farming tests | Automated orchestration proven; latest long-distance deadline fix awaits physical confirmation |
| Recurring livestock chore preempts excavation | Real husbandry task contract and real scheduler/task-spec/checkpoint synthetic scenario | Orchestration proven; live action executor not yet authorized or implemented |
| Night sleep preempts fallback | Real sleep task/schedule and scheduler synthetic scenario; nearby, far, and nightly physical sleep were confirmed | Proven for current tested bed workflow |
| Unloading occurs as needed | Transactional chest backend and service-coordinator tests; physical excavation unload behavior exercised | Subsystem proven; full combined observation run pending |
| Tinkers repair occurs as needed | Transactional Tinkers Crafting Station repair tests and physical repeated-material/full-cycle confirmation | Subsystem proven; full combined observation run pending |
| Excavation resumes after each interruption | Real scheduler synthetic scenario and physical excavation/farm/sleep interruption testing | Partially proven; combined observation run pending |
| Excavation resumes after reconnect | Export/restore tests preserve exact checkpoint and reject stale epochs; physical excavation rejoin was confirmed | Proven for current tested workflow |
| Reserved items are not lost | Container transition tokens, cursor/slot postconditions, staged repair-material return, and physical repair testing | Partially proven; full observation run pending |
| Population bounds are never violated | Pure husbandry planner preserves at least one breeding pair and excludes babies, named/tamed/protected entities | Policy proven; live execution and physical evidence missing |
| Excavation checkpoint is not lost | Persistence and reconnect tests plus physical rejoin testing | Proven for current tested workflow |

## Milestone 4 functionality still missing or incomplete

- Live husbandry execution for feeding, collecting drops, and bounded culling.
- Explicit operator authorization for automatic killing of eligible excess adult
  livestock before the live executor is bound.
- Managed-quarry ramp construction.
- Managed-quarry lighting placement and reserve management.
- Fluid containment/removal integration; clean-volume excavation currently
  refuses blind fluid work.
- Ordinary tree farming/replanting as a recurring base chore. Excavation tree
  recovery exists but is not a tree-farm task.
- Physical confirmation of the separated long-distance farm travel/action
  deadlines in commit `0853c61`.
- Installation and physical validation of the read-only livestock pen scan.
- The final configured-duration disposable-world observation run combining
  radius-250 excavation, recurring crops and livestock, nightly sleep, unloading,
  repair, interruption, and reconnect.

## Automated orchestration scenario

`OperationalBaseMvpOrchestrationTest` uses the production `TaskOrchestrator`,
production schedule rules, production task specifications, action-epoch
transitions, controller export/restore, and a real radius-250 excavation
specification. It verifies the following sequence:

1. Fallback excavation advances and records a checkpoint.
2. A recurring farm occurrence safely suspends the excavation without changing
   its frontier evidence.
3. The farm completes and excavation resumes from the preserved checkpoint.
4. Night sleep, the next farm occurrence, and livestock work become due while
   excavation is active.
5. The three chores execute in configured relative order while the fallback
   remains suspended.
6. Excavation resumes and advances from the exact prior checkpoint.
7. Controller and scheduler state are exported, restored into a fresh runtime,
   and assigned a fresh action epoch.
8. No already-consumed schedule occurrence is duplicated after reconnect.
9. Excavation receives the exact persisted checkpoint and continues advancing.

The scenario uses synthetic bounded runners so it proves orchestration and
persistence only. Physical backends must still satisfy their separate evidence
rows and the combined disposable-world exit criterion.
