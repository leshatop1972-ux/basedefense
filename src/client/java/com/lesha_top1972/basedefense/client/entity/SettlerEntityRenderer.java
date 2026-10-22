package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.SettlerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/**
 * Отрисовка жителей поселения. У каждого жителя своя текстура,
 * а с уровнем она меняется: farmer.png → farmer_2.png → farmer_3.png.
 */
public class SettlerEntityRenderer extends MobEntityRenderer<SettlerEntity, SettlerEntityModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "settler"), "main");

    private final Identifier[] textures;

    /** @param levels сколько текстур-уровней есть у жителя (1 — одна текстура на все уровни) */
    public SettlerEntityRenderer(EntityRendererFactory.Context context, String textureName, int levels) {
        super(context, new SettlerEntityModel(context.getPart(LAYER)), 0.5f);
        this.textures = new Identifier[levels];
        for (int i = 0; i < levels; i++) {
            String suffix = i == 0 ? "" : "_" + (i + 1);
            textures[i] = new Identifier(Basedefense.MOD_ID, "textures/entity/" + textureName + suffix + ".png");
        }
    }

    @Override
    public Identifier getTexture(SettlerEntity entity) {
        int index = Math.max(0, Math.min(textures.length - 1, entity.getLevel() - 1));
        return textures[index];
    }
}
