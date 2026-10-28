package com.lesha_top1972.basedefense.client.entity.enemy.bloodthirster;

import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.enemy.bloodthirster.BloodlingEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Отрисовка «мелкого кровожада»: модель + светящиеся глаза. */
public class BloodlingRenderer extends MobEntityRenderer<BloodlingEntity, BloodlingModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "bloodling"), "main");
    private static final Identifier TEXTURE = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("bloodling.png"));
    private static final Identifier GLOW = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("bloodling_glowmask.png"));

    public BloodlingRenderer(EntityRendererFactory.Context context) {
        super(context, new BloodlingModel(context.getPart(LAYER)), 0.4f);
        this.addFeature(new GlowLayer<>(this, GLOW));
    }

    @Override
    public Identifier getTexture(BloodlingEntity entity) {
        return TEXTURE;
    }
}
