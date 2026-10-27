package com.lesha_top1972.basedefense.client.fx;

import com.lesha_top1972.basedefense.fx.Fx;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.joml.Vector3f;

/**
 * Клиентская сторона {@link Fx}: по одному пакету рисует кольца, лучи, линии, выбросы, столбы дыма
 * и фронт землетрясения. Разброс и скорость частиц — как у серверного spawnParticles (по Гауссу).
 */
public final class FxClient {

    private FxClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Fx.PACKET, (client, handler, buf, sender) -> {
            int type = buf.readByte();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float x2 = buf.readFloat();
            float y2 = buf.readFloat();
            float z2 = buf.readFloat();
            float a = buf.readFloat();
            float b = buf.readFloat();
            int color = buf.readInt();
            int block = buf.readVarInt();
            int flags = buf.readVarInt();
            client.execute(() -> play(type, x, y, z, x2, y2, z2, a, b, color, block, flags));
        });
    }

    private static final BlockStateParticleEffect DROPS =
            new BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.REDSTONE_BLOCK.getDefaultState());
    private static final BlockStateParticleEffect CLOTS =
            new BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.NETHER_WART_BLOCK.getDefaultState());
    private static final DustParticleEffect MIST = new DustParticleEffect(new Vector3f(0.5f, 0.02f, 0.06f), 1.6f);

    /** Брызги крови — как BloodFx.splash. */
    static void blood(ClientWorld w, double x, double y, double z, int count, double spread) {
        spawn(w, DROPS, x, y, z, count, spread, spread * 0.6, spread, 0.2);
        spawn(w, CLOTS, x, y, z, Math.max(1, count / 3), spread, spread * 0.5, spread, 0.15);
        spawn(w, MIST, x, y, z, Math.max(1, count / 2), spread, spread * 0.6, spread, 0.0);
    }

    private static DustParticleEffect dust(int color) {
        float size = ((color >>> 24) & 0xFF) / 10f;
        return new DustParticleEffect(new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f), size <= 0 ? 1f : size);
    }

    /** Как ServerWorld.spawnParticles: count штук с разбросом d* по Гауссу и скоростью speed. */
    static void spawn(ClientWorld w, ParticleEffect e, double x, double y, double z, int count, double dx, double dy,
                      double dz, double speed) {
        Random r = w.random;
        if (count <= 0) {
            w.addParticle(e, x, y, z, dx * speed, dy * speed, dz * speed);
            return;
        }
        for (int i = 0; i < count; i++) {
            w.addParticle(e, x + r.nextGaussian() * dx, y + r.nextGaussian() * dy, z + r.nextGaussian() * dz,
                    r.nextGaussian() * speed, r.nextGaussian() * speed, r.nextGaussian() * speed);
        }
    }

    private static final DustParticleEffect SOIL = new DustParticleEffect(new Vector3f(0.36f, 0.27f, 0.18f), 1.4f);
    private static final DustParticleEffect SOOT = new DustParticleEffect(new Vector3f(0.16f, 0.14f, 0.13f), 2.2f);
    private static final DustParticleEffect GLOW = new DustParticleEffect(new Vector3f(1.0f, 0.62f, 0.2f), 0.9f);

    /** Взрыв подкопщика: своя вспышка вместо ванильного огромного взрыва. */
    static void blast(ClientWorld w, double x, double y, double z, double r, boolean big, ParticleEffect blockFx) {
        Random rnd = w.random;
        w.addParticle(ParticleTypes.FLASH, x, y + 0.5, z, 0, 0, 0);
        spawn(w, ParticleTypes.EXPLOSION, x, y + 0.5, z, big ? 3 : 1, r * 0.2, 0.3, r * 0.2, 0);
        // облако пыли и дыма
        int clouds = big ? 26 : 14;
        for (int i = 0; i < clouds; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double d = rnd.nextDouble() * r * 0.6;
            w.addParticle(i % 3 == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE,
                    x + Math.cos(ang) * d, y + 0.3 + rnd.nextDouble() * 0.8, z + Math.sin(ang) * d,
                    Math.cos(ang) * 0.04, 0.02 + rnd.nextDouble() * (big ? 0.07 : 0.04), Math.sin(ang) * 0.04);
        }
        spawn(w, SOOT, x, y + 0.6, z, big ? 18 : 10, r * 0.35, 0.4, r * 0.35, 0.02);
        // осколки блоков и куски стены
        if (blockFx != null) {
            int n = big ? 60 : 28;
            for (int i = 0; i < n; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double sp = (big ? 0.25 : 0.15) + rnd.nextDouble() * (big ? 0.35 : 0.2);
                w.addParticle(blockFx, x, y + 0.4 + rnd.nextDouble() * (big ? 1.2 : 0.4), z,
                        Math.cos(ang) * sp, 0.15 + rnd.nextDouble() * 0.4, Math.sin(ang) * sp);
            }
        }
        // искры
        int sparks = big ? 22 : 12;
        for (int i = 0; i < sparks; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double sp = 0.15 + rnd.nextDouble() * 0.3;
            w.addParticle(i % 2 == 0 ? ParticleTypes.LAVA : GLOW, x, y + 0.5, z,
                    Math.cos(ang) * sp, 0.1 + rnd.nextDouble() * 0.3, Math.sin(ang) * sp);
        }
        // ударная волна по земле
        int pts = (int) (r * (big ? 14 : 10));
        for (int i = 0; i < pts; i++) {
            double ang = i * Math.PI * 2 / pts;
            double px = x + Math.cos(ang) * r * 0.5;
            double pz = z + Math.sin(ang) * r * 0.5;
            w.addParticle(ParticleTypes.CLOUD, px, y + 0.15, pz, Math.cos(ang) * 0.22, 0.01, Math.sin(ang) * 0.22);
            if (blockFx != null && i % 2 == 0) {
                w.addParticle(blockFx, px, y + 0.1, pz, Math.cos(ang) * 0.3, 0.05, Math.sin(ang) * 0.3);
            }
        }
    }

    private static final DustParticleEffect PETAL = new DustParticleEffect(new Vector3f(0.55f, 0.03f, 0.07f), 2.2f);
    private static final DustParticleEffect PETAL_DARK = new DustParticleEffect(new Vector3f(0.22f, 0.0f, 0.03f), 2.6f);
    private static final DustParticleEffect SPARK_RED = new DustParticleEffect(new Vector3f(1.0f, 0.2f, 0.18f), 1.0f);

    /** Кровожад вырывается из кокона: вспышка, ошмётки кокона веером, брызги вверх, ударная волна по земле. */
    static void cocoonBurst(ClientWorld w, double x, double y, double z, double r, ParticleEffect blockFx) {
        Random rnd = w.random;
        w.addParticle(ParticleTypes.FLASH, x, y + 1.6, z, 0, 0, 0);
        // ошмётки кокона — лепестки летят во все стороны от тела
        for (int i = 0; i < 70; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double el = (rnd.nextDouble() - 0.3) * 1.2;
            double sp = 0.25 + rnd.nextDouble() * 0.35;
            double h = 0.6 + rnd.nextDouble() * 2.2;
            w.addParticle(i % 3 == 0 ? PETAL_DARK : PETAL, x + Math.cos(ang) * 0.8, y + h, z + Math.sin(ang) * 0.8,
                    Math.cos(ang) * sp * Math.cos(el), Math.sin(el) * sp, Math.sin(ang) * sp * Math.cos(el));
        }
        // брызги крови фонтаном вверх и в стороны
        for (int i = 0; i < 50; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double sp = 0.15 + rnd.nextDouble() * 0.35;
            w.addParticle(i % 3 == 0 ? CLOTS : DROPS, x, y + 1.2 + rnd.nextDouble() * 1.2, z,
                    Math.cos(ang) * sp, 0.25 + rnd.nextDouble() * 0.45, Math.sin(ang) * sp);
        }
        spawn(w, MIST, x, y + 1.5, z, 30, 0.9, 0.9, 0.9, 0.0);
        // яркие искры‑капли
        for (int i = 0; i < 18; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            w.addParticle(SPARK_RED, x, y + 1.5, z, Math.cos(ang) * 0.5, (rnd.nextDouble() - 0.3) * 0.4, Math.sin(ang) * 0.5);
        }
        // ударная волна: два кольца по земле расходятся (облака + пыль блока + кровавая взвесь)
        for (int k = 0; k < 2; k++) {
            int pts = (int) (r * 8);
            double sp = k == 0 ? 0.55 : 0.35;
            for (int i = 0; i < pts; i++) {
                double ang = (i + k * 0.5) * Math.PI * 2 / pts;
                double px = x + Math.cos(ang) * 0.9;
                double pz = z + Math.sin(ang) * 0.9;
                w.addParticle(ParticleTypes.CLOUD, px, y + 0.15, pz, Math.cos(ang) * sp, 0.01, Math.sin(ang) * sp);
                if (i % 2 == 0) {
                    w.addParticle(PETAL, px, y + 0.3, pz, Math.cos(ang) * sp * 0.9, 0.02, Math.sin(ang) * sp * 0.9);
                }
                if (blockFx != null && i % 3 == 0) {
                    w.addParticle(blockFx, px, y + 0.1, pz, Math.cos(ang) * sp, 0.08, Math.sin(ang) * sp);
                }
            }
        }
    }

    private static void play(int type, double x, double y, double z, float x2, float y2, float z2, float a, float b,
                             int color, int block, int flags) {
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) {
            return;
        }
        BlockState st = block != 0 ? Block.getStateFromRawId(block) : null;
        ParticleEffect blockFx = st != null && !st.isAir() ? new BlockStateParticleEffect(ParticleTypes.BLOCK, st) : null;
        switch (type) {
            case Fx.RING -> {
                int pts = Math.max(1, (int) b);
                for (int i = 0; i < pts; i++) {
                    double ang = i / (double) pts * Math.PI * 2;
                    double px = x + Math.cos(ang) * a;
                    double pz = z + Math.sin(ang) * a;
                    if ((flags & Fx.F_BLOCK) != 0 && blockFx != null) {
                        spawn(w, blockFx, px, y + 0.1, pz, 2, 0.15, 0.05, 0.15, 0.12);
                    }
                    if ((flags & Fx.F_CLOUD) != 0 && i % 2 == 0) {
                        spawn(w, ParticleTypes.CLOUD, px, y + 0.15, pz, 1, 0, 0.03, 0, 0.03);
                    }
                    if ((flags & Fx.F_SOUL) != 0) {
                        spawn(w, ParticleTypes.SOUL_FIRE_FLAME, px, y + 0.3, pz, 1, 0, 0.05, 0, 0.01);
                    }
                    if ((flags & Fx.F_DUST) != 0 && ((flags & Fx.F_BLOCK) == 0 || i % 3 == 0)) {
                        w.addParticle(dust(color), px, y + 0.25, pz, 0, 0.04, 0);
                    }
                    if ((flags & Fx.F_SWEEP) != 0) {
                        w.addParticle(ParticleTypes.SWEEP_ATTACK, px, y, pz, 0, 0, 0);
                    }
                }
            }
            case Fx.BLOOD -> blood(w, x, y, z, Math.max(1, (int) b), a);
            case Fx.ARC -> {
                float yaw = Float.intBitsToFloat(color) * MathHelper.RADIANS_PER_DEGREE;
                int steps = Math.max(1, (int) b);
                for (int i = 0; i <= steps; i++) {
                    double ang = x2 + (y2 - x2) * i / steps;
                    double px = x - Math.sin(yaw - ang) * a;
                    double pz = z + Math.cos(yaw - ang) * a;
                    if ((flags & Fx.F_BLOOD) != 0) {
                        blood(w, px, y + z2, pz, 4, 0.2);
                    }
                    if ((flags & Fx.F_SWEEP) != 0 && i % 2 == 0) {
                        w.addParticle(ParticleTypes.SWEEP_ATTACK, px, y + z2, pz, 0, 0, 0);
                    }
                }
            }
            case Fx.GEYSER_RING -> {
                for (double rr = 1.5; rr <= a; rr += 1.5) {
                    int n = (int) (rr * 4);
                    for (int i = 0; i < n; i++) {
                        double ang = i * Math.PI * 2 / n + rr;
                        blood(w, x + Math.cos(ang) * rr, y + 0.4 + rr * 0.15, z + Math.sin(ang) * rr, 6, 0.2);
                    }
                }
            }
            case Fx.RAYS -> {
                int rays = Math.max(1, (int) b);
                double step = y2 > 0.05 ? y2 : 0.6;
                for (int k = 0; k < rays; k++) {
                    double ang = x2 + k * Math.PI * 2 / rays;
                    for (double d = 0.8; d <= a; d += step) {
                        if (blockFx != null) {
                            spawn(w, blockFx, x + Math.cos(ang) * d, y + 0.1, z + Math.sin(ang) * d, 3, 0.1, 0.05, 0.1, 0.1);
                        }
                    }
                }
            }
            case Fx.LINE -> {
                int pts = Math.max(2, (int) b);
                for (int i = 0; i < pts; i++) {
                    double t = i / (double) (pts - 1);
                    double px = MathHelper.lerp(t, x, x2);
                    double py = MathHelper.lerp(t, y, y2);
                    double pz = MathHelper.lerp(t, z, z2);
                    if ((flags & Fx.F_DUST) != 0) {
                        spawn(w, dust(color), px, py, pz, 1, 0.05, 0.05, 0.05, 0);
                    }
                    if ((flags & Fx.F_SWEEP) != 0) {
                        w.addParticle(ParticleTypes.SWEEP_ATTACK, px, py, pz, 0, 0, 0);
                    }
                    if ((flags & Fx.F_CRIT) != 0) {
                        spawn(w, ParticleTypes.CRIT, px, py, pz, 4, 0.3, 0.3, 0.3, 0.2);
                    }
                    if ((flags & Fx.F_BLOCK) != 0 && blockFx != null) {
                        int gy = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(px), MathHelper.floor(pz));
                        spawn(w, blockFx, px, gy + 0.1, pz, 5, 0.2, 0.05, 0.2, 0.12);
                    }
                    if ((flags & Fx.F_BLOOD) != 0) {
                        blood(w, px, py, pz, 3, 0.25);
                    }
                }
            }
            case Fx.ERUPTION -> {
                if (blockFx != null) {
                    spawn(w, blockFx, x, y + 0.2, z, 7, 0.3, 0.2, 0.3, 0.25);
                }
                spawn(w, dust(color), x, y + 0.4, z, 3, 0.2, 0.5, 0.2, 0.06);
                spawn(w, ParticleTypes.FLAME, x, y + 0.3, z, 2, 0.15, 0.3, 0.15, 0.04);
                if ((flags & Fx.F_LOUD) != 0) {
                    spawn(w, ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 2, 0.2, 0.3, 0.2, 0.02);
                }
            }
            case Fx.COLUMN -> {
                for (int i = 0; i < 10; i++) {
                    double py = y + i * (a / 10.0);
                    spawn(w, ParticleTypes.LARGE_SMOKE, x, py, z, 1, 0.12, 0.2, 0.12, 0.005);
                    if (i % 2 == 0) {
                        spawn(w, dust(color), x, py, z, 1, 0.2, 0.2, 0.2, 0);
                    }
                }
                spawn(w, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.2, z, 3, 0.3, 0.05, 0.3, 0.01);
            }
            case Fx.QUAKE_FRONT -> {
                int pts = (int) Math.max(12, a * 6);
                Random r = w.random;
                for (int i = 0; i < pts; i++) {
                    double ang = (i + r.nextDouble() * 0.5) / pts * Math.PI * 2;
                    int bx = MathHelper.floor(x + Math.cos(ang) * a);
                    int bz = MathHelper.floor(z + Math.sin(ang) * a);
                    int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
                    if (Math.abs(top - y) > 4) {
                        continue;
                    }
                    BlockState gs = w.getBlockState(new BlockPos(bx, top, bz));
                    if (!gs.isAir()) {
                        spawn(w, new BlockStateParticleEffect(ParticleTypes.BLOCK, gs), bx + 0.5, top + 1.1, bz + 0.5, 3,
                                0.3, 0.1, 0.3, 0.15);
                    }
                    if (i % 3 == 0) {
                        spawn(w, ParticleTypes.CLOUD, bx + 0.5, top + 1.2, bz + 0.5, 1, 0.1, 0.05, 0.1, 0.03);
                    }
                }
            }
            case Fx.BLAST -> blast(w, x, y, z, a, (flags & Fx.F_LOUD) != 0, blockFx);
            case Fx.COCOON_BURST -> cocoonBurst(w, x, y, z, a, blockFx);
            case Fx.IMPACT -> {
                Random r = w.random;
                boolean big = (flags & Fx.F_LOUD) != 0;
                int n = big ? 30 : 12;
                for (int i = 0; i < n; i++) {
                    double vx = r.nextGaussian() * 0.25 * a;
                    double vz = r.nextGaussian() * 0.25 * a;
                    if (blockFx != null) {
                        w.addParticle(blockFx, x, y, z, vx, 0.15 + r.nextDouble() * 0.3, vz);
                    }
                    if (i % 2 == 0) {
                        w.addParticle(ParticleTypes.CRIT, x, y, z, vx * 2, 0.2 + r.nextDouble() * 0.3, vz * 2);
                    }
                }
                spawn(w, ParticleTypes.POOF, x, y, z, big ? 8 : 3, 0.4 * a, 0.3, 0.4 * a, 0.03);
                spawn(w, SOOT, x, y - 0.3, z, big ? 10 : 4, 0.5 * a, 0.2, 0.5 * a, 0.02);
                if (big) {
                    w.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
                }
            }
            case Fx.FOUNTAIN -> {
                Random r = w.random;
                for (int i = 0; i < 26; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double sp = 0.08 + r.nextDouble() * 0.16;
                    if (blockFx != null) {
                        w.addParticle(blockFx, x + Math.cos(ang) * 0.3, y + 0.2, z + Math.sin(ang) * 0.3,
                                Math.cos(ang) * sp, 0.25 + r.nextDouble() * 0.3, Math.sin(ang) * sp);
                    }
                }
                spawn(w, ParticleTypes.CLOUD, x, y + 0.3, z, 5, 0.3, 0.2, 0.3, 0.03);
                spawn(w, SOIL, x, y + 0.4, z, 8, 0.35, 0.3, 0.35, 0.02);
            }
            default -> {
            }
        }
    }
}
