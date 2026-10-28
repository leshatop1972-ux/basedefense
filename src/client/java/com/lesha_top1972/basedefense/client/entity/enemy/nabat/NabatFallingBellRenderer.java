package com.lesha_top1972.basedefense.client.entity.enemy.nabat;

import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatFallingBellEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Падающий колокол: «Метка набата» (висит над головой, раскачивается) и «Колокольный дождь».
 * Внизу — кольцо‑тень, которое к удару светлеет и сжимается до радиуса поражения.
 */
public class NabatFallingBellRenderer extends EntityRenderer<NabatFallingBellEntity> {

    private final ModelPart bell;
    private final NabatBellModel<NabatFallingBellEntity> model;

    public NabatFallingBellRenderer(EntityRendererFactory.Context context) {
        super(context);
        ModelPart root = context.getPart(NabatBellRenderer.LAYER);
        this.model = new NabatBellModel<>(root);
        this.bell = root;
    }

    @Override
    public void render(NabatFallingBellEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        boolean mark = entity.kind() == NabatFallingBellEntity.MARK;
        float k = entity.chargeProgress(tickDelta);
        float age = entity.age + tickDelta;
        // кольцо на земле
        double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double dy = entity.groundY() - y;
        float r = (float) entity.radius();
        float ringR = r * (1.25f - 0.25f * k);
        int alpha = (int) (60 + 170 * k);
        if (!entity.isLanded()) {
            NabatRing.draw(matrices, vcp, dy, ringR, alpha, age * (mark ? 2f : 4f));
        }
        // колокол
        matrices.push();
        float s = mark ? 0.95f : 0.7f;
        if (mark && k < 1f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(age * 0.35f) * 9f * (0.4f + k)));
        }
        matrices.scale(-s, -s, s);
        matrices.translate(0, -1.5, 0);
        this.model.shake = mark ? k : 0.3f;
        this.model.setAngles(entity, 0, 0, age, 0, 0);
        this.bell.render(matrices, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(NabatBellRenderer.TEX)), light,
                OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        this.bell.render(matrices, vcp.getBuffer(RenderLayer.getEyes(NabatBellRenderer.GLOW)), 15728640,
                OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }

    @Override
    public Identifier getTexture(NabatFallingBellEntity entity) {
        return NabatBellRenderer.TEX;
    }
}
