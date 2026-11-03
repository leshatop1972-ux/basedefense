package com.lesha_top1972.basedefense.entity.enemy.thunder_tiger;

import com.lesha_top1972.basedefense.fx.Fx;
import com.lesha_top1972.basedefense.fx.Shake;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static com.lesha_top1972.basedefense.entity.enemy.thunder_tiger.TigerTimings.*;

/**
 * Действия «Громового тигра» по тикам: моменты ударов, рывков и выстрелов берутся из {@link TigerTimings}
 * (их пишет tools/thunder_tiger_gen.py вместе с анимациями — удар всегда совпадает с кадром).
 */
final class TigerActions {

    private TigerActions() {
    }

    static boolean isDodge(int a) {
        return a == DODGE_L || a == DODGE_R || a == DODGE_BACK;
    }

    /** Неостановимые: стойка от ударов почти не растёт, золотое/огненное свечение. */
    static boolean unstoppable(int a) {
        return a == SHATTER || a == TANGLE || a == FLURRY_A || a == FLURRY_B || a == FLURRY_END || a == ONE_HIT
                || a == PHASE2 || a == FINAL_RISE;
    }

    /** Атаки, после которых считаем «обычные» для ротации (2 обычные → 1 по площади). */
    static boolean regular(int a) {
        return a == DOUBLE || a == TRIPLE || a == DOUBLE_BLAST || a == TRIPLE_BLAST || a == SHATTER || a == COUNTER
                || a == ONE_HIT;
    }

    static int length(int a) {
        return switch (a) {
            case DOUBLE -> DOUBLE_LEN;
            case TRIPLE -> TRIPLE_LEN;
            case DOUBLE_BLAST -> DOUBLE_BLAST_LEN;
            case TRIPLE_BLAST -> TRIPLE_BLAST_LEN;
            case SHATTER -> SHATTER_LEN;
            case TANGLE -> TANGLE_LEN;
            case STANCE -> STANCE_LEN;
            case COUNTER -> COUNTER_LEN;
            case RELOAD -> RELOAD_LEN;
            case RELOAD_TAC -> RELOAD_TAC_LEN;
            case ONE_WAIT -> ONE_WAIT_LEN;
            case ONE_HIT -> ONE_HIT_LEN;
            case STAGGER -> STAGGER_LEN;
            case PHASE2 -> PHASE2_LEN;
            case FLURRY_A -> FLURRY_A_LEN;
            case FLURRY_B -> FLURRY_B_LEN;
            case FLURRY_END -> FLURRY_END_LEN;
            case DODGE_L -> DODGE_L_LEN;
            case DODGE_R -> DODGE_R_LEN;
            case DODGE_BACK -> DODGE_BACK_LEN;
            case DASH -> DASH_LEN;
            case KICK -> KICK_LEN;
            case BACKSHOT -> BACKSHOT_LEN;
            case DEATH -> DEATH_LEN;
            case TAUNT -> TAUNT_LEN;
            case TWIRL -> TWIRL_LEN;
            case FINAL_RISE -> FINAL_RISE_LEN;
            default -> 20;
        };
    }

    // ================================================================= начало / конец
    static void start(ThunderTigerEntity t, int a, LivingEntity target) {
        t.action = a;
        t.actionTick = 0;
        t.actionTarget = target != null ? target : t.getTarget();
        t.hitIds.clear();
        t.struckDuringWait = false;
        t.getNavigation().stop();
        t.setGlow(0);
        t.setLazy(a == TAUNT || a == TWIRL);
        t.sendAction(a);
        switch (a) {
            case PHASE2 -> {
                t.setPhase(Math.max(2, t.getPhase()));
                t.speak("phase2");
            }
            case FINAL_RISE -> t.speak("final");
            case STAGGER -> t.speak("stagger");
            case DEATH -> {
                t.setVelocity(0, t.getVelocity().y, 0);
                t.speak("death");
            }
            case SHATTER -> {
                t.sound(ModSounds.TIGER_IGNITE, 1.2f, 1.3f);
                t.speak("shatter");
            }
            case TANGLE -> {
                t.sound(ModSounds.TIGER_IGNITE, 1.6f, 0.8f);
                t.speak("tangle");
            }
            case ONE_WAIT -> t.speak("one_strike");
            case STANCE -> t.speak("stance");
            case DASH -> t.sound(ModSounds.TIGER_DASH, 1f, 1f);
            case TAUNT -> t.speak("taunt");
            default -> {
            }
        }
    }

    static void end(ThunderTigerEntity t) {
        int a = t.action;
        t.action = ThunderTigerEntity.NONE;
        t.setGlow(0);
        t.hitIds.clear();
        LivingEntity tgt = t.actionTarget;
        if (a == TWIRL && t.pendingArena) {
            t.pendingArena = false;
            TigerArena.pull(t);
            return;
        }
        switch (a) {
            case STANCE -> start(t, RELOAD, tgt);                       // никто не ударил — спокойно перезарядился
            case ONE_WAIT -> start(t, ONE_HIT, tgt);
            case STAGGER -> {
                if (t.breaks >= 3 && !t.isFinal()) {
                    start(t, FINAL_RISE, tgt);
                } else if (t.getPhase() == 1 && (t.breaks >= 2 || t.getHealth() <= t.getMaxHealth() * 0.5f)) {
                    start(t, PHASE2, tgt);
                } else {
                    t.brain.afterAction(a);
                }
            }
            case FLURRY_A, FLURRY_B -> {
                if (t.flurryLeft > 0) {
                    t.flurryPause = 6 + t.getRandom().nextInt(5);
                } else {
                    start(t, FLURRY_END, tgt);
                }
            }
            case DEATH -> t.finishDeath();
            default -> t.brain.afterAction(a);
        }
    }

    // ================================================================= помощники
    private static LivingEntity target(ThunderTigerEntity t) {
        LivingEntity a = t.actionTarget;
        if (a != null && a.isAlive()) {
            return a;
        }
        return t.getTarget();
    }

    private static double dist(ThunderTigerEntity t, LivingEntity e) {
        return e == null ? 99 : Math.sqrt(t.squaredDistanceTo(e.getX(), t.getY(), e.getZ()));
    }

    private static void face(ThunderTigerEntity t, LivingEntity e, float maxTurn) {
        if (e != null) {
            t.faceTarget(e, maxTurn);
        }
    }

    /** Толчок: горизонтально dir × h, вверх up (если > 0). */
    static void impulse(ThunderTigerEntity t, Vec3d dir, double h, double up) {
        Vec3d d = new Vec3d(dir.x, 0, dir.z);
        if (d.lengthSquared() > 1e-6) {
            d = d.normalize();
        }
        t.setVelocity(d.x * h, up > 0 ? up : t.getVelocity().y, d.z * h);
        t.velocityModified = true;
    }

    /** Рывок к цели: скорость speed (бл/тик), остановиться за stop блоков. */
    private static void dashToward(ThunderTigerEntity t, LivingEntity e, double speed, double stop) {
        if (e == null) {
            return;
        }
        Vec3d to = new Vec3d(e.getX() - t.getX(), 0, e.getZ() - t.getZ());
        double d = to.length();
        if (d <= stop) {
            t.setVelocity(0, t.getVelocity().y, 0);
            return;
        }
        Vec3d n = to.normalize();
        double s = Math.min(speed, d - stop);
        t.setVelocity(n.x * s, t.getVelocity().y, n.z * s);
        t.velocityModified = true;
        face(t, e, 40);
    }

    private static boolean hitAt(int k, int[] hits) {
        for (int h : hits) {
            if (h == k) {
                return true;
            }
        }
        return false;
    }

    private static int hitIndex(int k, int[] hits) {
        for (int i = 0; i < hits.length; i++) {
            if (hits[i] == k) {
                return i;
            }
        }
        return -1;
    }

    /** За сколько тиков до удара тигр перестаёт доворачиваться к цели — можно отойти в сторону. */
    static final int LOCK = 3;

    /** Удар скоро (в ближайшие LOCK тиков) или прямо сейчас. */
    private static boolean soon(int k, int[] hits) {
        for (int h : hits) {
            if (k <= h && h - k <= LOCK) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли замах через 2 тика (звук свиста). */
    private static boolean swingAt(int k, int[] hits) {
        return hitAt(k + 2, hits);
    }

    /** Точка у обуха тесака (откуда бьёт пламя) — приблизительно, по направлению взгляда. */
    private static Vec3d muzzle(ThunderTigerEntity t) {
        return t.getPos().add(0, 1.35, 0).add(t.forward().multiply(1.2));
    }

    /** Выстрел патроном, если он есть: звук, пламя из выхлопов. true — выстрел был. */
    static boolean shoot(ThunderTigerEntity t, Vec3d at) {
        if (t.getAmmo() <= 0) {
            return false;
        }
        t.setAmmo(t.getAmmo() - 1);
        boolean savage = t.isSavage();
        t.sound(savage ? ModSounds.TIGER_SHOT_SAVAGE : ModSounds.TIGER_SHOT, 1.8f, 0.92f + t.getRandom().nextFloat() * 0.16f);
        ServerWorld s = t.sw();
        if (s != null) {
            Fx.tigerShot(s, muzzle(t), t.forward().multiply(-1).add(0, 0.4, 0), savage);
            Fx.tigerBlast(s, at, savage ? 1.6 : 1.2, savage);
            Shake.send(s, at, 14, savage ? 0.35f : 0.25f, 6);
        }
        return true;
    }

    private static void slashFx(ThunderTigerEntity t, double reach, double tilt, int dir, boolean fire) {
        ServerWorld s = t.sw();
        if (s != null) {
            Fx.tigerSlash(s, t.getPos().add(0, 1.3, 0), t.getYaw(), reach, tilt, dir, fire);
        }
    }

    private static void scorchAll(ThunderTigerEntity t, List<LivingEntity> hit) {
        for (LivingEntity e : hit) {
            ThunderTigerEntity.scorch(e, t);
        }
    }

    private static void burstAll(ThunderTigerEntity t, List<LivingEntity> hit) {
        for (LivingEntity e : hit) {
            t.burst(e);
        }
    }

    // ================================================================= парирование (контрстойка)
    static void parry(ThunderTigerEntity t, LivingEntity attacker) {
        t.sound(ModSounds.TIGER_CLANG, 1.4f, 1.15f);
        ServerWorld s = t.sw();
        if (s != null) {
            Fx.tigerParry(s, t.getPos().add(0, 1.3, 0).add(t.forward().multiply(0.8)));
        }
        attacker.takeKnockback(0.6, t.getX() - attacker.getX(), t.getZ() - attacker.getZ());
        attacker.velocityModified = true;
        t.speak("counter");
        start(t, COUNTER, attacker);
        t.faceTarget(attacker, 180);
    }

    // ================================================================= тик действия
    static void tick(ThunderTigerEntity t) {
        int a = t.action;
        int k = t.actionTick;
        LivingEntity e = target(t);
        double d = dist(t, e);
        switch (a) {
            case DOUBLE -> {
                if (!soon(k, DOUBLE_HITS)) {
                    face(t, e, k < DOUBLE_HITS[0] ? 26 : 12);
                }
                if (k == DOUBLE_HITS[0] - 2 && d > 2.6) {
                    impulse(t, t.forward(), Math.min(0.5, (d - 2.2) * 0.33), 0);
                }
                if (swingAt(k, DOUBLE_HITS)) {
                    t.sound(ModSounds.TIGER_SWING, 1f, 1.05f + t.getRandom().nextFloat() * 0.1f);
                }
                int i = hitIndex(k, DOUBLE_HITS);
                if (i >= 0) {
                    t.hitIds.clear();
                    t.arcHit(3.7, 77, ThunderTigerEntity.DMG_SLASH, 0.5, 0.05, i == 0 ? 2 : 3, false, false);
                    slashFx(t, 3.6, i == 0 ? 0.05 : -0.25, i == 0 ? 1 : -1, false);
                    if (i == 0) {
                        t.speak("double");
                    }
                }
            }
            case TRIPLE -> {
                if (!soon(k, TRIPLE_HITS)) {
                    face(t, e, k < TRIPLE_HITS[0] ? 26 : 12);
                }
                if (k == TRIPLE_HITS[0] - 2 && d > 2.6) {
                    impulse(t, t.forward(), Math.min(0.5, (d - 2.2) * 0.33), 0);
                }
                if (k == TRIPLE_HITS[2] - 3 && d > 2.8) {
                    impulse(t, t.forward(), Math.min(0.55, (d - 2.4) * 0.37), 0);
                }
                if (swingAt(k, TRIPLE_HITS)) {
                    t.sound(k + 2 == TRIPLE_HITS[2] ? ModSounds.TIGER_SWING_HEAVY : ModSounds.TIGER_SWING, 1f, 1f);
                }
                int i = hitIndex(k, TRIPLE_HITS);
                if (i == 0 || i == 1) {
                    t.hitIds.clear();
                    t.arcHit(3.7, 77, ThunderTigerEntity.DMG_SLASH, 0.5, 0.05, i == 0 ? 2 : 2, false, false);
                    slashFx(t, 3.6, i == 0 ? 0.05 : -0.25, i == 0 ? 1 : -1, false);
                } else if (i == 2) {
                    t.hitIds.clear();
                    List<LivingEntity> hit = t.arcHit(4.3, 35, ThunderTigerEntity.DMG_HEAVY, 0.6, 0.15, 1, false, false);
                    burstAll(t, hit);
                    slashFx(t, 4.2, 1.45, 1, false);
                    ServerWorld s = t.sw();
                    if (s != null) {
                        Fx.impact(s, t.getPos().add(t.forward().multiply(2.6)), 1.2f, false);
                        Shake.send(s, t.getPos(), 14, 0.3f, 6);
                    }
                    t.sound(ModSounds.TIGER_SMASH, 1.2f, 1.1f);
                }
            }
            case DOUBLE_BLAST -> {
                if (!soon(k, DOUBLE_BLAST_HITS) && k < DOUBLE_BLAST_DASH_FROM) {
                    face(t, e, 26);
                }
                if (k == DOUBLE_BLAST_SMASH - 2) {
                    t.sound(ModSounds.TIGER_SWING_HEAVY, 1f, 1f);
                }
                if (k == DOUBLE_BLAST_SMASH) {
                    t.hitIds.clear();
                    Vec3d p = t.getPos().add(t.forward().multiply(2.6));
                    boolean shot = shoot(t, p);
                    t.areaHit(p, 2.6, ThunderTigerEntity.DMG_SMASH, 1.3, 0.45, 2, shot);
                    ServerWorld s = t.sw();
                    if (s != null) {
                        Fx.impact(s, p, 1.4f, true);
                    }
                    t.sound(ModSounds.TIGER_SMASH, 1.4f, 1f);
                    t.speak("double_blast");
                }
                if (k >= DOUBLE_BLAST_DASH_FROM && k <= DOUBLE_BLAST_DASH_TO) {
                    if (k == DOUBLE_BLAST_DASH_FROM) {
                        t.sound(ModSounds.TIGER_DASH, 1f, 1.1f);
                    }
                    dashToward(t, e, 0.9, 2.3);
                }
                if (k == DOUBLE_BLAST_HITS[1] - 2) {
                    t.sound(ModSounds.TIGER_SWING, 1f, 0.95f);
                }
                if (k == DOUBLE_BLAST_HITS[1]) {
                    t.hitIds.clear();
                    boolean shot = shoot(t, t.getPos().add(0, 1.2, 0).add(t.forward().multiply(2.4)));
                    t.arcHit(3.8, 77, ThunderTigerEntity.DMG_SLASH, 0.8, 0.35, 2, shot, false);
                    slashFx(t, 3.8, -0.6, -1, shot);
                }
            }
            case TRIPLE_BLAST -> {
                if (k < TRIPLE_BLAST_DASH_FROM) {
                    face(t, e, 26);
                }
                if (k == TRIPLE_BLAST_DASH_FROM) {
                    t.sound(ModSounds.TIGER_DASH, 1.1f, 1f);
                    t.speak("triple_blast");
                }
                if (k >= TRIPLE_BLAST_DASH_FROM && k <= TRIPLE_BLAST_DASH_TO) {
                    dashToward(t, e, 0.95, 2.1);
                }
                if (k > TRIPLE_BLAST_DASH_TO && !soon(k, TRIPLE_BLAST_HITS)) {
                    face(t, e, 11);
                }
                if (k == TRIPLE_BLAST_LEAP) {
                    impulse(t, t.forward(), d > 2.5 ? 0.35 : 0.05, 0.42);
                }
                if (swingAt(k, TRIPLE_BLAST_HITS)) {
                    t.sound(k + 2 == TRIPLE_BLAST_HITS[2] ? ModSounds.TIGER_SWING_HEAVY : ModSounds.TIGER_SWING, 1f, 1f);
                }
                int i = hitIndex(k, TRIPLE_BLAST_HITS);
                if (i >= 0) {
                    t.hitIds.clear();
                    boolean last = i == 2;
                    Vec3d p = t.getPos().add(0, 1.1, 0).add(t.forward().multiply(last ? 2.8 : 2.4));
                    boolean shot = shoot(t, p);
                    t.arcHit(last ? 4.4 : 3.9, last ? 70 : 80, last ? ThunderTigerEntity.DMG_HEAVY : ThunderTigerEntity.DMG_SLASH,
                            last ? 1.4 : 0.5, last ? 0.4 : 0.1, 2, shot, false);
                    slashFx(t, last ? 4.2 : 3.7, last ? 0.8 : (i == 0 ? 0.05 : -0.3), i == 1 ? -1 : 1, shot);
                    if (last && t.sw() != null) {
                        Shake.send(t.sw(), t.getPos(), 16, 0.4f, 8);
                    }
                }
            }
            case SHATTER -> {
                t.setGlow(k >= SHATTER_GLOW_FROM && k <= SHATTER_GLOW_TO ? 1 : 0);
                if (!soon(k, SHATTER_HITS)) {
                    face(t, e, 17);
                }
                if (swingAt(k, SHATTER_HITS)) {
                    t.sound(ModSounds.TIGER_SWING_HEAVY, 1.2f, 0.9f);
                }
                if (k == SHATTER_LEAP) {
                    impulse(t, t.forward(), d > 3 ? Math.min(0.6, (d - 2.5) * 0.25) : 0.05, 0.5);
                }
                int i = hitIndex(k, SHATTER_HITS);
                if (i == 0 || i == 1) {
                    t.hitIds.clear();
                    List<LivingEntity> hit = i == 0
                            ? t.arcHit(4.4, 35, ThunderTigerEntity.DMG_HEAVY, 0.5, 0.1, 2, false, false)
                            : t.arcHit(4.6, 95, ThunderTigerEntity.DMG_HEAVY, 0.9, 0.2, 2, false, false);
                    scorchAll(t, hit);
                    slashFx(t, 4.4, i == 0 ? 1.45 : 0.0, 1, true);
                    ServerWorld s = t.sw();
                    if (s != null) {
                        if (i == 0) {
                            Fx.impact(s, t.getPos().add(t.forward().multiply(2.8)), 1.4f, true);
                        }
                        Shake.send(s, t.getPos(), 16, 0.35f, 7);
                    }
                    t.sound(ModSounds.TIGER_SMASH, 1.3f, 1.05f);
                } else if (i == 2) {
                    t.hitIds.clear();
                    Vec3d p = t.getPos().add(t.forward().multiply(2.0));
                    List<LivingEntity> hit = t.areaHit(p, 3.4, ThunderTigerEntity.DMG_HEAVY * 1.2f, 1.2, 0.5, 2, false);
                    scorchAll(t, hit);
                    ServerWorld s = t.sw();
                    if (s != null) {
                        Fx.impact(s, p, 2.0f, true);
                        Fx.tigerBlast(s, p, 2.2, true);
                        Shake.send(s, t.getPos(), 22, 0.6f, 10);
                    }
                    t.sound(ModSounds.TIGER_SMASH, 1.8f, 0.85f);
                }
            }
            case TANGLE -> {
                if (k <= TANGLE_CHARGE_TO) {
                    t.setGlow(2);
                    face(t, e, 10);
                    t.setVelocity(0, t.getVelocity().y, 0);
                }
                if (k >= TANGLE_DASH_FROM && k <= TANGLE_DASH_TO) {
                    if (k == TANGLE_DASH_FROM) {
                        t.sound(ModSounds.TIGER_DASH, 1.2f, 0.9f);
                    }
                    dashToward(t, e, 1.0, 2.7);
                }
                if (k == TANGLE_LEAP) {
                    impulse(t, t.forward(), d > 3 ? 0.3 : 0.05, 0.55);
                }
                if (k == TANGLE_SMASH - 2) {
                    t.sound(ModSounds.TIGER_SWING_HEAVY, 1.4f, 0.8f);
                }
                if (k == TANGLE_SMASH) {
                    t.setGlow(0);
                    t.hitIds.clear();
                    int inner = t.getInner();
                    float mul = 1f + 0.024f * inner;
                    Vec3d p = t.getPos().add(t.forward().multiply(1.6));
                    boolean shot = shoot(t, p);
                    t.areaHit(p, 3.0, ThunderTigerEntity.DMG_TANGLE * mul, 1.2, 0.5, 2, shot);
                    t.startWave(t.getPos(), ThunderTigerEntity.DMG_WAVE * mul);
                    t.setInner(0);
                    ServerWorld s = t.sw();
                    if (s != null) {
                        Fx.impact(s, p, 2.4f, true);
                        Shake.send(s, t.getPos(), 26, 0.8f, 12);
                    }
                    t.sound(ModSounds.TIGER_WAVE, 2.0f, 1f);
                    t.sound(ModSounds.TIGER_SMASH, 1.8f, 0.8f);
                }
            }
            case STANCE -> face(t, e, 15);
            case COUNTER -> {
                if (!soon(k, COUNTER_HITS)) {
                    face(t, e, 55);
                }
                if (k == COUNTER_HITS[0] - 2) {
                    t.sound(ModSounds.TIGER_SWING, 1.1f, 1.1f);
                }
                if (k == COUNTER_HITS[0]) {
                    t.hitIds.clear();
                    boolean shot = shoot(t, t.getPos().add(0, 1.2, 0).add(t.forward().multiply(2.2)));
                    t.arcHit(4.2, 90, ThunderTigerEntity.DMG_COUNTER, 1.0, 0.2, 2, shot, false);
                    slashFx(t, 4.0, -0.15, -1, shot);
                }
            }
            case RELOAD -> {
                t.setVelocity(0, t.getVelocity().y, 0);
                if (k == RELOAD_OPEN) {
                    t.sound(ModSounds.TIGER_DRUM_OPEN, 1f, 1f);
                }
                if (k == RELOAD_EJECT) {
                    int spent = ThunderTigerEntity.MAX_AMMO - t.getAmmo();
                    t.setAmmo(0);
                    ServerWorld s = t.sw();
                    if (s != null && spent > 0) {
                        Fx.tigerShells(s, t.getPos().add(0, 1.3, 0).add(t.forward().multiply(0.5)), spent);
                    }
                    t.sound(ModSounds.TIGER_SHELL, 0.9f, 1f);
                }
                if (hitAt(k, RELOAD_INSERT)) {
                    t.setAmmo(t.getAmmo() + 2);
                    t.sound(ModSounds.TIGER_DRUM_LOAD, 0.9f, 0.95f + t.getRandom().nextFloat() * 0.1f);
                }
                if (k == RELOAD_CLOSE) {
                    t.setAmmo(ThunderTigerEntity.MAX_AMMO);
                    t.sound(ModSounds.TIGER_DRUM_CLOSE, 1.1f, 1f);
                }
                if (k == RELOAD_SPIN) {
                    t.sound(ModSounds.TIGER_SPIN, 0.9f, 1f);
                }
            }
            case RELOAD_TAC -> {
                if (k == RELOAD_TAC_OPEN) {
                    t.sound(ModSounds.TIGER_DRUM_OPEN, 1f, 1.1f);
                }
                if (k == RELOAD_TAC_EJECT) {
                    int spent = ThunderTigerEntity.MAX_AMMO - t.getAmmo();
                    ServerWorld s = t.sw();
                    if (s != null && spent > 0) {
                        Fx.tigerShells(s, t.getPos().add(0, 1.3, 0).add(t.forward().multiply(0.5)), spent);
                    }
                    t.sound(ModSounds.TIGER_SHELL, 0.8f, 1.1f);
                }
                if (hitAt(k, RELOAD_TAC_INSERT)) {
                    t.setAmmo(ThunderTigerEntity.MAX_AMMO);
                    t.sound(ModSounds.TIGER_DRUM_LOAD, 0.9f, 1.1f);
                }
                if (k == RELOAD_TAC_CLOSE) {
                    t.sound(ModSounds.TIGER_DRUM_CLOSE, 1f, 1.1f);
                }
            }
            case ONE_WAIT -> {
                t.setVelocity(0, t.getVelocity().y, 0);
                face(t, e, 5);
                if (k == ONE_WAIT_PLANT) {
                    t.sound(ModSounds.TIGER_PLANT, 1.3f, 1f);
                    ServerWorld s = t.sw();
                    if (s != null) {
                        Fx.impact(s, t.getPos().add(t.forward().multiply(1.1)), 0.8f, false);
                    }
                }
                if (k == ONE_WAIT_PLANT + 14 || k == ONE_WAIT_PLANT + 42) {
                    t.sound(ModSounds.TIGER_CHUCKLE, 1f, 1f);
                }
                // ударили первым — сразу выдёргивает тесак (удар слабее, его можно блокировать)
                if (t.struckDuringWait && k > ONE_WAIT_PLANT + 4) {
                    t.action = ThunderTigerEntity.NONE;
                    start(t, ONE_HIT, e);
                    t.struckDuringWait = true;
                }
            }
            case ONE_HIT -> {
                if (!soon(k, ONE_HIT_HITS)) {
                    face(t, e, 25);
                }
                if (k == ONE_HIT_PULL) {
                    t.sound(ModSounds.TIGER_PLANT, 1f, 1.3f);
                }
                if (k == ONE_HIT_HITS[0] - 2) {
                    t.sound(ModSounds.TIGER_SWING_HEAVY, 1.4f, 1f);
                    if (d > 3.0) {
                        impulse(t, t.forward(), Math.min(0.62, (d - 2.6) * 0.37), 0);
                    }
                }
                if (k == ONE_HIT_HITS[0]) {
                    t.hitIds.clear();
                    boolean weak = t.struckDuringWait;
                    t.arcHit(4.4, 85, weak ? ThunderTigerEntity.DMG_ONE * 0.5f : ThunderTigerEntity.DMG_ONE, 1.4, 0.35, 3,
                            false, !weak);
                    slashFx(t, 4.4, 0.1, 1, true);
                    if (t.sw() != null) {
                        Shake.send(t.sw(), t.getPos(), 18, 0.5f, 8);
                    }
                }
            }
            case STAGGER -> {
                t.setVelocity(0, t.getVelocity().y, 0);
                if (k == STAGGER_FALL) {
                    t.sound(ModSounds.TIGER_PLANT, 1f, 0.8f);
                    if (t.sw() != null) {
                        Fx.impact(t.sw(), t.getPos(), 0.8f, false);
                    }
                }
            }
            case PHASE2 -> {
                t.setVelocity(0, t.getVelocity().y, 0);
                face(t, e, 6);
                if (k == PHASE2_FLICK) {
                    t.sound(ModSounds.TIGER_FLICK, 1f, 1f);
                }
                if (k == PHASE2_CRACK) {
                    t.sound(ModSounds.TIGER_CHUCKLE, 1.2f, 0.85f);
                }
                if (k == PHASE2_BURST) {
                    t.setRings(ThunderTigerEntity.RINGS_MAX);
                    t.setSavage(true);
                    t.setAmmo(ThunderTigerEntity.MAX_AMMO);
                    flare(t);
                }
            }
            case FINAL_RISE -> {
                t.setVelocity(0, t.getVelocity().y, 0);
                face(t, e, 6);
                if (k == FINAL_RISE_FLARE) {
                    t.setPhase(3);
                    t.setRings(ThunderTigerEntity.RINGS_MAX);
                    t.setSavage(true);
                    t.setAmmo(ThunderTigerEntity.MAX_AMMO);
                    t.setPosture(0f);
                    flare(t);
                }
            }
            case FLURRY_A, FLURRY_B -> {
                int from = a == FLURRY_A ? FLURRY_A_DASH_FROM : FLURRY_B_DASH_FROM;
                int to = a == FLURRY_A ? FLURRY_A_DASH_TO : FLURRY_B_DASH_TO;
                if (k == 0 && e != null) {
                    Vec3d dir = new Vec3d(e.getX() - t.getX(), 0, e.getZ() - t.getZ());
                    double len = dir.length();
                    dir = len > 1e-3 ? dir.normalize() : t.forward();
                    double ang = (t.getRandom().nextDouble() - 0.5) * 0.8;
                    dir = new Vec3d(dir.x * Math.cos(ang) - dir.z * Math.sin(ang), 0, dir.x * Math.sin(ang) + dir.z * Math.cos(ang));
                    t.dashDir = dir;
                    t.dashSpeed = MathHelper.clamp((len + 3.5) / (to - from + 1), 0.55, 1.45);
                    t.dashStart = t.getPos();
                    t.faceTarget(e, 180);
                    t.sound(ModSounds.TIGER_FLURRY, 1f, 0.9f + t.getRandom().nextFloat() * 0.25f);
                }
                if (k >= from && k <= to) {
                    t.setVelocity(t.dashDir.x * t.dashSpeed, t.getVelocity().y, t.dashDir.z * t.dashSpeed);
                    t.velocityModified = true;
                    for (LivingEntity v : t.victims(2.2)) {
                        if (v.squaredDistanceTo(t) < 2.2 * 2.2) {
                            t.strike(v, ThunderTigerEntity.DMG_FLURRY, 0.3, 0.1, 1, false, false);
                        }
                    }
                }
                if (k == to + 1) {
                    t.setVelocity(0, t.getVelocity().y, 0);
                    if (t.sw() != null) {
                        Fx.tigerTrail(t.sw(), t.dashStart.add(0, 1, 0), t.getPos().add(0, 1, 0));
                        Fx.tigerSlash(t.sw(), t.getPos().add(0, 1.3, 0), t.getYaw() + 180, 3.0, 0.0, a == FLURRY_A ? 1 : -1, true);
                    }
                }
            }
            case FLURRY_END -> {
                if (k < FLURRY_END_JUMP) {
                    face(t, e, 25);
                }
                if (k == FLURRY_END_JUMP) {
                    impulse(t, t.forward(), MathHelper.clamp(d * 0.06, 0.05, 0.5), 0.78);
                    t.sound(ModSounds.TIGER_DASH, 1.2f, 0.8f);
                }
                if (k == FLURRY_END_HITS[0] - 3) {
                    t.sound(ModSounds.TIGER_SWING_HEAVY, 1.6f, 0.75f);
                }
                if (k == FLURRY_END_HITS[0]) {
                    t.hitIds.clear();
                    int n = t.getAmmo();
                    float mul = 1f + 0.08f * n;
                    t.areaHit(t.getPos(), 4.6, ThunderTigerEntity.DMG_FLURRY_END * mul, 1.6, 0.6, 3, n > 0);
                    ServerWorld s = t.sw();
                    if (s != null) {
                        for (int i = 0; i < Math.max(1, n); i++) {
                            double ang = i * Math.PI * 2 / Math.max(1, n) + t.getYaw() * MathHelper.RADIANS_PER_DEGREE;
                            Fx.tigerBlast(s, t.getPos().add(Math.cos(ang) * 2.4, 0.2, Math.sin(ang) * 2.4), 1.6, true);
                        }
                        Fx.impact(s, t.getPos(), 2.6f, true);
                        Shake.send(s, t.getPos(), 30, 1.0f, 16);
                    }
                    t.setAmmo(0);
                    t.sound(ModSounds.TIGER_FINAL, 2.2f, 1f);
                    t.speak("flurry_end");
                }
            }
            case DODGE_L, DODGE_R, DODGE_BACK -> {
                int to = a == DODGE_L ? DODGE_L_MOVE_TO : (a == DODGE_R ? DODGE_R_MOVE_TO : DODGE_BACK_MOVE_TO);
                if (k == to && t.sw() != null) {
                    Fx.tigerTrail(t.sw(), t.dashStart.add(0, 1, 0), t.getPos().add(0, 1, 0));
                }
                if (k > to) {
                    face(t, e, 30);
                }
            }
            case DASH -> {
                if (k == 0) {
                    t.dashStart = t.getPos();
                }
                if (k >= DASH_DASH_FROM && k <= DASH_DASH_TO) {
                    dashToward(t, e, 0.9, 2.5);
                }
                if (k == DASH_DASH_TO + 1 && t.sw() != null) {
                    Fx.tigerTrail(t.sw(), t.dashStart.add(0, 1, 0), t.getPos().add(0, 1, 0));
                }
            }
            case KICK -> {
                if (!soon(k, KICK_HITS)) {
                    face(t, e, 25);
                }
                if (k == KICK_HITS[0]) {
                    t.hitIds.clear();
                    if (!t.arcHit(2.9, 55, ThunderTigerEntity.DMG_KICK, 1.9, 0.35, 0, false, false).isEmpty()) {
                        t.sound(ModSounds.TIGER_KICK, 1.2f, 1f);
                    }
                }
            }
            case BACKSHOT -> {
                if (k == BACKSHOT_HOP_FROM) {
                    impulse(t, t.forward().multiply(-1), 0.85, 0.3);
                }
                if (k > BACKSHOT_HOP_TO) {
                    face(t, e, 20);
                }
                if (k == BACKSHOT_SHOTS[0]) {
                    t.hitIds.clear();
                    Vec3d p = t.getPos().add(t.forward().multiply(3.0));
                    if (shoot(t, p)) {
                        for (LivingEntity v : t.areaHit(p, 2.2, ThunderTigerEntity.DMG_BACKSHOT, 0.6, 0.25, 1, true)) {
                            v.setOnFireFor(3);
                        }
                        if (t.sw() != null) {
                            Fx.tigerFire(t.sw(), p, 2.2);
                        }
                    }
                }
            }
            case TAUNT -> {
                face(t, e, 8);
                if (k == 6) {
                    t.sound(ModSounds.TIGER_CHUCKLE, 1f, 1f);
                }
            }
            case TWIRL -> {
                face(t, e, 8);
                if (k == TWIRL_SPIN_FROM) {
                    t.sound(ModSounds.TIGER_SPIN, 1f, 1f);
                }
            }
            case DEATH -> t.setVelocity(0, t.getVelocity().y, 0);
            default -> {
            }
        }
        if (t.action == a && t.actionTick == k) {
            t.actionTick++;
            if (t.actionTick >= length(a)) {
                end(t);
            }
        }
    }

    /** Вспышка колец силы: оттолкнуть всех рядом, свет, звук. */
    private static void flare(ThunderTigerEntity t) {
        for (LivingEntity v : t.victims(5.0)) {
            v.takeKnockback(1.2, t.getX() - v.getX(), t.getZ() - v.getZ());
            v.addVelocity(0, 0.35, 0);
            v.velocityModified = true;
        }
        ServerWorld s = t.sw();
        if (s != null) {
            Fx.tigerRings(s, t.getPos());
            Shake.send(s, t.getPos(), 28, 0.7f, 14);
        }
        t.sound(ModSounds.TIGER_PHASE, 2f, 1f);
    }
}
