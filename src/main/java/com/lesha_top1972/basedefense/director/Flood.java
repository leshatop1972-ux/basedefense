package com.lesha_top1972.basedefense.director;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Подъём воды (наводнение +4, ливень +3): всё, что ниже уровня «море + подъём» и открыто сверху (крыша
 * спасает), заливается водой — вокруг игроков (радиус 48) и Базы, по кусочку за тик. Каждый поставленный
 * блок воды запоминается (data/basedefense_flood.dat); на следующее утро вода быстро уходит — убираются
 * только наши источники, которые всё ещё вода.
 */
public final class Flood extends PersistentState {

    private static final String SAVE_ID = "basedefense_flood";
    private static final int RADIUS = 48;
    private static final int COLUMNS_PER_TICK = 450;
    private static final int RECEDE_PER_TICK = 2500;
    private static final int LIMIT = 400_000;

    private final LongArrayList placed = new LongArrayList();
    private final LongOpenHashSet placedSet = new LongOpenHashSet();
    private boolean receding;

    /** Курсор обхода квадрата вокруг каждого центра (не сохраняется). */
    private static final Map<UUID, Integer> CURSORS = new HashMap<>();
    private static final UUID BASE_KEY = new UUID(0, 0);

    public static Flood get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(Flood::fromNbt, Flood::new, SAVE_ID);
    }

    /** Подъём воды в этот момент дня. */
    static int rise(DirectorEvent e, long timeInDay) {
        if (e == DirectorEvent.FLOOD) {
            return Math.max(1, (int) Math.ceil(4.0 * Math.min(1.0, timeInDay / 6000.0)));    // 4 блока к полудню
        }
        return Math.max(1, (int) Math.ceil(3.0 * Math.min(1.0, timeInDay / 12000.0)));       // ливень: 3 к вечеру
    }

    /** Тик подъёма: обойти по кусочку квадраты вокруг игроков и Базы. */
    void tickRise(ServerWorld w, int rise) {
        if (receding) {
            return;
        }
        int top = w.getSeaLevel() - 1 + rise;           // верх воды (море — до seaLevel − 1)
        List<BlockPos> centers = new ArrayList<>();
        List<UUID> keys = new ArrayList<>();
        for (ServerPlayerEntity p : w.getPlayers()) {
            centers.add(p.getBlockPos());
            keys.add(p.getUuid());
        }
        BlockPos base = WaveManager.basePos(w);
        if (base != null && w.shouldTickEntity(base)) {
            centers.add(base);
            keys.add(BASE_KEY);
        }
        int side = RADIUS * 2 + 1;
        int total = side * side;
        for (int c = 0; c < centers.size(); c++) {
            BlockPos center = centers.get(c);
            int cursor = CURSORS.getOrDefault(keys.get(c), 0);
            for (int n = 0; n < COLUMNS_PER_TICK; n++) {
                // шаг с простым числом — обход «вразброс», вода растёт равномерно, а не полосой
                cursor = (cursor + 7919) % total;
                int x = center.getX() - RADIUS + cursor % side;
                int z = center.getZ() - RADIUS + cursor / side;
                fillColumn(w, x, z, top);
            }
            CURSORS.put(keys.get(c), cursor);
        }
    }

    private void fillColumn(ServerWorld w, int x, int z, int top) {
        if (placed.size() >= LIMIT || !w.isChunkLoaded(x >> 4, z >> 4)) {
            return;
        }
        int y0 = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y0 > top || y0 <= w.getBottomY()) {
            return;
        }
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int y = y0; y <= top; y++) {
            p.set(x, y, z);
            BlockState st = w.getBlockState(p);
            boolean free = st.isAir() || st.isOf(Blocks.SNOW)
                    || (st.isReplaceable() && st.getFluidState().isEmpty());
            if (!free) {
                continue;
            }
            w.setBlockState(p, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
            long key = p.asLong();
            if (placedSet.add(key)) {
                placed.add(key);
            }
            markDirty();
        }
    }

    /** Начать отступление воды (следующее утро). */
    void startRecede() {
        if (!placed.isEmpty()) {
            receding = true;
            markDirty();
        }
    }

    /** Тик отступления — быстро, по 2500 блоков. */
    void tickRecede(ServerWorld w) {
        if (!receding) {
            return;
        }
        BlockPos.Mutable p = new BlockPos.Mutable();
        int n = 0;
        while (!placed.isEmpty() && n < RECEDE_PER_TICK) {
            long key = placed.removeLong(placed.size() - 1);
            placedSet.remove(key);
            p.set(key);
            n++;
            if (!w.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) {
                continue;
            }
            BlockState st = w.getBlockState(p);
            if (st.isOf(Blocks.WATER) && st.getFluidState().isStill() && st.getFluidState().getFluid() == Fluids.WATER) {
                w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        if (placed.isEmpty()) {
            receding = false;
        }
        markDirty();
    }

    boolean isReceding() {
        return receding;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLongArray("Placed", placed.toLongArray());
        nbt.putBoolean("Receding", receding);
        return nbt;
    }

    private static Flood fromNbt(NbtCompound nbt) {
        Flood f = new Flood();
        for (long k : nbt.getLongArray("Placed")) {
            if (f.placedSet.add(k)) {
                f.placed.add(k);
            }
        }
        f.receding = nbt.getBoolean("Receding");
        return f;
    }
}
