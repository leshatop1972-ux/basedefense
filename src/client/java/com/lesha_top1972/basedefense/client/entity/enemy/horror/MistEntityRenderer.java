package com.lesha_top1972.basedefense.client.entity.enemy.horror;

import com.lesha_top1972.basedefense.client.entity.bedrock.AssetGroups;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.enemy.horror.MistEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Туманная сущность: полупрозрачная (дымный второй слой и растворяющиеся ноги). */
public class MistEntityRenderer extends MobEntityRenderer<MistEntity, HorrorModel<MistEntity>> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "mist_entity"), "main");
    private static final Identifier TEX = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("mist_entity.png"));

    public MistEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new HorrorModel<>(context.getPart(LAYER), "mist_entity", 0.106f), 0.35f);
    }

    @Override
    protected RenderLayer getRenderLayer(MistEntity entity, boolean showBody, boolean translucent, boolean showOutline) {
        return showBody ? RenderLayer.getEntityTranslucent(TEX) : super.getRenderLayer(entity, showBody, translucent, showOutline);
    }

    @Override
    public Identifier getTexture(MistEntity entity) {
        return TEX;
    }
}
