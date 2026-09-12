package io.github.kaseyawolf2.horizonwright.navigation.baritone;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraftforge.common.util.ForgeDirection;

import io.github.kaseyawolf2.horizonwright.core.excavation.BlockPosition;
import io.github.kaseyawolf2.horizonwright.forge.client.PillarMaterialPolicy;

/** Tracks the actual outgoing placement face, independent of screen crosshair and later held-slot changes. */
final class ScaffoldPlacementTracker {

    private final Map<BlockPosition, Pending> pending = new LinkedHashMap<>();

    BlockPosition sent(C08PacketPlayerBlockPlacement packet, int tick, Predicate<BlockPosition> air) {
        int face = packet.func_149568_f();
        ItemStack stack = packet.func_149574_g();
        if (face < 0 || face > 5 || !PillarMaterialPolicy.suitable(stack)) return null;
        ForgeDirection side = ForgeDirection.getOrientation(face);
        int y = packet.func_149571_d() + side.offsetY;
        if (y < 0 || y > 255) return null;
        BlockPosition position = new BlockPosition(
            packet.func_149576_c() + side.offsetX,
            y,
            packet.func_149570_e() + side.offsetZ);
        if (!air.test(position)) return null;
        pending.put(position, new Pending(((ItemBlock) stack.getItem()).field_150939_a, tick));
        return position;
    }

    void observe(int tick, Function<BlockPosition, Block> blocks, BiConsumer<BlockPosition, Block> confirmed) {
        Iterator<Map.Entry<BlockPosition, Pending>> iterator = pending.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPosition, Pending> entry = iterator.next();
            if (blocks.apply(entry.getKey()) == entry.getValue().block) {
                confirmed.accept(entry.getKey(), entry.getValue().block);
                iterator.remove();
            } else if (tick - entry.getValue().started >= 40) iterator.remove();
        }
    }

    List<BlockPosition> positions() {
        return new ArrayList<>(pending.keySet());
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }

    void clear() {
        pending.clear();
    }

    private static final class Pending {

        final Block block;
        final int started;

        Pending(Block block, int started) {
            this.block = block;
            this.started = started;
        }
    }
}
