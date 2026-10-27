package com.lesha_top1972.basedefense.client.entity.nabat;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.Entity;

/**
 * Кости внутри модели оружия (тетива, стрела, вымпел…): поиск по имени и проигрывание клипов
 * оружия (animations/&lt;оружие&gt;.animation.json, создаёт tools/weaponize.py).
 */
public final class WeaponRig extends BedrockEntityModel<Entity> {

    public WeaponRig(ModelPart weaponRoot) {
        super(weaponRoot);
    }

    @Override
    public void setAngles(Entity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
                          float headPitch) {
    }

    public void reset() {
        this.resetPose();
    }

    public void playClip(BedrockAnimations.Clip clip, float seconds, float weight) {
        this.play(clip, seconds, weight);
    }

    public ModelPart part(String name) {
        return this.bone(name);
    }

    public boolean has(String name) {
        return this.getChild(name).isPresent();
    }
}
