# Operational-base MVP acceptance evidence

This ledger tracks the authoritative evidence for the Milestone 4 exit criteria
in `HORIZONWRIGHT_IMPLEMENTATION_PLAN.md`. A green automated build is necessary
but cannot replace the disposable-world observation run required by the plan.

## Acceptance scenario components

| Requirement | Current evidence | Status |
| --- | --- | --- |
| Radius-250 excavation | Geometry, bounded scan, checkpoint, cache refresh, missed-layer recovery, navigation, breaking, unloading, repair, and reconnect tests; the orchestration scenario now carries a persisted managed-quarry material policy; substantial physical clean-volume testing | Partially proven; live managed infrastructure and complete observation run pending |
| Recurring crop chore preempts excavation | Real scheduler/task-spec/checkpoint synthetic scenario plus physical one-pass and scheduled farming tests; CropsNH walking inside the protected crop radius was physically observed for both long and near starts | Automated orchestration and crop-zone sprint suppression proven; installed travel-deadline and held-slot authorization fixes await physical confirmation |
| Recurring tree chore preempts excavation | Real tree task spec participates in the radius-250 scheduler/preemption/reconnect scenario; live bounded adapter and restart-safe fell/replant tests | Automated orchestration proven; physical tree pass pending |
| Recurring livestock chore preempts excavation | Real husbandry task contract and scheduler scenario; live vanilla feeding/drop collection adapter with explicit culling denial | Non-destructive execution automated-build proven; physical test and authorized culling pending |
| Night sleep preempts fallback | Real sleep task/schedule and scheduler synthetic scenario; nearby, far, and nightly physical sleep were confirmed | Proven for current tested bed workflow |
| Unloading occurs as needed | Transactional chest backend and service-coordinator tests; physical excavation unload behavior exercised | Subsystem proven; full combined observation run pending |
| Tinkers repair occurs as needed | Transactional Tinkers Crafting Station repair tests and physical repeated-material/full-cycle confirmation | Subsystem proven; full combined observation run pending |
| Excavation resumes after each interruption | Real scheduler synthetic scenario and physical excavation/farm/sleep interruption testing | Partially proven; combined observation run pending |
| Excavation resumes after reconnect | Export/restore tests preserve exact checkpoint and reject stale epochs; physical excavation rejoin was confirmed | Proven for current tested workflow |
| Reserved items are not lost | Container transition tokens, cursor/slot postconditions, staged repair-material return, and physical repair testing | Partially proven; full observation run pending |
| Population bounds are never violated | Pure planner preserves breeding pair; excludes babies/named/tamed/operator-protected and currently breeding-engaged adults; exact protected UUIDs persist in the identity-bound world profile and invalidate stale observation fingerprints; live boundary refuses all culls pending authorization | Non-destructive actions cannot reduce population; protected-stock workflow and authorized live culling still need physical evidence |
| Excavation checkpoint is not lost | Persistence and reconnect tests plus physical rejoin testing | Proven for current tested workflow |

## Milestone 4 functionality still missing or incomplete

- Physical validation of live vanilla feeding and exact-drop collection.
- Live bounded culling remains unimplemented.
- Explicit operator authorization for automatic killing of eligible excess adult
  livestock before the culling executor is bound.
- Managed-quarry ramp construction and full-player-inventory material staging
  are connected; physical validation is pending.
- Managed-quarry lighting placement and full-player-inventory material staging
  are connected; physical validation is pending.
- The managed-quarry task now persists approved ramp/light/filler materials and
  uses a retained descending-staircase geometry inside the quarry. The resumable runner observes,
  sequences, and requires server-confirmed evidence for each infrastructure
  action. The Minecraft backend performs a Baritone approach, normal right-click
  placement, transactional full-inventory material staging/return, packet-drain
  cleanup, and post-server material confirmation.
- Managed-quarry source and flowing-fluid containment is connected through the
  configured filler, full-inventory staging, and server-confirmed placement;
  physical validation is pending. Clean-volume excavation remains conservative.
- The ordinary-tree finite/scheduled CHORE contract and conservative vanilla
  adapter are implemented with bounded loaded-area captures, sapling-reserve
  enforcement, separately durable fell/replant frontiers, Baritone-owned
  approaches, bottom-up exact-log digging, exact-sapling inventory staging, and
  exact postcondition confirmation. Commands, work-area GUI entry points,
  schedule editing, and deletion cleanup are connected. Physical validation
  remains pending.
- Physical confirmation of the separated long-distance farm travel/action
  deadlines in commit `0853c61` and authorized harvest-slot changes in commit
  `d4f29eb`. The prior physical run entered the protected crop radius and walked
  through harvesting, but it used the build that still retried at 30 seconds.
- Physical validation of the exact Pam HarvestCraft 1.3.11-GTNH ground-crop,
  hanging-fruit, and non-destructive fruiting-log adapter.
- The public-API `IGrowable` adapter now requires a same-block `IPlantable`
  replacement and current supporting-block acceptance, while excluding
  persistent vanilla growables and dedicated optional-mod integrations.
  Automated planner/classifier coverage passes; physical validation on a
  disposable third-party crop is pending.
- Physical validation of the installed livestock pen scan and new
  non-destructive action executor, including protect/rejoin/unprotect.
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
4. Night sleep, the next farm occurrence, tree work, and livestock work become due while
   excavation is active.
5. The four chores execute in configured relative order while the fallback
   remains suspended.
6. Excavation resumes and advances from the exact prior checkpoint.
7. Controller and scheduler state are exported, restored into a fresh runtime,
   and assigned a fresh action epoch.
8. No already-consumed schedule occurrence is duplicated after reconnect.
9. Excavation receives the exact persisted checkpoint and continues advancing.

The scenario uses synthetic bounded runners so it proves orchestration and
persistence only. Physical backends must still satisfy their separate evidence
rows and the combined disposable-world exit criterion.
