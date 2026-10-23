package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.entity.BloodlingEntity;
import net.minecraft.client.model.ModelPart;

import java.util.Map;

/** Модель «мелкого кровожада» (tools/bloodthirster_gen.py): idle/walk по скорости, attack — по взмаху руки. */
public class BloodlingModel extends BedrockEntityModel<BloodlingEntity> {

    private static final Map<String, BedrockAnimations.Clip> C = BedrockAnimations.load("bloodling");
    private static final BedrockAnimations.Clip IDLE = C.get("idle");
    private static final BedrockAnimations.Clip WALK = C.get("walk");
    private static final BedrockAnimations.Clip ATTACK = C.get("attack");

    private final ModelPart head;

    public BloodlingModel(ModelPart root) {
        super(root);
        this.head = this.bone("head");
    }

    @Override
    public void setAngles(BloodlingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        float move = clamp01(limbDistance * 2.5f);
        float atk = this.handSwingProgress > 0f ? 1f : 0f;
        this.play(IDLE, animationProgress / 20f, (1f - move));
        this.play(WALK, limbAngle * 0.12f, move);
        if (atk > 0f) {
            this.play(ATTACK, this.handSwingProgress * ATTACK.length(), 1f);
        }
        this.head.yaw += headYaw * DEG;
        this.head.pitch += headPitch * DEG;
    }
}
