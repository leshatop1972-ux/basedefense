package com.lesha_top1972.basedefense.entity.enemy.nabat;

import com.lesha_top1972.basedefense.effect.ModEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * «Шум» игроков в бою с Набатом. Очки шума = урон, нанесённый Набату, его слугам и колоколам
 * (+5 в секунду, пока стоишь на гудящем камне). Через 2 с без новых очков шум спадает на 6 в секунду.
 * Уровень I–V (пороги 10/25/45/70/100) виден значком «Шум»; самый шумный — цель мощных атак Набата.
 * На IV–V над головой появляются янтарные нотки (их видят друзья).
 */
public final class NabatNoise {

    static final float[] LEVELS = {10f, 25f, 45f, 70f, 100f};
    static final float MAX = 130f;
    static final int HOLD_TICKS = 40;
    static final float DECAY_PER_TICK = 0.3f;

    private static final Map<UUID, float[]> POINTS = new HashMap<>();   // [очки, тиков с последнего добавления]

    private NabatNoise() {
    }

    public static void add(PlayerEntity player, float points) {
        if (player.getWorld().isClient || points <= 0 || player.isCreative() || player.isSpectator()) {
            return;
        }
        float[] p = POINTS.computeIfAbsent(player.getUuid(), k -> new float[2]);
        p[0] = Math.min(MAX, p[0] + points);
        p[1] = 0;
    }

    public static float points(PlayerEntity player) {
        float[] p = POINTS.get(player.getUuid());
        return p == null ? 0f : p[0];
    }

    /** Уровень шума 0..5. */
    public static int level(PlayerEntity player) {
        float v = points(player);
        int lvl = 0;
        for (float t : LEVELS) {
            if (v >= t) {
                lvl++;
            }
        }
        return lvl;
    }

    /** Самый шумный игрок в радиусе (при равенстве — ближайший); null — никого нет. */
    public static PlayerEntity loudest(ServerWorld world, Vec3d center, double radius) {
        List<PlayerEntity> list = world.getEntitiesByClass(PlayerEntity.class, new Box(center, center).expand(radius),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        PlayerEntity best = null;
        double bestScore = -1;
        for (PlayerEntity p : list) {
            double score = points(p) * 1000.0 - p.squaredDistanceTo(center) * 0.01;
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    /** Каждый тик сервера: спад шума, значок, нотки над головой. */
    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, float[]>> it = POINTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, float[]> e = it.next();
            float[] p = e.getValue();
            p[1]++;
            if (p[1] > HOLD_TICKS) {
                p[0] -= DECAY_PER_TICK;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
            if (p[0] <= 0 || player == null) {
                if (player != null) {
                    player.removeStatusEffect(ModEffects.NOISE);
                }
                it.remove();
                continue;
            }
            int lvl = level(player);
            StatusEffectInstance cur = player.getStatusEffect(ModEffects.NOISE);
            if (lvl <= 0) {
                if (cur != null) {
                    player.removeStatusEffect(ModEffects.NOISE);
                }
            } else if (cur == null || cur.getAmplifier() != lvl - 1 || cur.getDuration() < 15) {
                if (cur != null) {
                    player.removeStatusEffect(ModEffects.NOISE);
                }
                player.addStatusEffect(new StatusEffectInstance(ModEffects.NOISE, 40, lvl - 1, false, false, true));
            }
            if (lvl >= 4 && (player.age % 12) == 0 && player.getWorld() instanceof ServerWorld sw) {
                // нотка: цвет задаётся первым смещением (0..1 → оттенок); 0.08 — янтарный
                sw.spawnParticles(ParticleTypes.NOTE, player.getX() + (player.getRandom().nextDouble() - 0.5) * 0.6,
                        player.getY() + player.getHeight() + 0.45, player.getZ() + (player.getRandom().nextDouble() - 0.5) * 0.6,
                        0, 0.08, 0, 0, 1);
            }
        }
    }
}
