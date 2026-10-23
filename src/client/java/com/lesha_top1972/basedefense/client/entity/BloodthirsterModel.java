package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.entity.BloodthirsterEntity;
import net.minecraft.client.model.ModelPart;

import java.util.Map;

import static com.lesha_top1972.basedefense.entity.BloodthirsterEntity.*;

/**
 * Модель босса «Кровожад» (geo/bloodthirster.geo.json, создаёт tools/bloodthirster_gen.py).
 * Движение по фазе: idle1/walk1/run1 (сгорбленный) или idle2/walk2/run2 (выпрямленный, с мечами).
 * Атаки по статусу с сервера; бросок — pounce_start → pounce_air (до приземления) → pounce_land,
 * «багровый вальс» — waltz_start → waltz_spin (цикл) → waltz_end. Мечи и венец видны только во 2‑й фазе
 * (и с середины смены облика).
 */
public class BloodthirsterModel extends BedrockEntityModel<BloodthirsterEntity> {

    private static final Map<String, BedrockAnimations.Clip> C = BedrockAnimations.load("bloodthirster");
    private static final BedrockAnimations.Clip[] IDLE = {C.get("idle1"), C.get("idle2")};
    private static final BedrockAnimations.Clip[] WALK = {C.get("walk1"), C.get("walk2")};
    private static final BedrockAnimations.Clip[] RUN = {C.get("run1"), C.get("run2")};
    private static final BedrockAnimations.Clip[] ACTIONS = {
            C.get("swing"), C.get("devour"), null, C.get("pounce_land"), C.get("transform"),
            C.get("slash"), C.get("thrust"), C.get("summon"), C.get("eggs"), null
    };
    private static final BedrockAnimations.Clip POUNCE_START = C.get("pounce_start");
    private static final BedrockAnimations.Clip POUNCE_AIR = C.get("pounce_air");
    private static final BedrockAnimations.Clip WALTZ_START = C.get("waltz_start");
    private static final BedrockAnimations.Clip WALTZ_SPIN = C.get("waltz_spin");
    private static final BedrockAnimations.Clip WALTZ_END = C.get("waltz_end");
    private static final float POUNCE_START_LEN = POUNCE_WINDUP / 20f;
    private static final float WALTZ_OPEN_S = WALTZ_OPEN / 20f;
    private static final float WALTZ_SPIN_S = WALTZ_SPIN_END / 20f;
    /** С этого момента смены облика видны мечи и венец (они вырастают в анимации). */
    private static final float SWORDS_APPEAR = 2.0f;

    private final ModelPart head;
    private final ModelPart swordR;
    private final ModelPart swordL;
    private final ModelPart halo;

    public BloodthirsterModel(ModelPart root) {
        super(root);
        this.head = this.bone("head");
        this.swordR = this.bone("sword_r");
        this.swordL = this.bone("sword_l");
        this.halo = this.bone("halo");
    }

    @Override
    public void setAngles(BloodthirsterEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        int a = entity.clientAction;
        float t = (animationProgress - entity.clientActionAge) / 20f;
        boolean p2 = entity.isPhase2();

        // --- атака ---
        BedrockAnimations.Clip clip = null;
        float ct = 0f;
        float w = 0f;
        if (a == POUNCE) {
            if (t < 4f) {
                clip = t < POUNCE_START_LEN ? POUNCE_START : POUNCE_AIR;
                ct = t < POUNCE_START_LEN ? t : t - POUNCE_START_LEN;
                w = t < POUNCE_START_LEN ? clamp01(t / 0.1f) : 1f;
            }
        } else if (a == WALTZ) {
            if (t < WALTZ_OPEN_S) {
                clip = WALTZ_START;
                ct = t;
                w = clamp01(t / 0.1f);
            } else if (t < WALTZ_SPIN_S) {
                clip = WALTZ_SPIN;
                ct = t - WALTZ_OPEN_S;
                w = 1f;
            } else if (t < WALTZ_SPIN_S + WALTZ_END.length()) {
                clip = WALTZ_END;
                ct = t - WALTZ_SPIN_S;
                w = clamp01((WALTZ_END.length() - ct) / 0.2f);
            }
        } else if (a >= 0 && a < ACTIONS.length && ACTIONS[a] != null && t >= 0 && t < ACTIONS[a].length()) {
            clip = ACTIONS[a];
            ct = t;
            float in = a == LAND ? 1f : clamp01(t / 0.12f);
            w = Math.min(in, clamp01((clip.length() - t) / 0.25f));
        }
        float free = 1f - w;

        // --- покой / шаг / бег (по фазе) ---
        int ph = p2 ? 1 : 0;
        float move = clamp01(limbDistance * 2.5f);
        float run = clamp01((limbDistance - 0.5f) / 0.3f);
        this.play(IDLE[ph], animationProgress / 20f, free * (1f - move));
        this.play(WALK[ph], limbAngle * (p2 ? 0.07f : 0.06f), free * move * (1f - run));
        this.play(RUN[ph], limbAngle * 0.035f, free * move * run);
        if (clip != null) {
            this.play(clip, ct, w);
        }

        boolean blades = p2 || (a == TRANSFORM && t >= SWORDS_APPEAR && t < 3.5f);
        this.swordR.visible = blades;
        this.swordL.visible = blades;
        this.halo.visible = blades;

        this.head.yaw += headYaw * DEG * free * 0.7f;
        this.head.pitch += headPitch * DEG * free * 0.7f;
    }
}
