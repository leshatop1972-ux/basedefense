package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.nabat.WeaponFeature;
import com.lesha_top1972.basedefense.entity.TatteredLancerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Отрисовка босса «Оборванный улан»: модель + светящаяся маска (глаза, остриё копья). */
public class TatteredLancerRenderer extends MobEntityRenderer<TatteredLancerEntity, TatteredLancerModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "tattered_lancer"), "main");

    public static final EntityModelLayer LANCE =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "tattered_lancer_lance"), "main");

    private static final Identifier TEXTURE =
            new Identifier(Basedefense.MOD_ID, "textures/entity/tattered_lancer.png");
    private static final Identifier GLOW =
            new Identifier(Basedefense.MOD_ID, "textures/entity/tattered_lancer_glowmask.png");

    public TatteredLancerRenderer(EntityRendererFactory.Context context) {
        super(context, new TatteredLancerModel(context.getPart(LAYER)), 0.6f);
        this.addFeature(new GlowLayer<>(this, GLOW));
        // копьё — отдельная модель с текстурой улана
        this.addFeature(new WeaponFeature<>(this, context.getPart(LANCE), TEXTURE, GLOW));
    }

    @Override
    public Identifier getTexture(TatteredLancerEntity entity) {
        return TEXTURE;
    }
}
