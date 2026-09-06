package io.github.kaseyawolf2.horizonwright.forge.client.farm;

final class TreePlantingRetry {

    private TreePlantingRetry() {}

    static int settleTicksRemaining(int elapsedTicks) {
        return Math.max(0, 10 - Math.max(0, elapsedTicks));
    }

    static boolean ready(int elapsedTicks) {
        return elapsedTicks >= 40;
    }

    static boolean exhausted(int retries) {
        return retries >= 3;
    }
}
