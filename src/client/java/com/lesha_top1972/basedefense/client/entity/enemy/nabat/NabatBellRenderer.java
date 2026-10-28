package com.lesha_top1972.basedefense.client.entity.enemy.nabat;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.entity.bedrock.GlowLayer;
import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatBellEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

/** Колокол‑опора: пока падает — под ним светится кольцо‑тень. */
public class NabatBellRenderer extends MobEntityRenderer<NabatBellEntity, NabatBellModel<NabatBellEntity>> {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "nabat_bell"), "main");
    static final Identifier TEX = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("nabat_bell.png"));
    static final Identifier GLOW = new Identifier(Basedefense.MOD_ID, AssetGroups.texture("nabat_bell_glowmask.png"));

    public NabatBellRenderer(EntityRendererFactory.Context context) {
        super(context, new NabatBellModel<>(context.getPart(LAYER)), 0.7f);
        this.addFeature(new GlowLayer<>(this, GLOW));
    }

    @Override
    public void render(NabatBellEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light) {
        this.getModel().shake = entity.hurtTime > 0 ? entity.hurtTime / 10f : 0f;
        super.render(entity, yaw, tickDelta, matrices, vcp, light);
        double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        int gy = entity.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(entity.getX()),
                MathHelper.floor(entity.getZ()));
        double dy = gy - y;
        if (dy < -0.6) {
            float k = (float) MathHelper.clamp(1.0 + dy / 16.0, 0.2, 1.0);
            NabatRing.draw(matrices, vcp, dy, 2.2f, (int) (90 + 140 * k), entity.age * 3f);
        }
    }

    @Override
    public Identifier getTexture(NabatBellEntity entity) {
        return TEX;
    }
}
