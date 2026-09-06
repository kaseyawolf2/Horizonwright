# Schedule controls and task reruns

Physical feedback: Run now passed the operator test on 2026-09-06.

## Schedule-specific editors

- Select sleep, farm, tree-farm, and livestock schedules. Only relevant inputs should appear.
- Livestock has separate minimum-adults and desired-herd-size inputs; its culling opt-in remains explicit.
- Save changed settings, select another schedule, return, and verify the saved values.
- Check the smallest normal GUI scale for overlapping labels, fields, and buttons.

## Immediate scheduled runs and timing

- Select an active interval schedule and watch its connected-time countdown decrease.
- Click Run now before it is due. It should queue one occurrence using saved settings and reset the interval.
- Click again while that occurrence is unfinished: it must reject the duplicate with a visible explanation.
- Pause the schedule: countdown reads paused and Run now is disabled. Resume restarts the interval.
- A nightly schedule shows approximate world-time remaining; Run now does not bypass its required conditions or Minecraft's sleep rules.
- Save/rejoin and verify the schedule sequence and remaining connected-time interval persist.

## Completed-task rerun

- Select a completed task and click Rerun task. A fresh task should queue with the same settings and no old progress checkpoint.
- The original task must remain completed in history. Non-completed tasks cannot be rerun.
- Rerunning a completed scheduled occurrence is a standalone copy and does not edit the recurring schedule.
- Ensure manual automation stop still rejects starting a rerun until reset.
