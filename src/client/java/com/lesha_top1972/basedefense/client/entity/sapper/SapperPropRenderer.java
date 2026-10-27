package com.lesha_top1972.basedefense.client.entity.sapper;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.nabat.NabatRing;
import com.lesha_top1972.basedefense.entity.SapperBombEntity;
import com.lesha_top1972.basedefense.entity.SapperChargeEntity;
import com.lesha_top1972.basedefense.entity.SapperSmokeEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Предметы подкопщика одной моделью Bedrock: бомба‑банка (вращается в полёте, на земле — кольцо‑метка,
 * сжимается за 0.6 с), подрывной заряд на стене и дымовая банка.
 */
public class SapperPropRenderer<T extends Entity> extends EntityRenderer<T> {

    public static final EntityModelLayer BOMB = layer("sapper_bomb");
    public static final EntityModelLayer CHARGE = layer("sapper_charge");
    public static final EntityModelLayer SMOKE = layer("sapper_smoke");

    private final ModelPart model;
    private final Identifier tex;
    private final RenderLayer glow;

    private static EntityModelLayer layer(String name) {
        return new EntityModelLayer(new Identifier(Basedefense.MOD_ID, name), "main");
    }

    public SapperPropRenderer(EntityRendererFactory.Context context, EntityModelLayer layer, String name) {
        super(context);
        this.model = context.getPart(layer);
        this.tex = new Identifier(Basedefense.MOD_ID, "textures/entity/" + name + ".png");
        this.glow = RenderLayer.getEyes(new Identifier(Basedefense.MOD_ID, "textures/entity/" + name + "_glowmask.png"));
    }

    public static EntityRendererFactory<SapperBombEntity> bomb() {
        return c -> new SapperPropRenderer<>(c, BOMB, "sapper_bomb");
    }

    public static EntityRendererFactory<SapperChargeEntity> charge() {
        return c -> new SapperPropRenderer<>(c, CHARGE, "sapper_charge");
    }

    public static EntityRendererFactory<SapperSmokeEntity> smoke() {
        return c -> new SapperPropRenderer<>(c, SMOKE, "sapper_smoke");
    }

    @Override
    public void render(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        float age = entity.age + tickDelta;
        float s = 1.0f;
        matrices.push();
        if (entity instanceof SapperBombEntity b) {
            s = 1.4f;
            float k = b.fuse(tickDelta);
            if (b.landedTicks() >= 0) {
                NabatRing.draw(matrices, vcp, 0.0, 2.5f * (1.3f - 0.3f * k), (int) (70 + 170 * k), age * 6f);
                // шипит и подрагивает
                matrices.translate(0, 0.14, 0);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(age * 2.3f) * 6f * k));
            } else {
                matrices.translate(0, 0.14, 0);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 25f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(age * 31f));
            }
        } else if (entity instanceof SapperChargeEntity c) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - c.face().asRotation()));
            float k = c.fuse(tickDelta);
            if (k > 0.7f) {
                float jit = (k - 0.7f) * 0.06f;
                matrices.translate(MathHelper.sin(age * 9f) * jit, 0, 0);
            }
        } else if (entity instanceof SapperSmokeEntity) {
            s = 1.2f;
            matrices.translate(0, 0.14, 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getId() * 37f));
        }
        matrices.scale(-s, -s, s);
        matrices.translate(0, -1.5, 0);
        this.model.render(matrices, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(this.tex)), light,
                OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        this.model.render(matrices, vcp.getBuffer(this.glow), 15728640, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
    }

    @Override
    public Identifier getTexture(T entity) {
        return this.tex;
    }
}
