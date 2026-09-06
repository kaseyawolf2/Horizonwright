package io.github.kaseyawolf2.horizonwright.core.base;

public enum AreaKind {

    UNASSIGNED("Choose type"),
    FARM("Crop farm"),
    LIVESTOCK("Livestock pen"),
    TREE_FARM("Tree farm"),
    EXCAVATION("Mining (excavation / quarry)"),
    QUARRY("Mining (excavation / quarry)");

    private final String label;

    AreaKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public AreaKind next() {
        return this == EXCAVATION || this == QUARRY ? UNASSIGNED : values()[ordinal() + 1];
    }
}
