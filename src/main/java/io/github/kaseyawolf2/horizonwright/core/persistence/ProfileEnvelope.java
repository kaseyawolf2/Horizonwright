package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.logistics.NamedLoadout;

public final class ProfileEnvelope {

    private final int schemaVersion;
    private final String documentKind;
    private final long writtenAtEpochMillis;
    private final WorldProfileIdentity identity;
    private final List<ProfileReassociation> reassociations;
    private final List<NamedLocation> namedLocations;
    private final List<NamedRoute> namedRoutes;
    private final List<NamedLoadout> namedLoadouts;
    private final List<NamedStorageEndpoint> namedStorageEndpoints;
    private final List<NamedRepairStation> namedRepairStations;
    private final List<NamedArea> namedAreas;

    public ProfileEnvelope(long writtenAtEpochMillis, WorldProfileIdentity identity,
        List<ProfileReassociation> reassociations, List<NamedLocation> namedLocations, List<NamedRoute> namedRoutes) {
        this(
            PersistenceSchema.CURRENT_VERSION,
            PersistenceSchema.PROFILE_DOCUMENT_KIND,
            writtenAtEpochMillis,
            identity,
            reassociations,
            namedLocations,
            namedRoutes,
            Collections.<NamedLoadout>emptyList(),
            Collections.<NamedStorageEndpoint>emptyList(),
            Collections.<NamedRepairStation>emptyList(),
            Collections.<NamedArea>emptyList());
    }

    public ProfileEnvelope(long writtenAtEpochMillis, WorldProfileIdentity identity,
        List<ProfileReassociation> reassociations, List<NamedLocation> namedLocations, List<NamedRoute> namedRoutes,
        List<NamedLoadout> namedLoadouts) {
        this(
            PersistenceSchema.CURRENT_VERSION,
            PersistenceSchema.PROFILE_DOCUMENT_KIND,
            writtenAtEpochMillis,
            identity,
            reassociations,
            namedLocations,
            namedRoutes,
            namedLoadouts,
            Collections.<NamedStorageEndpoint>emptyList(),
            Collections.<NamedRepairStation>emptyList(),
            Collections.<NamedArea>emptyList());
    }

    public ProfileEnvelope(long writtenAtEpochMillis, WorldProfileIdentity identity,
        List<ProfileReassociation> reassociations, List<NamedLocation> namedLocations, List<NamedRoute> namedRoutes,
        List<NamedLoadout> namedLoadouts, List<NamedStorageEndpoint> namedStorageEndpoints) {
        this(
            PersistenceSchema.CURRENT_VERSION,
            PersistenceSchema.PROFILE_DOCUMENT_KIND,
            writtenAtEpochMillis,
            identity,
            reassociations,
            namedLocations,
            namedRoutes,
            namedLoadouts,
            namedStorageEndpoints,
            Collections.<NamedRepairStation>emptyList(),
            Collections.<NamedArea>emptyList());
    }

    public ProfileEnvelope(long writtenAtEpochMillis, WorldProfileIdentity identity,
        List<ProfileReassociation> reassociations, List<NamedLocation> namedLocations, List<NamedRoute> namedRoutes,
        List<NamedLoadout> namedLoadouts, List<NamedStorageEndpoint> namedStorageEndpoints,
        List<NamedRepairStation> namedRepairStations) {
        this(
            PersistenceSchema.CURRENT_VERSION,
            PersistenceSchema.PROFILE_DOCUMENT_KIND,
            writtenAtEpochMillis,
            identity,
            reassociations,
            namedLocations,
            namedRoutes,
            namedLoadouts,
            namedStorageEndpoints,
            namedRepairStations,
            Collections.<NamedArea>emptyList());
    }

    public ProfileEnvelope(long writtenAtEpochMillis, WorldProfileIdentity identity,
        List<ProfileReassociation> reassociations, List<NamedLocation> namedLocations, List<NamedRoute> namedRoutes,
        List<NamedLoadout> namedLoadouts, List<NamedStorageEndpoint> namedStorageEndpoints,
        List<NamedRepairStation> namedRepairStations, List<NamedArea> namedAreas) {
        this(
            PersistenceSchema.CURRENT_VERSION,
            PersistenceSchema.PROFILE_DOCUMENT_KIND,
            writtenAtEpochMillis,
            identity,
            reassociations,
            namedLocations,
            namedRoutes,
            namedLoadouts,
            namedStorageEndpoints,
            namedRepairStations,
            namedAreas);
    }

    private ProfileEnvelope(int schemaVersion, String documentKind, long writtenAtEpochMillis,
        WorldProfileIdentity identity, List<ProfileReassociation> reassociations, List<NamedLocation> namedLocations,
        List<NamedRoute> namedRoutes, List<NamedLoadout> namedLoadouts,
        List<NamedStorageEndpoint> namedStorageEndpoints, List<NamedRepairStation> namedRepairStations,
        List<NamedArea> namedAreas) {
        this.schemaVersion = schemaVersion;
        this.documentKind = documentKind;
        this.writtenAtEpochMillis = writtenAtEpochMillis;
        this.identity = identity;
        this.reassociations = immutableCopy(reassociations, "reassociations");
        this.namedLocations = immutableCopy(namedLocations, "namedLocations");
        this.namedRoutes = immutableCopy(namedRoutes, "namedRoutes");
        this.namedLoadouts = immutableCopy(namedLoadouts, "namedLoadouts");
        this.namedStorageEndpoints = immutableCopy(namedStorageEndpoints, "namedStorageEndpoints");
        this.namedRepairStations = immutableCopy(namedRepairStations, "namedRepairStations");
        this.namedAreas = immutableCopy(namedAreas, "namedAreas");
        validate();
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getDocumentKind() {
        return documentKind;
    }

    public long getWrittenAtEpochMillis() {
        return writtenAtEpochMillis;
    }

    public WorldProfileIdentity getIdentity() {
        return identity;
    }

    public List<ProfileReassociation> getReassociations() {
        return Collections.unmodifiableList(reassociations);
    }

    public List<NamedLocation> getNamedLocations() {
        return Collections.unmodifiableList(namedLocations);
    }

    public List<NamedRoute> getNamedRoutes() {
        return Collections.unmodifiableList(namedRoutes);
    }

    public List<NamedLoadout> getNamedLoadouts() {
        return Collections.unmodifiableList(namedLoadouts);
    }

    public List<NamedStorageEndpoint> getNamedStorageEndpoints() {
        return Collections.unmodifiableList(namedStorageEndpoints);
    }

    public List<NamedRepairStation> getNamedRepairStations() {
        return Collections.unmodifiableList(namedRepairStations);
    }

    public List<NamedArea> getNamedAreas() {
        return Collections.unmodifiableList(namedAreas);
    }

    void validate() {
        if (schemaVersion != PersistenceSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("profile schemaVersion must be " + PersistenceSchema.CURRENT_VERSION);
        }
        if (!PersistenceSchema.PROFILE_DOCUMENT_KIND.equals(documentKind)) {
            throw new IllegalArgumentException(
                "documentKind must be '" + PersistenceSchema.PROFILE_DOCUMENT_KIND + "'");
        }
        PersistenceValidation.requireNonNegative(writtenAtEpochMillis, "profile writtenAtEpochMillis");
        if (identity == null) {
            throw new IllegalArgumentException("profile identity must not be null");
        }
        identity.validate();
        if (writtenAtEpochMillis < identity.getCreatedAtEpochMillis()) {
            throw new IllegalArgumentException("profile writtenAtEpochMillis predates identity creation");
        }

        PersistenceValidation.requireList(reassociations, "profile reassociations");
        validateReassociationChain();
        PersistenceValidation.requireUniqueIds(namedLocations, "profile namedLocations");
        for (NamedLocation location : namedLocations) {
            location.validate();
        }
        PersistenceValidation.requireUniqueIds(namedRoutes, "profile namedRoutes");
        for (NamedRoute route : namedRoutes) {
            route.validate();
        }
        PersistenceValidation.requireList(namedLoadouts, "profile namedLoadouts");
        Set<String> loadoutIds = new HashSet<>();
        for (NamedLoadout loadout : namedLoadouts) {
            loadout.validate();
            if (!loadoutIds.add(loadout.getId())) {
                throw new IllegalArgumentException(
                    "profile namedLoadouts contains duplicate id '" + loadout.getId() + "'");
            }
        }
        PersistenceValidation.requireUniqueIds(namedStorageEndpoints, "profile namedStorageEndpoints");
        Set<String> locationIds = new HashSet<>();
        for (NamedLocation location : namedLocations) locationIds.add(location.getId());
        for (NamedStorageEndpoint endpoint : namedStorageEndpoints) {
            endpoint.validate();
            if (!locationIds.contains(endpoint.getLocationId())) {
                throw new IllegalArgumentException(
                    "storage endpoint '" + endpoint.getId()
                        + "' references missing location '"
                        + endpoint.getLocationId()
                        + "'");
            }
        }
        PersistenceValidation.requireUniqueIds(namedRepairStations, "profile namedRepairStations");
        for (NamedRepairStation station : namedRepairStations) {
            station.validate();
            if (!locationIds.contains(station.getLocationId())) {
                throw new IllegalArgumentException(
                    "repair station '" + station.getId()
                        + "' references missing location '"
                        + station.getLocationId()
                        + "'");
            }
            if (!loadoutIds.contains(station.getLoadoutId())) {
                throw new IllegalArgumentException(
                    "repair station '" + station.getId()
                        + "' references missing loadout '"
                        + station.getLoadoutId()
                        + "'");
            }
        }
        PersistenceValidation.requireList(namedAreas, "profile namedAreas");
        Set<String> areaIds = new HashSet<>();
        for (NamedArea area : namedAreas) {
            if (!areaIds.add(area.getId())) {
                throw new IllegalArgumentException("profile namedAreas contains duplicate id '" + area.getId() + "'");
            }
        }
    }

    private void validateReassociationChain() {
        ProfileReassociation previous = null;
        long lastConfirmation = -1L;
        Set<String> confirmationIds = new HashSet<>();
        for (ProfileReassociation reassociation : reassociations) {
            reassociation.validate();
            if (!confirmationIds.add(reassociation.getConfirmationId())) {
                throw new IllegalArgumentException(
                    "profile reassociations contain duplicate confirmationId '" + reassociation.getConfirmationId()
                        + "'");
            }
            if (reassociation.getConfirmedAtEpochMillis() < identity.getCreatedAtEpochMillis()) {
                throw new IllegalArgumentException("profile reassociation predates identity creation");
            }
            if (reassociation.getConfirmedAtEpochMillis() < lastConfirmation) {
                throw new IllegalArgumentException("profile reassociations must be chronological");
            }
            if (reassociation.getConfirmedAtEpochMillis() > writtenAtEpochMillis) {
                throw new IllegalArgumentException("profile reassociation occurs after writtenAtEpochMillis");
            }
            if (previous != null
                && !reassociation.startsAt(previous.getNewServerAddress(), previous.getNewWorldFingerprint())) {
                throw new IllegalArgumentException("profile reassociations must form one continuous identity chain");
            }
            lastConfirmation = reassociation.getConfirmedAtEpochMillis();
            previous = reassociation;
        }
        if (previous != null && !previous.endsAt(identity.getServerAddress(), identity.getWorldFingerprint())) {
            throw new IllegalArgumentException("latest reassociation must end at the current profile world identity");
        }
    }

    private static <T> List<T> immutableCopy(List<T> values, String field) {
        return Collections.unmodifiableList(new ArrayList<>(PersistenceValidation.requireList(values, field)));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProfileEnvelope)) {
            return false;
        }
        ProfileEnvelope that = (ProfileEnvelope) other;
        return schemaVersion == that.schemaVersion && writtenAtEpochMillis == that.writtenAtEpochMillis
            && Objects.equals(documentKind, that.documentKind)
            && Objects.equals(identity, that.identity)
            && Objects.equals(reassociations, that.reassociations)
            && Objects.equals(namedLocations, that.namedLocations)
            && Objects.equals(namedRoutes, that.namedRoutes)
            && Objects.equals(namedLoadouts, that.namedLoadouts)
            && Objects.equals(namedStorageEndpoints, that.namedStorageEndpoints)
            && Objects.equals(namedRepairStations, that.namedRepairStations)
            && Objects.equals(namedAreas, that.namedAreas);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            schemaVersion,
            documentKind,
            writtenAtEpochMillis,
            identity,
            reassociations,
            namedLocations,
            namedRoutes,
            namedLoadouts,
            namedStorageEndpoints,
            namedRepairStations,
            namedAreas);
    }
}
