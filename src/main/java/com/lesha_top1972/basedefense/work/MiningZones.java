package com.lesha_top1972.basedefense.work;

import com.lesha_top1972.basedefense.Basedefense;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Размеченные шахты (в каждом измерении свои): прямоугольные области, которые поселенцы с работой «Добыча»
 * выкапывают слоями сверху вниз. Размечаются «Разметчиком шахты». Клиентам рассылается список — рамки видно,
 * когда в руке разметчик.
 */
public class MiningZones extends PersistentState {

    public static final Identifier SYNC = new Identifier(Basedefense.MOD_ID, "mining_zones");
    private static final String SAVE_ID = "basedefense_mining_zones";
    /** Предел стороны и объёма одной шахты. */
    public static final int MAX_SIDE = 32;
    public static final int MAX_ZONES = 32;

    public record Zone(int id, BlockPos min, BlockPos max) {
        public boolean contains(BlockPos p) {
            return p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                    && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
        }

        public BlockPos center() {
            return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
        }
    }

    private final List<Zone> zones = new ArrayList<>();
    private int nextId = 1;

    public static MiningZones get(ServerWorld w) {
        return w.getPersistentStateManager().getOrCreate(MiningZones::fromNbt, MiningZones::new, SAVE_ID);
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> send(h.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) -> send(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> send(newP));
    }

    public List<Zone> zones() {
        return zones;
    }

    @Nullable
    public Zone add(ServerWorld w, BlockPos a, BlockPos b) {
        if (zones.size() >= MAX_ZONES) {
            return null;
        }
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        Zone z = new Zone(nextId++, min, max);
        zones.add(z);
        markDirty();
        broadcast(w);
        return z;
    }

    @Nullable
    public Zone at(BlockPos p) {
        for (Zone z : zones) {
            if (z.contains(p)) {
                return z;
            }
        }
        return null;
    }

    public void remove(ServerWorld w, Zone z) {
        zones.remove(z);
        markDirty();
        broadcast(w);
    }

    private void broadcast(ServerWorld w) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            send(p);
        }
    }

    public static void send(ServerPlayerEntity p) {
        List<Zone> list = get(p.getServerWorld()).zones;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(list.size());
        for (Zone z : list) {
            buf.writeBlockPos(z.min());
            buf.writeBlockPos(z.max());
        }
        ServerPlayNetworking.send(p, SYNC, buf);
    }

    public static MiningZones fromNbt(NbtCompound nbt) {
        MiningZones s = new MiningZones();
        s.nextId = Math.max(1, nbt.getInt("NextId"));
        NbtList list = nbt.getList("Zones", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            s.zones.add(new Zone(c.getInt("Id"), NbtHelper.toBlockPos(c.getCompound("Min")),
                    NbtHelper.toBlockPos(c.getCompound("Max"))));
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putInt("NextId", nextId);
        NbtList list = new NbtList();
        for (Zone z : zones) {
            NbtCompound c = new NbtCompound();
            c.putInt("Id", z.id());
            c.put("Min", NbtHelper.fromBlockPos(z.min()));
            c.put("Max", NbtHelper.fromBlockPos(z.max()));
            list.add(c);
        }
        nbt.put("Zones", list);
        return nbt;
    }
}
