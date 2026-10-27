package com.lesha_top1972.basedefense.client.entity.bedrock;

import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.MathHelper;

/**
 * Голова смотрит на цель/ближайшего игрока поверх анимации: угол из headYaw/headPitch сущности
 * (их плавно ведут цели взгляда), с ограничением, чтобы шея не выворачивалась, и с весом
 * (во время атак — слабее или ноль, чтобы не ломать удары).
 */
public final class HeadLook {

    public static final float MAX_YAW = 60f;
    public static final float MAX_PITCH = 35f;
    private static final float DEG = (float) (Math.PI / 180.0);

    private HeadLook() {
    }

    public static void apply(ModelPart head, float headYaw, float headPitch, float weight) {
        apply(head, headYaw, headPitch, weight, MAX_YAW, MAX_PITCH);
    }

    public static void apply(ModelPart head, float headYaw, float headPitch, float weight, float maxYaw, float maxPitch) {
        if (weight <= 0.001f) {
            return;
        }
        float yaw = MathHelper.clamp(MathHelper.wrapDegrees(headYaw), -maxYaw, maxYaw);
        float pitch = MathHelper.clamp(headPitch, -maxPitch, maxPitch);
        head.yaw += yaw * DEG * weight;
        head.pitch += pitch * DEG * weight;
    }
}
