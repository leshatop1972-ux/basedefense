package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.combat.KatanaCombat;
import com.lesha_top1972.basedefense.item.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Катана в руке: своя модель (geo/katana, ножны — geo/katana_saya; tools/katana_gen.py).
 * У игрока с катаной — всегда своя поза: в покое катана в ножнах у бедра, рука на рукояти; удары — по ключам.
 * У остальных (стойка для брони, мобы) — как обычный меч в руке.
 */
public final class KatanaRender {

    public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "katana"), "main");
    public static final EntityModelLayer SAYA = new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "katana_saya"), "main");
    private static final Identifier TEXTURE = new Identifier(Basedefense.MOD_ID, "textures/entity/katana.png");
    private static final Identifier SAYA_TEXTURE = new Identifier(Basedefense.MOD_ID, "textures/entity/katana_saya.png");

    private static ModelPart katana;
    private static ModelPart saya;

    private KatanaRender() {
    }

    public static boolean isKatana(ItemStack stack) {
        return stack.isOf(ModItems.KATANA);
    }

    private static void draw(ModelPart part, Identifier tex, MatrixStack matrices, VertexConsumerProvider vcp, int light,
                             int overlay, boolean glint, float scale) {
        matrices.push();
        matrices.scale(scale, scale, scale);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180f));
        matrices.translate(0f, -1.5f, 0f);                      // загрузчик сдвигает модель на 24 px
        part.render(matrices, ItemRenderer.getDirectItemGlintConsumer(vcp, RenderLayer.getEntityCutoutNoCull(tex), false, glint),
                light, overlay);
        matrices.pop();
    }

    private static void drawAt(boolean isSaya, BloodSwordPose.Frame f, MatrixStack matrices, VertexConsumerProvider vcp,
                               int light, int overlay, boolean glint, float scale) {
        if (katana == null) {
            katana = MinecraftClient.getInstance().getEntityModelLoader().getModelPart(LAYER);
            saya = MinecraftClient.getInstance().getEntityModelLoader().getModelPart(SAYA);
        }
        matrices.push();
        matrices.translate(f.p.x, f.p.y, f.p.z);
        matrices.multiply(new Quaternionf(f.q));
        draw(isSaya ? saya : katana, isSaya ? SAYA_TEXTURE : TEXTURE, matrices, vcp, light, overlay, glint && !isSaya, scale);
        matrices.pop();
    }

    /** Покой у не‑игроков: как ванильный меч в руке (HeldItemRenderer.renderItem). */
    public static boolean renderRest(ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
                                     VertexConsumerProvider vcp, int light) {
        if (!isKatana(stack)) {
            return false;
        }
        boolean fp = mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
        boolean tp = mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
        if (!fp && !tp) {
            return false;
        }
        BloodSwordPose.Frame f = BloodSwordPose.rest(false, leftHanded, new BloodSwordPose.Frame());
        drawAt(false, f, matrices, vcp, light, OverlayTexture.DEFAULT_UV, stack.hasGlint(),
                fp ? KatanaKeys.SCALE_1P : KatanaKeys.SCALE_3P);
        return true;
    }

    /** 3‑е лицо, игрок (HeldItemFeatureRenderer.renderItem): катана по позе из setAngles и ножны у бедра. */
    public static boolean renderThirdPerson(LivingEntity entity, ItemStack stack, Arm arm, MatrixStack matrices,
                                            VertexConsumerProvider vcp, int light) {
        if (!(entity instanceof PlayerEntity pl) || !KatanaCombat.holding(pl) || arm != entity.getMainArm()) {
            return false;
        }
        KatanaClient.State st = KatanaClient.get(entity.getId());
        if (st == null || !st.posed) {
            return false;
        }
        int overlay = LivingEntityRenderer.getOverlay(entity, 0f);
        drawAt(true, st.saya, matrices, vcp, light, overlay, false, KatanaKeys.SCALE_3P);
        drawAt(false, st.katana, matrices, vcp, light, overlay, stack.hasGlint(), KatanaKeys.SCALE_3P);
        return true;
    }

    /** 1‑е лицо (HeldItemRenderer.renderFirstPersonItem): ножны у бедра, катана и правая рука по ключам. */
    public static boolean renderFirstPerson(AbstractClientPlayerEntity player, Hand hand, ItemStack item,
                                            float equipProgress, MatrixStack matrices, VertexConsumerProvider vcp,
                                            int light) {
        if (hand != Hand.MAIN_HAND || !isKatana(item)) {
            return false;
        }
        KatanaClient.State st = KatanaClient.state(player.getId());
        float now = KatanaClient.now();
        float[] v = KatanaClient.values(st, KatanaKeys.FP_IDLE[0], KatanaKeys.FP_SWINGS, now, new float[12]);
        float dip = equipProgress * -0.6f;
        BloodSwordPose.Frame f = KatanaClient.frame(new Vector3f(v[0], v[1] + dip, v[2]), new Vector3f(v[3], v[4], v[5]),
                new Vector3f(v[6], v[7], v[8]), new BloodSwordPose.Frame());
        float[] i = KatanaKeys.FP_IDLE[0];
        BloodSwordPose.Frame s = KatanaClient.frame(new Vector3f(i[1], i[2] + dip, i[3]), new Vector3f(i[4], i[5], i[6]),
                new Vector3f(i[7], i[8], i[9]), new BloodSwordPose.Frame());
        drawAt(true, s, matrices, vcp, light, OverlayTexture.DEFAULT_UV, false, KatanaKeys.SCALE_1P);
        drawAt(false, f, matrices, vcp, light, OverlayTexture.DEFAULT_UV, item.hasGlint(), KatanaKeys.SCALE_1P);
        if (!player.isInvisible()) {
            EntityRenderer<?> r = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
            if (r instanceof PlayerEntityRenderer pr) {
                PlayerEntityModel<AbstractClientPlayerEntity> pm = pr.getModel();
                boolean slim = "slim".equals(player.getModel());
                Vector3f dir = new Vector3f(-v[9], -v[10], -v[11]).normalize();
                BloodSwordRender.renderArm(matrices, vcp, light, player.getSkinTexture(), pm.rightArm, pm.rightSleeve,
                        player.isPartVisible(PlayerModelPart.RIGHT_SLEEVE), new Vector3f(f.p), dir, slim ? -0.5f : -1f);
            }
        }
        return true;
    }
}
