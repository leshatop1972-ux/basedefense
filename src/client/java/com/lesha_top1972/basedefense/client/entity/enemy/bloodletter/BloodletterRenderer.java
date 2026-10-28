package com.lesha_top1972.basedefense.client.entity.enemy.bloodletter;

import com.lesha_top1972.basedefense.client.entity.bedrock.AssetGroups;
import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.entity.enemy.bloodletter.BloodletterEntity;
import net.minecraft.client.render.Frustum;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * «Кровопускатель»: светятся рана на маске, сердце, жилы и шитьё рясы. Во время «Кровавого вихря» вокруг него
 * на 3 блоках вращается кровавый след — лента из тёмной полупрозрачной крови (blood_trail.png) и яркой
 * сердцевины (blood_trail_glow.png), к хвосту тоньше и прозрачнее. Угол головы следа — как у сервера
 * ({@link BloodletterEntity#trailYaw}), поэтому попадание совпадает с тем, что видно.
 */
public class BloodletterRenderer extends MobEntityRenderer<BloodletterEntity, BloodletterModel> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "bloodletter"), "main");
    private static final Identifier TEX = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("bloodletter.png"));
    private static final Identifier TRAIL = new Identifier(Basedefense.MOD_ID, "textures/fx/blood_trail.png");
    private static final Identifier TRAIL_GLOW = new Identifier(Basedefense.MOD_ID, "textures/fx/blood_trail_glow.png");
    /** Длина следа (градусы) и число сегментов ленты. */
    private static final float TAIL = 170f;
    private static final int SEGS = 32;

    public BloodletterRenderer(EntityRendererFactory.Context context) {
        super(context, new BloodletterModel(context.getPart(LAYER)), 0.55f);
        this.addFeature(new GlowLayer<>(this, new Identifier(Basedefense.MOD_ID, AssetGroups.texture("bloodletter_glowmask.png"))));
    }

    @Override
    public Identifier getTexture(BloodletterEntity entity) {
        return TEX;
    }

    private static boolean trailActive(BloodletterEntity e, float t) {
        return e.clientAction == BloodletterEntity.WHIRL && t >= BloodletterEntity.WHIRL_START - 1
                && t <= BloodletterEntity.WHIRL_END + 7;
    }

    @Override
    public boolean shouldRender(BloodletterEntity e, Frustum frustum, double x, double y, double z) {
        if (trailActive(e, e.age - e.clientActionAge)) {
            return frustum.isVisible(e.getBoundingBox().expand(BloodletterEntity.TRAIL_RADIUS + 1.0, 1.0,
                    BloodletterEntity.TRAIL_RADIUS + 1.0));
        }
        return super.shouldRender(e, frustum, x, y, z);
    }

    @Override
    public void render(BloodletterEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        super.render(e, yaw, tickDelta, matrices, vcp, light);
        float t = e.age + tickDelta - e.clientActionAge;
        if (trailActive(e, t)) {
            this.trail(e, t, matrices, vcp);
        }
    }

    private void trail(BloodletterEntity e, float t, MatrixStack matrices, VertexConsumerProvider vcp) {
        float fadeIn = MathHelper.clamp((t - BloodletterEntity.WHIRL_START + 1) / 3f, 0f, 1f);
        float fadeOut = MathHelper.clamp((BloodletterEntity.WHIRL_END + 7 - t) / 7f, 0f, 1f);
        float alpha = fadeIn * fadeOut;
        if (alpha <= 0.01f) {
            return;
        }
        float head = BloodletterEntity.trailYaw(e.clientWhirlYaw, t);
        // в начале след короткий — «вытягивается» из когтей; после остановки хвост догоняет голову
        float grow = MathHelper.clamp((t - BloodletterEntity.WHIRL_START + 1) / 8f, 0.15f, 1f);
        float shrink = MathHelper.clamp((BloodletterEntity.WHIRL_END + 7 - t) / 7f, 0f, 1f);
        float len = TAIL * grow * (t > BloodletterEntity.WHIRL_END ? shrink : 1f);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        Matrix3f n = entry.getNormalMatrix();
        VertexConsumer body = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(TRAIL));
        this.ribbon(body, m, n, head, len, t, alpha, false);
        VertexConsumer glow = vcp.getBuffer(RenderLayer.getEyes(TRAIL_GLOW));
        this.ribbon(glow, m, n, head, len, t, alpha, true);
    }

    /** Лента от хвоста (u = 0) к голове (u = 1) по дуге радиусом ≈3, высота 0.1–1.5 (к хвосту ниже и тоньше). */
    private void ribbon(VertexConsumer vc, Matrix4f m, Matrix3f n, float head, float len, float t, float alpha, boolean glow) {
        double r0 = BloodletterEntity.TRAIL_RADIUS;
        for (int i = 0; i < SEGS; i++) {
            float s0 = i / (float) SEGS;
            float s1 = (i + 1) / (float) SEGS;
            float[] a = this.column(head, len, s0, t, r0);
            float[] b = this.column(head, len, s1, t, r0);
            int ca = glow ? (int) (255 * alpha) : 255;
            int aa = glow ? 255 : (int) (235 * alpha);
            // обе стороны ленты
            this.quad(vc, m, n, a, b, s0, s1, ca, aa);
            this.quad(vc, m, n, b, a, s1, s0, ca, aa);
        }
    }

    /** Точка столбца ленты: x, z, низ, верх. */
    private float[] column(float head, float len, float s, float t, double r0) {
        float yaw = (head - len * (1f - s)) * MathHelper.RADIANS_PER_DEGREE;
        double r = r0 + 0.18 * Math.sin(s * 9.0 + t * 0.7) - 0.25 * (1f - s);
        float x = (float) (-MathHelper.sin(yaw) * r);
        float z = (float) (MathHelper.cos(yaw) * r);
        float mid = 0.8f + 0.12f * MathHelper.sin(s * 7f + t * 0.5f);
        float half = 0.25f + 0.45f * (float) Math.pow(s, 0.6);
        return new float[]{x, z, mid - half, mid + half};
    }

    private void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] a, float[] b, float ua, float ub, int c, int alpha) {
        int light = 0xF000F0;
        vc.vertex(m, a[0], a[2], a[1]).color(c, c, c, alpha).texture(ua, 1f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, 1, 0).next();
        vc.vertex(m, b[0], b[2], b[1]).color(c, c, c, alpha).texture(ub, 1f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, 1, 0).next();
        vc.vertex(m, b[0], b[3], b[1]).color(c, c, c, alpha).texture(ub, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, 1, 0).next();
        vc.vertex(m, a[0], a[3], a[1]).color(c, c, c, alpha).texture(ua, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, 1, 0).next();
    }
}
