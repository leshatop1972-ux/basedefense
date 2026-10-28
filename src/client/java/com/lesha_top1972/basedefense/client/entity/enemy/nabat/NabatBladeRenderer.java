package com.lesha_top1972.basedefense.client.entity.enemy.nabat;

import com.lesha_top1972.basedefense.client.entity.bedrock.AssetGroups;
import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponFeature;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatBladeEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Отрисовка мечника «Отзвук»: латы + светящаяся пустота внутри + меч отдельной моделью. */
public class NabatBladeRenderer extends MobEntityRenderer<NabatBladeEntity, NabatBladeModel> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "nabat_blade"), "main");
    public static final EntityModelLayer SWORD = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "nabat_sword"), "main");
    private static final Identifier TEX = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("nabat_blade.png"));

    public NabatBladeRenderer(EntityRendererFactory.Context context) {
        super(context, new NabatBladeModel(context.getPart(LAYER)), 0.5f);
        this.addFeature(new GlowLayer<>(this, new Identifier(Basedefense.MOD_ID, AssetGroups.texture("nabat_blade_glowmask.png"))));
        this.addFeature(new WeaponFeature<>(this, context.getPart(SWORD),
                new Identifier(Basedefense.MOD_ID, AssetGroups.texture("nabat_sword.png")),
                new Identifier(Basedefense.MOD_ID, AssetGroups.texture("nabat_sword_glowmask.png"))));
    }

    @Override
    public Identifier getTexture(NabatBladeEntity entity) {
        return TEX;
    }
}
