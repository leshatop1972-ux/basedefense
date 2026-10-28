package com.lesha_top1972.basedefense.client.entity.enemy.horror;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.client.entity.bedrock.HeadLook;
import com.lesha_top1972.basedefense.entity.enemy.horror.HorrorEntity;
import net.minecraft.client.model.ModelPart;

import java.util.Map;

/** Модель «сущностей» (как игрок): idle / walk по скорости, удар рукой и «Двойной разрыв» — по статусу, голова следит за игроком. */
public class HorrorModel<T extends HorrorEntity> extends BedrockEntityModel<T> {

    private static final String[] ACTIONS = {"attack", "rend"};
    private final Map<String, BedrockAnimations.Clip> clips;
    private final ModelPart head;
    private final float walkK;

    public HorrorModel(ModelPart root, String name, float walkK) {
        super(root);
        this.clips = BedrockAnimations.load(name);
        this.head = this.bone("head");
        this.walkK = walkK;
    }

    @Override
    public void setAngles(T e, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        this.resetPose();
        int a = e.clientAction;
        float t = (animationProgress - e.clientActionAge) / 20f;
        BedrockAnimations.Clip c = a >= 0 && a < ACTIONS.length ? this.clips.get(ACTIONS[a]) : null;
        float w = 0f;
        if (c != null && t >= 0 && t < c.length()) {
            w = Math.min(clamp01(t / 0.06f), clamp01((c.length() - t) / 0.15f));
        }
        float free = 1f - w;
        float move = clamp01(limbDistance * 2.5f);
        this.play(this.clips.get("idle"), (animationProgress + e.getId() * 17) / 20f, free * (1f - move));
        this.play(this.clips.get("walk"), limbAngle * this.walkK, free * move);
        if (w > 0) {
            this.play(c, t, w);
        }
        HeadLook.apply(this.head, headYaw, headPitch, free);
    }
}
