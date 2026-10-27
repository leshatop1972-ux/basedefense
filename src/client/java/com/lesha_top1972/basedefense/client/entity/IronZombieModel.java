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
 * по скорости, attack добавляется сверху (только верх тела).
 */
public class IronZombieModel extends BedrockEntityModel<IronZombieEntity> {

    private static final Map<String, BedrockAnimations.Clip> CLIPS = BedrockAnimations.load("iron_zombie");
    private static final BedrockAnimations.Clip IDLE = CLIPS.get("idle");
    private static final BedrockAnimations.Clip WALK = CLIPS.get("walk");
    private static final BedrockAnimations.Clip ATTACK = CLIPS.get("attack");

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

        float move = clamp01(limbDistance * 2.5f);
        this.play(IDLE, animationProgress / 20f, 1f - move);
        this.play(WALK, limbAngle * 0.05f * WALK_SCALE, move);

        entity.attackState.update(animationProgress, 1f);
        if (entity.attackState.isRunning()) {
            float t = entity.attackState.getTimeRunning() / 1000f;
            if (ATTACK != null && t <= ATTACK.length()) {
                this.play(ATTACK, t, 1f);
            } else {
                entity.attackState.stop();
            }
        }

        HeadLook.apply(this.head, headYaw, headPitch, 1f);
    }
}
