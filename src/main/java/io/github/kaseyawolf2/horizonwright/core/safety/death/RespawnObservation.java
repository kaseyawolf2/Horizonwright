package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Complete evidence required for one tick of post-respawn stabilization. */
public final class RespawnObservation {

    private final String playerIdentity;
    private final double health;
    private final boolean dead;
    private final boolean worldLoaded;
    private final boolean normalInventoryContainer;
    private final DimensionBlockPosition playerPosition;
    private final InventoryManifest inventory;

    public RespawnObservation(String playerIdentity, double health, boolean dead, boolean worldLoaded,
        boolean normalInventoryContainer, DimensionBlockPosition playerPosition, InventoryManifest inventory) {
        this.playerIdentity = ConnectionIdentity.requireText(playerIdentity, "playerIdentity");
        if (!Double.isFinite(health)) {
            throw new IllegalArgumentException("health must be finite");
        }
        if (playerPosition == null || inventory == null) {
            throw new IllegalArgumentException("playerPosition and inventory must not be null");
        }
        this.health = health;
        this.dead = dead;
        this.worldLoaded = worldLoaded;
        this.normalInventoryContainer = normalInventoryContainer;
        this.playerPosition = playerPosition;
        this.inventory = inventory;
    }

    public String getPlayerIdentity() {
        return playerIdentity;
    }

    public double getHealth() {
        return health;
    }

    public boolean isDead() {
        return dead;
    }

    public boolean isWorldLoaded() {
        return worldLoaded;
    }

    public boolean isNormalInventoryContainer() {
        return normalInventoryContainer;
    }

    public DimensionBlockPosition getPlayerPosition() {
        return playerPosition;
    }

    public InventoryManifest getInventory() {
        return inventory;
    }
}
