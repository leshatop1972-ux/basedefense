package com.lesha_top1972.basedefense.client.weapon.fp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Руки игрока от 1‑го лица (его скин, рукава второго слоя): кулак — в точке hand, рукоять проходит сквозь кулак
 * (клинок — в сторону большого пальца), рука тянется к «плечу» anchor за краем экрана и вытягивается, пока её верх
 * не уйдёт за камеру — обрезанного конца руки на экране не видно. Та же формула — в tools/fpview.py (превью).
 */
public final class FpArms {

    /** px от плеча вдоль руки до середины кулака. */
    static final float GRIP_Y = 8.5f;
    /** px выше плеча (верх кубика руки). */
    static final float TOP = 2.0f;
    static final float STRETCH_MAX = 1.8f;

    private FpArms() {
    }

    /** Поворот руки (столбцы X, Y, Z): Y — от плеча к кисти, −Z — к клинку. */
    static Matrix3f frame(Vector3f hand, Vector3f blade, Vector3f anchor, Matrix3f out) {
        Vector3f y = new Vector3f(hand).sub(anchor).normalize();
        Vector3f z = new Vector3f(blade).sub(new Vector3f(y).mul(blade.dot(y))).negate();
        if (z.lengthSquared() < 1e-6f) {
            z = new Vector3f(y).cross(1f, 0f, 0f);
        }
        z.normalize();
        Vector3f x = new Vector3f(y).cross(z);
        return out.set(x, y, z);
    }

    static float stretch(Vector3f hand, Vector3f y, float scale) {
        float l0 = (GRIP_Y + TOP) * scale / 16f;
        float dz = -y.z;
        if (dz <= 0.05f) {
            return STRETCH_MAX;
        }
        float need = (-0.04f - hand.z) / dz;
        return MathHelper.clamp(need / l0, 1f, STRETCH_MAX);
    }

    /** Обе руки игрока. hand/blade/anchor — в пространстве камеры (блоки). */
    public static void render(AbstractClientPlayerEntity player, MatrixStack matrices, VertexConsumerProvider vcp, int light,
                              Vector3f handR, Vector3f bladeR, Vector3f anchorR,
                              Vector3f handL, Vector3f bladeL, Vector3f anchorL, float scale, float width) {
        if (player.isInvisible()) {
            return;
        }
        EntityRenderer<?> r = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (!(r instanceof PlayerEntityRenderer pr)) {
            return;
        }
        PlayerEntityModel<AbstractClientPlayerEntity> pm = pr.getModel();
        boolean slim = "slim".equals(player.getModel());
        Identifier skin = player.getSkinTexture();
        arm(matrices, vcp, light, skin, pm.rightArm, pm.rightSleeve, player.isPartVisible(PlayerModelPart.RIGHT_SLEEVE),
                handR, bladeR, anchorR, slim ? -0.5f : -1f, scale, width);
        arm(matrices, vcp, light, skin, pm.leftArm, pm.leftSleeve, player.isPartVisible(PlayerModelPart.LEFT_SLEEVE),
                handL, bladeL, anchorL, slim ? 0.5f : 1f, scale, width);
    }

    private static void arm(MatrixStack matrices, VertexConsumerProvider vcp, int light, Identifier skin, ModelPart arm,
                            ModelPart sleeve, boolean sleeveVisible, Vector3f hand, Vector3f blade, Vector3f anchor,
                            float cx, float scale, float width) {
        Matrix3f rot = frame(hand, blade, anchor, new Matrix3f());
        Vector3f y = rot.getColumn(1, new Vector3f());
        float k = stretch(hand, y, scale);
        Vector3f local = new Vector3f(cx * width * scale / 16f, GRIP_Y * k * scale / 16f, 0f);
        Vector3f origin = rot.transform(local).negate().add(hand);
        ModelTransform savedArm = arm.getTransform();
        ModelTransform savedSleeve = sleeve.getTransform();
        boolean armVis = arm.visible;
        boolean sleeveVis = sleeve.visible;
        arm.setTransform(ModelTransform.NONE);
        sleeve.setTransform(ModelTransform.NONE);
        arm.visible = true;
        sleeve.visible = sleeveVisible;
        matrices.push();
        matrices.translate(origin.x, origin.y, origin.z);
        matrices.multiply(new Quaternionf().setFromNormalized(rot));
        matrices.scale(width * scale, k * scale, width * scale);
        arm.render(matrices, vcp.getBuffer(RenderLayer.getEntitySolid(skin)), light, OverlayTexture.DEFAULT_UV);
        if (sleeveVisible) {
            sleeve.render(matrices, vcp.getBuffer(RenderLayer.getEntityTranslucent(skin)), light, OverlayTexture.DEFAULT_UV);
        }
        matrices.pop();
        arm.setTransform(savedArm);
        sleeve.setTransform(savedSleeve);
        arm.visible = armVis;
        sleeve.visible = sleeveVis;
    }
}
