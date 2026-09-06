# Area shapes and farm boundary test

Areas now support an inclusive two-corner rectangle or a horizontal circle with center XYZ, radius, and independent inclusive bottom/top Y. Center Y is retained but does not replace the height limits. Existing areas remain rectangles. Mining areas offer excavation and managed-quarry settings on the same saved geometry; old QUARRY area records remain readable.

## Geometry and mining

1. Create each shape through Areas -> Circle / coordinate editor. Save, reopen, change settings, leave/rejoin, and verify shape, coordinates, radius, and limits remain unchanged.
2. For a circular farm, also save its farm settings and verify it remains circular. The old farm editor shows read-only enclosing bounds for circles; Edit geometry changes the actual shape.
3. Queue small disposable rectangles and circles using both mining modes. Rectangle corners must be included, circle bounding-box corners excluded. Verify pause/resume retains the footprint. Managed rectangle ramps require at least 3x3 cells; managed circles retain the radius >=2 requirement.
4. Hover Work damage: it explains the legacy diagnostic estimate, not mining speed or a durability percentage. This field does not currently trigger repairs.

## Boundary trees and drops

1. Put a supported tree at a planting-area corner with branches outside and above the area. Its rooted connected logs should be harvested. A partial-edge 2x2 footprint is recognized when at least one root cell lies inside. A tree rooted entirely outside must not become an independent harvest target.
2. Replanting must still use only the configured grid inside the shape. A 2x2 planting requires its entire footprint inside.
3. Tree pickup searches live loaded drops in the enclosing bounds plus 32 blocks horizontally and world height. Test drops just outside the edge and below the planting surface. Unreachable items should remain diagnosable rather than silently claimed collected.
4. Crop pickup now rescans actual nearby live item positions instead of only walking beside the crop: up to 6 blocks horizontally, 16 below and 4 above the harvested crop. Test an edge harvest whose drops land outside or below the plot. It retains the action deadline for inaccessible drops/full inventory.

These are bounded searches, not permission to harvest arbitrary neighboring trees. Connected tree discovery remains limited to 32 horizontal blocks from the seed/root and the existing 256 captured-log limit. Pickup can include other dropped items within the search box; item ownership is not inferred. No unloaded chunks are force-loaded. No Baritone jar or global packet policy changes are included.

Automated geometry and checkpoint tests do not substitute for these physical Minecraft tests.
