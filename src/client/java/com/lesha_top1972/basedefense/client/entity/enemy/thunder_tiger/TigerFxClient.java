package com.lesha_top1972.basedefense.client.entity.enemy.thunder_tiger;

import com.lesha_top1972.basedefense.fx.Fx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

/**
 * Эффекты «Громового тигра» по пакетам {@link Fx} (типы TIGER_*): выстрел из выхлопов с гильзой, взрыв патрона,
 * огненная волна, разрыв дрожи, слом стойки, след рывка, вспышка колец, гильзы при перезарядке, дуга разреза,
 * огонь по земле, парирование. Частиц немного и они не летят в камеру — драку видно.
 */
public final class TigerFxClient {

    private static final ItemStackParticleEffect SHELL = new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET));
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0f, 0.82f, 0.35f), 1.0f);
    private static final DustParticleEffect GOLD_BIG = new DustParticleEffect(new Vector3f(1.0f, 0.86f, 0.4f), 1.6f);
    private static final DustParticleEffect ORANGE = new DustParticleEffect(new Vector3f(1.0f, 0.5f, 0.12f), 1.0f);
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(0.9f, 0.15f, 0.08f), 1.1f);
    private static final DustParticleEffect SOOT = new DustParticleEffect(new Vector3f(0.12f, 0.1f, 0.1f), 1.8f);
    private static final DustParticleEffect TREMOR = new DustParticleEffect(new Vector3f(0.85f, 0.6f, 0.3f), 0.9f);
    private static final DustParticleEffect WHITE = new DustParticleEffect(new Vector3f(1.0f, 1.0f, 1.0f), 0.7f);

    private TigerFxClient() {
    }

    /** Не рисовать частицу вплотную к камере (чтобы не закрывала обзор). */
    private static boolean nearCamera(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d c = mc.gameRenderer.getCamera().getPos();
        return c.squaredDistanceTo(x, y, z) < 1.2;
    }

    private static void add(ClientWorld w, ParticleEffect e, double x, double y, double z, double vx, double vy, double vz) {
        if (!nearCamera(x, y, z)) {
            w.addParticle(e, x, y, z, vx, vy, vz);
        }
    }

    public static void play(ClientWorld w, int type, double x, double y, double z, float x2, float y2, float z2, float a,
                            float b, int color, ParticleEffect blockFx, int flags) {
        Random r = w.random;
        boolean loud = (flags & Fx.F_LOUD) != 0;
        switch (type) {
            case Fx.TIGER_SHOT -> {
                // пламя из трубок в сторону x2..z2, дым, гильза вылетает вбок‑вверх
                Vec3d d = new Vec3d(x2, y2, z2);
                if (d.lengthSquared() > 1e-4) {
                    d = d.normalize();
                }
                for (int i = 0; i < 10; i++) {
                    double s = 0.12 + r.nextDouble() * 0.2;
                    add(w, i % 3 == 0 ? ParticleTypes.LAVA : (loud ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME),
                            x, y, z, d.x * s + (r.nextDouble() - 0.5) * 0.06, d.y * s + 0.03, d.z * s + (r.nextDouble() - 0.5) * 0.06);
                }
                for (int i = 0; i < 4; i++) {
                    add(w, ParticleTypes.SMOKE, x, y, z, d.x * 0.05, 0.04, d.z * 0.05);
                }
                add(w, SHELL, x, y + 0.1, z, (r.nextDouble() - 0.5) * 0.15, 0.25, (r.nextDouble() - 0.5) * 0.15);
            }
            case Fx.TIGER_BLAST -> {
                if (loud) {
                    add(w, ParticleTypes.FLASH, x, y, z, 0, 0, 0);
                }
                int n = loud ? 26 : 16;
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double sp = 0.08 + r.nextDouble() * (loud ? 0.22 : 0.15);
                    add(w, i % 4 == 0 ? ParticleTypes.LAVA : (loud && i % 2 == 0 ? RED : ParticleTypes.FLAME),
                            x, y + 0.2, z, Math.cos(ang) * sp, 0.05 + r.nextDouble() * 0.15, Math.sin(ang) * sp);
                }
                for (int i = 0; i < 6; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    add(w, ParticleTypes.LARGE_SMOKE, x + Math.cos(ang) * a * 0.3, y + 0.3, z + Math.sin(ang) * a * 0.3,
                            0, 0.03, 0);
                }
                if (blockFx != null) {
                    for (int i = 0; i < 10; i++) {
                        double ang = r.nextDouble() * Math.PI * 2;
                        add(w, blockFx, x, y + 0.1, z, Math.cos(ang) * 0.2, 0.25, Math.sin(ang) * 0.2);
                    }
                }
            }
            case Fx.TIGER_WAVE -> {
                // кольцо огня по земле радиусом a
                int pts = Math.max(12, (int) (a * 7));
                for (int i = 0; i < pts; i++) {
                    double ang = i * Math.PI * 2 / pts + r.nextDouble() * 0.1;
                    double px = x + Math.cos(ang) * a;
                    double pz = z + Math.sin(ang) * a;
                    add(w, i % 5 == 0 ? ParticleTypes.LAVA : (loud && i % 2 == 0 ? RED : ParticleTypes.FLAME),
                            px, y + 0.15, pz, Math.cos(ang) * 0.04, 0.08 + r.nextDouble() * 0.06, Math.sin(ang) * 0.04);
                    if (i % 4 == 0 && blockFx != null) {
                        add(w, blockFx, px, y + 0.1, pz, 0, 0.15, 0);
                    }
                }
            }
            case Fx.TIGER_BURST -> {
                // вертикальные «волны» дрожи вокруг цели, удар
                for (int ring = 0; ring < 3; ring++) {
                    double rr = 0.5 + ring * 0.35;
                    double yy = y - 0.6 + ring * 0.5;
                    for (int i = 0; i < 14; i++) {
                        double ang = i * Math.PI * 2 / 14;
                        add(w, loud ? ORANGE : TREMOR, x + Math.cos(ang) * rr, yy, z + Math.sin(ang) * rr,
                                Math.cos(ang) * 0.06, 0, Math.sin(ang) * 0.06);
                    }
                }
                add(w, ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
                if (loud) {
                    for (int i = 0; i < 10; i++) {
                        add(w, ParticleTypes.FLAME, x, y, z, (r.nextDouble() - 0.5) * 0.25, r.nextDouble() * 0.2,
                                (r.nextDouble() - 0.5) * 0.25);
                    }
                }
            }
            case Fx.TIGER_BREAK -> {
                for (int i = 0; i < 30; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double sp = 0.15 + r.nextDouble() * 0.3;
                    add(w, i % 3 == 0 ? GOLD_BIG : (i % 3 == 1 ? ParticleTypes.CRIT : ParticleTypes.ELECTRIC_SPARK),
                            x, y, z, Math.cos(ang) * sp, (r.nextDouble() - 0.3) * 0.3, Math.sin(ang) * sp);
                }
                for (int i = 0; i < 20; i++) {
                    double ang = i * Math.PI * 2 / 20;
                    add(w, ParticleTypes.CLOUD, x + Math.cos(ang) * 0.6, y - 1.3, z + Math.sin(ang) * 0.6,
                            Math.cos(ang) * 0.12, 0.01, Math.sin(ang) * 0.12);
                    if (blockFx != null && i % 2 == 0) {
                        add(w, blockFx, x + Math.cos(ang) * 0.8, y - 1.3, z + Math.sin(ang) * 0.8, 0, 0.1, 0);
                    }
                }
            }
            case Fx.TIGER_TRAIL -> {
                // тёмный след рывка: силуэт из сажи и угольков вдоль пути
                Vec3d p0 = new Vec3d(x, y, z);
                Vec3d p1 = new Vec3d(x2, y2, z2);
                int n = (int) MathHelper.clamp(p0.distanceTo(p1) * 4, 4, 40);
                for (int i = 0; i < n; i++) {
                    Vec3d p = p0.lerp(p1, i / (double) n);
                    double h = r.nextDouble() * 1.8 - 0.9;
                    add(w, SOOT, p.x + (r.nextDouble() - 0.5) * 0.3, p.y + h, p.z + (r.nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
                    if (i % 3 == 0) {
                        add(w, ParticleTypes.SMALL_FLAME, p.x, p.y + h * 0.5, p.z, 0, 0.01, 0);
                    }
                }
            }
            case Fx.TIGER_RINGS -> {
                // золотая вспышка кольцом + столб света
                for (int ring = 0; ring < 2; ring++) {
                    int pts = 40;
                    for (int i = 0; i < pts; i++) {
                        double ang = i * Math.PI * 2 / pts;
                        double sp = 0.35 + ring * 0.2;
                        add(w, ring == 0 ? GOLD_BIG : GOLD, x, y + 0.3 + ring * 0.8, z, Math.cos(ang) * sp, 0.02,
                                Math.sin(ang) * sp);
                    }
                }
                for (int i = 0; i < 24; i++) {
                    add(w, ParticleTypes.END_ROD, x + (r.nextDouble() - 0.5) * 0.6, y + r.nextDouble() * 3.0,
                            z + (r.nextDouble() - 0.5) * 0.6, 0, 0.12 + r.nextDouble() * 0.1, 0);
                }
                add(w, ParticleTypes.FLASH, x, y + 1.5, z, 0, 0, 0);
                for (int i = 0; i < 16; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    add(w, ParticleTypes.FLAME, x, y + 0.2, z, Math.cos(ang) * 0.3, 0.05, Math.sin(ang) * 0.3);
                }
            }
            case Fx.TIGER_SHELLS -> {
                int n = Math.max(1, (int) b);
                for (int i = 0; i < n; i++) {
                    add(w, SHELL, x, y, z, (r.nextDouble() - 0.5) * 0.12, 0.12 + r.nextDouble() * 0.1,
                            (r.nextDouble() - 0.5) * 0.12);
                }
                add(w, ParticleTypes.SMOKE, x, y, z, 0, 0.03, 0);
            }
            case Fx.TIGER_SLASH -> {
                // дуга перед тигром: yaw = x2, наклон y2 (рад), направление z2
                float yaw = x2 * MathHelper.RADIANS_PER_DEGREE;
                double tilt = y2;
                int dir = z2 >= 0 ? 1 : -1;
                int steps = 16;
                for (int i = 0; i <= steps; i++) {
                    double u = (i / (double) steps - 0.5) * 2.0 * dir;         // −1..1 по ходу удара
                    double ang = u * 1.25;                                     // ±72°
                    // точка дуги в системе тигра: вперёд cos, вбок sin; наклон — поднимает дугу
                    double f = Math.cos(ang) * a * 0.8;
                    double s = Math.sin(ang) * a * 0.8;
                    double up = Math.sin(tilt) * f * 0.6 - s * Math.sin(tilt) * 0.0;
                    double fw = Math.cos(tilt) * f;
                    double px = x - MathHelper.sin(yaw) * fw - MathHelper.cos(yaw) * s;
                    double pz = z + MathHelper.cos(yaw) * fw - MathHelper.sin(yaw) * s;
                    double py = y - up * 0.9 - (tilt > 1.0 ? (1 - Math.abs(u)) * 0.0 : 0);
                    add(w, loud ? (i % 2 == 0 ? ParticleTypes.FLAME : ORANGE) : WHITE, px, py, pz, 0, 0, 0);
                    if (i % 4 == 0) {
                        add(w, ParticleTypes.CRIT, px, py, pz, 0, 0, 0);
                    }
                }
                add(w, ParticleTypes.SWEEP_ATTACK, x - MathHelper.sin(yaw) * a * 0.6, y, z + MathHelper.cos(yaw) * a * 0.6,
                        0, 0, 0);
            }
            case Fx.TIGER_FIRE -> {
                for (int i = 0; i < 26; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double rr = Math.sqrt(r.nextDouble()) * a;
                    add(w, i % 5 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, x + Math.cos(ang) * rr, y + 0.1,
                            z + Math.sin(ang) * rr, 0, 0.04 + r.nextDouble() * 0.05, 0);
                }
                for (int i = 0; i < 5; i++) {
                    add(w, ParticleTypes.LARGE_SMOKE, x + (r.nextDouble() - 0.5) * a, y + 0.3, z + (r.nextDouble() - 0.5) * a,
                            0, 0.04, 0);
                }
            }
            case Fx.TIGER_PARRY -> {
                for (int i = 0; i < 14; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double sp = 0.2 + r.nextDouble() * 0.25;
                    add(w, i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.CRIT, x, y, z, Math.cos(ang) * sp,
                            (r.nextDouble() - 0.3) * 0.3, Math.sin(ang) * sp);
                }
                add(w, ParticleTypes.FLASH, x, y, z, 0, 0, 0);
            }
            default -> {
            }
        }
    }
}
