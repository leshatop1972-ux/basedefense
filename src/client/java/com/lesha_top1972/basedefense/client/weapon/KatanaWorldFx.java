package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.client.weapon.fp.FpFx;
import com.lesha_top1972.basedefense.weapon.KatanaCombat;
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
 * крест‑накрест на целях), искры попаданий, воздух, стягивающийся к игроку при зарядке. Ленты — слой «глаз»
 * (WorldRenderEvents.AFTER_ENTITIES), частицы — ванильные.
 */
public final class KatanaWorldFx {

    private static final FpFx FX = new FpFx(KatanaRender.TEXTURE, 36f / 128f, 62f / 128f);
    private static final DustParticleEffect WHITE = new DustParticleEffect(new Vector3f(0.92f, 0.95f, 1.0f), 0.6f);
    private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.55f, 0.02f, 0.04f), 0.9f);

    /** Светящаяся полоса в мире: от a до b, ширина w, «ось ширины» n, время жизни. */
    private record Streak(Vec3d a, Vec3d b, Vec3d n, float w, float life, float delay, long born) {
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
                // короткая вспышка‑разрез поперёк удара
                Vec3d d = v.lengthSquared() > 1e-6 ? v.normalize() : new Vec3d(1, 0, 0);
                Vec3d side = d.crossProduct(new Vec3d(0, 1, 0));
                if (side.lengthSquared() < 1e-6) {
                    side = new Vec3d(1, 0, 0);
                }
                Vec3d dir = side.normalize().multiply(0.9).add(0, (r.nextFloat() - 0.5) * 0.9, 0);
                STREAKS.add(new Streak(p.subtract(dir), p.add(dir), d, 0.05f, 5f, 0f, now));
            }
            case KatanaCombat.FX_DASH -> {
                Vec3d a = p;
                Vec3d b = p.add(v);
                Vec3d n = new Vec3d(0, 1, 0);
                STREAKS.add(new Streak(a, b, n, 0.32f, 14f, 0f, now));
                STREAKS.add(new Streak(a.add(0, 0.25, 0), b.add(0, 0.25, 0), n, 0.08f, 18f, 0f, now));
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
                    STREAKS.add(new Streak(p.subtract(dir), p.add(dir), n, 0.07f, 9f, i * 1.0f, now));
                }
                for (int i = 0; i < 14; i++) {
                    c.world.addParticle(BLOOD, p.x + r.nextGaussian() * w * 0.4, p.y + r.nextGaussian() * h * 0.3,
                            p.z + r.nextGaussian() * w * 0.4, 0, 0, 0);
                }
                c.world.addParticle(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 0, 0, 0);
                c.world.addParticle(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 0, 0, 0);
            }
            default -> {
            }
        }
    }

    /** Зарядка: воздух и искры стягиваются к игроку; готово (≥ 1.5 с) — ярче. */
    static void chargeParticles(World w, PlayerEntity p, int useTime) {
        if (!(w.getTime() % 2 == 0)) {
            return;
        }
        Random r = w.random;
        boolean ready = useTime >= KatanaCombat.CHARGE_TICKS;
        int n = ready ? 2 : 1;
        Vec3d c = p.getPos().add(0, 0.9, 0);
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 1.4 + r.nextDouble() * 0.8;
            Vec3d s = c.add(Math.cos(ang) * rad, (r.nextDouble() - 0.4) * 1.2, Math.sin(ang) * rad);
            Vec3d vel = c.subtract(s).multiply(0.09);
            w.addParticle(ready ? ParticleTypes.END_ROD : ParticleTypes.WHITE_ASH, s.x, s.y, s.z, vel.x, vel.y, vel.z);
        }
    }

    /** Готово: кольцо искр вокруг игрока. */
    static void readyBurst(World w, PlayerEntity p) {
        Vec3d c = p.getPos().add(0, 0.9, 0);
        for (int i = 0; i < 16; i++) {
            double ang = i * Math.PI * 2 / 16;
            w.addParticle(ParticleTypes.END_ROD, c.x, c.y, c.z, Math.cos(ang) * 0.12, 0.01, Math.sin(ang) * 0.12);
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
        VertexConsumer vc = FX.buffer(vcp);
        Iterator<Streak> it = STREAKS.iterator();
        while (it.hasNext()) {
            Streak s = it.next();
            float age = now - s.born - s.delay;
            if (age > s.life) {
                it.remove();
                continue;
            }
            if (age < 0) {
                continue;
            }
            float appear = MathHelper.clamp(age / 1.5f, 0f, 1f);             // разрез «прорисовывается» за 1.5 тика
            float k = (1f - age / s.life);
            k = k * k;
            Vec3d a = s.a.subtract(cam);
            Vec3d b = a.add(s.b.subtract(s.a).multiply(appear));
            // ширина — поперёк полосы и к камере (видна с любой стороны)
            Vec3d along = b.subtract(a);
            Vec3d toCam = a.add(b).multiply(-0.5);
            Vec3d side = along.crossProduct(toCam);
            if (side.lengthSquared() < 1e-8) {
                side = s.n;
            }
            side = side.normalize().multiply(s.w * (0.4 + 0.6 * k));
            float[] hot = {0.85f * k, 0.92f * k, 1.0f * k};
            float[] off = {0f, 0f, 0f};
            Vector3f a0 = vec(a.subtract(side));
            Vector3f a1 = vec(a.add(side));
            Vector3f b0 = vec(b.subtract(side));
            Vector3f b1 = vec(b.add(side));
            Vector3f am = vec(a);
            Vector3f bm = vec(b);
            FX.quad(vc, m, a0, am, bm, b0, off, hot, hot, off);
            FX.quad(vc, m, am, a1, b1, bm, hot, off, off, hot);
        }
    }

    private static Vector3f vec(Vec3d v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }
}
