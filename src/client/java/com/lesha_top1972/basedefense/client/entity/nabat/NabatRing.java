package com.lesha_top1972.basedefense.client.entity.nabat;

import com.lesha_top1972.basedefense.Basedefense;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Светящееся кольцо‑тень на земле (куда упадёт колокол). */
public final class NabatRing {

    private static final Identifier RING = new Identifier(Basedefense.MOD_ID, "textures/misc/nabat_ring.png");

    private NabatRing() {
    }

    /** dy — высота земли относительно точки отрисовки (блоки), r — радиус, alpha 0..255. */
    public static void draw(MatrixStack matrices, VertexConsumerProvider vcp, double dy, float r, int alpha, float spin) {
        matrices.push();
        matrices.translate(0, dy + 0.03, 0);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(RING));
        MatrixStack.Entry e = matrices.peek();
        Matrix4f m = e.getPositionMatrix();
        Matrix3f n = e.getNormalMatrix();
        v(m, n, vc, -r, -r, 0, 0, alpha);
        v(m, n, vc, -r, r, 0, 1, alpha);
        v(m, n, vc, r, r, 1, 1, alpha);
        v(m, n, vc, r, -r, 1, 0, alpha);
        matrices.pop();
    }

    private static void v(Matrix4f m, Matrix3f n, VertexConsumer vc, float x, float z, float u, float v, int alpha) {
        vc.vertex(m, x, 0, z).color(255, 210, 140, alpha).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(15728880).normal(n, 0, 1, 0).next();
    }
}
