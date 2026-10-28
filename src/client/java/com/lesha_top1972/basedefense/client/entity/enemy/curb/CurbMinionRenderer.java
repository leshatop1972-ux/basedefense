package com.lesha_top1972.basedefense.client.entity.enemy.curb;

import com.lesha_top1972.basedefense.client.entity.bedrock.AssetGroups;
import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.enemy.curb.CurbMinionEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Отрисовка прислужника Бордюра: модель + светящийся знак на лице и струи. */
public class CurbMinionRenderer extends MobEntityRenderer<CurbMinionEntity, CurbModel<CurbMinionEntity>> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "curb_minion"), "main");
    private static final Identifier TEXTURE = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("curb_minion.png"));
    private static final Identifier GLOW = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("curb_minion_glowmask.png"));

    public CurbMinionRenderer(EntityRendererFactory.Context context) {
        super(context, new CurbModel<>(context.getPart(LAYER), "curb_minion"), 0.5f);
        this.addFeature(new GlowLayer<>(this, GLOW));
    }

    @Override
    public Identifier getTexture(CurbMinionEntity entity) {
        return TEXTURE;
    }
}
