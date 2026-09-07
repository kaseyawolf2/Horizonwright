# Modded storage integration status

Priority: Iron Chests, Et Futurum Requiem, then JABBA, Storage Drawers, Binnie compartments.

## Container transfer layer

Inspected installed IronChest-6.1.13.jar and etfuturum-2.6.48-GTNH.jar with javap. ContainerIronChest and ContainerChestGeneric use destination slots followed by 27 main-inventory and 9 hotbar slots. Player-to-storage quick moves traverse destination slots forward. Iron Chest type acceptance is reflected by its validating slots; Et Futurum's merge override rejects the transfer if destination slots reject the stack.

The adapter recognizes exact vanilla ContainerChest, cpw.mods.ironchest.ContainerIronChest, and ganymedes01.etfuturum.inventory.ContainerChestGeneric classes. It validates inventory ownership, contiguous window and inventory indices, 36 player slots, bounded destination size, 64-slot stack limits, and destination acceptance before predicting a shift-click. Existing synchronized transaction confirmation remains required. Unknown container classes are not inferred or given custom-packet support.

## Still required before acceptance

- Profile/default/area storage capture now recognizes the installed Iron Chests tile classes and Et Futurum's TileEntityBarrel (which uses ContainerChestGeneric). Exact vanilla TileEntityChest capture remains supported. Entity storage such as chest boats is not treated as block storage. These capture paths still need physical tests.
- Travel/open/identity verification/close and return-to-excavation lifecycle.
  The unload runner has a separate cancellable accessStorage phase with MOVEMENT/LOOK/USE authority, before any container transaction. Runtime tests cover open-before-plan and pause cleanup. The live backend is wired to Baritone adjacent-goal navigation, live reach/ray validation, one right-click held through packet dispatch, and matching registered-inventory verification. Access times out after two minutes and refuses another open container or sneaking interaction. Close now requires an empty cursor, matching storage, outgoing packet dispatch and session cleanup before task completion. The existing ExcavationServiceCoordinator resumes its saved parent on child completion. These live paths still need physical testing.
- Dedicated live tests for each chest tier, rejected items, full storage, double chests, interrupted transactions, and unloading supplies preservation.
- JABBA, Storage Drawers and Binnie require separate protocol/interaction inspection and are not supported by this adapter.

The regression build alone does not prove live modded unloading. Do not label this transfer-layer change as complete automatic unloading.

## Physical test for the integrated cycle

1. Save an Iron Chests chest as default storage or the mining area's override. Leave free destination slots. Start excavation away from storage with inventory nearly full and watch travel, opening, unload, close, and resumed mining.
2. Verify tools and reserved supplies remain. Verify the selected mining tool is not restored to an unrelated hotbar slot after every block.
3. Repeat with vanilla single/double chests and the supported Et Futurum storage block. Confirm the actual block name/class in traces if unsupported.
4. Pause during travel and during transfers, then resume. No stale inventory click may be replayed. A reopened container must be observed again.
5. Test full storage, a missing/obstructed chest, a wrong open container and a nonempty cursor. These must report errors without dropping items or closing unrelated containers.

No broad mod-packet filtering is added. Continuous mining while travelling, opportunistic pickup, spiral traversal, and lower-priority storage integrations are separate outstanding work.
