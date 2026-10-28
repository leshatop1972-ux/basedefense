package com.lesha_top1972.basedefense.client.entity.enemy.iron_zombie;

import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.enemy.iron_zombie.IronZombieEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Отрисовка: модель из Blockbench + светящаяся маска (iron_zombie_glowmask.png). */
public class IronZombieRenderer extends MobEntityRenderer<IronZombieEntity, IronZombieModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "iron_zombie"), "main");

    private static final Identifier TEXTURE =
            new Identifier(Basedefense.MOD_ID, AssetGroups.texture("iron_zombie.png"));
    private static final Identifier GLOW =
            new Identifier(Basedefense.MOD_ID, AssetGroups.texture("iron_zombie_glowmask.png"));

    public IronZombieRenderer(EntityRendererFactory.Context context) {
        super(context, new IronZombieModel(context.getPart(LAYER)), 0.5f);
        this.addFeature(new GlowLayer<>(this, GLOW));
    }

    @Override
    public Identifier getTexture(IronZombieEntity entity) {
        return TEXTURE;
    }
}
