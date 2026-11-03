package com.lesha_top1972.basedefense.client.fx;

import com.lesha_top1972.basedefense.fx.Shake;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.MathHelper;

/**
 * Тряска камеры от сервера ({@link Shake}): короткие затухающие колебания yaw/pitch (mixin/client/CameraMixin).
 * Сила ограничена MAX_DEG, чтобы прицел не уводило. Выключить — ENABLED = false.
 */
public final class CameraShake {

    public static final boolean ENABLED = true;
    /** Максимальный размах (градусы) при силе 1. */
    public static final float MAX_DEG = 1.2f;

    private static float strength;
    private static int total;
    private static int left;
    private static int age;

    private CameraShake() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Shake.PACKET, (client, handler, buf, sender) -> {
            float s = buf.readFloat();
            int t = buf.readVarInt();
            client.execute(() -> add(s, t));
        });
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            age++;
            if (left > 0) {
                left--;
            }
        });
    }

    private static void add(float s, int ticks) {
        float cur = current(0f);
        if (s >= cur) {
            strength = Math.min(1f, s);
            total = Math.max(1, ticks);
            left = total;
        }
    }

    private static float current(float tickDelta) {
        if (left <= 0) {
            return 0f;
        }
        float k = (left - tickDelta) / total;
        return strength * MathHelper.clamp(k, 0f, 1f) * MathHelper.clamp(k, 0f, 1f);
    }

    /** Смещение yaw (градусы) в этом кадре. */
    public static float yaw(float tickDelta) {
        if (!ENABLED) {
            return 0f;
        }
        float t = age + tickDelta;
        return current(tickDelta) * MAX_DEG * (MathHelper.sin(t * 2.9f) * 0.7f + MathHelper.sin(t * 5.3f) * 0.3f);
    }

    /** Смещение pitch (градусы) в этом кадре. */
    public static float pitch(float tickDelta) {
        if (!ENABLED) {
            return 0f;
        }
        float t = age + tickDelta;
        return current(tickDelta) * MAX_DEG * (MathHelper.sin(t * 3.7f + 1.3f) * 0.6f + MathHelper.sin(t * 6.1f) * 0.4f);
    }
}
