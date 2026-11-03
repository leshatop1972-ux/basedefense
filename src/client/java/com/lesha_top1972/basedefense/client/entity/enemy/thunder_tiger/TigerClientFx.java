package com.lesha_top1972.basedefense.client.entity.enemy.thunder_tiger;

import com.lesha_top1972.basedefense.entity.enemy.thunder_tiger.ThunderTigerEntity;
import com.lesha_top1972.basedefense.entity.enemy.thunder_tiger.TigerTimings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Частицы «Громового тигра», которые клиент рисует сам (без пакетов), раз в тик: дымок сигары, след клинка на взмахах,
 * золотое свечение неостановимых ударов, пламя заряда ульты, угольки свирепых патронов, аура внутренней силы,
 * «звёзды» при сломе стойки. Количество частиц маленькое и вдали от камеры — чтобы не мешать драке.
 */
public final class TigerClientFx {

    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0f, 0.82f, 0.35f), 0.8f);
    private static final DustParticleEffect GOLD_S = new DustParticleEffect(new Vector3f(1.0f, 0.9f, 0.55f), 0.5f);
    private static final DustParticleEffect TRAIL = new DustParticleEffect(new Vector3f(0.95f, 0.95f, 1.0f), 0.55f);
    private static final DustParticleEffect TRAIL_RED = new DustParticleEffect(new Vector3f(1.0f, 0.35f, 0.12f), 0.65f);
    private static final DustParticleEffect EMBER = new DustParticleEffect(new Vector3f(1.0f, 0.3f, 0.08f), 0.45f);
    private static final DustParticleEffect AURA = new DustParticleEffect(new Vector3f(1.0f, 0.86f, 0.3f), 0.7f);

    private TigerClientFx() {
    }

    /** Взмах этого действия идёт сейчас (по тикам ударов из TigerTimings), +-2 тика. */
    private static boolean swinging(int a, int k) {
        int[] hits = switch (a) {
            case TigerTimings.DOUBLE -> TigerTimings.DOUBLE_HITS;
            case TigerTimings.TRIPLE -> TigerTimings.TRIPLE_HITS;
            case TigerTimings.DOUBLE_BLAST -> TigerTimings.DOUBLE_BLAST_HITS;
            case TigerTimings.TRIPLE_BLAST -> TigerTimings.TRIPLE_BLAST_HITS;
            case TigerTimings.SHATTER -> TigerTimings.SHATTER_HITS;
            case TigerTimings.TANGLE -> TigerTimings.TANGLE_HITS;
            case TigerTimings.COUNTER -> TigerTimings.COUNTER_HITS;
            case TigerTimings.ONE_HIT -> TigerTimings.ONE_HIT_HITS;
            case TigerTimings.FLURRY_A -> TigerTimings.FLURRY_A_HITS;
            case TigerTimings.FLURRY_B -> TigerTimings.FLURRY_B_HITS;
            case TigerTimings.FLURRY_END -> TigerTimings.FLURRY_END_HITS;
            default -> null;
        };
        if (hits == null) {
            return false;
        }
        for (int h : hits) {
            if (k >= h - 3 && k <= h + 1) {
                return true;
            }
        }
        return false;
    }

    public static void tick(ThunderTigerEntity e) {
        if (e.fxAge == e.age) {
            return;
        }
        e.fxAge = e.age;
        World w = e.getWorld();
        Random r = e.getRandom();
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean bladeOk = e.age - e.bladeAge <= 1 && e.bladeTip != Vec3d.ZERO;
        int a = e.clientAction;
        int k = e.age - e.clientActionAge;
        boolean inAction = a >= 0 && a < TigerTimings.NAMES.length && k >= 0 && k < 200;
        // дымок сигары
        if (e.getPhase() == 1 && e.cigarTip != Vec3d.ZERO && e.age % 6 == 0) {
            w.addParticle(ParticleTypes.SMOKE, e.cigarTip.x, e.cigarTip.y, e.cigarTip.z, 0, 0.015, 0);
        }
        if (e.getPhase() == 1 && e.cigarTip != Vec3d.ZERO && e.age % 2 == 0 && r.nextFloat() < 0.25f) {
            w.addParticle(EMBER, e.cigarTip.x, e.cigarTip.y, e.cigarTip.z, 0, 0.01, 0);
        }
        if (bladeOk) {
            // след клинка на взмахах
            if (inAction && swinging(a, k)) {
                Vec3d prev = e.prevBladeTip;
                Vec3d cur = e.bladeTip;
                if (prev != Vec3d.ZERO && prev.squaredDistanceTo(cur) > 0.09 && prev.squaredDistanceTo(cur) < 16) {
                    boolean fire = e.getAmmo() > 0 || e.getGlow() != 0;
                    for (int i = 0; i < 6; i++) {
                        double f = i / 6.0;
                        Vec3d p = prev.lerp(cur, f);
                        Vec3d m = e.bladeMid.lerp(p, 0.6 + 0.4 * r.nextDouble());
                        w.addParticle(fire && e.isSavage() ? TRAIL_RED : TRAIL, p.x, p.y, p.z, 0, 0, 0);
                        if (i % 2 == 0) {
                            w.addParticle(TRAIL, m.x, m.y, m.z, 0, 0, 0);
                        }
                    }
                    if (fire && r.nextFloat() < 0.6f) {
                        w.addParticle(ParticleTypes.SMALL_FLAME, cur.x, cur.y, cur.z, 0, 0.01, 0);
                    }
                }
            }
            e.prevBladeTip = e.bladeTip;
            int glow = e.getGlow();
            if (glow == 1) {
                // золото: неостановимый удар
                for (int i = 0; i < 2; i++) {
                    Vec3d p = e.bladeBase.lerp(e.bladeTip, r.nextDouble());
                    w.addParticle(i == 0 ? GOLD : GOLD_S, p.x, p.y, p.z, 0, 0.02, 0);
                }
                if (r.nextFloat() < 0.3f) {
                    Vec3d p = e.bladeBase.lerp(e.bladeTip, r.nextDouble());
                    w.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, (r.nextDouble() - 0.5) * 0.02, 0.03,
                            (r.nextDouble() - 0.5) * 0.02);
                }
            } else if (glow == 2) {
                // заряд пламенем
                for (int i = 0; i < 3; i++) {
                    Vec3d p = e.bladeBase.lerp(e.bladeTip, r.nextDouble());
                    w.addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, (r.nextDouble() - 0.5) * 0.02, 0.04,
                            (r.nextDouble() - 0.5) * 0.02);
                }
                if (r.nextFloat() < 0.4f) {
                    w.addParticle(ParticleTypes.SMOKE, e.bladeExhaust.x, e.bladeExhaust.y, e.bladeExhaust.z, 0, 0.05, 0);
                }
                if (r.nextFloat() < 0.15f) {
                    w.addParticle(ParticleTypes.LAVA, e.bladeExhaust.x, e.bladeExhaust.y, e.bladeExhaust.z, 0, 0, 0);
                }
            } else if (e.isSavage() && e.age % 3 == 0) {
                Vec3d p = e.bladeBase.lerp(e.bladeTip, r.nextDouble());
                w.addParticle(EMBER, p.x, p.y, p.z, 0, 0.015, 0);
            }
            // тонкий дымок из трубок после выстрелов (пока патронов меньше полного)
            if (e.getAmmo() < ThunderTigerEntity.MAX_AMMO && e.age % 9 == 0) {
                w.addParticle(ParticleTypes.SMOKE, e.bladeExhaust.x, e.bladeExhaust.y, e.bladeExhaust.z, 0, 0.02, 0);
            }
        }
        // аура внутренней силы
        if (e.getInner() >= ThunderTigerEntity.INNER_AURA && e.age % 2 == 0) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 0.5 + r.nextDouble() * 0.4;
            w.addParticle(AURA, e.getX() + Math.cos(ang) * rad, e.getY() + 0.1 + r.nextDouble() * 0.3,
                    e.getZ() + Math.sin(ang) * rad, 0, 0.06, 0);
            if (r.nextFloat() < 0.2f) {
                w.addParticle(ParticleTypes.WAX_OFF, e.getX() + Math.cos(ang) * rad, e.getY() + 0.3,
                        e.getZ() + Math.sin(ang) * rad, 0, 0.5, 0);
            }
        }
        // кольца силы: изредка золотые искры за спиной
        if (e.getRings() > 0 && e.age % 5 == 0) {
            float yaw = e.bodyYaw * 0.017453292f;
            double bx = e.getX() + Math.sin(yaw) * 0.45;
            double bz = e.getZ() - Math.cos(yaw) * 0.45;
            w.addParticle(GOLD_S, bx + (r.nextDouble() - 0.5) * 1.3, e.getY() + 1.7 + r.nextDouble() * 0.6,
                    bz + (r.nextDouble() - 0.5) * 1.3, 0, 0.01, 0);
        }
        // слом стойки: «звёзды» над головой
        if (inAction && a == TigerTimings.STAGGER && k > TigerTimings.STAGGER_FALL && k < TigerTimings.STAGGER_RISE
                && e.age % 3 == 0) {
            double ang = e.age * 0.4;
            w.addParticle(ParticleTypes.ENCHANTED_HIT, e.getX() + Math.cos(ang) * 0.45, e.getY() + 2.0,
                    e.getZ() + Math.sin(ang) * 0.45, 0, 0, 0);
        }
        // пыль из‑под ног на рывках и в шквале
        if (inAction && (a == TigerTimings.DASH || a == TigerTimings.FLURRY_A || a == TigerTimings.FLURRY_B
                || a == TigerTimings.DODGE_L || a == TigerTimings.DODGE_R || a == TigerTimings.DODGE_BACK
                || a == TigerTimings.TRIPLE_BLAST && k < TigerTimings.TRIPLE_BLAST_DASH_TO
                || a == TigerTimings.TANGLE && k >= TigerTimings.TANGLE_DASH_FROM && k <= TigerTimings.TANGLE_DASH_TO)
                && e.isOnGround() && k < 12) {
            w.addParticle(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.1, e.getZ(), (r.nextDouble() - 0.5) * 0.05, 0.02,
                    (r.nextDouble() - 0.5) * 0.05);
        }
        // отброшенная сигара (переход в фазу 2)
        if (inAction && a == TigerTimings.PHASE2 && k == TigerTimings.PHASE2_FLICK && e.cigarTip != Vec3d.ZERO) {
            float yaw = e.bodyYaw * 0.017453292f;
            double vx = -Math.cos(yaw) * -0.25 - Math.sin(yaw) * 0.05;
            double vz = -Math.sin(yaw) * -0.25 + Math.cos(yaw) * 0.05;
            for (int i = 0; i < 4; i++) {
                w.addParticle(ParticleTypes.LAVA, e.cigarTip.x, e.cigarTip.y, e.cigarTip.z, vx, 0.2, vz);
            }
            w.addParticle(ParticleTypes.SMOKE, e.cigarTip.x, e.cigarTip.y, e.cigarTip.z, vx * 0.3, 0.05, vz * 0.3);
        }
        if (mc.player == null) {
            return;
        }
    }
}
