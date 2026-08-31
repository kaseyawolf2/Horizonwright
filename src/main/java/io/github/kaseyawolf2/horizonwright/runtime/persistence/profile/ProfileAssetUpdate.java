package io.github.kaseyawolf2.horizonwright.runtime.persistence.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedLocation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedRepairStation;
import io.github.kaseyawolf2.horizonwright.core.persistence.NamedStorageEndpoint;

/** One validated, all-or-nothing set of profile asset additions or replacements. */
public final class ProfileAssetUpdate {

    private final List<NamedLocation> locations;
    private final List<NamedLoadout> loadouts;
    private final List<NamedStorageEndpoint> storageEndpoints;
    private final List<NamedRepairStation> repairStations;
    private final List<NamedArea> areas;

    public ProfileAssetUpdate(List<NamedLocation> locations, List<NamedLoadout> loadouts,
        List<NamedStorageEndpoint> storageEndpoints, List<NamedRepairStation> repairStations) {
        this(locations, loadouts, storageEndpoints, repairStations, Collections.<NamedArea>emptyList());
    }

    public ProfileAssetUpdate(List<NamedLocation> locations, List<NamedLoadout> loadouts,
        List<NamedStorageEndpoint> storageEndpoints, List<NamedRepairStation> repairStations, List<NamedArea> areas) {
        this.locations = copy(locations, "locations");
        this.loadouts = copy(loadouts, "loadouts");
        this.storageEndpoints = copy(storageEndpoints, "storageEndpoints");
        this.repairStations = copy(repairStations, "repairStations");
        this.areas = copy(areas, "areas");
        requireUniqueLocationIds(this.locations);
        requireUniqueLoadoutIds(this.loadouts);
        requireUniqueStorageIds(this.storageEndpoints);
        requireUniqueStationIds(this.repairStations);
        requireUniqueAreaIds(this.areas);
        if (isEmpty()) throw new IllegalArgumentException("profile asset update must not be empty");
    }

    public static ProfileAssetUpdate of(NamedLocation location, NamedLoadout loadout,
        NamedStorageEndpoint storageEndpoint, NamedRepairStation repairStation) {
        return new ProfileAssetUpdate(
            singleton(location),
            singleton(loadout),
            singleton(storageEndpoint),
            singleton(repairStation));
    }

    public static ProfileAssetUpdate ofArea(NamedArea area) {
        return new ProfileAssetUpdate(
            Collections.<NamedLocation>emptyList(),
            Collections.<NamedLoadout>emptyList(),
            Collections.<NamedStorageEndpoint>emptyList(),
            Collections.<NamedRepairStation>emptyList(),
            singleton(area));
    }

    public List<NamedLocation> getLocations() {
        return locations;
    }

    public List<NamedLoadout> getLoadouts() {
        return loadouts;
    }

    public List<NamedStorageEndpoint> getStorageEndpoints() {
        return storageEndpoints;
    }

    public List<NamedRepairStation> getRepairStations() {
        return repairStations;
    }

    public List<NamedArea> getAreas() {
        return areas;
    }

    private boolean isEmpty() {
        return locations.isEmpty() && loadouts.isEmpty()
            && storageEndpoints.isEmpty()
            && repairStations.isEmpty()
            && areas.isEmpty();
    }

    private static <T> List<T> copy(List<T> source, String field) {
        if (source == null || source.contains(null)) {
            throw new IllegalArgumentException(field + " must not be null or contain null");
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static <T> List<T> singleton(T value) {
        return value == null ? Collections.<T>emptyList() : Collections.singletonList(value);
    }

    private static void requireUniqueLocationIds(List<NamedLocation> values) {
        Set<String> ids = new HashSet<>();
        for (NamedLocation value : values) requireUnique(ids, value.getId(), "location");
    }

    private static void requireUniqueLoadoutIds(List<NamedLoadout> values) {
        Set<String> ids = new HashSet<>();
        for (NamedLoadout value : values) requireUnique(ids, value.getId(), "loadout");
    }

    private static void requireUniqueStorageIds(List<NamedStorageEndpoint> values) {
        Set<String> ids = new HashSet<>();
        for (NamedStorageEndpoint value : values) requireUnique(ids, value.getId(), "storage endpoint");
    }

    private static void requireUniqueStationIds(List<NamedRepairStation> values) {
        Set<String> ids = new HashSet<>();
        for (NamedRepairStation value : values) requireUnique(ids, value.getId(), "repair station");
    }

    private static void requireUniqueAreaIds(List<NamedArea> values) {
        Set<String> ids = new HashSet<>();
        for (NamedArea value : values) requireUnique(ids, value.getId(), "area");
    }

    private static void requireUnique(Set<String> ids, String id, String kind) {
        if (!ids.add(id)) throw new IllegalArgumentException("duplicate " + kind + " id in one update: " + id);
    }
}
