package io.github.kaseyawolf2.horizonwright.core.repair;

import java.util.Objects;

/** Adapter-neutral Tinkers tool evidence with mutable damage separated from stable identity. */
public final class RepairToolSnapshot {

    private final String stableToolIdentity;
    private final int damage;
    private final int maximumDamage;
    private final int reservedInventorySlot;

    public RepairToolSnapshot(String stableToolIdentity, int damage, int maximumDamage, int reservedInventorySlot) {
        if (stableToolIdentity == null || stableToolIdentity.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("stableToolIdentity must not be blank");
        }
        if (damage < 0 || maximumDamage <= 0 || damage > maximumDamage || reservedInventorySlot < 0) {
            throw new IllegalArgumentException("tool damage, maximum damage, or reserved slot is invalid");
        }
        this.stableToolIdentity = stableToolIdentity.trim();
        this.damage = damage;
        this.maximumDamage = maximumDamage;
        this.reservedInventorySlot = reservedInventorySlot;
    }

    public String getStableToolIdentity() {
        return stableToolIdentity;
    }

    public int getDamage() {
        return damage;
    }

    public int getMaximumDamage() {
        return maximumDamage;
    }

    public int getReservedInventorySlot() {
        return reservedInventorySlot;
    }

    public int getRemainingDurability() {
        return maximumDamage - damage;
    }

    public double getRemainingFraction() {
        return (double) getRemainingDurability() / (double) maximumDamage;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RepairToolSnapshot)) {
            return false;
        }
        RepairToolSnapshot that = (RepairToolSnapshot) other;
        return damage == that.damage && maximumDamage == that.maximumDamage
            && reservedInventorySlot == that.reservedInventorySlot
            && stableToolIdentity.equals(that.stableToolIdentity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stableToolIdentity, damage, maximumDamage, reservedInventorySlot);
    }
}
