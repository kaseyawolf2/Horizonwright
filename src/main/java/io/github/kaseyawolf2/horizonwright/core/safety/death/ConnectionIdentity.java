package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.util.Objects;

/** Stable profile identity plus a monotonically increasing live connection epoch. */
public final class ConnectionIdentity {

    private final long connectionEpoch;
    private final String serverIdentity;
    private final String worldIdentity;
    private final String playerIdentity;

    public ConnectionIdentity(long connectionEpoch, String serverIdentity, String worldIdentity,
        String playerIdentity) {
        if (connectionEpoch <= 0L) {
            throw new IllegalArgumentException("connectionEpoch must be positive");
        }
        this.connectionEpoch = connectionEpoch;
        this.serverIdentity = requireText(serverIdentity, "serverIdentity");
        this.worldIdentity = requireText(worldIdentity, "worldIdentity");
        this.playerIdentity = requireText(playerIdentity, "playerIdentity");
    }

    public long getConnectionEpoch() {
        return connectionEpoch;
    }

    public String getServerIdentity() {
        return serverIdentity;
    }

    public String getWorldIdentity() {
        return worldIdentity;
    }

    public String getPlayerIdentity() {
        return playerIdentity;
    }

    public boolean isSameProfile(ConnectionIdentity other) {
        return other != null && serverIdentity.equals(other.serverIdentity)
            && worldIdentity.equals(other.worldIdentity);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConnectionIdentity)) {
            return false;
        }
        ConnectionIdentity that = (ConnectionIdentity) other;
        return connectionEpoch == that.connectionEpoch && serverIdentity.equals(that.serverIdentity)
            && worldIdentity.equals(that.worldIdentity)
            && playerIdentity.equals(that.playerIdentity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionEpoch, serverIdentity, worldIdentity, playerIdentity);
    }

    static String requireText(String value, String name) {
        if (value == null || value.trim()
            .isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
