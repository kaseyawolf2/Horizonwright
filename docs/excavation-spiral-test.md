# Inward spiral traversal

New area-based mining submissions persist `traversal=spiral-v1`. Legacy tasks lacking the field retain chunk order. The geometry identity includes traversal version, so a legacy checkpoint cannot silently become a spiral checkpoint.

Traversal visits clockwise rectangular rings inward, top layer to bottom; circle bounds clip each ring. This is not a claim of exact Baritone #circle ordering, nor of continuous movement while mining. It retains the bounded batch cursor and allocates no full-volume list. Thin rectangles, even dimensions, negative chunk boundaries, circle radius zero, and clipped circular coverage have automated tests.

Physical tests:

1. Queue a new small rectangular area, then a circle. Observe inward target ordering within each layer and full coverage.
2. Pause/rejoin midway. The task should resume its exact next target without reverting to chunk traversal.
3. Resume an existing pre-change task. It must retain its old order.
4. Check reclaimed/added blocks above the active layer; reinspection must still descend without skipping blocks.

Known limitation: clipping square rings to a circle can leave gaps between arcs; obstacles and unreachable targets can still cause repositioning. Continuous mining while travelling and pickup-directed movement remain separate work. Do not infer a measured speedup from passing geometry tests.
