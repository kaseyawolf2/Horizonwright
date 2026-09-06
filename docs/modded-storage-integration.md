# Modded storage integration status

Priority: Iron Chests, Et Futurum Requiem, then JABBA, Storage Drawers, Binnie compartments.

## Container transfer layer

Inspected installed IronChest-6.1.13.jar and etfuturum-2.6.48-GTNH.jar with javap. ContainerIronChest and ContainerChestGeneric use destination slots followed by 27 main-inventory and 9 hotbar slots. Player-to-storage quick moves traverse destination slots forward. Iron Chest type acceptance is reflected by its validating slots; Et Futurum's merge override rejects the transfer if destination slots reject the stack.

The adapter recognizes exact vanilla ContainerChest, cpw.mods.ironchest.ContainerIronChest, and ganymedes01.etfuturum.inventory.ContainerChestGeneric classes. It validates inventory ownership, contiguous window and inventory indices, 36 player slots, bounded destination size, 64-slot stack limits, and destination acceptance before predicting a shift-click. Existing synchronized transaction confirmation remains required. Unknown container classes are not inferred or given custom-packet support.

## Still required before acceptance

- Profile/default/area storage capture now recognizes the installed Iron Chests tile classes and Et Futurum's TileEntityBarrel (which uses ContainerChestGeneric). Exact vanilla TileEntityChest capture remains supported. Entity storage such as chest boats is not treated as block storage. These capture paths still need physical tests.
- Travel/open/identity verification/close and return-to-excavation lifecycle.
  The unload runner now has a separate cancellable accessStorage phase with MOVEMENT/LOOK/USE authority, before any container transaction. Runtime tests cover open-before-plan and pause cleanup. The live chest backend still needs to implement this handle; its default remains manual access, so automatic chest travel is not yet functional.
- Dedicated live tests for each chest tier, rejected items, full storage, double chests, interrupted transactions, and unloading supplies preservation.
- JABBA, Storage Drawers and Binnie require separate protocol/interaction inspection and are not supported by this adapter.

The regression build alone does not prove live modded unloading. Do not label this transfer-layer change as complete automatic unloading.
