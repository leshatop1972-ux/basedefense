package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.weapon.fp.FpAnim;
import com.lesha_top1972.basedefense.client.weapon.fp.FpArms;
import com.lesha_top1972.basedefense.client.weapon.fp.FpFx;
import com.lesha_top1972.basedefense.client.weapon.fp.FpMesh;
import com.lesha_top1972.basedefense.weapon.KatanaCombat;
import com.lesha_top1972.basedefense.weapon.KatanaItem;
import com.lesha_top1972.basedefense.weapon.KatanaVariant;
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

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

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
    private static final int TRAIL_N = 14;

    /** Вид катаны на экране: модель, текстура, маска свечения, цвета следа и свечения зарядки. */
    record Look(KatanaVariant variant, FpMesh mesh, Identifier texture, Identifier glow, FpFx fx, float[] trail, float[] charge) {
    }

    private static final Map<KatanaVariant, Look> LOOKS = new EnumMap<>(KatanaVariant.class);

    private KatanaRender() {
    }

    static Look look(KatanaVariant v) {
        return LOOKS.computeIfAbsent(v, k -> {
            Identifier tex = new Identifier(Basedefense.MOD_ID, "textures/entity/weapon/" + k.model + ".png");
            Identifier glow = k.ancient ? new Identifier(Basedefense.MOD_ID, "textures/entity/weapon/" + k.model + "_glow.png") : null;
            float[] trail = k.ancient ? new float[]{0.78f, 0.36f, 1.0f} : new float[]{0.72f, 0.82f, 1.0f};
            float[] charge = k.ancient ? new float[]{0.85f, 0.45f, 1.0f} : new float[]{0.7f, 0.85f, 1.0f};
            // белый участок атласа (36, 62) — для свечения
            return new Look(k, FpMesh.load(k.model), tex, glow, new FpFx(tex, 36f / 128f, 62f / 128f), trail, charge);
        });
    }

    static Look look(ItemStack stack) {
        return look(stack.getItem() instanceof KatanaItem k ? k.variant() : KatanaVariant.BASE);
    }

    public static boolean isKatana(ItemStack stack) {
        return stack.getItem() instanceof KatanaItem;
    }

    /** Нарисовать часть модели в точке p с поворотом q; scale — блоков на 1 px модели (+ свечение древней). */
    private static void part(Look lk, String name, Vector3f p, Quaternionf q, float scale, MatrixStack matrices,
                             VertexConsumerProvider vcp, int light, int overlay, boolean glint) {
        matrices.push();
        matrices.translate(p.x, p.y, p.z);
        matrices.multiply(q);
        matrices.scale(scale, scale, scale);
        VertexConsumer vc = ItemRenderer.getDirectItemGlintConsumer(vcp, RenderLayer.getEntityCutoutNoCull(lk.texture()), false, glint);
        lk.mesh().render(name, matrices, vc, light, overlay, 0xFFFFFFFF);
        if (lk.glow() != null) {
            lk.mesh().render(name, matrices, vcp.getBuffer(RenderLayer.getEyes(lk.glow())), 0xF000F0, overlay, 0xFFFFFFFF);
        }
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
        Look lk = look(item);
        FpFx fx = lk.fx();
        float s = a.scale / 16f;
        if (pose.saya > 0.01f) {
            part(lk, "saya", pose.sp, pose.sq, s, matrices, vcp, light, OverlayTexture.DEFAULT_UV, false);
        }
        part(lk, "katana", pose.g, pose.q, s, matrices, vcp, light, OverlayTexture.DEFAULT_UV, item.hasGlint());
        Vector3f bladeR = pose.q.transform(new Vector3f(0, 1, 0));
        FpArms.render(player, matrices, vcp, light, pose.g, bladeR, pose.ar, pose.lp, pose.la, pose.al, a.scale, a.armWidth);
        // свечение клинка при зарядке и блик готовности
        if (pose.glow > 0.02f) {
            matrices.push();
            matrices.translate(pose.g.x, pose.g.y, pose.g.z);
            matrices.multiply(pose.q);
            matrices.scale(s, s, s);
            float[] cc = lk.charge();
            float g = pose.glow * 0.47f;
            lk.mesh().render("katana", matrices, fx.buffer(vcp), 0xF000F0, OverlayTexture.DEFAULT_UV,
                    0xFF000000 | (int) (255 * g * cc[0]) << 16 | (int) (255 * g * cc[1]) << 8 | (int) (255 * g * cc[2]));
            matrices.pop();
            Vector3f fl = pose.q.transform(new Vector3f(a.flare[0], a.flare[1], a.flare[2]).mul(s)).add(pose.g).mul(0.92f);
            fx.star(fx.buffer(vcp), matrices, fl, 0.13f * pose.glow * -fl.z, pose.glow, cc[0], cc[1], cc[2]);
        }
        // след: тот же клип в прошлые моменты (плавно при любой частоте кадров)
        FpAnim.Clip clip = KatanaClient.actionClip();
        if (clip != null && pose.trail > 0.01f) {
            trailFirstPerson(lk, a, clip, KatanaClient.actionTime(), matrices, vcp, s);
        }
        return true;
    }

    private static void trailFirstPerson(Look lk, FpAnim a, FpAnim.Clip clip, float t, MatrixStack matrices,
                                         VertexConsumerProvider vcp, float s) {
        VertexConsumer vc = lk.fx().buffer(vcp);
        float[] c = lk.trail();
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
            FpFx fx = lk.fx();
            fx.quad(vc, matrices, base[i], tip[i], tip[i + 1], base[i + 1], col(c, k[i] * 0.3f), col(c, k[i]),
                    col(c, k[i + 1]), col(c, k[i + 1] * 0.3f));
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
        Look lk = look(stack);
        part(lk, "saya", st.saya.p, st.saya.q, sc, matrices, vcp, light, overlay, false);
        part(lk, "katana", st.katana.p, st.katana.q, sc, matrices, vcp, light, overlay, stack.hasGlint());
        trailThirdPerson(lk, st, sc, matrices, vcp);
        return true;
    }

    private static void trailThirdPerson(Look lk, KatanaClient.State st, float sc, MatrixStack matrices, VertexConsumerProvider vcp) {
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
        VertexConsumer vc = lk.fx().buffer(vcp);
        float[] c = lk.trail();
        float[] prev = null;
        for (float[] e : st.trail) {
            if (prev != null && (prev[7] > 0f || e[7] > 0f)) {
                float k0 = trailK(now - prev[0]) * prev[7];
                float k1 = trailK(now - e[0]) * e[7];
                if (k0 > 0.001f || k1 > 0.001f) {
                    lk.fx().quad(vc, matrices, new Vector3f(prev[1], prev[2], prev[3]), new Vector3f(prev[4], prev[5], prev[6]),
                            new Vector3f(e[4], e[5], e[6]), new Vector3f(e[1], e[2], e[3]),
                            col(c, k0 * 0.3f), col(c, k0), col(c, k1), col(c, k1 * 0.3f));
                }
            }
            prev = e;
        }
    }

    private static float trailK(float ageTicks) {
        float x = 1f - ageTicks / 3f;
        return x <= 0f ? 0f : x * x * 0.5f;
    }

    private static float[] col(float[] c, float k) {
        return new float[]{c[0] * k, c[1] * k, c[2] * k};
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
        part(look(stack), "katana", f.p, f.q, KatanaKeys.SCALE_3P / 16f, matrices, vcp, light, OverlayTexture.DEFAULT_UV,
                stack.hasGlint());
        return true;
    }
}
