package com.lesha_top1972.basedefense.client.entity.enemy.thunder_tiger;

import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponTrack;
import com.lesha_top1972.basedefense.entity.enemy.thunder_tiger.ThunderTigerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Тесак тигра — как WeaponFeature (точка хвата по костям, поворот из дорожки), плюс:
 * раскалённый клинок (свечение поверх: золото — неостановимый удар, пламя — заряд ульты, тлеющий красный — свирепые
 * патроны) и точки клинка/сигары в мире для частиц (TigerClientFx).
 */
public class TigerBladeFeature extends FeatureRenderer<ThunderTigerEntity, ThunderTigerModel> {

    /** Точки на модели тесака (Bedrock, px): основание клинка, середина, остриё, обух у трубок. */
    private static final float[] BASE = {0f, 8f, -0.5f};
    private static final float[] MID = {0f, 20f, -0.5f};
    private static final float[] TIP = {0f, 33f, 0f};
    private static final float[] EXHAUST = {0f, 10f, 1.5f};
    /** Кончик сигары в системе кости cigar (Java, px). */
    private static final float[] CIGAR = {0f, 0f, -4.2f};

    private final ModelPart weapon;
    private final Identifier texture;
    private final Vector3f tmp = new Vector3f();
    private final Vector4f v4 = new Vector4f();

    public TigerBladeFeature(FeatureRendererContext<ThunderTigerEntity, ThunderTigerModel> context, ModelPart weaponRoot,
                             Identifier texture) {
        super(context);
        this.weapon = weaponRoot;
        this.texture = texture;
    }

    private Vec3d world(Matrix4f m, float x, float y, float z, Vec3d cam) {
        this.v4.set(x, y, z, 1f).mul(m);
        return new Vec3d(cam.x + this.v4.x, cam.y + this.v4.y, cam.z + this.v4.z);
    }

    private Vec3d bladePoint(Matrix4f m, float[] p, Vec3d cam) {
        return this.world(m, p[0] / 16f, (24f - p[1]) / 16f, p[2] / 16f, cam);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ThunderTigerEntity entity,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw,
                       float headPitch) {
        ThunderTigerModel model = this.getContextModel();
        Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        // кончик сигары
        matrices.push();
        for (ModelPart p : model.cigarChain) {
            p.rotate(matrices);
        }
        entity.cigarTip = this.world(matrices.peek().getPositionMatrix(), CIGAR[0] / 16f, CIGAR[1] / 16f, CIGAR[2] / 16f, cam);
        matrices.pop();

        WeaponTrack tr = model.weaponTrack();
        model.poseWeapon(0, this.weapon);
        MatrixStack chain = new MatrixStack();
        for (ModelPart p : model.handChain()) {
            p.rotate(chain);
        }
        chain.translate(tr.handX / 16f, tr.handY / 16f, tr.handZ / 16f);
        chain.peek().getPositionMatrix().getTranslation(this.tmp);
        matrices.push();
        matrices.translate(this.tmp.x, this.tmp.y, this.tmp.z);
        matrices.multiply(model.weaponRotation());
        matrices.translate(tr.gripX / 16f, tr.gripY / 16f - 1.5f, tr.gripZ / 16f);
        int overlay = LivingEntityRenderer.getOverlay(entity, 0f);
        this.weapon.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(this.texture)), light,
                overlay, 1f, 1f, 1f, 1f);
        // свечение клинка поверх
        int glow = entity.getGlow();
        float pulse = 0.75f + 0.25f * MathHelper.sin(animationProgress * 0.6f);
        if (glow == 1) {
            this.weapon.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(this.texture)),
                    15728880, OverlayTexture.DEFAULT_UV, 1f, 0.85f, 0.35f, 0.55f * pulse);
        } else if (glow == 2) {
            this.weapon.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(this.texture)),
                    15728880, OverlayTexture.DEFAULT_UV, 1f, 0.45f, 0.1f, 0.6f * pulse);
        } else if (entity.isSavage()) {
            this.weapon.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(this.texture)),
                    15728880, OverlayTexture.DEFAULT_UV, 1f, 0.25f, 0.1f, 0.18f * pulse);
        }
        // точки клинка в мире (для частиц)
        Matrix4f m = matrices.peek().getPositionMatrix();
        entity.bladeBase = this.bladePoint(m, BASE, cam);
        entity.bladeMid = this.bladePoint(m, MID, cam);
        entity.bladeTip = this.bladePoint(m, TIP, cam);
        entity.bladeExhaust = this.bladePoint(m, EXHAUST, cam);
        entity.bladeAge = entity.age;
        matrices.pop();
    }
}
