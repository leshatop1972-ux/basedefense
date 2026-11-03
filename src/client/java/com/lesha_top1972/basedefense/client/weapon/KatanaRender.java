package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.weapon.fp.FpAnim;
import com.lesha_top1972.basedefense.client.weapon.fp.FpArms;
import com.lesha_top1972.basedefense.client.weapon.fp.FpFx;
import com.lesha_top1972.basedefense.client.weapon.fp.FpMesh;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.weapon.KatanaCombat;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Iterator;

/**
 * Катана: модель — сетка (assets/basedefense/mesh/katana.json, изогнутый клинок, гарда, оплётка, ножны;
 * tools/katana_gen.py), текстура textures/entity/weapon/katana.png.
 * <p>
 * 1‑е лицо: руки игрока + катана (+ ножны при зарядке) по клипу KatanaClient, след клинка (лента по прошлым
 * положениям из того же клипа), свечение при зарядке, блик готовности.
 * 3‑е лицо: катана по позе KatanaClient.applyModel, ножны у бедра, след. У остальных (стойки, мобы) — как меч.
 */
public final class KatanaRender {

    static final Identifier TEXTURE = new Identifier(Basedefense.MOD_ID, "textures/entity/weapon/katana.png");
    /** Белый участок атласа (для свечения). */
    private static final FpFx FX = new FpFx(TEXTURE, 36f / 128f, 62f / 128f);
    private static final int TRAIL_N = 14;
    /** Цвет следа (холодный белый). */
    private static final float TR_R = 0.72f;
    private static final float TR_G = 0.82f;
    private static final float TR_B = 1.0f;

    private static FpMesh mesh;

    private KatanaRender() {
    }

    static FpMesh mesh() {
        if (mesh == null) {
            mesh = FpMesh.load("katana");
        }
        return mesh;
    }

    public static boolean isKatana(ItemStack stack) {
        return stack.isOf(ModItems.KATANA);
    }

    /** Нарисовать часть модели в точке p с поворотом q; scale — блоков на 1 px модели. */
    private static void part(String name, Vector3f p, Quaternionf q, float scale, MatrixStack matrices,
                             VertexConsumerProvider vcp, int light, int overlay, boolean glint) {
        matrices.push();
        matrices.translate(p.x, p.y, p.z);
        matrices.multiply(q);
        matrices.scale(scale, scale, scale);
        VertexConsumer vc = ItemRenderer.getDirectItemGlintConsumer(vcp, RenderLayer.getEntityCutoutNoCull(TEXTURE), false, glint);
        mesh().render(name, matrices, vc, light, overlay, 0xFFFFFFFF);
        matrices.pop();
    }

    // =====================================================================
    // 1‑е лицо (HeldItemRenderer.renderFirstPersonItem)
    // =====================================================================
    public static boolean renderFirstPerson(AbstractClientPlayerEntity player, Hand hand, ItemStack item,
                                            float equipProgress, MatrixStack matrices, VertexConsumerProvider vcp,
                                            int light) {
        if (!KatanaCombat.holding(player)) {
            return false;
        }
        if (hand != Hand.MAIN_HAND) {
            return true;                                   // вторая рука держит рукоять — её предмет прячем
        }
        if (!isKatana(item)) {
            return false;
        }
        FpAnim a = KatanaClient.anim();
        FpAnim.Pose pose = KatanaClient.pose();
        float s = a.scale / 16f;
        if (pose.saya > 0.01f) {
            part("saya", pose.sp, pose.sq, s, matrices, vcp, light, OverlayTexture.DEFAULT_UV, false);
        }
        part("katana", pose.g, pose.q, s, matrices, vcp, light, OverlayTexture.DEFAULT_UV, item.hasGlint());
        Vector3f bladeR = pose.q.transform(new Vector3f(0, 1, 0));
        FpArms.render(player, matrices, vcp, light, pose.g, bladeR, pose.ar, pose.lp, pose.la, pose.al, a.scale, a.armWidth);
        // свечение клинка при зарядке и блик готовности
        if (pose.glow > 0.02f) {
            matrices.push();
            matrices.translate(pose.g.x, pose.g.y, pose.g.z);
            matrices.multiply(pose.q);
            matrices.scale(s, s, s);
            int c = (int) (pose.glow * 120f);
            mesh().render("katana", matrices, FX.buffer(vcp), 0xF000F0, OverlayTexture.DEFAULT_UV,
                    0xFF000000 | (c * 6 / 10) << 16 | (c * 8 / 10) << 8 | c);
            matrices.pop();
            Vector3f fl = pose.q.transform(new Vector3f(a.flare[0], a.flare[1], a.flare[2]).mul(s)).add(pose.g).mul(0.92f);
            FX.star(FX.buffer(vcp), matrices, fl, 0.13f * pose.glow * -fl.z, pose.glow, 0.7f, 0.85f, 1.0f);
        }
        // след: тот же клип в прошлые моменты (плавно при любой частоте кадров)
        FpAnim.Clip clip = KatanaClient.actionClip();
        if (clip != null && pose.trail > 0.01f) {
            trailFirstPerson(a, clip, KatanaClient.actionTime(), matrices, vcp, s);
        }
        return true;
    }

    private static void trailFirstPerson(FpAnim a, FpAnim.Clip clip, float t, MatrixStack matrices, VertexConsumerProvider vcp,
                                         float s) {
        VertexConsumer vc = FX.buffer(vcp);
        FpAnim.Pose p = new FpAnim.Pose();
        Vector3f[] base = new Vector3f[TRAIL_N + 1];
        Vector3f[] tip = new Vector3f[TRAIL_N + 1];
        float[] k = new float[TRAIL_N + 1];
        for (int i = 0; i <= TRAIL_N; i++) {
            float tt = Math.max(0f, t - a.trailLength * i / TRAIL_N);
            a.sample(clip, tt, p);
            base[i] = p.q.transform(new Vector3f(a.trailBase[0], a.trailBase[1], 0f).mul(s)).add(p.g);
            tip[i] = p.q.transform(new Vector3f(a.trailTip[0], a.trailTip[1], 0f).mul(s)).add(p.g);
            float fade = 1f - (float) i / TRAIL_N;
            k[i] = fade * fade * p.trail * 0.55f;
        }
        for (int i = 0; i < TRAIL_N; i++) {
            float[] b0 = {TR_R * k[i] * 0.3f, TR_G * k[i] * 0.3f, TR_B * k[i] * 0.3f};
            float[] t0 = {TR_R * k[i], TR_G * k[i], TR_B * k[i]};
            float[] b1 = {TR_R * k[i + 1] * 0.3f, TR_G * k[i + 1] * 0.3f, TR_B * k[i + 1] * 0.3f};
            float[] t1 = {TR_R * k[i + 1], TR_G * k[i + 1], TR_B * k[i + 1]};
            FX.quad(vc, matrices, base[i], tip[i], tip[i + 1], base[i + 1], b0, t0, t1, b1);
        }
    }

    // =====================================================================
    // 3‑е лицо (HeldItemFeatureRenderer.renderItem, матрица — пространство модели игрока)
    // =====================================================================
    public static boolean renderThirdPerson(LivingEntity entity, ItemStack stack, Arm arm, MatrixStack matrices,
                                            VertexConsumerProvider vcp, int light) {
        if (!(entity instanceof PlayerEntity pl) || !KatanaCombat.holding(pl)) {
            return false;
        }
        if (arm != entity.getMainArm()) {
            return true;                                   // предмет второй руки прячем — она на рукояти
        }
        KatanaClient.State st = KatanaClient.get(entity.getId());
        if (st == null || !st.posed) {
            return false;
        }
        int overlay = LivingEntityRenderer.getOverlay(entity, 0f);
        float sc = KatanaKeys.SCALE_3P / 16f;
        part("saya", st.saya.p, st.saya.q, sc, matrices, vcp, light, overlay, false);
        part("katana", st.katana.p, st.katana.q, sc, matrices, vcp, light, overlay, stack.hasGlint());
        trailThirdPerson(st, sc, matrices, vcp);
        return true;
    }

    private static void trailThirdPerson(KatanaClient.State st, float sc, MatrixStack matrices, VertexConsumerProvider vcp) {
        FpAnim a = KatanaClient.anim();
        float now = KatanaClient.now();
        float on = KatanaClient.trailTP(st, now);
        Vector3f b = st.katana.q.transform(new Vector3f(a.trailBase[0], a.trailBase[1], 0f).mul(sc)).add(st.katana.p);
        Vector3f t = st.katana.q.transform(new Vector3f(a.trailTip[0], a.trailTip[1], 0f).mul(sc)).add(st.katana.p);
        float[] last = st.trail.peekLast();
        if (last == null || now - last[0] > 0.05f) {
            st.trail.addLast(new float[]{now, b.x, b.y, b.z, t.x, t.y, t.z, on});
        }
        Iterator<float[]> it = st.trail.iterator();
        while (it.hasNext()) {
            if (now - it.next()[0] > 3f) {
                it.remove();
            }
        }
        if (st.trail.size() < 2) {
            return;
        }
        VertexConsumer vc = FX.buffer(vcp);
        float[] prev = null;
        for (float[] e : st.trail) {
            if (prev != null && (prev[7] > 0f || e[7] > 0f)) {
                float k0 = trailK(now - prev[0]) * prev[7];
                float k1 = trailK(now - e[0]) * e[7];
                if (k0 > 0.001f || k1 > 0.001f) {
                    FX.quad(vc, matrices, new Vector3f(prev[1], prev[2], prev[3]), new Vector3f(prev[4], prev[5], prev[6]),
                            new Vector3f(e[4], e[5], e[6]), new Vector3f(e[1], e[2], e[3]),
                            col(k0 * 0.3f), col(k0), col(k1), col(k1 * 0.3f));
                }
            }
            prev = e;
        }
    }

    private static float trailK(float ageTicks) {
        float x = 1f - ageTicks / 3f;
        return x <= 0f ? 0f : x * x * 0.5f;
    }

    private static float[] col(float k) {
        return new float[]{TR_R * k, TR_G * k, TR_B * k};
    }

    // =====================================================================
    // Покой у не‑игроков: как меч в руке (HeldItemRenderer.renderItem)
    // =====================================================================
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
        BloodSwordPose.Frame f = BloodSwordPose.rest(fp, leftHanded, new BloodSwordPose.Frame());
        part("katana", f.p, f.q, KatanaKeys.SCALE_3P / 16f, matrices, vcp, light, OverlayTexture.DEFAULT_UV, stack.hasGlint());
        return true;
    }
}
