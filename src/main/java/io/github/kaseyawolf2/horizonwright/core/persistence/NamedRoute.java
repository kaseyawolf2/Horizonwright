package io.github.kaseyawolf2.horizonwright.core.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.kaseyawolf2.horizonwright.core.persistence.PersistenceValidation.IdentifiedPersistenceValue;

public final class NamedRoute implements IdentifiedPersistenceValue {

    private final String id;
    private final String displayName;
    private final List<RouteNode> nodes;

    public NamedRoute(String id, String displayName, List<RouteNode> nodes) {
        this.id = PersistenceValidation.requireStableId(id, "id");
        this.displayName = PersistenceValidation.requireText(displayName, "displayName");
        this.nodes = Collections.unmodifiableList(new ArrayList<>(PersistenceValidation.requireList(nodes, "nodes")));
        validate();
    }

    @Override
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<RouteNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    void validate() {
        PersistenceValidation.requireStableId(id, "named route id");
        PersistenceValidation.requireText(displayName, "named route displayName");
        PersistenceValidation.requireList(nodes, "named route nodes");
        if (nodes.size() < 2) {
            throw new IllegalArgumentException("named route must contain at least two dimension-bearing nodes");
        }
        for (RouteNode node : nodes) {
            node.validate();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NamedRoute)) {
            return false;
        }
        NamedRoute that = (NamedRoute) other;
        return Objects.equals(id, that.id) && Objects.equals(displayName, that.displayName)
            && Objects.equals(nodes, that.nodes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, nodes);
    }
}
