package com.lesha_top1972.basedefense.client.entity.beetle;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.GlowLayer;
import com.lesha_top1972.basedefense.entity.BeetleMiteEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

public class BeetleMiteRenderer extends MobEntityRenderer<BeetleMiteEntity, BeetleMiteModel> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "beetle_mite"), "main");
    private static final Identifier TEX = new Identifier(Basedefense.MOD_ID, "textures/entity/beetle_mite.png");

    public BeetleMiteRenderer(EntityRendererFactory.Context context) {
        super(context, new BeetleMiteModel(context.getPart(LAYER)), 0.45f);
        this.addFeature(new GlowLayer<>(this, new Identifier(Basedefense.MOD_ID, "textures/entity/beetle_mite_glowmask.png")));
    }

    @Override
    protected float getLyingAngle(BeetleMiteEntity entity) {
        return 180f;
    }

    @Override
    public Identifier getTexture(BeetleMiteEntity entity) {
        return TEX;
    }
}
