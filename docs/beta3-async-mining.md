# Asynchronous mining confirmation

Ordinary excavation now continues to the next target after the client removes a block and the final outgoing action packets drain. Server confirmation remains pending in an observation-only handle that no longer owns movement, look, digging, or held-item capabilities.

The runner tracks at most 16 pending blocks. It accepts confirmations out of order, counts a mined block only when confirmed, and waits when the queue fills. A restored block causes cancellation of the currently active action and re-observation of the small speculative window, starting before its oldest unresolved break. This revisits the rejected target and skips already-cleared positions using fresh observations. Repeated rejection is bounded, and five seconds without confirmation fails without committing speculative progress.

Saved progress remains at the checkpoint preceding unresolved breaks. Once all pending breaks confirm, the current frontier may be saved. Pause, disconnect, backend replacement, and cancellation close pending watches and retain the earlier frontier, so restarting cannot silently omit an unconfirmed target. Layer/final verification and unload/repair servicing wait for pending evidence; navigation obstructions, pillar-dependent work, and verification digs continue to require immediate confirmation.

Regression tests cover next-block submission before acknowledgement, released action leases, out-of-order acknowledgements, restored-block revisiting, queue bounds, final completion gating, restart/pause durability, missing acknowledgements, and unload coordination. The preceding server packet tests still cover single-block, multi-block, and chunk evidence.

This removes the per-block wait for server acknowledgement when the queue has room. Outgoing packet ordering and action cleanup still run. No in-game throughput benchmark has been performed for this build.
