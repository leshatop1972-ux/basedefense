package com.lesha_top1972.basedefense.client.entity.enemy.bloodthirster;

import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.enemy.bloodthirster.BloodEggEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Отрисовка «яйца кровожада»: модель + светящиеся жилы. */
public class BloodEggRenderer extends MobEntityRenderer<BloodEggEntity, BloodEggModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "blood_egg"), "main");
    private static final Identifier TEXTURE = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("blood_egg.png"));
    private static final Identifier GLOW = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("blood_egg_glowmask.png"));

    public BloodEggRenderer(EntityRendererFactory.Context context) {
        super(context, new BloodEggModel(context.getPart(LAYER)), 0.6f);
        this.addFeature(new GlowLayer<>(this, GLOW));
    }

    @Override
    public Identifier getTexture(BloodEggEntity entity) {
        return TEXTURE;
    }
}
