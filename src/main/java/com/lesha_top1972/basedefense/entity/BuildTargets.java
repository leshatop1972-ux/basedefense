package com.lesha_top1972.basedefense.entity;

import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Поиск блоков построек ({@link BuiltBlocks}) вокруг моба в радиусе до 200 блоков — общий кеш по чанкам на мир.
 * Чанк сканируется только у поверхности (не глубже 10 блоков от верха столбца — в пещеры и шахты не лезут),
 * секции без нужных блоков пропускаются по палитре ({@code hasAny}), поэтому дёшево. Кеш чанка живёт 60 с;
 * за один запрос сканируется не больше {@code budget} новых чанков (ближние — первыми).
 * Загружены должны быть только чанки рядом с игроками — дальние незагруженные пропускаются.
 */
public final class BuildTargets {

    public static final int RADIUS = 200;
    static final int DEPTH = 10;
    static final long RESCAN = 1200;
    static final int MAX_PER_CHUNK = 256;

    private record Entry(long time, long[] pos) {
    }

    private static final Map<RegistryKey<World>, Map<Long, Entry>> CACHE = new HashMap<>();
    private static int[][] offsets;
    private static int offsetsR = -1;

    private BuildTargets() {
    }

    /** Смещения чанков в круге rc, отсортированные по расстоянию. */
    private static int[][] offsets(int rc) {
        if (offsetsR != rc) {
            List<int[]> l = new ArrayList<>();
            for (int dx = -rc; dx <= rc; dx++) {
                for (int dz = -rc; dz <= rc; dz++) {
                    if (dx * dx + dz * dz <= (rc + 1) * (rc + 1)) {
                        l.add(new int[]{dx, dz, dx * dx + dz * dz});
                    }
                }
            }
            l.sort((a, b) -> Integer.compare(a[2], b[2]));
            offsets = l.toArray(new int[0][]);
            offsetsR = rc;
        }
        return offsets;
    }

    /** Ближайший блок постройки в радиусе radius от from (или null). */
    public static synchronized BlockPos nearest(ServerWorld w, BlockPos from, int radius, int budget) {
        Map<Long, Entry> cache = CACHE.computeIfAbsent(w.getRegistryKey(), k -> new HashMap<>());
        if (cache.size() > 6000) {
            cache.clear();
        }
        long now = w.getTime();
        int cx = from.getX() >> 4;
        int cz = from.getZ() >> 4;
        BlockPos best = null;
        double bestD = (double) radius * radius;
        for (int[] o : offsets(radius / 16 + 1)) {
            double minD = Math.max(0, Math.sqrt(o[2]) - 1.5) * 16;
            if (minD * minD > bestD) {
                break;
            }
            int x = cx + o[0];
            int z = cz + o[1];
            long key = ChunkPos.toLong(x, z);
            Entry e = cache.get(key);
            if ((e == null || now - e.time > RESCAN) && budget > 0) {
                WorldChunk ch = w.getChunkManager().getWorldChunk(x, z, false);
                if (ch != null) {
                    e = scan(w, ch, now);
                    cache.put(key, e);
                    budget--;
                }
            }
            if (e == null) {
                continue;
            }
            boolean dirty = false;
            for (long l : e.pos) {
                if (l == Long.MIN_VALUE) {
                    continue;
                }
                BlockPos p = BlockPos.fromLong(l);
                double d = p.getSquaredDistance(from);
                if (d < bestD) {
                    BlockState st = w.getBlockState(p);
                    if (BuiltBlocks.isTarget(st)) {
                        best = p;
                        bestD = d;
                    } else {
                        dirty = true;
                    }
                }
            }
            if (dirty) {
                // сломанные блоки убираем из кеша
                long[] keep = Arrays.stream(e.pos).filter(l -> l != Long.MIN_VALUE
                        && BuiltBlocks.isTarget(w.getBlockState(BlockPos.fromLong(l)))).toArray();
                cache.put(key, new Entry(e.time, keep));
            }
        }
        return best;
    }

    /**
     * Идти к далёкой точке: ближе 28 блоков — прямо навигацией, дальше — по промежуточным точкам через 20 блоков
     * (поиск пути на 200 блоков слишком дорогой и ограничен дальностью преследования).
     */
    public static void walkTo(net.minecraft.entity.mob.MobEntity m, net.minecraft.util.math.Vec3d to, double speed) {
        net.minecraft.util.math.Vec3d d = new net.minecraft.util.math.Vec3d(to.x - m.getX(), 0, to.z - m.getZ());
        double len = d.length();
        if (len < 28) {
            m.getNavigation().startMovingTo(to.x, to.y, to.z, speed);
            return;
        }
        net.minecraft.util.math.Vec3d p = m.getPos().add(d.multiply(20.0 / len));
        int y = m.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.x), (int) Math.floor(p.z));
        if (!m.getNavigation().startMovingTo(p.x, y, p.z, speed)) {
            m.getMoveControl().moveTo(p.x, y, p.z, speed);
        }
    }

    private static Entry scan(World w, WorldChunk ch, long now) {
        int bx = ch.getPos().getStartX();
        int bz = ch.getPos().getStartZ();
        int[] top = new int[256];
        int minTop = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int t = ch.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, lx, lz);
                top[lx * 16 + lz] = t;
                minTop = Math.min(minTop, t);
                maxTop = Math.max(maxTop, t);
            }
        }
        List<Long> out = new ArrayList<>();
        ChunkSection[] secs = ch.getSectionArray();
        int bottom = ch.getBottomSectionCoord();
        for (int i = 0; i < secs.length && out.size() < MAX_PER_CHUNK; i++) {
            ChunkSection s = secs[i];
            int sy = (bottom + i) << 4;
            if (s == null || s.isEmpty() || sy + 15 < minTop - DEPTH || sy > maxTop + 1
                    || !s.hasAny(BuiltBlocks::isTarget)) {
                continue;
            }
            for (int ly = 0; ly < 16; ly++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int y = sy + ly;
                        if (y < top[lx * 16 + lz] - DEPTH) {
                            continue;
                        }
                        if (BuiltBlocks.isTarget(s.getBlockState(lx, ly, lz))) {
                            out.add(BlockPos.asLong(bx + lx, y, bz + lz));
                            if (out.size() >= MAX_PER_CHUNK) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        long[] arr = new long[out.size()];
        for (int k = 0; k < arr.length; k++) {
            arr[k] = out.get(k);
        }
        return new Entry(now, arr);
    }
}
