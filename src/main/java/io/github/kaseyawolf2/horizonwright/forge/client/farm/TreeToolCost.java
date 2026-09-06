package io.github.kaseyawolf2.horizonwright.forge.client.farm;

/** Estimated ticks, not a promise of server-side axe coverage. */
final class TreeToolCost {

    private TreeToolCost() {}

    static double estimate(float progress, int logs, int cubeLogs, int highLogs, boolean lumber, boolean wholeTree) {
        if (!(progress > 0) || !Float.isFinite(progress)) return Double.POSITIVE_INFINITY;
        int coverage = lumber ? (wholeTree ? logs : Math.max(1, cubeLogs)) : 1;
        int cuts = (logs + coverage - 1) / coverage;
        double climb = lumber && wholeTree ? 0 : Math.max(0, highLogs) * 40D;
        return cuts * Math.ceil(1D / progress) + climb;
    }
}
