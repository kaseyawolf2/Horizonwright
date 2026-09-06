package io.github.kaseyawolf2.horizonwright.forge.client.farm;

final class TreePlantingRetry {

    private TreePlantingRetry() {}

    static boolean ready(int elapsedTicks) {
        return elapsedTicks >= 40;
    }

    static boolean exhausted(int retries) {
        return retries >= 3;
    }
}
