package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.item.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Меч кровожада в руке — своя модель (geo/blood_greatsword, tools/bt_sword_gen.py) со светящейся маской.
 * <ul>
 *   <li>в покое — как ванильный меч в одной руке (вместо плоской картинки);</li>
 *   <li>замах и удар от 3‑го лица — меч в точке кисти по позе {@link BloodSwordPose};</li>
 *   <li>от 1‑го лица — меч и обе руки рисуются здесь по ключам 1‑го лица.</li>
 * </ul>
 * В инвентаре, на земле и в рамке — обычная иконка (models/item/bloodthirster_sword.json).
 */
public final class BloodSwordRender {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(Basedefense.MOD_ID, "blood_greatsword"), "main");
    private static final Identifier TEXTURE = new Identifier(Basedefense.MOD_ID, "textures/entity/blood_greatsword.png");
    private static final Identifier GLOW = new Identifier(Basedefense.MOD_ID, "textures/entity/blood_greatsword_glowmask.png");
    private static final float REST_GLOW = 0.5f;

    private static ModelPart model;
    private static final float[] FP_KEYS = new float[13];

    private BloodSwordRender() {
    }

    private static ModelPart model() {
        if (model == null) {
            model = MinecraftClient.getInstance().getEntityModelLoader().getModelPart(LAYER);
        }
        return model;
    }

    /** Модель могла перезагрузиться (F3+T) — взять заново. */
    public static void reload() {
        model = null;
    }

    public static boolean isSword(ItemStack stack) {
        return stack.isOf(ModItems.BLOODTHIRSTER_SWORD);
    }

    /** Нарисовать меч: matrices — в системе меча (начало — кисть у гарды, +y — клинок, блоки). */
    static void renderSword(MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay, ItemStack stack,
                            float scale, float glow) {
        ModelPart part = model();
        float sc = scale / BloodSwordKeys.MODEL_K;
        matrices.push();
        matrices.scale(sc, sc, sc);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180f));
        matrices.translate(0f, -1.5f, 0f);                      // загрузчик сдвигает модель на 24 px
        part.render(matrices, ItemRenderer.getDirectItemGlintConsumer(vcp,
                RenderLayer.getEntityCutoutNoCull(TEXTURE), false, stack.hasGlint()), light, overlay);
        part.render(matrices, vcp.getBuffer(RenderLayer.getEyes(GLOW)), 0xF000F0, OverlayTexture.DEFAULT_UV,
                glow, glow, glow, 1f);
        matrices.pop();
    }

    private static void apply(MatrixStack matrices, BloodSwordPose.Frame f) {
        matrices.translate(f.p.x, f.p.y, f.p.z);
        matrices.multiply(new Quaternionf(f.q));
    }

    private static float glow(LivingEntity entity) {
        BloodSwordAnim a = entity != null ? BloodSwordClient.anim(entity.getId()) : null;
        return a != null ? a.glow(BloodSwordClient.now()) : REST_GLOW;
    }

    // =====================================================================
    // Покой: вместо плоской картинки в руке (HeldItemRenderer.renderItem)
    // =====================================================================
    public static boolean renderRest(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
                                     MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        if (!isSword(stack)) {
            return false;
        }
        boolean fp = mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
        boolean tp = mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
        if (!fp && !tp) {
            return false;
        }
        BloodSwordPose.Frame f = BloodSwordPose.rest(fp, leftHanded, new BloodSwordPose.Frame());
        matrices.push();
        apply(matrices, f);
        renderSword(matrices, vcp, light, OverlayTexture.DEFAULT_UV, stack,
                fp ? BloodSwordKeys.SCALE_1P : BloodSwordKeys.SCALE_3P, glow(entity));
        matrices.pop();
        return true;
    }

    // =====================================================================
    // 3‑е лицо: замах и удар (HeldItemFeatureRenderer.renderItem)
    // =====================================================================
    /** @return true — нарисовано здесь (или предмет второй руки спрятан), ванильное не нужно. */
    public static boolean renderThirdPerson(Object contextModel, LivingEntity entity, ItemStack stack, Arm arm,
                                            MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        BloodSwordAnim a = BloodSwordClient.anim(entity.getId());
        if (a == null || a.tpWeight <= 0.001f || !(contextModel instanceof ModelWithArms arms)) {
            return false;
        }
        if (arm != entity.getMainArm()) {
            return a.tpWeight > 0.3f;                     // меч в обеих руках — во второй руке ничего не видно
        }
        if (!isSword(stack)) {
            return false;
        }
        // покой: от кисти текущей (уже смешанной) руки — как HeldItemFeatureRenderer
        boolean left = arm == Arm.LEFT;
        MatrixStack tmp = new MatrixStack();
        arms.setArmAngle(arm, tmp);
        tmp.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
        tmp.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
        tmp.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
        apply(tmp, BloodSwordPose.rest(false, left, new BloodSwordPose.Frame()));
        BloodSwordPose.Frame rest = new BloodSwordPose.Frame();
        tmp.peek().getPositionMatrix().getTranslation(rest.p);
        rest.q.setFromNormalized(new Matrix3f(tmp.peek().getPositionMatrix()));
        BloodSwordPose.Frame f = new BloodSwordPose.Frame().blend(rest, a.tpSword, a.tpWeight);
        matrices.push();
        apply(matrices, f);
        renderSword(matrices, vcp, light, LivingEntityRenderer.getOverlay(entity, 0f), stack, BloodSwordKeys.SCALE_3P,
                a.glow(BloodSwordClient.now()));
        matrices.pop();
        return true;
    }

    // =====================================================================
    // 1‑е лицо (HeldItemRenderer.renderFirstPersonItem)
    // =====================================================================
    public static boolean renderFirstPerson(AbstractClientPlayerEntity player, Hand hand, ItemStack item,
                                            float equipProgress, MatrixStack matrices, VertexConsumerProvider vcp,
                                            int light) {
        BloodSwordAnim a = BloodSwordClient.anim(player.getId());
        if (a == null) {
            return false;
        }
        float now = BloodSwordClient.now();
        float w = a.weight(now);
        if (w <= 0.001f) {
            return false;
        }
        if (hand != Hand.MAIN_HAND) {
            return w > 0.3f;                              // вторая рука держит рукоять — её предмет прячем
        }
        if (!isSword(item)) {
            return false;
        }
        int m = player.getMainArm() == Arm.RIGHT ? 1 : -1;
        // покой — как обычный меч в руке (с «опусканием» при смене предмета)
        BloodSwordPose.Frame rest = BloodSwordPose.rest(true, m < 0, new BloodSwordPose.Frame());
        rest.p.add(m * 0.56f, -0.52f + equipProgress * -0.6f, -0.72f);
        BloodSwordPose.FP pose = BloodSwordPose.solveFirstPerson(a.firstPerson(now, FP_KEYS), m, new BloodSwordPose.FP());
        BloodSwordPose.Frame f = new BloodSwordPose.Frame().blend(rest, pose.sword, w);

        matrices.push();
        apply(matrices, f);
        renderSword(matrices, vcp, light, OverlayTexture.DEFAULT_UV, item, BloodSwordKeys.SCALE_1P, a.glow(now));
        matrices.pop();

        // руки: ведущая — у гарды, вторая — ниже на рукояти; в начале и в конце приходят из‑за края экрана
        if (!player.isInvisible()) {
            EntityRenderer<?> r = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
            if (r instanceof PlayerEntityRenderer pr) {
                PlayerEntityModel<AbstractClientPlayerEntity> pm = pr.getModel();
                boolean slim = "slim".equals(player.getModel());
                Vector3f handD = new Vector3f(f.p);
                Vector3f handO = BloodSwordPose.secondHandFirstPerson(f, new Vector3f());
                float away = (1f - w) * 0.9f;
                handD.sub(new Vector3f(pose.dirD).mul(away));
                handO.sub(new Vector3f(pose.dirO).mul(away));
                Identifier skin = player.getSkinTexture();
                boolean rightIsD = m > 0;
                renderArm(matrices, vcp, light, skin, pm.rightArm, pm.rightSleeve,
                        player.isPartVisible(PlayerModelPart.RIGHT_SLEEVE), rightIsD ? handD : handO,
                        rightIsD ? pose.dirD : pose.dirO, slim ? -0.5f : -1f);
                renderArm(matrices, vcp, light, skin, pm.leftArm, pm.leftSleeve,
                        player.isPartVisible(PlayerModelPart.LEFT_SLEEVE), rightIsD ? handO : handD,
                        rightIsD ? pose.dirO : pose.dirD, slim ? 0.5f : 1f);
            }
        }
        return true;
    }

    /** Рука от 1‑го лица: середина кулака — в hand, рука тянется против dir (к плечу). */
    private static void renderArm(MatrixStack matrices, VertexConsumerProvider vcp, int light, Identifier skin,
                                  ModelPart arm, ModelPart sleeve, boolean sleeveVisible, Vector3f hand, Vector3f dir,
                                  float hx) {
        Matrix3f rot = BloodSwordPose.basis(dir, new Vector3f(-1, 0, 0), new Matrix3f());
        Vector3f origin = rot.transform(new Vector3f(hx / 16f, BloodSwordKeys.ARM_REACH / 16f, 0f));
        origin.negate().add(hand);
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
