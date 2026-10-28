package com.lesha_top1972.basedefense.block;

import net.minecraft.block.ChestBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Склад Базы: сундуки вокруг блока «База» (в здании базы их много).
 * Сюда курьер сносит добычу жителей, отсюда строитель берёт материалы, а курьер — еду и продукты.
 */
public final class BaseStorage {

    /** В каком радиусе от блока «База» сундуки считаются складом. */
    public static final int RADIUS = 8;

    private static List<BlockPos> cache = List.of();
    private static long cacheTime = -1000;
    @Nullable
    private static BlockPos cachePos;

    private BaseStorage() {
    }

    /** Где стоит блок «База», если он в этом мире (измерении). */
    @Nullable
    public static BlockPos basePos(ServerWorld world) {
        BaseLocationState base = BaseLocationState.get(world.getServer());
        if (!base.hasBase() || !world.getRegistryKey().equals(base.getDimension())) {
            return null;
        }
        return base.getPos();
    }

    /** Сундуки склада (каждый блок сундука отдельно). Пересчитываются раз в 5 секунд. */
    public static List<BlockPos> chests(ServerWorld world) {
        BlockPos base = basePos(world);
        if (base == null) {
            return List.of();
        }
        long now = world.getTime();
        if (!base.equals(cachePos) || now - cacheTime > 100 || now < cacheTime) {
            List<BlockPos> found = new ArrayList<>();
            if (world.isChunkLoaded(base)) {
                for (BlockPos pos : BlockPos.iterate(base.add(-RADIUS, -2, -RADIUS), base.add(RADIUS, 3, RADIUS))) {
                    if (world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                        found.add(pos.toImmutable());
                    }
                }
            }
            cache = found;
            cacheTime = now;
            cachePos = base;
        }
        return cache;
    }
}
