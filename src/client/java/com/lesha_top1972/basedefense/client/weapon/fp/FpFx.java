package com.lesha_top1972.basedefense.client.weapon.fp;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Светящиеся ленты и блики (след клинка, свечение кромки, звезда готовности): слой «глаз» (getEyes) — складывает
 * цвет с картинкой под ним, не пишет глубину, всегда полностью светится. Цвет задаёт яркость (альфа не нужна).
 * Текстура — белый участок атласа оружия (u, v).
 */
public final class FpFx {

    private final Identifier texture;
    private final float u;
    private final float v;

    public FpFx(Identifier texture, float u, float v) {
        this.texture = texture;
        this.u = u;
        this.v = v;
    }

    public VertexConsumer buffer(VertexConsumerProvider vcp) {
        return vcp.getBuffer(RenderLayer.getEyes(this.texture));
    }

    /** Полупрозрачный (не светящийся) слой — затемнения, «пустота» разлома. Цвет с альфой: {r, g, b, a}. */
    public VertexConsumer translucent(VertexConsumerProvider vcp) {
        return vcp.getBuffer(RenderLayer.getEntityTranslucent(this.texture));
    }

    public void quadA(VertexConsumer vc, MatrixStack matrices, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                      float[] ca, float[] cb, float[] cc, float[] cd) {
        MatrixStack.Entry e = matrices.peek();
        Matrix4f m = e.getPositionMatrix();
        Matrix3f n = e.getNormalMatrix();
        for (Object[] v : new Object[][]{{a, ca}, {b, cb}, {c, cc}, {d, cd}}) {
            Vector3f p = (Vector3f) v[0];
            float[] col = (float[]) v[1];
            vc.vertex(m, p.x, p.y, p.z).color(clamp(col[0]), clamp(col[1]), clamp(col[2]), clamp(col[3])).texture(this.u, this.v)
                    .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(n, 0f, 1f, 0f).next();
        }
    }

    /** Четырёхугольник a‑b‑c‑d, у каждой вершины своя яркость/цвет (r, g, b 0..1). */
    public void quad(VertexConsumer vc, MatrixStack matrices, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                     float[] ca, float[] cb, float[] cc, float[] cd) {
        MatrixStack.Entry e = matrices.peek();
        Matrix4f m = e.getPositionMatrix();
        Matrix3f n = e.getNormalMatrix();
        put(vc, m, n, a, ca);
        put(vc, m, n, b, cb);
        put(vc, m, n, c, cc);
        put(vc, m, n, d, cd);
    }

    private void put(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f p, float[] c) {
        vc.vertex(m, p.x, p.y, p.z).color(clamp(c[0]), clamp(c[1]), clamp(c[2]), 1f).texture(this.u, this.v)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(n, 0f, 1f, 0f).next();
    }

    private static float clamp(float x) {
        return x < 0f ? 0f : Math.min(1f, x);
    }

    /** Звезда‑блик в точке p лицом к камере (в пространстве камеры): размер s, яркость g. */
    public void star(VertexConsumer vc, MatrixStack matrices, Vector3f p, float s, float g, float r0, float g0, float b0) {
        float[] hot = {r0 * g, g0 * g, b0 * g};
        float[] off = {0f, 0f, 0f};
        float[][] rays = {{1f, 0f, 0.10f}, {0f, 1f, 0.10f}, {0.7f, 0.7f, 0.05f}, {0.7f, -0.7f, 0.05f}};
        for (float[] ray : rays) {
            Vector3f uu = new Vector3f(ray[0], ray[1], 0f).mul(s);
            Vector3f vv = new Vector3f(-ray[1], ray[0], 0f).mul(s * ray[2]);
            quad(vc, matrices, new Vector3f(p).sub(uu), new Vector3f(p).sub(vv), new Vector3f(p).add(uu), new Vector3f(p).add(vv),
                    off, hot, off, hot);
        }
        float h = s * 0.35f;
        float[] halo = {r0 * g * 0.5f, g0 * g * 0.5f, b0 * g * 0.5f};
        quad(vc, matrices, new Vector3f(p).add(-h, -h, 0f), new Vector3f(p).add(h, -h, 0f), new Vector3f(p).add(h, h, 0f),
                new Vector3f(p).add(-h, h, 0f), off, halo, off, halo);
    }
}
