package com.lesha_top1972.basedefense.fx;

import com.lesha_top1972.basedefense.Basedefense;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Лёгкая тряска камеры (клиент — client/fx/CameraShake). Сила падает с расстоянием до нуля на краю radius.
 * Тряска короткая и слабая (не больше ~1.2°), чтобы не мешать драке; выключается на клиенте (CameraShake.ENABLED).
 */
public final class Shake {

    public static final Identifier PACKET = new Identifier(Basedefense.MOD_ID, "shake");

    private Shake() {
    }

    /** strength — 0..1 (1 — самая сильная), ticks — длительность. */
    public static void send(ServerWorld w, Vec3d at, double radius, float strength, int ticks) {
        for (ServerPlayerEntity p : PlayerLookup.around(w, at, radius)) {
            double d = Math.sqrt(p.squaredDistanceTo(at));
            float k = (float) Math.max(0.0, 1.0 - d / radius);
            if (k <= 0.05f) {
                continue;
            }
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeFloat(strength * k);
            buf.writeVarInt(ticks);
            ServerPlayNetworking.send(p, PACKET, buf);
        }
    }
}
