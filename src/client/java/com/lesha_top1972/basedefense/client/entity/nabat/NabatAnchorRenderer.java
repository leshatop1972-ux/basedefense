package com.lesha_top1972.basedefense.client.entity.nabat;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.GlowLayer;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.entity.NabatAnchorEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Якорь узника и золотая цепь от него к улану (цепь провисает и звенья чередуются, как настоящие). */
public class NabatAnchorRenderer extends MobEntityRenderer<NabatAnchorEntity, NabatAnchorRenderer.AnchorModel> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "nabat_anchor"), "main");
    private static final Identifier TEX = new Identifier(Basedefense.MOD_ID, "textures/entity/nabat_anchor.png");
    private static final Identifier CHAIN = new Identifier("minecraft", "textures/block/chain.png");
    private static final DustParticleEffect TEAL = new DustParticleEffect(new Vector3f(0.3f, 0.95f, 0.9f), 0.7f);

    public NabatAnchorRenderer(EntityRendererFactory.Context context) {
        super(context, new AnchorModel(context.getPart(LAYER)), 0.5f);
        this.addFeature(new GlowLayer<>(this, new Identifier(Basedefense.MOD_ID, "textures/entity/nabat_anchor_glowmask.png")));
    }

    @Override
    public void render(NabatAnchorEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
        Entity lancer = entity.getWorld().getEntityById(entity.lancerNetId());
        if (lancer == null || !lancer.isAlive()) {
            return;
        }
        Vec3d a = entity.getLerpedPos(tickDelta).add(0, 0.8, 0);
        Vec3d b = lancer.getLerpedPos(tickDelta).add(0, 1.05, 0);
        Vec3d base = entity.getLerpedPos(tickDelta);
        Vec3d d = b.subtract(a);
        double len = d.length();
        if (len < 0.1) {
            return;
        }
        int links = Math.max(2, (int) (len / 0.22));
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(CHAIN));
        MatrixStack.Entry e = matrices.peek();
        Matrix4f m = e.getPositionMatrix();
        Matrix3f n = e.getNormalMatrix();
        double sag = Math.max(0, 1.6 - len * 0.08) * (1.0 - Math.min(1.0, len / 14.0) * 0.8);
        Vec3d prev = a.subtract(base);
        for (int i = 1; i <= links; i++) {
            double t = i / (double) links;
            Vec3d p = a.add(d.multiply(t)).add(0, -sag * 4 * t * (1 - t), 0).subtract(base);
            Vec3d seg = p.subtract(prev);
            Vec3d up = Math.abs(seg.normalize().y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
            Vec3d side = seg.crossProduct(up).normalize();
            if (i % 2 == 0) {
                side = seg.crossProduct(side).normalize();
            }
            Vec3d w = side.multiply(0.07);
            int li = entity.cracks() > 0 && i % 5 == entity.cracks() ? 15728880 : light;
            quad(m, n, vc, prev.subtract(w), prev.add(w), p.add(w), p.subtract(w), li);
            prev = p;
        }
        if (entity.getRandom().nextInt(4) == 0) {
            double t = entity.getRandom().nextDouble();
            Vec3d p = a.add(d.multiply(t)).add(0, -sag * 4 * t * (1 - t), 0);
            entity.getWorld().addParticle(TEAL, p.x, p.y, p.z, 0, 0.01, 0);
        }
    }

    private static void quad(Matrix4f m, Matrix3f n, VertexConsumer vc, Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, int light) {
        // обе стороны (цепь видна с любой стороны)
        v(m, n, vc, p0, 0f, 0f, light);
        v(m, n, vc, p1, 0.1875f, 0f, light);
        v(m, n, vc, p2, 0.1875f, 0.25f, light);
        v(m, n, vc, p3, 0f, 0.25f, light);
        v(m, n, vc, p3, 0f, 0.25f, light);
        v(m, n, vc, p2, 0.1875f, 0.25f, light);
        v(m, n, vc, p1, 0.1875f, 0f, light);
        v(m, n, vc, p0, 0f, 0f, light);
    }

    private static void v(Matrix4f m, Matrix3f n, VertexConsumer vc, Vec3d p, float u, float vv, int light) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 226, 150, 255).texture(u, vv)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, 1, 0).next();
    }

    @Override
    public Identifier getTexture(NabatAnchorEntity entity) {
        return TEX;
    }

    @Override
    public boolean shouldRender(NabatAnchorEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
        return true;       // цепь длинная — не прятать, когда сам якорь за кадром
    }

    /** Модель якоря: реликварий слегка покачивается. */
    public static class AnchorModel extends BedrockEntityModel<NabatAnchorEntity> {
        private final ModelPart anchor;

        public AnchorModel(ModelPart root) {
            super(root);
            this.anchor = this.bone("anchor");
        }

        @Override
        public void setAngles(NabatAnchorEntity entity, float limbAngle, float limbDistance, float animationProgress,
                              float headYaw, float headPitch) {
            this.resetPose();
            float k = entity.cracks() * 0.02f;
            this.anchor.roll = MathHelper.sin(animationProgress * 0.9f) * k;
        }
    }
}
