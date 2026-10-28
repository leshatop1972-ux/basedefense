package com.lesha_top1972.basedefense.client.entity.enemy.nabat;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/** Колокол (колокол‑опора, падающий колокол, «плен»): язык качается сам, при ударе — сильнее. */
public class NabatBellModel<T extends Entity> extends BedrockEntityModel<T> {

    private final ModelPart clapper;
    private final ModelPart bell;
    /** Тряска (0..1) — «плен» и удары. */
    public float shake;

    public NabatBellModel(ModelPart root) {
        super(root);
        this.clapper = this.bone("bell_clapper");
        this.bell = this.bone("bell");
    }

    @Override
    public void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
                          float headPitch) {
        this.resetPose();
        float t = animationProgress;
        this.clapper.pitch = MathHelper.sin(t * 0.21f) * 0.06f;
        this.clapper.roll = MathHelper.sin(t * 0.17f + 1.3f) * 0.05f;
        if (this.shake > 0) {
            this.bell.roll = MathHelper.sin(t * 2.1f) * 0.03f * this.shake;
            this.bell.pitch = MathHelper.sin(t * 1.7f + 0.6f) * 0.03f * this.shake;
            this.clapper.pitch += MathHelper.sin(t * 1.3f) * 0.3f * this.shake;
        }
    }
}
