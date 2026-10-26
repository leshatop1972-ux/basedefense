package com.lesha_top1972.basedefense.client.entity.nabat;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.NabatEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.WeakHashMap;

/**
 * Отрисовка «Набата»: модель ×1.6, светящаяся маска (в 3‑й фазе — треснувший колокол), молот отдельной
 * моделью, мягкий крен на поворотах в полёте (без «переворота» при смерти — у него своя анимация),
 * рябь «Купола» в месте попадания.
 */
public class NabatRenderer extends MobEntityRenderer<NabatEntity, NabatModel> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "nabat"), "main");
    public static final EntityModelLayer MAUL = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "nabat_maul"), "main");
    private static final Identifier TEX = id("textures/entity/nabat.png");
    private static final Identifier TEX3 = id("textures/entity/nabat_p3.png");
    private static final Identifier GLOW = id("textures/entity/nabat_glowmask.png");
    private static final Identifier GLOW3 = id("textures/entity/nabat_p3_glowmask.png");
    private static final Identifier MAUL_TEX = id("textures/entity/nabat_maul.png");
    private static final Identifier MAUL_GLOW = id("textures/entity/nabat_maul_glowmask.png");
    private static final Identifier RIPPLE = id("textures/misc/nabat_ripple.png");

    /** Плавный крен по каждому Набату (клиент). */
    private final WeakHashMap<NabatEntity, float[]> bank = new WeakHashMap<>();

    public NabatRenderer(EntityRendererFactory.Context context) {
        super(context, new NabatModel(context.getPart(LAYER)), 2.2f);
        this.addFeature(new PhaseGlow(this));
        ModelPart maul = context.getPart(MAUL);
        this.addFeature(new WeaponFeature<>(this, maul, MAUL_TEX, MAUL_GLOW));
    }

    private static Identifier id(String path) {
        return new Identifier(Basedefense.MOD_ID, path);
    }

    @Override
    protected void scale(NabatEntity entity, MatrixStack matrices, float amount) {
        float k = NabatEntity.MODEL_SCALE;
        matrices.scale(k, k, k);
    }

    @Override
    protected void setupTransforms(NabatEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw,
                                   float tickDelta) {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw));
        // крен в полёте: по скорости поворота корпуса, плавно
        float[] b = this.bank.computeIfAbsent(entity, e -> new float[]{0f, bodyYaw, 0f});
        if (b[2] != animationProgress) {
            float turn = MathHelper.wrapDegrees(bodyYaw - b[1]);
            b[1] = bodyYaw;
            float want = entity.isGrounded() || entity.deathTime > 0 ? 0f : MathHelper.clamp(turn * 4f, -14f, 14f);
            b[0] = MathHelper.lerp(0.08f, b[0], want);
            b[2] = animationProgress;
        }
        if (Math.abs(b[0]) > 0.05f) {
            matrices.translate(0, 3.5, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(b[0]));
            matrices.translate(0, -3.5, 0);
        }
    }

    @Override
    protected float getLyingAngle(NabatEntity entity) {
        return 0f;
    }

    @Override
    public Identifier getTexture(NabatEntity entity) {
        return entity.isPhase3() ? TEX3 : TEX;
    }

    @Override
    public void render(NabatEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        // рябь купола: светлое кольцо расходится от точки попадания (0.4 с)
        float t = (entity.age + tickDelta - entity.clientDomeHitAge) / 8f;
        if (t >= 0 && t < 1) {
            matrices.push();
            matrices.translate(entity.clientDomeHit.x, entity.clientDomeHit.y, entity.clientDomeHit.z);
            matrices.multiply(this.dispatcher.getRotation());
            float s = 0.8f + t * 2.4f;
            matrices.scale(s, s, s);
            int alpha = (int) (200 * (1f - t) * (1f - t));
            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(RIPPLE));
            MatrixStack.Entry e = matrices.peek();
            quad(e.getPositionMatrix(), e.getNormalMatrix(), vc, alpha);
            matrices.pop();
        }
    }

    static void quad(Matrix4f m, Matrix3f n, VertexConsumer vc, int alpha) {
        v(m, n, vc, -0.5f, -0.5f, 0f, 1f, alpha);
        v(m, n, vc, 0.5f, -0.5f, 1f, 1f, alpha);
        v(m, n, vc, 0.5f, 0.5f, 1f, 0f, alpha);
        v(m, n, vc, -0.5f, 0.5f, 0f, 0f, alpha);
    }

    private static void v(Matrix4f m, Matrix3f n, VertexConsumer vc, float x, float y, float u, float vv, int alpha) {
        vc.vertex(m, x, y, 0).color(255, 220, 160, alpha).texture(u, vv).overlay(OverlayTexture.DEFAULT_UV)
                .light(15728880).normal(n, 0, 1, 0).next();
    }

    /** Светящаяся маска: обычная или «треснувший колокол» 3‑й фазы. */
    static class PhaseGlow extends FeatureRenderer<NabatEntity, NabatModel> {
        private final RenderLayer normal = RenderLayer.getEyes(GLOW);
        private final RenderLayer cracked = RenderLayer.getEyes(GLOW3);

        PhaseGlow(FeatureRendererContext<NabatEntity, NabatModel> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, NabatEntity entity,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw,
                           float headPitch) {
            VertexConsumer vc = vertexConsumers.getBuffer(entity.isPhase3() ? this.cracked : this.normal);
            this.getContextModel().render(matrices, vc, 15728640, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        }
    }

}
