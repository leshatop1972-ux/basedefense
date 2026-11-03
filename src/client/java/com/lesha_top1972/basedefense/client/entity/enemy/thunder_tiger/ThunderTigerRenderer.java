package com.lesha_top1972.basedefense.client.entity.enemy.thunder_tiger;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.bedrock.AssetGroups;
import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.entity.enemy.thunder_tiger.ThunderTigerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Отрисовка «Громового тигра»: модель, светящиеся уголёк сигары и кольца, тесак отдельной моделью; частицы — TigerClientFx. */
public class ThunderTigerRenderer extends MobEntityRenderer<ThunderTigerEntity, ThunderTigerModel> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "thunder_tiger"), "main");
    public static final EntityModelLayer BLADE = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "thunder_tiger_blade"), "main");
    public static final float SCALE = 1.0f;
    static final Identifier TEX = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("thunder_tiger.png"));
    static final Identifier BLADE_TEX = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("thunder_tiger_blade.png"));

    public ThunderTigerRenderer(EntityRendererFactory.Context context) {
        super(context, new ThunderTigerModel(context.getPart(LAYER)), 0.6f);
        this.addFeature(new GlowLayer<>(this, new Identifier(Basedefense.MOD_ID, AssetGroups.texture("thunder_tiger_glowmask.png"))));
        this.addFeature(new TigerBladeFeature(this, context.getPart(BLADE), BLADE_TEX));
    }

    @Override
    protected void scale(ThunderTigerEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTexture(ThunderTigerEntity entity) {
        return TEX;
    }

    @Override
    public void render(ThunderTigerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        TigerClientFx.tick(entity);
    }

    @Override
    protected float getLyingAngle(ThunderTigerEntity entity) {
        return 80f;
    }
}
