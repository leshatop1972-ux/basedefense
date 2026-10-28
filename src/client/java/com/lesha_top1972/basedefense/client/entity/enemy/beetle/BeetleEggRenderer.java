package com.lesha_top1972.basedefense.client.entity.enemy.beetle;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.entity.enemy.beetle.BeetleEggEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

import java.util.Map;

/** Яйцо жука: пульсирует (к вылуплению — вдвое чаще), тёплые прожилки светятся. */
public class BeetleEggRenderer extends MobEntityRenderer<BeetleEggEntity, BeetleEggRenderer.Model> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "beetle_egg"), "main");
    private static final Identifier TEX = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("beetle_egg.png"));

    public BeetleEggRenderer(EntityRendererFactory.Context context) {
        super(context, new Model(context.getPart(LAYER)), 0.45f);
        this.addFeature(new GlowLayer<>(this, new Identifier(Basedefense.MOD_ID, AssetGroups.texture("beetle_egg_glowmask.png"))));
    }

    @Override
    public Identifier getTexture(BeetleEggEntity entity) {
        return TEX;
    }

    public static class Model extends BedrockEntityModel<BeetleEggEntity> {
        private static final Map<String, BedrockAnimations.Clip> C = BedrockAnimations.load("beetle_egg");

        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setAngles(BeetleEggEntity e, float limbAngle, float limbDistance, float animationProgress,
                              float headYaw, float headPitch) {
            this.resetPose();
            float k = e.life() > BeetleEggEntity.HATCH_TICKS - 80 ? 2f : 1f;
            this.play(C.get("idle"), (animationProgress + e.getId() * 11) * k / 20f, 1f);
        }
    }
}
