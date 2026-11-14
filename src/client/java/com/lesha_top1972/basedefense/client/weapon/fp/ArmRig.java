package com.lesha_top1972.basedefense.client.weapon.fp;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Суставная рука игрока: плечо → локоть → запястье → кисть (для оружия с «живыми» руками — тесак Громового тигра).
 * Рука — один гнутый брусок из скина игрока: в локте и запястье сечения сходятся под углом («на ус»), вдоль
 * предплечья сечение доворачивается к кисти. Локоть ищется по двум костям с подсказкой, куда ему смотреть; кисть
 * держит рукоять, запястье гнётся не больше WRIST_LIM — дальше кисть доворачивается к предплечью.
 * Та же математика — в tools/limb_rig.py (превью); правь оба файла вместе.
 */
public final class ArmRig {

    /** px: над плечом, плечо, предплечье, запястье → хват, хват → конец кисти. */
    public static final float CAP = 2.0f;
    public static final float LU = 4.6f;
    public static final float LF = 3.7f;
    public static final float HW = 1.3f;
    public static final float HE = 1.1f;
    private static final float TOTAL = CAP + LU + LF + HW + HE;
    private static final float ROW_E = 12f * (CAP + LU) / TOTAL;
    private static final float ROW_W = 12f * (CAP + LU + LF) / TOTAL;
    private static final float WRIST_LIM = (float) Math.toRadians(55);
    private static final float HANDLE_MIN = (float) Math.toRadians(38);
    private static final float STRETCH_MAX = 1.35f;
    /** Ось кисти в системе оружия: костяшки — к кромке и чуть к острию. */
    public static final Vector3f H0 = new Vector3f(0.90f, 0.42f, 0f).normalize();
    private static final int[][] CR = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};

    /** Решение руки: плечо, локоть, запястье, ось кисти, растяжение. */
    public static final class Sol {
        public final Vector3f s = new Vector3f();
        public final Vector3f e = new Vector3f();
        public final Vector3f w = new Vector3f();
        public final Vector3f h = new Vector3f(0, 1, 0);
        public float k = 1f;
        public boolean valid;
    }

    private ArmRig() {
    }

    private static Vector3f perp(Vector3f v, Vector3f u, Vector3f fallback) {
        Vector3f p = new Vector3f(v).sub(new Vector3f(u).mul(v.dot(u)));
        if (p.lengthSquared() < 1e-8f) {
            p.set(fallback).sub(new Vector3f(u).mul(fallback.dot(u)));
            if (p.lengthSquared() < 1e-8f) {
                p.set(u).cross(0f, 0f, 1f);
                if (p.lengthSquared() < 1e-8f) {
                    p.set(u).cross(1f, 0f, 0f);
                }
            }
        }
        return p.normalize();
    }

    /**
     * @param shoulder плечевой сустав
     * @param grip     середина кулака (хват)
     * @param hand     ось кисти (куда смотрят костяшки)
     * @param pole     куда смотрит локоть
     * @param unit     длина 1 px
     * @param lm       множитель длины плеча и предплечья
     * @param handle   ось рукояти в кулаке (или null)
     */
    public static Sol solve(Vector3f shoulder, Vector3f grip, Vector3f hand, Vector3f pole, float unit, float lm,
                            Vector3f handle, Sol out) {
        Vector3f h = new Vector3f(hand).normalize();
        float a0 = LU * lm * unit;
        float b0 = LF * lm * unit;
        Vector3f w = new Vector3f();
        Vector3f e = new Vector3f();
        float k = 1f;
        for (int it = 0; it < 2; it++) {
            w.set(grip).sub(new Vector3f(h).mul(HW * unit));
            Vector3f d = new Vector3f(w).sub(shoulder);
            float dist = Math.max(1e-5f, d.length());
            k = Math.min(STRETCH_MAX, Math.max(1f, dist / ((a0 + b0) * 0.995f)));
            float a = a0 * k;
            float b = b0 * k;
            float dc = Math.max(Math.abs(a - b) + 1e-4f, Math.min(dist, (a + b) * 0.999f));
            Vector3f dn = new Vector3f(d).div(dist);
            float ca = Math.max(-1f, Math.min(1f, (a * a + dc * dc - b * b) / (2f * a * dc)));
            float sa = (float) Math.sqrt(Math.max(0f, 1f - ca * ca));
            Vector3f p = perp(pole, dn, new Vector3f(0f, -1f, 0f));
            e.set(shoulder).add(new Vector3f(dn).mul(a * ca)).add(p.mul(a * sa));
            if (it == 0) {
                Vector3f f = new Vector3f(w).sub(e).normalize();
                float ang = (float) Math.acos(Math.max(-1f, Math.min(1f, f.dot(h))));
                if (ang > WRIST_LIM) {
                    Vector3f ax = new Vector3f(h).cross(f);
                    if (ax.lengthSquared() > 1e-10f) {
                        ax.normalize();
                        h.rotateAxis(ang - WRIST_LIM, ax.x, ax.y, ax.z).normalize();
                        if (handle != null) {
                            Vector3f ty = new Vector3f(handle).normalize();
                            float c = h.dot(ty);
                            float cm = (float) Math.cos(HANDLE_MIN);
                            if (Math.abs(c) > cm) {                // слишком вдоль рукояти — отвести
                                Vector3f pp = perp(h, ty, new Vector3f(ty).cross(0f, 0f, 1f));
                                h.set(ty).mul(c > 0 ? cm : -cm).add(pp.mul((float) Math.sin(HANDLE_MIN))).normalize();
                            }
                        }
                        continue;
                    }
                }
            }
            break;
        }
        out.s.set(shoulder);
        out.e.set(e);
        out.w.set(w);
        out.h.set(h);
        out.k = k;
        out.valid = true;
        return out;
    }

    private static Vector3f[] flat(Vector3f c, Vector3f u, Vector3f s, float hw, float hd) {
        Vector3f t = new Vector3f(u).cross(s);
        Vector3f[] r = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            r[i] = new Vector3f(c).add(new Vector3f(s).mul(hw * CR[i][0])).add(new Vector3f(t).mul(hd * CR[i][1]));
        }
        return r;
    }

    private static Vector3f[] mitre(Vector3f c, Vector3f uin, Vector3f uout, Vector3f s, float hw, float hd) {
        Vector3f m = new Vector3f(uin).add(uout);
        if (m.lengthSquared() > 1e-8f) {
            m.normalize();
        } else {
            m.set(uin);
        }
        float den = Math.max(0.60f, uin.dot(m));
        Vector3f t = new Vector3f(uin).cross(s);
        Vector3f[] r = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            Vector3f o = new Vector3f(s).mul(hw * CR[i][0]).add(new Vector3f(t).mul(hd * CR[i][1]));
            float lam = -o.dot(m) / den;
            r[i] = new Vector3f(c).add(o).add(new Vector3f(uin).mul(lam));
        }
        return r;
    }

    /**
     * Нарисовать руку (один слой скина).
     *
     * @param back   куда смотрит тыльная сторона кисти
     * @param outer  «наружу от тела»
     * @param wscale толщина (доля от обычной), inflate — прибавка px (рукав второго слоя)
     */
    public static void render(Sol sol, Vector3f back, Vector3f outer, float unit, boolean right, boolean slim, float wscale,
                              float inflate, float lm, boolean sleeve, MatrixStack matrices, VertexConsumer vc, int light,
                              int overlay) {
        if (!sol.valid) {
            return;
        }
        Vector3f u1 = new Vector3f(sol.e).sub(sol.s).normalize();
        Vector3f u2 = new Vector3f(sol.w).sub(sol.e);
        float fl = u2.length();
        u2.normalize();
        Vector3f u3 = new Vector3f(sol.h);
        Vector3f s1 = perp(outer, u1, new Vector3f(1f, 0f, 0f));
        float hw = ((slim ? 1.5f : 2f) * wscale + inflate) * unit;
        float hd = (2f * wscale + inflate) * unit;
        Vector3f s2 = new Quaternionf().rotationTo(u1, u2).transform(new Vector3f(s1));
        Vector3f s3 = new Quaternionf().rotationTo(u2, u3).transform(new Vector3f(s2));
        Vector3f sH = perp(back, u3, s3);
        float tau = (float) Math.atan2(new Vector3f(s3).cross(sH).dot(u3), s3.dot(sH));
        tau = Math.max(-2.6f, Math.min(2.6f, tau));
        Vector3f[][] rings = new Vector3f[6][];
        float[] rows = {0f, ROW_E, ROW_E + (ROW_W - ROW_E) / 3f, ROW_E + (ROW_W - ROW_E) * 2f / 3f, ROW_W, 12f};
        rings[0] = flat(new Vector3f(sol.s).sub(new Vector3f(u1).mul(CAP * lm * unit)), u1, s1, hw, hd);
        rings[1] = mitre(sol.e, u1, u2, s1, hw, hd);
        rings[2] = flat(new Vector3f(sol.e).add(new Vector3f(u2).mul(fl / 3f)), u2,
                new Vector3f(s2).rotateAxis(tau / 3f, u2.x, u2.y, u2.z), hw, hd);
        rings[3] = flat(new Vector3f(sol.e).add(new Vector3f(u2).mul(fl * 2f / 3f)), u2,
                new Vector3f(s2).rotateAxis(tau * 2f / 3f, u2.x, u2.y, u2.z), hw, hd);
        rings[4] = mitre(sol.w, u2, u3, new Vector3f(s2).rotateAxis(tau, u2.x, u2.y, u2.z), hw, hd);
        rings[5] = flat(new Vector3f(sol.w).add(new Vector3f(u3).mul((HW + HE) * unit)), u3, sH, hw, hd);

        int w = slim ? 3 : 4;
        int bx;
        int by;
        if (right) {
            bx = 40;
            by = sleeve ? 32 : 16;
        } else {
            bx = sleeve ? 48 : 32;
            by = 48;
        }
        int[] first = {bx, bx + 4};
        int[] front = {bx + 4, bx + 4 + w};
        int[] third = {bx + 4 + w, bx + 8 + w};
        int[] backC = {bx + 8 + w, bx + 8 + 2 * w};
        // грани между углами (0,1) (1,2) (2,3) (3,0): наружная, −t, внутренняя, +t
        int[][] cols = right ? new int[][]{first, front, third, backC} : new int[][]{third, backC, first, front};
        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f nrm = matrices.peek().getNormalMatrix();
        Vector3f n = new Vector3f();
        Vector3f mid = new Vector3f();
        for (int j = 0; j < 5; j++) {
            Vector3f[] a = rings[j];
            Vector3f[] b = rings[j + 1];
            mid.zero();
            for (int i = 0; i < 4; i++) {
                mid.add(a[i]).add(b[i]);
            }
            mid.div(8f);
            for (int i = 0; i < 4; i++) {
                int i2 = (i + 1) % 4;
                n.set(a[i]).add(a[i2]).add(b[i2]).add(b[i]).div(4f).sub(mid);
                if (n.lengthSquared() > 1e-10f) {
                    n.normalize();
                } else {
                    n.set(0f, 1f, 0f);
                }
                float c0 = cols[i][0] / 64f;
                float c1 = cols[i][1] / 64f;
                float va = (by + 4 + rows[j]) / 64f;
                float vb = (by + 4 + rows[j + 1]) / 64f;
                vertex(vc, pos, nrm, a[i], c0, va, n, light, overlay);
                vertex(vc, pos, nrm, a[i2], c1, va, n, light, overlay);
                vertex(vc, pos, nrm, b[i2], c1, vb, n, light, overlay);
                vertex(vc, pos, nrm, b[i], c0, vb, n, light, overlay);
            }
        }
        // торцы: над плечом и конец кисти
        Vector3f[] a = rings[0];
        n.set(u1).negate();
        float y0 = by / 64f;
        float y1 = (by + 4) / 64f;
        vertex(vc, pos, nrm, a[0], (bx + 4) / 64f, y0, n, light, overlay);
        vertex(vc, pos, nrm, a[3], (bx + 4 + w) / 64f, y0, n, light, overlay);
        vertex(vc, pos, nrm, a[2], (bx + 4 + w) / 64f, y1, n, light, overlay);
        vertex(vc, pos, nrm, a[1], (bx + 4) / 64f, y1, n, light, overlay);
        Vector3f[] b = rings[5];
        n.set(u3);
        vertex(vc, pos, nrm, b[0], (bx + 4 + w) / 64f, y0, n, light, overlay);
        vertex(vc, pos, nrm, b[1], (bx + 4 + 2 * w) / 64f, y0, n, light, overlay);
        vertex(vc, pos, nrm, b[2], (bx + 4 + 2 * w) / 64f, y1, n, light, overlay);
        vertex(vc, pos, nrm, b[3], (bx + 4 + w) / 64f, y1, n, light, overlay);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pos, Matrix3f nrm, Vector3f p, float u, float v, Vector3f n,
                               int light, int overlay) {
        vc.vertex(pos, p.x, p.y, p.z).color(255, 255, 255, 255).texture(u, v).overlay(overlay).light(light)
                .normal(nrm, n.x, n.y, n.z).next();
    }

    /** Рука игрока его скином: кожа и рукав второго слоя (если включён в настройках скина). */
    public static void draw(AbstractClientPlayerEntity player, Sol sol, Vector3f back, Vector3f outer, float unit, boolean right,
                            float wscale, float lm, MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay) {
        if (player.isInvisible() || !sol.valid) {
            return;
        }
        boolean slim = "slim".equals(player.getModel());
        Identifier skin = player.getSkinTexture();
        render(sol, back, outer, unit, right, slim, wscale, 0f, lm, false, matrices,
                vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(skin)), light, overlay);
        if (player.isPartVisible(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE)) {
            render(sol, back, outer, unit, right, slim, wscale, 0.25f, lm, true, matrices,
                    vcp.getBuffer(RenderLayer.getEntityTranslucent(skin)), light, overlay);
        }
    }
}
