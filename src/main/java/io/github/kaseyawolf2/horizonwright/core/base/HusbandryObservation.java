package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable, revisioned candidate-entity snapshot captured around one named pen. */
public final class HusbandryObservation {

    public static final int MAX_ANIMAL_CANDIDATES = 512;
    public static final int MAX_DROP_CANDIDATES = 256;

    private final NamedArea pen;
    private final long revision;
    private final String observationFingerprint;
    private final List<AnimalObservation> animals;
    private final List<HusbandryDropObservation> drops;
    private final boolean completePenScan;
    private final boolean entirePenLoaded;

    public HusbandryObservation(NamedArea pen, long revision, String observationFingerprint,
        List<AnimalObservation> animals, List<HusbandryDropObservation> drops, boolean completePenScan,
        boolean entirePenLoaded) {
        if (pen == null || revision < 0L
            || observationFingerprint == null
            || observationFingerprint.trim()
                .isEmpty()
            || animals == null
            || animals.contains(null)
            || animals.size() > MAX_ANIMAL_CANDIDATES
            || drops == null
            || drops.contains(null)
            || drops.size() > MAX_DROP_CANDIDATES) {
            throw new IllegalArgumentException("named pen, revision, fingerprint, and animals are required");
        }
        Set<String> identities = new HashSet<String>();
        for (AnimalObservation animal : animals) {
            if (!identities.add(animal.getIdentity())) {
                throw new IllegalArgumentException("duplicate animal identity " + animal.getIdentity());
            }
        }
        for (HusbandryDropObservation drop : drops) {
            if (!identities.add("drop:" + drop.getIdentity())) {
                throw new IllegalArgumentException("duplicate drop identity " + drop.getIdentity());
            }
        }
        this.pen = pen;
        this.revision = revision;
        this.observationFingerprint = observationFingerprint.trim();
        this.animals = Collections.unmodifiableList(new ArrayList<AnimalObservation>(animals));
        this.drops = Collections.unmodifiableList(new ArrayList<HusbandryDropObservation>(drops));
        this.completePenScan = completePenScan;
        this.entirePenLoaded = entirePenLoaded;
    }

    public NamedArea getPen() {
        return pen;
    }

    public long getRevision() {
        return revision;
    }

    /** Opaque fingerprint covering every entity and drop state that can affect the policy. */
    public String getObservationFingerprint() {
        return observationFingerprint;
    }

    public List<AnimalObservation> getAnimals() {
        return animals;
    }

    public List<HusbandryDropObservation> getDrops() {
        return drops;
    }

    public boolean isCompletePenScan() {
        return completePenScan;
    }

    public boolean isEntirePenLoaded() {
        return entirePenLoaded;
    }
}
