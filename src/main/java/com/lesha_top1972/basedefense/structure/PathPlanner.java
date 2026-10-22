package com.lesha_top1972.basedefense.structure;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Планировщик тропинок поселения. Сеть получается «как у людей», а не кратчайшими путями:
 * - вокруг центральной площади — кольцевая тропинка;
 * - от кольца по четырём сторонам света — прямые главные дороги шириной 3
 *   (только в те стороны, где есть дома, и ровно такой длины, какая нужна);
 * - от главной дороги к каждому дому — прямое ответвление шириной 1, под прямым углом,
 *   с коротким «крыльцом» у самой двери. Ответвление никогда не идёт назад сквозь дом.
 * Тропинка кладётся только на землю/траву (дома, деревья, воду и камень не трогает).
 */
public final class PathPlanner {

    private PathPlanner() {
    }

    /** Все клетки тропинок по порядку: кольцо, главные дороги, ответвления. Y — примерный (уровень площади). */
    public static List<BlockPos> plan(TownState.Square square, List<TownState.Entrance> entrances) {
        int cx = square.center().getX();
        int cz = square.center().getZ();
        int y = square.center().getY();
        int ring = square.radius() + 1;

        Set<Long> seen = new LinkedHashSet<>();
        List<BlockPos> ringTiles = new ArrayList<>();
        // Кольцо вокруг площади
        for (int d = -ring; d <= ring; d++) {
            ringTiles.add(new BlockPos(cx + d, y, cz - ring));
            ringTiles.add(new BlockPos(cx + d, y, cz + ring));
            ringTiles.add(new BlockPos(cx - ring, y, cz + d));
            ringTiles.add(new BlockPos(cx + ring, y, cz + d));
        }

        // Ответвления к домам; заодно узнаём, насколько длинными должны быть главные дороги
        int[] extent = new int[4];   // +x, -x, +z, -z
        List<List<BlockPos>> branches = new ArrayList<>();
        for (TownState.Entrance e : entrances) {
            BlockPos door = e.ground();
            Direction out = e.outward();
            BlockPos start = door.offset(out);           // «крыльцо»: две клетки прямо от двери
            int sx = start.getX();
            int sz = start.getZ();
            int dx = sx - cx;
            int dz = sz - cz;

            boolean alongZ = Math.abs(dx) >= Math.abs(dz);   // идём по z к дороге, что тянется вдоль x
            if (goesBack(out, alongZ, alongZ ? cz - sz : cx - sx)) {
                alongZ = !alongZ;
            }
            List<BlockPos> branch = new ArrayList<>();
            branch.add(door);
            branch.add(start);
            if (alongZ) {
                int end;
                if (Math.abs(dx) <= ring) {
                    end = cz + Integer.signum(dz) * ring;            // упираемся в кольцо
                } else {
                    end = cz + Integer.signum(dz);                   // упираемся в край главной дороги
                    int idx = dx > 0 ? 0 : 1;
                    extent[idx] = Math.max(extent[idx], Math.abs(dx) + 1);
                }
                walk(branch, sx, sz, 0, end, y);
            } else {
                int end;
                if (Math.abs(dz) <= ring) {
                    end = cx + Integer.signum(dx) * ring;
                } else {
                    end = cx + Integer.signum(dx);
                    int idx = dz > 0 ? 2 : 3;
                    extent[idx] = Math.max(extent[idx], Math.abs(dz) + 1);
                }
                walk(branch, sx, sz, 1, end, y);
            }
            branches.add(branch);
        }

        // Главные дороги шириной 3 от кольца наружу
        List<BlockPos> roads = new ArrayList<>();
        for (int d = ring + 1; d <= Math.max(Math.max(extent[0], extent[1]), Math.max(extent[2], extent[3])); d++) {
            for (int w = -1; w <= 1; w++) {
                if (d <= extent[0]) roads.add(new BlockPos(cx + d, y, cz + w));
                if (d <= extent[1]) roads.add(new BlockPos(cx - d, y, cz + w));
                if (d <= extent[2]) roads.add(new BlockPos(cx + w, y, cz + d));
                if (d <= extent[3]) roads.add(new BlockPos(cx + w, y, cz - d));
            }
        }

        List<BlockPos> result = new ArrayList<>();
        addAll(result, seen, ringTiles);
        addAll(result, seen, roads);
        for (List<BlockPos> branch : branches) {
            // Ответвление строим от дороги к дому
            List<BlockPos> reversed = new ArrayList<>(branch);
            java.util.Collections.reverse(reversed);
            addAll(result, seen, reversed);
        }
        return result;
    }

    /** Не уходит ли ответвление назад, сквозь дом (против направления «из двери»). */
    private static boolean goesBack(Direction out, boolean alongZ, int delta) {
        if (delta == 0) {
            return false;
        }
        if (alongZ && out.getAxis() == Direction.Axis.Z) {
            return Integer.signum(delta) == -out.getOffsetZ();
        }
        if (!alongZ && out.getAxis() == Direction.Axis.X) {
            return Integer.signum(delta) == -out.getOffsetX();
        }
        return false;
    }

    /** Прямая линия от (sx, sz) по оси (0 — z, 1 — x) до координаты end включительно. */
    private static void walk(List<BlockPos> into, int sx, int sz, int axis, int end, int y) {
        int from = axis == 0 ? sz : sx;
        int step = Integer.signum(end - from);
        if (step == 0) {
            return;
        }
        for (int v = from + step; v != end + step; v += step) {
            into.add(axis == 0 ? new BlockPos(sx, y, v) : new BlockPos(v, y, sz));
        }
    }

    private static void addAll(List<BlockPos> result, Set<Long> seen, List<BlockPos> tiles) {
        for (BlockPos p : tiles) {
            long key = BlockPos.asLong(p.getX(), 0, p.getZ());
            if (seen.add(key)) {
                result.add(p);
            }
        }
    }

    // =====================================================================
    // Работа с миром
    // =====================================================================

    /** Блок земли в этой колонке (под травой/цветами) или null, если чанк не загружен. */
    @Nullable
    public static BlockPos groundAt(ServerWorld world, int x, int z) {
        BlockPos probe = new BlockPos(x, 0, z);
        if (!world.isChunkLoaded(probe)) {
            return null;
        }
        BlockPos p = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, probe).down();
        for (int i = 0; i < 3 && isPlant(world.getBlockState(p)); i++) {
            p = p.down();
        }
        return p;
    }

    private static boolean isPlant(BlockState state) {
        return !state.isAir() && state.isReplaceable() && state.getFluidState().isEmpty();
    }

    /** Нужно ли тут сделать тропинку (земля/трава, сверху воздух или растение). */
    public static boolean needsPath(ServerWorld world, BlockPos ground) {
        Block b = world.getBlockState(ground).getBlock();
        boolean soil = b == Blocks.GRASS_BLOCK || b == Blocks.DIRT || b == Blocks.COARSE_DIRT
                || b == Blocks.PODZOL || b == Blocks.MYCELIUM || b == Blocks.ROOTED_DIRT;
        if (!soil) {
            return false;
        }
        BlockState above = world.getBlockState(ground.up());
        return above.isAir() || isPlant(above);
    }

    /** Сделать клетку тропинкой (растение сверху убирается). */
    public static void makePath(ServerWorld world, BlockPos ground) {
        BlockPos up = ground.up();
        if (!world.getBlockState(up).isAir()) {
            world.breakBlock(up, false);
        }
        world.setBlockState(ground, Blocks.DIRT_PATH.getDefaultState(), Block.NOTIFY_ALL);
    }
}
