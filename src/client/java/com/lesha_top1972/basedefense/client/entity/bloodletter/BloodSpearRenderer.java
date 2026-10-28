package com.lesha_top1972.basedefense.client.entity.bloodletter;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.BloodSpearEntity;
import com.lesha_top1972.basedefense.entity.BloodletterEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Кровавое копьё (geo/bloodletter_spear, остриё в −z). Над мобом рисуется по общей формуле веера
 * ({@link BloodletterEntity#hoverPos}) от сглаженной позиции моба — без дрожания; появляясь, вырастает из капли
 * (с лёгким перехлёстом), перед броском отходит назад и дрожит, в полёте смотрит по скорости,
 * воткнувшись — тает в конце.
 */
public class BloodSpearRenderer extends EntityRenderer<BloodSpearEntity> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "bloodletter_spear"), "main");
    private static final Identifier TEX = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("bloodletter_spear.png"));
    private static final RenderLayer GLOW = RenderLayer.getEyes(new Identifier(Basedefense.MOD_ID, AssetGroups.texture("bloodletter_spear_glowmask.png")));
    private static final float SCALE = 0.8f;

    private final ModelPart model;

    public BloodSpearRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = context.getPart(LAYER);
    }

    private static float backOut(float x) {
        float c = 1.9f;
        float k = x - 1f;
        return 1f + (c + 1f) * k * k * k + c * k * k;
    }

    @Override
    public void render(BloodSpearEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        float age = e.age + tickDelta;
        int st = e.state();
        Vec3d dir = e.clientDir;
        if (dir == null) {
            return;
        }
        if (e.clientPrevDir != null) {
            Vec3d d = e.clientPrevDir.multiply(1 - tickDelta).add(dir.multiply(tickDelta));
            dir = d.lengthSquared() > 1e-4 ? d.normalize() : dir;
        }
        float s = SCALE;
        matrices.push();
        if (st == BloodSpearEntity.HOVER || st == BloodSpearEntity.PRIME) {
            BloodletterEntity o = e.letter();
            if (o != null) {
                Vec3d feet = o.getLerpedPos(tickDelta);
                float oy = MathHelper.lerpAngleDegrees(tickDelta, o.prevYaw, o.getYaw());
                Vec3d want = BloodletterEntity.hoverPos(feet, oy, e.slot(), age);
                Vec3d cur = e.getLerpedPos(tickDelta);
                matrices.translate(want.x - cur.x, want.y - cur.y, want.z - cur.z);
            }
            float grow = MathHelper.clamp(age / 10f, 0f, 1f);
            s *= grow >= 1f ? 1f : Math.max(0.05f, backOut(grow));
            if (st == BloodSpearEntity.PRIME) {
                float k = MathHelper.clamp((e.age - e.clientStateAge + tickDelta) / 6f, 0f, 1f);
                double back = 0.55 * (1 - (1 - k) * (1 - k));
                matrices.translate(-dir.x * back, -dir.y * back, -dir.z * back);
                float j = 0.025f * k;
                matrices.translate(MathHelper.sin(age * 5.1f) * j, MathHelper.sin(age * 6.3f) * j, MathHelper.sin(age * 4.7f) * j);
            }
        } else if (st == BloodSpearEntity.STUCK) {
            float since = e.age - e.clientStateAge + tickDelta;
            float k = MathHelper.clamp((since - 14f) / 10f, 0f, 1f);
            s *= 1f - 0.85f * k;
        }
        float theta = (float) Math.atan2(-dir.x, -dir.z);
        float phi = (float) Math.asin(MathHelper.clamp(dir.y, -1.0, 1.0));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(theta));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(phi));
        if (st == BloodSpearEntity.HOVER || st == BloodSpearEntity.PRIME) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(age * 4f));      // медленно вращается вокруг оси
        }
        matrices.scale(-s, -s, s);
        this.model.render(matrices, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), light, OverlayTexture.DEFAULT_UV,
                1f, 1f, 1f, 1f);
        this.model.render(matrices, vcp.getBuffer(GLOW), 15728640, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        matrices.pop();
        super.render(e, yaw, tickDelta, matrices, vcp, light);
    }

    @Override
    public Identifier getTexture(BloodSpearEntity entity) {
        return TEX;
    }
}
