# Manual Prism smoke test

Human-driven testing is the default because GTNH startup and GUI control are
slow. Automated GUI control is reserved for infrequent release checkpoints.

## Milestone 0 navigation checkpoint

1. Build and install the production Horizonwright JAR plus the exact separate
   `vendor/baritone/baritone-v1.2.19-mc1.7.10-1-7-10-forge+fcbbd4882c.jar`
   into the isolated instance.
   Confirm there is exactly one Horizonwright JAR and one Baritone JAR in
   `.minecraft/mods`.
2. Launch through `GTNH-2.9.0-Beta2-Horizonwright.lnk` and maximize Minecraft.
3. At the main menu, open **Mods** and confirm both `Horizonwright` and Baritone
   are present with the expected versions.
4. Back up and then open the disposable `Horizonwright Smoke Tes` world, or
   create a new disposable world. Never use a valued world or multiplayer
   server.
5. Run `/hw profile status`. On the first launch for this disposable world it
   must report explicit enrollment is needed; run `/hw profile enroll`, then
   confirm `/hw profile status` reports `READY`. Rejoining the same world must
   select the same profile without asking for enrollment again.
6. Press `H` and confirm the dashboard reports:
   - action epoch `1` and zero leases while idle;
   - Baritone enhanced build `fcbbd4882c` ready;
   - safety ready; and
   - unattended operation disabled.
   Open the **Baritone** tab, search for `allowSprint`, select it, and confirm
   its type, current value, default, Apply, Reset, and Toggle controls are
   visible. Toggle it and reset it, then reopen the tab and confirm the reset
   value persisted.
7. Stand on clear, level ground with at least five unobstructed blocks ahead.
   Note the selected hotbar slot, close the dashboard, and run `/hw status`.
8. Run `/hw goto ~4 ~ ~ 1`. Confirm the player walks to the clear target and
   status reaches `COMPLETED`. Confirm no block was dug or placed, no entity was
   attacked or used, and the selected hotbar slot did not change. Keep Waila's
   overlay enabled and confirm it continues updating during the route.
9. In a disposable open area, submit a target more than 128 blocks away (for
   example `/hw goto ~160 ~ ~ 2`) and confirm it is accepted and begins moving.
   While it is moving, press `E`; confirm the inventory opens normally. Close
   it, cancel the long route if necessary, and confirm direct control remains
   available.
10. Face another clear stretch and run `/hw goto ~12 ~ ~ 1`, then promptly run
   `/hw navcancel`. Confirm status reaches `CANCELLED`, motion stops, and no key
   remains stuck.
11. Start a longer clear route, save and leave the world while it is active,
    then rejoin the same enrolled world. Confirm the same goal is restored and
    resumes from the player's current position rather than disappearing or
    creating a duplicate.
12. Start another clear route and run `/hw stop`. Confirm automation stops, the
    dashboard reports `AUTOMATION STOPPED`, and a new `/hw goto` is refused.
    Then verify ordinary player movement, manual block breaking, the `E`
    inventory, and unrelated mod UI/network behavior still work. Run `/hw reset`
    after cleanup drains, run `/hw resume` without copying a task ID, and confirm
    the suspended route resumes. The dashboard's task-control button must also
    read `Resume task` when that route is the sole resumable task. Do not use an
    actual death to test this manual-stop checkpoint.
13. Create two resumable routes. Run `/hw resume` without an ID and confirm both
    chat choices are clickable and show hover details; click one and confirm
    only that exact task resumes. Repeat with two resumable routes from the
    dashboard and confirm **Choose task** opens a selection popup instead of
    guessing.
14. Save and quit to title, then exit Minecraft cleanly.
15. Report completion, cancellation, automation-stop/reset, hotbar, long-route,
    inventory-menu, and world-mutation
    results. Check the log for Horizonwright packet denials: no Forge, custom,
    unclassified, or non-packet traffic may be denied merely because it is
    unknown. If anything fails, also provide
    `.minecraft/logs/fml-client-latest.log` from the instance.

The Milestone 0 lease intentionally permits only movement and look. Use a clear
route: if Baritone attempts to dig, place, interact, attack, change slots, or
mutate a container, Horizonwright blocks the packet and fails the navigation
request.

Unknown or unintegrated network traffic is outside Horizonwright's ownership
and must pass through unchanged in every state, including death safety. Only
packet semantics with an explicit tested integration may be gated.

## Pending live vanilla-chest unload checkpoint

Use the dashboard's **Profile assets** page to create the named loadout and
targeted storage endpoint without hand-editing JSON. The first guided editor
creates an accept-all endpoint, so use only a disposable vanilla chest and test
selective destination rules later when the filter editor is present. Verify
reserved stacks remain in the player inventory and unreserved items follow
vanilla shift-click merge order,
and every click receives an accepted server confirmation plus the exact
synchronized after-state. Also verify a full chest, held cursor stack, wrong
chest coordinate, modded/subclassed container, disconnect, rejection, and
timeout all stop without replaying a click. This checkpoint is not yet a
recorded physical result.

## Pending live Tinkers repair checkpoint

Use the dashboard's **Profile assets** page to capture the tool and repair
material slots, then target and save the named repair station without
hand-editing JSON. Use the pinned Tool Station or Tool Forge, a disposable
damaged tool, and cheap approved repair material. For the current
prepared-station slice, place
the damaged tool in semantic input slot `1` and material in slots `2...`, leave
the configured reserved player slot and cursor empty, and confirm slot `0`
shows a lower-damage output preview. Verify Horizonwright takes that exact
output, consumes only the preview-declared approved material amounts, and
returns the same stable tool identity to the reserved slot. Wrong coordinates,
unapproved tool/material identities, altered preview NBT, occupied reserved
slot, rejected transaction, timeout, disconnect, and restart must all stop
without replay. This checkpoint is not yet a recorded physical result, and
automatic station population is still outstanding.

## Pending excavation service-composition checkpoint

After installing a build containing the guided editor, configure one disposable
excavation with both a named unload endpoint and a named repair station. Cause an unload
threshold at a recognizable frontier and confirm exactly one unload child is
shown, the parent remains blocked until the child is verified complete, and the
parent then resumes the identical frontier. Repeat for repair. Disconnect once
while a child is queued and once after its final transaction but before the
parent resumes; each reconnect must show the same child rather than create a
duplicate. A failed or cancelled child must leave the parent blocked for
operator inspection. This checkpoint is automated-test covered but has not yet
been physically recorded.

## Recorded physical result

On 2026-08-30, the operator completed the long-route, inventory-during-walk,
automation stop/reset, direct movement/mining/inventory, unrelated-traffic, and
ID-free resume checks above in the isolated GTNH 2.9.0-beta-2 instance. The
operator also created two resumable routes and confirmed that ambiguous
`/hw resume` listed both choices and that an exact task ID resumed only the
selected route. All checks passed with production JAR SHA-256
`CF9C16E234048D818C0957BA3284394FA6C62C81CA6D24ED6AC8DDB5415B41D7`.
