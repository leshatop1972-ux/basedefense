package com.lesha_top1972.basedefense.client.entity.enemy.lancer;

import com.lesha_top1972.basedefense.client.entity.bedrock.AssetGroups;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.enemy.lancer.FallingLanceEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Ледяная стрела: рисуется как ванильная стрела (крест из плоскостей + торец), но своей
 * текстурой (textures/entity/falling_lance.png), чуть крупнее и всегда светится.
 */
public class FallingLanceRenderer extends EntityRenderer<FallingLanceEntity> {

    private static final Identifier TEXTURE = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("falling_lance.png"));

    public FallingLanceRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(FallingLanceEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()) - 90.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch())));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45.0f));
        matrices.scale(0.13f, 0.13f, 0.13f);
        matrices.translate(-4.0f, 0.0f, 0.0f);
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(TEXTURE));
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        Matrix3f n = entry.getNormalMatrix();
        // торец (хвост)
        vertex(m, n, vc, -7, -2, -2, 0.0f, 0.15625f, -1, 0, 0, full);
        vertex(m, n, vc, -7, -2, 2, 0.15625f, 0.15625f, -1, 0, 0, full);
        vertex(m, n, vc, -7, 2, 2, 0.15625f, 0.3125f, -1, 0, 0, full);
        vertex(m, n, vc, -7, 2, -2, 0.0f, 0.3125f, -1, 0, 0, full);
        vertex(m, n, vc, -7, 2, -2, 0.0f, 0.15625f, 1, 0, 0, full);
        vertex(m, n, vc, -7, 2, 2, 0.15625f, 0.15625f, 1, 0, 0, full);
        vertex(m, n, vc, -7, -2, 2, 0.15625f, 0.3125f, 1, 0, 0, full);
        vertex(m, n, vc, -7, -2, -2, 0.0f, 0.3125f, 1, 0, 0, full);
        // крест из четырёх плоскостей
        for (int i = 0; i < 4; i++) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
            vertex(m, n, vc, -8, -2, 0, 0.0f, 0.0f, 0, 1, 0, full);
            vertex(m, n, vc, 8, -2, 0, 0.5f, 0.0f, 0, 1, 0, full);
            vertex(m, n, vc, 8, 2, 0, 0.5f, 0.15625f, 0, 1, 0, full);
            vertex(m, n, vc, -8, 2, 0, 0.0f, 0.15625f, 0, 1, 0, full);
        }
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private static void vertex(Matrix4f m, Matrix3f n, VertexConsumer vc, int x, int y, int z,
                               float u, float v, int nx, int ny, int nz, int light) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(n, nx, ny, nz).next();
    }

    @Override
    public Identifier getTexture(FallingLanceEntity entity) {
        return TEXTURE;
    }
}
