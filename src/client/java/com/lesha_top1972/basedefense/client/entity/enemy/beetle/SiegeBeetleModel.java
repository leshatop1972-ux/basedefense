package com.lesha_top1972.basedefense.client.entity.enemy.beetle;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.client.entity.bedrock.HeadLook;
import com.lesha_top1972.basedefense.entity.enemy.beetle.SiegeBeetleEntity;
import net.minecraft.client.model.ModelPart;

import java.util.Map;

/**
 * Осадный жук (geo/siege_beetle, клипы — tools/beetle_gen.py): idle / walk / run смешиваются по сглаженной
 * скорости шага, скорость клипа — по limbAngle (лапы не скользят); действия — по статусу с сервера.
 */
public class SiegeBeetleModel extends BedrockEntityModel<SiegeBeetleEntity> {

    private static final String[] ACTIONS = {"ram", "horn", "bite", "lay", "hurt"};
    /** Секунд клипа на единицу limbAngle (шаг лап совпадает со скоростью). */
    private static final float WALK_K = 0.49f;
    private static final float RUN_K = 0.165f;

    private final Map<String, BedrockAnimations.Clip> clips;
    private final ModelPart head;
    private float speed;
    private float lastAnim = -1f;

    public SiegeBeetleModel(ModelPart root) {
        super(root);
        this.head = this.bone("head");
        this.clips = BedrockAnimations.load("siege_beetle");
    }

    private void clip(String name, float t, float w) {
        this.play(this.clips.get(name), t, w);
    }

    @Override
    public void setAngles(SiegeBeetleEntity e, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        // сглаженная скорость (без рывков между шагом и бегом)
        float dt = this.lastAnim < 0 ? 1f : Math.max(0f, Math.min(1f, animationProgress - this.lastAnim));
        this.lastAnim = animationProgress;
        this.speed += (limbDistance - this.speed) * Math.min(1f, 0.25f * dt + 0.02f);
        float w = 0f;
        String name = null;
        float ct = 0f;
        int a = e.clientAction;
        float t = (animationProgress - e.clientActionAge) / 20f;
        BedrockAnimations.Clip c = a >= 0 && a < ACTIONS.length ? this.clips.get(ACTIONS[a]) : null;
        if (c != null && t >= 0 && t < c.length()) {
            name = ACTIONS[a];
            ct = t;
            float in = clamp01(t / (a == SiegeBeetleEntity.HURT ? 0.05f : 0.12f));
            w = Math.min(in, clamp01((c.length() - t) / 0.25f));
        }
        float free = 1f - w;
        float move = clamp01(this.speed * 3f);
        float runK = clamp01((this.speed - 0.45f) / 0.3f);
        this.clip("idle", (animationProgress + e.getId() * 13) / 20f, free * (1f - move));
        this.clip("walk", limbAngle * WALK_K, free * move * (1f - runK));
        this.clip("run", limbAngle * RUN_K, free * move * runK);
        if (name != null) {
            this.clip(name, ct, w);
        }
        HeadLook.apply(this.head, headYaw, headPitch, free * 0.6f, 35f, 20f);
    }
}
