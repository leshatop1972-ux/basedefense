package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.artifact.ArtifactClient;
import com.lesha_top1972.basedefense.client.weapon.BloodSwordClient;
import com.lesha_top1972.basedefense.client.weapon.KatanaClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Меч кровожада и катана: позы игрока (руки, корпус, ноги) поверх ванильной. */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void basedefense$bloodSwordPose(LivingEntity entity, float limbAngle, float limbDistance,
                                            float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        BloodSwordClient.applyModel((BipedEntityModel<?>) (Object) this, entity);
        KatanaClient.applyModel((BipedEntityModel<?>) (Object) this, entity);
        ArtifactClient.applyModel((BipedEntityModel<?>) (Object) this, entity);
    }
}
