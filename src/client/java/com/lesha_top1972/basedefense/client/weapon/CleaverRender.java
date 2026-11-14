package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.weapon.fp.FpAnim;
import com.lesha_top1972.basedefense.client.weapon.fp.ArmRig;
import com.lesha_top1972.basedefense.client.weapon.fp.FpFx;
import com.lesha_top1972.basedefense.client.weapon.fp.FpMesh;
import com.lesha_top1972.basedefense.weapon.CleaverCombat;
import com.lesha_top1972.basedefense.weapon.CleaverItem;
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
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Iterator;

/**
 * Тесак Громового тигра: модель — сетка assets/basedefense/mesh/cleaver.json (части: cleaver — тесак, drum — барабан,
 * loader — обойма; tools/cleaver_gen.py), текстура textures/entity/weapon/cleaver.png и маска накала cleaver_glow.png.
 * <p>
 * 1‑е лицо: суставные руки игрока (fp/ArmRig) + тесак по клипу CleaverClient; барабан крутится и откидывается, обойма в левой руке,
 * накал (кромка светится всегда, полосы/труба/сопла — от нагрева), струи пламени из сопел при выстреле, след клинка
 * (стальной или огненный). 3‑е лицо: тесак по позе CleaverClient.applyModel, накал, след.
 */
public final class CleaverRender {

    static final Identifier TEXTURE = new Identifier(Basedefense.MOD_ID, "textures/entity/weapon/cleaver.png");
    static final Identifier GLOW = new Identifier(Basedefense.MOD_ID, "textures/entity/weapon/cleaver_glow.png");
    private static final int TRAIL_N = 16;
    private static final float[] STEEL = {1.0f, 0.90f, 0.72f};
    private static final float[] FIRE = {1.0f, 0.50f, 0.12f};
    /** Кромка тлеет всегда. */
    private static final float BASE_GLOW = 0.22f;

    private static FpMesh mesh;
    private static final ArmRig.Sol SOL_R = new ArmRig.Sol();
    private static final ArmRig.Sol SOL_L = new ArmRig.Sol();
    private static final FpFx FX = new FpFx(TEXTURE, CleaverKeys.WHITE_U, CleaverKeys.WHITE_V);

    private CleaverRender() {
    }

    private static FpMesh mesh() {
        if (mesh == null) {
            mesh = FpMesh.load("cleaver");
        }
        return mesh;
    }

    public static boolean isCleaver(ItemStack stack) {
        return stack.getItem() instanceof CleaverItem;
    }

    private static int gray(float k) {
        int c = (int) (255 * Math.max(0f, Math.min(1f, k)));
        return 0xFF000000 | c << 16 | c << 8 | c;
    }

    private static float[] mix(float fire, float k) {
        return new float[]{(STEEL[0] + (FIRE[0] - STEEL[0]) * fire) * k, (STEEL[1] + (FIRE[1] - STEEL[1]) * fire) * k,
                (STEEL[2] + (FIRE[2] - STEEL[2]) * fire) * k};
    }

    /** Тесак с барабаном в точке p с поворотом q; scale — блоков на 1 px модели; open/spin — барабан; heat — накал 0..1. */
    private static void weapon(Vector3f p, Quaternionf q, float scale, float open, float spin, float heat, MatrixStack matrices,
                               VertexConsumerProvider vcp, int light, int overlay, boolean glint) {
        FpMesh m = mesh();
        matrices.push();
        matrices.translate(p.x, p.y, p.z);
        matrices.multiply(q);
        matrices.scale(scale, scale, scale);
        VertexConsumer vc = ItemRenderer.getDirectItemGlintConsumer(vcp, RenderLayer.getEntityCutoutNoCull(TEXTURE), false, glint);
        m.render("cleaver", matrices, vc, light, overlay, 0xFFFFFFFF);
        matrices.push();
        matrices.translate(CleaverKeys.DRUM_HINGE, CleaverKeys.DRUM[1], CleaverKeys.DRUM[2]);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(-open * CleaverKeys.DRUM_SWING));
        matrices.translate(CleaverKeys.DRUM[0] - CleaverKeys.DRUM_HINGE, 0f, 0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(spin * 6.2831855f));
        m.render("drum", matrices, vc, light, overlay, 0xFFFFFFFF);
        int g = gray(Math.max(BASE_GLOW, heat));
        VertexConsumer glow = vcp.getBuffer(RenderLayer.getEyes(GLOW));
        m.render("drum", matrices, glow, 0xF000F0, overlay, g);
        matrices.pop();
        m.render("cleaver", matrices, glow, 0xF000F0, overlay, g);
        matrices.pop();
    }

    // =====================================================================
    // 1‑е лицо (HeldItemRenderer.renderFirstPersonItem)
    // =====================================================================
    public static boolean renderFirstPerson(AbstractClientPlayerEntity player, Hand hand, ItemStack item,
                                            float equipProgress, MatrixStack matrices, VertexConsumerProvider vcp,
                                            int light) {
        if (!CleaverCombat.holding(player)) {
            return false;
        }
        if (hand != Hand.MAIN_HAND) {
            return true;                                   // левая рука занята тесаком/обоймой — её предмет прячем
        }
        if (!isCleaver(item)) {
            return false;
        }
        FpAnim a = CleaverClient.anim();
        FpAnim.Pose pose = CleaverClient.pose();
        float s = a.scale / 16f;
        float fire = pose.ext[2];
        float flash = pose.ext[3];
        CleaverClient.State st = CleaverClient.get(player.getId());
        float heat = Math.max(Math.max(fire, pose.glow * 0.6f), st == null ? 0f : st.heat(CleaverClient.now()));
        weapon(pose.g, pose.q, s, pose.ext[0], pose.ext[1], heat, matrices, vcp, light, OverlayTexture.DEFAULT_UV, item.hasGlint());
        if (pose.saya > 0.01f) {                           // обойма в левой руке
            matrices.push();
            matrices.translate(pose.sp.x, pose.sp.y, pose.sp.z);
            matrices.multiply(pose.sq);
            matrices.scale(s, s, s);
            mesh().render("loader", matrices, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light,
                    OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
            matrices.pop();
        }
        // руки — суставные: плечо, локоть, запястье, кисть на рукояти
        Vector3f bladeR = pose.q.transform(new Vector3f(0, 1, 0));
        Vector3f handR = pose.q.transform(new Vector3f(ArmRig.H0));
        Vector3f backR = pose.q.transform(new Vector3f(0, 0, 1));
        float lm = CleaverKeys.FP_ARM_LEN;
        ArmRig.solve(pose.ar, pose.g, handR, vec(CleaverKeys.FP_POLE_R), s, lm, bladeR, SOL_R);
        ArmRig.draw(player, SOL_R, backR, new Vector3f(1, 0, 0), s, true, a.armWidth, lm, matrices, vcp, light, OverlayTexture.DEFAULT_UV);
        Vector3f backL = new Vector3f(pose.ext[4], pose.ext[5], pose.ext[6]);
        if (backL.lengthSquared() < 1e-4f) {
            backL.set(-1, 0, 0);
        }
        Vector3f handL = new Vector3f(pose.la);
        if (handL.lengthSquared() < 1e-4f) {
            handL.set(0, 0, -1);
        }
        ArmRig.solve(pose.al, pose.lp, handL, vec(CleaverKeys.FP_POLE_L), s, lm, pose.sq.transform(new Vector3f(0, 1, 0)), SOL_L);
        ArmRig.draw(player, SOL_L, backL, new Vector3f(-1, 0, 0), s, false, a.armWidth, lm, matrices, vcp, light, OverlayTexture.DEFAULT_UV);
        VertexConsumer fx = FX.buffer(vcp);
        // выстрел: струи пламени из сопел и вспышка
        if (flash > 0.02f) {
            jets(pose.g, pose.q, s, flash, matrices, fx, 0.55f);
            float[] v = CleaverKeys.VENTS[1];
            Vector3f fl = pose.q.transform(new Vector3f(v[0], v[1], v[2]).mul(s)).add(pose.g).mul(0.92f);
            FX.star(fx, matrices, fl, 0.20f * flash * -fl.z, flash, 1.0f, 0.7f, 0.3f);
        }
        // блик готовности зарядки
        if (pose.glow > 0.02f) {
            Vector3f fl = pose.q.transform(new Vector3f(a.flare[0], a.flare[1], a.flare[2]).mul(s)).add(pose.g).mul(0.92f);
            FX.star(fx, matrices, fl, 0.12f * pose.glow * -fl.z, pose.glow, 1.0f, 0.75f, 0.35f);
        }
        // след: тот же клип в прошлые моменты (плавно при любой частоте кадров)
        FpAnim.Clip clip = CleaverClient.actionClip();
        if (clip != null && pose.trail > 0.01f) {
            trailFirstPerson(a, clip, CleaverClient.actionTime(), matrices, fx, s);
        }
        return true;
    }

    private static Vector3f vec(float[] v) {
        return new Vector3f(v[0], v[1], v[2]);
    }

    /** Струи пламени из трёх сопел: по две скрещённые ленты вдоль выхлопа. len — длина струи в блоках при g = 1. */
    private static void jets(Vector3f p, Quaternionf q, float s, float g, MatrixStack matrices, VertexConsumer vc, float len) {
        float[] hot = {1.0f * g, 0.85f * g, 0.5f * g};
        float[] mid = {1.0f * g, 0.45f * g, 0.08f * g};
        float[] off = {0f, 0f, 0f};
        Vector3f z = q.transform(new Vector3f(0, 0, 1));
        for (float[] v : CleaverKeys.VENTS) {
            Vector3f p0 = q.transform(new Vector3f(v[0], v[1], v[2]).mul(s)).add(p);
            Vector3f d = q.transform(new Vector3f(v[3], v[4], v[5]));
            Vector3f side2 = new Vector3f(d).cross(z).normalize();
            Vector3f pm = new Vector3f(d).mul(len * g * 0.45f).add(p0);
            Vector3f pe = new Vector3f(d).mul(len * g).add(p0);
            for (Vector3f side : new Vector3f[]{z, side2}) {
                Vector3f w = new Vector3f(side).mul(0.05f * (0.5f + g) * (len / 0.55f));
                Vector3f w2 = new Vector3f(w).mul(1.7f);
                Vector3f w3 = new Vector3f(w).mul(0.3f);
                FX.quad(vc, matrices, new Vector3f(p0).sub(w), new Vector3f(p0).add(w), new Vector3f(pm).add(w2),
                        new Vector3f(pm).sub(w2), hot, hot, mid, mid);
                FX.quad(vc, matrices, new Vector3f(pm).sub(w2), new Vector3f(pm).add(w2), new Vector3f(pe).add(w3),
                        new Vector3f(pe).sub(w3), mid, mid, off, off);
            }
        }
    }

    private static void trailFirstPerson(FpAnim a, FpAnim.Clip clip, float t, MatrixStack matrices, VertexConsumer vc, float s) {
        FpAnim.Pose p = new FpAnim.Pose();
        Vector3f[] base = new Vector3f[TRAIL_N + 1];
        Vector3f[] tip = new Vector3f[TRAIL_N + 1];
        float[] k = new float[TRAIL_N + 1];
        float[] f = new float[TRAIL_N + 1];
        for (int i = 0; i <= TRAIL_N; i++) {
            float tt = Math.max(0f, t - a.trailLength * i / TRAIL_N);
            a.sample(clip, tt, p);
            base[i] = p.q.transform(new Vector3f(a.trailBase[0], a.trailBase[1], 0f).mul(s)).add(p.g);
            tip[i] = p.q.transform(new Vector3f(a.trailTip[0], a.trailTip[1], 0f).mul(s)).add(p.g);
            float fade = 1f - (float) i / TRAIL_N;
            k[i] = fade * fade * p.trail * 0.6f;
            f[i] = p.ext[2];
        }
        for (int i = 0; i < TRAIL_N; i++) {
            FX.quad(vc, matrices, base[i], tip[i], tip[i + 1], base[i + 1], mix(f[i], k[i] * 0.3f), mix(f[i], k[i]),
                    mix(f[i + 1], k[i + 1]), mix(f[i + 1], k[i + 1] * 0.3f));
        }
    }

    // =====================================================================
    // 3‑е лицо (HeldItemFeatureRenderer.renderItem, матрица — пространство модели игрока)
    // =====================================================================
    public static boolean renderThirdPerson(LivingEntity entity, ItemStack stack, Arm arm, MatrixStack matrices,
                                            VertexConsumerProvider vcp, int light) {
        if (!(entity instanceof PlayerEntity pl) || !CleaverCombat.holding(pl)) {
            return false;
        }
        if (arm != entity.getMainArm()) {
            return true;                                   // предмет второй руки прячем
        }
        CleaverClient.State st = CleaverClient.get(entity.getId());
        if (st == null || !st.posed) {
            return false;
        }
        int overlay = LivingEntityRenderer.getOverlay(entity, 0f);
        float sc = CleaverKeys.SCALE_3P / 16f;
        float now = CleaverClient.now();
        float heat = st.heat(now);
        if (st.chargeKind != 0) {
            heat = Math.max(heat, Math.min(1f, (now - st.chargeStart) / 20f));
        }
        weapon(st.weapon.p, st.weapon.q, sc, CleaverClient.drumOpenTP(st, now), 0f, heat, matrices, vcp, light, overlay, stack.hasGlint());
        if (entity instanceof AbstractClientPlayerEntity acp) {      // суставные руки (ванильные спрятаны в applyModel)
            matrices.push();
            matrices.scale(1f / 16f, 1f / 16f, 1f / 16f);
            ArmRig.draw(acp, st.armR, st.backR, st.outR, 1f, true, 1f, 1f, matrices, vcp, light, overlay);
            ArmRig.draw(acp, st.armL, st.backL, st.outL, 1f, false, 1f, 1f, matrices, vcp, light, overlay);
            matrices.pop();
        }
        float a = now - st.start;
        if (CleaverClient.fiery(st) && CleaverClient.trailTP(st, now) > 0f && a < 30f) {
            jets(st.weapon.p, st.weapon.q, sc, 0.8f, matrices, FX.buffer(vcp), 0.5f);
        }
        trailThirdPerson(st, sc, now, matrices, vcp);
        return true;
    }

    private static void trailThirdPerson(CleaverClient.State st, float sc, float now, MatrixStack matrices, VertexConsumerProvider vcp) {
        FpAnim a = CleaverClient.anim();
        float on = CleaverClient.trailTP(st, now);
        Vector3f b = st.weapon.q.transform(new Vector3f(a.trailBase[0], a.trailBase[1], 0f).mul(sc)).add(st.weapon.p);
        Vector3f t = st.weapon.q.transform(new Vector3f(a.trailTip[0], a.trailTip[1], 0f).mul(sc)).add(st.weapon.p);
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
        float fire = CleaverClient.fiery(st) ? 1f : 0f;
        float[] prev = null;
        for (float[] e : st.trail) {
            if (prev != null && (prev[7] > 0f || e[7] > 0f)) {
                float k0 = trailK(now - prev[0]) * prev[7];
                float k1 = trailK(now - e[0]) * e[7];
                if (k0 > 0.001f || k1 > 0.001f) {
                    FX.quad(vc, matrices, new Vector3f(prev[1], prev[2], prev[3]), new Vector3f(prev[4], prev[5], prev[6]),
                            new Vector3f(e[4], e[5], e[6]), new Vector3f(e[1], e[2], e[3]),
                            mix(fire, k0 * 0.3f), mix(fire, k0), mix(fire, k1), mix(fire, k1 * 0.3f));
                }
            }
            prev = e;
        }
    }

    private static float trailK(float ageTicks) {
        float x = 1f - ageTicks / 3f;
        return x <= 0f ? 0f : x * x * 0.55f;
    }

    // =====================================================================
    // Покой у не‑игроков (стойки, мобы): как меч в руке (HeldItemRenderer.renderItem)
    // =====================================================================
    public static boolean renderRest(ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
                                     VertexConsumerProvider vcp, int light) {
        if (!isCleaver(stack)) {
            return false;
        }
        boolean fp = mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
        boolean tp = mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
        if (!fp && !tp) {
            return false;
        }
        BloodSwordPose.Frame f = BloodSwordPose.rest(fp, leftHanded, new BloodSwordPose.Frame());
        weapon(f.p, f.q, CleaverKeys.SCALE_3P / 16f, 0f, 0f, 0f, matrices, vcp, light, OverlayTexture.DEFAULT_UV, stack.hasGlint());
        return true;
    }
}
