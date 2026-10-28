package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.SettlerEntity;
import com.lesha_top1972.basedefense.entity.SettlerLooks;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Отрисовка жителей поселения. Внешность не зависит от профессии: каждому жителю случайно
 * достаётся одна из 14 (8 мужчин, 6 женщин, список — SettlerLooks), текстуры 128×64.
 * Для женщин модель включает тонкие руки, грудь, юбку и причёску; у некоторых — шляпа, кепка
 * или сумочка на поясе (см. LOOKS).
 */
public class SettlerEntityRenderer extends MobEntityRenderer<SettlerEntity, SettlerEntityModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "settler"), "main");

    private static final int COUNT = SettlerLooks.COUNT;
    private static final Identifier[] SKINS = new Identifier[COUNT];

    private static final int NONE = SettlerEntityModel.HAT_NONE;
    private static final int BRIM = SettlerEntityModel.HAT_BRIM;
    private static final int CAP = SettlerEntityModel.HAT_CAP;

    /**
     * Фигура, причёска и аксессуары для каждой внешности (номер = номер в SettlerLooks).
     * Должно совпадать с CHARACTERS в tools/settler_textures.py (hair_style, hat, pouch).
     */
    private static final SettlerEntityModel.Look[] LOOKS = {
            new SettlerEntityModel.Look(false, false, false, NONE, false),   // male_1 — сюртук
            new SettlerEntityModel.Look(false, false, false, NONE, true),    // male_2 — жилет, сумочка
            new SettlerEntityModel.Look(false, false, false, NONE, false),   // male_3 — жилет с галстуком
            new SettlerEntityModel.Look(false, false, false, NONE, true),    // male_4 — рыжий, сумочка
            new SettlerEntityModel.Look(false, false, false, CAP, false),    // male_5 — седой, кепка
            new SettlerEntityModel.Look(true, false, true, NONE, false),     // female_1 — хвост
            new SettlerEntityModel.Look(true, true, false, NONE, false),     // female_2 — длинные волосы
            new SettlerEntityModel.Look(true, false, false, NONE, false),    // female_3 — пучок и платок
            new SettlerEntityModel.Look(false, false, false, BRIM, true),    // male_6 — старик, соломенная шляпа
            new SettlerEntityModel.Look(false, false, false, CAP, false),    // male_7 — парень в кепке
            new SettlerEntityModel.Look(true, false, true, NONE, true),      // female_4 — рыжая коса, сумочка
            new SettlerEntityModel.Look(true, false, false, BRIM, false),    // female_5 — каре, шляпа
            new SettlerEntityModel.Look(false, false, false, BRIM, true),    // male_8 — бородач в зелёном сюртуке и фетровой шляпе
            new SettlerEntityModel.Look(true, false, false, NONE, false),    // female_6 — седая бабушка с пучком
    };

    static {
        for (int i = 0; i < COUNT; i++) {
            SKINS[i] = new Identifier(Basedefense.MOD_ID, "textures/entity/settler/" + SettlerLooks.NAMES[i] + ".png");
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
