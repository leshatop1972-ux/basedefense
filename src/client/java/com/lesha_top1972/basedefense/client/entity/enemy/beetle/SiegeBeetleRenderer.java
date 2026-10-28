package com.lesha_top1972.basedefense.client.entity.enemy.beetle;

import com.lesha_top1972.basedefense.client.entity.bedrock.AssetGroups;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.entity.enemy.beetle.SiegeBeetleEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Осадный жук: модель ×1.3, светятся глаза и слабые трещины на роге. */
public class SiegeBeetleRenderer extends MobEntityRenderer<SiegeBeetleEntity, SiegeBeetleModel> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "siege_beetle"), "main");
    public static final float SCALE = 1.3f;
    private static final Identifier TEX = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("siege_beetle.png"));

    public SiegeBeetleRenderer(EntityRendererFactory.Context context) {
        super(context, new SiegeBeetleModel(context.getPart(LAYER)), 1.7f);
        this.addFeature(new GlowLayer<>(this, new Identifier(Basedefense.MOD_ID, AssetGroups.texture("siege_beetle_glowmask.png"))));
    }

    @Override
    protected void scale(SiegeBeetleEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(SCALE, SCALE, SCALE);
    }

    @Override
    protected float getLyingAngle(SiegeBeetleEntity entity) {
        return 180f;          // умирая, переворачивается на спину
    }

    @Override
    public Identifier getTexture(SiegeBeetleEntity entity) {
        return TEX;
    }
}
