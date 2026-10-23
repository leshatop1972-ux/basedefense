package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.entity.BloodEggEntity;
import net.minecraft.client.model.ModelPart;

import java.util.Map;

/** Модель «яйца кровожада»: только пульсация (сердцебиение). */
public class BloodEggModel extends BedrockEntityModel<BloodEggEntity> {

    private static final Map<String, BedrockAnimations.Clip> C = BedrockAnimations.load("blood_egg");
    private static final BedrockAnimations.Clip IDLE = C.get("idle");

    public BloodEggModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setAngles(BloodEggEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        this.play(IDLE, (animationProgress + entity.getId() * 7) / 20f, 1f);
    }
}
