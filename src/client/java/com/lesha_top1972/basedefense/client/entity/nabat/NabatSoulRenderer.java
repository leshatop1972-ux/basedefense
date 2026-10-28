package com.lesha_top1972.basedefense.client.entity.nabat;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.NabatSoulEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Душа «Реквиема»: мягкий огонёк (янтарный, у улана — бирюзовый), дышит; в рывке вытягивается. */
public class NabatSoulRenderer extends EntityRenderer<NabatSoulEntity> {

    private static final Identifier TEX = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("nabat_soul.png"));

    public NabatSoulRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(NabatSoulEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        float age = entity.age + tickDelta;
        float pulse = 1f + 0.12f * MathHelper.sin(age * 0.4f);
        float s = (entity.isBig() ? 0.85f : 0.55f) * pulse;
        int r = entity.isBig() ? 110 : 255;
        int g = entity.isBig() ? 245 : 200;
        int b = entity.isBig() ? 230 : 120;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEX));
        matrices.push();
        matrices.translate(0, 0.3, 0);
        matrices.multiply(this.dispatcher.getRotation());
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(age * 4f));
        if (entity.isDashing()) {
            matrices.scale(s * 0.8f, s * 1.5f, s);
        } else {
            matrices.scale(s, s, s);
        }
        MatrixStack.Entry e = matrices.peek();
        quad(e.getPositionMatrix(), e.getNormalMatrix(), vc, r, g, b, 230);
        matrices.scale(1.9f, 1.9f, 1.9f);                     // мягкий ореол
        e = matrices.peek();
        quad(e.getPositionMatrix(), e.getNormalMatrix(), vc, r, g, b, 70);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }

    private static void quad(Matrix4f m, Matrix3f n, VertexConsumer vc, int r, int g, int b, int a) {
        v(m, n, vc, -0.5f, -0.5f, 0f, 1f, r, g, b, a);
        v(m, n, vc, 0.5f, -0.5f, 1f, 1f, r, g, b, a);
        v(m, n, vc, 0.5f, 0.5f, 1f, 0f, r, g, b, a);
        v(m, n, vc, -0.5f, 0.5f, 0f, 0f, r, g, b, a);
    }

    private static void v(Matrix4f m, Matrix3f n, VertexConsumer vc, float x, float y, float u, float vv,
                          int r, int g, int b, int a) {
        vc.vertex(m, x, y, 0).color(r, g, b, a).texture(u, vv).overlay(OverlayTexture.DEFAULT_UV)
                .light(15728880).normal(n, 0, 1, 0).next();
    }

    @Override
    public Identifier getTexture(NabatSoulEntity entity) {
        return TEX;
    }
}
