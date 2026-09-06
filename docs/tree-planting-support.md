# Tree planting and non-single-trunk support

## Current implementation (physical validation pending)

- Tree-farm setup chooses vanilla sapling type and grid spacing (2–16 blocks
  between planting-pattern origins). The choices persist in task/schedule specs.
- Newly queued GUI jobs can populate a fully loaded empty farm on grass/dirt.
  Existing logs/wood or vanilla saplings prevent the empty-farm bootstrap from
  planting over an existing woodlot. Existing schedules without a planting choice
  retain their old harvest/replant-only behavior until edited in tree setup.
- Single-sapling oak, spruce, birch, jungle, and acacia; four-sapling dark oak,
  giant spruce, and giant jungle patterns.
- Recognized vanilla square 2x2 root groups are felled as one captured tree and
  replanted as a four-sapling group. Boundary crossing, irregular root groups,
  trees over 256 captured logs, and areas over 65,536 scanned blocks remain excluded.
- Planting-only checkpoints and partial four-sapling groups can resume; already
  placed saplings are observed, not placed twice. Every placement preserves reserve.
- Replant leases now include inventory/container authority, fixing the rejection
  immediately after the first tree was felled.

## Physical tests

1. Retry the previously failed tree: it should replant its cleared root.
2. Empty area: choose a species and spacing, queue a pass with enough saplings
   above reserve; verify the grid, inventory staging, and area boundaries.
3. Save a scheduled pass, reopen setup, and verify both planting options persist.
4. Choose dark oak/giant spruce/giant jungle: verify four matching saplings per
   location, then test felling/replanting a fully grown bounded tree.
5. Interrupt and reconnect while a four-sapling group is partly planted; verify
   only the missing saplings are placed. Test insufficient saplings and Retry now.

## Broader GTNH tree support still required

Reference: the operator supplied the source text of https://wiki.gtnewhorizons.com/wiki/Tree
on 2026-09-06 because the page returned HTTP 403. Treat it as a species reference,
not authority to automate every listed block interaction.

Sapling count, grown trunk footprint, height, root behavior, and lumber-axe
compatibility are independent properties. Do not infer sapling patterns from
trunk width. Dedicated adapters and tests remain required for Twilight Forest,
Thaumcraft Greatwood/Silverwood, GT++ Rainforest Oak, Natura Redwood, and Biomes
O' Plenty Redwood/Sacred Oak. None are advertised as supported by this patch.

The reference describes destructive roots for several trees and possible nodes
in Silverwood. Those integrations must account for protected areas and special
blocks rather than treating all matching wood as ordinary lumber. Large trees
also require a reviewed alternative to the current bounded-capture limits.
