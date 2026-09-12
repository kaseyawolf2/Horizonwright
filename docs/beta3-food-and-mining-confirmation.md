# Food unloading and server-confirmed excavation

Food: automatic inventory no longer reserves food, including food on the hotbar. Food is cargo until auto-eating and provisioning have an explicit supply policy. This also removes the implicit food reserve from inventory packing and ME deposits; explicit task/loadout reservations continue to apply.

Mining diagnosis: the September 12 client trace reported 1,835 unique positions successfully mined. Comparison with the saved New World found four still occupied: sandstone at (281,75,385), stone at (270,75,390), grass at (224,75,410), and sandstone at (272,75,359). The previous finish transition trusted client-predicted air plus outgoing packet drainage, without requiring incoming server block evidence or a final live-world check.

Each actively dug excavation target now has a connection-scoped server block watch. It observes single-block, multi-block, and chunk updates, including high block-id arrays, without consuming or modifying packets. Completion requires both server-confirmed air and a fresh client-world air observation after packet drainage. A restored matching block retries the same target up to three times. Missing server confirmation times out through the existing action deadline; a failed break never advances the task checkpoint. Watches close on retry, cancellation, failure, or completion.

Tests cover absence of server evidence, solid-to-air and air-to-solid server corrections, unrelated targets/connections, watch retirement, the actual inbound firewall path, negative chunk coordinates, multi-block updates, modded block ids, chunk section absence versus unload, and food eligibility for normal unloading/packing/network deposit.

The saved-world comparison is diagnostic only: no world blocks or task checkpoint files were edited. In-game verification is needed with this build, especially latency-dependent block corrections.
