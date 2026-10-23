package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.entity.TatteredLancerEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.MathHelper;

import java.util.Map;

/**
 * Модель босса «Оборванный улан» (geo/tattered_lancer.geo.json, создаётся tools/lancer_gen.py).
 * Движение: idle / walk / run плавно сменяются по скорости. Атаки (по статусу с сервера):
 * thrust, charge, sweep, uppercut, backstep, roar, grand; прыжок — leap_start → leap_air (сальто, держит
 * позу до приземления) → leap_land; ливень копий — rain_rise → rain_hover (парит) → rain_descend → leap_land.
 * Вымпел и перо дополнительно трепещут кодом.
 */
public class TatteredLancerModel extends BedrockEntityModel<TatteredLancerEntity> {

    private static final Map<String, BedrockAnimations.Clip> C = BedrockAnimations.load("tattered_lancer");
    private static final BedrockAnimations.Clip IDLE = C.get("idle");
    private static final BedrockAnimations.Clip WALK = C.get("walk");
    private static final BedrockAnimations.Clip RUN = C.get("run");
    private static final BedrockAnimations.Clip[] ACTIONS = {
            C.get("thrust"), C.get("charge"), null, C.get("leap_land"), C.get("sweep"), C.get("uppercut"),
            C.get("backstep"), C.get("roar"), C.get("grand"), null, null
    };
    private static final BedrockAnimations.Clip RAIN_RISE = C.get("rain_rise");
    private static final BedrockAnimations.Clip RAIN_HOVER = C.get("rain_hover");
    private static final BedrockAnimations.Clip RAIN_DESCEND = C.get("rain_descend");
    private static final BedrockAnimations.Clip LEAP_START = C.get("leap_start");
    private static final BedrockAnimations.Clip LEAP_AIR = C.get("leap_air");
    private static final float LEAP_START_LEN = TatteredLancerEntity.LEAP_WINDUP / 20f;
    /** Если сигнал приземления не пришёл, прыжок на клиенте заканчивается сам. */
    private static final float LEAP_TIMEOUT = 4.0f;

    private final ModelPart head;
    private final ModelPart pennant;
    private final ModelPart pennantTip;
    private final ModelPart plume;
    /** Цепочки костей от корня — чтобы найти в мире остриё копья и глаза (следы, блики, огонь из глаз). */
    private final ModelPart[] lanceChain;
    private final ModelPart[] headChain;

    public TatteredLancerModel(ModelPart root) {
        super(root);
        this.head = this.bone("head");
        this.pennant = this.bone("pennant");
        this.pennantTip = this.bone("pennant_tip");
        this.plume = this.bone("plume");
        ModelPart boneRoot = this.bone("bone_root");
        ModelPart body = this.bone("body");
        ModelPart torso = this.bone("torso");
        this.lanceChain = new ModelPart[]{root, boneRoot, body, torso, this.bone("right_arm"), this.bone("right_fore"),
                this.bone("lance")};
        this.headChain = new ModelPart[]{root, boneRoot, body, torso, this.head};
    }

    public ModelPart[] lanceChain() {
        return this.lanceChain;
    }

    public ModelPart[] headChain() {
        return this.headChain;
    }

    @Override
    public void setAngles(TatteredLancerEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();

        // --- атака ---
        BedrockAnimations.Clip clip = null;
        float ct = 0f;
        float w = 0f;
        int a = entity.clientAction;
        float t = (animationProgress - entity.clientActionAge) / 20f;
        if (a == TatteredLancerEntity.LEAP) {
            if (t < LEAP_TIMEOUT) {
                if (t < LEAP_START_LEN) {
                    clip = LEAP_START;
                    ct = t;
                    w = clamp01(t / 0.08f);
                } else {
                    clip = LEAP_AIR;
                    ct = t - LEAP_START_LEN;
                    w = 1f;
                }
            }
        } else if (a == TatteredLancerEntity.RAIN) {
            if (t < 12f) {
                clip = t < RAIN_RISE.length() ? RAIN_RISE : RAIN_HOVER;
                ct = t < RAIN_RISE.length() ? t : t - RAIN_RISE.length();
                w = t < RAIN_RISE.length() ? clamp01(t / 0.1f) : 1f;
            }
        } else if (a == TatteredLancerEntity.RAIN_END) {
            if (t < 6f) {
                clip = RAIN_DESCEND;
                ct = t;
                w = 1f;
            }
        } else if (a >= 0 && a < ACTIONS.length && ACTIONS[a] != null && t >= 0 && t < ACTIONS[a].length()) {
            clip = ACTIONS[a];
            ct = t;
            float in = a == TatteredLancerEntity.LAND ? 1f : clamp01(t / 0.1f);
            w = Math.min(in, clamp01((clip.length() - t) / 0.18f));
        }
        float free = 1f - w;

        // --- ходьба / бег / покой ---
        float move = clamp01(limbDistance * 2.5f);
        float run = clamp01((limbDistance - 0.55f) / 0.3f);
        this.play(IDLE, animationProgress / 20f, free * (1f - move));
        this.play(WALK, limbAngle * 0.1f, free * move * (1f - run));
        this.play(RUN, limbAngle * 0.03f, free * move * run);
        if (clip != null) {
            this.play(clip, ct, w);
        }

        // --- трепет вымпела и пера ---
        float speed = Math.min(1f, limbDistance + (clip != null ? 0.6f : 0f));
        float age = animationProgress;
        this.pennant.yaw += MathHelper.sin(age * 0.7f) * (0.12f + 0.25f * speed);
        this.pennantTip.yaw += MathHelper.sin(age * 0.7f - 0.9f) * (0.18f + 0.35f * speed);
        this.plume.pitch += MathHelper.sin(age * 0.35f) * (0.05f + 0.1f * speed);

        this.head.yaw += headYaw * DEG * free;
        this.head.pitch += headPitch * DEG * free;
    }
}
