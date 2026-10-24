package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.entity.AbstractCurbEntity;
import com.lesha_top1972.basedefense.entity.CurbMinionEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.MathHelper;

import java.util.Map;

/**
 * Модель «Бордюра» и его прислужника (geo/curb*.geo.json, создаёт tools/curb_gen.py).
 * idle / walk / run — по скорости; атаки — по статусу с сервера (combo, jet, slam, spin, summon),
 * у призванного прислужника — emerge (выход из‑под земли). Реактивные струи из локтей (кости jet_r/jet_l,
 * только светящаяся маска) — длина и мерцание задаются кодом по силе струи.
 */
public class CurbModel<T extends AbstractCurbEntity> extends BedrockEntityModel<T> {

    private final BedrockAnimations.Clip idle;
    private final BedrockAnimations.Clip walk;
    private final BedrockAnimations.Clip run;
    private final BedrockAnimations.Clip emerge;
    private final BedrockAnimations.Clip[] actions;

    private final ModelPart head;
    private final ModelPart[] jets;

    public CurbModel(ModelPart root, String name) {
        super(root);
        Map<String, BedrockAnimations.Clip> c = BedrockAnimations.load(name);
        this.idle = c.get("idle");
        this.walk = c.get("walk");
        this.run = c.get("run");
        this.emerge = c.get("emerge");
        this.actions = new BedrockAnimations.Clip[]{c.get("combo"), c.get("jet"), c.get("slam"), c.get("spin"),
                c.get("summon")};
        this.head = this.bone("head");
        this.jets = new ModelPart[]{this.bone("jet_r"), this.bone("jet_l")};
    }

    @Override
    public void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        BedrockAnimations.Clip clip = null;
        float ct = 0f;
        float w = 0f;
        int a = entity.clientAction;
        float t = (animationProgress - entity.clientActionAge) / 20f;
        if (entity instanceof CurbMinionEntity m && m.isEmerging() && this.emerge != null) {
            clip = this.emerge;
            ct = animationProgress / 20f;
            w = 1f;
        } else if (a >= 0 && a < this.actions.length && this.actions[a] != null && t >= 0
                && t < this.actions[a].length()) {
            clip = this.actions[a];
            ct = t;
            w = Math.min(clamp01(t / 0.08f), clamp01((clip.length() - t) / 0.2f));
        }
        float free = 1f - w;

        float move = clamp01(limbDistance * 2.5f);
        float runK = clamp01((limbDistance - 0.55f) / 0.3f);
        this.play(this.idle, animationProgress / 20f, free * (1f - move));
        this.play(this.walk, limbAngle * 0.1f, free * move * (1f - runK));
        this.play(this.run, limbAngle * 0.035f, free * move * runK);
        if (clip != null) {
            this.play(clip, ct, w);
        }

        // реактивные струи: длина по силе тяги + мерцание
        float tickDelta = MinecraftClient.getInstance().getTickDelta();
        for (int s = 0; s < 2; s++) {
            float p = entity.jetPower(s, tickDelta);
            ModelPart jet = this.jets[s];
            jet.visible = p > 0.02f;
            if (jet.visible) {
                float fl = MathHelper.sin(animationProgress * 2.3f + s) * 0.5f + MathHelper.sin(animationProgress * 3.7f) * 0.5f;
                jet.yScale = (0.45f + 0.95f * p) * (1f + 0.14f * fl);
                jet.xScale = (0.7f + 0.35f * p) * (1f - 0.08f * fl);
                jet.zScale = jet.xScale;
            }
        }

        this.head.yaw += headYaw * DEG * free * 0.8f;
        this.head.pitch += headPitch * DEG * free * 0.8f;
    }
}
