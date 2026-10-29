package com.lesha_top1972.basedefense.client.entity.enemy.curb;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.bedrock.AssetGroups;
import com.lesha_top1972.basedefense.entity.enemy.curb.CurbCaseEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Чёрный чемодан Бори228: корпус 12×8×4, ручка сверху, два серебристых замка (текстура — tools/curb_gen.py,
 * case_texture). В полёте кувыркается, после приземления лежит плашмя.
 */
public class CurbCaseRenderer extends EntityRenderer<CurbCaseEntity> {

    private static final Identifier TEXTURE = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("curb_case.png"));
    private final ModelPart model;

    public CurbCaseRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.3f;
        this.model = createModel().createModel();
    }

    /** Координаты в пикселях, центр корпуса в (0, 0, 0), ручка — по +y. */
    private static TexturedModelData createModel() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        Dilation none = Dilation.NONE;
        root.addChild("body", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-6f, -4f, -2f, 12f, 8f, 4f, none)
                .uv(0, 14).cuboid(-2.5f, 5.5f, -0.5f, 5f, 1f, 1f, none)
                .uv(0, 18).cuboid(-2.5f, 4f, -0.5f, 1f, 1.5f, 1f, none)
                .uv(0, 18).cuboid(1.5f, 4f, -0.5f, 1f, 1.5f, 1f, none)
                .uv(16, 14).cuboid(-4.5f, 2.2f, -2.4f, 2f, 1.5f, 0.5f, none)
                .uv(16, 14).cuboid(2.5f, 2.2f, -2.4f, 2f, 1.5f, 0.5f, none),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 32, 32);
    }

    @Override
    public void render(CurbCaseEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        if (entity.clientLandedAge < 0) {
            // кувырок назад через ручку
            matrices.translate(0, 0.3, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-(entity.age + tickDelta) * 26f));
        } else {
            // лежит плашмя (замками вверх)
            matrices.translate(0, 0.13, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
        }
        this.model.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light,
                OverlayTexture.DEFAULT_UV);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(CurbCaseEntity entity) {
        return TEXTURE;
    }
}
