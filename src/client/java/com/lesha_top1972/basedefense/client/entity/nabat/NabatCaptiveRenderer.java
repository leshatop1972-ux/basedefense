package com.lesha_top1972.basedefense.client.entity.nabat;

import com.lesha_top1972.basedefense.client.entity.GlowLayer;
import com.lesha_top1972.basedefense.entity.NabatCaptiveEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** «Колокольный плен»: тот же колокол, только ×1.55 и дрожит от ударов изнутри. */
public class NabatCaptiveRenderer extends MobEntityRenderer<NabatCaptiveEntity, NabatBellModel<NabatCaptiveEntity>> {

    static final float SCALE = 1.55f;

    public NabatCaptiveRenderer(EntityRendererFactory.Context context) {
        super(context, new NabatBellModel<>(context.getPart(NabatBellRenderer.LAYER)), 1.2f);
        this.addFeature(new GlowLayer<>(this, NabatBellRenderer.GLOW));
    }

    @Override
    public void render(NabatCaptiveEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        // пленнику (вид из глаз) колокол не рисуем: изнутри он закрыл бы весь экран
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.options.getPerspective().isFirstPerson() && entity.age > 8
                && entity.squaredDistanceTo(mc.player.getX(), entity.getY(), mc.player.getZ()) < 1.2) {
            return;
        }
        this.getModel().shake = 0.6f + (entity.hurtTime > 0 ? entity.hurtTime / 6f : 0f);
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }

    @Override
    protected void scale(NabatCaptiveEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTexture(NabatCaptiveEntity entity) {
        return NabatBellRenderer.TEX;
    }
}
