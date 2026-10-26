package com.lesha_top1972.basedefense.client.entity.nabat;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.NabatSpearEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Золотое копьё «Копий небес»: висит остриём вниз и медленно вращается, разгораясь; под ним на земле —
 * кольцо‑метка, сжимающееся к удару. После удара торчит из земли и немного дрожит.
 */
public class NabatSpearRenderer extends EntityRenderer<NabatSpearEntity> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "nabat_spear"), "main");
    static final Identifier TEX = new Identifier(Basedefense.MOD_ID, "textures/entity/nabat_spear.png");
    static final Identifier GLOW = new Identifier(Basedefense.MOD_ID, "textures/entity/nabat_spear_glowmask.png");

    private final ModelPart spear;

    public NabatSpearRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.spear = context.getPart(LAYER);
    }

    @Override
    public void render(NabatSpearEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        float k = entity.charge(tickDelta);
        float age = entity.age + tickDelta;
        int stuck = entity.stuckTicks();
        double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        float r = (float) entity.radius();
        if (stuck < 0) {
            NabatRing.draw(matrices, vcp, entity.groundY() - y, r * (1.35f - 0.35f * k), (int) (50 + 190 * k), age * 5f);
        } else if (stuck < 8) {
            // вспышка кольца в момент удара
            float f = stuck / 8f;
            NabatRing.draw(matrices, vcp, entity.groundY() - y, r * (1.0f + 0.8f * f), (int) (230 * (1f - f)), 0f);
        }
        matrices.push();
        float s = entity.isBig() ? 1.6f : 1.0f;
        if (stuck < 0) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getYaw() + age * (k < 1f ? 6f : 0f)));
        } else {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getYaw()));
            float wob = stuck < 12 ? MathHelper.sin(stuck * 1.7f) * (12 - stuck) * 0.6f : 0f;
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(wob));
            matrices.translate(0, -0.35 * s, 0);                    // острие ушло в землю
        }
        matrices.scale(-s, -s, s);
        matrices.translate(0, -1.5, 0);
        this.spear.render(matrices, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), light,
                OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        this.spear.render(matrices, vcp.getBuffer(RenderLayer.getEyes(GLOW)), 15728640,
                OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }

    @Override
    public Identifier getTexture(NabatSpearEntity entity) {
        return TEX;
    }
}
