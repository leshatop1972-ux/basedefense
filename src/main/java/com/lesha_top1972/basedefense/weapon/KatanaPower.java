package com.lesha_top1972.basedefense.weapon;

import com.lesha_top1972.basedefense.Basedefense;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * «Древняя сила» (древняя катана): +1 за каждый удар, отбитый блоком, максимум 20; каждые 10 с без пополнения
 * уходит по 1. Тратится особой атакой (половина). Значение шлётся игроку — его рисует значок слева от ячеек.
 */
public final class KatanaPower {

    public static final Identifier SYNC = new Identifier(Basedefense.MOD_ID, "katana_power");
    public static final int MAX = 20;
    /** Раз в сколько тиков уходит единица. */
    public static final int DECAY = 200;

    private static final Map<UUID, int[]> POWER = new HashMap<>();   // {сила, тик следующего распада}

    private KatanaPower() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(KatanaPower::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> POWER.remove(handler.player.getUuid()));
    }

    public static int get(ServerPlayerEntity p) {
        int[] v = POWER.get(p.getUuid());
        return v == null ? 0 : v[0];
    }

    public static void add(ServerPlayerEntity p, int n) {
        int now = p.getServer() == null ? 0 : p.getServer().getTicks();
        int[] v = POWER.computeIfAbsent(p.getUuid(), k -> new int[]{0, 0});
        int old = v[0];
        v[0] = Math.max(0, Math.min(MAX, v[0] + n));
        if (n > 0) {
            v[1] = now + DECAY;                    // пополнили — отсчёт распада заново
        }
        if (v[0] != old) {
            sync(p, v[0]);
        }
    }

    /** Потратить половину (с округлением вверх). @return сколько было до траты. */
    public static int spendHalf(ServerPlayerEntity p) {
        int had = get(p);
        if (had > 0) {
            add(p, -(had + 1) / 2);
        }
        return had;
    }

    private static void tick(MinecraftServer server) {
        if (POWER.isEmpty()) {
            return;
        }
        int now = server.getTicks();
        for (Map.Entry<UUID, int[]> e : POWER.entrySet()) {
            int[] v = e.getValue();
            if (v[0] > 0 && now >= v[1]) {
                v[0]--;
                v[1] = now + DECAY;
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null) {
                    sync(p, v[0]);
                }
            }
        }
    }

    private static void sync(ServerPlayerEntity p, int value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(value);
        ServerPlayNetworking.send(p, SYNC, buf);
    }
}
