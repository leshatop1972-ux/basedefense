package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.artifact.ArtifactClient;
import com.lesha_top1972.basedefense.client.weapon.BloodSwordClient;
import com.lesha_top1972.basedefense.effect.ModEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Заморозка обнуляет скорость, замах мечом кровожада замедляет — без этого обзор (FOV) сужался бы. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
    private void basedefense$frozenFov(CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        if (ModEffects.isFrozen(self) || self == MinecraftClient.getInstance().player && BloodSwordClient.localBusy()) {
            cir.setReturnValue(1.0f);
        }
        if (self == MinecraftClient.getInstance().player) {            // рывок артефакта — обзор чуть шире
            float w = ArtifactClient.localWeight();
            if (w > 0f) {
                cir.setReturnValue(cir.getReturnValueF() * (1f + 0.1f * w));
            }
        }
    }
}
