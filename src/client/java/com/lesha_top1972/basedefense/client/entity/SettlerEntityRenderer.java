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
 * Отрисовка жителей поселения. Внешность не зависит от профессии: каждому жителю случайно
 * достаётся одна из 8 (5 мужчин, 3 женщины) — textures/entity/settler/male_1..5, female_1..3 (128×64).
 * Для женщин модель включает тонкие руки, грудь, юбку и причёску (см. LOOKS).
 */
public class SettlerEntityRenderer extends MobEntityRenderer<SettlerEntity, SettlerEntityModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "settler"), "main");

    private static final int COUNT = SettlerEntity.MALE_SKINS + SettlerEntity.FEMALE_SKINS;
    private static final Identifier[] SKINS = new Identifier[COUNT];

    /** Причёски и фигура для каждой внешности (номер = номер текстуры). */
    private static final SettlerEntityModel.Look[] LOOKS = {
            new SettlerEntityModel.Look(false, false, false),   // male_1
            new SettlerEntityModel.Look(false, false, false),   // male_2
            new SettlerEntityModel.Look(false, false, false),   // male_3
            new SettlerEntityModel.Look(false, false, false),   // male_4
            new SettlerEntityModel.Look(false, false, false),   // male_5
            new SettlerEntityModel.Look(true, false, true),     // female_1 — хвост
            new SettlerEntityModel.Look(true, true, false),     // female_2 — длинные волосы
            new SettlerEntityModel.Look(true, false, false),    // female_3 — пучок
    };

    static {
        for (int i = 0; i < COUNT; i++) {
            String name = i < SettlerEntity.MALE_SKINS ? "male_" + (i + 1) : "female_" + (i - SettlerEntity.MALE_SKINS + 1);
            SKINS[i] = new Identifier(Basedefense.MOD_ID, "textures/entity/settler/" + name + ".png");
        }
    }

    public SettlerEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new SettlerEntityModel(context.getPart(LAYER)), 0.5f);
        this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
    }

    private static int skinOf(SettlerEntity entity) {
        int skin = entity.getSkin();
        return skin >= 0 && skin < COUNT ? skin : 0;
    }

    @Override
    public void render(SettlerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        this.getModel().setLook(LOOKS[skinOf(entity)]);
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /** Чуть меньше игрока; женщины ещё чуть ниже. */
    @Override
    protected void scale(SettlerEntity entity, MatrixStack matrices, float amount) {
        float s = entity.isFemale() ? 0.9f : 0.9375f;
        matrices.scale(s, s, s);
    }

    @Override
    public Identifier getTexture(SettlerEntity entity) {
        return SKINS[skinOf(entity)];
    }
}
