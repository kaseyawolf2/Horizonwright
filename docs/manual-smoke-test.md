# Manual Prism smoke test

Human-driven testing is the default because GTNH startup and GUI control are
slow. Automated GUI control is reserved for infrequent release checkpoints.

## Milestone 0 navigation checkpoint

1. Build and install the production Horizonwright JAR plus the exact separate
   `vendor/baritone/baritone-v1.2.19-mc1.7.10.jar` into the isolated instance.
   Confirm there is exactly one Horizonwright JAR and one Baritone JAR in
   `.minecraft/mods`.
2. Launch through `GTNH-2.9.0-Beta2-Horizonwright.lnk` and maximize Minecraft.
3. At the main menu, open **Mods** and confirm both `Horizonwright` and Baritone
   are present with the expected versions.
4. Back up and then open the disposable `Horizonwright Smoke Tes` world, or
   create a new disposable world. Never use a valued world or multiplayer
   server.
5. Press `H` and confirm the dashboard reports:
   - action epoch `1` and zero leases while idle;
   - Baritone `v1.2.19-mc1.7.10` ready;
   - safety ready; and
   - unattended operation disabled.
6. Stand on clear, level ground with at least five unobstructed blocks ahead.
   Note the selected hotbar slot, close the dashboard, and run `/hw status`.
7. Run `/hw goto ~4 ~ ~ 1`. Confirm the player walks to the clear target and
   status reaches `COMPLETED`. Confirm no block was dug or placed, no entity was
   attacked or used, and the selected hotbar slot did not change. Keep Waila's
   overlay enabled and confirm it continues updating during the route.
8. In a disposable open area, submit a target more than 128 blocks away (for
   example `/hw goto ~160 ~ ~ 2`) and confirm it is accepted and begins moving.
   While it is moving, press `E`; confirm the inventory opens normally. Close
   it, cancel the long route if necessary, and confirm direct control remains
   available.
9. Face another clear stretch and run `/hw goto ~12 ~ ~ 1`, then promptly run
   `/hw navcancel`. Confirm status reaches `CANCELLED`, motion stops, and no key
   remains stuck.
10. Start another clear route and run `/hw stop`. Confirm automation stops, the
    dashboard reports `AUTOMATION STOPPED`, and a new `/hw goto` is refused.
    Then verify ordinary player movement, manual block breaking, the `E`
    inventory, and unrelated mod UI/network behavior still work. Run `/hw reset`
    after cleanup drains, submit a new short route, and confirm automation works
    again. Do not use an actual death to test this manual-stop checkpoint.
11. Save and quit to title, then exit Minecraft cleanly.
12. Report completion, cancellation, automation-stop/reset, hotbar, long-route,
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
