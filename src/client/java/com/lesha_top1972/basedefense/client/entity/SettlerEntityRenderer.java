package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.SettlerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Отрисовка жителей поселения. У каждого жителя своя текстура и головной убор,
 * а с уровнем текстура меняется: farmer.png → farmer_2.png → farmer_3.png.
 * Инструмент в руке рисуется как у игрока.
 */
public class SettlerEntityRenderer extends MobEntityRenderer<SettlerEntity, SettlerEntityModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "settler"), "main");

    private final Identifier[] textures;
    private final SettlerEntityModel.Headwear headwear;

    public SettlerEntityRenderer(EntityRendererFactory.Context context, String textureName,
                                 SettlerEntityModel.Headwear headwear) {
        super(context, new SettlerEntityModel(context.getPart(LAYER)), 0.5f);
        this.headwear = headwear;
        this.textures = new Identifier[SettlerEntity.MAX_LEVEL];
        for (int i = 0; i < textures.length; i++) {
            String suffix = i == 0 ? "" : "_" + (i + 1);
            textures[i] = new Identifier(Basedefense.MOD_ID, "textures/entity/" + textureName + suffix + ".png");
        }
        this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
    }

    @Override
    public void render(SettlerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        this.getModel().setHeadwear(headwear);
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /** Чуть меньше игрока — житель выглядит аккуратнее. */
    @Override
    protected void scale(SettlerEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    public Identifier getTexture(SettlerEntity entity) {
        int index = Math.max(0, Math.min(textures.length - 1, entity.getLevel() - 1));
        return textures[index];
    }
}
