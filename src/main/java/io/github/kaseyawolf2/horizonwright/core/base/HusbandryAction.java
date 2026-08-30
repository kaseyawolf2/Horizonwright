package io.github.kaseyawolf2.horizonwright.core.base;

public final class HusbandryAction {

    private final HusbandryActionKind kind;
    private final String animalIdentity;
    private final HusbandryDropObservation dropTarget;

    HusbandryAction(HusbandryActionKind kind, String animalIdentity, HusbandryDropObservation dropTarget) {
        if (kind == null) {
            throw new IllegalArgumentException("husbandry action kind is required");
        }
        if (kind == HusbandryActionKind.COLLECT_DROPS) {
            if (animalIdentity != null || dropTarget == null) {
                throw new IllegalArgumentException("drop collection requires one typed drop and no animal");
            }
        } else if (animalIdentity == null || animalIdentity.trim()
            .isEmpty() || dropTarget != null) {
                throw new IllegalArgumentException("feed and cull actions require an animal identity");
            }
        this.kind = kind;
        this.animalIdentity = animalIdentity == null ? null : animalIdentity.trim();
        this.dropTarget = dropTarget;
    }

    public HusbandryActionKind getKind() {
        return kind;
    }

    public String getAnimalIdentity() {
        return animalIdentity;
    }

    public HusbandryDropObservation getDropTarget() {
        return dropTarget;
    }

    public boolean requiresPostconditionVerification() {
        return true;
    }
}
