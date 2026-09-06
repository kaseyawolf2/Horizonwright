package io.github.kaseyawolf2.horizonwright.forge.client.farm;

final class TreePlantingPattern {

    private TreePlantingPattern() {}

    static boolean needsFill(int cells, int empty, int matching) {
        return (cells == 1 || cells == 4) && empty > 0 && matching >= 0 && empty + matching == cells;
    }
}
