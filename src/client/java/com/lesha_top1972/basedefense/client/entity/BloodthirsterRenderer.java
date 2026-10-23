package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.BloodthirsterEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Отрисовка «Кровожада»: текстура и светящаяся маска по фазе (2‑я — *_p2). */
public class BloodthirsterRenderer extends MobEntityRenderer<BloodthirsterEntity, BloodthirsterModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "bloodthirster"), "main");

    private static final Identifier TEX1 = tex("bloodthirster");
    private static final Identifier TEX2 = tex("bloodthirster_p2");
    private static final Identifier GLOW1 = tex("bloodthirster_glowmask");
    private static final Identifier GLOW2 = tex("bloodthirster_p2_glowmask");

    private static Identifier tex(String name) {
        return new Identifier(Basedefense.MOD_ID, "textures/entity/" + name + ".png");
    }

    public BloodthirsterRenderer(EntityRendererFactory.Context context) {
        super(context, new BloodthirsterModel(context.getPart(LAYER)), 1.4f);
        this.addFeature(new PhaseGlow(this));
    }

    /** Модель огромная: ×1.3 (хитбокс 1.6×4.0 подобран под этот масштаб). */
    @Override
    protected void scale(BloodthirsterEntity entity, MatrixStack matrices, float amount) {
        float k = BloodthirsterEntity.MODEL_SCALE;
        matrices.scale(k, k, k);
    }

    @Override
    public Identifier getTexture(BloodthirsterEntity entity) {
        return entity.isPhase2() ? TEX2 : TEX1;
    }

    /** Светящиеся глаза, прожилки, кромки мечей — маска текущей фазы. */
    static class PhaseGlow extends FeatureRenderer<BloodthirsterEntity, BloodthirsterModel> {
        PhaseGlow(FeatureRendererContext<BloodthirsterEntity, BloodthirsterModel> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                           BloodthirsterEntity entity, float limbAngle, float limbDistance, float tickDelta,
                           float animationProgress, float headYaw, float headPitch) {
            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEyes(entity.isPhase2() ? GLOW2 : GLOW1));
            this.getContextModel().render(matrices, vc, 0xF00000, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        }
    }
}
