# Manual Prism smoke test

Human-driven testing is the default because GTNH startup and GUI control are
slow. Automated GUI control is reserved for infrequent release checkpoints.

1. Build and install the production JAR into the isolated test instance.
2. Launch the instance through its Prism shortcut and maximize Minecraft.
3. At the main menu, open **Mods**, search for `Horizonwright`, and confirm the
   expected version.
4. Open the disposable `Horizonwright Smoke Tes` world, or create a new
   disposable world if it is unavailable.
5. Press `H` and confirm the bootstrap dashboard reports:
   - action epoch `1`;
   - action leases `0`;
   - no navigation backend;
   - safety ready;
   - unattended operation disabled.
6. Close the dashboard, save and quit to title, then exit Minecraft cleanly.
7. Report the visible result. If anything fails, also provide
   `.minecraft/logs/fml-client-latest.log` from the test instance.

Do not test on a valued world or multiplayer server. Do not add Baritone until
the packaging decision and real navigation adapter milestone are complete.
