package com.lesha_top1972.basedefense.client.entity.enemy.nabat;

import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponHolder;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.client.entity.bedrock.HeadLook;
import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponTrack;
import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatBladeEntity;
import net.minecraft.client.model.ModelPart;
import org.joml.Quaternionf;

import java.util.Map;

/** Модель мечника «Отзвук» (geo/nabat_blade.geo.json): ходьба/бег по скорости, атаки по статусу, меч — отдельно. */
public class NabatBladeModel extends BedrockEntityModel<NabatBladeEntity> implements WeaponHolder {

    private static final String[] ACTIONS = {"combo", "dash", "parry", "assemble"};

    private final Map<String, BedrockAnimations.Clip> clips;
    private final WeaponTrack weapon = new WeaponTrack("nabat_blade");
    private final Quaternionf weaponRot = new Quaternionf();
    private final ModelPart[] chain;
    private final ModelPart head;

    public NabatBladeModel(ModelPart root) {
        super(root);
        this.clips = BedrockAnimations.load("nabat_blade");
        this.chain = new ModelPart[]{root, this.bone("bone_root"), this.bone("body"), this.bone("torso"),
                this.bone("right_arm"), this.bone("right_fore"), this.bone("right_hand")};
        this.head = this.bone("head");
    }

    @Override
    public WeaponTrack weaponTrack() {
        return this.weapon;
    }

    @Override
    public ModelPart[] handChain() {
        return this.chain;
    }

    @Override
    public Quaternionf weaponRotation() {
        return this.weaponRot;
    }

    private void play2(String name, float t, float w) {
        BedrockAnimations.Clip c = this.clips.get(name);
        if (c != null && w > 0.001f) {
            this.play(c, t, w);
            this.weapon.add(name, t, w);
        }
    }

    @Override
    public void setAngles(NabatBladeEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        this.weapon.reset();
        String clip = null;
        float ct = 0f;
        float w = 0f;
        int a = entity.clientAction;
        float t = (animationProgress - entity.clientActionAge) / 20f;
        if (entity.isAssembling() && a != NabatBladeEntity.ASSEMBLE) {
            a = NabatBladeEntity.ASSEMBLE;
            t = animationProgress / 20f;
        }
        BedrockAnimations.Clip c = a >= 0 && a < ACTIONS.length ? this.clips.get(ACTIONS[a]) : null;
        if (c != null && t >= 0 && t < c.length()) {
            clip = ACTIONS[a];
            ct = t;
            float in = a == NabatBladeEntity.ASSEMBLE ? 1f : clamp01(t / 0.08f);
            w = Math.min(in, clamp01((c.length() - t) / 0.2f));
        }
        float free = 1f - w;
        float move = clamp01(limbDistance * 2.5f);
        float runK = clamp01((limbDistance - 0.55f) / 0.3f);
        this.play2("idle", animationProgress / 20f, free * (1f - move));
        this.play2("walk", limbAngle * 0.09f, free * move * (1f - runK));
        this.play2("run", limbAngle * 0.035f, free * move * runK);
        if (clip != null) {
            this.play2(clip, ct, w);
        }
        this.weapon.result(this.weaponRot);
        HeadLook.apply(this.head, headYaw, headPitch, free * 0.8f);
    }
}
