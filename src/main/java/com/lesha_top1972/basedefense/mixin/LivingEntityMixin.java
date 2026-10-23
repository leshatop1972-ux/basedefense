package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.effect.ModEffects;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Заморозка: прыжок без силы (нельзя подпрыгнуть). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "getJumpVelocity", at = @At("RETURN"), cancellable = true)
    private void basedefense$frozenJump(CallbackInfoReturnable<Float> cir) {
        if (ModEffects.isFrozen((LivingEntity) (Object) this)) {
            cir.setReturnValue(0.0f);
        }
    }
}
