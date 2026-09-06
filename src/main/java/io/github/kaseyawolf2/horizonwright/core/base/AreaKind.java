package io.github.kaseyawolf2.horizonwright.core.base;

public enum AreaKind {

    UNASSIGNED("Choose type"),
    FARM("Crop farm"),
    LIVESTOCK("Livestock pen"),
    TREE_FARM("Tree farm"),
    EXCAVATION("Excavation"),
    QUARRY("Managed quarry");

    private final String label;

    AreaKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public AreaKind next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
