package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.SettlerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Отрисовка жителей поселения. У каждого жителя своя текстура. */
public class SettlerEntityRenderer extends MobEntityRenderer<SettlerEntity, SettlerEntityModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "settler"), "main");

    private final Identifier texture;

    public SettlerEntityRenderer(EntityRendererFactory.Context context, String textureName) {
        super(context, new SettlerEntityModel(context.getPart(LAYER)), 0.5f);
        this.texture = new Identifier(Basedefense.MOD_ID, "textures/entity/" + textureName + ".png");
    }

    @Override
    public Identifier getTexture(SettlerEntity entity) {
        return texture;
    }
}
