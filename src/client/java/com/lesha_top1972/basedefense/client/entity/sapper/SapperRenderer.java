package com.lesha_top1972.basedefense.client.entity.sapper;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.GlowLayer;
import com.lesha_top1972.basedefense.client.entity.nabat.WeaponFeature;
import com.lesha_top1972.basedefense.entity.SapperEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Отрисовка подкопщика: модель ×0.85, светящиеся свеча и фитили, кирка отдельной моделью. */
public class SapperRenderer extends MobEntityRenderer<SapperEntity, SapperModel> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "sapper"), "main");
    public static final EntityModelLayer PICK = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "sapper_pick"), "main");
    public static final float SCALE = 0.85f;
    private static final Identifier TEX = new Identifier(Basedefense.MOD_ID, "textures/entity/sapper.png");

    public SapperRenderer(EntityRendererFactory.Context context) {
        super(context, new SapperModel(context.getPart(LAYER)), 0.4f);
        this.addFeature(new GlowLayer<>(this, new Identifier(Basedefense.MOD_ID, "textures/entity/sapper_glowmask.png")));
        this.addFeature(new WeaponFeature<>(this, context.getPart(PICK),
                new Identifier(Basedefense.MOD_ID, "textures/entity/sapper_pick.png"), null));
    }

    @Override
    protected void scale(SapperEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTexture(SapperEntity entity) {
        return TEX;
    }
}
