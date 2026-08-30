package io.github.kaseyawolf2.horizonwright.core.base;

/** Immutable named-pen animal view; players and unsupported species never enter this model. */
public final class AnimalObservation {

    private final String identity;
    private final LivestockSpecies species;
    private final BasePosition position;
    private final boolean adult;
    private final boolean named;
    private final boolean tamed;
    private final boolean protectedStock;
    private final boolean readyToBreed;
    private final boolean breedingEngaged;

    public AnimalObservation(String identity, LivestockSpecies species, BasePosition position, boolean adult,
        boolean named, boolean tamed, boolean protectedStock, boolean readyToBreed, boolean breedingEngaged) {
        if (identity == null || identity.trim()
            .isEmpty() || species == null || position == null) {
            throw new IllegalArgumentException("animal identity, species, and position are required");
        }
        if (!adult && (readyToBreed || breedingEngaged)) {
            throw new IllegalArgumentException("a baby animal cannot participate in breeding");
        }
        if (readyToBreed && breedingEngaged) {
            throw new IllegalArgumentException("an animal cannot need feed and already be breeding-engaged");
        }
        this.identity = identity.trim();
        this.species = species;
        this.position = position;
        this.adult = adult;
        this.named = named;
        this.tamed = tamed;
        this.protectedStock = protectedStock;
        this.readyToBreed = readyToBreed;
        this.breedingEngaged = breedingEngaged;
    }

    public String getIdentity() {
        return identity;
    }

    public LivestockSpecies getSpecies() {
        return species;
    }

    public BasePosition getPosition() {
        return position;
    }

    public boolean isAdult() {
        return adult;
    }

    public boolean isNamed() {
        return named;
    }

    public boolean isTamed() {
        return tamed;
    }

    public boolean isProtectedStock() {
        return protectedStock;
    }

    public boolean isReadyToBreed() {
        return readyToBreed;
    }

    public boolean isBreedingEngaged() {
        return breedingEngaged;
    }

    public boolean isEligibleTarget() {
        return adult && !named && !tamed && !protectedStock;
    }

    public boolean isEligibleFeedTarget() {
        return isEligibleTarget() && readyToBreed;
    }
}
