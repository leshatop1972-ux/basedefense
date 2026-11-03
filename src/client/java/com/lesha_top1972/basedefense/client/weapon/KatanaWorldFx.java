package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.client.weapon.fp.FpFx;
import com.lesha_top1972.basedefense.weapon.KatanaCombat;
import com.lesha_top1972.basedefense.weapon.KatanaVariant;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Эффекты катаны в мире (видят все): след рывка «Иссэн» (светлая полоса по пути), отложенный разрез (вспышки‑разрезы
 * крест‑накрест на целях), искры попаданий и блока, воздух, стягивающийся к игроку при зарядке.
 * Древняя катана — разлом пространства: рваная трещина с тёмной «пустотой» внутри и фиолетовыми краями, осколки,
 * на целях — крест из трещин. Светящееся — слой «глаз» (складывает цвет), пустота — полупрозрачный слой.
 */
public final class KatanaWorldFx {

    private static final FpFx FX = new FpFx(KatanaRender.TEXTURE, 36f / 128f, 62f / 128f);
    private static final DustParticleEffect WHITE = new DustParticleEffect(new Vector3f(0.92f, 0.95f, 1.0f), 0.6f);
    private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.55f, 0.02f, 0.04f), 0.9f);
    private static final DustParticleEffect VIOLET = new DustParticleEffect(new Vector3f(0.72f, 0.30f, 1.0f), 1.1f);
    private static final DustParticleEffect VOID = new DustParticleEffect(new Vector3f(0.18f, 0.02f, 0.30f), 1.4f);

    /** Полоса в мире от a до b: ширина w, цвет, время жизни (тики), задержка, тёмная (пустота) или светящаяся. */
    private record Streak(Vec3d a, Vec3d b, Vec3d n, float w, float life, float delay, long born,
                          float r, float g, float bl, boolean dark) {
    }

    private static final List<Streak> STREAKS = new ArrayList<>();

    private KatanaWorldFx() {
    }

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(KatanaCombat.FX, (client, handler, buf, sender) -> {
            int type = buf.readByte();
            Vec3d p = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3d v = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());
            int data = buf.readVarInt();
            client.execute(() -> play(type, p, v, data));
        });
        WorldRenderEvents.AFTER_ENTITIES.register(KatanaWorldFx::render);
    }

    private static long time() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c.world == null ? 0 : c.world.getTime();
    }

    private static void light(Vec3d a, Vec3d b, Vec3d n, float w, float life, float delay, long now, float r, float g, float bl) {
        STREAKS.add(new Streak(a, b, n, w, life, delay, now, r, g, bl, false));
    }

    private static void dark(Vec3d a, Vec3d b, Vec3d n, float w, float life, float delay, long now) {
        STREAKS.add(new Streak(a, b, n, w, life, delay, now, 0.10f, 0.0f, 0.18f, true));
    }

    private static void play(int type, Vec3d p, Vec3d v, int data) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) {
            return;
        }
        Random r = c.world.random;
        long now = time();
        switch (type) {
            case KatanaCombat.FX_HIT -> {
                c.world.addParticle(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 0, 0, 0);
                for (int i = 0; i < 6; i++) {
                    c.world.addParticle(BLOOD, p.x + r.nextGaussian() * 0.15, p.y + r.nextGaussian() * 0.15,
                            p.z + r.nextGaussian() * 0.15, 0, 0, 0);
                }
                Vec3d d = v.lengthSquared() > 1e-6 ? v.normalize() : new Vec3d(1, 0, 0);
                Vec3d side = d.crossProduct(new Vec3d(0, 1, 0));
                if (side.lengthSquared() < 1e-6) {
                    side = new Vec3d(1, 0, 0);
                }
                Vec3d dir = side.normalize().multiply(0.9).add(0, (r.nextFloat() - 0.5) * 0.9, 0);
                light(p.subtract(dir), p.add(dir), d, 0.05f, 5f, 0f, now, 0.85f, 0.92f, 1.0f);
            }
            case KatanaCombat.FX_BLOCK -> {
                boolean anc = data == KatanaVariant.ANCIENT.ordinal();
                for (int i = 0; i < 10; i++) {
                    c.world.addParticle(anc ? ParticleTypes.WITCH : ParticleTypes.CRIT, p.x, p.y, p.z,
                            r.nextGaussian() * 0.25, r.nextGaussian() * 0.2 + 0.1, r.nextGaussian() * 0.25);
                }
                for (int i = 0; i < 4; i++) {
                    c.world.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, r.nextGaussian() * 0.3, r.nextGaussian() * 0.3,
                            r.nextGaussian() * 0.3);
                }
                Vec3d up = new Vec3d(0, 0.35, 0);
                Vec3d side = v.crossProduct(new Vec3d(0, 1, 0));
                side = side.lengthSquared() < 1e-6 ? new Vec3d(0.35, 0, 0) : side.normalize().multiply(0.35);
                float cr = anc ? 0.8f : 1.0f;
                float cg = anc ? 0.45f : 0.95f;
                light(p.subtract(up), p.add(up), v, 0.03f, 3f, 0f, now, cr, cg, 1.0f);
                light(p.subtract(side), p.add(side), v, 0.03f, 3f, 0f, now, cr, cg, 1.0f);
            }
            case KatanaCombat.FX_DASH -> {
                Vec3d a = p;
                Vec3d b = p.add(v);
                Vec3d n = new Vec3d(0, 1, 0);
                light(a, b, n, 0.32f, 14f, 0f, now, 0.85f, 0.92f, 1.0f);
                light(a.add(0, 0.25, 0), b.add(0, 0.25, 0), n, 0.08f, 18f, 0f, now, 0.85f, 0.92f, 1.0f);
                int steps = (int) Math.max(4, v.length() * 3);
                for (int i = 0; i <= steps; i++) {
                    Vec3d q = a.add(v.multiply((double) i / steps));
                    c.world.addParticle(ParticleTypes.CLOUD, q.x, q.y - 0.6, q.z, 0, 0.02, 0);
                    c.world.addParticle(WHITE, q.x + r.nextGaussian() * 0.2, q.y + r.nextGaussian() * 0.3, q.z + r.nextGaussian() * 0.2, 0, 0, 0);
                }
            }
            case KatanaCombat.FX_BURST -> {
                double w = Math.max(0.6, v.x);
                double h = Math.max(1.0, v.y);
                for (int i = 0; i < 4; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double tilt = (r.nextDouble() - 0.5) * 2.2;
                    Vec3d dir = new Vec3d(Math.cos(ang), tilt, Math.sin(ang)).normalize().multiply(Math.max(w, h * 0.6) * 0.9 + 0.4);
                    Vec3d n = new Vec3d(-Math.sin(ang), 0, Math.cos(ang));
                    light(p.subtract(dir), p.add(dir), n, 0.07f, 9f, i * 1.0f, now, 0.85f, 0.92f, 1.0f);
                }
                for (int i = 0; i < 14; i++) {
                    c.world.addParticle(BLOOD, p.x + r.nextGaussian() * w * 0.4, p.y + r.nextGaussian() * h * 0.3,
                            p.z + r.nextGaussian() * w * 0.4, 0, 0, 0);
                }
                c.world.addParticle(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 0, 0, 0);
                c.world.addParticle(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 0, 0, 0);
            }
            case KatanaCombat.FX_RIFT -> rift(c.world, r, p, p.add(v), now, 1.0f, 0f, 34f);
            case KatanaCombat.FX_RIFT_BURST -> {
                double w = Math.max(0.6, v.x);
                double h = Math.max(1.0, v.y);
                double size = Math.max(w, h * 0.6) + 0.6;
                for (int i = 0; i < 3; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double tilt = (r.nextDouble() - 0.5) * 2.4;
                    Vec3d dir = new Vec3d(Math.cos(ang), tilt, Math.sin(ang)).normalize().multiply(size);
                    rift(c.world, r, p.subtract(dir), p.add(dir), now, 0.6f, i * 1.5f, 22f);
                }
                for (int i = 0; i < 24; i++) {
                    c.world.addParticle(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, r.nextGaussian() * 0.25, r.nextGaussian() * 0.25,
                            r.nextGaussian() * 0.25);
                }
                for (int i = 0; i < 10; i++) {
                    c.world.addParticle(BLOOD, p.x + r.nextGaussian() * w * 0.4, p.y + r.nextGaussian() * h * 0.3,
                            p.z + r.nextGaussian() * w * 0.4, 0, 0, 0);
                }
                c.world.addParticle(ParticleTypes.FLASH, p.x, p.y, p.z, 0, 0, 0);
            }
            default -> {
            }
        }
    }

    /**
     * Разлом пространства от a до b: рваная ломаная; на каждом отрезке — тёмная «пустота», фиолетовые края,
     * белая сердцевина; трещина «бежит» от a к b, держится и схлопывается. Осколки и частицы портала.
     */
    private static void rift(World w, Random r, Vec3d a, Vec3d b, long now, float k, float delay, float life) {
        Vec3d d = b.subtract(a);
        double len = d.length();
        if (len < 1e-3) {
            return;
        }
        Vec3d dir = d.multiply(1.0 / len);
        Vec3d side = dir.crossProduct(new Vec3d(0, 1, 0));
        side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
        Vec3d up = side.crossProduct(dir).normalize();
        int n = Math.max(5, (int) (len * 2.2));
        Vec3d prev = a;
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n;
            double jag = i == n ? 0.0 : (r.nextDouble() - 0.5) * 0.45 * k;
            double jag2 = i == n ? 0.0 : (r.nextDouble() - 0.5) * 0.18 * k;
            Vec3d q = a.add(d.multiply(t)).add(up.multiply(jag)).add(side.multiply(jag2));
            float dl = delay + (float) (t * 3.0);                    // трещина бежит вдоль пути за 3 тика
            float wv = (float) (0.5 + 0.5 * Math.sin(t * Math.PI));   // шире в середине
            dark(prev, q, up, 0.20f * k * wv + 0.04f, life, dl, now);
            light(prev, q, up, 0.10f * k * wv + 0.03f, life * 0.95f, dl, now, 0.70f, 0.25f, 1.0f);
            light(prev, q, up, 0.025f * k + 0.01f, life * 0.8f, dl, now, 1.0f, 0.85f, 1.0f);
            prev = q;
            if (r.nextFloat() < 0.6f) {                              // осколки пространства
                Vec3d o = q.add(up.multiply((r.nextDouble() - 0.5) * 0.9 * k)).add(side.multiply((r.nextDouble() - 0.5) * 0.9 * k));
                Vec3d s = new Vec3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().multiply(0.12 + 0.12 * r.nextDouble());
                light(o.subtract(s), o.add(s), up, 0.03f, life * 0.6f, dl + 2f, now, 0.85f, 0.55f, 1.0f);
            }
        }
        int steps = (int) Math.max(6, len * 4);
        for (int i = 0; i <= steps; i++) {
            Vec3d q = a.add(d.multiply((double) i / steps));
            w.addParticle(ParticleTypes.REVERSE_PORTAL, q.x, q.y, q.z, r.nextGaussian() * 0.04, r.nextGaussian() * 0.04, r.nextGaussian() * 0.04);
            if (i % 2 == 0) {
                w.addParticle(VIOLET, q.x + r.nextGaussian() * 0.25, q.y + r.nextGaussian() * 0.25, q.z + r.nextGaussian() * 0.25, 0, 0, 0);
                w.addParticle(VOID, q.x + r.nextGaussian() * 0.15, q.y + r.nextGaussian() * 0.15, q.z + r.nextGaussian() * 0.15, 0, 0, 0);
            }
            if (i % 3 == 0) {
                w.addParticle(ParticleTypes.PORTAL, q.x, q.y - 0.2, q.z, r.nextGaussian() * 0.3, 0.1, r.nextGaussian() * 0.3);
            }
        }
    }

    /** Зарядка: воздух и искры стягиваются к игроку; готово (≥ 1.5 с) — ярче; у древней — фиолетовые. */
    static void chargeParticles(World w, PlayerEntity p, int age, boolean ancient) {
        if (!(w.getTime() % 2 == 0)) {
            return;
        }
        Random r = w.random;
        boolean ready = age >= KatanaCombat.CHARGE_TICKS;
        int n = ready ? 2 : 1;
        Vec3d c = p.getPos().add(0, 0.9, 0);
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 1.4 + r.nextDouble() * 0.8;
            Vec3d s = c.add(Math.cos(ang) * rad, (r.nextDouble() - 0.4) * 1.2, Math.sin(ang) * rad);
            Vec3d vel = c.subtract(s).multiply(0.09);
            if (ancient) {
                w.addParticle(ready ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.WITCH, s.x, s.y, s.z, vel.x, vel.y, vel.z);
            } else {
                w.addParticle(ready ? ParticleTypes.END_ROD : ParticleTypes.WHITE_ASH, s.x, s.y, s.z, vel.x, vel.y, vel.z);
            }
        }
    }

    /** Готово: кольцо искр вокруг игрока. */
    static void readyBurst(World w, PlayerEntity p) {
        Vec3d c = p.getPos().add(0, 0.9, 0);
        boolean ancient = KatanaCombat.variant(p).ancient;
        for (int i = 0; i < 16; i++) {
            double ang = i * Math.PI * 2 / 16;
            w.addParticle(ancient ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD, c.x, c.y, c.z,
                    Math.cos(ang) * 0.12, 0.01, Math.sin(ang) * 0.12);
        }
    }

    private static void render(WorldRenderContext ctx) {
        if (STREAKS.isEmpty() || ctx.consumers() == null) {
            return;
        }
        float now = time() + ctx.tickDelta();
        Vec3d cam = ctx.camera().getPos();
        MatrixStack m = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        // сначала «пустота» (полупрозрачный слой), потом свечение поверх
        for (int pass = 0; pass < 2; pass++) {
            boolean darkPass = pass == 0;
            VertexConsumer vc = darkPass ? FX.translucent(vcp) : FX.buffer(vcp);
            for (Streak s : STREAKS) {
                if (s.dark() != darkPass) {
                    continue;
                }
                float age = now - s.born() - s.delay();
                if (age < 0 || age > s.life()) {
                    continue;
                }
                float appear = MathHelper.clamp(age / 1.5f, 0f, 1f);
                float k = 1f - age / s.life();
                k = s.dark() ? Math.min(1f, k * 2.5f) : k * k;          // пустота держится и потом схлопывается
                Vec3d a = s.a().subtract(cam);
                Vec3d b = a.add(s.b().subtract(s.a()).multiply(appear));
                Vec3d along = b.subtract(a);
                Vec3d toCam = a.add(b).multiply(-0.5);
                Vec3d side = along.crossProduct(toCam);
                if (side.lengthSquared() < 1e-8) {
                    side = s.n();
                }
                side = side.normalize().multiply(s.w() * (0.4 + 0.6 * k));
                Vector3f a0 = vec(a.subtract(side));
                Vector3f a1 = vec(a.add(side));
                Vector3f b0 = vec(b.subtract(side));
                Vector3f b1 = vec(b.add(side));
                Vector3f am = vec(a);
                Vector3f bm = vec(b);
                if (darkPass) {
                    float[] core = {s.r(), s.g(), s.bl(), 0.85f * k};
                    float[] rim = {s.r(), s.g(), s.bl(), 0.0f};
                    FX.quadA(vc, m, a0, am, bm, b0, rim, core, core, rim);
                    FX.quadA(vc, m, am, a1, b1, bm, core, rim, rim, core);
                } else {
                    float[] hot = {s.r() * k, s.g() * k, s.bl() * k};
                    float[] off = {0f, 0f, 0f};
                    FX.quad(vc, m, a0, am, bm, b0, off, hot, hot, off);
                    FX.quad(vc, m, am, a1, b1, bm, hot, off, off, hot);
                }
            }
        }
        Iterator<Streak> it = STREAKS.iterator();
        while (it.hasNext()) {
            Streak s = it.next();
            if (now - s.born() - s.delay() > s.life()) {
                it.remove();
            }
        }
    }

    private static Vector3f vec(Vec3d v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }
}
