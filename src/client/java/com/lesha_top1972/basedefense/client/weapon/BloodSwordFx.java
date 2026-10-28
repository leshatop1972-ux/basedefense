package com.lesha_top1972.basedefense.client.weapon;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Arm;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Частицы меча кровожада: искры крови на клинке во время замаха, капли с готового клинка, кровавый след
 * за клинком при ударе и сам «Кровавый разруб» (разлом по земле, всплески, вертикальный след удара).
 */
public final class BloodSwordFx {

    private static final float DEG = (float) (Math.PI / 180.0);
    private static final float BLADE_FROM = 7f;      // px модели меча от кисти: начало клинка …
    private static final float BLADE_TO = 26f;       // … и острие (без запаса)

    private static final DustParticleEffect EMBER = new DustParticleEffect(new Vector3f(0.9f, 0.08f, 0.1f), 0.7f);
    private static final DustParticleEffect CLOT = new DustParticleEffect(new Vector3f(0.35f, 0.0f, 0.04f), 2.0f);
    private static final DustColorTransitionParticleEffect TRAIL = new DustColorTransitionParticleEffect(
            new Vector3f(1.0f, 0.22f, 0.2f), new Vector3f(0.28f, 0.0f, 0.05f), 0.9f);
    private static final DustColorTransitionParticleEffect TRAIL_TIP = new DustColorTransitionParticleEffect(
            new Vector3f(1.0f, 0.62f, 0.55f), new Vector3f(0.5f, 0.02f, 0.06f), 1.0f);
    private static final DustColorTransitionParticleEffect ARC = new DustColorTransitionParticleEffect(
            new Vector3f(1.0f, 0.18f, 0.16f), new Vector3f(0.22f, 0.0f, 0.04f), 1.0f);
    private static final BlockStateParticleEffect DROPS =
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
    private static final BlockStateParticleEffect GORE =
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.getDefaultState());

    private static final List<Task> TASKS = new ArrayList<>();
    private static final float[] TP_KEYS = new float[11];
    private static final float[] FP_KEYS = new float[13];

    private record Task(int at, Runnable run) {
    }

    private static int clock;

    private BloodSwordFx() {
    }

    static void tick() {
        clock++;
        if (TASKS.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.at <= clock) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            t.run.run();
        }
    }

    private static void later(int ticks, Runnable r) {
        if (ticks <= 0) {
            r.run();
        } else {
            TASKS.add(new Task(clock + ticks, r));
        }
    }

    // =====================================================================
    // Частицы во время анимации
    // =====================================================================
    static void animTick(MinecraftClient client, LivingEntity e, BloodSwordAnim a, float now) {
        ClientWorld w = client.world;
        if (w == null) {
            return;
        }
        Random r = w.random;
        boolean firstPerson = e == client.player && client.options.getPerspective().isFirstPerson();
        if (firstPerson) {
            return;     // у самой камеры частицы огромные и закрывают обзор — от 1‑го лица хватает свечения клинка
        }
        if (a.phase == BloodSwordAnim.CHARGE) {
            float p = a.charge(now);
            int n = r.nextFloat() < 0.3f + 0.7f * p ? 1 : 0;
            if (a.ready(now)) {
                n += 1;
                if (!a.readyFx) {                             // меч готов: вспышка искр вдоль клинка
                    a.readyFx = true;
                    for (int i = 0; i < 12; i++) {
                        Vector3f q = blade(client, e, a, now, BLADE_FROM + (BLADE_TO - BLADE_FROM) * i / 11f, firstPerson);
                        w.addParticle(TRAIL_TIP, q.x, q.y, q.z, 0, 0, 0);
                    }
                }
                if (r.nextInt(3) == 0) {                      // капли крови с клинка
                    Vector3f q = blade(client, e, a, now, BLADE_FROM + r.nextFloat() * (BLADE_TO - BLADE_FROM), firstPerson);
                    w.addParticle(DROPS, q.x, q.y, q.z, 0, 0, 0);
                }
            }
            for (int i = 0; i < n; i++) {
                Vector3f q = blade(client, e, a, now, BLADE_FROM + r.nextFloat() * (BLADE_TO - BLADE_FROM), firstPerson);
                w.addParticle(EMBER, q.x + r.nextGaussian() * 0.05, q.y + r.nextGaussian() * 0.05,
                        q.z + r.nextGaussian() * 0.05, 0, 0.02, 0);
            }
        } else if (a.phase == BloodSwordAnim.SLAM) {
            float s = a.age(now);
            if (s > 0.2f && s < 3.8f) {                       // кровавый след за клинком
                for (float back : new float[]{0.5f, 0f}) {
                    for (int i = 0; i < 6; i++) {
                        float along = 11f + (BLADE_TO - 11f) * i / 5f;
                        Vector3f q = blade(client, e, a, now - back, along, firstPerson);
                        w.addParticle(i == 5 ? TRAIL_TIP : TRAIL, q.x, q.y, q.z, 0, 0, 0);
                    }
                }
            }
        }
    }

    /** Точка клинка (along — px модели меча от кисти) в мире: по позе 3‑го лица или, у себя, — 1‑го. */
    private static Vector3f blade(MinecraftClient client, LivingEntity e, BloodSwordAnim a, float t, float along,
                                  boolean firstPerson) {
        int m = e.getMainArm() == Arm.RIGHT ? 1 : -1;
        BloodSwordAnim at = a;
        if (firstPerson) {
            BloodSwordPose.FP fp = BloodSwordPose.solveFirstPerson(at.firstPerson(t, FP_KEYS), m, new BloodSwordPose.FP());
            Vector3f p = fp.sword.axisY(new Vector3f()).mul(along * BloodSwordKeys.SCALE_1P / 16f).add(fp.sword.p);
            Camera cam = client.gameRenderer.getCamera();
            new Quaternionf().rotationY(-(cam.getYaw() + 180f) * DEG).rotateX(-cam.getPitch() * DEG).transform(p);
            return p.add((float) cam.getPos().x, (float) cam.getPos().y, (float) cam.getPos().z);
        }
        BloodSwordPose.TP tp = BloodSwordPose.solveThirdPerson(at.thirdPerson(t, TP_KEYS), m, 12f, 0f, 0f,
                new BloodSwordPose.TP());
        Vector3f b = tp.sword.getColumn(1, new Vector3f());
        Vector3f p = b.mul(along * BloodSwordKeys.SCALE_3P).add(tp.handD);   // px модели игрока
        // как LivingEntityRenderer: поворот 180° − bodyYaw, масштаб (−1, −1, 1), сдвиг на 1.501
        float bx = -p.x / 16f;
        float by = 1.501f - p.y / 16f;
        float bz = p.z / 16f;
        float th = (180f - e.bodyYaw) * DEG;
        float c = (float) Math.cos(th);
        float sn = (float) Math.sin(th);
        return new Vector3f((float) e.getX() + bx * c + bz * sn, (float) e.getY() + by, (float) e.getZ() - bx * sn + bz * c);
    }

    // =====================================================================
    // «Кровавый разруб» (пакет Fx.BLOOD_CLEAVE)
    // =====================================================================
    /** (x, y, z) — точка удара на земле, (fx, fz) — направление удара, reach — длина разлома. */
    public static void cleave(ClientWorld w, double x, double y, double z, float fx, float fz, float reach, int hits,
                              ParticleEffect blockFx) {
        Random r = w.random;
        double len = Math.sqrt(fx * fx + fz * fz);
        if (len < 1e-4) {
            return;
        }
        double ux = fx / len;
        double uz = fz / len;
        double sx = -uz;
        double sz = ux;
        double ox = x - ux * 1.6;          // где стоит игрок
        double oz = z - uz * 1.6;
        // след удара в воздухе: дуга сверху‑сзади до земли впереди
        double cy = y + 1.45;
        for (int i = 0; i <= 26; i++) {
            double ang = (70 - 110 * i / 26.0) * DEG;
            double up = Math.sin(ang) * 2.1;
            double fw = Math.cos(ang) * 2.1;
            if (cy + up < y + 0.05) {
                continue;
            }
            w.addParticle(ARC, ox + ux * fw + sx * (r.nextFloat() - 0.5) * 0.1, cy + up, oz + uz * fw + sz * (r.nextFloat() - 0.5) * 0.1,
                    0, 0, 0);
        }
        w.addParticle(ParticleTypes.SWEEP_ATTACK, x, y + 0.5, z, 0, 0, 0);
        // удар в землю: комья земли и брызги крови кольцом
        for (int i = 0; i < 26; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double sp = 0.12 + r.nextDouble() * 0.2;
            if (blockFx != null) {
                w.addParticle(blockFx, x, y + 0.1, z, Math.cos(a) * sp, 0.15 + r.nextDouble() * 0.25, Math.sin(a) * sp);
            }
            if (i % 2 == 0) {
                w.addParticle(DROPS, x, y + 0.2, z, Math.cos(a) * sp * 0.8, 0.25 + r.nextDouble() * 0.3, Math.sin(a) * sp * 0.8);
            }
        }
        // разлом бежит вперёд за 3 тика: пыль земли, тёмная кровь в трещине, кровавые всплески
        int steps = (int) Math.ceil((reach + 0.2) / 0.3);
        for (int k = 0; k <= steps; k++) {
            double f = 0.4 + k * 0.3;
            final double px = ox + ux * f;
            final double pz = oz + uz * f;
            final boolean geyser = k % 3 == 1;
            later((int) (f / reach * 3), () -> {
                double jitter = (r.nextDouble() - 0.5) * 0.25;
                double qx = px + sx * jitter;
                double qz = pz + sz * jitter;
                w.addParticle(CLOT, qx, y + 0.06, qz, 0, 0, 0);
                if (blockFx != null) {
                    for (int i = 0; i < 2; i++) {
                        w.addParticle(blockFx, qx, y + 0.1, qz, sx * (r.nextDouble() - 0.5) * 0.2,
                                0.12 + r.nextDouble() * 0.2, sz * (r.nextDouble() - 0.5) * 0.2);
                    }
                }
                if (geyser) {
                    for (int i = 0; i < 6; i++) {
                        float sz2 = 1.9f - i * 0.2f;
                        w.addParticle(new DustParticleEffect(new Vector3f(0.75f - i * 0.06f, 0.03f, 0.07f), sz2),
                                qx + (r.nextDouble() - 0.5) * 0.15, y + 0.15 + i * 0.18, qz + (r.nextDouble() - 0.5) * 0.15,
                                0, 0, 0);
                    }
                    for (int i = 0; i < 3; i++) {
                        w.addParticle(i == 0 ? GORE : DROPS, qx, y + 0.2, qz, (r.nextDouble() - 0.5) * 0.12,
                                0.3 + r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.12);
                    }
                }
            });
        }
        // края удара: по блоку в стороны
        for (int k = 1; k <= 3; k++) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double px = ox + ux * k + sx * 1.4 * sgn;
                double pz = oz + uz * k + sz * 1.4 * sgn;
                later(k, () -> w.addParticle(EMBER, px, y + 0.15, pz, 0, 0.03, 0));
            }
        }
        if (hits > 0) {
            for (int i = 0; i < 10 + 4 * hits; i++) {
                w.addParticle(DROPS, x + r.nextGaussian() * 0.4, y + 0.6 + r.nextDouble() * 0.6, z + r.nextGaussian() * 0.4,
                        r.nextGaussian() * 0.1, 0.2 + r.nextDouble() * 0.2, r.nextGaussian() * 0.1);
            }
        }
    }
}
