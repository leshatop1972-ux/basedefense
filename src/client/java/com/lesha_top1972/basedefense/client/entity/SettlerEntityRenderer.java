package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.SettlerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Отрисовка жителей поселения.
 * Внешность (лицо, волосы, одежда) — одна из 8 случайных: textures/entity/settler/male_1..5, female_1..3.
 * Поверх — наряд профессии (шляпа/колпак/каска и фартук, жилет…): textures/entity/outfit/&lt;профессия&gt;.png,
 * с уровнем наряд богаче: _2, _3. В руке — инструмент.
 */
public class SettlerEntityRenderer extends MobEntityRenderer<SettlerEntity, SettlerEntityModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "settler"), "main");
    public static final EntityModelLayer OUTFIT_LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "settler"), "outfit");

    private static final Identifier[] SKINS = new Identifier[SettlerEntity.MALE_SKINS + SettlerEntity.FEMALE_SKINS];

    static {
        for (int i = 0; i < SKINS.length; i++) {
            String name = i < SettlerEntity.MALE_SKINS ? "male_" + (i + 1) : "female_" + (i - SettlerEntity.MALE_SKINS + 1);
            SKINS[i] = new Identifier(Basedefense.MOD_ID, "textures/entity/settler/" + name + ".png");
        }
    }

    public SettlerEntityRenderer(EntityRendererFactory.Context context, String profession,
                                 SettlerEntityModel.Headwear headwear) {
        super(context, new SettlerEntityModel(context.getPart(LAYER)), 0.5f);
        this.addFeature(new OutfitFeature(this, new SettlerEntityModel(context.getPart(OUTFIT_LAYER)),
                profession, headwear));
        this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
    }

    /** Чуть меньше игрока — житель выглядит аккуратнее. */
    @Override
    protected void scale(SettlerEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    public Identifier getTexture(SettlerEntity entity) {
        int skin = entity.getSkin();
        return SKINS[skin >= 0 && skin < SKINS.length ? skin : 0];
    }

    /** Наряд профессии поверх внешности (двигается вместе с телом). */
    static class OutfitFeature extends FeatureRenderer<SettlerEntity, SettlerEntityModel> {

        private final SettlerEntityModel outfit;
        private final SettlerEntityModel.Headwear headwear;
        private final Identifier[] textures = new Identifier[SettlerEntity.MAX_LEVEL];

        OutfitFeature(FeatureRendererContext<SettlerEntity, SettlerEntityModel> context, SettlerEntityModel outfit,
                      String profession, SettlerEntityModel.Headwear headwear) {
            super(context);
            this.outfit = outfit;
            this.headwear = headwear;
            for (int i = 0; i < textures.length; i++) {
                String suffix = i == 0 ? "" : "_" + (i + 1);
                textures[i] = new Identifier(Basedefense.MOD_ID, "textures/entity/outfit/" + profession + suffix + ".png");
            }
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, SettlerEntity entity,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                           float headYaw, float headPitch) {
            outfit.setHeadwear(headwear);
            int level = Math.max(1, Math.min(textures.length, entity.getLevel()));
            render(this.getContextModel(), outfit, textures[level - 1], matrices, vertexConsumers, light, entity,
                    limbAngle, limbDistance, animationProgress, headYaw, headPitch, tickDelta, 1.0f, 1.0f, 1.0f);
        }
    }
}
