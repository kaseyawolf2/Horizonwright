package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import java.util.List;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationRequest;

/** Retains canopy support and the exact leaf-break allowlist across approach retries. */
final class ExcavationApproachPolicy {

    private ExcavationApproachPolicy() {}

    static NavigationRequest create(String id, long epoch, int dimension, BlockPosition target, int attempt,
        boolean support, List<String> leaves, long now, long timeout) {
        int x = target.getX(), y = target.getY(), z = target.getZ();
        if (attempt == 1) {
            if (!leaves.isEmpty()) return NavigationRequest
                .adjacentToAllowingPlacementAndBreaking(id, epoch, dimension, x, y, z, leaves, now, timeout);
            return support ? NavigationRequest.adjacentToAllowingPlacement(id, epoch, dimension, x, y, z, now, timeout)
                : NavigationRequest.adjacentTo(id, epoch, dimension, x, y, z, now, timeout);
        }
        if (!leaves.isEmpty()) return NavigationRequest
            .nearAllowingPlacementAndBreaking(id, epoch, dimension, x, y, z, 3, leaves, now, timeout);
        return support ? NavigationRequest.nearAllowingPlacement(id, epoch, dimension, x, y, z, 3, now, timeout)
            : new NavigationRequest(id, epoch, dimension, x, y, z, 3, now, timeout);
    }
}
