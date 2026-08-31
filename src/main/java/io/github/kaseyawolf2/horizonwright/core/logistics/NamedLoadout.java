package io.github.kaseyawolf2.horizonwright.core.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Named collection of minimum inventory reservations. */
public final class NamedLoadout {

    private final String id;
    private final String displayName;
    private final List<LoadoutReservation> reservations;

    public NamedLoadout(String id, String displayName, List<LoadoutReservation> reservations) {
        this.id = requireText(id, "id");
        this.displayName = requireText(displayName, "displayName");
        this.reservations = reservations == null ? null : Collections.unmodifiableList(new ArrayList<>(reservations));
        validate();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<LoadoutReservation> getReservations() {
        return reservations;
    }

    public void validate() {
        requireText(id, "loadout id");
        requireText(displayName, "loadout displayName");
        if (reservations == null || reservations.contains(null)) {
            throw new IllegalArgumentException("loadout reservations must not be null or contain null");
        }
        Set<String> ids = new HashSet<>();
        for (LoadoutReservation reservation : reservations) {
            reservation.validate();
            if (!ids.add(reservation.getId())) {
                throw new IllegalArgumentException(
                    "loadout contains duplicate reservation id '" + reservation.getId() + "'");
            }
        }
        for (int left = 0; left < reservations.size(); left++) {
            for (int right = left + 1; right < reservations.size(); right++) {
                if (overlaps(reservations.get(left), reservations.get(right))) {
                    throw new IllegalArgumentException(
                        "loadout reservations '" + reservations.get(left)
                            .getId()
                            + "' and '"
                            + reservations.get(right)
                                .getId()
                            + "' overlap; use one unambiguous minimum reservation");
                }
            }
        }
    }

    private static boolean overlaps(LoadoutReservation left, LoadoutReservation right) {
        if (!left.getItemId()
            .equals(right.getItemId())) {
            return false;
        }
        boolean metadataOverlaps = left.getMetadata() == -1 || right.getMetadata() == -1
            || left.getMetadata() == right.getMetadata();
        boolean dataOverlaps = left.getDataHash() == null || right.getDataHash() == null
            || left.getDataHash()
                .equals(right.getDataHash());
        return metadataOverlaps && dataOverlaps;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NamedLoadout)) {
            return false;
        }
        NamedLoadout that = (NamedLoadout) other;
        return id.equals(that.id) && displayName.equals(that.displayName) && reservations.equals(that.reservations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, reservations);
    }
}
