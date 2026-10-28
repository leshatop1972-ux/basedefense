package com.lesha_top1972.basedefense.client.entity.enemy.lancer;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.enemy.lancer.FallingLanceEntity;
import com.lesha_top1972.basedefense.entity.enemy.lancer.TatteredLancerEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static com.lesha_top1972.basedefense.entity.enemy.lancer.TatteredLancerEntity.*;

/**
 * Эффекты боя с Оборванным уланом (только клиент):
 * <ul>
 *   <li>«ливень копий»: руническая печать в небе, луч от улана к ней, стена‑купол по краю зоны,
 *       след и метка на земле под каждым падающим копьём, кольцо там, где оно воткнулось.</li>
 * </ul>
 * Палитра под босса: выцветшая бирюза (сюртук), тусклое золото (галуны), малиновый (кушак) — метки копий.
 * Текстуры — textures/fx/*.png (tools/lancer_fx_textures.py), смешивание аддитивное (RenderLayer.getEyes).
 */
public final class LancerFx {

    private static Identifier tex(String name) {
        return new Identifier(Basedefense.MOD_ID, "textures/fx/" + name + ".png");
    }

    private static final Identifier RING = tex("ring");
    private static final Identifier RUNE = tex("rune");
    private static final Identifier BEAM = tex("beam");
    private static final Identifier MARKER = tex("marker");
    private static final Identifier WALL = tex("wall");

    static final float[] TEAL = {0.30f, 0.70f, 0.62f};
    static final float[] GOLD = {0.85f, 0.64f, 0.30f};
    static final float[] CRIMSON = {0.80f, 0.12f, 0.20f};

    private static final DustParticleEffect DUST_GOLD = new DustParticleEffect(new Vector3f(0.86f, 0.68f, 0.36f), 1.0f);

    /** Кольца на месте воткнувшихся копий. */
    private record Ring(Vec3d pos, float start) {
    }

    private static final List<Ring> RINGS = new ArrayList<>();
    private static final float RING_LIFE = 8f;
    private static int ticks;

    private LancerFx() {
    }

    public static void init() {
        ClientTickEvents.END_WORLD_TICK.register(LancerFx::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(LancerFx::render);
    }

    // ---------------- тик ----------------

    private static void tick(ClientWorld world) {
        ticks++;
        RINGS.removeIf(r -> ticks - r.start() > RING_LIFE);
        for (Entity e : world.getEntities()) {
            if (e instanceof TatteredLancerEntity l && l.clientAction == RAIN && l.deathTime == 0) {
                int t = l.age - l.clientActionAge;
                if (t > RAIN_RISE && t % 2 == 0) {
                    // золотые искры кружат вокруг парящего улана
                    double a = l.age * 0.35;
                    world.addParticle(DUST_GOLD, l.getX() + Math.cos(a) * 1.2, l.getBodyY(0.4),
                            l.getZ() + Math.sin(a) * 1.2, 0, 0, 0);
                }
            } else if (e instanceof FallingLanceEntity f) {
                if (Double.isNaN(f.clientGroundY)) {
                    Vec3d from = f.getPos();
                    BlockHitResult hit = world.raycast(new RaycastContext(from, from.add(0, -48, 0),
                            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, f));
                    f.clientGroundY = hit.getType() == HitResult.Type.MISS ? from.y - 48 : hit.getPos().y;
                }
                if (f.isStuck() && !f.clientImpactShown) {
                    f.clientImpactShown = true;
                    RINGS.add(new Ring(new Vec3d(f.getX(), f.getY() + 0.05, f.getZ()), ticks));
                }
            }
        }
    }

    // ---------------- отрисовка ----------------

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || context.consumers() == null) {
            return;
        }
        float tickDelta = context.tickDelta();
        float now = ticks + tickDelta;
        Draw d = new Draw(context.matrixStack(), context.consumers(), context.camera().getPos());
        for (Ring r : RINGS) {
            float p = MathHelper.clamp((now - r.start()) / RING_LIFE, 0f, 1f);
            d.flat(RING, r.pos(), 1.7f * (0.3f + 0.7f * p), 0f, TEAL, (1f - p) * (1f - p));
        }
        for (Entity e : client.world.getEntities()) {
            if (e instanceof TatteredLancerEntity l && l.deathTime == 0) {
                drawLancer(d, l, tickDelta, now);
            } else if (e instanceof FallingLanceEntity f && !f.isStuck()) {
                drawFallingLance(d, f, tickDelta, now);
            }
        }
    }

    private static void drawLancer(Draw d, TatteredLancerEntity l, float tickDelta, float now) {
        Vec3d pos = l.getLerpedPos(tickDelta);
        int a = l.clientAction;
        // --- ливень копий: печать в небе, луч, стена‑купол ---
        if (l.clientRainAge < 0) {
            return;
        }
        float rt = l.age + tickDelta - l.clientRainAge;
        Vec3d zone = l.clientZone;
        if (rt >= 30 && rt <= RAIN_STOP + 15) {
            float k = Math.min(MathHelper.clamp((rt - 30f) / 30f, 0f, 1f),
                    MathHelper.clamp((RAIN_STOP + 15 - rt) / 15f, 0f, 1f));
            float boost = rt >= RAIN_START && rt < RAIN_STOP ? 1f + 0.15f * MathHelper.sin(now * 0.9f) : 0.7f;
            Vec3d sky = zone.add(0, 15.0, 0);
            d.flat(RUNE, sky, (float) RAIN_RADIUS * 0.85f, now * 0.8f, TEAL, k * boost);
            d.flat(RUNE, sky.add(0, -0.3, 0), 5f, -now * 1.6f, GOLD, k * boost * 0.6f);
            if (rt >= 50 && a == RAIN) {
                float kb = k * MathHelper.clamp((rt - 50f) / 10f, 0f, 1f);
                d.segment(BEAM, pos.add(0, 1.0, 0), sky, 0.3f, 0.8f, 1f, 0f, GOLD, kb * 0.7f);
            }
        }
        float zk = l.clientZoneAlpha(tickDelta);
        if (zk > 0f) {
            drawWall(d, zone, (float) RAIN_RADIUS, rt >= RAIN_START ? 3.2f : 2.6f, now, zk * 0.5f);
        }
    }

    private static void drawWall(Draw d, Vec3d c, float radius, float height, float now, float k) {
        int n = 48;
        float scroll = now * 0.01f;
        for (int i = 0; i < n; i++) {
            double a0 = i / (double) n * Math.PI * 2;
            double a1 = (i + 1) / (double) n * Math.PI * 2;
            Vec3d b0 = c.add(Math.cos(a0) * radius, 0.02, Math.sin(a0) * radius);
            Vec3d b1 = c.add(Math.cos(a1) * radius, 0.02, Math.sin(a1) * radius);
            float u0 = i / (float) n * 6f + scroll;
            float u1 = (i + 1) / (float) n * 6f + scroll;
            d.quad(WALL, d.rel(b0.add(0, height, 0)), d.rel(b0), d.rel(b1), d.rel(b1.add(0, height, 0)),
                    u0, 0f, u1, 1f, TEAL, k);
        }
    }

    private static void drawFallingLance(Draw d, FallingLanceEntity f, float tickDelta, float now) {
        Vec3d p = f.getLerpedPos(tickDelta);
        d.segment(BEAM, p.add(0, 0.6, 0), p.add(0, 4.0, 0), 0.2f, 0.04f, 0f, 1f, TEAL, 0.6f);
        if (!Double.isNaN(f.clientGroundY)) {
            double h = p.y - f.clientGroundY;
            float k = (float) MathHelper.clamp(1.0 - h / 18.0, 0.15, 1.0);
            d.flat(MARKER, new Vec3d(p.x, f.clientGroundY + 0.06, p.z), 1.3f, now * 4f, CRIMSON, k * 0.9f);
        }
    }

    // ---------------- рисование четырёхугольников ----------------

    private static final class Draw {
        final MatrixStack matrices;
        final VertexConsumerProvider consumers;
        final Vec3d cam;

        Draw(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam) {
            this.matrices = matrices;
            this.consumers = consumers;
            this.cam = cam;
        }

        float[] rel(Vec3d p) {
            return new float[]{(float) (p.x - this.cam.x), (float) (p.y - this.cam.y), (float) (p.z - this.cam.z)};
        }

        /** Четырёхугольник с двух сторон; точки — относительно камеры. Яркость k (аддитивно). */
        void quad(Identifier texture, float[] p0, float[] p1, float[] p2, float[] p3,
                  float u0, float v0, float u1, float v1, float[] rgb, float k) {
            if (k <= 0.004f) {
                return;
            }
            VertexConsumer vc = this.consumers.getBuffer(RenderLayer.getEyes(texture));
            MatrixStack.Entry e = this.matrices.peek();
            Matrix4f m = e.getPositionMatrix();
            Matrix3f n = e.getNormalMatrix();
            int r = (int) (MathHelper.clamp(rgb[0] * k, 0f, 1f) * 255);
            int g = (int) (MathHelper.clamp(rgb[1] * k, 0f, 1f) * 255);
            int b = (int) (MathHelper.clamp(rgb[2] * k, 0f, 1f) * 255);
            v(vc, m, n, p0, u0, v0, r, g, b);
            v(vc, m, n, p1, u0, v1, r, g, b);
            v(vc, m, n, p2, u1, v1, r, g, b);
            v(vc, m, n, p3, u1, v0, r, g, b);
            v(vc, m, n, p3, u1, v0, r, g, b);
            v(vc, m, n, p2, u1, v1, r, g, b);
            v(vc, m, n, p1, u0, v1, r, g, b);
            v(vc, m, n, p0, u0, v0, r, g, b);
        }

        private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] p, float u, float vv, int r, int g, int b) {
            vc.vertex(m, p[0], p[1], p[2]).color(r, g, b, 255).texture(u, vv).overlay(OverlayTexture.DEFAULT_UV)
                    .light(0xF000F0).normal(n, 0, 1, 0).next();
        }

        /** Плоский квадрат (на земле или в небе): центр, полуразмер, поворот в градусах. */
        void flat(Identifier texture, Vec3d c, float half, float rotDeg, float[] rgb, float k) {
            float a = rotDeg * MathHelper.RADIANS_PER_DEGREE;
            float cs = MathHelper.cos(a) * half;
            float sn = MathHelper.sin(a) * half;
            float[] o = this.rel(c);
            this.quad(texture,
                    new float[]{o[0] - cs + sn, o[1], o[2] - sn - cs},
                    new float[]{o[0] - cs - sn, o[1], o[2] - sn + cs},
                    new float[]{o[0] + cs - sn, o[1], o[2] + sn + cs},
                    new float[]{o[0] + cs + sn, o[1], o[2] + sn - cs},
                    0, 0, 1, 1, rgb, k);
        }

            /** Лента от a до b, повёрнутая к камере вокруг своей оси (луч). u — поперёк, v — вдоль. */
        void segment(Identifier texture, Vec3d a, Vec3d b, float wa, float wb, float va, float vb, float[] rgb, float k) {
            float[] pa = this.rel(a);
            float[] pb = this.rel(b);
            Vector3f dir = new Vector3f(pb[0] - pa[0], pb[1] - pa[1], pb[2] - pa[2]);
            Vector3f mid = new Vector3f((pa[0] + pb[0]) / 2, (pa[1] + pb[1]) / 2, (pa[2] + pb[2]) / 2);
            Vector3f side = dir.cross(mid, new Vector3f());
            if (side.lengthSquared() < 1e-8f) {
                side.set(1, 0, 0);
            }
            side.normalize();
            this.quad(texture,
                    new float[]{pa[0] - side.x * wa, pa[1] - side.y * wa, pa[2] - side.z * wa},
                    new float[]{pb[0] - side.x * wb, pb[1] - side.y * wb, pb[2] - side.z * wb},
                    new float[]{pb[0] + side.x * wb, pb[1] + side.y * wb, pb[2] + side.z * wb},
                    new float[]{pa[0] + side.x * wa, pa[1] + side.y * wa, pa[2] + side.z * wa},
                    0, va, 1, vb, rgb, k);
        }
    }
}
