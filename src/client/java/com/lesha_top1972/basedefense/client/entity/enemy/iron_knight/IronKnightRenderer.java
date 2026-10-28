package com.lesha_top1972.basedefense.client.entity.enemy.iron_knight;

import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.enemy.iron_knight.IronKnightEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Отрисовка: модель из Blockbench + светящаяся маска (iron_knight_glowmask.png). */
public class IronKnightRenderer extends MobEntityRenderer<IronKnightEntity, IronKnightModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "iron_knight"), "main");

    private static final Identifier TEXTURE =
            new Identifier(Basedefense.MOD_ID, AssetGroups.texture("iron_knight.png"));
    private static final Identifier GLOW =
            new Identifier(Basedefense.MOD_ID, AssetGroups.texture("iron_knight_glowmask.png"));

    public IronKnightRenderer(EntityRendererFactory.Context context) {
        super(context, new IronKnightModel(context.getPart(LAYER)), 0.6f);
        this.addFeature(new GlowLayer<>(this, GLOW));
    }

    @Override
    public Identifier getTexture(IronKnightEntity entity) {
        return TEXTURE;
    }
}
