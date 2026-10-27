package com.lesha_top1972.basedefense.client.entity.beetle;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.entity.BeetleMiteEntity;
import net.minecraft.client.model.ModelPart;

import java.util.Map;

/** Маленький жук: idle / run по скорости, укус — по статусу. */
public class BeetleMiteModel extends BedrockEntityModel<BeetleMiteEntity> {

    private final Map<String, BedrockAnimations.Clip> clips;

    public BeetleMiteModel(ModelPart root) {
        super(root);
        this.clips = BedrockAnimations.load("beetle_mite");
    }

    @Override
    public void setAngles(BeetleMiteEntity e, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        BedrockAnimations.Clip bite = this.clips.get("bite");
        float t = (animationProgress - e.clientBiteAge) / 20f;
        float w = 0f;
        if (bite != null && t >= 0 && t < bite.length()) {
            w = Math.min(clamp01(t / 0.06f), clamp01((bite.length() - t) / 0.15f));
        }
        float free = 1f - w;
        float move = clamp01(limbDistance * 2.5f);
        this.play(this.clips.get("idle"), (animationProgress + e.getId() * 7) / 20f, free * (1f - move));
        this.play(this.clips.get("run"), limbAngle * 0.44f, free * move);
        if (w > 0) {
            this.play(bite, t, w);
        }
    }
}
