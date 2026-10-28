package com.lesha_top1972.basedefense.client.entity.enemy.horror;

import com.lesha_top1972.basedefense.client.entity.bedrock.AssetGroups;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.entity.enemy.horror.BloodEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Кровавая сущность: светятся глаза, рот и трещины. */
public class BloodEntityRenderer extends MobEntityRenderer<BloodEntity, HorrorModel<BloodEntity>> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "blood_entity"), "main");
    private static final Identifier TEX = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("blood_entity.png"));

    public BloodEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new HorrorModel<>(context.getPart(LAYER), "blood_entity", 0.085f), 0.4f);
        this.addFeature(new GlowLayer<>(this, new Identifier(Basedefense.MOD_ID, AssetGroups.texture("blood_entity_glowmask.png"))));
    }

    @Override
    public Identifier getTexture(BloodEntity entity) {
        return TEX;
    }
}
