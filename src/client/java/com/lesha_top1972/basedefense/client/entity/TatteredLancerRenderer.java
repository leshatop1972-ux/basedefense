package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.fx.LancerFx;
import com.lesha_top1972.basedefense.client.fx.LancerTrack;
import com.lesha_top1972.basedefense.entity.TatteredLancerEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Отрисовка босса «Оборванный улан»: модель + светящаяся маска (глаза, остриё копья).
 * Каждый кадр по настоящей позе модели запоминает, где остриё копья и глаза (LancerTrack — для следа,
 * бликов и огня из глаз), а на рывках рисует 3 светящихся фантома‑послеобраза позади.
 */
public class TatteredLancerRenderer extends MobEntityRenderer<TatteredLancerEntity, TatteredLancerModel> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "tattered_lancer"), "main");

    private static final Identifier TEXTURE =
            new Identifier(Basedefense.MOD_ID, "textures/entity/tattered_lancer.png");
    private static final Identifier GLOW =
            new Identifier(Basedefense.MOD_ID, "textures/entity/tattered_lancer_glowmask.png");

    /** Точки в системе кости (пиксели, как в Blockbench, y вниз): остриё, начало следа, глаза. */
    private static final Vector3f TIP = new Vector3f(0f, 0f, -34.2f);
    private static final Vector3f TRAIL_START = new Vector3f(0f, 0f, -24.0f);
    private static final Vector3f EYE_L = new Vector3f(1.9f, -4.4f, -4.3f);
    private static final Vector3f EYE_R = new Vector3f(-1.9f, -4.4f, -4.3f);

    public TatteredLancerRenderer(EntityRendererFactory.Context context) {
        super(context, new TatteredLancerModel(context.getPart(LAYER)), 0.6f);
        this.addFeature(new GlowLayer<>(this, GLOW));
    }

    @Override
    public Identifier getTexture(TatteredLancerEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TatteredLancerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        if (entity.deathTime > 0 || entity.isInvisible()) {
            return;
        }
        // поза модели уже выставлена (setAngles внутри super.render) — считаем точки в мире
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
        Vec3d pos = entity.getLerpedPos(tickDelta);
        Matrix4f lance = chainMatrix(this.model.lanceChain(), bodyYaw);
        Matrix4f head = chainMatrix(this.model.headChain(), bodyYaw);
        LancerTrack track = LancerFx.track(entity);
        float time = entity.age + tickDelta;
        track.sample(time, pos, bodyYaw, point(lance, TIP, pos), point(lance, TRAIL_START, pos),
                point(head, EYE_L, pos), point(head, EYE_R, pos));

        // фантомы на рывках
        float ghost = ghostStrength(entity, tickDelta);
        if (ghost <= 0f) {
            return;
        }
        boolean crimson = entity.clientAction == TatteredLancerEntity.GRAND;
        for (int i = 1; i <= 3; i++) {
            int k = track.ghostIndex(time - i * 1.5f);
            if (k < 0) {
                continue;
            }
            Vec3d gp = track.posAt(k);
            if (gp.squaredDistanceTo(pos) < 0.09) {
                continue;
            }
            float a = ghost * (0.6f - i * 0.15f);
            float r = crimson ? 1.0f * a : 0.35f * a;
            float g = crimson ? 0.25f * a : 0.6f * a;
            float b = crimson ? 0.35f * a : 1.0f * a;
            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEyes(TEXTURE));
            matrices.push();
            matrices.translate(gp.x - pos.x, gp.y - pos.y, gp.z - pos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - track.yawAt(k)));
            matrices.scale(-1.0f, -1.0f, 1.0f);
            matrices.translate(0.0f, -1.501f, 0.0f);
            this.model.render(matrices, vc, 0xF000F0, OverlayTexture.DEFAULT_UV, r, g, b, 1.0f);
            matrices.pop();
        }
    }

    /** Насколько ярки фантомы сейчас (0 — не рисовать): рывки тарана, «тройного тарана», отскок, прыжок, подброс. */
    private static float ghostStrength(TatteredLancerEntity e, float tickDelta) {
        int a = e.clientAction;
        float t = e.age + tickDelta - e.clientActionAge;
        return switch (a) {
            case TatteredLancerEntity.CHARGE -> t >= 11 && t < 27 ? 1f : 0f;
            case TatteredLancerEntity.GRAND -> ((t >= 16 && t < 28) || (t >= 31 && t < 43) || (t >= 46 && t < 58)
                    || (t >= 68 && t < 74)) ? 1f : 0f;
            case TatteredLancerEntity.BACKSTEP -> t >= 1 && t < 11 ? 0.8f : 0f;
            case TatteredLancerEntity.UPPERCUT -> t >= 8 && t < 17 ? 0.7f : 0f;
            case TatteredLancerEntity.LEAP -> t >= TatteredLancerEntity.LEAP_WINDUP && !e.isOnGround() ? 0.6f : 0f;
            default -> 0f;
        };
    }

    /** Матрица от ног сущности до кости: те же преобразования, что делает LivingEntityRenderer + кости модели. */
    private static Matrix4f chainMatrix(ModelPart[] chain, float bodyYaw) {
        MatrixStack m = new MatrixStack();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw));
        m.scale(-1.0f, -1.0f, 1.0f);
        m.translate(0.0f, -1.501f, 0.0f);
        for (ModelPart part : chain) {
            part.rotate(m);
        }
        return new Matrix4f(m.peek().getPositionMatrix());
    }

    private static Vec3d point(Matrix4f m, Vector3f localPixels, Vec3d pos) {
        Vector3f v = new Vector3f(localPixels).div(16f);
        m.transformPosition(v);
        return pos.add(v.x, v.y, v.z);
    }
}
