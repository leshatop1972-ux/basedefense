package com.lesha_top1972.basedefense.client.entity.enemy.thunder_tiger;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.client.entity.bedrock.HeadLook;
import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponHolder;
import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponRig;
import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponTrack;
import com.lesha_top1972.basedefense.entity.enemy.thunder_tiger.ThunderTigerEntity;
import com.lesha_top1972.basedefense.entity.enemy.thunder_tiger.TigerTimings;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

import java.util.Map;

/**
 * Модель «Громового тигра» (geo/thunder_tiger — модель автора мода, кости перестроены tools/thunder_tiger_gen.py).
 * Циклы смешиваются по скорости и направлению шага: вальяжно (курит / вразвалку), бой фазы 1 (стойка с тесаком
 * в одной руке), фаза 2 (двумя руками); вбок — приставной шаг, назад — пятится, быстро — бег. Действия — по статусу
 * с сервера. Тесак — отдельная модель по дорожке thunder_tiger.weapon.json. Сигара пропадает в фазе 2, кольца силы
 * за спиной вращаются, брови хмурятся в бою.
 */
public class ThunderTigerModel extends BedrockEntityModel<ThunderTigerEntity> implements WeaponHolder {

    private final Map<String, BedrockAnimations.Clip> clips;
    private final WeaponTrack weapon = new WeaponTrack("thunder_tiger");
    private final Quaternionf weaponRot = new Quaternionf();
    private final ModelPart[] chain;
    final ModelPart[] cigarChain;
    private final ModelPart head;
    private final ModelPart cigar;
    private final ModelPart rightBrow;
    private final ModelPart leftBrow;
    private final ModelPart[] rings = new ModelPart[ThunderTigerEntity.RINGS_MAX];
    private WeaponRig rig;
    /** Откинутый патронник (0..1) и качание цепочки — для poseWeapon. */
    private float chamberOpen;
    private float chainSwing;

    public ThunderTigerModel(ModelPart root) {
        super(root);
        this.clips = BedrockAnimations.load("thunder_tiger");
        this.chain = new ModelPart[]{root, this.bone("bone_root"), this.bone("body"), this.bone("torso"),
                this.bone("right_arm"), this.bone("right_fore"), this.bone("right_hand")};
        this.head = this.bone("head");
        this.cigar = this.bone("cigar");
        this.cigarChain = new ModelPart[]{root, this.bone("bone_root"), this.bone("body"), this.bone("torso"), this.head,
                this.cigar};
        this.rightBrow = this.bone("right_brow");
        this.leftBrow = this.bone("left_brow");
        for (int k = 0; k < this.rings.length; k++) {
            this.rings[k] = this.bone("ring" + k);
        }
    }

    @Override
    public WeaponTrack weaponTrack() {
        return this.weapon;
    }

    @Override
    public ModelPart[] handChain() {
        return this.chain;
    }

    @Override
    public Quaternionf weaponRotation() {
        return this.weaponRot;
    }

    @Override
    public void poseWeapon(int slot, ModelPart weaponRoot) {
        if (this.rig == null || this.rig.getPart() != weaponRoot) {
            this.rig = new WeaponRig(weaponRoot);
        }
        this.rig.reset();
        if (this.rig.has("chamber")) {
            this.rig.part("chamber").pitch = -1.2f * this.chamberOpen;
        }
        if (this.rig.has("chain")) {
            this.rig.part("chain").pitch = this.chainSwing;
        }
    }

    private void play2(String name, float t, float w) {
        BedrockAnimations.Clip c = this.clips.get(name);
        if (c != null && w > 0.001f) {
            this.play(c, t, w);
            this.weapon.add(name, t, w);
        }
    }

    private static void show(ModelPart p, float s) {
        p.xScale = s;
        p.yScale = s;
        p.zScale = s;
    }

    static boolean attacking(int a) {
        return a >= 0 && a != TigerTimings.RELOAD && a != TigerTimings.RELOAD_TAC && a != TigerTimings.TAUNT
                && a != TigerTimings.TWIRL && a != TigerTimings.STAGGER && a != TigerTimings.DEATH
                && a != TigerTimings.ONE_WAIT;
    }

    @Override
    public void setAngles(ThunderTigerEntity e, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        this.weapon.reset();
        int a = e.clientAction;
        float t = (animationProgress - e.clientActionAge) / 20f;
        String clip = null;
        float ct = 0f;
        float w = 0f;
        BedrockAnimations.Clip c = a >= 0 && a < TigerTimings.NAMES.length ? this.clips.get(TigerTimings.NAMES[a]) : null;
        if (c != null && t >= 0) {
            boolean dying = a == TigerTimings.DEATH;
            if (t < c.length() || dying) {
                clip = TigerTimings.NAMES[a];
                ct = Math.min(t, c.length());
                boolean quick = a == TigerTimings.FLURRY_A || a == TigerTimings.FLURRY_B || a == TigerTimings.DODGE_L
                        || a == TigerTimings.DODGE_R || a == TigerTimings.DODGE_BACK || a == TigerTimings.COUNTER
                        || a == TigerTimings.STAGGER;
                float in = clamp01(t / (quick ? 0.04f : 0.08f));
                float out = dying ? 1f : clamp01((c.length() - t) / 0.18f);
                w = Math.min(in, out);
            }
        }
        float free = 1f - w;
        int phase = e.getPhase();
        boolean lazy = e.isLazy() && phase == 1;
        if (free > 0.001f) {
            float sec = animationProgress / 20f;
            float yaw = e.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
            double dx = e.getX() - e.prevX;
            double dz = e.getZ() - e.prevZ;
            double fwd = -dx * MathHelper.sin(yaw) + dz * MathHelper.cos(yaw);
            double side = -dx * MathHelper.cos(yaw) - dz * MathHelper.sin(yaw);
            double speed = Math.sqrt(dx * dx + dz * dz);
            float move = clamp01((float) speed * 9f);
            float run = clamp01((float) (speed - 0.17) / 0.08f);
            float sum = (float) (Math.abs(fwd) + Math.abs(side)) + 1e-4f;
            float sideK = clamp01((float) Math.abs(side) / sum * 1.4f - 0.2f);
            float backK = fwd < 0 ? clamp01((float) -fwd / sum * 1.4f - 0.2f) * (1f - sideK) : 0f;
            String idle = lazy ? "idle_smoke" : (phase == 1 ? "guard" : "idle");
            String walk = lazy ? "walk_lazy" : "walk";
            float wMove = free * move * (1f - run);
            this.play2(idle, sec, free * (1f - move));
            if (lazy) {
                this.play2(walk, limbAngle * 0.16f, wMove);
            } else {
                this.play2(side > 0 ? "strafe_r" : "strafe_l", sec * 1.15f, wMove * sideK);
                this.play2("back", sec, wMove * backK);
                this.play2(walk, limbAngle * 0.12f, wMove * (1f - sideK - backK));
            }
            this.play2("run", limbAngle * 0.065f, free * move * run);
        }
        if (clip != null) {
            this.play2(clip, ct, w);
        }
        this.weapon.result(this.weaponRot);
        HeadLook.apply(this.head, headYaw, headPitch, free * 0.35f, 30f, 25f);
        // сигара: до фазы 2 (в переходе — пока не вынул изо рта)
        boolean cig = phase == 1 || (a == TigerTimings.PHASE2 && clip != null && ct < 0.55f);
        show(this.cigar, cig ? 1f : 0f);
        // брови: хмурится в атаке и во второй фазе
        float frown = (attacking(a) && clip != null ? 1f : 0f) * 0.8f + (phase >= 2 ? 0.4f : 0f);
        frown = Math.min(1f, frown);
        this.rightBrow.roll += -0.28f * frown;
        this.leftBrow.roll += 0.28f * frown;
        this.rightBrow.pivotY += 0.3f * frown;
        this.leftBrow.pivotY += 0.3f * frown;
        // кольца силы
        int n = e.getRings();
        float flare = 0f;
        if (clip != null && (a == TigerTimings.PHASE2 || a == TigerTimings.FINAL_RISE)) {
            float at = a == TigerTimings.PHASE2 ? TigerTimings.PHASE2_BURST / 20f : TigerTimings.FINAL_RISE_FLARE / 20f;
            flare = MathHelper.clamp(1f - Math.abs(ct - at) / 0.35f, 0f, 1f);
        }
        for (int k = 0; k < this.rings.length; k++) {
            ModelPart r = this.rings[k];
            if (k < n) {
                float s = 1f + 0.5f * flare + 0.05f * MathHelper.sin(animationProgress * 0.15f + k);
                show(r, s);
                r.roll += animationProgress * 0.06f * (k % 2 == 0 ? 1f : -1f) + k * 0.6f;
                r.pivotY += MathHelper.sin(animationProgress * 0.08f + k * 1.3f) * 0.6f;
            } else {
                show(r, 0f);
            }
        }
        // патронник: откинут во время перезарядки
        this.chamberOpen = 0f;
        if (clip != null && (a == TigerTimings.RELOAD || a == TigerTimings.RELOAD_TAC)) {
            float open = (a == TigerTimings.RELOAD ? TigerTimings.RELOAD_OPEN : TigerTimings.RELOAD_TAC_OPEN) / 20f;
            float close = (a == TigerTimings.RELOAD ? TigerTimings.RELOAD_CLOSE : TigerTimings.RELOAD_TAC_CLOSE) / 20f;
            this.chamberOpen = clamp01((ct - open) / 0.08f) * clamp01((close - ct) / 0.05f);
        }
        this.chainSwing = 0.35f * MathHelper.sin(animationProgress * 0.25f) + 0.15f * MathHelper.sin(animationProgress * 0.61f);
    }
}
