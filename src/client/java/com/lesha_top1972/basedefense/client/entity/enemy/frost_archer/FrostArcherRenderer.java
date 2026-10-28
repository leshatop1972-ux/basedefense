package com.lesha_top1972.basedefense.client.entity.enemy.frost_archer;

import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponFeature;
import com.lesha_top1972.basedefense.entity.enemy.frost_archer.FrostArcherEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Морозная лучница: модель приподнята над землёй и плавно покачивается (парит), тень остаётся на земле. */
public class FrostArcherRenderer extends MobEntityRenderer<FrostArcherEntity, FrostArcherModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "frost_archer"), "main");

    public static final EntityModelLayer BOW =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "frost_archer_bow"), "main");

    private static final Identifier TEXTURE =
            new Identifier(Basedefense.MOD_ID, AssetGroups.texture("frost_archer.png"));
    private static final Identifier GLOW =
            new Identifier(Basedefense.MOD_ID, AssetGroups.texture("frost_archer_glowmask.png"));

    public FrostArcherRenderer(EntityRendererFactory.Context context) {
        super(context, new FrostArcherModel(context.getPart(LAYER)), 0.45f);
        this.addFeature(new GlowLayer<>(this, GLOW));
        // лук — отдельная модель с текстурой лучницы
        this.addFeature(new WeaponFeature<>(this, context.getPart(BOW), TEXTURE, GLOW));
    }

    @Override
    public void render(FrostArcherEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        float bob = MathHelper.sin((entity.age + tickDelta) * 0.08f) * 0.05f;
        matrices.translate(0.0, FrostArcherEntity.HOVER + bob, 0.0);
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        matrices.pop();
    }

    @Override
    public Identifier getTexture(FrostArcherEntity entity) {
        return TEXTURE;
    }
}
