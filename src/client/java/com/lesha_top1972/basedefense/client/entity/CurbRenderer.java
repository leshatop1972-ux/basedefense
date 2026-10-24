package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.CurbEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Отрисовка босса «Бордюр»: модель ×1.12 + светящаяся маска (визор, ядро, полосы, струи). */
public class CurbRenderer extends MobEntityRenderer<CurbEntity, CurbModel<CurbEntity>> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "curb"), "main");
    private static final Identifier TEXTURE = new Identifier(Basedefense.MOD_ID, "textures/entity/curb.png");
    private static final Identifier GLOW = new Identifier(Basedefense.MOD_ID, "textures/entity/curb_glowmask.png");

    public CurbRenderer(EntityRendererFactory.Context context) {
        super(context, new CurbModel<>(context.getPart(LAYER), "curb"), 0.6f);
        this.addFeature(new GlowLayer<>(this, GLOW));
    }

    @Override
    protected void scale(CurbEntity entity, MatrixStack matrices, float amount) {
        float k = CurbEntity.MODEL_SCALE;
        matrices.scale(k, k, k);
    }

    @Override
    public Identifier getTexture(CurbEntity entity) {
        return TEXTURE;
    }
}
