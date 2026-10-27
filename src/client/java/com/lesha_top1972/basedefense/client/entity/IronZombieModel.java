package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.client.entity.bedrock.HeadLook;
import com.lesha_top1972.basedefense.entity.IronZombieEntity;
import net.minecraft.client.model.ModelPart;

import java.util.Map;

/**
 * Модель железного зомби из Blockbench (geo/iron_zombie.geo.json).
 * Анимации (animations/iron_zombie.animation.json): idle и walk плавно сменяют друг друга
 * по скорости, attack добавляется сверху (только верх тела). «Рывок‑хват»: lunge и grab
 * (клипы дописывает tools/iron_zombie_anims.py) — на время приёма idle/walk приглушаются.
 */
public class IronZombieModel extends BedrockEntityModel<IronZombieEntity> {

    private static final Map<String, BedrockAnimations.Clip> CLIPS = BedrockAnimations.load("iron_zombie");
    private static final BedrockAnimations.Clip IDLE = CLIPS.get("idle");
    private static final BedrockAnimations.Clip WALK = CLIPS.get("walk");
    private static final BedrockAnimations.Clip ATTACK = CLIPS.get("attack");
    private static final BedrockAnimations.Clip LUNGE = CLIPS.get("lunge");
    private static final BedrockAnimations.Clip GRAB = CLIPS.get("grab");
    /** Плавный вход и выход приёма (с). */
    private static final float BLEND = 0.12f;

    /** Скорость проигрывания шага относительно limbAngle. */
    private static final float WALK_SCALE = 2.2f;

    private final ModelPart head;

    public IronZombieModel(ModelPart root) {
        super(root);
        this.head = this.bone("head");
    }

    @Override
    public void setAngles(IronZombieEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();

        // --- «Рывок‑хват»: время клипов и вес приёма
        entity.lungeState.update(animationProgress, 1f);
        entity.grabState.update(animationProgress, 1f);
        float lt = -1f;
        float gt = -1f;
        if (entity.lungeState.isRunning()) {
            lt = entity.lungeState.getTimeRunning() / 1000f;
            if (LUNGE == null || lt > LUNGE.length()) {
                entity.lungeState.stop();
                lt = -1f;
            }
        }
        if (entity.grabState.isRunning()) {
            gt = entity.grabState.getTimeRunning() / 1000f;
            if (GRAB == null || gt > GRAB.length()) {
                entity.grabState.stop();
                gt = -1f;
            }
        }
        float lungeW = 0f;
        float grabW = 0f;
        if (gt >= 0f) {
            grabW = Math.min(1f, gt / BLEND) * clamp01((GRAB.length() - gt) / BLEND);
            if (lt >= 0f && gt < BLEND) {
                lungeW = 1f - gt / BLEND;           // перетекание из рывка в хват
            } else if (lt >= 0f) {
                entity.lungeState.stop();
                lt = -1f;
            }
        } else if (lt >= 0f) {
            lungeW = Math.min(1f, lt / BLEND) * clamp01((LUNGE.length() - lt) / BLEND);
        }
        float action = Math.max(lungeW, grabW);

        float move = clamp01(limbDistance * 2.5f);
        float base = 1f - 0.85f * action;
        this.play(IDLE, animationProgress / 20f, (1f - move) * base);
        this.play(WALK, limbAngle * 0.05f * WALK_SCALE, move * base);
        if (lt >= 0f) {
            this.play(LUNGE, lt, lungeW);
        }
        if (gt >= 0f) {
            this.play(GRAB, gt, grabW);
        }

        entity.attackState.update(animationProgress, 1f);
        if (entity.attackState.isRunning()) {
            float t = entity.attackState.getTimeRunning() / 1000f;
            if (ATTACK != null && t <= ATTACK.length()) {
                this.play(ATTACK, t, 1f);
            } else {
                entity.attackState.stop();
            }
        }

        HeadLook.apply(this.head, headYaw, headPitch, 1f - 0.8f * action);
    }
}
