package io.github.kaseyawolf2.horizonwright.testfixtures;

public final class OrdinaryCropSemantics {

    private OrdinaryCropSemantics() {}

    public static Decision evaluate(CropKind kind, int metadata, Boolean growableCanGrow) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (metadata < 0) {
            throw new IllegalArgumentException("metadata must not be negative");
        }

        boolean mature;
        switch (kind) {
            case WHEAT:
            case CARROTS:
            case POTATOES:
                mature = (metadata & 7) == 7;
                break;
            case NETHER_WART:
                mature = (metadata & 3) == 3;
                break;
            case COCOA:
                mature = ((metadata & 12) >> 2) == 3;
                break;
            case GENERIC_IGROWABLE:
                if (growableCanGrow == null) {
                    return new Decision(false, HarvestAction.HOLD_FOR_ADAPTER);
                }
                mature = !growableCanGrow;
                break;
            default:
                throw new IllegalStateException("unsupported crop kind " + kind);
        }
        return new Decision(mature, mature ? HarvestAction.BREAK_AND_REPLANT : HarvestAction.WAIT);
    }

    public enum CropKind {
        WHEAT,
        CARROTS,
        POTATOES,
        NETHER_WART,
        COCOA,
        GENERIC_IGROWABLE
    }

    public enum HarvestAction {
        WAIT,
        BREAK_AND_REPLANT,
        HOLD_FOR_ADAPTER
    }

    public static final class Decision {

        private final boolean mature;
        private final HarvestAction action;

        private Decision(boolean mature, HarvestAction action) {
            this.mature = mature;
            this.action = action;
        }

        public boolean isMature() {
            return mature;
        }

        public HarvestAction getAction() {
            return action;
        }
    }
}
