package io.github.kaseyawolf2.horizonwright.testfixtures;

public final class PistonBootsCapabilityFixture {

    private PistonBootsCapabilityFixture() {}

    public static CapabilitySnapshot evaluate(boolean equipped, int durabilityRemaining, int headClearanceBlocks,
        boolean landingSafe, boolean sprinting, long equipmentRevision) {
        if (durabilityRemaining < 0 || headClearanceBlocks < 0 || equipmentRevision < 1L) {
            throw new IllegalArgumentException("invalid Piston Boots input");
        }
        boolean active = equipped && durabilityRemaining > 0;
        return new CapabilitySnapshot(
            equipmentRevision,
            active,
            active && headClearanceBlocks >= 2 && landingSafe,
            active,
            active && sprinting ? 1 : 0);
    }

    public static final class CapabilitySnapshot {

        private final long equipmentRevision;
        private final boolean autoStep;
        private final boolean highJumpEdge;
        private final boolean safeFall;
        private final int extraSprintCostUnits;

        private CapabilitySnapshot(long equipmentRevision, boolean autoStep, boolean highJumpEdge, boolean safeFall,
            int extraSprintCostUnits) {
            this.equipmentRevision = equipmentRevision;
            this.autoStep = autoStep;
            this.highJumpEdge = highJumpEdge;
            this.safeFall = safeFall;
            this.extraSprintCostUnits = extraSprintCostUnits;
        }

        public boolean hasAutoStep() {
            return autoStep;
        }

        public boolean hasHighJumpEdge() {
            return highJumpEdge;
        }

        public boolean hasSafeFall() {
            return safeFall;
        }

        public int getExtraSprintCostUnits() {
            return extraSprintCostUnits;
        }

        public boolean isValidFor(long currentEquipmentRevision, boolean currentlyEquipped,
            int currentDurabilityRemaining) {
            return autoStep && currentlyEquipped
                && currentDurabilityRemaining > 0
                && equipmentRevision == currentEquipmentRevision;
        }
    }
}
