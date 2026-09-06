# Free-hand harvest recovery

Operator feedback on 2026-09-06: the fruit farm completed a mostly clean full run;
the remaining observed hiccups were a reserved empty hotbar slot being filled and
the slot still being occupied after an inventory move.

## Physical checks

- Harvest with an empty hotbar slot. Allow pickups to fill it during travel;
  another empty hotbar slot should be selected without restarting the task.
- Fill all nine hotbar slots, leaving a main-inventory slot empty. The harvester
  should swap one hotbar stack into that space, settle, and harvest empty-handed.
- Confirm the moved stack is retained, no item is dropped, and no cursor stack is
  left behind. CropsNH spade staging should remain unchanged.
- With every inventory slot occupied, verify a clear inventory-full error and
  working Retry now after freeing space.
- Repeated slot refills are limited to three local recoveries per harvest action;
  debug logs identify replanning, swapping, recovery attempts, and any failure.
