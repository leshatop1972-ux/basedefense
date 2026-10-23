package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.effect.ModEffects;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Заморозка обнуляет скорость — без этого обзор (FOV) сильно сужался бы. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
    private void basedefense$frozenFov(CallbackInfoReturnable<Float> cir) {
        if (ModEffects.isFrozen((AbstractClientPlayerEntity) (Object) this)) {
            cir.setReturnValue(1.0f);
        }
    }
}
