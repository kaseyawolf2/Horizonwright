package io.github.kaseyawolf2.horizonwright.core.base;

/** Bounds for one species in one named pen. */
public final class HusbandryPolicy {

    private final NamedArea pen;
    private final LivestockSpecies species;
    private final long revision;
    private final int minimumAdults;
    private final int maximumAdults;

    public HusbandryPolicy(NamedArea pen, LivestockSpecies species, long revision, int minimumAdults,
        int maximumAdults) {
        if (pen == null || species == null || revision < 0L) {
            throw new IllegalArgumentException("named pen, species, and policy revision are required");
        }
        if (minimumAdults < 2 || maximumAdults < minimumAdults) {
            throw new IllegalArgumentException("adult bounds must preserve at least one breeding pair");
        }
        this.pen = pen;
        this.species = species;
        this.revision = revision;
        this.minimumAdults = minimumAdults;
        this.maximumAdults = maximumAdults;
    }

    public NamedArea getPen() {
        return pen;
    }

    public String getPenId() {
        return pen.getId();
    }

    public LivestockSpecies getSpecies() {
        return species;
    }

    public long getRevision() {
        return revision;
    }

    public int getMinimumAdults() {
        return minimumAdults;
    }

    public int getMaximumAdults() {
        return maximumAdults;
    }
}
