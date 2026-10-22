package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.FarmerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

public class FarmerEntityRenderer extends MobEntityRenderer<FarmerEntity, FarmerEntityModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "farmer"), "main");

    private static final Identifier TEXTURE =
            new Identifier(Basedefense.MOD_ID, "textures/entity/farmer.png");

    public FarmerEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new FarmerEntityModel(context.getPart(LAYER)), 0.5f);
    }

    @Override
    public Identifier getTexture(FarmerEntity entity) {
        return TEXTURE;
    }
}
